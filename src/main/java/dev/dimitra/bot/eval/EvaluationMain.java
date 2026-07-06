package dev.dimitra.bot.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.dimitra.bot.Env;
import dev.dimitra.bot.analysis.SmellAnalyzer;
import dev.dimitra.bot.eval.EvaluationDatasetLoader.LoadedCase;
import dev.dimitra.bot.eval.EvaluationScorer.CasePrediction;
import dev.dimitra.bot.eval.EvaluationScorer.EvaluationReport;
import dev.dimitra.bot.eval.EvaluationScorer.MetricsSummary;
import dev.dimitra.bot.llm.LlmClient;
import dev.dimitra.bot.llm.LlmFinding;
import dev.dimitra.bot.llm.LlmRouter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class EvaluationMain {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private EvaluationMain() {
    }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        EvaluationOptions options = EvaluationOptions.parse(args);
        List<LoadedCase> cases = new EvaluationDatasetLoader(options.maxFileContentChars()).load(options.datasetDir());
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("No evaluation cases found under " + options.datasetDir());
        }

        if (options.dryRun()) {
            System.out.println("Loaded " + cases.size() + " evaluation case(s) from "
                    + options.datasetDir().toAbsolutePath());
            System.out.println("Max file content chars: " + options.maxFileContentChars());
            System.out.println("Truncated primary files: " + countTruncatedPrimaryFiles(cases));
            return;
        }

        boolean debug = Env.getBoolean("DEBUG_SMELLS", false);
        prepareOutputDir(options.outputDir(), debug);

        String predictionSource = options.predictionsPath() == null ? "llm" : "predictions-file";
        if (options.predictionsPath() != null) {
            System.out.println("Using predictions file; --validation-mode is recorded only and does not revalidate "
                    + "those predictions. Scoring uses --match-mode "
                    + options.matchMode().name().toLowerCase(Locale.ROOT) + ".");
        }
        List<PredictionEntry> predictionEntries = options.predictionsPath() == null
                ? runAnalyzer(cases, options)
                : readPredictions(options.predictionsPath());
        predictionEntries = dedupePredictionEntries(predictionEntries, options.matchMode());
        Set<String> annotatedRules = options.scoreOnlyAnnotatedRules()
                ? annotatedDetectionRules(cases)
                : Set.of();
        List<PredictionEntry> scoredPredictionEntries = options.scoreOnlyAnnotatedRules()
                ? filterPredictionEntriesByRules(predictionEntries, annotatedRules)
                : predictionEntries;
        EvaluationMetadata metadata = metadata(options, predictionSource);

        Map<String, PredictionEntry> predictionsByCase = new LinkedHashMap<>();
        for (PredictionEntry entry : scoredPredictionEntries) {
            predictionsByCase.put(entry.caseId(), entry);
        }

        List<CasePrediction> casePredictions = new ArrayList<>();
        for (LoadedCase loadedCase : cases) {
            String caseId = caseId(loadedCase);
            PredictionEntry prediction = predictionsByCase.get(caseId);
            List<EvaluationCase.EvaluationLabel> labels = labelsOf(loadedCase);
            casePredictions.add(new CasePrediction(
                    caseId,
                    loadedCase.path(),
                    labels,
                    prediction == null ? List.of() : safeFindings(prediction.findings()),
                    prediction == null ? List.of("No predictions supplied for this case.") : safeStrings(prediction.warnings()),
                    resolveEvaluateDetection(loadedCase.evaluationCase(), labels, options),
                    resolveEvaluateRefactoring(loadedCase.evaluationCase(), labels, options)));
        }

        EvaluationReport report = new EvaluationScorer().score(
                casePredictions,
                options.lineTolerance(),
                options.matchMode());
        writeOutputs(options.outputDir(), predictionEntries, casePredictions, report, metadata, debug);
        printSummary(report.summary(), options.outputDir(), metadata);
    }

    private static List<PredictionEntry> runAnalyzer(List<LoadedCase> cases, EvaluationOptions options)
            throws IOException, InterruptedException {
        LlmClient llm = LlmRouter.fromEnv();
        boolean debug = Env.getBoolean("DEBUG_SMELLS", false);
        List<PredictionEntry> predictions = new ArrayList<>();

        for (LoadedCase loadedCase : cases) {
            String caseId = caseId(loadedCase);
            Path caseOutputDir = debug
                    ? options.outputDir().resolve("debug").resolve("llm").resolve(sanitize(caseId))
                    : null;
            SmellAnalyzer analyzer = new SmellAnalyzer(
                    llm,
                    Env.getInt("LLM_MAX_FILES_PER_CHUNK", 4),
                    Env.getInt("LLM_MAX_PATCH_CHARS", 18000),
                    buildLlmOptionsFromEnv(),
                    debug,
                    caseOutputDir,
                    options.analysisScope(),
                    options.validationMode(),
                    options.outputDir());

            SmellAnalyzer.AnalysisResult result = analyzer.analyze(
                    firstNonBlank(loadedCase.evaluationCase().repository(), "evaluation/local"),
                    loadedCase.evaluationCase().prNumber() == null ? 1 : loadedCase.evaluationCase().prNumber(),
                    loadedCase.analyzedFiles(),
                    new SmellAnalyzer.AnalysisMetadata(
                            datasetName(options.datasetDir()),
                            caseId,
                            options.matchMode().name(),
                            options.maxFileContentChars()));

            predictions.add(new PredictionEntry(
                    caseId,
                    result.findings(),
                    result.warnings(),
                    result.chunksAnalyzed()));
        }

        return predictions;
    }

    private static List<PredictionEntry> readPredictions(Path predictionsPath) throws IOException {
        return MAPPER.readValue(predictionsPath.toFile(), new TypeReference<List<PredictionEntry>>() {
        });
    }

    static List<PredictionEntry> dedupePredictionEntries(
            List<PredictionEntry> entries,
            EvaluationMatchMode matchMode) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        EvaluationMatchMode safeMatchMode = matchMode == null ? EvaluationMatchMode.LINE : matchMode;
        List<PredictionEntry> dedupedEntries = new ArrayList<>();
        for (PredictionEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            dedupedEntries.add(new PredictionEntry(
                    entry.caseId(),
                    dedupeFindings(entry.findings(), safeMatchMode),
                    entry.warnings(),
                    entry.chunksAnalyzed()));
        }
        return dedupedEntries;
    }

    private static List<LlmFinding> dedupeFindings(List<LlmFinding> findings, EvaluationMatchMode matchMode) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        Map<String, LlmFinding> deduped = new LinkedHashMap<>();
        for (LlmFinding finding : findings) {
            if (finding == null) {
                continue;
            }
            deduped.putIfAbsent(predictionIdentity(finding, matchMode), finding);
        }
        return new ArrayList<>(deduped.values());
    }

    private static String predictionIdentity(LlmFinding finding, EvaluationMatchMode matchMode) {
        return switch (matchMode) {
            case TARGET -> targetPredictionIdentity(finding);
            case HYBRID -> hasTargetMetadata(finding)
                    ? targetPredictionIdentity(finding)
                    : linePredictionIdentity(finding);
            case LINE -> linePredictionIdentity(finding);
        };
    }

    private static String linePredictionIdentity(LlmFinding finding) {
        return normalizePath(finding.file())
                + "#"
                + canonicalRuleOrOriginal(finding.rule())
                + "#"
                + finding.line();
    }

    private static String targetPredictionIdentity(LlmFinding finding) {
        return normalizePath(finding.file())
                + "#"
                + canonicalRuleOrOriginal(finding.rule())
                + "#"
                + firstNonBlank(finding.targetType(), "?").trim().toUpperCase(Locale.ROOT)
                + "#"
                + firstNonBlank(finding.targetName(), "?").trim();
    }

    private static EvaluationMetadata metadata(EvaluationOptions options, String predictionSource) {
        return new EvaluationMetadata(
                options.datasetDir().toAbsolutePath().normalize().toString(),
                datasetName(options.datasetDir()),
                options.analysisScope().name(),
                options.validationMode().name(),
                options.matchMode().name(),
                options.lineTolerance(),
                options.scoreOnlyAnnotatedRules(),
                predictionSource,
                options.predictionsPath() == null ? null : options.predictionsPath().toString());
    }

    private static Set<String> annotatedDetectionRules(List<LoadedCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return Set.of();
        }

        Set<String> rules = new LinkedHashSet<>();
        for (LoadedCase loadedCase : cases) {
            for (EvaluationCase.EvaluationLabel label : labelsOf(loadedCase)) {
                String canonicalRule = EvaluationScorer.canonicalRule(label.rule());
                if (canonicalRule != null) {
                    rules.add(canonicalRule);
                }
            }
        }
        return rules;
    }

    static List<PredictionEntry> filterPredictionEntriesByRules(
            List<PredictionEntry> entries,
            Set<String> annotatedRules) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        Set<String> safeAnnotatedRules = annotatedRules == null ? Set.of() : annotatedRules;
        List<PredictionEntry> filteredEntries = new ArrayList<>();
        for (PredictionEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            filteredEntries.add(new PredictionEntry(
                    entry.caseId(),
                    filterFindingsByRules(entry.findings(), safeAnnotatedRules),
                    entry.warnings(),
                    entry.chunksAnalyzed()));
        }
        return filteredEntries;
    }

    private static List<LlmFinding> filterFindingsByRules(
            List<LlmFinding> findings,
            Set<String> annotatedRules) {
        if (findings == null || findings.isEmpty() || annotatedRules.isEmpty()) {
            return List.of();
        }
        return findings.stream()
                .filter(finding -> {
                    if (finding == null) {
                        return false;
                    }
                    String canonicalRule = EvaluationScorer.canonicalRule(finding.rule());
                    return canonicalRule != null && annotatedRules.contains(canonicalRule);
                })
                .toList();
    }

    private static void writeOutputs(Path outputDir,
            List<PredictionEntry> predictions,
            List<CasePrediction> casePredictions,
            EvaluationReport report,
            EvaluationMetadata metadata,
            boolean debug) throws IOException {
        ObjectWriter pretty = MAPPER.writerWithDefaultPrettyPrinter();
        Files.writeString(outputDir.resolve("summary.json"),
                pretty.writeValueAsString(new EvaluationSummaryOutput(metadata, report.summary())));
        Files.writeString(outputDir.resolve("predictions.json"), pretty.writeValueAsString(predictions));
        Files.writeString(outputDir.resolve("report.md"), new EvaluationMarkdownRenderer().render(report, metadata));
        if (debug) {
            Path debugDir = outputDir.resolve("debug");
            Files.createDirectories(debugDir);
            Files.writeString(debugDir.resolve("cases.json"), pretty.writeValueAsString(caseOutputs(casePredictions, report)));
        }
    }

    private static void prepareOutputDir(Path outputDir, boolean debug) throws IOException {
        Files.createDirectories(outputDir);
        deleteIfExists(outputDir.resolve("metrics.json"));
        deleteIfExists(outputDir.resolve("dataset_preview.json"));
        deleteIfExists(outputDir.resolve("cases.json"));
        deleteIfExists(outputDir.resolve("token-usage.csv"));
        if (!debug) {
            deleteRecursivelyIfExists(outputDir.resolve("debug"), outputDir);
        }
    }

    private static void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static void deleteRecursivelyIfExists(Path path, Path allowedRoot) throws IOException {
        Path normalizedRoot = allowedRoot.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot) || !Files.exists(normalizedPath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(normalizedPath)) {
            List<Path> pathsToDelete = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path pathToDelete : pathsToDelete) {
                Files.deleteIfExists(pathToDelete);
            }
        }
    }

    private static List<CaseOutput> caseOutputs(List<CasePrediction> casePredictions, EvaluationReport report) {
        Map<String, CasePrediction> predictionsByCase = new LinkedHashMap<>();
        for (CasePrediction prediction : casePredictions) {
            predictionsByCase.put(prediction.caseId(), prediction);
        }

        return report.cases().stream()
                .map(score -> {
                    CasePrediction prediction = predictionsByCase.get(score.caseId());
                    List<EvaluationCase.EvaluationLabel> expectedLabels = prediction == null
                            ? List.of()
                            : prediction.labels();
                    return new CaseOutput(
                            score.caseId(),
                            score.path(),
                            expectedLabels,
                            score.metrics(),
                            score.refactoringMetrics(),
                            score.matches(),
                            score.falsePositives(),
                            score.falseNegatives(),
                            score.refactoringMatches(),
                            score.refactoringFalsePositives(),
                            score.refactoringFalseNegatives(),
                            score.unsupportedLabels(),
                            score.warnings());
                })
                .toList();
    }

    private static void printSummary(MetricsSummary summary, Path outputDir, EvaluationMetadata metadata) {
        System.out.println("Code Smell Bot Evaluation");
        System.out.println("Dataset: " + metadata.datasetPath());
        System.out.println("Analysis scope: " + metadata.analysisScope());
        System.out.println("Validation mode: " + metadata.validationMode());
        System.out.println("Match mode: " + metadata.matchMode());
        System.out.println("Line tolerance: " + metadata.lineTolerance());
        System.out.println("Score only annotated rules: " + metadata.scoreOnlyAnnotatedRules());
        System.out.println("Prediction source: " + metadata.predictionSource());
        System.out.println("Cases: " + summary.cases());
        System.out.println("Detection labels: " + summary.labels());
        System.out.println("Unsupported labels skipped: " + summary.unsupportedLabels());
        System.out.println("Detection predictions: " + summary.predictions());
        System.out.println("Detection precision: " + percent(summary.precision()));
        System.out.println("Detection recall: " + percent(summary.recall()));
        System.out.println("Detection F1: " + percent(summary.f1()));
        System.out.println("Refactoring labels: " + summary.refactoring().labels());
        System.out.println("Refactoring predictions: " + summary.refactoring().predictions());
        System.out.println("Refactoring precision: " + percent(summary.refactoring().precision()));
        System.out.println("Refactoring recall: " + percent(summary.refactoring().recall()));
        System.out.println("Refactoring F1: " + percent(summary.refactoring().f1()));
        System.out.println("Artifacts: " + outputDir.toAbsolutePath());
    }

    private static List<EvaluationCase.EvaluationLabel> labelsOf(LoadedCase loadedCase) {
        return loadedCase.evaluationCase().labels() == null
                ? List.of()
                : loadedCase.evaluationCase().labels();
    }

    private static long countTruncatedPrimaryFiles(List<LoadedCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return 0;
        }
        return cases.stream()
                .flatMap(loadedCase -> loadedCase.analyzedFiles().stream())
                .filter(file -> file.fileContent() != null && file.fileContent().contains("... [truncated]"))
                .count();
    }

    private static List<LlmFinding> safeFindings(List<LlmFinding> findings) {
        return findings == null ? List.of() : findings;
    }

    private static List<String> safeStrings(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static boolean resolveEvaluateDetection(
            EvaluationCase evaluationCase,
            List<EvaluationCase.EvaluationLabel> labels,
            EvaluationOptions options) {
        if (evaluationCase.evaluateDetection() != null) {
            return evaluationCase.evaluateDetection();
        }
        return switch (options.evaluateDetection()) {
            case TRUE -> true;
            case FALSE -> false;
            case AUTO -> hasValidDetectionLabel(labels);
        };
    }

    private static boolean resolveEvaluateRefactoring(
            EvaluationCase evaluationCase,
            List<EvaluationCase.EvaluationLabel> labels,
            EvaluationOptions options) {
        if (evaluationCase.evaluateRefactoring() != null) {
            return evaluationCase.evaluateRefactoring();
        }
        return switch (options.evaluateRefactoring()) {
            case TRUE -> true;
            case FALSE -> false;
            case AUTO -> hasRefactoringLabel(labels);
        };
    }

    private static boolean hasValidDetectionLabel(List<EvaluationCase.EvaluationLabel> labels) {
        return labels != null
                && labels.stream().anyMatch(label -> EvaluationScorer.canonicalRule(label.rule()) != null);
    }

    private static boolean hasRefactoringLabel(List<EvaluationCase.EvaluationLabel> labels) {
        return labels != null
                && labels.stream().anyMatch(label -> !isBlank(label.suggestedRefactoring()));
    }

    private static String caseId(LoadedCase loadedCase) {
        return firstNonBlank(loadedCase.evaluationCase().id(), stripJsonExtension(loadedCase.path().getFileName().toString()));
    }

    private static String datasetName(Path datasetDir) {
        Path filename = datasetDir.normalize().getFileName();
        return sanitize(filename == null ? "dataset" : filename.toString());
    }

    private static String stripJsonExtension(String filename) {
        return filename.endsWith(".json") ? filename.substring(0, filename.length() - ".json".length()) : filename;
    }

    private static String canonicalRuleOrOriginal(String rule) {
        String canonical = EvaluationScorer.canonicalRule(rule);
        return canonical == null ? firstNonBlank(rule, "?") : canonical;
    }

    private static String normalizePath(String path) {
        String normalized = firstNonBlank(path, "").replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static boolean hasTargetMetadata(LlmFinding finding) {
        return finding != null
                && !isBlank(finding.targetType())
                && !isBlank(finding.targetName());
    }

    private static Map<String, Object> buildLlmOptionsFromEnv() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", Env.getDouble("LLM_TEMPERATURE", 0.0));
        options.put("max_tokens", Env.getInt("LLM_MAX_TOKENS", 1200));
        return options;
    }

    private static String sanitize(String value) {
        String sanitized = firstNonBlank(value, "case")
                .replaceAll("[^A-Za-z0-9_.-]+", "-");
        return sanitized.isBlank() ? "case" : sanitized;
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PredictionEntry(
            String caseId,
            List<LlmFinding> findings,
            List<String> warnings,
            Integer chunksAnalyzed) {
    }

    private record CaseOutput(
            String caseId,
            String path,
            List<EvaluationCase.EvaluationLabel> expectedLabels,
            EvaluationScorer.SummaryMetrics metrics,
            EvaluationScorer.SummaryMetrics refactoringMetrics,
            List<EvaluationScorer.Match> matches,
            List<LlmFinding> falsePositives,
            List<EvaluationCase.EvaluationLabel> falseNegatives,
            List<EvaluationScorer.RefactoringMatch> refactoringMatches,
            List<LlmFinding> refactoringFalsePositives,
            List<EvaluationCase.EvaluationLabel> refactoringFalseNegatives,
            List<EvaluationCase.EvaluationLabel> unsupportedLabels,
            List<String> warnings) {
    }

}
