package ua.edu.university.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.util.ConfigManager;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Сервіс для отримання кліматичних показників ділянки.
 * Використовує історичні дані Open-Meteo для аналізу умов росту рослинності.
 */
public class WeatherApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(WeatherApiService.class);

    public JsonObject getClimateStatistics(double lat, double lon) {
        String baseUrl = ConfigManager.getProperty("api.weather.url");
        String params = ConfigManager.getProperty("api.weather.params");

        String startDate = LocalDate.now().minusYears(1).withDayOfYear(1).toString();
        String endDate = LocalDate.now().minusYears(1).withDayOfYear(365).toString();

        // ДОДАНО Locale.US для коректного форматування double (крапка замість коми)
        String url = String.format(Locale.US, "%s?latitude=%f&longitude=%f&start_date=%s&end_date=%s%s",
                baseUrl, lat, lon, startDate, endDate, params);

        try {
            logger.info("Запит кліматичних даних для координат: {}, {}", lat, lon);
            String response = sendGetRequest(url);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return processWeatherData(json);
        } catch (Exception e) {
            logger.error("Помилка отримання погоди: {}", e.getMessage());
            return null;
        }
    }

    private JsonObject processWeatherData(JsonObject root) {
        JsonObject daily = root.getAsJsonObject("daily");
        JsonArray maxTemps = daily.getAsJsonArray("temperature_2m_max");
        JsonArray minTemps = daily.getAsJsonArray("temperature_2m_min");
        JsonArray rainSum = daily.getAsJsonArray("precipitation_sum");

        double totalRain = 0;
        double absoluteMax = -100;
        double absoluteMin = 100;

        for (int i = 0; i < maxTemps.size(); i++) {
            if (!maxTemps.get(i).isJsonNull()) totalRain += rainSum.get(i).getAsDouble();
            if (!maxTemps.get(i).isJsonNull()) absoluteMax = Math.max(absoluteMax, maxTemps.get(i).getAsDouble());
            if (!minTemps.get(i).isJsonNull()) absoluteMin = Math.min(absoluteMin, minTemps.get(i).getAsDouble());
        }

        JsonObject stats = new JsonObject();
        stats.addProperty("annual_precipitation", Math.round(totalRain));
        stats.addProperty("max_temp", absoluteMax);
        stats.addProperty("min_temp", absoluteMin);
        return stats;
    }
}