package ua.edu.university.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.util.ConfigManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Базовий абстрактний клас для всіх API-сервісів.
 * Інкапсулює HTTP-клієнт, логування запитів/відповідей та обробку HTTP-помилок.
 * Конкретні сервіси наслідують цей клас і використовують sendGetRequest / sendPostRequest.
 */
public abstract class BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(BaseApiService.class);

    private static final String DEFAULT_TIMEOUT_KEY = "api.request.timeout.seconds";

    /** Запас, якщо ключа немає в конфігу (ConfigManager віддав би 0 = «без межі»). */
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;

    protected final HttpClient httpClient;
    protected final String userAgent;
    private final Duration requestTimeout;

    public BaseApiService() {
        this(DEFAULT_TIMEOUT_KEY);
    }

    /**
     * Для джерел, які законно відповідають довше за типову межу: Overpass
     * на інфраструктурний запит стабільно витрачає близько 36 с, і спільні
     * 20 с обрізали його на півдорозі — у звіті чинники, що залежать від
     * OSM, виходили порожніми («Дані недоступні»).
     */
    protected BaseApiService(String timeoutPropertyKey) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.userAgent = ConfigManager.getProperty("api.user.agent");
        this.requestTimeout = resolveRequestTimeout(timeoutPropertyKey);
    }

    /**
     * connectTimeout обмежує ЛИШЕ встановлення TCP-з'єднання — він не покриває
     * ні DNS-резолвінг, ні читання відповіді. Без таймауту на самому запиті
     * зовнішній сервіс, який прийняв з'єднання й замовк, блокував потік
     * безстроково: генерація звіту не падала, а висла, доки браузер сам не
     * обривав запит (Railway логував це як 499 після 180 с). Усі виклики тут
     * мають фолбеки, тож швидка помилка завжди краща за вічне очікування.
     */
    private static Duration resolveRequestTimeout(String key) {
        int seconds = ConfigManager.getIntProperty(key);
        if (seconds <= 0) {
            seconds = ConfigManager.getIntProperty(DEFAULT_TIMEOUT_KEY);
        }
        return Duration.ofSeconds(seconds > 0 ? seconds : DEFAULT_TIMEOUT_SECONDS);
    }

    protected String sendGetRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(requestTimeout)
                .GET()
                .build();
        return executeRequest(request);
    }

    protected String sendPostRequest(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return executeRequest(request);
    }

    private String executeRequest(HttpRequest request) throws Exception {
        long startTime = System.currentTimeMillis();
        logger.info(">>> [HTTP {}] URL: {}", request.method(), request.uri());
        request.bodyPublisher().ifPresent(pub -> logger.debug(">>> [BODY]: (виконується відправка даних)"));
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long duration = System.currentTimeMillis() - startTime;

        logger.info("<<< [ВІДПОВІДЬ] Статус: {} | Час: {}ms", response.statusCode(), duration);
        logger.debug("<<< [DATA]: {}", truncate(response.body()));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            logger.error("!!! [API ERROR] Код: {} | Тіло: {}", response.statusCode(), truncate(response.body()));
            throw new RuntimeException("HTTP Помилка: " + response.statusCode());
        }
    }

    /**
     * Overpass-відповіді для щільної забудови можуть бути сотнями KB —
     * повне тіло в логах і засмічує потік, і при паралельних запитах
     * рядки різних відповідей переплітаються, роблячи лог нечитабельним.
     */
    private static String truncate(String body) {
        if (body == null || body.length() <= 1000) return body;
        return body.substring(0, 1000) + "... [обрізано, ще " + (body.length() - 1000) + " симв.]";
    }
}
