package ua.edu.university.util;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonReader;

public class GeoAnalysisUtil {
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
}
