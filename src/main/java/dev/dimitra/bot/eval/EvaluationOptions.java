package dev.dimitra.bot.eval;

import dev.dimitra.bot.Env;
import dev.dimitra.bot.analysis.AnalysisScope;
import dev.dimitra.bot.analysis.ValidationMode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

record EvaluationOptions(
        Path datasetDir,
        Path outputDir,
        Path predictionsPath,
        int lineTolerance,
        AnalysisScope analysisScope,
        ValidationMode validationMode,
        EvaluationMatchMode matchMode,
        EvaluationSwitch evaluateDetection,
        EvaluationSwitch evaluateRefactoring,
        boolean scoreOnlyAnnotatedRules,
        int maxFileContentChars,
        boolean dryRun) {

    static EvaluationOptions parse(String[] args) {
        return parse(args, System.getenv());
    }

    static EvaluationOptions parse(String[] args, Map<String, String> processEnv) {
        Map<String, String> flags = parseFlags(args);
        Path datasetDir = Paths.get(firstNonBlank(
                flags.get("dataset"),
                Env.get("EVAL_DATASET_DIR", "evaluation-dataset/micro")));
        Path predictionsPath = flags.containsKey("predictions")
                ? Paths.get(flags.get("predictions"))
                : null;
        int lineTolerance = intFlag(flags, "line-tolerance", Env.getInt("EVAL_LINE_TOLERANCE", 3));
        AnalysisScope analysisScope = AnalysisScope.parse(firstNonBlank(
                flags.get("scope"),
                Env.get("EVAL_ANALYSIS_SCOPE", "file")));
        ValidationMode validationMode = ValidationMode.parse(firstNonBlank(
                flags.get("validation-mode"),
                Env.get("EVAL_VALIDATION_MODE")),
                analysisScope);
        EvaluationMatchMode matchMode = EvaluationMatchMode.parse(firstNonBlank(
                flags.get("match-mode"),
                Env.get("EVAL_MATCH_MODE")),
                validationMode);
        EvaluationSwitch evaluateDetection = EvaluationSwitch.parse(firstNonBlank(
                flags.get("evaluate-detection"),
                Env.get("EVAL_EVALUATE_DETECTION", "auto")),
                "evaluate-detection");
        EvaluationSwitch evaluateRefactoring = EvaluationSwitch.parse(firstNonBlank(
                flags.get("evaluate-refactoring"),
                Env.get("EVAL_EVALUATE_REFACTORING", "auto")),
                "evaluate-refactoring");
        boolean scoreOnlyAnnotatedRules = booleanFlag(
                flags,
                "score-only-annotated-rules",
                Env.getBoolean("EVAL_SCORE_ONLY_ANNOTATED_RULES", false));
        int maxFileContentChars = maxFileContentChars(flags, processEnv);
        Path outputDir = outputDir(flags, datasetDir, analysisScope);
        boolean dryRun = flags.containsKey("dry-run");
        return new EvaluationOptions(
                datasetDir,
                outputDir,
                predictionsPath,
                lineTolerance,
                analysisScope,
                validationMode,
                matchMode,
                evaluateDetection,
                evaluateRefactoring,
                scoreOnlyAnnotatedRules,
                maxFileContentChars,
                dryRun);
    }

    private static Path outputDir(Map<String, String> flags, Path datasetDir, AnalysisScope analysisScope) {
        if (flags.containsKey("out")) {
            return Paths.get(flags.get("out"));
        }

        String envOutputDir = Env.get("EVAL_OUTPUT_DIR");
        if (envOutputDir != null && !envOutputDir.isBlank()) {
            return Paths.get(envOutputDir);
        }

        return Paths.get("out", "evaluation", datasetName(datasetDir) + "-" + scopeName(analysisScope));
    }

    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }

            String key = arg.substring(2);
            if ("dry-run".equals(key)) {
                flags.put(key, "true");
                continue;
            }

            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            flags.put(key, args[++i]);
        }
        return flags;
    }

    private static int intFlag(Map<String, String> flags, String key, int fallback) {
        if (!flags.containsKey(key)) {
            return fallback;
        }
        try {
            return Integer.parseInt(flags.get(key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + key + " must be an integer");
        }
    }

    private static boolean booleanFlag(Map<String, String> flags, String key, boolean fallback) {
        if (!flags.containsKey(key)) {
            return fallback;
        }
        String value = firstNonBlank(flags.get(key), "true").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> throw new IllegalArgumentException("--" + key + " must be true or false");
        };
    }

    private static int maxFileContentChars(Map<String, String> flags, Map<String, String> processEnv) {
        if (flags.containsKey("max-file-content-chars")) {
            return intFlag(flags, "max-file-content-chars", 0);
        }
        Integer evalValue = intEnv(processEnv, "EVAL_MAX_FILE_CONTENT_CHARS");
        if (evalValue != null) {
            return evalValue;
        }
        Integer sharedValue = intEnv(processEnv, "MAX_FILE_CONTENT_CHARS");
        if (sharedValue != null) {
            return sharedValue;
        }
        return Env.getInt("EVAL_MAX_FILE_CONTENT_CHARS", Env.getInt("MAX_FILE_CONTENT_CHARS", 0));
    }

    private static Integer intEnv(Map<String, String> processEnv, String key) {
        if (processEnv == null) {
            return null;
        }
        String value = processEnv.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }

    private static String datasetName(Path datasetDir) {
        Path filename = datasetDir.normalize().getFileName();
        return sanitize(filename == null ? "dataset" : filename.toString());
    }

    private static String scopeName(AnalysisScope analysisScope) {
        return analysisScope.name().toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String value) {
        String sanitized = firstNonBlank(value, "case")
                .replaceAll("[^A-Za-z0-9_.-]+", "-");
        return sanitized.isBlank() ? "case" : sanitized;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    enum EvaluationSwitch {
        TRUE,
        FALSE,
        AUTO;

        private static EvaluationSwitch parse(String rawValue, String optionName) {
            String value = firstNonBlank(rawValue, "auto").toLowerCase(Locale.ROOT);
            return switch (value) {
                case "true" -> TRUE;
                case "false" -> FALSE;
                case "auto" -> AUTO;
                default -> throw new IllegalArgumentException("--" + optionName + " must be true, false, or auto");
            };
        }
    }
}
