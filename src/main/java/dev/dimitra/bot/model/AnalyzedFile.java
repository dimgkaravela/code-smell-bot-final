package dev.dimitra.bot.model;

import java.util.List;

public record AnalyzedFile(
        String filename,
        String status,
        String previousFilename,
        Integer additions,
        Integer deletions,
        Integer changes,
        String diff,
        String fileContent,
        List<ContextFile> supportingFiles,
        boolean syntheticDiff) {
    public AnalyzedFile(
            String filename,
            String status,
            String previousFilename,
            Integer additions,
            Integer deletions,
            Integer changes,
            String diff,
            String fileContent,
            List<ContextFile> supportingFiles) {
        this(filename, status, previousFilename, additions, deletions, changes, diff, fileContent, supportingFiles,
                false);
    }
}
