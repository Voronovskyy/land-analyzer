package ua.edu.university.web.dto;

import java.util.List;

public class ReportRequest {

    private double lat;
    private double lon;
    private String title;
    private String areaHa;
    private String priceUah;
    private String priceUsd;
    private double elevation;
    private double suitability;
    private String geoJson;
    private List<SearchResponse.LatLon> boundaries;
    private List<String> layers;
    private boolean aiEnabled;
    private String aiPassword;

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAreaHa() { return areaHa; }
    public void setAreaHa(String areaHa) { this.areaHa = areaHa; }

    public String getPriceUah() { return priceUah; }
    public void setPriceUah(String priceUah) { this.priceUah = priceUah; }

    public String getPriceUsd() { return priceUsd; }
    public void setPriceUsd(String priceUsd) { this.priceUsd = priceUsd; }

    public double getElevation() { return elevation; }
    public void setElevation(double elevation) { this.elevation = elevation; }

    public double getSuitability() { return suitability; }
    public void setSuitability(double suitability) { this.suitability = suitability; }

    public String getGeoJson() { return geoJson; }
    public void setGeoJson(String geoJson) { this.geoJson = geoJson; }

    public List<SearchResponse.LatLon> getBoundaries() { return boundaries; }
    public void setBoundaries(List<SearchResponse.LatLon> boundaries) { this.boundaries = boundaries; }

    public List<String> getLayers() { return layers; }
    public void setLayers(List<String> layers) { this.layers = layers; }

    public boolean isAiEnabled() { return aiEnabled; }
    public void setAiEnabled(boolean aiEnabled) { this.aiEnabled = aiEnabled; }

    public String getAiPassword() { return aiPassword; }
    public void setAiPassword(String aiPassword) { this.aiPassword = aiPassword; }
}
