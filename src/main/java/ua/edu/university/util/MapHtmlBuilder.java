package ua.edu.university.util;

import java.util.Locale;

public class MapHtmlBuilder {

    public static String build(double lat, double lon, int zoom, String geoJson,
                               double priceUah, double priceUsd, double rate,
                               double elevation, double suitability) {

        StringBuilder html = new StringBuilder();
        appendHeader(html);
        appendMapContainer(html);
        appendScriptsStart(html, lat, lon, zoom);

        if (geoJson != null && !geoJson.isEmpty()) {
            appendGeoJsonAnalysis(html, geoJson, priceUah, priceUsd, rate, elevation, suitability);
        }

        appendHelperFunctions(html); // Додаємо допоміжні JS-функції
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
                .append("        body { padding: 0; margin: 0; overflow: hidden; }\n")
                .append("        #map { height: 100vh; width: 100vw; transition: all 0.5s ease; }\n")
                .append("        .popup-title { color: #c0392b; font-size: 14px; font-family: Arial; }\n")
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

                // Ініціалізація карти з активним шаром OSM
                .append(String.format(Locale.US, "        var map = L.map('map', { center: [%f, %f], zoom: %d, layers: [osm] });\n", lat, lon, zoom))

                // Реєструємо шари в глобальному об'єкті для доступу з Java
                .append("        var layers = {\n")
                .append("            'osm': osm,\n")
                .append("            'satellite': satellite,\n")
                .append("            'terrainGroup': terrainGroup,\n")
                .append("            'demLayer': demLayer,\n")
                .append("            'ndviLayer': ndviLayer\n")
                .append("        };\n")

                // Додаємо стандартний перемикач (Control.Layers), щоб користувач міг клікати мишкою
                .append("        var baseMaps = {\n")
                .append("            'Схема': osm,\n")
                .append("            'Супутник': satellite,\n")
                .append("            'Рельєф': terrainGroup,\n")
                .append("            'Висоти (DEM)': demLayer,\n")
                .append("            'Вегетація (NDVI)': ndviLayer\n")
                .append("        };\n")
                .append("        L.control.layers(baseMaps).addTo(map);\n");
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
                .append("        window.plotLayer = plotLayer;\n")
                .append("        var popupContent = `\n")
                .append("           <div style='min-width: 200px; font-family: Arial;'>\n")
                .append("           <b class='popup-title' style='color: #c0392b; font-size: 14px;'>Економічний паспорт ділянки</b><hr style='margin: 5px 0;'>\n")
                .append("           <b>Площа:</b> ${(areaM2/10000).toFixed(4)} га<br>\n")
                .append(String.format(Locale.US, "           <b>Висота:</b> %.1f м н.р.м.<br>\n", elevation))
                .append(String.format(Locale.US, "           <b>Придатність:</b> <span style='color:%s; font-weight: bold;'>%.0f%%</span><br>\n",
                        suitability > 0.7 ? "green" : "orange", suitability * 100))
                .append(String.format(Locale.US, "           <b style='color: #27ae60;'>Вартість:</b> %,.2f ₴<br>\n", priceUah))
                .append(String.format(Locale.US, "           <b style='color: #2980b9;'>Вартість:</b> $%,.0f<br>\n", priceUsd))
                .append(String.format(Locale.US, "           <hr style='margin: 5px 0;'><small style='color: gray;'>Курс НБУ: 1$ = %.2f грн</small>\n", rate))
                .append("           </div>` ;\n")

                .append("        plotLayer.bindPopup(popupContent).openPopup();\n")
                .append("        map.fitBounds(plotLayer.getBounds());\n");
    }

    /**
     * НОВІ ФУНКЦІЇ ДЛЯ КЕРУВАННЯ З JAVA
     */
    private static void appendHelperFunctions(StringBuilder html) {
        html.append("\n        // Головна функція перемикання для звітів\n")
                .append("        function showLayer(layerKey) {\n")
                .append("            // 1. Видаляємо всі базові шари\n")
                .append("            Object.values(layers).forEach(l => { if(map.hasLayer(l)) map.removeLayer(l); });\n")
                .append("\n            // 2. Додаємо вибраний шар\n")
                .append("            if (layers[layerKey]) {\n")
                .append("                layers[layerKey].addTo(map);\n")
                .append("            }\n")
                .append("\n            // 3. Завжди тримаємо межі ділянки зверху\n")
                .append("            if (window.plotLayer) window.plotLayer.bringToFront();\n")
                .append("            if (window.manualLayer) window.manualLayer.bringToFront();\n")
                .append("        }\n")
                .append("\n        // Функція для ручного малювання полігону (4 точки)\n")
                .append("        function drawManualPolygon(coords) {\n")
                .append("            if (window.manualLayer) map.removeLayer(window.manualLayer);\n")
                .append("            window.manualLayer = L.polygon(coords, {color: '#e74c3c', weight: 3, fillOpacity: 0.3}).addTo(map);\n")
                .append("            map.fitBounds(window.manualLayer.getBounds());\n")
                .append("        }\n");
    }

    private static void appendScriptsEnd(StringBuilder html) {
        html.append("    </script>\n</body>\n</html>");
    }
}