package dev.dimitra.bot.eval;

import dev.dimitra.bot.llm.LlmFinding;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EvaluationScorer {
    private static final List<String> ALLOWED_RULES = List.of(
            "Long Method",
            "Long Parameter List",
            "Duplicate Code",
            "Large Class",
            "Feature Envy",
            "Message Chains");

    private static final Map<String, String> RULE_ALIASES = buildRuleAliases();

    public EvaluationReport score(List<CasePrediction> predictions, int lineTolerance) {
        return score(predictions, lineTolerance, EvaluationMatchMode.LINE);
    }

    public EvaluationReport score(List<CasePrediction> predictions,
            int lineTolerance,
            EvaluationMatchMode matchMode) {
        EvaluationMatchMode safeMatchMode = matchMode == null ? EvaluationMatchMode.LINE : matchMode;
        validateTargetMetadataIfRequired(predictions, safeMatchMode);

        List<CaseScore> caseScores = new ArrayList<>();
        MutableMetrics detectionTotals = new MutableMetrics("all");
        MutableMetrics refactoringTotals = new MutableMetrics("all");
        Map<String, MutableMetrics> byRule = new LinkedHashMap<>();
        Map<String, MutableMetrics> byRefactoring = new LinkedHashMap<>();
        for (String rule : ALLOWED_RULES) {
            byRule.put(rule, new MutableMetrics(rule));
        }

        for (CasePrediction prediction : predictions) {
            CaseScore caseScore = scoreCase(prediction, lineTolerance, safeMatchMode);
            caseScores.add(caseScore);
            detectionTotals.add(caseScore.metrics());
            refactoringTotals.add(caseScore.refactoringMetrics());

            for (RuleMetrics ruleMetrics : caseScore.byRule().values()) {
                byRule.computeIfAbsent(ruleMetrics.rule(), MutableMetrics::new).add(ruleMetrics);
            }
            for (RuleMetrics refactoringMetrics : caseScore.byRefactoring().values()) {
                byRefactoring.computeIfAbsent(refactoringMetrics.rule(), MutableMetrics::new)
                        .add(refactoringMetrics);
            }
        }

        Map<String, RuleMetrics> finalByRule = nonEmptyMetrics(byRule);
        Map<String, RuleMetrics> finalByRefactoring = nonEmptyMetrics(byRefactoring);

        MetricsSummary summary = new MetricsSummary(
                caseScores.size(),
                detectionTotals.labels,
                detectionTotals.unsupportedLabels,
                detectionTotals.predictions,
                detectionTotals.truePositives,
                detectionTotals.falsePositives,
                detectionTotals.falseNegatives,
                ratio(detectionTotals.truePositives, detectionTotals.truePositives + detectionTotals.falsePositives),
                ratio(detectionTotals.truePositives, detectionTotals.truePositives + detectionTotals.falseNegatives),
                f1(detectionTotals.truePositives, detectionTotals.falsePositives, detectionTotals.falseNegatives),
                finalByRule,
                refactoringTotals.toSummaryMetrics(),
                finalByRefactoring);

        return new EvaluationReport(summary, caseScores);
    }

    private static void validateTargetMetadataIfRequired(
            List<CasePrediction> predictions,
            EvaluationMatchMode matchMode) {
        if (matchMode != EvaluationMatchMode.TARGET || predictions == null) {
            return;
        }

        for (CasePrediction prediction : predictions) {
            if (prediction == null || !prediction.evaluateDetection()) {
                continue;
            }

            for (EvaluationCase.EvaluationLabel label : safeLabels(prediction.labels())) {
                if (label == null || canonicalRule(label.rule()) == null) {
                    continue;
                }
                if (!hasTargetMetadata(label.targetType(), label.targetName())) {
                    throw new IllegalArgumentException(missingTargetMetadataMessage(
                            "label",
                            prediction.caseId(),
                            label.file(),
                            safeLine(label.line()),
                            label.rule()));
                }
            }

            for (LlmFinding finding : safeFindings(prediction.findings())) {
                if (finding == null || canonicalRule(finding.rule()) == null) {
                    continue;
                }
                if (!hasTargetMetadata(finding.targetType(), finding.targetName())) {
                    throw new IllegalArgumentException(missingTargetMetadataMessage(
                            "prediction",
                            prediction.caseId(),
                            finding.file(),
                            finding.line(),
                            finding.rule()));
                }
            }
        }
    }

    private static String missingTargetMetadataMessage(
            String itemType,
            String caseId,
            String file,
            int line,
            String rule) {
        return "Target match mode requires targetType and targetName on labels and predictions. "
                + "Add targetType/targetName or use --match-mode line. "
                + "First missing " + itemType
                + ": case=" + nvl(caseId, "?")
                + " file=" + nvl(file, "?")
                + " line=" + line
                + " rule=" + nvl(rule, "?");
    }

    private static List<EvaluationCase.EvaluationLabel> safeLabels(List<EvaluationCase.EvaluationLabel> labels) {
        return labels == null ? List.of() : labels;
    }

    private static List<LlmFinding> safeFindings(List<LlmFinding> findings) {
        return findings == null ? List.of() : findings;
    }

    private CaseScore scoreCase(CasePrediction prediction, int lineTolerance, EvaluationMatchMode matchMode) {
        List<CanonicalLabel> detectionLabels = new ArrayList<>();
        List<RefactoringLabel> refactoringLabels = new ArrayList<>();
        List<EvaluationCase.EvaluationLabel> unsupportedLabels = new ArrayList<>();

        for (EvaluationCase.EvaluationLabel label : prediction.labels()) {
            boolean hasRule = !isBlank(label.rule());
            boolean hasRefactoring = !isBlank(label.suggestedRefactoring());

            if (prediction.evaluateDetection() && hasRule) {
                String canonicalRule = canonicalRule(label.rule());
                if (canonicalRule == null) {
                    unsupportedLabels.add(label);
                } else {
                    detectionLabels.add(new CanonicalLabel(label, canonicalRule));
                }
            }

            if (prediction.evaluateRefactoring() && hasRefactoring) {
                refactoringLabels.add(new RefactoringLabel(
                        label,
                        normalizeRefactoringKey(label.suggestedRefactoring()),
                        displayName(label.suggestedRefactoring())));
            }

            if ((prediction.evaluateDetection() || prediction.evaluateRefactoring()) && !hasRule && !hasRefactoring) {
                unsupportedLabels.add(label);
            }
        }

        List<CanonicalFinding> detectionFindings = prediction.evaluateDetection()
                ? prediction.findings().stream()
                        .map(finding -> new CanonicalFinding(finding, canonicalRule(finding.rule())))
                        .filter(finding -> finding.rule() != null)
                        .toList()
                : List.of();

        List<RefactoringFinding> refactoringFindings = prediction.evaluateRefactoring()
                ? prediction.findings().stream()
                        .filter(finding -> !isBlank(finding.suggestedRefactoring()))
                        .map(finding -> new RefactoringFinding(
                                finding,
                                normalizeRefactoringKey(finding.suggestedRefactoring()),
                                displayName(finding.suggestedRefactoring())))
                        .toList()
                : List.of();

        DetectionScore detectionScore = scoreDetection(detectionLabels, detectionFindings, lineTolerance, matchMode);
        RefactoringScore refactoringScore = scoreRefactoring(refactoringLabels, refactoringFindings, lineTolerance);

        MutableMetrics detectionMetrics = new MutableMetrics("case");
        detectionMetrics.labels = detectionLabels.size();
        detectionMetrics.unsupportedLabels = unsupportedLabels.size();
        detectionMetrics.predictions = detectionFindings.size();
        detectionMetrics.truePositives = detectionScore.matches().size();
        detectionMetrics.falsePositives = detectionScore.falsePositives().size();
        detectionMetrics.falseNegatives = detectionScore.falseNegatives().size();

        MutableMetrics refactoringMetrics = new MutableMetrics("case");
        refactoringMetrics.labels = refactoringLabels.size();
        refactoringMetrics.predictions = refactoringFindings.size();
        refactoringMetrics.truePositives = refactoringScore.matches().size();
        refactoringMetrics.falsePositives = refactoringScore.falsePositives().size();
        refactoringMetrics.falseNegatives = refactoringScore.falseNegatives().size();

        return new CaseScore(
                prediction.caseId(),
                prediction.path() == null ? "" : prediction.path().toString(),
                detectionMetrics.toSummaryMetrics(),
                scoreRules(detectionLabels, detectionFindings, detectionScore.matches()),
                detectionScore.matches(),
                detectionScore.falsePositives(),
                detectionScore.falseNegatives(),
                refactoringMetrics.toSummaryMetrics(),
                scoreRefactorings(refactoringLabels, refactoringFindings, refactoringScore.matches()),
                refactoringScore.matches(),
                refactoringScore.falsePositives(),
                refactoringScore.falseNegatives(),
                unsupportedLabels,
                prediction.warnings());
    }

    private static DetectionScore scoreDetection(
            List<CanonicalLabel> labels,
            List<CanonicalFinding> findings,
            int lineTolerance,
            EvaluationMatchMode matchMode) {
        Set<Integer> matchedLabelIndexes = new LinkedHashSet<>();
        Set<Integer> matchedFindingIndexes = new LinkedHashSet<>();
        List<Match> matches = new ArrayList<>();

        for (int findingIndex = 0; findingIndex < findings.size(); findingIndex++) {
            CanonicalFinding finding = findings.get(findingIndex);
            int bestLabelIndex = bestDetectionLabel(
                    finding,
                    labels,
                    matchedLabelIndexes,
                    lineTolerance,
                    matchMode);
            if (bestLabelIndex >= 0) {
                matchedFindingIndexes.add(findingIndex);
                matchedLabelIndexes.add(bestLabelIndex);
                matches.add(new Match(labels.get(bestLabelIndex).raw(), finding.raw()));
            }
        }

        List<LlmFinding> falsePositives = unmatchedFindings(findings, matchedFindingIndexes);
        List<EvaluationCase.EvaluationLabel> falseNegatives = unmatchedLabels(labels, matchedLabelIndexes);
        return new DetectionScore(matches, falsePositives, falseNegatives);
    }

    private static RefactoringScore scoreRefactoring(
            List<RefactoringLabel> labels,
            List<RefactoringFinding> findings,
            int lineTolerance) {
        Set<Integer> matchedLabelIndexes = new LinkedHashSet<>();
        Set<Integer> matchedFindingIndexes = new LinkedHashSet<>();
        List<RefactoringMatch> matches = new ArrayList<>();

        for (int findingIndex = 0; findingIndex < findings.size(); findingIndex++) {
            RefactoringFinding finding = findings.get(findingIndex);
            int bestLabelIndex = bestRefactoringLabel(finding, labels, matchedLabelIndexes, lineTolerance);
            if (bestLabelIndex >= 0) {
                matchedFindingIndexes.add(findingIndex);
                matchedLabelIndexes.add(bestLabelIndex);
                matches.add(new RefactoringMatch(labels.get(bestLabelIndex).raw(), finding.raw()));
            }
        }

        List<LlmFinding> falsePositives = unmatchedRefactoringFindings(findings, matchedFindingIndexes);
        List<EvaluationCase.EvaluationLabel> falseNegatives = unmatchedRefactoringLabels(labels, matchedLabelIndexes);
        return new RefactoringScore(matches, falsePositives, falseNegatives);
    }

    private static int bestDetectionLabel(
            CanonicalFinding finding,
            List<CanonicalLabel> labels,
            Set<Integer> matchedLabelIndexes,
            int lineTolerance,
            EvaluationMatchMode matchMode) {
        int bestLabelIndex = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int labelIndex = 0; labelIndex < labels.size(); labelIndex++) {
            if (matchedLabelIndexes.contains(labelIndex)) {
                continue;
            }

            CanonicalLabel label = labels.get(labelIndex);
            if (!matches(finding, label, lineTolerance, matchMode)) {
                continue;
            }

            int distance = lineDistance(finding.raw().line(), safeLine(label.raw().line()));
            if (distance < bestDistance) {
                bestLabelIndex = labelIndex;
                bestDistance = distance;
            }
        }

        return bestLabelIndex;
    }

    private static int bestRefactoringLabel(
            RefactoringFinding finding,
            List<RefactoringLabel> labels,
            Set<Integer> matchedLabelIndexes,
            int lineTolerance) {
        int bestLabelIndex = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int labelIndex = 0; labelIndex < labels.size(); labelIndex++) {
            if (matchedLabelIndexes.contains(labelIndex)) {
                continue;
            }

            RefactoringLabel label = labels.get(labelIndex);
            if (!matchesRefactoring(finding, label, lineTolerance)) {
                continue;
            }

            int distance = lineDistance(finding.raw().line(), safeLine(label.raw().line()));
            if (distance < bestDistance) {
                bestLabelIndex = labelIndex;
                bestDistance = distance;
            }
        }

        return bestLabelIndex;
    }

    private static List<LlmFinding> unmatchedFindings(
            List<CanonicalFinding> findings,
            Set<Integer> matchedFindingIndexes) {
        List<LlmFinding> falsePositives = new ArrayList<>();
        for (int i = 0; i < findings.size(); i++) {
            if (!matchedFindingIndexes.contains(i)) {
                falsePositives.add(findings.get(i).raw());
            }
        }
        return falsePositives;
    }

    private static List<EvaluationCase.EvaluationLabel> unmatchedLabels(
            List<CanonicalLabel> labels,
            Set<Integer> matchedLabelIndexes) {
        List<EvaluationCase.EvaluationLabel> falseNegatives = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            if (!matchedLabelIndexes.contains(i)) {
                falseNegatives.add(labels.get(i).raw());
            }
        }
        return falseNegatives;
    }

    private static List<LlmFinding> unmatchedRefactoringFindings(
            List<RefactoringFinding> findings,
            Set<Integer> matchedFindingIndexes) {
        List<LlmFinding> falsePositives = new ArrayList<>();
        for (int i = 0; i < findings.size(); i++) {
            if (!matchedFindingIndexes.contains(i)) {
                falsePositives.add(findings.get(i).raw());
            }
        }
        return falsePositives;
    }

    private static List<EvaluationCase.EvaluationLabel> unmatchedRefactoringLabels(
            List<RefactoringLabel> labels,
            Set<Integer> matchedLabelIndexes) {
        List<EvaluationCase.EvaluationLabel> falseNegatives = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            if (!matchedLabelIndexes.contains(i)) {
                falseNegatives.add(labels.get(i).raw());
            }
        }
        return falseNegatives;
    }

    private static Map<String, RuleMetrics> scoreRules(
            List<CanonicalLabel> labels,
            List<CanonicalFinding> findings,
            List<Match> matches) {
        Map<String, MutableMetrics> byRule = new LinkedHashMap<>();

        for (CanonicalLabel label : labels) {
            byRule.computeIfAbsent(label.rule(), MutableMetrics::new).labels++;
        }
        for (CanonicalFinding finding : findings) {
            byRule.computeIfAbsent(finding.rule(), MutableMetrics::new).predictions++;
        }
        for (Match match : matches) {
            String rule = canonicalRuleOrOriginal(match.finding().rule());
            byRule.computeIfAbsent(rule, MutableMetrics::new).truePositives++;
        }

        return completedMetrics(byRule);
    }

    private static Map<String, RuleMetrics> scoreRefactorings(
            List<RefactoringLabel> labels,
            List<RefactoringFinding> findings,
            List<RefactoringMatch> matches) {
        Map<String, MutableMetrics> byRefactoringKey = new LinkedHashMap<>();

        for (RefactoringLabel label : labels) {
            byRefactoringKey.computeIfAbsent(label.refactoringKey(), key -> new MutableMetrics(label.refactoringName()))
                    .labels++;
        }
        for (RefactoringFinding finding : findings) {
            byRefactoringKey.computeIfAbsent(finding.refactoringKey(), key -> new MutableMetrics(finding.refactoringName()))
                    .predictions++;
        }
        for (RefactoringMatch match : matches) {
            String key = normalizeRefactoringKey(match.label().suggestedRefactoring());
            byRefactoringKey.computeIfAbsent(key, ignored -> new MutableMetrics(displayName(match.label().suggestedRefactoring())))
                    .truePositives++;
        }

        return completedMetrics(byRefactoringKey);
    }

    private static Map<String, RuleMetrics> completedMetrics(Map<String, MutableMetrics> metricsByName) {
        Map<String, RuleMetrics> finalMetrics = new LinkedHashMap<>();
        for (Map.Entry<String, MutableMetrics> entry : metricsByName.entrySet()) {
            MutableMetrics metrics = entry.getValue();
            metrics.falsePositives = Math.max(0, metrics.predictions - metrics.truePositives);
            metrics.falseNegatives = Math.max(0, metrics.labels - metrics.truePositives);
            finalMetrics.put(entry.getKey(), metrics.toRuleMetrics());
        }
        return finalMetrics;
    }

    private static Map<String, RuleMetrics> nonEmptyMetrics(Map<String, MutableMetrics> metricsByName) {
        Map<String, RuleMetrics> finalMetrics = new LinkedHashMap<>();
        for (Map.Entry<String, MutableMetrics> entry : metricsByName.entrySet()) {
            RuleMetrics metrics = entry.getValue().toRuleMetrics();
            if (metrics.labels() > 0 || metrics.predictions() > 0) {
                finalMetrics.put(entry.getKey(), metrics);
            }
        }
        return finalMetrics;
    }

    private static boolean matches(
            CanonicalFinding finding,
            CanonicalLabel label,
            int lineTolerance,
            EvaluationMatchMode matchMode) {
        if (!finding.rule().equals(label.rule()) || !samePath(finding.raw().file(), label.raw().file())) {
            return false;
        }

        EvaluationMatchMode safeMatchMode = matchMode == null ? EvaluationMatchMode.LINE : matchMode;
        if (safeMatchMode == EvaluationMatchMode.LINE) {
            return linesMatch(finding.raw().line(), safeLine(label.raw().line()), lineTolerance);
        }

        boolean bothHaveTargets = hasTargetMetadata(finding.raw().targetType(), finding.raw().targetName())
                && hasTargetMetadata(label.raw().targetType(), label.raw().targetName());
        if (safeMatchMode == EvaluationMatchMode.TARGET || bothHaveTargets) {
            return targetsMatch(
                    finding.raw().targetType(),
                    finding.raw().targetName(),
                    label.raw().targetType(),
                    label.raw().targetName());
        }
        return linesMatch(finding.raw().line(), safeLine(label.raw().line()), lineTolerance);
    }

    private static boolean matchesRefactoring(RefactoringFinding finding, RefactoringLabel label, int lineTolerance) {
        return finding.refactoringKey().equals(label.refactoringKey())
                && samePath(finding.raw().file(), label.raw().file())
                && linesMatch(finding.raw().line(), safeLine(label.raw().line()), lineTolerance);
    }

    private static boolean targetsMatch(
            String findingTargetType,
            String findingTargetName,
            String labelTargetType,
            String labelTargetName) {
        return !isBlank(findingTargetType)
                && !isBlank(findingTargetName)
                && !isBlank(labelTargetType)
                && !isBlank(labelTargetName)
                && normalizeTargetType(findingTargetType).equals(normalizeTargetType(labelTargetType))
                && findingTargetName.trim().equals(labelTargetName.trim());
    }

    private static boolean hasTargetMetadata(String targetType, String targetName) {
        return !isBlank(targetType) && !isBlank(targetName);
    }

    private static String normalizeTargetType(String targetType) {
        return nvl(targetType, "").trim().toUpperCase(Locale.ROOT);
    }

    private static boolean samePath(String left, String right) {
        String normalizedLeft = normalizePath(left);
        String normalizedRight = normalizePath(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return false;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }
        Path leftFileName = Path.of(normalizedLeft).getFileName();
        Path rightFileName = Path.of(normalizedRight).getFileName();
        return leftFileName != null
                && rightFileName != null
                && leftFileName.toString().equals(rightFileName.toString());
    }

    private static boolean linesMatch(int findingLine, int labelLine, int lineTolerance) {
        if (findingLine <= 0 || labelLine <= 0) {
            return true;
        }
        return Math.abs(findingLine - labelLine) <= Math.max(0, lineTolerance);
    }

    private static int lineDistance(int findingLine, int labelLine) {
        if (findingLine <= 0 || labelLine <= 0) {
            return 0;
        }
        return Math.abs(findingLine - labelLine);
    }

    private static int safeLine(Integer line) {
        return line == null ? 0 : line;
    }

    public static String canonicalRule(String rule) {
        if (rule == null || rule.isBlank()) {
            return null;
        }
        return RULE_ALIASES.get(normalizeRuleKey(rule));
    }

    private static String canonicalRuleOrOriginal(String rule) {
        String canonical = canonicalRule(rule);
        return canonical == null ? nvl(rule, "Unsupported Rule") : canonical;
    }

    private static Map<String, String> buildRuleAliases() {
        Map<String, String> aliases = new HashMap<>();
        for (String rule : ALLOWED_RULES) {
            addAlias(aliases, rule, rule);
        }

        addAlias(aliases, "duplicate code", "Duplicate Code");
        addAlias(aliases, "duplicate code inside a method", "Duplicate Code");
        addAlias(aliases, "duplicates", "Duplicate Code");
        addAlias(aliases, "large class", "Large Class");
        addAlias(aliases, "god class", "Large Class");
        addAlias(aliases, "feature envy", "Feature Envy");
        addAlias(aliases, "message chain", "Message Chains");
        addAlias(aliases, "message chains", "Message Chains");
        addAlias(aliases, "train wreck", "Message Chains");

        return Map.copyOf(aliases);
    }

    private static void addAlias(Map<String, String> aliases, String alias, String rule) {
        aliases.put(normalizeRuleKey(alias), rule);
    }

    private static String normalizeRuleKey(String value) {
        return nvl(value, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static String normalizeRefactoringKey(String value) {
        return nvl(value, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private static String normalizePath(String path) {
        String normalized = nvl(path, "").replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static String displayName(String value) {
        return nvl(value, "Unspecified Refactoring").trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : ((double) numerator) / denominator;
    }

    private static double f1(int truePositives, int falsePositives, int falseNegatives) {
        double precision = ratio(truePositives, truePositives + falsePositives);
        double recall = ratio(truePositives, truePositives + falseNegatives);
        return precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
    }

    private static String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CanonicalLabel(EvaluationCase.EvaluationLabel raw, String rule) {
    }

    private record CanonicalFinding(LlmFinding raw, String rule) {
    }

    private record RefactoringLabel(
            EvaluationCase.EvaluationLabel raw,
            String refactoringKey,
            String refactoringName) {
    }

    private record RefactoringFinding(
            LlmFinding raw,
            String refactoringKey,
            String refactoringName) {
    }

    private record DetectionScore(
            List<Match> matches,
            List<LlmFinding> falsePositives,
            List<EvaluationCase.EvaluationLabel> falseNegatives) {
    }

    private record RefactoringScore(
            List<RefactoringMatch> matches,
            List<LlmFinding> falsePositives,
            List<EvaluationCase.EvaluationLabel> falseNegatives) {
    }

    private static final class MutableMetrics {
        private final String rule;
        private int labels;
        private int unsupportedLabels;
        private int predictions;
        private int truePositives;
        private int falsePositives;
        private int falseNegatives;

        private MutableMetrics(String rule) {
            this.rule = rule;
        }

        private void add(RuleMetrics metrics) {
            this.labels += metrics.labels();
            this.predictions += metrics.predictions();
            this.truePositives += metrics.truePositives();
            this.falsePositives += metrics.falsePositives();
            this.falseNegatives += metrics.falseNegatives();
        }

        private void add(SummaryMetrics metrics) {
            this.labels += metrics.labels();
            this.unsupportedLabels += metrics.unsupportedLabels();
            this.predictions += metrics.predictions();
            this.truePositives += metrics.truePositives();
            this.falsePositives += metrics.falsePositives();
            this.falseNegatives += metrics.falseNegatives();
        }

        private SummaryMetrics toSummaryMetrics() {
            return new SummaryMetrics(
                    labels,
                    unsupportedLabels,
                    predictions,
                    truePositives,
                    falsePositives,
                    falseNegatives,
                    ratio(truePositives, truePositives + falsePositives),
                    ratio(truePositives, truePositives + falseNegatives),
                    f1(truePositives, falsePositives, falseNegatives));
        }

        private RuleMetrics toRuleMetrics() {
            return new RuleMetrics(
                    rule,
                    labels,
                    predictions,
                    truePositives,
                    falsePositives,
                    falseNegatives,
                    ratio(truePositives, truePositives + falsePositives),
                    ratio(truePositives, truePositives + falseNegatives),
                    f1(truePositives, falsePositives, falseNegatives));
        }
    }

    public record CasePrediction(
            String caseId,
            Path path,
            List<EvaluationCase.EvaluationLabel> labels,
            List<LlmFinding> findings,
            List<String> warnings,
            boolean evaluateDetection,
            boolean evaluateRefactoring) {
    }

    public record EvaluationReport(
            MetricsSummary summary,
            List<CaseScore> cases) {
    }

    public record MetricsSummary(
            int cases,
            int labels,
            int unsupportedLabels,
            int predictions,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            double f1,
            Map<String, RuleMetrics> byRule,
            SummaryMetrics refactoring,
            Map<String, RuleMetrics> byRefactoring) {
    }

    public record SummaryMetrics(
            int labels,
            int unsupportedLabels,
            int predictions,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            double f1) {
    }

    public record RuleMetrics(
            String rule,
            int labels,
            int predictions,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            double precision,
            double recall,
            double f1) {
    }

    public record CaseScore(
            String caseId,
            String path,
            SummaryMetrics metrics,
            Map<String, RuleMetrics> byRule,
            List<Match> matches,
            List<LlmFinding> falsePositives,
            List<EvaluationCase.EvaluationLabel> falseNegatives,
            SummaryMetrics refactoringMetrics,
            Map<String, RuleMetrics> byRefactoring,
            List<RefactoringMatch> refactoringMatches,
            List<LlmFinding> refactoringFalsePositives,
            List<EvaluationCase.EvaluationLabel> refactoringFalseNegatives,
            List<EvaluationCase.EvaluationLabel> unsupportedLabels,
            List<String> warnings) {
    }

    public record Match(
            EvaluationCase.EvaluationLabel label,
            LlmFinding finding) {
    }

    public record RefactoringMatch(
            EvaluationCase.EvaluationLabel label,
            LlmFinding finding) {
    }
}
