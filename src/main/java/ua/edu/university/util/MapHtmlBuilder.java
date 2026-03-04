package ua.edu.university.util;

import ua.edu.university.model.Coordinate;
import java.util.List;
import java.util.Locale;

public class MapHtmlBuilder {

    public static String build(double lat, double lon, int zoom, String geoJson,
                               List<Coordinate> boundaries,
                               double priceUah, double priceUsd, double rate,
                               double elevation, double suitability) {

        StringBuilder html = new StringBuilder();
        appendHeader(html);
        appendMapContainer(html);
        appendScriptsStart(html, lat, lon, zoom);

        if (geoJson != null && !geoJson.isEmpty()) {
            appendGeoJsonAnalysis(html, geoJson, priceUah, priceUsd, rate, elevation, suitability);
        } else if (boundaries != null && !boundaries.isEmpty()) {
            appendManualBoundariesAnalysis(html, boundaries, priceUah, priceUsd, rate, elevation, suitability);
        }

        appendHelperFunctions(html);
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
                .append("        .popup-title { color: #e74c3c; font-size: 14px; font-family: Arial; font-weight: bold; }\n")
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
                // ПОВЕРТАЄМО NDVI ШАР
                .append("        var ndviLayer = L.tileLayer('https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2020_3857/default/g/{z}/{y}/{x}.jpg');\n")

                .append(String.format(Locale.US, "        var map = L.map('map', { center: [%f, %f], zoom: %d, layers: [osm] });\n", lat, lon, zoom))

                // РЕЄСТРУЄМО ВСІ ШАРИ ДЛЯ ВИКЛИКУ З JAVA (showLayer)
                .append("        var layers = {\n")
                .append("            'osm': osm,\n")
                .append("            'satellite': satellite,\n")
                .append("            'terrainGroup': terrainGroup,\n")
                .append("            'demLayer': demLayer,\n")
                .append("            'ndviLayer': ndviLayer\n")
                .append("        };\n")

                .append("        var baseMaps = {\n")
                .append("            'Схема': osm,\n")
                .append("            'Супутник': satellite,\n")
                .append("            'Рельєф': terrainGroup,\n")
                .append("            'Висоти (DEM)': demLayer,\n")
                .append("            'Рослинність (NDVI)': ndviLayer\n")
                .append("        };\n")
                .append("        L.control.layers(baseMaps).addTo(map);\n");
    }

    private static void appendManualBoundariesAnalysis(StringBuilder html, List<Coordinate> boundaries,
                                                       double priceUah, double priceUsd, double rate,
                                                       double elevation, double suitability) {
        StringBuilder pts = new StringBuilder("[");
        for (Coordinate c : boundaries) {
            pts.append(String.format(Locale.US, "[%f, %f],", c.getLatitude(), c.getLongitude()));
        }
        pts.append("]");

        html.append("        var manualPoints = ").append(pts).append(";\n")
                .append("        var polygon = L.polygon(manualPoints, {color: '#e74c3c', fillColor: '#c0392b', weight: 3, fillOpacity: 0.5}).addTo(map);\n")
                .append("        window.plotLayer = polygon;\n")
                .append("        var areaM2 = turf.area(polygon.toGeoJSON());\n")
                .append("        var popupContent = `\n")
                .append("           <div style='min-width: 200px; font-family: Arial;'>\n")
                .append("           <b class='popup-title'>Польовий паспорт</b><hr style='margin: 5px 0;'>\n")
                .append("           <b>Площа:</b> ${(areaM2/10000).toFixed(4)} га<br>\n")
                .append(String.format(Locale.US, "           <b>Висота:</b> %.1f м<br>\n", elevation))
                .append(String.format(Locale.US, "           <b style='color: #27ae60;'>Оцінка:</b> %,.2f ₴<br>\n", priceUah))
                .append("           <hr style='margin: 2px 0;'><small>Джерело: GPS (Польові виміри)</small></div>`;\n")
                .append("        polygon.bindPopup(popupContent).openPopup();\n")
                .append("        map.fitBounds(polygon.getBounds());\n");
    }

    private static void appendGeoJsonAnalysis(StringBuilder html, String geoJson, double priceUah,
                                              double priceUsd, double rate, double elevation, double suitability) {
        html.append("        var geojsonData = ").append(geoJson).append(";\n")
                .append("        var plotLayer = L.geoJson(geojsonData, { style: {color: '#e74c3c', fillColor: '#c0392b', weight: 3, fillOpacity: 0.4} }).addTo(map);\n")
                .append("        window.plotLayer = plotLayer;\n")
                .append("        var areaM2 = turf.area(geojsonData);\n")
                .append("        var popupContent = `\n")
                .append("           <div style='min-width: 200px; font-family: Arial;'>\n")
                .append("           <b class='popup-title'>Економічний паспорт (ДЗК)</b><hr style='margin: 5px 0;'>\n")
                .append("           <b>Площа:</b> ${(areaM2/10000).toFixed(4)} га<br>\n")
                .append(String.format(Locale.US, "           <b>Вартість:</b> %,.2f ₴<br>\n", priceUah))
                .append("           </div>`;\n")
                .append("        plotLayer.bindPopup(popupContent).openPopup();\n")
                .append("        map.fitBounds(plotLayer.getBounds());\n");
    }

    private static void appendHelperFunctions(StringBuilder html) {
        html.append("\n        function showLayer(layerKey) {\n")
                .append("            Object.values(layers).forEach(l => { if(map.hasLayer(l)) map.removeLayer(l); });\n")
                .append("            if (layers[layerKey]) {\n")
                .append("                layers[layerKey].addTo(map);\n")
                .append("                console.log('Layer switched to: ' + layerKey);\n")
                .append("            }\n")
                .append("            if (window.plotLayer) window.plotLayer.bringToFront();\n")
                .append("        }\n");
    }

    private static void appendScriptsEnd(StringBuilder html) {
        html.append("    </script>\n</body>\n</html>");
    }
}