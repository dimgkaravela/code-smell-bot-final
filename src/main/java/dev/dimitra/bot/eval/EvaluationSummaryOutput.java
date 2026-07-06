package dev.dimitra.bot.eval;

public record EvaluationSummaryOutput(
        EvaluationMetadata metadata,
        EvaluationScorer.MetricsSummary summary) {
}
