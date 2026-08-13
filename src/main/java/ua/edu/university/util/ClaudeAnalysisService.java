package ua.edu.university.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Клієнт Claude API (Anthropic) для AI-аналізу геопросторових даних.
 * Надає 6 методів аналізу: інфраструктура, рельєф, DEM, NDVI,
 * супутниковий ретроспективний знімок і схили. Промти завантажуються
 * з classpath-ресурсів через {@link PromptLoader}. API-ключ береться
 * зі змінної середовища {@code ANTHROPIC_API_KEY}.
 */
public class ClaudeAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeAnalysisService.class);

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 800;

    private final HttpClient httpClient;
    private final String apiKey;

    public ClaudeAnalysisService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
    }

    public String getInfrastructureAnalysis(double lat, double lon, String facts) {
        PromptLoader.PromptTemplate t = PromptLoader.load("infrastructure");
        return call("INFRASTRUCTURE", buildPrompt(t.role(), t.task(), lat, lon, facts));
    }

    public String getTerrainAnalysis(double lat, double lon, String facts) {
        PromptLoader.PromptTemplate t = PromptLoader.load("terrain");
        return call("TERRAIN", buildPrompt(t.role(), t.task(), lat, lon, facts));
    }

    public String getDemAnalysis(double lat, double lon, String facts) {
        PromptLoader.PromptTemplate t = PromptLoader.load("dem");
        return call("DEM", buildPrompt(t.role(), t.task(), lat, lon, facts));
    }

    public String getNdviAnalysis(double lat, double lon, String facts) {
        PromptLoader.PromptTemplate t = PromptLoader.load("ndvi");
        return call("NDVI", buildPrompt(t.role(), t.task(), lat, lon, facts));
    }

    public String getSatelliteRetrospective(double lat, double lon, String facts) {
        PromptLoader.PromptTemplate t = PromptLoader.load("satellite");
        return call("RETROSPECTIVE", buildPrompt(t.role(), t.task(), lat, lon, facts));
    }

    public String getSlopeAnalysis(double lat, double lon, String facts) {
        PromptLoader.PromptTemplate t = PromptLoader.load("slope");
        return call("SLOPE", buildPrompt(t.role(), t.task(), lat, lon, facts));
    }

    // ─── Внутрішні методи ────────────────────────────────────────────────

    /**
     * @param facts блок фактичних даних від {@link PlotFactsBuilder}; порожній
     *              рядок допустимий, але тоді модель спирається лише на
     *              координати і схильна домислювати деталі місцевості.
     */
    private String buildPrompt(String role, String task, double lat, double lon, String facts) {
        int limit = ConfigManager.getIntProperty("ai.claude.sentence.limit");
        String lang = ConfigManager.getProperty("ai.claude.language");
        return String.format(
                "%s. %s.%s %s, до %d речень. " +
                        "Науковий стиль, тільки факти та характеристики. " +
                        "Без порад, рекомендацій, власних оцінок та фраз типу «варто», «краще», «рекомендується».",
                role, String.format(task, lat, lon), facts == null ? "" : facts, lang, limit);
    }

    private String call(String type, String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("ANTHROPIC_API_KEY не встановлено");
            return "API ключ Claude не налаштовано.";
        }

        String model = ConfigManager.getProperty("ai.claude.model");
        int maxTokens = ConfigManager.getIntProperty("ai.claude.max.tokens");

        JsonObject msgObj = new JsonObject();
        msgObj.addProperty("role", "user");
        msgObj.addProperty("content", prompt);

        JsonArray messages = new JsonArray();
        messages.add(msgObj);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long start = System.currentTimeMillis();
            logger.info("--- [CLAUDE REQUEST: {}] (спроба {}/{}) ---", type, attempt, MAX_ATTEMPTS);
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long ms = System.currentTimeMillis() - start;

                if (response.statusCode() == 200) {
                    String text = extractText(response.body());
                    if (text == null || text.isBlank()) {
                        logger.warn("Claude API: відповідь без текстового блоку (спроба {}/{}): {}",
                                attempt, MAX_ATTEMPTS, truncate(response.body()));
                        if (attempt == MAX_ATTEMPTS) {
                            return "Сталася помилка при зверненні до Claude.";
                        }
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    logger.info("Відповідь Claude за {} мс (спроба {})", ms, attempt);
                    return text.trim();
                }

                logger.warn("Claude API помилка {} (спроба {}/{}): {}",
                        response.statusCode(), attempt, MAX_ATTEMPTS, response.body());
                if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                    return "Помилка Claude API: " + response.statusCode();
                }
            } catch (Exception e) {
                logger.warn("Помилка Claude API (спроба {}/{}) [{}]: {}",
                        attempt, MAX_ATTEMPTS, e.getClass().getSimpleName(), e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    logger.error("Claude API: усі спроби вичерпано для {}", type, e);
                    return "Сталася помилка при зверненні до Claude.";
                }
            }
            sleepBeforeRetry(attempt);
        }
        return "Сталася помилка при зверненні до Claude.";
    }

    /**
     * Витягує текст з усіх блоків типу "text" у content[]. Не бере
     * content[0] наосліп — Claude інколи повертає перед текстом інші
     * блоки (напр. "thinking"), і в них немає поля "text", що раніше
     * призводило до NullPointerException навіть при статусі 200.
     */
    private String extractText(String responseBody) {
        JsonArray content = JsonParser.parseString(responseBody)
                .getAsJsonObject()
                .getAsJsonArray("content");
        if (content == null || content.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JsonObject block = content.get(i).getAsJsonObject();
            if (block.has("text") && block.get("text").isJsonPrimitive()) {
                sb.append(block.get("text").getAsString());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String truncate(String s) {
        if (s == null || s.length() <= 500) return s;
        return s.substring(0, 500) + "...";
    }

    /** 429 (rate limit), 5xx (включно з 529 overloaded) — варто повторити; 4xx (напр. 401/400) — ні. */
    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BASE_DELAY_MS * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
