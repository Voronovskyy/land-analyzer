package ua.edu.university.web.dto;

import java.util.List;

public class SearchResponse {

    private double lat;
    private double lon;
    private double areaSqM;
    private String areaHa;
    private double elevation;
    private double suitability;
    private String priceUah;
    private String priceUsd;
    private double rate;
    private String geoJson;
    private List<LatLon> boundaries;

    public static class LatLon {
        public double lat;
        public double lon;

        public LatLon() {}
        public LatLon(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public double getAreaSqM() { return areaSqM; }
    public void setAreaSqM(double areaSqM) { this.areaSqM = areaSqM; }

    public String getAreaHa() { return areaHa; }
    public void setAreaHa(String areaHa) { this.areaHa = areaHa; }

    public double getElevation() { return elevation; }
    public void setElevation(double elevation) { this.elevation = elevation; }

    public double getSuitability() { return suitability; }
    public void setSuitability(double suitability) { this.suitability = suitability; }

    public String getPriceUah() { return priceUah; }
    public void setPriceUah(String priceUah) { this.priceUah = priceUah; }

    public String getPriceUsd() { return priceUsd; }
    public void setPriceUsd(String priceUsd) { this.priceUsd = priceUsd; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public String getGeoJson() { return geoJson; }
    public void setGeoJson(String geoJson) { this.geoJson = geoJson; }

    public List<LatLon> getBoundaries() { return boundaries; }
    public void setBoundaries(List<LatLon> boundaries) { this.boundaries = boundaries; }
}
