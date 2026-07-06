package dev.dimitra.bot.llm;

import dev.dimitra.bot.Env;

/**
 * Factory/router for LLM strategies.
 *
 * Each provider-specific client adapts its vendor API to the shared
 * {@link LlmClient} interface used by the analyzer.
 */
public final class LlmRouter {
    private LlmRouter() {
    }

    public static LlmClient fromEnv() {
        String provider = Env.get("LLM_PROVIDER", "gemini").trim().toLowerCase();

        switch (provider) {
            case "gemini" -> {
                String model = Env.get("GEMINI_MODEL", "gemini-3.1-flash-lite");
                return new GeminiClient(
                        Env.require("GEMINI_API_KEY"),
                        model);
            }
            default -> throw new IllegalArgumentException("Unsupported LLM_PROVIDER: " + provider
                    + ". Supported provider: gemini");
        }
    }
}
