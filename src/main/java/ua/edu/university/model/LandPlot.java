package ua.edu.university.model;

import java.util.List;

public class LandPlot {
    private String address;
    private String cadastralNumber; // Для України часто має формат ХХХХХХХХХХ:ХХ:ХХХ:ХХХХ
    private Coordinate centerCoordinate; // Центральна точка ділянки
    // Межі ділянки (Bounding Box або полігон) - згодом ми будемо отримувати це з АПІ
    private List<Coordinate> boundaries;
    // Заділ на майбутнє: дані про рельєф (висота над рівнем моря тощо)
    private Double averageElevation;
    // Заділ на майбутнє: результат оцінювання придатності
    private Double suitabilityScore;

    // Порожній конструктор (часто потрібен для бібліотек парсингу JSON, як-от Gson)
    public LandPlot() {
    }

    public LandPlot(String address, String cadastralNumber) {
        this.address = address;
        this.cadastralNumber = cadastralNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getCadastralNumber() {
        return cadastralNumber;
    }

    public Coordinate getCenterCoordinate() {
        return centerCoordinate;
    }

    public List<Coordinate> getBoundaries() {
        return boundaries;
    }

    public Double getAverageElevation() {
        return averageElevation;
    }

    public Double getSuitabilityScore() {
        return suitabilityScore;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setCadastralNumber(String cadastralNumber) {
        this.cadastralNumber = cadastralNumber;
    }

    public void setCenterCoordinate(Coordinate centerCoordinate) {
        this.centerCoordinate = centerCoordinate;
    }

    public void setBoundaries(List<Coordinate> boundaries) {
        this.boundaries = boundaries;
    }

    public void setAverageElevation(Double averageElevation) {
        this.averageElevation = averageElevation;
    }

    public void setSuitabilityScore(Double suitabilityScore) {
        this.suitabilityScore = suitabilityScore;
    }

    @Override
    public String toString() {
        return "Ділянка: " + (address != null ? address : cadastralNumber) +
                " | Координати: " + centerCoordinate;
    }
}
