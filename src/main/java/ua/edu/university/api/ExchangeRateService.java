package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.util.ConfigManager;

/**
 * Сервіс для отримання актуального курсу валют (USD до UAH).
 * Підтримує основне джерело (ПриватБанк) та резервне (НБУ) для забезпечення
 * точності фінансових розрахунків у PhD модулі.
 */
public class ExchangeRateService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    /**
     * Отримує поточний курс продажу USD.
     * Спочатку намагається використати API ПриватБанку, у разі помилки
     * звертається до API НБУ.
     * @return актуальний курс долара до гривні
     */
    public double getCurrentUsdRate() {
        String privatApiUrl = ConfigManager.getProperty("api.exchange.privat.url");

        try {
            logger.info("Запит курсу валют через API ПриватБанку...");
            String rawJson = sendGetRequest(privatApiUrl);

            if (rawJson != null && !rawJson.isEmpty()) {
                JsonArray array = JsonParser.parseString(rawJson).getAsJsonArray();

                for (JsonElement element : array) {
                    JsonObject currency = element.getAsJsonObject();
                    String currencyCode = currency.get("ccy").getAsString();

                    if ("USD".equalsIgnoreCase(currencyCode)) {
                        // Використовуємо курс продажу (sale)
                        double rate = currency.get("sale").getAsDouble();
                        logger.info("Курс ПриватБанку встановлено: 1 USD = {} UAH", rate);
                        return rate;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("ПриватБанк недоступний: {}. Перехід до резервного джерела НБУ.", e.getMessage());
        }

        return getFallbackNbuRate();
    }

    /**
     * Резервний метод отримання курсу через API Національного банку України.
     * @return курс НБУ або дефолтне значення з конфігурації
     */
    private double getFallbackNbuRate() {
        String nbuUrl = ConfigManager.getProperty("api.exchange.nbu.url");
        double defaultRate = ConfigManager.getDoubleProperty("currency.default.rate");

        try {
            logger.info("Запит резервного курсу через НБУ...");
            String rawJson = sendGetRequest(nbuUrl);

            if (rawJson != null && !rawJson.isEmpty()) {
                JsonArray array = JsonParser.parseString(rawJson).getAsJsonArray();
                if (array.size() > 0) {
                    double rate = array.get(0).getAsJsonObject().get("rate").getAsDouble();
                    logger.info("Отримано резервний курс НБУ: 1 USD = {} UAH", rate);
                    return rate;
                }
            }
        } catch (Exception e) {
            logger.warn("НБУ недоступний. Використано дефолтний курс: {} UAH", defaultRate);
        }

        return defaultRate;
    }
}