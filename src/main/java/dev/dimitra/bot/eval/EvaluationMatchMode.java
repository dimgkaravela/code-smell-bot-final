package dev.dimitra.bot.eval;

import dev.dimitra.bot.analysis.ValidationMode;

import java.util.Locale;

public enum EvaluationMatchMode {
    LINE,
    TARGET,
    HYBRID;

    public static EvaluationMatchMode defaultForValidationMode(ValidationMode validationMode) {
        return validationMode == ValidationMode.STRICT_LINE ? LINE : TARGET;
    }

    public static EvaluationMatchMode parse(String value, ValidationMode validationMode) {
        if (value == null || value.isBlank()) {
            return defaultForValidationMode(validationMode);
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "line", "strict-line", "strict_line", "baseline" -> LINE;
            case "target", "target-name", "target_name" -> TARGET;
            case "hybrid" -> HYBRID;
            default -> throw new IllegalArgumentException(
                    "Unsupported match mode: " + value + ". Use 'line', 'target', or 'hybrid'.");
        };
    }
}
