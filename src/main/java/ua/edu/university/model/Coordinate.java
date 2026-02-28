package ua.edu.university.model;

public class Coordinate {
    private double latitude;
    private double longitude;
    private String geoJson;
    private String source;
    private int dataYear;
    private int imageryYear;
    private double ngoPerSqm;

    public Coordinate(double latitude, double longitude, String geoJson) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.geoJson = geoJson;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getGeoJson() {
        return geoJson;
    }

    public void setGeoJson(String geoJson) {
        this.geoJson = geoJson;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getDataYear() {
        return dataYear;
    }

    public void setDataYear(int dataYear) {
        this.dataYear = dataYear;
    }

    public int getImageryYear() {
        return imageryYear;
    }

    public void setImageryYear(int imageryYear) {
        this.imageryYear = imageryYear;
    }

    public double getNgoPerSqm() {
        return ngoPerSqm;
    }

    public void setNgoPerSqm(double ngoPerSqm) {
        this.ngoPerSqm = ngoPerSqm;
    }

    @Override
    public String toString() {
        return "Coordinate{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                ", geoJson='" + geoJson + '\'' +
                ", source='" + source + '\'' +
                ", dataYear=" + dataYear +
                ", imageryYear=" + imageryYear +
                ", ngoPerSqm=" + ngoPerSqm +
                '}';
    }
}