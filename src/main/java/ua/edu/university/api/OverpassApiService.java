package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Сервіс для просторового аналізу об'єктів навколо ділянки (OSM Overpass).
 * Дозволяє знайти відстань до ЛЕП, доріг, водойм та лісів.
 */
public class OverpassApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(OverpassApiService.class);
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter?data=";

    public JsonObject getInfrastructureDetails(double lat, double lon) {
        // Запит на пошук ЛЕП, головних доріг та водних об'єктів у радіусі 1000м
        String query = String.format(Locale.US, "[out:json];" +
                "(" +
                "  node[\"power\"=\"line\"](around:1000," + lat + "," + lon + ");" +
                "  way[\"highway\"=\"primary\"](around:1000," + lat + "," + lon + ");" +
                "  way[\"waterway\"](around:1000," + lat + "," + lon + ");" +
                ");" +
                "out body;");

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String response = sendGetRequest(OVERPASS_URL + encodedQuery);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return processElements(json);
        } catch (Exception e) {
            logger.error("Overpass API error: {}", e.getMessage());
            return new JsonObject();
        }
    }

    private JsonObject processElements(JsonObject root) {
        JsonArray elements = root.getAsJsonArray("elements");
        JsonObject result = new JsonObject();

        boolean hasPower = false;
        boolean hasWater = false;

        for (int i = 0; i < elements.size(); i++) {
            JsonObject el = elements.get(i).getAsJsonObject();
            if (el.has("tags")) {
                JsonObject tags = el.getAsJsonObject("tags");
                if (tags.has("power")) hasPower = true;
                if (tags.has("waterway")) hasWater = true;
            }
        }

        result.addProperty("power_nearby", hasPower);
        result.addProperty("water_nearby", hasWater);
        result.addProperty("objects_count", elements.size());
        return result;
    }
}