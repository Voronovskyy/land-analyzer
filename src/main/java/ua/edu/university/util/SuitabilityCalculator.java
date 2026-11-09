package ua.edu.university.util;

import com.google.gson.JsonObject;

import java.util.Locale;

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

    /**
     * Оцінка одного чинника разом із підставою.
     * Підстава потрібна у звіті: сам по собі відсоток (напр. «Водні ресурси —
     * 55 %») не пояснює, чому оцінка знижена, і виглядає як помилка.
     *
     * @param score [0..1], або -1 якщо даних немає
     */
    public record Factor(String name, double weight, double score, String reason) {
        public boolean hasData() {
            return score >= 0;
        }
    }

    // ── Публічний API ─────────────────────────────────────────────────────

    /** Повний перелік чинників з оцінками та підставами, у порядку FACTOR_NAMES. */
    public static Factor[] evaluate(double elevation, JsonObject infra, JsonObject climate) {
        return new Factor[]{
                elevationFactor(elevation),
                infra != null ? roadFactor(infra) : noData(1),
                infra != null ? powerFactor(infra) : noData(2),
                infra != null ? waterFactor(infra) : noData(3),
                infra != null ? envFactor(infra) : noData(4),
                climate != null ? climateFactor(climate) : noData(5),
        };
    }

    private static Factor noData(int i) {
        return new Factor(FACTOR_NAMES[i], FACTOR_WEIGHTS[i], -1, "Дані недоступні");
    }

    /**
     * Повний розрахунок КП на основі всіх доступних даних.
     * Якщо infra або climate = null — відповідні чинники виключаються,
     * а ваги нормалізуються.
     */
    /** Припущення для чинника, дані по якому відсутні: ні добре, ні погано. */
    private static final double NEUTRAL_SCORE = 0.5;

    public static double calculate(double elevation, JsonObject infra, JsonObject climate) {
        double[] scores = computeScores(elevation, infra, climate);
        double sumWS = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= 0) {               // -1 означає "дані відсутні"
                sumWS += FACTOR_WEIGHTS[i] * scores[i];
            }
        }
        // Невідомі чинники беремо нейтральними, а не перенормовуємо ваги до
        // відомих. Перенормування неявно припускало, що невідоме дорівнює
        // середньому відомого: при двох доступних чинниках із максимальним
        // балом (рельєф + клімат = 35 % ваги) звіт друкував 100 % придатності
        // поруч із чотирма рядками «Дані недоступні».
        double result = sumWS + (1.0 - knownWeight(scores)) * NEUTRAL_SCORE;
        return round2(Math.min(1.0, Math.max(0.05, result)));
    }

    /**
     * Частка ваги чинників, для яких є дані [0..1]. Потрібна у звіті поряд із
     * КП: та сама цифра означає різне при повному й частковому покритті.
     */
    public static double coverage(double elevation, JsonObject infra, JsonObject climate) {
        return round2(knownWeight(computeScores(elevation, infra, climate)));
    }

    private static double knownWeight(double[] scores) {
        double sumW = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= 0) sumW += FACTOR_WEIGHTS[i];
        }
        return sumW;
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
    /**
     * Смуги переглянуто: раніше об'єкт на межі радіуса пошуку (1500 м) давав
     * 0.75 проти 0.45 за повну відсутність води — третина діапазону чинника
     * за водойму, яка на такій відстані ділянці ні ресурс, ні ризик.
     */
    private static double waterScore(JsonObject i) {
        if (!safeBool(i, "water_nearby")) return 0.45;
        long dist = safeLong(i, "water_distance_m");
        double score;
        if (dist < 0) score = 0.75;             // вода є, відстань невідома
        else if (dist < 80) score = 0.55;       // надто близько — ризик підтоплення
        else if (dist < 300) score = 1.00;
        else if (dist < 600) score = 0.85;
        else if (dist < 1000) score = 0.60;
        else score = 0.48;                      // майже як відсутність води
        // Ручай — слабший ресурс за річку чи канал: обсяг і стабільність
        // стоку непорівнянні, особливо в межень.
        if ("stream".equals(safeStr(i, "water_type_raw"))) score *= 0.85;
        return score;
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

    // ── Чинники з підставами ─────────────────────────────────────────────

    private static Factor elevationFactor(double e) {
        String reason;
        if (e >= 100 && e <= 500) reason = String.format(Locale.US, "%.0f м — оптимальний діапазон", e);
        else if (e >= 50 && e < 100) reason = String.format(Locale.US, "%.0f м — низовина, можливе підтоплення", e);
        else if (e > 500 && e <= 800) reason = String.format(Locale.US, "%.0f м — пагорби, ускладнений рельєф", e);
        else if (e > 800 && e <= 1200) reason = String.format(Locale.US, "%.0f м — передгір'я", e);
        else if (e > 1200) reason = String.format(Locale.US, "%.0f м — гірський рельєф", e);
        else reason = String.format(Locale.US, "%.0f м — висока ймовірність підтоплення", e);
        return new Factor(FACTOR_NAMES[0], FACTOR_WEIGHTS[0], elevationScore(e), reason);
    }

    private static Factor roadFactor(JsonObject i) {
        String reason;
        if (!safeBool(i, "road_nearby")) {
            reason = "доріг у радіусі 1000 м не виявлено";
        } else {
            long dist = safeLong(i, "road_distance_m");
            reason = dist < 0 ? "дорога поруч" : "найближча дорога ~" + dist + " м";
        }
        return new Factor(FACTOR_NAMES[1], FACTOR_WEIGHTS[1], roadScore(i), reason);
    }

    private static Factor powerFactor(JsonObject i) {
        String reason;
        if (!safeBool(i, "power_nearby")) {
            reason = "ЛЕП у радіусі 500 м не виявлено";
        } else {
            long dist = safeLong(i, "power_distance_m");
            reason = dist < 0 ? "ЛЕП у радіусі 500 м" : "найближча ЛЕП ~" + dist + " м";
        }
        return new Factor(FACTOR_NAMES[2], FACTOR_WEIGHTS[2], powerScore(i), reason);
    }

    private static Factor waterFactor(JsonObject i) {
        String reason;
        if (!safeBool(i, "water_nearby")) {
            reason = "водойм у радіусі 1500 м не виявлено";
            // Канава — не ресурс, але й не замовчуємо її: вона свідчить про
            // те, що територію доводиться осушувати.
            long drain = safeLong(i, "drain_distance_m");
            if (safeBool(i, "drain_nearby") && drain >= 0) {
                reason += "; дренажна канава ~" + drain + " м — не водний ресурс";
            }
        } else {
            long dist = safeLong(i, "water_distance_m");
            if (dist < 0) reason = "водойму виявлено, відстань не визначено";
            else if (dist < 80) reason = "вода за ~" + dist + " м — надто близько, ризик підтоплення";
            else if (dist < 300) reason = "вода за ~" + dist + " м — оптимально";
            else if (dist < 1000) reason = "вода за ~" + dist + " м";
            else reason = "вода за ~" + dist + " м — надто далеко для використання";
        }
        return new Factor(FACTOR_NAMES[3], FACTOR_WEIGHTS[3], waterScore(i), reason);
    }

    private static Factor envFactor(JsonObject i) {
        StringBuilder sb = new StringBuilder();
        if (safeBool(i, "cemetery_100m")) sb.append("кладовище; ");
        if (safeBool(i, "industrial_100m")) sb.append("промзона; ");
        if (safeBool(i, "station_100m")) sb.append("залізнична станція; ");
        String reason = sb.length() == 0
                ? "у радіусі 100 м знижуючих чинників немає"
                : "у радіусі 100 м: " + sb.substring(0, sb.length() - 2);
        return new Factor(FACTOR_NAMES[4], FACTOR_WEIGHTS[4], envScore(i), reason);
    }

    private static Factor climateFactor(JsonObject c) {
        StringBuilder sb = new StringBuilder();
        if (c.has("annual_precipitation")) {
            double p = c.get("annual_precipitation").getAsDouble();
            sb.append(String.format(Locale.US, "опади %.0f мм", p));
            if (p < 250) sb.append(" — посушливо");
            else if (p < 400) sb.append(" — недостатньо");
            else if (p > 1100) sb.append(" — надлишок");
            else if (p > 900) sb.append(" — підвищені");
            else sb.append(" — норма");
        }
        if (c.has("max_temp")) {
            double t = c.get("max_temp").getAsDouble();
            if (t > 36) sb.append(String.format(Locale.US, "; макс. %.0f °C — спека", t));
        }
        if (c.has("min_temp")) {
            double t = c.get("min_temp").getAsDouble();
            if (t < -22) sb.append(String.format(Locale.US, "; мін. %.0f °C — сильні морози", t));
        }
        String reason = sb.length() == 0 ? "кліматичні показники в межах норми" : sb.toString();
        return new Factor(FACTOR_NAMES[5], FACTOR_WEIGHTS[5], climateScore(c), reason);
    }

    // ── Допоміжні методи ─────────────────────────────────────────────────

    private static boolean safeBool(JsonObject o, String key) {
        return o.has(key) && o.get(key).getAsBoolean();
    }

    private static long safeLong(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsLong() : -1;
    }

    private static String safeStr(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
