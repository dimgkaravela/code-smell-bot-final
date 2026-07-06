package dev.dimitra.bot.analysis;

public record TokenUsageEntry(
        String dataset,
        String caseId,
        String scope,
        String validationMode,
        String matchMode,
        String model,
        int maxFilesPerChunk,
        int maxChunkChars,
        int maxFileContentChars,
        int chunkIndex,
        int filesInChunk,
        int promptChars,
        Integer promptTokens,
        Integer outputTokens,
        Integer totalTokens,
        String finishReason,
        boolean parseOk,
        int warningCount) {
}
