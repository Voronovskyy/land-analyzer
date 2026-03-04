package ua.edu.university.util;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервіс інтелектуального мультимодального аналізу територій.
 * Використовує нейронні мережі Google Gemini для генерації експертних висновків
 * у сферах урбаністики, геоморфології, гідрології та екології.
 */
public class GeminiAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalysisService.class);
    private final Client client;

    /**
     * Конструктор ініціалізує клієнта Google GenAI.
     */
    public GeminiAnalysisService() {
        this.client = new Client();
    }

    /**
     * Виконує аналіз інфраструктури та комфорту проживання.
     */
    public String getInfrastructureAnalysis(double lat, double lon) {
        String prompt = buildPrompt("Ти урбаніст",
                "Проаналізуй район %f, %f. Опиши транспортну доступність, наявність ТЦ, шкіл та лікарень у радіусі 3 км. Оціни комфорт проживання.",
                lat, lon);
        return callGemini("INFRASTRUCTURE", prompt);
    }

    /**
     * Виконує аналіз рельєфу та придатності території для будівництва.
     */
    public String getTerrainAnalysis(double lat, double lon) {
        String prompt = buildPrompt("Ти геоморфолог",
                "Проаналізуй рельєф навколо %f, %f. Опиши тип ландшафту, наявність схилів чи ярів, та складність території для будівництва.",
                lat, lon);
        return callGemini("TERRAIN", prompt);
    }

    /**
     * Аналізує гідрологічні ризики та висотний профіль.
     */
    public String getDemAnalysis(double lat, double lon) {
        String prompt = buildPrompt("Ти гідролог",
                "На основі висотних даних для %f, %f, опиши ризики підтоплення, напрямок стоку вод та загальний висотний профіль ділянки.",
                lat, lon);
        return callGemini("HYDROLOGY", prompt);
    }

    /**
     * Оцінює стан екосистеми та густоту рослинності.
     */
    public String getNdviAnalysis(double lat, double lon) {
        String prompt = buildPrompt("Ти еколог",
                "Проаналізуй рослинність за координатами %f, %f. Оціни густоту лісового покриву, стан екосистеми та екологічну привабливість ділянки.",
                lat, lon);
        return callGemini("ECOLOGY/NDVI", prompt);
    }

    /**
     * Виконує ретроспективний аналіз змін території за 10 років.
     */
    public String getSatelliteRetrospective(double lat, double lon) {
        String prompt = buildPrompt("Ти аналітик супутникових знімків",
                "Опиши динаміку забудови району %f, %f за останні 10 років. Які перспективи інвестицій у цю землю?",
                lat, lon);
        return callGemini("RETROSPECTIVE", prompt);
    }

    /**
     * Допоміжний метод для побудови уніфікованого промпту.
     */
    private String buildPrompt(String role, String task, double lat, double lon) {
        int limit = ConfigManager.getIntProperty("ai.gemini.sentence.limit");
        String lang = ConfigManager.getProperty("ai.gemini.language");
        return String.format("%s. %s. %s, до %d речень.",
                role, String.format(task, lat, lon), lang, limit);
    }

    /**
     * Виконує запит до моделі та логує результати.
     */
    private String callGemini(String type, String prompt) {
        String model = ConfigManager.getProperty("ai.gemini.model");
        long startTime = System.currentTimeMillis();
        logger.info("--- [GEMINI REQUEST: {}] ---", type);
        logger.debug("Prompt content: {}", prompt);

        try {
            GenerateContentResponse response = client.models.generateContent(model, prompt, null);
            String text = response.text();
            long duration = System.currentTimeMillis() - startTime;

            if (text == null || text.isBlank()) {
                logger.warn("Gemini повернув порожню відповідь за {} мс", duration);
                return "Аналіз тимчасово недоступний.";
            }

            logger.info("Успішна відповідь Gemini за {} мс", duration);
            return text.trim();

        } catch (Exception e) {
            logger.error("Помилка API Gemini: {}", e.getMessage());
            return "Сталася помилка при зверненні до сервісу ШІ.";
        }
    }
}