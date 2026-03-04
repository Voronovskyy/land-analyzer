package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;
import ua.edu.university.util.ConfigManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Сервіс для перетворення текстових адрес у географічні координати (геокодування).
 * Використовує API Nominatim (OpenStreetMap) для PhD модуля аналізу територій.
 */
public class GeoApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(GeoApiService.class);
    private final String baseUrl;

    /**
     * Ініціалізує сервіс, зчитуючи базову адресу API з конфігурації.
     */
    public GeoApiService() {
        super();
        this.baseUrl = ConfigManager.getProperty("api.nominatim.url");
    }

    /**
     * Отримує координати об'єкта за його текстовою адресою.
     * * @param address текстовий рядок адреси
     *
     * @return об'єкт Coordinate з широтою, довготою та геометрією (GeoJSON) або null, якщо не знайдено
     * @throws Exception при помилках мережі або кодування
     */
    public Coordinate getCoordinates(String address) throws Exception {
        if (address == null || address.isBlank()) {
            logger.warn("Спроба геокодування порожньої адреси.");
            return null;
        }

        // Зчитування параметрів з конфігурації
        String format = ConfigManager.getProperty("api.nominatim.format");
        String limit = ConfigManager.getProperty("api.nominatim.limit");
        String polygon = ConfigManager.getProperty("api.nominatim.polygon");

        // Формування безпечного URL
        String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String url = String.format("%s?q=%s&format=%s&limit=%s&polygon_geojson=%s",
                baseUrl, encodedAddress, format, limit, polygon);

        logger.info("Виконання запиту геокодування для: {}", address);
        logger.debug("URL запиту: {}", url);

        try {
            String rawJson = sendGetRequest(url);
            if (rawJson == null || rawJson.isEmpty()) {
                logger.warn("Отримана порожня відповідь для адреси: {}", address);
                return null;
            }

            JsonArray jsonArray = JsonParser.parseString(rawJson).getAsJsonArray();

            if (!jsonArray.isEmpty()) {
                JsonObject firstResult = jsonArray.get(0).getAsJsonObject();
                double lat = firstResult.get("lat").getAsDouble();
                double lon = firstResult.get("lon").getAsDouble();

                // Отримання геометрії GeoJSON, якщо вона присутня
                String geoJson = firstResult.has("geojson")
                        ? firstResult.get("geojson").toString()
                        : null;

                logger.info("Адресу успішно локалізовано: [{}, {}]", lat, lon);
                return new Coordinate(lat, lon, geoJson);
            }

        } catch (Exception e) {
            logger.error("Помилка під час обробки відповіді геокодування: {}", e.getMessage());
            throw e;
        }

        logger.warn("Об'єкт за адресою '{}' не знайдено в базі OSM.", address);
        return null;
    }
}