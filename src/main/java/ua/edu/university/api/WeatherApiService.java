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

    public JsonObject getMonthlyData(double lat, double lon) {
        String baseUrl = ConfigManager.getProperty("api.weather.url");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate start = yesterday.minusDays(29);

        String url = String.format(Locale.US,
                "%s?latitude=%f&longitude=%f&start_date=%s&end_date=%s&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto",
                baseUrl, lat, lon, start.toString(), yesterday.toString());

        try {
            logger.info("Запит 30-денних даних для: {}, {}", lat, lon);
            String response = sendGetRequest(url);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            return json.has("daily") ? json.getAsJsonObject("daily") : null;
        } catch (Exception e) {
            logger.error("Помилка отримання 30-денних даних: {}", e.getMessage());
            return null;
        }
    }

    public JsonObject getYearlyMonthlyData(double lat, double lon) {
        String baseUrl = ConfigManager.getProperty("api.weather.url");
        int prevYear    = LocalDate.now().getYear() - 1;
        String startDate = prevYear + "-01-01";
        String endDate   = prevYear + "-12-31";
        String url = String.format(Locale.US,
                "%s?latitude=%f&longitude=%f&start_date=%s&end_date=%s&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=auto",
                baseUrl, lat, lon, startDate, endDate);
        try {
            String response = sendGetRequest(url);
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if (!json.has("daily")) return null;
            JsonObject daily = json.getAsJsonObject("daily");
            JsonArray times   = daily.getAsJsonArray("time");
            JsonArray tmax    = daily.getAsJsonArray("temperature_2m_max");
            JsonArray tmin    = daily.getAsJsonArray("temperature_2m_min");
            JsonArray precip  = daily.getAsJsonArray("precipitation_sum");

            double[] tmaxSum = new double[12], tminSum = new double[12], precipSum = new double[12];
            int[]    cnt     = new int[12];
            for (int i = 0; i < times.size(); i++) {
                int m = Integer.parseInt(times.get(i).getAsString().substring(5, 7)) - 1;
                if (!tmax.get(i).isJsonNull())   { tmaxSum[m]   += tmax.get(i).getAsDouble();   cnt[m]++; }
                if (!tmin.get(i).isJsonNull())   { tminSum[m]   += tmin.get(i).getAsDouble(); }
                if (!precip.get(i).isJsonNull()) { precipSum[m] += precip.get(i).getAsDouble(); }
            }
            String[] names = {"Січ","Лют","Бер","Кві","Тра","Чер","Лип","Сер","Вер","Жов","Лис","Гру"};
            JsonArray mNames = new JsonArray(), tmaxAvg = new JsonArray(),
                      tminAvg = new JsonArray(), pSum = new JsonArray();
            for (int m = 0; m < 12; m++) {
                mNames.add(names[m]);
                int c = cnt[m] > 0 ? cnt[m] : 1;
                tmaxAvg.add(Math.round(tmaxSum[m] / c * 10.0) / 10.0);
                tminAvg.add(Math.round(tminSum[m] / c * 10.0) / 10.0);
                pSum.add(Math.round(precipSum[m] * 10.0) / 10.0);
            }
            JsonObject result = new JsonObject();
            result.add("months",     mNames);
            result.add("tmax_avg",   tmaxAvg);
            result.add("tmin_avg",   tminAvg);
            result.add("precip_sum", pSum);
            return result;
        } catch (Exception e) {
            logger.error("getYearlyMonthlyData error: {}", e.getMessage());
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