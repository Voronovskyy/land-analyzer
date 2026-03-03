package ua.edu.university.util;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import java.util.List;

public class GeoAnalysisUtil {

    private static final Logger logger = LoggerFactory.getLogger(GeoAnalysisUtil.class);

    public static double calculateAreaFromGeoJson(String geoJson) {
        if (geoJson == null || geoJson.isEmpty()) return 0;
        try {
            GeoJsonReader reader = new GeoJsonReader();
            Geometry geometry = reader.read(geoJson);

            double areaInDegrees = geometry.getArea();
            double latitude = geometry.getCentroid().getY();
            double metersPerDegree = 111319.9;
            double cosLat = Math.cos(Math.toRadians(latitude));
            double areaInMeters = areaInDegrees * metersPerDegree * (metersPerDegree * cosLat);

            return Math.abs(areaInMeters);
        } catch (Exception e) {
            return 0;
        }
    }

    public static List<Coordinate> parseBoundariesFromGeoJson(String geoJson) {
        List<Coordinate> boundaries = new java.util.ArrayList<>();
        try {
            // Простий парсинг координат з рядка GeoJSON (якщо ви не використовуєте бібліотеку Jackson/Gson)
            // Шукаємо масив всередині "coordinates":[[[...]]]}
            String coordinatesPart = geoJson.split("\\[\\[\\[")[1].split("\\]\\]\\]")[0];
            String[] pairs = coordinatesPart.split("\\],\\[");

            for (String pair : pairs) {
                String[] lonLat = pair.replace("[", "").replace("]", "").split(",");
                double lon = Double.parseDouble(lonLat[0]);
                double lat = Double.parseDouble(lonLat[1]);
                boundaries.add(new Coordinate(lat, lon));
            }
        } catch (Exception e) {
            logger.warn("Не вдалося розпарсити GeoJSON для 3D моделі, використовуємо дефолтний квадрат.");
        }
        return boundaries;
    }
}
