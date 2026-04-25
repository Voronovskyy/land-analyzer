package ua.edu.university.util;

import com.google.gson.JsonObject;

/**
 * Зважений розрахунок коефіцієнта придатності земельної ділянки.
 * <p>
 * Чинники та ваги:
 * 1. Рельєф / висота над р.м.    15%
 * 2. Транспортна доступність      20%
 * 3. Електрична інфраструктура   15%
 * 4. Водні ресурси поруч         10%
 * 5. Екологічні ризики            20%
 * 6. Кліматичні умови             20%
 */
public class SuitabilityCalculator {

    // ── Назви чинників для відображення у звіті ──────────────────────────

    public static final String[] FACTOR_NAMES = {
            "Рельєф / висота",
            "Транспортна доступність",
            "Електрична інфраструктура",
            "Водні ресурси",
            "Екологічні ризики",
            "Кліматичні умови"
    };

    public static final double[] FACTOR_WEIGHTS = {0.15, 0.20, 0.15, 0.10, 0.20, 0.20};

    // ── Публічний API ─────────────────────────────────────────────────────

    /**
     * Повний розрахунок КП на основі всіх доступних даних.
     * Якщо infra або climate = null — відповідні чинники виключаються,
     * а ваги нормалізуються.
     */
    public static double calculate(double elevation, JsonObject infra, JsonObject climate) {
        double[] scores = computeScores(elevation, infra, climate);
        double sumW = 0, sumWS = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= 0) {               // -1 означає "дані відсутні"
                sumW += FACTOR_WEIGHTS[i];
                sumWS += FACTOR_WEIGHTS[i] * scores[i];
            }
        }
        double result = sumW > 0 ? sumWS / sumW : 0.65;
        return round2(Math.min(1.0, Math.max(0.05, result)));
    }

    /**
     * Швидка оцінка лише за висотою (використовується на карті одразу після пошуку).
     */
    public static double calculateFromElevation(double elevation) {
        return round2(elevationScore(elevation));
    }

    /**
     * Повертає масив оцінок [0..1] для кожного чинника у порядку FACTOR_NAMES.
     * -1.0 означає "дані недоступні".
     */
    public static double[] getFactorScores(double elevation, JsonObject infra, JsonObject climate) {
        return computeScores(elevation, infra, climate);
    }

    // ── Внутрішні розрахунки ─────────────────────────────────────────────

    private static double[] computeScores(double elevation, JsonObject infra, JsonObject climate) {
        double[] s = new double[6];
        s[0] = elevationScore(elevation);
        s[1] = infra != null ? roadScore(infra) : -1;
        s[2] = infra != null ? powerScore(infra) : -1;
        s[3] = infra != null ? waterScore(infra) : -1;
        s[4] = infra != null ? envScore(infra) : -1;
        s[5] = climate != null ? climateScore(climate) : -1;
        return s;
    }

    // 1. Рельєф (висота над рівнем моря, м)
    private static double elevationScore(double e) {
        if (e >= 100 && e <= 500) return 1.00;   // ідеальний діапазон для України
        if (e >= 50 && e < 100) return 0.75;   // низовина, помірний ризик підтоплення
        if (e > 500 && e <= 800) return 0.75;   // пагорби, важчий рельєф
        if (e > 800 && e <= 1200) return 0.50;   // передгір'я
        if (e > 1200) return 0.30;   // гірський рельєф
        return 0.55;                              // < 50 м — висока ймовірність підтоплення
    }

    // 2. Транспортна доступність
    private static double roadScore(JsonObject i) {
        if (!safeBool(i, "road_nearby")) return 0.25;
        long dist = safeLong(i, "road_distance_m");
        if (dist < 0 || dist <= 150) return 1.00;
        if (dist <= 300) return 0.95;
        if (dist <= 600) return 0.85;
        if (dist <= 1000) return 0.70;
        return 0.50;
    }

    // 3. Електрична інфраструктура
    private static double powerScore(JsonObject i) {
        return safeBool(i, "power_nearby") ? 1.0 : 0.35;
    }

    // 4. Водні ресурси
    private static double waterScore(JsonObject i) {
        if (!safeBool(i, "water_nearby")) return 0.45;
        long dist = safeLong(i, "water_distance_m");
        if (dist >= 0 && dist < 80) return 0.55;  // занадто близько — ризик повені
        if (dist < 300) return 1.00;
        if (dist < 700) return 0.90;
        if (dist < 1500) return 0.75;
        return 0.55;
    }

    // 5. Екологічні ризики (знижуючі чинники)
    private static double envScore(JsonObject i) {
        double score = 1.0;
        if (safeBool(i, "cemetery_100m")) score -= 0.30;
        if (safeBool(i, "industrial_100m")) score -= 0.40;
        if (safeBool(i, "station_100m")) score -= 0.20;
        return Math.max(0.0, score);
    }

    // 6. Кліматичні умови (за річними показниками)
    private static double climateScore(JsonObject c) {
        double score = 1.0;

        if (c.has("annual_precipitation")) {
            double p = c.get("annual_precipitation").getAsDouble();
            if (p < 250) score -= 0.45;
            else if (p < 400) score -= 0.25;
            else if (p >= 400 && p <= 900) score += 0.00; // оптимум
            else if (p > 1100) score -= 0.20;
            else if (p > 900) score -= 0.10;
        }

        if (c.has("max_temp")) {
            double t = c.get("max_temp").getAsDouble();
            if (t > 40) score -= 0.20;
            else if (t > 36) score -= 0.10;
        }

        if (c.has("min_temp")) {
            double t = c.get("min_temp").getAsDouble();
            if (t < -30) score -= 0.20;
            else if (t < -22) score -= 0.10;
        }

        return Math.min(1.0, Math.max(0.0, score));
    }

    // ── Допоміжні методи ─────────────────────────────────────────────────

    private static boolean safeBool(JsonObject o, String key) {
        return o.has(key) && o.get(key).getAsBoolean();
    }

    private static long safeLong(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsLong() : -1;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
