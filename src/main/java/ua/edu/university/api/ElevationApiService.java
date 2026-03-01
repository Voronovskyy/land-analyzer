package ua.edu.university.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElevationApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(ElevationApiService.class);
    private static final String API_URL = "https://api.open-elevation.com/api/v1/lookup?locations=";

    public double getElevation(double lat, double lon) {
        try {
            String url = API_URL + lat + "," + lon;
            String response = sendGetRequest(url);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.getAsJsonArray("results")
                    .get(0).getAsJsonObject()
                    .get("elevation").getAsDouble();
        } catch (Exception e) {
            logger.error("Помилка отримання висоти: {}", e.getMessage());
            return 250.0; // Дефолтне значення для Львівщини у разі помилки
        }
    }
}