package dev.dimitra.bot.eval;

import dev.dimitra.bot.eval.EvaluationScorer.EvaluationReport;
import dev.dimitra.bot.eval.EvaluationScorer.CasePrediction;
import dev.dimitra.bot.llm.LlmFinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationScorerTest {
    @Test
    void exactMatchGivesTruePositive() {
        EvaluationReport report = score(oneCase(
                List.of(label("src/Foo.java", 12, "Long Method")),
                List.of(finding("src/Foo.java", 12, "Long Method"))), 0);

        assertEquals(1, report.summary().labels());
        assertEquals(1, report.summary().predictions());
        assertEquals(1, report.summary().truePositives());
        assertEquals(0, report.summary().falsePositives());
        assertEquals(0, report.summary().falseNegatives());
        assertEquals(1.0, report.summary().precision(), 0.0001);
        assertEquals(1.0, report.summary().recall(), 0.0001);
        assertEquals(1.0, report.summary().f1(), 0.0001);
        assertEquals(1, report.cases().get(0).matches().size());
    }

    @Test
    void ruleAliasMatchesCanonicalRule() {
        EvaluationReport report = score(oneCase(
                List.of(label("src/Foo.java", 12, "Duplicate Code Inside a Method")),
                List.of(finding("src/Foo.java", 12, "duplicate code"))), 0);

        assertEquals(1, report.summary().truePositives());
        EvaluationScorer.RuleMetrics metrics = report.summary().byRule().get("Duplicate Code");
        assertEquals(1, metrics.labels());
        assertEquals(1, metrics.predictions());
        assertEquals(1, metrics.truePositives());
    }

    @Test
    void lineToleranceAllowsNearbyLinesAndRejectsOutsideTolerance() {
        CasePrediction prediction = oneCase(
                List.of(label("src/Foo.java", 10, "Long Method")),
                List.of(finding("src/Foo.java", 12, "Long Method")));

        EvaluationReport withinTolerance = score(prediction, 2);
        EvaluationReport outsideTolerance = score(prediction, 1);

        assertEquals(1, withinTolerance.summary().truePositives());
        assertEquals(0, withinTolerance.summary().falsePositives());
        assertEquals(0, withinTolerance.summary().falseNegatives());

        assertEquals(0, outsideTolerance.summary().truePositives());
        assertEquals(1, outsideTolerance.summary().falsePositives());
        assertEquals(1, outsideTolerance.summary().falseNegatives());
    }

    @Test
    void targetModeMatchesByTargetIdentityAndIgnoresLineDistance() {
        EvaluationReport report = score(oneCase(
                List.of(targetLabel("src/Foo.java", 10, "Long Method", "METHOD", "longMethod")),
                List.of(targetFinding("src/Foo.java", 99, "Long Method", "METHOD", "longMethod"))),
                0,
                EvaluationMatchMode.TARGET);

        assertEquals(1, report.summary().truePositives());
        assertEquals(0, report.summary().falsePositives());
        assertEquals(0, report.summary().falseNegatives());
    }

    @Test
    void targetModeRejectsWrongTargetName() {
        EvaluationReport report = score(oneCase(
                List.of(targetLabel("src/Foo.java", 10, "Long Method", "METHOD", "longMethod")),
                List.of(targetFinding("src/Foo.java", 10, "Long Method", "METHOD", "otherMethod"))),
                0,
                EvaluationMatchMode.TARGET);

        assertEquals(0, report.summary().truePositives());
        assertEquals(1, report.summary().falsePositives());
        assertEquals(1, report.summary().falseNegatives());
    }

    @Test
    void targetModeFailsFastWhenLabelTargetMetadataIsMissing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> score(oneCase(
                List.of(label("src/Foo.java", 10, "Long Method")),
                List.of(targetFinding("src/Foo.java", 99, "Long Method", "METHOD", "longMethod"))),
                0,
                EvaluationMatchMode.TARGET));

        assertTrue(ex.getMessage().contains("--match-mode line"));
        assertTrue(ex.getMessage().contains("label"));
    }

    @Test
    void targetModeFailsFastWhenPredictionTargetMetadataIsMissing() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> score(oneCase(
                List.of(targetLabel("src/Foo.java", 10, "Long Method", "METHOD", "longMethod")),
                List.of(finding("src/Foo.java", 99, "Long Method"))),
                0,
                EvaluationMatchMode.TARGET));

        assertTrue(ex.getMessage().contains("--match-mode line"));
        assertTrue(ex.getMessage().contains("prediction"));
    }

    @Test
    void duplicatePredictionsDoNotMatchSameLabelTwice() {
        EvaluationReport report = score(oneCase(
                List.of(label("src/Foo.java", 12, "Long Method")),
                List.of(
                        finding("src/Foo.java", 12, "Long Method"),
                        finding("src/Foo.java", 12, "Long Method"))), 0);

        assertEquals(1, report.summary().truePositives());
        assertEquals(1, report.summary().falsePositives());
        assertEquals(0, report.summary().falseNegatives());
        assertEquals(1, report.cases().get(0).matches().size());
        assertEquals(1, report.cases().get(0).falsePositives().size());
    }

    @Test
    void externalPredictionsDedupeByLineIdentityInLineMode() {
        List<EvaluationMain.PredictionEntry> deduped = EvaluationMain.dedupePredictionEntries(
                List.of(new EvaluationMain.PredictionEntry(
                        "case-1",
                        List.of(
                                targetFinding("src/Foo.java", 12, "Long Method", "METHOD", "first"),
                                targetFinding("src/Foo.java", 12, "Long Method", "METHOD", "second")),
                        List.of(),
                        1)),
                EvaluationMatchMode.LINE);

        assertEquals(1, deduped.get(0).findings().size());
        assertEquals("first", deduped.get(0).findings().get(0).targetName());
    }

    @Test
    void externalPredictionsDedupeByTargetIdentityInTargetMode() {
        List<EvaluationMain.PredictionEntry> deduped = EvaluationMain.dedupePredictionEntries(
                List.of(new EvaluationMain.PredictionEntry(
                        "case-1",
                        List.of(
                                targetFinding("src/Foo.java", 12, "Long Method", "METHOD", "longMethod"),
                                targetFinding("src/Foo.java", 99, "Long Method", "METHOD", "longMethod"),
                                targetFinding("src/Foo.java", 99, "Long Method", "METHOD", "otherMethod")),
                        List.of(),
                        1)),
                EvaluationMatchMode.TARGET);

        assertEquals(2, deduped.get(0).findings().size());
        assertEquals(12, deduped.get(0).findings().get(0).line());
        assertEquals("otherMethod", deduped.get(0).findings().get(1).targetName());
    }

    @Test
    void annotatedRuleFilterKeepsOnlyDatasetAnnotatedRules() {
        List<EvaluationMain.PredictionEntry> filtered = EvaluationMain.filterPredictionEntriesByRules(
                List.of(new EvaluationMain.PredictionEntry(
                        "case-1",
                        List.of(
                                finding("src/Foo.java", 12, "Long Method"),
                                finding("src/Foo.java", 20, "Large Class"),
                                finding("src/Foo.java", 30, "Unknown Rule")),
                        List.of("warning"),
                        1)),
                Set.of("Long Method"));

        assertEquals(1, filtered.size());
        assertEquals(1, filtered.get(0).findings().size());
        assertEquals("Long Method", filtered.get(0).findings().get(0).rule());
        assertEquals(List.of("warning"), filtered.get(0).warnings());
        assertEquals(1, filtered.get(0).chunksAnalyzed());
    }

    @Test
    void scoreOnlyAnnotatedRulesOptionParsesBooleanFlag() {
        EvaluationOptions options = EvaluationOptions.parse(new String[] {
                "--score-only-annotated-rules", "true"
        });

        assertTrue(options.scoreOnlyAnnotatedRules());
    }

    @Test
    void maxFileContentCharsOptionParsesIntegerFlag() {
        EvaluationOptions options = EvaluationOptions.parse(new String[] {
                "--max-file-content-chars", "25"
        });

        assertEquals(25, options.maxFileContentChars());
    }

    @Test
    void autoPreservesRefactoringOnlyBehavior() {
        EvaluationReport report = score(oneCase(
                List.of(refactoringLabel("src/Foo.java", 12, "Extract Class")),
                List.of(finding("src/Foo.java", 12, "Large Class", "ExtractClass"))), 0);

        assertEquals(0, report.summary().labels());
        assertEquals(0, report.summary().predictions());
        assertEquals(0, report.summary().falsePositives());
        assertEquals(1, report.summary().refactoring().labels());
        assertEquals(1, report.summary().refactoring().predictions());
        assertEquals(1, report.summary().refactoring().truePositives());
        assertEquals(0, report.summary().refactoring().falsePositives());
        assertEquals(0, report.summary().refactoring().falseNegatives());
        assertEquals(1, report.cases().get(0).refactoringMatches().size());
    }

    @Test
    void autoPreservesDetectionOnlyBehavior() {
        EvaluationReport report = score(oneCase(
                List.of(label("src/Foo.java", 12, "Long Method")),
                List.of(finding("src/Foo.java", 12, "Long Method", "Extract Method"))), 0);

        assertEquals(1, report.summary().truePositives());
        assertEquals(0, report.summary().refactoring().labels());
        assertEquals(0, report.summary().refactoring().predictions());
        assertEquals(0, report.cases().get(0).refactoringMatches().size());
    }

    @Test
    void explicitCleanDetectionCaseCountsPredictedSmellAsFalsePositive() {
        EvaluationReport report = score(oneCase(
                List.of(),
                List.of(finding("src/Foo.java", 12, "Long Method")),
                true,
                false), 0);

        assertEquals(0, report.summary().labels());
        assertEquals(1, report.summary().predictions());
        assertEquals(0, report.summary().truePositives());
        assertEquals(1, report.summary().falsePositives());
        assertEquals(0, report.summary().falseNegatives());
        assertEquals(0, report.summary().refactoring().predictions());
    }

    @Test
    void explicitCleanRefactoringCaseCountsPredictedRefactoringAsFalsePositive() {
        EvaluationReport report = score(oneCase(
                List.of(),
                List.of(finding("src/Foo.java", 12, "Long Method", "Extract Method")),
                false,
                true), 0);

        assertEquals(0, report.summary().predictions());
        assertEquals(0, report.summary().refactoring().labels());
        assertEquals(1, report.summary().refactoring().predictions());
        assertEquals(0, report.summary().refactoring().truePositives());
        assertEquals(1, report.summary().refactoring().falsePositives());
        assertEquals(0, report.summary().refactoring().falseNegatives());
    }

    @Test
    void explicitFalseDisablesScoringEvenWhenLabelsExist() {
        EvaluationReport report = score(oneCase(
                List.of(combinedLabel("src/Foo.java", 12, "Long Method", "Extract Method")),
                List.of(finding("src/Foo.java", 12, "Long Method", "Extract Method")),
                false,
                false), 0);

        assertEquals(0, report.summary().labels());
        assertEquals(0, report.summary().predictions());
        assertEquals(0, report.summary().truePositives());
        assertEquals(0, report.summary().falsePositives());
        assertEquals(0, report.summary().falseNegatives());
        assertEquals(0, report.summary().refactoring().labels());
        assertEquals(0, report.summary().refactoring().predictions());
        assertEquals(0, report.summary().refactoring().truePositives());
        assertEquals(0, report.summary().refactoring().falsePositives());
        assertEquals(0, report.summary().refactoring().falseNegatives());
    }

    @Test
    void combinedLabelCountsDetectionAndRefactoring() {
        EvaluationReport report = score(oneCase(
                List.of(combinedLabel("src/Foo.java", 12, "Large Class", "Extract Class")),
                List.of(finding("src/Foo.java", 12, "Large Class", "ExtractClass"))), 0);

        assertEquals(1, report.summary().labels());
        assertEquals(1, report.summary().predictions());
        assertEquals(1, report.summary().truePositives());
        assertEquals(1, report.summary().refactoring().labels());
        assertEquals(1, report.summary().refactoring().predictions());
        assertEquals(1, report.summary().refactoring().truePositives());
    }

    @Test
    void zeroDenominatorMetricsAreZero() {
        EvaluationReport report = score(oneCase(List.of(), List.of()), 0);

        assertEquals(1, report.summary().cases());
        assertEquals(0, report.summary().labels());
        assertEquals(0, report.summary().predictions());
        assertEquals(0, report.summary().truePositives());
        assertEquals(0.0, report.summary().precision(), 0.0001);
        assertEquals(0.0, report.summary().recall(), 0.0001);
        assertEquals(0.0, report.summary().f1(), 0.0001);
        assertTrue(report.summary().byRule().isEmpty());

        EvaluationScorer.SummaryMetrics caseMetrics = report.cases().get(0).metrics();
        assertEquals(0.0, caseMetrics.precision(), 0.0001);
        assertEquals(0.0, caseMetrics.recall(), 0.0001);
        assertEquals(0.0, caseMetrics.f1(), 0.0001);
    }

    private static EvaluationReport score(CasePrediction prediction, int lineTolerance) {
        return new EvaluationScorer().score(List.of(prediction), lineTolerance);
    }

    private static EvaluationReport score(
            CasePrediction prediction,
            int lineTolerance,
            EvaluationMatchMode matchMode) {
        return new EvaluationScorer().score(List.of(prediction), lineTolerance, matchMode);
    }

    private static CasePrediction oneCase(
            List<EvaluationCase.EvaluationLabel> labels,
            List<LlmFinding> findings) {
        return oneCase(labels, findings, hasValidDetectionLabel(labels), hasRefactoringLabel(labels));
    }

    private static CasePrediction oneCase(
            List<EvaluationCase.EvaluationLabel> labels,
            List<LlmFinding> findings,
            boolean evaluateDetection,
            boolean evaluateRefactoring) {
        return new CasePrediction(
                "case-1",
                null,
                labels,
                findings,
                List.of(),
                evaluateDetection,
                evaluateRefactoring);
    }

    private static boolean hasValidDetectionLabel(List<EvaluationCase.EvaluationLabel> labels) {
        return labels.stream().anyMatch(label -> EvaluationScorer.canonicalRule(label.rule()) != null);
    }

    private static boolean hasRefactoringLabel(List<EvaluationCase.EvaluationLabel> labels) {
        return labels.stream().anyMatch(label -> label.suggestedRefactoring() != null
                && !label.suggestedRefactoring().isBlank());
    }

    private static EvaluationCase.EvaluationLabel label(String file, Integer line, String rule) {
        return new EvaluationCase.EvaluationLabel(file, line, rule, "Minor", "", "", "");
    }

    private static EvaluationCase.EvaluationLabel targetLabel(
            String file,
            Integer line,
            String rule,
            String targetType,
            String targetName) {
        return new EvaluationCase.EvaluationLabel(
                file,
                line,
                rule,
                targetType,
                targetName,
                "Minor",
                "",
                "",
                "");
    }

    private static EvaluationCase.EvaluationLabel refactoringLabel(String file, Integer line, String refactoring) {
        return combinedLabel(file, line, "", refactoring);
    }

    private static EvaluationCase.EvaluationLabel combinedLabel(
            String file,
            Integer line,
            String rule,
            String refactoring) {
        return new EvaluationCase.EvaluationLabel(
                file,
                line,
                rule,
                "Minor",
                "",
                refactoring,
                "Apply the expected refactoring.");
    }

    private static LlmFinding finding(String file, int line, String rule) {
        return finding(file, line, rule, "Extract Method");
    }

    private static LlmFinding finding(String file, int line, String rule, String refactoring) {
        return new LlmFinding(
                file,
                line,
                rule,
                "Minor",
                "The finding explains the smell.",
                refactoring,
                "Extract the repeated or oversized logic into a named helper.");
    }

    private static LlmFinding targetFinding(
            String file,
            int line,
            String rule,
            String targetType,
            String targetName) {
        return new LlmFinding(
                file,
                line,
                rule,
                targetType,
                targetName,
                "Minor",
                "The finding explains the smell.",
                "Extract Method",
                "Extract the repeated or oversized logic into a named helper.");
    }
}
