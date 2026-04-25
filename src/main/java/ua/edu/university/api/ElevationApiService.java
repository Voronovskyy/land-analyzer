package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;
import ua.edu.university.util.ConfigManager;

import java.util.List;

/**
 * Сервіс для отримання даних про висоту над рівнем моря (Digital Elevation Model).
 * Використовує Open-Elevation API для PhD модуля геоморфологічного аналізу.
 */
public class ElevationApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(ElevationApiService.class);

    /**
     * Отримує значення висоти для заданих координат.
     *
     * @param lat широта в градусах (WGS84)
     * @param lon довгота в градусах (WGS84)
     * @return висота в метрах над рівнем моря
     */
    public double[] getMultipleElevations(List<Coordinate> points) {
        double[] result = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            result[i] = getElevation(points.get(i).getLatitude(), points.get(i).getLongitude());
        }
        return result;
    }

    public double getElevation(double lat, double lon) {
        String baseUrl = ConfigManager.getProperty("api.elevation.url");
        double defaultElevation = ConfigManager.getDoubleProperty("api.elevation.default");

        try {
            String url = baseUrl + lat + "," + lon;
            logger.info("Запит висоти для координат: {}, {}", lat, lon);

            String response = sendGetRequest(url);
            if (response == null || response.isEmpty()) {
                throw new Exception("Порожня відповідь від сервера висот");
            }

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            JsonArray results = json.getAsJsonArray("results");

            if (results != null && !results.isEmpty()) {
                JsonObject firstResult = results.get(0).getAsJsonObject();
                if (firstResult.has("elevation")) {
                    double elevation = firstResult.get("elevation").getAsDouble();
                    logger.debug("Отримана висота: {} м", elevation);
                    return elevation;
                }
            }

            throw new Exception("Поле 'elevation' відсутнє у відповіді");
        } catch (Exception e) {
            logger.error("Помилка отримання висоти (буде використано дефолтне значення {} м): {}",
                    defaultElevation, e.getMessage());
            return defaultElevation;
        }
    }
}