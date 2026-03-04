package ua.edu.university.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;
import ua.edu.university.util.ConfigManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сервіс для взаємодії з Державним земельним кадастром.
 * На етапі розробки PhD модуля використовує локальну імітаційну базу даних (JSON).
 */
public class CadastreApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(CadastreApiService.class);

    /**
     * Знаходить дані про земельну ділянку за її кадастровим номером.
     *
     * @param number кадастровий номер у форматі 0000000000:00:000:0000
     * @return об'єкт Coordinate з координатами центру та геометрією (GeoJSON)
     * @throws Exception якщо виникла помилка при читанні локальної бази даних
     */
    public Coordinate getPlotByCadastralNumber(String number) throws Exception {
        String urlTemplate = ConfigManager.getProperty("api.cadastre.url.template");
        String dataPath = ConfigManager.getProperty("api.cadastre.mock.data.path");

        logger.info(">>> ІМІТАЦІЯ ЗАПИТУ ДО ДЕРЖГЕОКАДАСТРУ: {}{}", urlTemplate, number);

        // Отримання шляху до ресурсу
        var resource = getClass().getResource(dataPath);
        if (resource == null) {
            logger.error("Файл імітаційної бази даних не знайдено за шляхом: {}", dataPath);
            return null;
        }

        // Імітація отримання відповіді від сервера (читання з JSON-файлу)
        String content = Files.readString(Path.of(resource.toURI()));
        JsonObject database = JsonParser.parseString(content).getAsJsonObject();

        // Перевірка наявності номера в імітаційній базі
        if (database.has(number)) {
            JsonObject data = database.getAsJsonObject(number);
            double lat = data.get("lat").getAsDouble();
            double lon = data.get("lon").getAsDouble();
            String geoJson = data.get("geojson").getAsString();

            logger.info("<<< [MOCK SERVER] Об'єкт {} успішно ідентифіковано.", number);
            return new Coordinate(lat, lon, geoJson);
        }

        logger.warn("<<< [MOCK SERVER] Кадастровий номер {} відсутній у локальній базі.", number);
        return null;
    }
}