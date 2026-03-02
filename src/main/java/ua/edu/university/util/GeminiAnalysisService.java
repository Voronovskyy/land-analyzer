package ua.edu.university.util;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

public class GeminiAnalysisService {
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
                        "Українською, до 4 речень.", lat, lon
        );
        return callGemini(prompt);
    }

    // 2. Для РЕЛЬЄФУ (Геоморфологія)
    public String getTerrainAnalysis(double lat, double lon) {
        String prompt = String.format(
                "Ти геоморфолог. Проаналізуй рельєф навколо %f, %f. Опиши тип ландшафту, " +
                        "наявність схилів чи ярів, та складність території для будівництва. " +
                        "Українською, до 4 речень.", lat, lon
        );
        return callGemini(prompt);
    }

    // 3. Для МОДЕЛІ ВИСОТ (Гідрологія)
    public String getDemAnalysis(double lat, double lon) {
        String prompt = String.format(
                "Ти гідролог. На основі висотних даних для %f, %f, опиши ризики підтоплення, " +
                        "напрямок стоку вод та загальний висотний профіль ділянки. " +
                        "Українською, до 4 речень.", lat, lon
        );
        return callGemini(prompt);
    }

    // 4. Для ВЕГЕТАЦІЇ (Екологія)
    public String getNdviAnalysis(double lat, double lon) {
        String prompt = String.format(
                "Ти еколог. Проаналізуй рослинність за координатами %f, %f. Оціни густоту " +
                        "лісового покриву, стан екосистеми та екологічну привабливість ділянки. " +
                        "Українською, до 4 речень.", lat, lon
        );
        return callGemini(prompt);
    }

    // 5. Для СУПУТНИКА (Ретроспектива)
    public String getSatelliteRetrospective(double lat, double lon) {
        String prompt = String.format(
                "Ти аналітик супутникових знімків. Опиши динаміку забудови району %f, %f " +
                        "за останні 10 років. Які перспективи інвестицій у цю землю? " +
                        "Українською, до 4 речень.", lat, lon
        );
        return callGemini(prompt);
    }

    private String callGemini(String prompt) {
        try {
            GenerateContentResponse response = client.models.generateContent(MODEL, prompt, null);
            String text = response.text();
            // Запобігаємо null або порожнім відповідям
            return (text == null || text.isEmpty()) ? "Аналіз недоступний." : text;
        } catch (Exception e) {
            System.err.println("Gemini Error: " + e.getMessage());
            return "Помилка отримання даних від ШІ.";
        }
    }
}