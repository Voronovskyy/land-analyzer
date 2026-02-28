package ua.edu.university.util;

import java.util.Locale;

public class MapHtmlBuilder {

    /**
     * Формує повний HTML-код для відображення карти з аналітикою ділянки.
     */
    public static String build(double lat, double lon, int zoom, String geoJson,
                               double priceUah, double priceUsd, double rate) {

        StringBuilder html = new StringBuilder();

        appendHeader(html);
        appendMapContainer(html);
        appendScriptsStart(html, lat, lon, zoom);

        if (isGeoDataAvailable(geoJson)) {
            appendGeoJsonAnalysis(html, geoJson, priceUah, priceUsd, rate);
        }

        appendScriptsEnd(html);

        return html.toString();
    }

    private static boolean isGeoDataAvailable(String geoJson) {
        return geoJson != null && !geoJson.isEmpty();
    }

    private static void appendHeader(StringBuilder html) {
        html.append("<!DOCTYPE html>\n<html>\n<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n")
                .append("    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n")
                .append("    <script src=\"https://unpkg.com/@turf/turf@6/turf.min.js\"></script>\n")
                .append("    <style>\n")
                .append("        body { padding: 0; margin: 0; }\n")
                .append("        #map { height: 100vh; }\n")
                .append("        .year-badge { background: white; padding: 5px 12px; border: 2px solid rgba(0,0,0,0.2); \n")
                .append("                      border-radius: 4px; font-family: Arial; font-weight: bold; font-size: 12px; }\n")
                .append("        .popup-title { color: #c0392b; font-size: 14px; }\n")
                .append("        .price-uah { color: #27ae60; font-weight: bold; }\n")
                .append("        .price-usd { color: #2980b9; font-weight: bold; }\n")
                .append("    </style>\n")
                .append("</head>\n");
    }

    private static void appendMapContainer(StringBuilder html) {
        html.append("<body>\n    <div id=\"map\"></div>\n");
    }

    private static void appendScriptsStart(StringBuilder html, double lat, double lon, int zoom) {
        html.append("    <script>\n")
                .append("        var osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: 'OSM' });\n")
                .append("        var satellite = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { attribution: 'Esri' });\n")
                .append(String.format(Locale.US, "        var map = L.map('map', { center: [%f, %f], zoom: %d, layers: [osm] });\n", lat, lon, zoom))
                .append("        L.control.layers({ \"Схема\": osm, \"Супутник\": satellite }).addTo(map);\n");
    }

    private static void appendGeoJsonAnalysis(StringBuilder html, String geoJson, double priceUah, double priceUsd, double rate) {
        String borderColor = ConfigManager.getProperty("plot.color.border");
        String fillColor = ConfigManager.getProperty("plot.color.fill");
        String opacity = ConfigManager.getProperty("plot.opacity");

        html.append("        var geojsonData = ").append(geoJson).append(";\n")
                .append("        var areaM2 = turf.area(geojsonData);\n")
                .append(String.format(Locale.US, "        var plotLayer = L.geoJSON(geojsonData, { style: {color: '%s', fillColor: '%s', weight: 3, fillOpacity: %s} }).addTo(map);\n",
                        borderColor, fillColor, opacity))
                .append("        var popupContent = `\n")
                .append("           <b class='popup-title'>Економічний паспорт ділянки</b><hr>\n")
                .append("           <b>Площа:</b> ${(areaM2/10000).toFixed(4)} га<br>\n")
                .append(String.format(Locale.US, "           <b>Вартість (UAH):</b> <span class='price-uah'>%,.2f ₴</span><br>\n", priceUah))
                .append(String.format(Locale.US, "           <b>Вартість (USD):</b> <span class='price-usd'>$%,.0f</span><br>\n", priceUsd))
                .append(String.format(Locale.US, "           <hr><small>Офіційний курс НБУ: 1$ = %.2f грн</small>`\n", rate))
                .append("        ;\n")
                .append("        plotLayer.bindPopup(popupContent).openPopup();\n")
                .append("        map.fitBounds(plotLayer.getBounds());\n");
    }

    private static void appendScriptsEnd(StringBuilder html) {
        html.append("    </script>\n</body>\n</html>");
    }
}