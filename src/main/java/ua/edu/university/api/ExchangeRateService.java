package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.util.ConfigManager;

public class ExchangeRateService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    public double getCurrentUsdRate() {
        String url = ConfigManager.getProperty("api.nbu.url");
        try {
            String rawJson = sendGetRequest(url);

            if (rawJson != null && !rawJson.isEmpty()) {
                JsonArray array = JsonParser.parseString(rawJson).getAsJsonArray();
                if (!array.isEmpty()) {
                    double rate = array.get(0).getAsJsonObject().get("rate").getAsDouble();
                    logger.info("Успішно отримано курс НБУ: 1 USD = {} UAH", rate);
                    return rate;
                }
            }
        } catch (Exception e) {
            logger.warn("Курс НБУ недоступний ({}). Використовуємо резервний курс.", e.getMessage());
        }
        return ConfigManager.getDoubleProperty("currency.default.rate");
    }
}
