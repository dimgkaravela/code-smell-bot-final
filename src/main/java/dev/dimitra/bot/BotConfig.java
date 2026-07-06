package dev.dimitra.bot;

import dev.dimitra.bot.analysis.AnalysisScope;
import dev.dimitra.bot.analysis.ValidationMode;

import java.util.LinkedHashMap;
import java.util.Map;

public record BotConfig(
        String token,
        String repository,
        String owner,
        String repo,
        int prNumber,
        int maxFiles,
        boolean postComment,
        boolean debugSmells,
        boolean fetchContents,
        boolean fetchRelatedFiles,
        int maxFilesPerChunk,
        int maxChunkChars,
        int maxFileContentChars,
        int maxRelatedFilesPerFile,
        int maxRelatedFileChars,
        AnalysisScope analysisScope,
        ValidationMode validationMode,
        Map<String, Object> llmOptions) {

    public static BotConfig fromEnv() {
        String token = Env.require("GITHUB_TOKEN");
        String repository = Env.require("REPOSITORY");
        int prNumber = Env.getInt("PR_NUMBER", -1);

        if (prNumber <= 0) {
            throw new IllegalArgumentException("PR_NUMBER must be > 0");
        }

        String[] repositoryParts = repository.split("/");
        if (repositoryParts.length != 2) {
            throw new IllegalArgumentException("REPOSITORY must be 'owner/repo'");
        }

        AnalysisScope analysisScope = AnalysisScope.parse(Env.get("ANALYSIS_SCOPE", "diff"));
        ValidationMode validationMode = ValidationMode.parse(Env.get("VALIDATION_MODE"), analysisScope);

        return new BotConfig(
                token,
                repository,
                repositoryParts[0],
                repositoryParts[1],
                prNumber,
                Env.getInt("MAX_FILES", 0),
                Env.getBoolean("POST_COMMENT", true),
                Env.getBoolean("DEBUG_SMELLS", false),
                Env.getBoolean("FETCH_CONTENTS", true),
                Env.getBoolean("FETCH_RELATED_FILES", true),
                Env.getInt("LLM_MAX_FILES_PER_CHUNK", 4),
                Env.getInt("LLM_MAX_PATCH_CHARS", 18000),
                Env.getInt("MAX_FILE_CONTENT_CHARS", 12000),
                Env.getInt("MAX_RELATED_FILES_PER_FILE", 2),
                Env.getInt("MAX_RELATED_FILE_CHARS", 4000),
                analysisScope,
                validationMode,
                buildLlmOptionsFromEnv());
    }

    private static Map<String, Object> buildLlmOptionsFromEnv() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", Env.getDouble("LLM_TEMPERATURE", 0.0));
        options.put("max_tokens", Env.getInt("LLM_MAX_TOKENS", 1200));

        return options;
    }
}
