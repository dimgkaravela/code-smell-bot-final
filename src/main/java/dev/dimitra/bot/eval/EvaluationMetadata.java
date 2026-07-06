package dev.dimitra.bot.eval;

public record EvaluationMetadata(
        String datasetPath,
        String datasetName,
        String analysisScope,
        String validationMode,
        String matchMode,
        int lineTolerance,
        boolean scoreOnlyAnnotatedRules,
        String predictionSource,
        String predictionsPath) {
}
