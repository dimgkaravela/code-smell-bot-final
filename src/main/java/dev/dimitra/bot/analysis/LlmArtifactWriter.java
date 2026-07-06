package dev.dimitra.bot.analysis;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

public class LlmArtifactWriter {
    private static final String TOKEN_USAGE_FILENAME = "token-usage.csv";
    private static final String TOKEN_USAGE_HEADER = String.join(",",
            "dataset",
            "caseId",
            "scope",
            "validationMode",
            "matchMode",
            "model",
            "maxFilesPerChunk",
            "maxChunkChars",
            "maxFileContentChars",
            "chunkIndex",
            "filesInChunk",
            "promptChars",
            "promptTokens",
            "outputTokens",
            "totalTokens",
            "finishReason",
            "parseOk",
            "warningCount");

    private final Path outputDir;
    private final Path tokenUsageOutputDir;

    public LlmArtifactWriter(Path outputDir) {
        this(outputDir, outputDir);
    }

    public LlmArtifactWriter(Path outputDir, Path tokenUsageOutputDir) {
        this.outputDir = outputDir;
        this.tokenUsageOutputDir = tokenUsageOutputDir;
    }

    public void saveSystemPrompt(String chunkPrefix, String systemPrompt) throws IOException {
        save(chunkPrefix + "-system-prompt.txt", systemPrompt);
    }

    public void saveUserPrompt(String chunkPrefix, String userPrompt) throws IOException {
        save(chunkPrefix + "-user-prompt.txt", userPrompt);
    }

    public void saveRawResponse(String chunkPrefix, String rawResponse) throws IOException {
        save(chunkPrefix + "-raw-response.txt", rawResponse);
    }

    public void appendTokenUsage(TokenUsageEntry entry) throws IOException {
        if (tokenUsageOutputDir == null || entry == null) {
            return;
        }

        Files.createDirectories(tokenUsageOutputDir);
        Path csv = tokenUsageOutputDir.resolve(TOKEN_USAGE_FILENAME);
        boolean writeHeader = Files.notExists(csv) || Files.size(csv) == 0;
        try (BufferedWriter writer = Files.newBufferedWriter(
                csv,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writer.write(TOKEN_USAGE_HEADER);
                writer.newLine();
            }
            writer.write(toCsvLine(entry));
            writer.newLine();
        }
    }

    private void save(String filename, String content) throws IOException {
        if (outputDir == null) {
            return;
        }
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve(filename), content == null ? "" : content);
    }

    private static String toCsvLine(TokenUsageEntry entry) {
        return String.join(",",
                csv(entry.dataset()),
                csv(entry.caseId()),
                csv(entry.scope()),
                csv(entry.validationMode()),
                csv(entry.matchMode()),
                csv(entry.model()),
                csv(entry.maxFilesPerChunk()),
                csv(entry.maxChunkChars()),
                csv(entry.maxFileContentChars()),
                csv(entry.chunkIndex()),
                csv(entry.filesInChunk()),
                csv(entry.promptChars()),
                csv(entry.promptTokens()),
                csv(entry.outputTokens()),
                csv(entry.totalTokens()),
                csv(entry.finishReason()),
                csv(entry.parseOk()),
                csv(entry.warningCount()));
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!needsEscaping(text)) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean needsEscaping(String value) {
        return value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
    }
}
