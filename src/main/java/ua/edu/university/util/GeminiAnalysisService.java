package ua.edu.university.util;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервіс інтелектуального аналізу територій за допомогою Google Gemini.
 * Додано розширене логування запитів для PhD моніторингу.
 */
public class GeminiAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAnalysisService.class);
    private final Client client;
    private static final String MODEL = "gemini-3-flash-preview";

    public GeminiAnalysisService() {
        this.client = new Client();
    }

    // 1. Для ПЛАН-СХЕМИ (Інфраструктура)
    public String getInfrastructureAnalysis(double lat, double lon) {
        String prompt = String.format(
                "Ти урбаніст. Проаналізуй район %f, %f. Опиши транспортну доступність, " +
                        "наявність ТЦ, шкіл та лікарень у радіусі 3 км. Оціни комфорт проживання. " +
                        "Українською, до 5 речень.", lat, lon
        );
        return callGemini("INFRASTRUCTURE", prompt);
    }

    // 2. Для РЕЛЬЄФУ (Геоморфологія)
    public String getTerrainAnalysis(double lat, double lon) {
        String prompt = String.format(
                "Ти геоморфолог. Проаналізуй рельєф навколо %f, %f. Опиши тип ландшафту, " +
                        "наявність схилів чи ярів, та складність території для будівництва. " +
                        "Українською, до 5 речень.", lat, lon
        );
        return callGemini("TERRAIN", prompt);
    }

    // 3. Для МОДЕЛІ ВИСОТ (Гідрологія)
    public String getDemAnalysis(double lat, double lon) {
        String prompt = String.format(
                "Ти гідролог. На основі висотних даних для %f, %f, опиши ризики підтоплення, " +
                        "напрямок стоку вод та загальний висотний профіль ділянки. " +
                        "Українською, до 5 речень.", lat, lon
        );
        return callGemini("HYDROLOGY", prompt);
    }

    // 4. Для ВЕГЕТАЦІЇ (Екологія)
    public String getNdviAnalysis(double lat, double lon) {
        String prompt = String.format(
                "Ти еколог. Проаналізуй рослинність за координатами %f, %f. Оціни густоту " +
                        "лісового покриву, стан екосистеми та екологічну привабливість ділянки. " +
                        "Українською, до 5 речень.", lat, lon
        );
        return callGemini("ECOLOGY/NDVI", prompt);
    }

    // 5. Для СУПУТНИКА (Ретроспектива)
    public String getSatelliteRetrospective(double lat, double lon) {
        String prompt = String.format(
                "Ти аналітик супутникових знімків. Опиши динаміку забудови району %f, %f " +
                        "за останні 10 років. Які перспективи інвестицій у цю землю? " +
                        "Українською, до 4 речень.", lat, lon
        );
        return callGemini("RETROSPECTIVE", prompt);
    }

    /**
     * Виконує запит до Gemini з детальним логуванням часу та змісту.
     */
    private String callGemini(String type, String prompt) {
        long startTime = System.currentTimeMillis();

        logger.info("--- [GEMINI API CALL START: {}] ---", type);
        logger.info("PROMPT: {}", prompt);

        try {
            GenerateContentResponse response = client.models.generateContent(MODEL, prompt, null);
            String text = response.text();

            long duration = System.currentTimeMillis() - startTime;

            if (text == null || text.isEmpty()) {
                logger.warn("--- [GEMINI API EMPTY RESPONSE] --- [Duration: {} ms]", duration);
                return "Аналіз недоступний.";
            }

            logger.info("--- [GEMINI API SUCCESS] --- [Duration: {} ms ({} sec)]",
                    duration, String.format("%.2f", duration / 1000.0));
            logger.debug("RESPONSE: {}", text); // Повний текст у debug логах

            return text;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("--- [GEMINI API ERROR] --- [Duration: {} ms]", duration);
            logger.error("Error Message: {}", e.getMessage());
            return "Помилка отримання даних від ШІ.";
        }
    }
}