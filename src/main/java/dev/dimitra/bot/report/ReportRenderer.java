package dev.dimitra.bot.report;

import dev.dimitra.bot.llm.LlmFinding;
import dev.dimitra.bot.model.AnalyzedFile;
import dev.dimitra.bot.model.ChangedFile;

import java.nio.file.Path;
import java.util.List;

public final class ReportRenderer {
    private ReportRenderer() {
    }

    public static DiffReport buildDiffReport(String repository,
            int prNumber,
            int totalFilesInPr,
            List<ChangedFile> files,
            int maxFiles,
            List<AnalyzedFile> analyzedJavaFiles,
            Path fullDiffPath) {
        int totalFiles = files.size();
        int totalAdditions = files.stream().mapToInt(f -> safeInt(f.additions())).sum();
        int totalDeletions = files.stream().mapToInt(f -> safeInt(f.deletions())).sum();
        int javaFilesCount = analyzedJavaFiles.size();
        int javaWithDiff = (int) analyzedJavaFiles.stream()
                .filter(f -> f.diff() != null && !f.diff().isBlank())
                .count();
        int javaWithContent = (int) analyzedJavaFiles.stream()
                .filter(f -> f.fileContent() != null && !f.fileContent().isBlank())
                .count();
        int supportingFilesFetched = analyzedJavaFiles.stream()
                .mapToInt(f -> f.supportingFiles() == null ? 0 : f.supportingFiles().size())
                .sum();

        DiffReport report = new DiffReport();
        report.repository = repository;
        report.prNumber = prNumber;
        report.totalFilesInPr = totalFilesInPr;
        report.totalFilesAnalyzed = totalFiles;
        report.maxFilesLimitApplied = maxFiles > 0 && totalFiles < totalFilesInPr;
        report.totalAdditions = totalAdditions;
        report.totalDeletions = totalDeletions;
        report.javaFiles = javaFilesCount;
        report.javaFilesWithDiff = javaWithDiff;
        report.javaFilesWithContent = javaWithContent;
        report.supportingFilesFetched = supportingFilesFetched;
        report.fullDiffArtifact = fullDiffPath.toString();
        report.javaChangedFiles = analyzedJavaFiles.stream()
                .map(f -> new JavaChanged(
                        nvl(f.filename(), "?"),
                        nvl(f.status(), "?"),
                        nvl(f.previousFilename(), ""),
                        safeInt(f.additions()),
                        safeInt(f.deletions()),
                        safeInt(f.changes()),
                        previewText(f.diff(), 400),
                        f.fileContent() != null && !f.fileContent().isBlank(),
                        f.supportingFiles() == null ? 0 : f.supportingFiles().size()))
                .toList();

        return report;
    }

    public static String renderMarkdown(List<LlmFinding> findings, List<String> warnings) {
        StringBuilder md = new StringBuilder();
        md.append("## Code Smell Report (LLM)\n");

        if (warnings != null && !warnings.isEmpty()) {
            md.append("> Warning: ")
                    .append(warnings.size())
                    .append(" analysis chunk(s) returned invalid JSON. Findings may be incomplete.\n\n");
        }

        if (findings == null || findings.isEmpty()) {
            md.append("No diff-scoped smells found in the analyzed Java files.\n");
            return md.toString();
        }

        md.append("| File | Line | Rule | Target Type | Target Name | Severity | Note | Suggested Refactoring |\n");
        md.append("|---|---:|---|---|---|---|---|---|\n");
        for (LlmFinding finding : findings) {
            md.append("| ").append(escapeMd(nvl(finding.file(), "?"))).append(" | ")
                    .append(finding.line()).append(" | ")
                    .append(escapeMd(nvl(finding.rule(), ""))).append(" | ")
                    .append(escapeMd(nvl(finding.targetType(), ""))).append(" | ")
                    .append(escapeMd(nvl(finding.targetName(), ""))).append(" | ")
                    .append(escapeMd(nvl(finding.severity(), ""))).append(" | ")
                    .append(escapeMd(nvl(finding.note(), ""))).append(" | ")
                    .append(escapeMd(nvl(finding.suggestedRefactoring(), ""))).append(" |\n");
        }
        appendRefactoringNotes(md, findings);
        return md.toString();
    }

    private static void appendRefactoringNotes(StringBuilder md, List<LlmFinding> findings) {
        List<LlmFinding> findingsWithNotes = findings.stream()
                .filter(finding -> finding.refactoringNote() != null && !finding.refactoringNote().isBlank())
                .toList();
        if (findingsWithNotes.isEmpty()) {
            return;
        }

        md.append("\n### Refactoring notes\n\n");
        for (LlmFinding finding : findingsWithNotes) {
            md.append("<details>\n")
                    .append("<summary>")
                    .append(escapeHtml(nvl(finding.file(), "?")))
                    .append(":")
                    .append(finding.line())
                    .append(" \u2014 ")
                    .append(escapeHtml(nvl(finding.rule(), "")))
                    .append("</summary>\n\n")
                    .append(finding.refactoringNote().trim())
                    .append("\n\n</details>\n\n");
        }
    }

    public static String renderConsoleSummary(String repository,
            int prNumber,
            int javaFilesCount,
            List<LlmFinding> findings,
            List<String> warnings,
            int chunksAnalyzed) {
        List<LlmFinding> safeFindings = findings == null ? List.of() : findings;
        StringBuilder sb = new StringBuilder();
        sb.append("Code Smell Bot\n");
        sb.append("Repo: ").append(nvl(repository, "?")).append("\n");
        sb.append("PR: #").append(prNumber).append("\n");
        sb.append("Java files analyzed: ").append(javaFilesCount).append("\n");
        sb.append("Chunks analyzed: ").append(chunksAnalyzed).append("\n");

        if (warnings != null && !warnings.isEmpty()) {
            sb.append("Warnings: ").append(warnings.size()).append("\n");
            for (String warning : warnings) {
                sb.append("- ").append(warning).append("\n");
            }
        }

        sb.append("\nFindings: ").append(safeFindings.size()).append("\n\n");
        if (safeFindings.isEmpty()) {
            sb.append("No code smells detected.");
            return sb.toString();
        }

        for (int i = 0; i < safeFindings.size(); i++) {
            LlmFinding finding = safeFindings.get(i);
            sb.append("[").append(i + 1).append("] ")
                    .append(nvl(finding.severity(), "Minor"))
                    .append(" - ")
                    .append(nvl(finding.rule(), "Unspecified"))
                    .append("\n");
            sb.append("File: ").append(nvl(finding.file(), "?"));
            if (finding.line() > 0) {
                sb.append(":").append(finding.line());
            }
            sb.append("\n");
            sb.append("Note: ").append(nvl(finding.note(), "No explanation provided.")).append("\n");
            if (finding.suggestedRefactoring() != null && !finding.suggestedRefactoring().isBlank()) {
                sb.append("Suggested refactoring: ").append(finding.suggestedRefactoring()).append("\n");
            }
            if (finding.refactoringNote() != null && !finding.refactoringNote().isBlank()) {
                sb.append("Refactoring note: ").append(finding.refactoringNote()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private static String previewText(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "\n... [truncated]";
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String escapeMd(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }

    private static String escapeHtml(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static class JavaChanged {
        public String filename;
        public String status;
        public String previousFilename;
        public int additions;
        public int deletions;
        public int changes;
        public String diffPreview;
        public boolean hasFileContent;
        public int supportingFiles;

        JavaChanged(String filename,
                String status,
                String previousFilename,
                int additions,
                int deletions,
                int changes,
                String diffPreview,
                boolean hasFileContent,
                int supportingFiles) {
            this.filename = filename;
            this.status = status;
            this.previousFilename = previousFilename;
            this.additions = additions;
            this.deletions = deletions;
            this.changes = changes;
            this.diffPreview = diffPreview;
            this.hasFileContent = hasFileContent;
            this.supportingFiles = supportingFiles;
        }
    }

    public static class DiffReport {
        public String repository;
        public int prNumber;
        public int totalFilesInPr;
        public int totalFilesAnalyzed;
        public boolean maxFilesLimitApplied;
        public int totalAdditions;
        public int totalDeletions;
        public int javaFiles;
        public int javaFilesWithDiff;
        public int javaFilesWithContent;
        public int supportingFilesFetched;
        public String fullDiffArtifact;
        public List<JavaChanged> javaChangedFiles;
    }
}
