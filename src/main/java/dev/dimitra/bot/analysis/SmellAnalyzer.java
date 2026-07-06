package dev.dimitra.bot.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimitra.bot.llm.LlmClient;
import dev.dimitra.bot.llm.LlmFinding;
import dev.dimitra.bot.model.AnalyzedFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SmellAnalyzer {
    public record AnalysisResult(List<LlmFinding> findings, List<String> warnings, int chunksAnalyzed) {
    }

    public record AnalysisMetadata(String dataset, String caseId, String matchMode, int maxFileContentChars) {
        private static final AnalysisMetadata EMPTY = new AnalysisMetadata("", "", "", 0);

        public AnalysisMetadata(String dataset, String caseId, String matchMode) {
            this(dataset, caseId, matchMode, 0);
        }

        public static AnalysisMetadata empty() {
            return EMPTY;
        }
    }

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxFilesPerChunk;
    private final int maxChunkChars;
    private final Map<String, Object> llmOptions;
    private final boolean debug;
    private final AnalysisScope scope;
    private final ValidationMode validationMode;
    private final PromptRenderer promptRenderer;
    private final LlmArtifactWriter artifactWriter;
    private final FindingValidator findingValidator;

    public SmellAnalyzer(LlmClient llm,
            int maxFilesPerChunk,
            int maxChunkChars,
            Map<String, Object> llmOptions,
            boolean debug,
            Path llmOutputDir) {
        this(llm, maxFilesPerChunk, maxChunkChars, llmOptions, debug, llmOutputDir, AnalysisScope.DIFF,
                ValidationMode.defaultForScope(AnalysisScope.DIFF));
    }

    public SmellAnalyzer(LlmClient llm,
            int maxFilesPerChunk,
            int maxChunkChars,
            Map<String, Object> llmOptions,
            boolean debug,
            Path llmOutputDir,
            AnalysisScope scope) {
        this(llm, maxFilesPerChunk, maxChunkChars, llmOptions, debug, llmOutputDir, scope,
                ValidationMode.defaultForScope(scope == null ? AnalysisScope.DIFF : scope));
    }

    public SmellAnalyzer(LlmClient llm,
            int maxFilesPerChunk,
            int maxChunkChars,
            Map<String, Object> llmOptions,
            boolean debug,
            Path llmOutputDir,
            AnalysisScope scope,
            ValidationMode validationMode) {
        this(llm, maxFilesPerChunk, maxChunkChars, llmOptions, debug, llmOutputDir, scope, validationMode,
                debug ? llmOutputDir : null);
    }

    public SmellAnalyzer(LlmClient llm,
            int maxFilesPerChunk,
            int maxChunkChars,
            Map<String, Object> llmOptions,
            boolean debug,
            Path llmOutputDir,
            AnalysisScope scope,
            ValidationMode validationMode,
            Path tokenUsageOutputDir) {
        AnalysisScope safeScope = scope == null ? AnalysisScope.DIFF : scope;
        ValidationMode safeValidationMode = validationMode == null
                ? ValidationMode.defaultForScope(safeScope)
                : validationMode;
        this.llm = llm;
        this.maxFilesPerChunk = maxFilesPerChunk <= 0 ? 4 : maxFilesPerChunk;
        this.maxChunkChars = maxChunkChars <= 0 ? 18000 : maxChunkChars;
        this.llmOptions = llmOptions == null ? Map.of() : Map.copyOf(llmOptions);
        this.debug = debug;
        this.scope = safeScope;
        this.validationMode = safeValidationMode;
        this.promptRenderer = new PromptRenderer(safeScope, safeValidationMode);
        this.artifactWriter = new LlmArtifactWriter(debug ? llmOutputDir : null, tokenUsageOutputDir);
        this.findingValidator = new FindingValidator(new AddedLineParser(), safeScope, safeValidationMode, debug);
    }

    public AnalysisResult analyze(String repository, int prNumber, List<AnalyzedFile> files)
            throws IOException, InterruptedException {
        return analyze(repository, prNumber, files, AnalysisMetadata.empty());
    }

    public AnalysisResult analyze(String repository, int prNumber, List<AnalyzedFile> files, AnalysisMetadata metadata)
            throws IOException, InterruptedException {
        List<AnalyzedFile> javaFiles = files == null
                ? List.of()
                : files.stream()
                        .filter(f -> f.filename() != null && f.filename().endsWith(".java"))
                        .toList();

        if (javaFiles.isEmpty()) {
            return new AnalysisResult(List.of(), List.of(), 0);
        }

        List<List<AnalyzedFile>> chunks = chunkFiles(javaFiles);
        List<LlmFinding> allFindings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            List<AnalyzedFile> chunk = chunks.get(i);
            String chunkPrefix = "chunk-" + String.format("%03d", i + 1);
            String prompt = promptRenderer.renderChunk(
                    repository,
                    prNumber,
                    chunk,
                    i + 1,
                    chunks.size(),
                    javaFiles.size());

            // These files let a failed or surprising LLM result be reproduced without
            // scraping CI logs.
            artifactWriter.saveSystemPrompt(chunkPrefix, promptRenderer.systemPrompt());
            artifactWriter.saveUserPrompt(chunkPrefix, prompt);

            if (debug) {
                System.out.println("===== LLM CHUNK " + (i + 1) + "/" + chunks.size() + " PROMPT =====");
                System.out.println(prompt);
                System.out.println("===== END LLM CHUNK PROMPT =====");
            }

            LlmClient.Result result = llm.chat(
                    promptRenderer.systemPrompt(),
                    List.of(new LlmClient.Message("user", prompt)),
                    llmOptions);

            String rawResponse = result.text() == null ? "" : result.text();
            artifactWriter.saveRawResponse(chunkPrefix, rawResponse);

            String raw = rawResponse.trim();
            if (debug) {
                System.out.println("===== LLM CHUNK " + (i + 1) + "/" + chunks.size() + " RAW RESPONSE =====");
                System.out.println(raw);
                System.out.println("===== END LLM CHUNK RAW RESPONSE =====");
            }

            String json = stripBackticksIfAny(raw);
            boolean parseOk = false;
            int warningCount = 0;
            try {
                List<LlmFinding> findings = mapper.readValue(json, new TypeReference<List<LlmFinding>>() {
                });
                parseOk = true;
                allFindings.addAll(findingValidator.validateAndAnchor(findings, chunk));
            } catch (Exception parseEx) {
                String warning = "Chunk " + (i + 1) + "/" + chunks.size()
                        + " returned invalid JSON. Findings from this chunk were skipped.";
                warnings.add(warning);
                warningCount = 1;
                if (debug) {
                    System.out.println("===== LLM CHUNK PARSE ERROR =====");
                    System.out.println(warning);
                    System.out.println(parseEx.getMessage());
                    System.out.println(json);
                    System.out.println("===== END LLM CHUNK PARSE ERROR =====");
                }
            }

            artifactWriter.appendTokenUsage(tokenUsageEntry(
                    metadata,
                    i + 1,
                    chunk.size(),
                    prompt.length(),
                    result.usage(),
                    parseOk,
                    warningCount));
        }

        return new AnalysisResult(mergeDuplicates(allFindings), warnings, chunks.size());
    }

    private List<List<AnalyzedFile>> chunkFiles(List<AnalyzedFile> files) {
        List<List<AnalyzedFile>> chunks = new ArrayList<>();
        List<AnalyzedFile> current = new ArrayList<>();
        int currentChars = 0;

        for (AnalyzedFile file : files) {
            String block = promptRenderer.renderFileBlock(file);
            int blockChars = block.length();
            boolean wouldOverflow = !current.isEmpty()
                    && (current.size() >= maxFilesPerChunk || currentChars + blockChars > maxChunkChars);

            if (wouldOverflow) {
                chunks.add(current);
                current = new ArrayList<>();
                currentChars = 0;
            }

            current.add(file);
            currentChars += blockChars;
        }

        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private TokenUsageEntry tokenUsageEntry(
            AnalysisMetadata metadata,
            int chunkIndex,
            int filesInChunk,
            int promptChars,
            LlmClient.Usage usage,
            boolean parseOk,
            int warningCount) {
        AnalysisMetadata safeMetadata = metadata == null ? AnalysisMetadata.empty() : metadata;
        return new TokenUsageEntry(
                nvl(safeMetadata.dataset(), ""),
                nvl(safeMetadata.caseId(), ""),
                enumName(scope),
                enumName(validationMode),
                metadataName(safeMetadata.matchMode()),
                llm == null ? "" : nvl(llm.modelName(), ""),
                maxFilesPerChunk,
                maxChunkChars,
                safeMetadata.maxFileContentChars(),
                chunkIndex,
                filesInChunk,
                promptChars,
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.outputTokens(),
                usage == null ? null : usage.totalTokens(),
                usage == null ? null : usage.finishReason(),
                parseOk,
                warningCount);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? "" : value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String metadataName(String value) {
        return nvl(value, "").trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private List<LlmFinding> mergeDuplicates(List<LlmFinding> findings) {
        Map<String, LlmFinding> deduped = new LinkedHashMap<>();
        for (LlmFinding finding : findings) {
            String key = nvl(finding.file(), "?")
                    + "#"
                    + findingIdentity(finding)
                    + "#"
                    + nvl(finding.rule(), "?");
            deduped.putIfAbsent(key, finding);
        }
        return new ArrayList<>(deduped.values());
    }

    private static String findingIdentity(LlmFinding finding) {
        if (!isBlank(finding.targetType()) && !isBlank(finding.targetName())) {
            return nvl(finding.targetType(), "?").toUpperCase()
                    + "#"
                    + finding.targetName().trim();
        }
        return String.valueOf(finding.line());
    }

    private static String stripBackticksIfAny(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed.trim();
    }

    private static String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
