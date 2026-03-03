package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.util.ConfigManager;

public class ExchangeRateService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateService.class);

    // Нова URL адреса API ПриватБанку (готівковий курс)
    private static final String PRIVAT_API_URL = "https://api.privatbank.ua/p24api/pubinfo?exchange&coursid=5";

    public double getCurrentUsdRate() {
        try {
            // Використовуємо стабільне посилання прямо в коді або з конфігу
            String rawJson = sendGetRequest(PRIVAT_API_URL);

            if (rawJson != null && !rawJson.isEmpty()) {
                JsonArray array = JsonParser.parseString(rawJson).getAsJsonArray();

                for (JsonElement element : array) {
                    JsonObject currency = element.getAsJsonObject();
                    String ccy = currency.get("ccy").getAsString(); // Код валюти

                    if ("USD".equalsIgnoreCase(ccy)) {
                        // Приват повертає курс продажу (sale) та купівлі (buy)
                        double rate = currency.get("sale").getAsDouble();
                        logger.info("Успішно отримано курс ПриватБанку: 1 USD = {} UAH", rate);
                        return rate;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Помилка отримання курсу ПриватБанку: {}. Спробуємо резервний НБУ.", e.getMessage());
        }

        // Якщо Приват не відповів, спробуємо пряме посилання НБУ (оновлене)
        return getFallbackNbuRate();
    }

    private double getFallbackNbuRate() {
        // Оновлене стабільне посилання НБУ (тільки для USD)
        String nbuUrl = "https://bank.gov.ua/NBUStatService/v1/statistichny/exchange?valcode=USD&json";
        try {
            String rawJson = sendGetRequest(nbuUrl);
            if (rawJson != null) {
                JsonArray array = JsonParser.parseString(rawJson).getAsJsonArray();
                return array.get(0).getAsJsonObject().get("rate").getAsDouble();
            }
        } catch (Exception e) {
            logger.warn("НБУ також недоступний. Використовуємо дефолт з конфігу.");
        }
        return ConfigManager.getDoubleProperty("currency.default.rate");
    }
}