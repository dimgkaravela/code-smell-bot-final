package dev.dimitra.bot.analysis;

public enum AnalysisScope {
    DIFF,
    FILE;

    public static AnalysisScope parse(String value) {
        if (value == null || value.isBlank()) {
            return DIFF;
        }

        return switch (value.trim().toLowerCase()) {
            case "file", "whole-file", "whole_file", "full-file", "full_file" -> FILE;
            case "diff", "pr-diff", "pr_diff", "changed-lines", "changed_lines" -> DIFF;
            default -> throw new IllegalArgumentException(
                    "Unsupported analysis scope: " + value + ". Use 'diff' or 'file'.");
        };
    }
}
