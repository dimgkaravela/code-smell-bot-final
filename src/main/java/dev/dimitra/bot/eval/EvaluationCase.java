package dev.dimitra.bot.eval;

import dev.dimitra.bot.model.ContextFile;

import java.util.List;

public record EvaluationCase(
        String id,
        String source,
        String repository,
        Integer prNumber,
        Boolean evaluateDetection,
        Boolean evaluateRefactoring,
        List<EvaluationFile> files,
        List<EvaluationLabel> labels) {

    public record EvaluationFile(
            String filename,
            String status,
            String previousFilename,
            Integer additions,
            Integer deletions,
            Integer changes,
            String diff,
            String diffPath,
            String fileContent,
            String fileContentPath,
            List<ContextFile> supportingFiles) {
    }

    public record EvaluationLabel(
            String file,
            Integer line,
            String rule,
            String targetType,
            String targetName,
            String severity,
            String note,
            String suggestedRefactoring,
            String refactoringNote) {
        public EvaluationLabel(String file,
                Integer line,
                String rule,
                String severity,
                String note,
                String suggestedRefactoring,
                String refactoringNote) {
            this(file, line, rule, null, null, severity, note, suggestedRefactoring, refactoringNote);
        }
    }
}
