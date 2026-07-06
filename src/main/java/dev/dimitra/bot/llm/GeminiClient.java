package dev.dimitra.bot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.dimitra.bot.llm.LlmClient.Message;
import dev.dimitra.bot.llm.LlmClient.Result;
import dev.dimitra.bot.llm.LlmClient.Usage;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter from Google's Gemini generateContent API to the shared LlmClient
 * strategy interface.
 */
public class GeminiClient implements LlmClient {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Pattern RETRY_DELAY_SECONDS = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9]+(?:\\.[0-9]+)?)s\"");
    private static final Pattern KEY_IN_QUERY = Pattern.compile("(?i)([?&]key=)([^&\\s]+)");

    private static String maskSecrets(String s) {
        if (s == null)
            return null;
        // Masks "...?key=ABCDEFG..." -> "...?key=ABCD****"
        return KEY_IN_QUERY.matcher(s).replaceAll(m -> {
            String prefix = m.group(1);
            String key = m.group(2);
            String shown = key.length() <= 4 ? key : key.substring(0, 4);
            return prefix + shown + "****";
        });
    }

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public GeminiClient(String apiKey, String model) {
        String k = Objects.requireNonNull(apiKey, "GEMINI_API_KEY missing").trim();
        if (k.isBlank()) {
            throw new IllegalArgumentException("GEMINI_API_KEY missing/blank");
        }
        if (k.length() < 10) {
            throw new IllegalArgumentException("GEMINI_API_KEY too short");
        }
        this.apiKey = k;
        // Good default: fast & cheap text model
        this.model = (model == null || model.isBlank())
                ? "gemini-3.1-flash-lite"
                : model;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public Result chat(String systemPrompt,
            List<Message> messages,
            Map<String, Object> options) throws IOException, InterruptedException {

        ObjectNode body = mapper.createObjectNode();

        // System prompt -> Gemini system_instruction
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = mapper.createObjectNode();
            ArrayNode sysParts = mapper.createArrayNode();
            ObjectNode part = mapper.createObjectNode();
            part.put("text", systemPrompt);
            sysParts.add(part);
            sys.set("parts", sysParts);
            body.set("system_instruction", sys);
        }

        // Messages -> contents[]
        ArrayNode contents = mapper.createArrayNode();
        for (Message m : messages) {
            ObjectNode content = mapper.createObjectNode();

            // Gemini uses "user" / "model" roles
            String role = m.role();
            String gemRole;
            if ("assistant".equalsIgnoreCase(role)) {
                gemRole = "model";
            } else {
                // treat user/system/other as user-side content
                gemRole = "user";
            }
            content.put("role", gemRole);

            ArrayNode parts = mapper.createArrayNode();
            ObjectNode part = mapper.createObjectNode();
            part.put("text", m.content());
            parts.add(part);
            content.set("parts", parts);

            contents.add(content);
        }
        body.set("contents", contents);

        ObjectNode generationConfig = buildGenerationConfig(options);
        if (generationConfig != null) {
            body.set("generationConfig", generationConfig);
        }

        String url = BASE_URL + "/" + model + ":generateContent";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = sendWithRetries(req);
        int status = resp.statusCode();
        if (status == 401 || status == 403) {
            System.err.println("[WARN] Gemini authentication failed; check GEMINI_API_KEY.");
        }
        if (status / 100 != 2) {
            // body shouldn't include your key, but mask defensively anyway
            String safeBody = maskSecrets(resp.body());
            throw new IOException("Gemini error " + status + ": " + safeBody);
        }

        JsonNode root = mapper.readTree(resp.body());

        // Extract text: candidates[0].content.parts[0].text
        JsonNode candidates = root.path("candidates");
        String text = "";
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode firstCandidate = candidates.get(0);
            JsonNode content = firstCandidate.path("content");
            JsonNode parts = content.path("parts");
            if (parts.isArray() && parts.size() > 0) {
                text = parts.get(0).path("text").asText("");
            }
        }

        // Usage metadata (if present)
        JsonNode usageNode = root.path("usageMetadata");
        Integer promptTokens = nullableInt(usageNode, "promptTokenCount");
        Integer completionTokens = nullableInt(usageNode, "candidatesTokenCount");
        Integer totalTokens = nullableInt(usageNode, "totalTokenCount");
        String finishReason = firstFinishReason(root);

        return new Result(text, new Usage(promptTokens, completionTokens, totalTokens, finishReason));
    }

    private static Integer nullableInt(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value != null && value.isNumber() ? value.intValue() : null;
    }

    private static String firstFinishReason(JsonNode root) {
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null;
        }
        JsonNode finishReason = candidates.get(0).get("finishReason");
        if (finishReason == null || finishReason.isNull()) {
            return null;
        }
        String value = finishReason.asText("");
        return value.isBlank() ? null : value;
    }

    private HttpResponse<String> sendWithRetries(HttpRequest req) throws IOException, InterruptedException {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (!isRetryable(status) || attempt == MAX_RETRY_ATTEMPTS) {
                return resp;
            }

            Duration delay = retryDelay(resp.body(), attempt);
            System.err.println("[WARN] Gemini returned " + status + "; retrying in "
                    + delay.toSeconds() + "s (attempt " + attempt + "/" + MAX_RETRY_ATTEMPTS + ").");
            Thread.sleep(delay.toMillis());
        }
        throw new IOException("Gemini request failed before receiving a response");
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status / 100 == 5;
    }

    private static Duration retryDelay(String responseBody, int attempt) {
        Matcher matcher = RETRY_DELAY_SECONDS.matcher(responseBody == null ? "" : responseBody);
        if (matcher.find()) {
            double seconds = Double.parseDouble(matcher.group(1));
            return Duration.ofMillis((long) Math.ceil(seconds * 1000.0) + 1000L);
        }

        long fallbackSeconds = Math.min(60L, 5L * (1L << Math.max(0, attempt - 1)));
        return Duration.ofSeconds(fallbackSeconds);
    }

    private ObjectNode buildGenerationConfig(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        ObjectNode cfg = mapper.createObjectNode();

        Double temperature = pickDouble(options, "temperature");
        if (temperature != null) {
            cfg.put("temperature", temperature);
        }

        Integer maxOutputTokens = pickInt(options, "max_output_tokens", "maxOutputTokens", "max_tokens");
        if (maxOutputTokens != null && maxOutputTokens > 0) {
            cfg.put("maxOutputTokens", maxOutputTokens);
        }

        return cfg.size() == 0 ? null : cfg;
    }

    private static Double pickDouble(Map<String, Object> options, String... keys) {
        Object raw = firstOption(options, keys);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s) {
            try {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    return Double.parseDouble(trimmed);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Integer pickInt(Map<String, Object> options, String... keys) {
        Object raw = firstOption(options, keys);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            try {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    return Integer.parseInt(trimmed);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Object firstOption(Map<String, Object> options, String... keys) {
        if (options == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = options.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
