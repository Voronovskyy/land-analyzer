package ua.edu.university.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервіс для розрахунку тривалості світлового дня (інсоляції).
 */
public class InsolationApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(InsolationApiService.class);

    public String getDayLength(double lat, double lon) {
        try {
            String url = String.format(java.util.Locale.US,
                    "https://api.sunrise-sunset.org/json?lat=%f&lng=%f&formatted=0", lat, lon);
            String response = sendGetRequest(url);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            int totalSeconds = json.getAsJsonObject("results").get("day_length").getAsInt();

            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;

            return String.format("%d год. %d хв.", hours, minutes);
        } catch (Exception e) {
            logger.error("Помилка парсингу тривалості дня: {}", e.getMessage());
            return "н/д";
        }
    }
}