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

public class ClaudeAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeAnalysisService.class);

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final HttpClient httpClient;
    private final String apiKey;

    public ClaudeAnalysisService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.apiKey = System.getenv("ANTHROPIC_API_KEY");
    }

    public String getInfrastructureAnalysis(double lat, double lon) {
        return call("INFRASTRUCTURE", buildPrompt(
                "Ти урбаніст",
                "Проаналізуй район %f, %f. Опиши транспортну доступність, наявність ТЦ, шкіл та лікарень у радіусі 3 км. Оціни комфорт проживання.",
                lat, lon));
    }

    public String getTerrainAnalysis(double lat, double lon) {
        return call("TERRAIN", buildPrompt(
                "Ти геоморфолог",
                "Проаналізуй рельєф навколо %f, %f. Опиши тип ландшафту, наявність схилів чи ярів та складність території для будівництва.",
                lat, lon));
    }

    public String getDemAnalysis(double lat, double lon) {
        return call("DEM", buildPrompt(
                "Ти гідролог",
                "На основі висотних даних для %f, %f опиши ризики підтоплення, напрямок стоку вод та загальний висотний профіль ділянки.",
                lat, lon));
    }

    public String getNdviAnalysis(double lat, double lon) {
        return call("NDVI", buildPrompt(
                "Ти еколог",
                "Проаналізуй рослинність за координатами %f, %f. Оціни густоту лісового покриву, стан екосистеми та екологічну привабливість ділянки.",
                lat, lon));
    }

    public String getSatelliteRetrospective(double lat, double lon) {
        return call("RETROSPECTIVE", buildPrompt(
                "Ти досвідчений аналітик супутникових знімків та інвестиційний консультант з нерухомості",
                "Проаналізуй динаміку забудови та розвитку району з координатами %f, %f за останні 10 років на основі відомих тенденцій. " +
                "Опиши: 1) зміни в землекористуванні та щільності забудови, 2) інфраструктурний розвиток, " +
                "3) інвестиційну привабливість та перспективи зростання вартості. " +
                "Надай конкретний та впевнений висновок. " +
                "Не згадуй методологічні застереження, обмеження аналізу, необхідність додаткових інструментів чи технологій.",
                lat, lon));
    }

    public String getSlopeAnalysis(double lat, double lon) {
        return call("SLOPE", buildPrompt(
                "Ти геотехнічний інженер та геоморфолог",
                "Проаналізуй рельєф та схили ділянки за координатами %f, %f. Оціни крутизну схилів, ризики ерозії ґрунту, стабільність основи та придатність для будівництва або сільськогосподарської обробки.",
                lat, lon));
    }

    // ─── Внутрішні методи ────────────────────────────────────────────────

    private String buildPrompt(String role, String task, double lat, double lon) {
        int limit = ConfigManager.getIntProperty("ai.claude.sentence.limit");
        String lang = ConfigManager.getProperty("ai.claude.language");
        return String.format(
                "%s. %s. %s, до %d речень. " +
                "Науковий стиль, тільки факти та характеристики. " +
                "Без порад, рекомендацій, власних оцінок та фраз типу «варто», «краще», «рекомендується».",
                role, String.format(task, lat, lon), lang, limit);
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

        long start = System.currentTimeMillis();
        logger.info("--- [CLAUDE REQUEST: {}] ---", type);
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long ms = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                logger.error("Claude API помилка {}: {}", response.statusCode(), response.body());
                return "Помилка Claude API: " + response.statusCode();
            }

            String text = JsonParser.parseString(response.body())
                    .getAsJsonObject()
                    .getAsJsonArray("content")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            logger.info("Відповідь Claude за {} мс", ms);
            return text.trim();

        } catch (Exception e) {
            logger.error("Помилка Claude API: {}", e.getMessage());
            return "Сталася помилка при зверненні до Claude.";
        }
    }
}
