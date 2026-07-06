package dev.dimitra.bot.analysis;

import dev.dimitra.bot.llm.LlmFinding;
import dev.dimitra.bot.model.AnalyzedFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindingValidator {
    private static final int ANCHOR_SEARCH_RADIUS = 3;
    private static final int FILE_ANCHOR_SEARCH_RADIUS = 3;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\d+");
    private static final Pattern IDENTIFIER_BEFORE_PAREN_PATTERN = Pattern.compile("\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final Pattern CLASS_DECLARATION_PATTERN = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern MESSAGE_CHAIN_PATTERN = Pattern.compile(
            "\\b[A-Za-z_][A-Za-z0-9_]*\\s*(?:\\.\\s*[A-Za-z_][A-Za-z0-9_]*\\s*\\([^)]*\\)\\s*){3,}");
    private static final Pattern REPEATED_FOREIGN_ACCESS_PATTERN = Pattern.compile(
            "\\b([a-z][A-Za-z0-9_]*)\\s*\\.\\s*(?:get[A-Z][A-Za-z0-9_]*|is[A-Z][A-Za-z0-9_]*|[a-z][A-Za-z0-9_]*)\\s*\\(");
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "the", "and", "for", "from", "with", "that", "this", "into", "only",
            "code", "line", "lines", "changed", "change", "added", "modified", "file",
            "method", "class", "smell", "issue", "finding", "because", "should", "could",
            "would", "when", "then", "than", "there", "their", "value", "values");
    private static final Set<String> ALLOWED_RULES = Set.of(
            "Long Method",
            "Long Parameter List",
            "Duplicate Code",
            "Duplicate Code Inside a Method",
            "Large Class",
            "Feature Envy",
            "Message Chains");
    private static final Set<String> METHOD_DECLARATION_ANCHOR_RULES = Set.of(
            "Long Method",
            "Long Parameter List",
            "Duplicate Code",
            "Duplicate Code Inside a Method",
            "Feature Envy");
    private static final Set<String> NON_DECLARATION_PREFIX_TOKENS = Set.of(
            "if", "for", "while", "switch", "catch", "return", "throw", "new", "assert");
    private static final Set<String> NON_DECLARATION_NAMES = Set.of(
            "if", "for", "while", "switch", "catch", "try", "new", "return", "throw", "assert", "synchronized");

    private final AddedLineParser addedLineParser;
    private final AnalysisScope scope;
    private final ValidationMode validationMode;
    private final boolean debug;

    public FindingValidator(AddedLineParser addedLineParser, boolean debug) {
        this(addedLineParser, AnalysisScope.DIFF, ValidationMode.defaultForScope(AnalysisScope.DIFF), debug);
    }

    public FindingValidator(AddedLineParser addedLineParser, AnalysisScope scope, boolean debug) {
        this(addedLineParser, scope, ValidationMode.defaultForScope(scope == null ? AnalysisScope.DIFF : scope), debug);
    }

    public FindingValidator(AddedLineParser addedLineParser,
            AnalysisScope scope,
            ValidationMode validationMode,
            boolean debug) {
        this.addedLineParser = addedLineParser;
        this.scope = scope == null ? AnalysisScope.DIFF : scope;
        this.validationMode = validationMode == null ? ValidationMode.defaultForScope(this.scope) : validationMode;
        this.debug = debug;
    }

    public List<LlmFinding> validateAndAnchor(List<LlmFinding> findings, List<AnalyzedFile> chunk) {
        if (validationMode == ValidationMode.TARGET_NAME || validationMode == ValidationMode.DIFF_TARGET_NAME) {
            return validateAndAnchorToTargets(findings, chunk, validationMode == ValidationMode.DIFF_TARGET_NAME);
        }

        if (scope == AnalysisScope.FILE) {
            return validateAndAnchorToFileContent(findings, chunk);
        }

        return validateAndAnchorToAddedLines(findings, chunk);
    }

    private List<LlmFinding> validateAndAnchorToAddedLines(List<LlmFinding> findings, List<AnalyzedFile> chunk) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        Map<String, Map<Integer, String>> addedLinesByFile = buildAddedLineMap(chunk);
        List<LlmFinding> validated = new ArrayList<>();

        for (LlmFinding finding : findings) {
            if (hasMissingRequiredFields(finding)) {
                continue;
            }

            Map<Integer, String> addedLines = addedLinesByFile.get(normalizePath(finding.file()));
            if (addedLines == null || addedLines.isEmpty()) {
                logValidationSkip(finding, "file has no added-line anchors in this chunk");
                continue;
            }

            if (addedLines.containsKey(finding.line())) {
                validated.add(finding);
                continue;
            }

            Integer nearbyLine = findNearbyRelatedAddedLine(finding, addedLines);
            if (nearbyLine == null) {
                logValidationSkip(finding,
                        "reported line is not an added line and no related nearby added line matched");
                continue;
            }

            logReanchor(finding, nearbyLine);
            validated.add(withLine(finding, nearbyLine));
        }

        return validated;
    }

    private List<LlmFinding> validateAndAnchorToFileContent(List<LlmFinding> findings, List<AnalyzedFile> chunk) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> contentLinesByFile = buildContentLineMap(chunk);
        Map<String, Map<Integer, String>> addedLinesByFile = buildAddedLineMap(chunk);
        List<LlmFinding> validated = new ArrayList<>();

        for (LlmFinding finding : findings) {
            if (hasMissingRequiredFields(finding)) {
                continue;
            }

            String normalizedPath = normalizePath(finding.file());
            List<String> contentLines = contentLinesByFile.get(normalizedPath);
            if (contentLines != null && !contentLines.isEmpty()) {
                Integer anchoredLine = anchorToFileContent(finding, contentLines);
                if (anchoredLine == null) {
                    logValidationSkip(finding,
                            "reported line is not in file content and no related nearby line matched");
                    continue;
                }

                if (anchoredLine == finding.line()) {
                    validated.add(finding);
                } else {
                    logReanchor(finding, anchoredLine);
                    validated.add(withLine(finding, anchoredLine));
                }
                continue;
            }

            // If a case supplies only a diff, file-scope analysis can still validate
            // against the added lines from that diff.
            Map<Integer, String> addedLines = addedLinesByFile.get(normalizedPath);
            if (addedLines == null || addedLines.isEmpty()) {
                logValidationSkip(finding, "file has no content or added-line anchors in this chunk");
                continue;
            }

            if (addedLines.containsKey(finding.line())) {
                validated.add(finding);
                continue;
            }

            Integer nearbyLine = findNearbyRelatedAddedLine(finding, addedLines);
            if (nearbyLine == null) {
                logValidationSkip(finding,
                        "reported line is not a supplied file line and no related diff line matched");
                continue;
            }

            logReanchor(finding, nearbyLine);
            validated.add(withLine(finding, nearbyLine));
        }

        return validated;
    }

    private List<LlmFinding> validateAndAnchorToTargets(
            List<LlmFinding> findings,
            List<AnalyzedFile> chunk,
            boolean requireDiffOverlap) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        Map<String, TargetFile> targetsByFile = buildTargetFileMap(chunk);
        Map<String, Map<Integer, String>> addedLinesByFile = buildAddedLineMap(chunk);
        List<LlmFinding> validated = new ArrayList<>();

        for (LlmFinding finding : findings) {
            if (hasMissingRequiredFields(finding)) {
                continue;
            }
            if (hasMissingTargetFields(finding)) {
                logValidationSkip(finding, "targetType and targetName are required in " + validationMode + " mode");
                continue;
            }
            if (!targetTypeMatchesRule(finding)) {
                logValidationSkip(finding, "targetType does not match the smell rule");
                continue;
            }

            String normalizedPath = normalizePath(finding.file());
            TargetFile targetFile = targetsByFile.get(normalizedPath);
            if (targetFile == null || targetFile.contentLines().isEmpty()) {
                logValidationSkip(finding, "file content is required for target-based validation");
                continue;
            }

            TargetDeclaration target = targetFile.find(normalizeTargetType(finding.targetType()), finding.targetName());
            if (target == null) {
                logValidationSkip(finding, "target declaration was not found in the analyzed file");
                continue;
            }

            if (requireDiffOverlap) {
                Map<Integer, String> addedLines = addedLinesByFile.get(normalizedPath);
                if (addedLines == null || addedLines.isEmpty()) {
                    logValidationSkip(finding, "file has no added-line anchors in this chunk");
                    continue;
                }
                if (!changedLinesOverlapTarget(target, addedLines.keySet(), targetFile.contentLines().size())) {
                    logValidationSkip(finding, "changed lines do not overlap the target declaration or body");
                    continue;
                }
            }

            int anchoredLine = anchorToTarget(finding, target, targetFile.contentLines());
            if (anchoredLine == finding.line()) {
                validated.add(finding);
            } else {
                logReanchor(finding, anchoredLine);
                validated.add(withLine(finding, anchoredLine));
            }
        }

        return validated;
    }

    private Map<String, Map<Integer, String>> buildAddedLineMap(List<AnalyzedFile> files) {
        Map<String, Map<Integer, String>> addedLinesByFile = new LinkedHashMap<>();
        for (AnalyzedFile file : files) {
            Map<Integer, String> addedLines = addedLineParser.parse(file.diff());

            putAddedLineMap(addedLinesByFile, file.filename(), addedLines);
            putAddedLineMap(addedLinesByFile, file.previousFilename(), addedLines);
        }
        return addedLinesByFile;
    }

    private Map<String, List<String>> buildContentLineMap(List<AnalyzedFile> files) {
        Map<String, List<String>> contentLinesByFile = new LinkedHashMap<>();
        for (AnalyzedFile file : files) {
            if (file.fileContent() == null || file.fileContent().isBlank()) {
                continue;
            }

            List<String> lines = file.fileContent().lines().toList();
            putContentLineMap(contentLinesByFile, file.filename(), lines);
            putContentLineMap(contentLinesByFile, file.previousFilename(), lines);
        }
        return contentLinesByFile;
    }

    private Map<String, TargetFile> buildTargetFileMap(List<AnalyzedFile> files) {
        Map<String, TargetFile> targetsByFile = new LinkedHashMap<>();
        for (AnalyzedFile file : files) {
            if (file.fileContent() == null || file.fileContent().isBlank()) {
                continue;
            }

            List<String> lines = file.fileContent().lines().toList();
            TargetFile targetFile = new TargetFile(lines, targetDeclarations(lines));
            putTargetFileMap(targetsByFile, file.filename(), targetFile);
            putTargetFileMap(targetsByFile, file.previousFilename(), targetFile);
        }
        return targetsByFile;
    }

    private static void putAddedLineMap(Map<String, Map<Integer, String>> target,
            String path,
            Map<Integer, String> addedLines) {
        String normalizedPath = normalizePath(path);
        if (!normalizedPath.isBlank()) {
            target.put(normalizedPath, addedLines);
        }
    }

    private static void putContentLineMap(Map<String, List<String>> target,
            String path,
            List<String> lines) {
        String normalizedPath = normalizePath(path);
        if (!normalizedPath.isBlank()) {
            target.put(normalizedPath, lines);
        }
    }

    private static void putTargetFileMap(Map<String, TargetFile> target,
            String path,
            TargetFile targetFile) {
        String normalizedPath = normalizePath(path);
        if (!normalizedPath.isBlank()) {
            target.put(normalizedPath, targetFile);
        }
    }

    private Integer anchorToFileContent(LlmFinding finding, List<String> contentLines) {
        Integer declarationLine = findNamedDeclarationLine(finding, contentLines);
        if (declarationLine != null) {
            return declarationLine;
        }

        int line = finding.line();
        if (line > 0 && line <= contentLines.size()) {
            return line;
        }

        return findNearbyRelatedContentLine(finding, contentLines);
    }

    private Integer findNamedDeclarationLine(LlmFinding finding, List<String> contentLines) {
        if (finding == null || contentLines == null || contentLines.isEmpty()) {
            return null;
        }

        if (METHOD_DECLARATION_ANCHOR_RULES.contains(finding.rule())) {
            return findNamedMethodDeclarationLine(finding, contentLines);
        }

        if ("Large Class".equals(finding.rule())) {
            return findNamedClassDeclarationLine(finding, contentLines);
        }

        return null;
    }

    private Integer findNamedMethodDeclarationLine(LlmFinding finding, List<String> contentLines) {
        List<Declaration> declarations = methodDeclarations(contentLines);
        if (declarations.isEmpty()) {
            return null;
        }

        return bestMentionedDeclarationLine(finding, declarations);
    }

    private Integer findNamedClassDeclarationLine(LlmFinding finding, List<String> contentLines) {
        List<Declaration> declarations = classDeclarations(contentLines);
        if (declarations.isEmpty()) {
            return null;
        }

        return bestMentionedDeclarationLine(finding, declarations);
    }

    private Integer bestMentionedDeclarationLine(LlmFinding finding, List<Declaration> declarations) {
        String text = findingText(finding);
        Declaration best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Declaration declaration : declarations) {
            if (!mentionsIdentifier(text, declaration.name())) {
                continue;
            }

            int distance = finding.line() <= 0
                    ? 0
                    : Math.abs(finding.line() - declaration.line());
            if (distance < bestDistance
                    || (distance == bestDistance && (best == null || declaration.line() < best.line()))) {
                best = declaration;
                bestDistance = distance;
            }
        }

        return best == null ? null : best.line();
    }

    private static List<Declaration> methodDeclarations(List<String> contentLines) {
        List<Declaration> declarations = new ArrayList<>();
        for (int i = 0; i < contentLines.size(); i++) {
            String line = withoutLineComment(contentLines.get(i));
            Matcher matcher = IDENTIFIER_BEFORE_PAREN_PATTERN.matcher(line);
            while (matcher.find()) {
                String name = matcher.group(1);
                int nameStart = matcher.start(1);
                if (isMethodDeclarationName(name, line, nameStart)) {
                    declarations.add(new Declaration(name, i + 1));
                    break;
                }
            }
        }
        return declarations;
    }

    private static boolean isMethodDeclarationName(String name, String line, int nameStart) {
        if (NON_DECLARATION_NAMES.contains(nvl(name, "").toLowerCase())) {
            return false;
        }

        if (nameStart > 0 && line.charAt(nameStart - 1) == '.') {
            return false;
        }

        String prefix = line.substring(0, nameStart);
        return looksLikeDeclarationPrefix(prefix);
    }

    private static boolean looksLikeDeclarationPrefix(String prefix) {
        String cleaned = nvl(prefix, "")
                .replaceAll("@[A-Za-z0-9_.]+(?:\\([^)]*\\))?", " ")
                .trim();
        if (cleaned.isBlank()
                || cleaned.endsWith(".")
                || cleaned.contains("=")
                || cleaned.contains("->")) {
            return false;
        }

        List<String> tokens = identifierTokens(cleaned);
        if (tokens.isEmpty()) {
            return false;
        }

        return !NON_DECLARATION_PREFIX_TOKENS.contains(tokens.get(0).toLowerCase());
    }

    private static List<Declaration> classDeclarations(List<String> contentLines) {
        List<Declaration> declarations = new ArrayList<>();
        for (int i = 0; i < contentLines.size(); i++) {
            Matcher matcher = CLASS_DECLARATION_PATTERN.matcher(withoutLineComment(contentLines.get(i)));
            if (matcher.find()) {
                declarations.add(new Declaration(matcher.group(1), i + 1));
            }
        }
        return declarations;
    }

    private static List<TargetDeclaration> targetDeclarations(List<String> contentLines) {
        List<TargetDeclaration> declarations = new ArrayList<>();
        for (Declaration declaration : classDeclarations(contentLines)) {
            declarations.add(toTargetDeclaration("CLASS", declaration.name(), declaration.line(), contentLines));
        }
        // TODO: targetName alone does not disambiguate overloaded methods; add signature-aware matching
        // or use diff/line proximity to choose between overloads when the prompt starts returning signatures.
        for (Declaration declaration : methodDeclarations(contentLines)) {
            declarations.add(toTargetDeclaration("METHOD", declaration.name(), declaration.line(), contentLines));
        }
        return declarations;
    }

    private static TargetDeclaration toTargetDeclaration(
            String targetType,
            String targetName,
            int declarationLine,
            List<String> contentLines) {
        int startLine = declarationLine;
        int endLine = declarationLine;
        int braceDepth = 0;
        boolean foundBody = false;

        for (int i = declarationLine - 1; i < contentLines.size(); i++) {
            String code = stripStringLiterals(withoutLineComment(contentLines.get(i)));
            for (int j = 0; j < code.length(); j++) {
                char ch = code.charAt(j);
                if (ch == '{') {
                    braceDepth++;
                    foundBody = true;
                } else if (ch == '}') {
                    braceDepth--;
                }
            }

            endLine = i + 1;
            if (foundBody && braceDepth <= 0) {
                break;
            }
            if (!foundBody && code.contains(";")) {
                break;
            }
        }

        if (!foundBody) {
            endLine = Math.min(contentLines.size(), declarationLine + FILE_ANCHOR_SEARCH_RADIUS);
        }

        return new TargetDeclaration(targetType, targetName, declarationLine, startLine, Math.max(startLine, endLine));
    }

    private static String stripStringLiterals(String line) {
        String value = nvl(line, "");
        StringBuilder cleaned = new StringBuilder();
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                cleaned.append(' ');
                continue;
            }
            if (ch == '\\' && (inString || inChar)) {
                escaped = true;
                cleaned.append(' ');
                continue;
            }
            if (ch == '"' && !inChar) {
                inString = !inString;
                cleaned.append(' ');
                continue;
            }
            if (ch == '\'' && !inString) {
                inChar = !inChar;
                cleaned.append(' ');
                continue;
            }
            cleaned.append((inString || inChar) ? ' ' : ch);
        }
        return cleaned.toString();
    }

    private static boolean changedLinesOverlapTarget(
            TargetDeclaration target,
            Set<Integer> changedLines,
            int lineCount) {
        for (Integer changedLine : changedLines) {
            if (changedLine == null) {
                continue;
            }
            if (changedLine >= target.startLine() && changedLine <= target.endLine()) {
                return true;
            }
        }

        int nearbyStart = Math.max(1, target.declarationLine() - 1);
        int nearbyEnd = Math.min(lineCount, target.declarationLine() + FILE_ANCHOR_SEARCH_RADIUS);
        for (Integer changedLine : changedLines) {
            if (changedLine != null && changedLine >= nearbyStart && changedLine <= nearbyEnd) {
                return true;
            }
        }
        return false;
    }

    private static int anchorToTarget(LlmFinding finding, TargetDeclaration target, List<String> contentLines) {
        if (target != null && target.declarationLine() > 0) {
            return target.declarationLine();
        }

        int line = finding.line();
        if (line > 0 && contentLines != null && line <= contentLines.size()) {
            return line;
        }

        if (contentLines == null || contentLines.isEmpty()) {
            return Math.max(1, line);
        }
        return Math.max(1, Math.min(contentLines.size(), line <= 0 ? 1 : line));
    }

    private static boolean mentionsIdentifier(String text, String identifier) {
        if (isBlank(text) || isBlank(identifier)) {
            return false;
        }

        Pattern exactIdentifier = Pattern.compile(
                "(?<![A-Za-z0-9_$])" + Pattern.quote(identifier) + "(?![A-Za-z0-9_$])");
        return exactIdentifier.matcher(text).find();
    }

    private static List<String> identifierTokens(String value) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(nvl(value, ""));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static String withoutLineComment(String line) {
        String value = nvl(line, "");
        int commentStart = value.indexOf("//");
        return commentStart < 0 ? value : value.substring(0, commentStart);
    }

    private Integer findNearbyRelatedContentLine(LlmFinding finding, List<String> contentLines) {
        if (finding.line() <= 0 || contentLines == null || contentLines.isEmpty()) {
            return null;
        }

        Integer bestLine = null;
        int bestDistance = Integer.MAX_VALUE;
        Set<String> findingTokens = tokenize(findingText(finding));
        int startLine = Math.max(1, finding.line() - FILE_ANCHOR_SEARCH_RADIUS);
        int endLine = Math.min(contentLines.size(), finding.line() + FILE_ANCHOR_SEARCH_RADIUS);

        for (int line = startLine; line <= endLine; line++) {
            String lineText = contentLines.get(line - 1);
            if (!looksRelatedToFinding(finding, findingTokens, lineText)) {
                continue;
            }

            int distance = Math.abs(line - finding.line());
            if (distance < bestDistance
                    || (distance == bestDistance && (bestLine == null || line < bestLine))) {
                bestLine = line;
                bestDistance = distance;
            }
        }

        return bestLine;
    }

    private Integer findNearbyRelatedAddedLine(LlmFinding finding, Map<Integer, String> addedLines) {
        if (finding.line() <= 0) {
            return null;
        }

        Integer bestLine = null;
        int bestDistance = Integer.MAX_VALUE;
        Set<String> findingTokens = tokenize(findingText(finding));

        for (Map.Entry<Integer, String> entry : addedLines.entrySet()) {
            int distance = Math.abs(entry.getKey() - finding.line());
            if (distance > ANCHOR_SEARCH_RADIUS) {
                continue;
            }

            // Nearby is not enough by itself; require a textual or rule-specific signal
            // before moving a finding.
            if (!looksRelatedToFinding(finding, findingTokens, entry.getValue())) {
                continue;
            }

            if (distance < bestDistance
                    || (distance == bestDistance && (bestLine == null || entry.getKey() < bestLine))) {
                bestLine = entry.getKey();
                bestDistance = distance;
            }
        }

        return bestLine;
    }

    private static boolean looksRelatedToFinding(LlmFinding finding, Set<String> findingTokens, String addedLineText) {
        Set<String> lineTokens = tokenize(addedLineText);
        for (String token : lineTokens) {
            if (findingTokens.contains(token)) {
                return true;
            }
        }

        return ruleLooksRelatedToLine(finding.rule(), addedLineText);
    }

    private static boolean ruleLooksRelatedToLine(String rule, String addedLineText) {
        String normalizedRule = nvl(rule, "").toLowerCase();
        String line = nvl(addedLineText, "").trim();
        if (line.isBlank()) {
            return false;
        }

        return switch (normalizedRule) {
            case "long method" -> line.contains("(") && (line.contains(")") || line.endsWith("{"));
            case "long parameter list" -> line.contains("(") && countChar(line, ',') >= 3;
            case "duplicate code", "duplicate code inside a method" -> true;
            case "large class" -> looksLikeClassResponsibilityExpansion(line);
            case "feature envy" -> looksLikeForeignObjectAccess(line);
            case "message chains" -> looksLikeMessageChain(line);
            default -> false;
        };
    }

    private static boolean hasMissingRequiredFields(LlmFinding finding) {
        return finding == null
                || isBlank(finding.file())
                || isBlank(finding.rule())
                || !ALLOWED_RULES.contains(finding.rule())
                || isBlank(finding.severity())
                || isBlank(finding.note())
                || isBlank(finding.suggestedRefactoring())
                || isBlank(finding.refactoringNote());
    }

    private static boolean hasMissingTargetFields(LlmFinding finding) {
        return finding == null
                || isBlank(finding.targetType())
                || isBlank(finding.targetName())
                || (!"METHOD".equals(normalizeTargetType(finding.targetType()))
                        && !"CLASS".equals(normalizeTargetType(finding.targetType())));
    }

    private static boolean targetTypeMatchesRule(LlmFinding finding) {
        String expectedType = expectedTargetType(finding.rule());
        return expectedType != null && expectedType.equals(normalizeTargetType(finding.targetType()));
    }

    private static String expectedTargetType(String rule) {
        return switch (nvl(rule, "")) {
            case "Large Class" -> "CLASS";
            case "Long Method",
                    "Long Parameter List",
                    "Duplicate Code",
                    "Duplicate Code Inside a Method",
                    "Feature Envy",
                    "Message Chains" -> "METHOD";
            default -> null;
        };
    }

    private static String normalizeTargetType(String targetType) {
        return nvl(targetType, "").trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean looksLikeClassResponsibilityExpansion(String line) {
        return line.matches(".*\\bclass\\s+[A-Za-z_][A-Za-z0-9_]*(?:Manager|Processor|Service|Handler|Controller)?\\b.*")
                || line.matches(".*\\b(?:public|private|protected)\\s+[A-Z][A-Za-z0-9_<>]*\\s+[a-z][A-Za-z0-9_]*\\s*(?:=|;).*")
                || line.matches(".*\\b(?:public|private|protected)\\s+[A-Za-z0-9_<>\\[\\]]+\\s+[a-z][A-Za-z0-9_]*\\s*\\(.*");
    }

    private static boolean looksLikeForeignObjectAccess(String line) {
        Matcher matcher = REPEATED_FOREIGN_ACCESS_PATTERN.matcher(line);
        return matcher.find();
    }

    private static boolean looksLikeMessageChain(String line) {
        String normalized = line.replaceAll("\\s+", "");
        if (normalized.contains(".stream()")
                || normalized.contains("Optional.")
                || normalized.contains(".map(")
                || normalized.contains(".filter(")
                || normalized.contains(".collect(")
                || normalized.contains(".orElse(")
                || normalized.contains(".build()")
                || normalized.contains("assertThat(")
                || normalized.contains(".isNotNull()")
                || normalized.contains(".isEqualTo(")) {
            return false;
        }
        return MESSAGE_CHAIN_PATTERN.matcher(line).find();
    }

    private static Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(nvl(value, ""));
        while (matcher.find()) {
            addToken(tokens, matcher.group());
        }
        return tokens;
    }

    private static void addToken(Set<String> tokens, String rawToken) {
        String token = nvl(rawToken, "");
        addTokenPart(tokens, token);

        String splitCamel = token.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        Matcher matcher = TOKEN_PATTERN.matcher(splitCamel);
        while (matcher.find()) {
            addTokenPart(tokens, matcher.group());
        }
    }

    private static void addTokenPart(Set<String> tokens, String rawToken) {
        String token = nvl(rawToken, "").toLowerCase();
        boolean numeric = token.chars().allMatch(Character::isDigit);
        if ((!numeric && token.length() < 3) || GENERIC_TOKENS.contains(token)) {
            return;
        }
        tokens.add(token);
    }

    private static int countChar(String value, char target) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private void logValidationSkip(LlmFinding finding, String reason) {
        if (debug) {
            System.out.println("Discarded finding " + nvl(finding.file(), "?") + ":" + finding.line()
                    + " (" + nvl(finding.rule(), "unknown rule") + "): " + reason);
        }
    }

    private void logReanchor(LlmFinding finding, int newLine) {
        if (debug) {
            System.out.println("Re-anchored finding " + finding.file() + ":" + finding.line()
                    + " -> " + newLine + " (" + nvl(finding.rule(), "unknown rule") + ")");
        }
    }

    private static LlmFinding withLine(LlmFinding finding, int line) {
        return new LlmFinding(
                finding.file(),
                line,
                finding.rule(),
                finding.targetType(),
                finding.targetName(),
                finding.severity(),
                finding.note(),
                finding.suggestedRefactoring(),
                finding.refactoringNote());
    }

    private static String findingText(LlmFinding finding) {
        if (finding == null) {
            return "";
        }
        return nvl(finding.note(), "")
                + " "
                + nvl(finding.suggestedRefactoring(), "")
                + " "
                + nvl(finding.refactoringNote(), "");
    }

    private static String normalizePath(String path) {
        String normalized = nvl(path, "").replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private record Declaration(String name, int line) {
    }

    private record TargetDeclaration(String targetType, String targetName, int declarationLine, int startLine, int endLine) {
    }

    private record TargetFile(List<String> contentLines, List<TargetDeclaration> declarations) {
        private TargetDeclaration find(String targetType, String targetName) {
            String normalizedType = normalizeTargetType(targetType);
            String normalizedName = nvl(targetName, "").trim();
            if (normalizedType.isBlank() || normalizedName.isBlank()) {
                return null;
            }
            for (TargetDeclaration declaration : declarations) {
                if (normalizedType.equals(declaration.targetType())
                        && normalizedName.equals(declaration.targetName())) {
                    return declaration;
                }
            }
            return null;
        }
    }
}
