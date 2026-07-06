package dev.dimitra.bot.llm;

public record LlmFinding(
        String file,          // e.g., "src/main/java/.../Foo.java"
        int line,             // 1-based line number if known; 0 if not
        String rule,          // e.g., "Long Method"
        String targetType,    // "METHOD" or "CLASS"
        String targetName,    // method or class name
        String severity,      // "Blocker" | "Major" | "Minor"
        String note,          // 1-2 sentence explanation
        String suggestedRefactoring,
        String refactoringNote
) {
    public LlmFinding(String file,
            int line,
            String rule,
            String severity,
            String note,
            String suggestedRefactoring,
            String refactoringNote) {
        this(file, line, rule, null, null, severity, note, suggestedRefactoring, refactoringNote);
    }
}
