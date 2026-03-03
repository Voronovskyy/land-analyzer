package ua.edu.university.util;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import java.util.List;

public class GeoAnalysisUtil {

    private static final Logger logger = LoggerFactory.getLogger(GeoAnalysisUtil.class);
    private static final double EARTH_RADIUS = 6371000;

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

    /**
     * Обчислює площу багатокутника за GPS координатами.
     * Використовує спрощену проекцію для точності на малих ділянках.
     * Повертає площу в квадратних метрах.
     */
    public static double calculateAreaFromCoordinates(List<Coordinate> points) {
        if (points == null || points.size() < 3) return 0;

        double area = 0;
        for (int i = 0; i < points.size(); i++) {
            Coordinate p1 = points.get(i);
            Coordinate p2 = points.get((i + 1) % points.size());

            // Перетворення координат у локальні метри (x, y)
            double x1 = Math.toRadians(p1.getLongitude()) * EARTH_RADIUS * Math.cos(Math.toRadians(p1.getLatitude()));
            double y1 = Math.toRadians(p1.getLatitude()) * EARTH_RADIUS;

            double x2 = Math.toRadians(p2.getLongitude()) * EARTH_RADIUS * Math.cos(Math.toRadians(p2.getLatitude()));
            double y2 = Math.toRadians(p2.getLatitude()) * EARTH_RADIUS;

            area += (x1 * y2) - (x2 * y1);
        }

        return Math.abs(area) / 2.0;
    }
}
