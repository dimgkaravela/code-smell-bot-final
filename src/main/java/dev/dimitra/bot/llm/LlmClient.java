package dev.dimitra.bot.llm;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Strategy interface used by the analyzer for chat-based LLM calls.
 */
public interface LlmClient {
    record Message(String role, String content) {
    }

    record Usage(Integer promptTokens, Integer outputTokens, Integer totalTokens, String finishReason) {
        public Usage(Integer promptTokens, Integer outputTokens) {
            this(promptTokens, outputTokens, null, null);
        }
    }

    record Result(String text, Usage usage) {
    }

    default String modelName() {
        return "";
    }

    Result chat(String systemPrompt, List<Message> messages, Map<String, Object> options)
            throws IOException, InterruptedException;
}
