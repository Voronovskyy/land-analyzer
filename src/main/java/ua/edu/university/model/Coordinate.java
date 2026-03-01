package ua.edu.university.model;

import java.util.List;

/**
 * Розширена модель географічної точки та аналітичних даних ділянки.
 */
public class Coordinate {
    private final double latitude;
    private final double longitude;
    private final String geoJson;
    private List<Coordinate> boundaries;
    private Double averageElevation;
    private Double suitabilityScore;

    public Coordinate(double lat, double lon, String geoJson) {
        this.latitude = lat;
        this.longitude = lon;
        this.geoJson = geoJson;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getGeoJson() {
        return geoJson;
    }

    public List<Coordinate> getBoundaries() {
        return boundaries;
    }

    public void setBoundaries(List<Coordinate> boundaries) {
        this.boundaries = boundaries;
    }

    public Double getAverageElevation() {
        return averageElevation;
    }

    public void setAverageElevation(Double averageElevation) {
        this.averageElevation = averageElevation;
    }

    public Double getSuitabilityScore() {
        return suitabilityScore;
    }

    public void setSuitabilityScore(Double suitabilityScore) {
        this.suitabilityScore = suitabilityScore;
    }

    @Override
    public String toString() {
        return String.format("Coordinate{lat=%.6f, lon=%.6f, elevation=%.1f, score=%.2f}",
                latitude, longitude, averageElevation, suitabilityScore);
    }
}