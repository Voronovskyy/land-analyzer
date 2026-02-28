package ua.edu.university.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import java.nio.file.Files;
import java.nio.file.Path;

public class CadastreApiService extends BaseApiService {
    private static final Logger logger = LoggerFactory.getLogger(CadastreApiService.class);

    public Coordinate getPlotByCadastralNumber(String number) throws Exception {
        String mockUrl = "https://e.land.gov.ua/api/v1/cadastre/" + number;
        logger.info(">>> ІМІТАЦІЯ ЗАПИТУ ДО ДЕРЖГЕОКАДАСТРУ: {}", mockUrl);

        // Замість реального інтернету, читаємо наш JSON файл
        // Це імітує отримання Response Body від сервера
        String content = Files.readString(Path.of(getClass().getResource("/cadastre_data.json").toURI()));
        JsonObject database = JsonParser.parseString(content).getAsJsonObject();

        if (database.has(number)) {
            JsonObject data = database.getAsJsonObject(number);
            double lat = data.get("lat").getAsDouble();
            double lon = data.get("lon").getAsDouble();
            String geoJson = data.get("geojson").getAsString();

            logger.info("<<< [MOCK SERVER] Ділянку {} знайдено.", number);
            return new Coordinate(lat, lon, geoJson);
        }

        logger.warn("<<< [MOCK SERVER] Номер {} не знайдено в базі.", number);
        return null;
    }
}