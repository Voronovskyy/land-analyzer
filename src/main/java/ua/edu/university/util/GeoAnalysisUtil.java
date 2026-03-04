package ua.edu.university.util;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import java.util.List;

/**
 * Утилітарний клас для геопросторового аналізу.
 * Містить методи для обчислення площі земельних ділянок на основі
 * сферичної тригонометрії та локальних декартових проєкцій.
 */
public class GeoAnalysisUtil {
    private static final Logger logger = LoggerFactory.getLogger(GeoAnalysisUtil.class);

    /**
     * Обчислює площу об'єкта на основі рядка у форматі GeoJSON.
     * Використовує апроксимацію метричної площі через косинус широти центру.
     *
     * @param geoJson рядок з геометрією об'єкта
     * @return площа в квадратних метрах
     */
    public static double calculateAreaFromGeoJson(String geoJson) {
        if (geoJson == null || geoJson.isEmpty()) {
            return 0;
        }

        double metersPerDegree = ConfigManager.getDoubleProperty("geo.meters.per.degree");

        try {
            GeoJsonReader reader = new GeoJsonReader();
            Geometry geometry = reader.read(geoJson);

            // Отримуємо площу в градусах та координати центру для корекції
            double areaInDegrees = geometry.getArea();
            double latitude = geometry.getCentroid().getY();

            // Корекція площі залежно від віддаленості від екватора
            double cosLat = Math.cos(Math.toRadians(latitude));
            double areaInMeters = areaInDegrees * metersPerDegree * (metersPerDegree * cosLat);

            double result = Math.abs(areaInMeters);
            logger.debug("Площа з GeoJSON: {} м2", result);
            return result;

        } catch (Exception e) {
            logger.error("Помилка обчислення площі з GeoJSON: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Обчислює площу багатокутника за списком GPS координат.
     * Реалізує алгоритм площі Гаусса (Shoelace formula) у локальній проєкції.
     * Підходить для високоточного аналізу малих та середніх земельних ділянок.
     *
     * @param points список точок меж ділянки
     * @return площа в квадратних метрах
     */
    public static double calculateAreaFromCoordinates(List<Coordinate> points) {
        if (points == null || points.size() < 3) {
            logger.warn("Недостатньо точок для обчислення площі (мінімум 3).");
            return 0;
        }

        double earthRadius = ConfigManager.getDoubleProperty("geo.earth.radius");
        double area = 0;

        try {
            for (int i = 0; i < points.size(); i++) {
                Coordinate p1 = points.get(i);
                Coordinate p2 = points.get((i + 1) % points.size());

                // Проєкція координат на площину (локальні x, y в метрах)
                double x1 = Math.toRadians(p1.getLongitude()) * earthRadius * Math.cos(Math.toRadians(p1.getLatitude()));
                double y1 = Math.toRadians(p1.getLatitude()) * earthRadius;

                double x2 = Math.toRadians(p2.getLongitude()) * earthRadius * Math.cos(Math.toRadians(p2.getLatitude()));
                double y2 = Math.toRadians(p2.getLatitude()) * earthRadius;

                // Накопичення знакової площі за формулою трапецій
                area += (x1 * y2) - (x2 * y1);
            }

            double finalArea = Math.abs(area) / 2.0;
            logger.info("Розрахована площа ділянки: {} м2", finalArea);
            return finalArea;

        } catch (Exception e) {
            logger.error("Критична помилка при математичному розрахунку площі: {}", e.getMessage());
            return 0;
        }
    }
}