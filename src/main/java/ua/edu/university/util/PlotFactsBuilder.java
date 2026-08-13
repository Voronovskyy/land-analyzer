package ua.edu.university.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ua.edu.university.model.Coordinate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static ua.edu.university.util.PdfCellHelper.*;

/**
 * Формує блок фактичних даних про ділянку для AI-промтів.
 * <p>
 * Без нього модель отримувала самі координати і вимушено домислювала
 * решту: у звітах траплялись твердження про «центральну частину Львова»
 * для ділянки в с. Зубра та «виражений горбистий рельєф» там, де таблиця
 * поруч показувала перепад 1 м. Уся ця інформація в системі вже зібрана —
 * достатньо передати її моделі й заборонити виходити за її межі.
 */
public class PlotFactsBuilder {

    /** Порожній рядок означає «фактів немає» — промт тоді лишається як був. */
    public static String build(String areaHa, double elevation, double[] cornerElevations,
                               List<Coordinate> boundaries,
                               JsonObject geoAddress, JsonObject infra,
                               JsonObject climate, JsonArray poi, String dayLength) {
        List<String> lines = new ArrayList<>();

        addIfPresent(lines, "Адреса", formatAddress(geoAddress));
        addIfPresent(lines, "Площа", areaHa);
        addIfPresent(lines, "Рельєф", formatRelief(elevation, cornerElevations, boundaries));
        addIfPresent(lines, "Дорога", formatRoad(infra));
        addIfPresent(lines, "Водний об'єкт", formatWater(infra));
        addIfPresent(lines, "Електромережі", formatPower(infra));
        addIfPresent(lines, "Санітарні чинники", formatHazards(infra));
        addIfPresent(lines, "Клімат", formatClimate(climate));
        addIfPresent(lines, "Тривалість світлового дня", dayLength);
        addIfPresent(lines, "Об'єкти поруч", formatPoi(poi));

        if (lines.isEmpty()) return "";

        return "\n\nФАКТИЧНІ ДАНІ ПРО ЦЮ ДІЛЯНКУ (зібрані з відкритих геоданих):\n"
                + String.join("\n", lines)
                + "\n\nСпирайся ВИКЛЮЧНО на ці дані та загальні закономірності. "
                + "Не називай населений пункт, район, вулицю чи об'єкти, яких немає у переліку вище. "
                + "Не суперечь наведеним числам. Якщо даних для якогось висновку бракує — так і зазнач.";
    }

    // ─── Форматування окремих блоків ─────────────────────────────────────

    private static String formatAddress(JsonObject geo) {
        if (geo == null || geo.size() == 0) return null;
        List<String> parts = new ArrayList<>();
        for (String key : new String[]{"road", "suburb", "city", "county", "state", "postcode"}) {
            String v = safeStr(geo, key);
            if (!v.isEmpty()) parts.add(v);
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String formatRelief(double elevation, double[] ce, List<Coordinate> boundaries) {
        StringBuilder sb = new StringBuilder(String.format(Locale.US, "%.1f м н.р.м.", elevation));
        if (ce != null && ce.length >= 4) {
            double min = ce[0], max = ce[0];
            for (double v : ce) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double delta = max - min;
            sb.append(String.format(Locale.US, ", перепад висот по кутах %.1f м", delta));
            double slope = estimateSlopeDegrees(delta, boundaries);
            if (slope >= 0) sb.append(String.format(Locale.US, " (ухил ~%.1f°)", slope));
        }
        return sb.toString();
    }

    private static String formatRoad(JsonObject infra) {
        if (infra == null) return null;
        if (!safeBoolean(infra, "road_nearby")) return "у радіусі 1000 м не виявлено";
        String type = safeStr(infra, "road_type");
        long dist = safeLong(infra, "road_distance_m");
        StringBuilder sb = new StringBuilder(type.isEmpty() ? "автодорога" : type);
        if (dist > 0) sb.append(", ~").append(dist).append(" м");
        return sb.toString();
    }

    private static String formatWater(JsonObject infra) {
        if (infra == null) return null;
        if (!safeBoolean(infra, "water_nearby")) return "у радіусі 1500 м не виявлено";
        String name = safeStr(infra, "water_name");
        String type = safeStr(infra, "water_type");
        long dist = safeLong(infra, "water_distance_m");
        StringBuilder sb = new StringBuilder();
        if (!type.isEmpty()) sb.append(type);
        if (!name.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(name);
        if (sb.length() == 0) sb.append("водний об'єкт");
        if (dist > 0) sb.append(", ~").append(dist).append(" м");
        return sb.toString();
    }

    private static String formatPower(JsonObject infra) {
        if (infra == null) return null;
        return safeBoolean(infra, "power_nearby")
                ? "ЛЕП виявлено в радіусі 500 м"
                : "у радіусі 500 м не виявлено";
    }

    private static String formatHazards(JsonObject infra) {
        if (infra == null) return null;
        List<String> found = new ArrayList<>();
        if (safeBoolean(infra, "cemetery_100m")) found.add("кладовище");
        if (safeBoolean(infra, "industrial_100m")) found.add("промзона");
        if (safeBoolean(infra, "station_100m")) found.add("залізнична станція");
        return found.isEmpty()
                ? "у радіусі 100 м кладовищ, промзон і залізничних станцій не виявлено"
                : "у радіусі 100 м: " + String.join(", ", found);
    }

    private static String formatClimate(JsonObject climate) {
        if (climate == null) return null;
        List<String> parts = new ArrayList<>();
        if (climate.has("annual_precipitation"))
            parts.add("опади " + climate.get("annual_precipitation").getAsString() + " мм/рік");
        if (climate.has("max_temp"))
            parts.add("абсолютний максимум " + climate.get("max_temp").getAsString() + " °C");
        if (climate.has("min_temp"))
            parts.add("абсолютний мінімум " + climate.get("min_temp").getAsString() + " °C");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String formatPoi(JsonArray poi) {
        if (poi == null || poi.size() == 0) return "у радіусі 2 км нічого не виявлено";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < poi.size(); i++) {
            JsonObject p = poi.get(i).getAsJsonObject();
            String type = p.has("type") ? p.get("type").getAsString() : "";
            String name = p.has("name") ? p.get("name").getAsString() : "";
            long dist = p.has("dist") ? p.get("dist").getAsLong() : -1;
            boolean unnamed = name.isEmpty() || name.equals(type) || name.equals("без назви");
            String entry = unnamed ? type : type + " «" + name + "»";
            if (dist >= 0) entry += " ~" + dist + " м";
            parts.add(entry);
        }
        return String.join("; ", parts);
    }

    // ─── Допоміжне ───────────────────────────────────────────────────────

    /** Ухил через перепад висот і найбільшу відстань між кутами; -1 — порахувати не вдалось. */
    private static double estimateSlopeDegrees(double delta, List<Coordinate> boundaries) {
        if (boundaries == null || boundaries.size() < 2) return -1;
        double maxDist = 0;
        for (int i = 0; i < boundaries.size(); i++) {
            for (int j = i + 1; j < boundaries.size(); j++) {
                maxDist = Math.max(maxDist, haversine(boundaries.get(i), boundaries.get(j)));
            }
        }
        if (maxDist < 1) return -1;
        return Math.toDegrees(Math.atan(delta / maxDist));
    }

    private static double haversine(Coordinate a, Coordinate b) {
        double R = 6371000.0;
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }

    private static void addIfPresent(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) lines.add("- " + label + ": " + value);
    }
}
