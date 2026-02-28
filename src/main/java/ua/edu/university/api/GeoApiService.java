package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;
import ua.edu.university.util.ConfigManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GeoApiService extends BaseApiService {

    private static final Logger logger = LoggerFactory.getLogger(GeoApiService.class);
    private final String nominatimUrl;

    public GeoApiService() {
        super();
        this.nominatimUrl = ConfigManager.getProperty("api.nominatim.url");
    }

    public Coordinate getCoordinates(String address) throws Exception {
        String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String url = nominatimUrl + "?q=" + encodedAddress + "&format=json&limit=1&polygon_geojson=1";
        logger.debug("Сформований URL: {}", url);

        String rawJson = sendGetRequest(url);

        JsonArray jsonArray = JsonParser.parseString(rawJson).getAsJsonArray();
        if (!jsonArray.isEmpty()) {
            JsonObject firstResult = jsonArray.get(0).getAsJsonObject();
            double lat = firstResult.get("lat").getAsDouble();
            double lon = firstResult.get("lon").getAsDouble();

            String geoJson = firstResult.has("geojson") ? firstResult.get("geojson").toString() : null;
            return new Coordinate(lat, lon, geoJson);
        }

        return null;
    }
}