package dev.dimitra.bot.analysis;

import java.util.Locale;

public enum ValidationMode {
    STRICT_LINE,
    TARGET_NAME,
    DIFF_TARGET_NAME;

    public static ValidationMode defaultForScope(AnalysisScope scope) {
        return scope == AnalysisScope.FILE ? TARGET_NAME : DIFF_TARGET_NAME;
    }

    public static ValidationMode parse(String value, AnalysisScope scope) {
        if (value == null || value.isBlank()) {
            return defaultForScope(scope);
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict-line", "strict_line", "line", "baseline" -> STRICT_LINE;
            case "target-name", "target_name", "target", "file-target" -> TARGET_NAME;
            case "diff-target-name", "diff_target_name", "diff-target", "diff_target" -> DIFF_TARGET_NAME;
            default -> throw new IllegalArgumentException(
                    "Unsupported validation mode: " + value
                            + ". Use 'strict-line', 'target-name', or 'diff-target-name'.");
        };
    }
}
