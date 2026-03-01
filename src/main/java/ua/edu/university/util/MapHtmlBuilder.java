package ua.edu.university.util;

import java.util.Locale;

public class MapHtmlBuilder {

    /**
     * Формує повний HTML-код для відображення карти з аналітикою ділянки.
     */
    public static String build(double lat, double lon, int zoom, String geoJson,
                               double priceUah, double priceUsd, double rate,
                               double elevation, double suitability) { // Додано параметри

        StringBuilder html = new StringBuilder();
        appendHeader(html);
        appendMapContainer(html);
        appendScriptsStart(html, lat, lon, zoom);

        if (geoJson != null && !geoJson.isEmpty()) {
            appendGeoJsonAnalysis(html, geoJson, priceUah, priceUsd, rate, elevation, suitability);
        }

        appendScriptsEnd(html);
        return html.toString();
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
                .append("        var osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png');\n")
                .append("        var satellite = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}');\n")
                .append("        var terrainGroup = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}');\n")
                .append("        var demLayer = L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png');\n")
                .append("        var ndviLayer = L.tileLayer('https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2020_3857/default/g/{z}/{y}/{x}.jpg');\n")
                .append(String.format(Locale.US, "        var map = L.map('map', { center: [%f, %f], zoom: %d, layers: [osm] });\n", lat, lon, zoom))
                .append("        function setActiveLayer(newLayer) {\n")
                .append("            map.eachLayer(function(l) {\n")
                .append("                if (l !== window.plotLayer && (l._url || (l.options && l.options.layers))) {\n")
                .append("                    map.removeLayer(l);\n")
                .append("                }\n")
                .append("            });\n")
                .append("            newLayer.addTo(map);\n")
                .append("            if (window.plotLayer) {\n")
                .append("                window.plotLayer.bringToFront();\n")
                .append("            }\n")
                .append("        }\n")
                .append("        L.control.layers({\n")
                .append("            'Схема': osm, \n")
                .append("            'Супутник': satellite, \n")
                .append("            'Рельєф': terrainGroup, \n")
                .append("            'Висоти (DEM)': demLayer, \n")
                .append("            'Вегетація (NDVI)': ndviLayer \n")
                .append("        }).addTo(map);\n");
    }

    private static void appendGeoJsonAnalysis(StringBuilder html, String geoJson, double priceUah,
                                              double priceUsd, double rate, double elevation, double suitability) {
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
                .append(String.format(Locale.US, "           <b>Висота:</b> %.1f м н.р.м.<br>\n", elevation))
                .append(String.format(Locale.US, "           <b>Придатність:</b> <span style='color:%s'>%.0f%%</span><br>\n",
                        suitability > 0.7 ? "green" : "orange", suitability * 100))
                .append(String.format(Locale.US, "           <b>Вартість (UAH):</b> <span class='price-uah'>%,.2f ₴</span><br>\n", priceUah))
                .append(String.format(Locale.US, "           <b>Вартість (USD):</b> <span class='price-usd'>$%,.0f</span><br>\n", priceUsd))
                .append(String.format(Locale.US, "           <hr><small>Курс НБУ: 1$ = %.2f грн</small>`\n", rate))
                .append("        ;\n")
                .append("        plotLayer.bindPopup(popupContent).openPopup();\n")
                .append("        map.fitBounds(plotLayer.getBounds());\n");
    }

    private static void appendScriptsEnd(StringBuilder html) {
        html.append("    </script>\n</body>\n</html>");
    }
}