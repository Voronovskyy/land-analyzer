package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.util.ConfigManager;

import java.util.*;

/**
 * Сервіс для отримання ринкових цін на земельні ділянки через DOM.RIA API.
 * Використовує reverse-geocoding (Nominatim) для визначення регіону, після чого
 * шукає активні оголошення продажу і повертає медіанну ціну UAH/м².
 *
 * Потребує валідного ключа api.domria.key в application.properties.
 * Реєстрація: https://developers.ria.com
 */
public class DomRiaApiService extends BaseApiService {

    private static final Logger logger = LoggerFactory.getLogger(DomRiaApiService.class);

    private static final int CATEGORY_LAND = 24;
    private static final int OPERATION_SALE = 1;
    private static final int MAX_LISTINGS   = 7;

    private static final double MIN_PRICE_PER_SQM =     1.0;
    private static final double MAX_PRICE_PER_SQM = 50_000.0;

    private final String searchUrl;
    private final String infoUrl;
    private final String reverseUrl;

    public DomRiaApiService() {
        super();
        this.searchUrl  = ConfigManager.getProperty("api.domria.search.url");
        this.infoUrl    = ConfigManager.getProperty("api.domria.info.url");
        this.reverseUrl = ConfigManager.getProperty("api.domria.reverse.url");
    }

    /**
     * Повертає медіанну ринкову ціну (UAH/м²) для земельних ділянок поблизу
     * заданих координат, або OptionalDouble.empty() при будь-якій помилці.
     */
    public OptionalDouble getMarketPricePerSqM(double lat, double lon) {
        String apiKey = ConfigManager.getProperty("api.domria.key");
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("YOUR_KEY")) {
            logger.warn("DOM.RIA API ключ не налаштовано — пропуск ринкової оцінки");
            return OptionalDouble.empty();
        }

        try {
            int stateId = resolveStateId(lat, lon);
            if (stateId <= 0) {
                logger.warn("Не вдалося визначити регіон для [{},{}]", lat, lon);
                return OptionalDouble.empty();
            }

            List<Integer> ids = searchListings(stateId, apiKey);
            if (ids.isEmpty()) {
                logger.warn("DOM.RIA: оголошень не знайдено для state_id={}", stateId);
                return OptionalDouble.empty();
            }

            List<Double> prices = new ArrayList<>();
            for (int id : ids.subList(0, Math.min(MAX_LISTINGS, ids.size()))) {
                getPricePerSqM(id, apiKey).ifPresent(prices::add);
            }

            if (prices.isEmpty()) return OptionalDouble.empty();

            Collections.sort(prices);
            double median = prices.get(prices.size() / 2);
            logger.info("DOM.RIA ринкова ціна: {:.2f} UAH/м² (вибірка {} оголошень)", median, prices.size());
            return OptionalDouble.of(median);

        } catch (Exception e) {
            logger.warn("DOM.RIA lookup провалився: {}", e.getMessage());
            return OptionalDouble.empty();
        }
    }

    // ─── Крок 1: визначення регіону через Nominatim reverse ───────────────

    private int resolveStateId(double lat, double lon) throws Exception {
        String url = String.format(
                "%s?lat=%f&lon=%f&format=json&addressdetails=1&accept-language=uk",
                reverseUrl, lat, lon);

        String json = sendGetRequest(url);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!obj.has("address")) return -1;

        JsonObject addr = obj.getAsJsonObject("address");
        String state = addr.has("state") ? addr.get("state").getAsString() : "";
        String city  = addr.has("city")  ? addr.get("city").getAsString()  : "";

        int id = STATE_IDS.getOrDefault(state.toLowerCase(), -1);
        if (id == -1) id = STATE_IDS.getOrDefault(city.toLowerCase(), -1);

        logger.info("Nominatim reverse → state='{}', state_id={}", state, id);
        return id;
    }

    // ─── Крок 2: пошук оголошень ──────────────────────────────────────────

    private List<Integer> searchListings(int stateId, String apiKey) throws Exception {
        String url = String.format(
                "%s?api_key=%s&category=%d&operation_type=%d&state_id=%d",
                searchUrl, apiKey, CATEGORY_LAND, OPERATION_SALE, stateId);

        String json = sendGetRequest(url);
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!obj.has("items")) return Collections.emptyList();

        List<Integer> ids = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("items")) {
            ids.add(el.getAsInt());
        }
        return ids;
    }

    // ─── Крок 3: отримання ціни одного оголошення ────────────────────────

    private OptionalDouble getPricePerSqM(int listingId, String apiKey) {
        try {
            String url  = infoUrl + listingId + "?api_key=" + apiKey;
            String json = sendGetRequest(url);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            double totalPriceUah = extractPrice(obj);
            double areaSqM       = extractAreaSqM(obj);

            if (totalPriceUah <= 0 || areaSqM <= 0) return OptionalDouble.empty();

            double pricePerSqM = totalPriceUah / areaSqM;
            if (pricePerSqM < MIN_PRICE_PER_SQM || pricePerSqM > MAX_PRICE_PER_SQM) {
                logger.debug("Ігноруємо аномальну ціну {:.0f} UAH/м² для id={}", pricePerSqM, listingId);
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(pricePerSqM);

        } catch (Exception e) {
            logger.debug("Не вдалося отримати ціну для id={}: {}", listingId, e.getMessage());
            return OptionalDouble.empty();
        }
    }

    // ─── Парсери відповіді DOM.RIA ────────────────────────────────────────

    /** Витягує ціну у гривнях. Пробує priceArr[], потім price. */
    private double extractPrice(JsonObject obj) {
        // Варіант 1: priceArr[{ currency, value }]
        if (obj.has("priceArr") && obj.get("priceArr").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("priceArr");
            for (JsonElement el : arr) {
                JsonObject p = el.getAsJsonObject();
                String cur = p.has("currency") ? p.get("currency").getAsString() : "";
                if ("UAH".equalsIgnoreCase(cur) && p.has("value")) {
                    return p.get("value").getAsDouble();
                }
            }
            // Якщо нема UAH — беремо перший елемент
            if (!arr.isEmpty() && arr.get(0).getAsJsonObject().has("value")) {
                return arr.get(0).getAsJsonObject().get("value").getAsDouble();
            }
        }
        // Варіант 2: пряме поле price
        if (obj.has("price")) return obj.get("price").getAsDouble();
        return 0;
    }

    /**
     * Витягує площу в м².
     * DOM.RIA зберігає площу ділянок у сотках (1 сотка = 100 м²).
     * Якщо значення < 500 — припускаємо сотки і множимо на 100.
     */
    private double extractAreaSqM(JsonObject obj) {
        double raw = 0;

        if (obj.has("total_area_with_kop"))
            raw = obj.get("total_area_with_kop").getAsDouble();
        else if (obj.has("total_area"))
            raw = parseDoubleField(obj.get("total_area"));
        else if (obj.has("area"))
            raw = parseDoubleField(obj.get("area"));

        if (raw <= 0) return 0;
        // Евристика: площа < 500 одиниць → скоріш за все сотки
        return raw < 500 ? raw * 100.0 : raw;
    }

    private double parseDoubleField(JsonElement el) {
        try {
            return el.isJsonPrimitive() ? el.getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ─── Таблиця відповідності назв областей до state_id DOM.RIA ─────────

    private static final Map<String, Integer> STATE_IDS = buildStateMap();

    private static Map<String, Integer> buildStateMap() {
        Map<String, Integer> m = new HashMap<>();
        // Українські назви (Nominatim з accept-language=uk)
        m.put("вінницька область",        1);
        m.put("волинська область",         2);
        m.put("дніпропетровська область",  3);
        m.put("донецька область",          4);
        m.put("житомирська область",       5);
        m.put("закарпатська область",      6);
        m.put("запорізька область",        7);
        m.put("івано-франківська область", 8);
        m.put("київська область",          9);
        m.put("кіровоградська область",   10);
        m.put("луганська область",        11);
        m.put("львівська область",        12);
        m.put("миколаївська область",     13);
        m.put("одеська область",          14);
        m.put("полтавська область",       15);
        m.put("рівненська область",       16);
        m.put("сумська область",          17);
        m.put("тернопільська область",    18);
        m.put("харківська область",       19);
        m.put("херсонська область",       20);
        m.put("хмельницька область",      21);
        m.put("черкаська область",        22);
        m.put("чернівецька область",      23);
        m.put("чернігівська область",     24);
        m.put("київ",                     25);
        m.put("м. київ",                  25);
        // English names (Nominatim fallback)
        m.put("vinnytsia oblast",          1);
        m.put("volyn oblast",              2);
        m.put("dnipropetrovsk oblast",     3);
        m.put("donetsk oblast",            4);
        m.put("zhytomyr oblast",           5);
        m.put("zakarpattia oblast",        6);
        m.put("zaporizhzhia oblast",       7);
        m.put("ivano-frankivsk oblast",    8);
        m.put("kyiv oblast",               9);
        m.put("kirovohrad oblast",        10);
        m.put("luhansk oblast",           11);
        m.put("lviv oblast",              12);
        m.put("mykolaiv oblast",          13);
        m.put("odessa oblast",            14);
        m.put("poltava oblast",           15);
        m.put("rivne oblast",             16);
        m.put("sumy oblast",              17);
        m.put("ternopil oblast",          18);
        m.put("kharkiv oblast",           19);
        m.put("kherson oblast",           20);
        m.put("khmelnytskyi oblast",      21);
        m.put("cherkasy oblast",          22);
        m.put("chernivtsi oblast",        23);
        m.put("chernihiv oblast",         24);
        m.put("kyiv city",                25);
        return Collections.unmodifiableMap(m);
    }
}
