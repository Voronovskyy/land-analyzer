package ua.edu.university.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Сервіс просторового аналізу інфраструктури навколо ділянки (OSM Overpass).
 * <p>
 * Виправлення: лінії ЛЕП — це way["power"~"line|cable|minor_line"], а не node.
 * Додано: дороги, відстань до найближчого об'єкта кожного типу, назви водойм.
 */
public class OverpassApiService extends BaseApiService {

    private static final Logger logger = LoggerFactory.getLogger(OverpassApiService.class);
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter?data=";
    private static final int RADIUS_POWER = 500;
    private static final int RADIUS_WATER = 1500;
    private static final int RADIUS_ROAD = 1000;
    private static final int RADIUS_PROXIMITY = 100;

    public JsonObject getInfrastructureDetails(double lat, double lon) {
        // out center tags — для кожного way повертає координати центру і теги
        String query = String.format(Locale.US,
                "[out:json][timeout:30];" +
                        "(" +
                        "  way[\"power\"~\"line|cable|minor_line\"](around:%d,%f,%f);" +
                        "  node[\"power\"~\"tower|pole\"](around:%d,%f,%f);" +
                        "  way[\"waterway\"~\"river|stream|canal|drain\"](around:%d,%f,%f);" +
                        "  relation[\"waterway\"~\"river|stream|canal\"](around:%d,%f,%f);" +
                        "  way[\"highway\"~\"trunk|primary|secondary|tertiary|residential\"](around:%d,%f,%f);" +
                        "  node[\"amenity\"~\"grave_yard|crematorium\"](around:%d,%f,%f);" +
                        "  way[\"landuse\"~\"cemetery|grave_yard\"](around:%d,%f,%f);" +
                        "  way[\"landuse\"=\"industrial\"](around:%d,%f,%f);" +
                        "  node[\"railway\"=\"station\"](around:%d,%f,%f);" +
                        "  node[\"public_transport\"=\"station\"](around:%d,%f,%f);" +
                        ");" +
                        "out geom tags;",
                RADIUS_POWER, lat, lon,
                RADIUS_POWER, lat, lon,
                RADIUS_WATER, lat, lon,
                RADIUS_WATER, lat, lon,
                RADIUS_ROAD, lat, lon,
                RADIUS_PROXIMITY, lat, lon,
                RADIUS_PROXIMITY, lat, lon,
                RADIUS_PROXIMITY, lat, lon,
                RADIUS_PROXIMITY, lat, lon,
                RADIUS_PROXIMITY, lat, lon);

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String response = sendGetRequest(OVERPASS_URL + encoded);
            return processElements(JsonParser.parseString(response)
                    .getAsJsonObject(), lat, lon);
        } catch (Exception e) {
            logger.error("Overpass API error: {}", e.getMessage());
            return fallbackResult();
        }
    }

    // ─── Обробка відповіді ────────────────────────────────────────────────
    private JsonObject processElements(JsonObject root, double refLat, double refLon) {
        JsonArray elements = root.has("elements") ? root.getAsJsonArray("elements") : new JsonArray();

        double minWaterDist = Double.MAX_VALUE;
        double minRoadDist = Double.MAX_VALUE;
        double nearestRoadLat = 0;
        double nearestRoadLon = 0;
        boolean hasPower = false;
        boolean hasCemetery = false;
        boolean hasIndustrial = false;
        boolean hasStation = false;
        String waterName = "";
        String waterType = "";
        String roadType = "";

        for (int i = 0; i < elements.size(); i++) {
            JsonObject el = elements.get(i).getAsJsonObject();
            JsonObject tags = el.has("tags") ? el.getAsJsonObject("tags") : new JsonObject();
            double dist = extractDistance(el, refLat, refLon);

            // ── Електромережі ──
            if (tags.has("power")) {
                String powerVal = tags.get("power").getAsString();
                if (powerVal.matches("line|cable|minor_line|tower|pole")) hasPower = true;
            }

            // ── Водні об'єкти — лише найближчий ──
            if (tags.has("waterway") && dist < minWaterDist) {
                minWaterDist = dist;
                waterType = tags.get("waterway").getAsString();
                waterName = tags.has("name") ? tags.get("name").getAsString() : "";
            }

            // ── Дороги — лише найближча (шукаємо ближчий вузол геометрії) ──
            if (tags.has("highway")) {
                String hw = tags.get("highway").getAsString();
                if (hw.matches("trunk|primary|secondary|tertiary|residential")) {
                    double[] closest = closestPoint(el, refLat, refLon);
                    if (closest[2] < minRoadDist) {
                        minRoadDist = closest[2];
                        roadType = hw;
                        nearestRoadLat = closest[0];
                        nearestRoadLon = closest[1];
                    }
                }
            }

            // ── Об'єкти у радіусі 100 м ──
            if (dist <= RADIUS_PROXIMITY) {
                if (tags.has("amenity") && tags.get("amenity").getAsString().matches("grave_yard|crematorium"))
                    hasCemetery = true;
                if (tags.has("landuse") && tags.get("landuse").getAsString().matches("cemetery|grave_yard"))
                    hasCemetery = true;
                if (tags.has("landuse") && "industrial".equals(tags.get("landuse").getAsString()))
                    hasIndustrial = true;
                if (tags.has("railway") && "station".equals(tags.get("railway").getAsString()))
                    hasStation = true;
                if (tags.has("public_transport") && "station".equals(tags.get("public_transport").getAsString()))
                    hasStation = true;
            }
        }

        boolean hasWater = minWaterDist < Double.MAX_VALUE;
        boolean hasRoad = minRoadDist < Double.MAX_VALUE;

        JsonObject result = new JsonObject();
        result.addProperty("power_nearby", hasPower);
        result.addProperty("power_distance_m", -1);
        result.addProperty("water_nearby", hasWater);
        result.addProperty("water_count", 1);
        result.addProperty("water_distance_m", hasWater ? Math.round(minWaterDist) : -1);
        result.addProperty("water_name", waterName);
        result.addProperty("water_type", localizeWaterType(waterType));
        result.addProperty("road_nearby", hasRoad);
        result.addProperty("road_count", 1);
        result.addProperty("road_distance_m", hasRoad ? Math.round(minRoadDist) : -1);
        result.addProperty("road_type", localizeRoadType(roadType));
        result.addProperty("road_lat", nearestRoadLat);
        result.addProperty("road_lon", nearestRoadLon);
        result.addProperty("cemetery_100m", hasCemetery);
        result.addProperty("industrial_100m", hasIndustrial);
        result.addProperty("station_100m", hasStation);
        result.addProperty("objects_count", elements.size());
        return result;
    }

    // ─── Відстань від елемента до точки (Haversine) ───────────────────────

    private double extractDistance(JsonObject el, double refLat, double refLon) {
        return closestPoint(el, refLat, refLon)[2];
    }

    /**
     * Повертає [lat, lon, distanceM] найближчої до refLat/refLon точки елемента.
     * Для way з geometry — перебирає всі вузли; для node — бере lat/lon напряму.
     */
    private double[] closestPoint(JsonObject el, double refLat, double refLon) {
        try {
            // Way з повною геометрією — перебираємо всі вузли
            if (el.has("geometry")) {
                JsonArray geom = el.getAsJsonArray("geometry");
                double bestLat = 0, bestLon = 0, bestDist = Double.MAX_VALUE;
                for (int i = 0; i < geom.size(); i++) {
                    JsonObject pt = geom.get(i).getAsJsonObject();
                    double lat = pt.get("lat").getAsDouble();
                    double lon = pt.get("lon").getAsDouble();
                    double d = haversine(refLat, refLon, lat, lon);
                    if (d < bestDist) {
                        bestDist = d;
                        bestLat = lat;
                        bestLon = lon;
                    }
                }
                return new double[]{bestLat, bestLon, bestDist};
            }
            // Node або way із center-координатою
            double lat, lon;
            if (el.has("center")) {
                JsonObject c = el.getAsJsonObject("center");
                lat = c.get("lat").getAsDouble();
                lon = c.get("lon").getAsDouble();
            } else if (el.has("lat") && el.has("lon")) {
                lat = el.get("lat").getAsDouble();
                lon = el.get("lon").getAsDouble();
            } else {
                return new double[]{0, 0, Double.MAX_VALUE};
            }
            return new double[]{lat, lon, haversine(refLat, refLon, lat, lon)};
        } catch (Exception e) {
            return new double[]{0, 0, Double.MAX_VALUE};
        }
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ─── Локалізація типів ────────────────────────────────────────────────

    private String localizeWaterType(String type) {
        return switch (type) {
            case "river" -> "річка";
            case "stream" -> "струмок";
            case "canal" -> "канал";
            case "drain" -> "дренаж";
            default -> type;
        };
    }

    private String localizeRoadType(String type) {
        return switch (type) {
            case "trunk" -> "магістраль";
            case "primary" -> "1-й клас";
            case "secondary" -> "2-й клас";
            case "tertiary" -> "3-й клас";
            case "residential" -> "жилий масив";
            default -> type;
        };
    }

    // ─── POI в радіусі 2 км ──────────────────────────────────────────────────

    public JsonArray getNearbyPOIs(double lat, double lon) {
        String query = String.format(Locale.US,
                "[out:json][timeout:15];" +
                        "(node[\"amenity\"~\"school|hospital|pharmacy|clinic|kindergarten\"](around:2000,%f,%f);" +
                        "node[\"shop\"~\"supermarket|convenience\"](around:1500,%f,%f);" +
                        "node[\"leisure\"~\"park|garden\"](around:1500,%f,%f););" +
                        "out center tags;",
                lat, lon, lat, lon, lat, lon);
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String response = sendGetRequest(OVERPASS_URL + encoded);
            JsonArray elements = JsonParser.parseString(response).getAsJsonObject()
                    .has("elements")
                    ? JsonParser.parseString(response).getAsJsonObject().getAsJsonArray("elements")
                    : new JsonArray();

            List<JsonObject> list = new ArrayList<>();
            for (int i = 0; i < elements.size(); i++) {
                JsonObject el = elements.get(i).getAsJsonObject();
                JsonObject tags = el.has("tags") ? el.getAsJsonObject("tags") : new JsonObject();
                double dist = extractDistance(el, lat, lon);
                if (dist >= Double.MAX_VALUE) continue;

                String type = poiType(tags);
                // Раніше сюди підставлявся тип — у таблиці звіту виходило
                // «Магазин | Магазин» замість порожньої назви.
                String name = tags.has("name") ? tags.get("name").getAsString() : "без назви";
                if (name.length() > 30) name = name.substring(0, 28) + "…";

                JsonObject poi = new JsonObject();
                poi.addProperty("type", type);
                poi.addProperty("name", name);
                poi.addProperty("dist", Math.round(dist));
                list.add(poi);
            }
            list.sort(Comparator.comparingLong(o -> o.getAsJsonPrimitive("dist").getAsLong()));

            JsonArray result = new JsonArray();
            for (int i = 0; i < Math.min(5, list.size()); i++) result.add(list.get(i));
            return result;
        } catch (Exception e) {
            logger.error("getNearbyPOIs error: {}", e.getMessage());
            return new JsonArray();
        }
    }

    private String poiType(JsonObject tags) {
        if (tags.has("amenity")) return switch (tags.get("amenity").getAsString()) {
            case "school" -> "Школа";
            case "hospital" -> "Лікарня";
            case "pharmacy" -> "Аптека";
            case "clinic" -> "Клініка";
            case "kindergarten" -> "Дитсадок";
            default -> "Заклад";
        };
        if (tags.has("shop")) return "Магазин";
        if (tags.has("leisure")) return switch (tags.get("leisure").getAsString()) {
            case "park" -> "Парк";
            case "garden" -> "Сад";
            default -> "Рекреація";
        };
        return "Об'єкт";
    }

    private JsonObject fallbackResult() {
        JsonObject r = new JsonObject();
        r.addProperty("power_nearby", false);
        r.addProperty("power_distance_m", -1);
        r.addProperty("water_nearby", false);
        r.addProperty("water_count", 0);
        r.addProperty("water_distance_m", -1);
        r.addProperty("water_name", "");
        r.addProperty("water_type", "");
        r.addProperty("road_nearby", false);
        r.addProperty("road_count", 0);
        r.addProperty("road_distance_m", -1);
        r.addProperty("road_type", "");
        r.addProperty("road_lat", 0.0);
        r.addProperty("road_lon", 0.0);
        r.addProperty("cemetery_100m", false);
        r.addProperty("industrial_100m", false);
        r.addProperty("station_100m", false);
        r.addProperty("objects_count", 0);
        return r;
    }
}
