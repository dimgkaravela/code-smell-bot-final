package dev.dimitra.bot.eval;

import dev.dimitra.bot.eval.EvaluationScorer.EvaluationReport;
import dev.dimitra.bot.eval.EvaluationScorer.MetricsSummary;
import dev.dimitra.bot.eval.EvaluationScorer.RuleMetrics;

import java.time.Instant;
import java.util.Locale;

final class EvaluationMarkdownRenderer {

    String render(EvaluationReport report) {
        return render(report, null);
    }

    String render(EvaluationReport report, EvaluationMetadata metadata) {
        MetricsSummary summary = report.summary();
        StringBuilder md = new StringBuilder();
        md.append("# Code Smell Bot Evaluation\n\n");
        md.append("- Generated: ").append(Instant.now()).append("\n");
        if (metadata != null) {
            md.append("- Dataset: ").append(metadata.datasetPath()).append("\n");
            md.append("- Dataset name: ").append(metadata.datasetName()).append("\n");
            md.append("- Analysis scope: ").append(metadata.analysisScope()).append("\n");
            md.append("- Analyzer validation mode: ").append(metadata.validationMode()).append("\n");
            md.append("- Evaluation match mode: ").append(metadata.matchMode()).append("\n");
            md.append("- Line tolerance: ").append(metadata.lineTolerance()).append("\n");
            md.append("- Score only annotated rules: ").append(metadata.scoreOnlyAnnotatedRules()).append("\n");
            md.append("- Prediction source: ").append(metadata.predictionSource()).append("\n");
            if (metadata.predictionsPath() != null && !metadata.predictionsPath().isBlank()) {
                md.append("- Predictions file: ").append(metadata.predictionsPath()).append("\n");
            }
        }
        md.append("- Cases: ").append(summary.cases()).append("\n");
        md.append("- Detection labels: ").append(summary.labels()).append("\n");
        md.append("- Unsupported labels skipped: ").append(summary.unsupportedLabels()).append("\n");
        md.append("- Detection predictions: ").append(summary.predictions()).append("\n");
        md.append("- Detection true positives: ").append(summary.truePositives()).append("\n");
        md.append("- Detection false positives: ").append(summary.falsePositives()).append("\n");
        md.append("- Detection false negatives: ").append(summary.falseNegatives()).append("\n");
        md.append("- Detection precision: ").append(percent(summary.precision())).append("\n");
        md.append("- Detection recall: ").append(percent(summary.recall())).append("\n");
        md.append("- Detection F1: ").append(percent(summary.f1())).append("\n");
        md.append("- Refactoring labels: ").append(summary.refactoring().labels()).append("\n");
        md.append("- Refactoring predictions: ").append(summary.refactoring().predictions()).append("\n");
        md.append("- Refactoring true positives: ").append(summary.refactoring().truePositives()).append("\n");
        md.append("- Refactoring false positives: ").append(summary.refactoring().falsePositives()).append("\n");
        md.append("- Refactoring false negatives: ").append(summary.refactoring().falseNegatives()).append("\n");
        md.append("- Refactoring precision: ").append(percent(summary.refactoring().precision())).append("\n");
        md.append("- Refactoring recall: ").append(percent(summary.refactoring().recall())).append("\n");
        md.append("- Refactoring F1: ").append(percent(summary.refactoring().f1())).append("\n\n");

        if (!summary.byRule().isEmpty()) {
            md.append("## By Rule\n\n");
            md.append("| Rule | Labels | Predictions | TP | FP | FN | Precision | Recall | F1 |\n");
            md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            for (RuleMetrics metrics : summary.byRule().values()) {
                md.append("| ").append(metrics.rule())
                        .append(" | ").append(metrics.labels())
                        .append(" | ").append(metrics.predictions())
                        .append(" | ").append(metrics.truePositives())
                        .append(" | ").append(metrics.falsePositives())
                        .append(" | ").append(metrics.falseNegatives())
                        .append(" | ").append(percent(metrics.precision()))
                        .append(" | ").append(percent(metrics.recall()))
                        .append(" | ").append(percent(metrics.f1()))
                        .append(" |\n");
            }
            md.append("\n");
        }

        if (!summary.byRefactoring().isEmpty()) {
            md.append("## By Refactoring\n\n");
            md.append("| Refactoring | Labels | Predictions | TP | FP | FN | Precision | Recall | F1 |\n");
            md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            for (RuleMetrics metrics : summary.byRefactoring().values()) {
                md.append("| ").append(metrics.rule())
                        .append(" | ").append(metrics.labels())
                        .append(" | ").append(metrics.predictions())
                        .append(" | ").append(metrics.truePositives())
                        .append(" | ").append(metrics.falsePositives())
                        .append(" | ").append(metrics.falseNegatives())
                        .append(" | ").append(percent(metrics.precision()))
                        .append(" | ").append(percent(metrics.recall()))
                        .append(" | ").append(percent(metrics.f1()))
                        .append(" |\n");
            }
            md.append("\n");
        }

        md.append("## Cases\n\n");
        md.append("| Case | Det Labels | Det Pred | Det TP | Det FP | Det FN | Det F1 | Ref Labels | Ref Pred | Ref TP | Ref FP | Ref FN | Ref F1 |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (EvaluationScorer.CaseScore score : report.cases()) {
            md.append("| ").append(score.caseId())
                    .append(" | ").append(score.metrics().labels())
                    .append(" | ").append(score.metrics().predictions())
                    .append(" | ").append(score.metrics().truePositives())
                    .append(" | ").append(score.metrics().falsePositives())
                    .append(" | ").append(score.metrics().falseNegatives())
                    .append(" | ").append(percent(score.metrics().f1()))
                    .append(" | ").append(score.refactoringMetrics().labels())
                    .append(" | ").append(score.refactoringMetrics().predictions())
                    .append(" | ").append(score.refactoringMetrics().truePositives())
                    .append(" | ").append(score.refactoringMetrics().falsePositives())
                    .append(" | ").append(score.refactoringMetrics().falseNegatives())
                    .append(" | ").append(percent(score.refactoringMetrics().f1()))
                    .append(" |\n");
        }

        return md.toString();
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }
}
