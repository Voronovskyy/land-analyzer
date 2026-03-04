package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сервіс для отримання загальнодержавних та регіональних даних.
 */
public class CountryContextService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(CountryContextService.class);
    private static final String API_URL = "https://restcountries.com/v3.1/alpha/UA";

    public JsonObject getRegionalContext() {
        try {
            String response = sendGetRequest(API_URL);
            JsonArray array = JsonParser.parseString(response).getAsJsonArray();
            JsonObject data = array.get(0).getAsJsonObject();

            JsonObject result = new JsonObject();
            result.addProperty("country", "Україна");
            result.addProperty("population", data.get("population").getAsLong());
            result.addProperty("timezone", data.getAsJsonArray("timezones").get(0).getAsString());

            return result;
        } catch (Exception e) {
            logger.error("Country API error: {}", e.getMessage());
            return null;
        }
    }
}