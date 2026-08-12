package ua.edu.university.util;

import ua.edu.university.model.Coordinate;

import java.util.List;
import java.util.Locale;

/**
 * Будівельник HTML-контенту для відображення інтерактивної карти.
 * Генерує динамічний JavaScript код для бібліотеки Leaflet, поєднуючи
 * картографічні шари та результати аналізу земельних ділянок.
 */
public class MapHtmlBuilder {

    /**
     * Головний метод побудови повної HTML-сторінки карти.
     */
    public static String build(double lat, double lon, int zoom, String geoJson,
                               List<Coordinate> boundaries,
                               double priceUah, double priceUsd, double rate,
                               double elevation, double suitability) {

        StringBuilder html = new StringBuilder();
        appendHeader(html);
        appendMapContainer(html);
        appendScriptsStart(html, lat, lon, zoom);

        // Пріоритет відображення: спочатку офіційний GeoJSON, потім ручні межі
        if (geoJson != null && !geoJson.isEmpty()) {
            appendGeoJsonAnalysis(html, geoJson, priceUah, priceUsd, rate, elevation, suitability);
        } else if (boundaries != null && !boundaries.isEmpty()) {
            appendManualBoundariesAnalysis(html, boundaries, priceUah, priceUsd, rate, elevation, suitability);
        }

        appendHelperFunctions(html);
        appendScriptsEnd(html);

        return html.toString();
    }

    /**
     * Додає мета-дані та підключає зовнішні бібліотеки (Leaflet, Turf.js).
     */
    private static void appendHeader(StringBuilder html) {
        html.append("<!DOCTYPE html>\n<html>\n<head>\n")
                .append("    <meta charset=\"UTF-8\">\n")
                .append("    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n")
                .append("    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n")
                .append("    <script src=\"https://unpkg.com/@turf/turf@6/turf.min.js\"></script>\n")
                .append("    <style>\n")
                .append("        body { padding: 0; margin: 0; overflow: hidden; }\n")
                .append("        #map { height: 100vh; width: 100vw; transition: all 0.5s ease; }\n")
                .append("        .light-popup .leaflet-popup-content-wrapper { background:transparent; border:none; box-shadow:none; padding:0; border-radius:10px; }\n")
                .append("        .light-popup .leaflet-popup-content { margin:0; }\n")
                .append("        .light-popup .leaflet-popup-tip-container { display:none; }\n")
                .append("    </style>\n")
                .append("</head>\n");
    }

    /**
     * Створює DOM-контейнер для карти.
     */
    private static void appendMapContainer(StringBuilder html) {
        html.append("<body>\n    <div id=\"map\"></div>\n");
    }

    /**
     * Ініціалізує карту та налаштовує перелік картографічних шарів.
     */
    private static void appendScriptsStart(StringBuilder html, double lat, double lon, int zoom) {
        html.append("    <script>\n")
                // Налаштування базових шарів (OSM, Супутник, Рельєф, NDVI)
                .append("        var osm = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png');\n")
                .append("        var satellite = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}');\n")
                .append("        var terrainGroup = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}');\n")
                .append("        var demLayer = L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png');\n")
                .append("        var ndviLayer = L.tileLayer('https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2020_3857/default/g/{z}/{y}/{x}.jpg');\n")
                .append("        var slopeLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}', {maxZoom:16});\n")
                // Створення об'єкта карти
                .append(String.format(Locale.US, "        var map = L.map('map', { center: [%f, %f], zoom: %d, layers: [osm] });\n", lat, lon, zoom))
                // Реєстрація шарів для керування через Java
                .append("        var layers = {\n")
                .append("            'osm': osm, 'satellite': satellite, 'terrainGroup': terrainGroup,\n")
                .append("            'demLayer': demLayer, 'ndviLayer': ndviLayer,\n")
                .append("            'slopeLayer': slopeLayer\n")
                .append("        };\n")
                // Додавання стандартного контролера шарів
                .append("        var baseMaps = {\n")
                .append("            'Схема': osm, 'Супутник': satellite, 'Рельєф': terrainGroup,\n")
                .append("            'Висоти (DEM)': demLayer, 'Рослинність (NDVI)': ndviLayer,\n")
                .append("            'Схили (Hillshade)': slopeLayer\n")
                .append("        };\n")
                .append("        L.control.layers(baseMaps, null, {collapsed:true}).addTo(map);\n");
    }

    /**
     * Відображає ділянку на основі списку координат (ручне введення).
     */
    private static void appendManualBoundariesAnalysis(StringBuilder html, List<Coordinate> boundaries,
                                                       double priceUah, double priceUsd, double rate,
                                                       double elevation, double suitability) {
        StringBuilder pts = new StringBuilder("[");
        for (Coordinate c : boundaries) {
            pts.append(String.format(Locale.US, "[%f, %f],", c.getLatitude(), c.getLongitude()));
        }
        pts.append("]");

        html.append("        var manualPoints = ").append(pts).append(";\n")
                .append("        var polygon = L.polygon(manualPoints,{color:'#e74c3c',fillColor:'#e74c3c',weight:2.5,fillOpacity:0.15}).addTo(map);\n")
                .append("        window.plotLayer = polygon;\n")
                .append("        var areaM2 = turf.area(polygon.toGeoJSON());\n")
                .append("        var pts2 = polygon.getLatLngs()[0]; var perimM=0;\n")
                .append("        for(var i=0;i<pts2.length;i++){perimM+=map.distance(pts2[i],pts2[(i+1)%pts2.length]);}\n")
                .append("        var ctr = polygon.getBounds().getCenter();\n")
                .append("        var ctrStr = ctr.lat.toFixed(5)+', '+ctr.lng.toFixed(5);\n")
                .append("        var popupContent = `")
                .append("<div style='font-family:\"Segoe UI\",Arial,sans-serif;width:252px;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,.15);'>")
                // Header
                .append("<div style='background:#27ae60;padding:12px 16px;'>")
                .append("<div style='color:rgba(255,255,255,.75);font-size:9px;font-weight:700;letter-spacing:1.5px;'>&#128205; ЗЕМЕЛЬНА ДІЛЯНКА · GPS</div>")
                .append("<div style='color:#fff;font-size:11px;margin-top:4px;font-family:monospace;opacity:.9;'>${ctrStr}</div>")
                .append("</div>")
                // Area + Elevation row
                .append("<div style='display:grid;grid-template-columns:1fr 1fr;border-bottom:1px solid #f0f0f0;'>")
                .append("<div style='padding:11px 14px;border-right:1px solid #f0f0f0;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#128210; ПЛОЩА</div>")
                .append("<div style='color:#2c3e50;font-size:14px;font-weight:700;margin-top:3px;'>${(areaM2/10000).toFixed(4)} га</div>")
                .append("<div style='color:#bdc3c7;font-size:9px;margin-top:1px;'>${areaM2.toFixed(0)} м²</div>")
                .append("</div>")
                .append("<div style='padding:11px 14px;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#9968; ВИСОТА</div>")
                .append(String.format(Locale.US, "<div style='color:#2c3e50;font-size:14px;font-weight:700;margin-top:3px;'>%.1f м</div>", elevation))
                .append("<div style='color:#bdc3c7;font-size:9px;margin-top:1px;'>WGS84</div>")
                .append("</div>")
                .append("</div>")
                // Perimeter
                .append("<div style='padding:10px 14px;border-bottom:1px solid #f0f0f0;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#128207; ПЕРИМЕТР</div>")
                .append("<div style='color:#2c3e50;font-size:14px;font-weight:700;margin-top:3px;'>${perimM.toFixed(0)} м</div>")
                .append("</div>")
                // Price
                .append("<div style='padding:11px 14px;border-bottom:1px solid #f0f0f0;background:#f9fffe;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#128176; ОЦІНОЧНА ВАРТІСТЬ</div>")
                .append(String.format(Locale.US, "<div style='color:#27ae60;font-size:18px;font-weight:700;margin-top:4px;'>%,.0f ₴</div>", priceUah))
                .append(String.format(Locale.US, "<div style='color:#95a5a6;font-size:11px;margin-top:2px;'>&#8776; $%,.0f USD</div>", priceUsd))
                .append("</div>")
                // Suitability
                .append("<div style='padding:11px 14px;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>ПРИДАТНІСТЬ</div>")
                .append("<div style='color:#7f8c8d;font-size:11px;font-style:italic;margin-top:4px;'>Розраховується у повному звіті</div>")
                .append("</div>")
                .append("</div>`;\n")
                .append("        polygon.bindPopup(popupContent,{maxWidth:280,className:'light-popup'}).openPopup();\n")
                .append("        map.fitBounds(polygon.getBounds(), {maxZoom: 17, padding: [40, 40]});\n");
    }

    /**
     * Відображає ділянку на основі GeoJSON даних (з Кадастру).
     */
    private static void appendGeoJsonAnalysis(StringBuilder html, String geoJson, double priceUah,
                                              double priceUsd, double rate, double elevation, double suitability) {
        html.append("        var geojsonData = ").append(geoJson).append(";\n")
                .append("        var plotLayer = L.geoJson(geojsonData,{style:{color:'#e74c3c',fillColor:'#e74c3c',weight:2.5,fillOpacity:0.15}}).addTo(map);\n")
                .append("        window.plotLayer = plotLayer;\n")
                .append("        var areaM2 = turf.area(geojsonData);\n")
                .append("        var lls = plotLayer.getLayers()[0].getLatLngs()[0]; var perimM=0;\n")
                .append("        for(var i=0;i<lls.length;i++){perimM+=map.distance(lls[i],lls[(i+1)%lls.length]);}\n")
                .append("        var ctr = plotLayer.getBounds().getCenter();\n")
                .append("        var ctrStr = ctr.lat.toFixed(5)+', '+ctr.lng.toFixed(5);\n")
                .append("        var popupContent = `")
                .append("<div style='font-family:\"Segoe UI\",Arial,sans-serif;width:252px;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,.15);'>")
                // Header
                .append("<div style='background:#27ae60;padding:12px 16px;'>")
                .append("<div style='color:rgba(255,255,255,.75);font-size:9px;font-weight:700;letter-spacing:1.5px;'>&#128205; ЗЕМЕЛЬНА ДІЛЯНКА · ДЗК</div>")
                .append("<div style='color:#fff;font-size:11px;margin-top:4px;font-family:monospace;opacity:.9;'>${ctrStr}</div>")
                .append("</div>")
                // Area + Elevation row
                .append("<div style='display:grid;grid-template-columns:1fr 1fr;border-bottom:1px solid #f0f0f0;'>")
                .append("<div style='padding:11px 14px;border-right:1px solid #f0f0f0;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#128210; ПЛОЩА</div>")
                .append("<div style='color:#2c3e50;font-size:14px;font-weight:700;margin-top:3px;'>${(areaM2/10000).toFixed(4)} га</div>")
                .append("<div style='color:#bdc3c7;font-size:9px;margin-top:1px;'>${areaM2.toFixed(0)} м²</div>")
                .append("</div>")
                .append("<div style='padding:11px 14px;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#9968; ВИСОТА</div>")
                .append(String.format(Locale.US, "<div style='color:#2c3e50;font-size:14px;font-weight:700;margin-top:3px;'>%.1f м</div>", elevation))
                .append("<div style='color:#bdc3c7;font-size:9px;margin-top:1px;'>WGS84</div>")
                .append("</div>")
                .append("</div>")
                // Perimeter
                .append("<div style='padding:10px 14px;border-bottom:1px solid #f0f0f0;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#128207; ПЕРИМЕТР</div>")
                .append("<div style='color:#2c3e50;font-size:14px;font-weight:700;margin-top:3px;'>${perimM.toFixed(0)} м</div>")
                .append("</div>")
                // Price
                .append("<div style='padding:11px 14px;border-bottom:1px solid #f0f0f0;background:#f9fffe;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>&#128176; ОЦІНОЧНА ВАРТІСТЬ</div>")
                .append(String.format(Locale.US, "<div style='color:#27ae60;font-size:18px;font-weight:700;margin-top:4px;'>%,.0f ₴</div>", priceUah))
                .append(String.format(Locale.US, "<div style='color:#95a5a6;font-size:11px;margin-top:2px;'>&#8776; $%,.0f USD</div>", priceUsd))
                .append("</div>")
                // Suitability
                .append("<div style='padding:11px 14px;'>")
                .append("<div style='color:#95a5a6;font-size:8px;font-weight:700;letter-spacing:1px;'>ПРИДАТНІСТЬ</div>")
                .append("<div style='color:#7f8c8d;font-size:11px;font-style:italic;margin-top:4px;'>Розраховується у повному звіті</div>")
                .append("</div>")
                .append("</div>`;\n")
                .append("        plotLayer.bindPopup(popupContent,{maxWidth:280,className:'light-popup'}).openPopup();\n")
                .append("        map.fitBounds(plotLayer.getBounds(), {maxZoom: 17, padding: [40, 40]});\n");
    }

    /**
     * Додає JS-функції для взаємодії Java-контролера з картою.
     */
    private static void appendHelperFunctions(StringBuilder html) {
        html.append("\n")
                // Глобальні лічильники для відстеження завантаження тайлів
                .append("        window.tileLoadComplete = true;\n")
                .append("        window.tileLoadingCount = 0;\n")
                .append("        function attachTileEvents(layer) {\n")
                .append("            layer.on('tileloadstart', function() {\n")
                .append("                window.tileLoadingCount++;\n")
                .append("                window.tileLoadComplete = false;\n")
                .append("            });\n")
                .append("            layer.on('tileload tileerror', function() {\n")
                .append("                window.tileLoadingCount = Math.max(0, window.tileLoadingCount - 1);\n")
                .append("                if (window.tileLoadingCount === 0) window.tileLoadComplete = true;\n")
                .append("            });\n")
                .append("        }\n")
                .append("        [osm, satellite, terrainGroup, demLayer, ndviLayer, slopeLayer].forEach(attachTileEvents);\n")
                .append("\n")
                .append("        function showLayer(layerKey) {\n")
                .append("            window.tileLoadComplete = false;\n")
                .append("            window.tileLoadingCount = 0;\n")
                .append("            Object.values(layers).forEach(l => { if(map.hasLayer(l)) map.removeLayer(l); });\n")
                .append("            if (layers[layerKey]) layers[layerKey].addTo(map);\n")
                .append("            if (window.plotLayer) window.plotLayer.bringToFront();\n")
                .append("        }\n");
    }

    /**
     * Закриває теги скрипта та тіла документа.
     */
    private static void appendScriptsEnd(StringBuilder html) {
        html.append("    </script>\n</body>\n</html>");
    }
}