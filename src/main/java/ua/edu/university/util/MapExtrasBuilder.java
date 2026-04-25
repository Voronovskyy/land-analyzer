package ua.edu.university.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import ua.edu.university.model.Coordinate;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import static ua.edu.university.util.PdfCellHelper.*;

/**
 * Будує допоміжні таблиці OpenPDF для кожної сторінки карти у звіті:
 * POI-інфраструктура, перепад висот, ризик підтоплення, агрокліматика,
 * річна діаграма та аналіз схилів. Всі методи статичні; клас не інстанціюється.
 * Імпортується через {@code import static} у {@link PdfReportService}.
 */
public final class MapExtrasBuilder {

    private MapExtrasBuilder() {
    }

    // 1 — Схема: найближча інфраструктура (POI)
    public static PdfPTable buildPoiExtras(JsonArray poiData, BaseFont bf, Color main) throws DocumentException {
        Font hf = new Font(bf, 10f, Font.BOLD, Color.WHITE);
        Font nf = new Font(bf, 10f, Font.NORMAL);
        Font bf2 = new Font(bf, 10f, Font.BOLD);

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{28, 52, 20});
        t.setSpacingBefore(22f);
        t.setSpacingAfter(2f);
        addCompact(t, "Тип", hf, main, false);
        addCompact(t, "Об'єкт", hf, main, false);
        addCompact(t, "Відстань", hf, main, true);

        if (poiData == null || poiData.size() == 0) {
            addCompact(t, "Об'єктів не знайдено в радіусі 2 км", nf, Color.WHITE, false);
            addCompact(t, "", nf, Color.WHITE, false);
            addCompact(t, "", nf, Color.WHITE, false);
        } else {
            for (int i = 0; i < poiData.size(); i++) {
                JsonObject p = poiData.get(i).getAsJsonObject();
                Color bg = (i % 2 == 0) ? Color.WHITE : COL_ROW_ALT;
                addCompact(t, p.get("type").getAsString(), nf, bg, false);
                addCompact(t, p.get("name").getAsString(), nf, bg, false);
                addCompact(t, "~" + p.get("dist").getAsLong() + " м", bf2, bg, true);
            }
        }
        return t;
    }

    // 2 — Рельєф: порівняльна таблиця висот кутів
    public static PdfPTable buildCornerElevExtras(double[] ce, double centerElev,
                                                  List<Coordinate> boundaries,
                                                  BaseFont bf, Color main) throws DocumentException {
        Font hf = new Font(bf, 10f, Font.BOLD, Color.WHITE);
        Font nf = new Font(bf, 10f, Font.NORMAL);
        Font bf2 = new Font(bf, 10f, Font.BOLD);

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{55, 45});
        t.setSpacingBefore(22f);
        t.setSpacingAfter(2f);
        addCompact(t, "Параметр рельєфу", hf, main, false);
        addCompact(t, "Значення", hf, main, true);

        if (ce == null || ce.length < 4) {
            addCompact(t, "Дані висот недоступні", nf, Color.WHITE, false);
            addCompact(t, "н/д", bf2, Color.WHITE, true);
            return t;
        }
        double minE = Arrays.stream(ce).min().getAsDouble();
        double maxE = Arrays.stream(ce).max().getAsDouble();
        double deltaH = maxE - minE;
        double slopeAngleDeg = estimateSlopeAngle(ce, boundaries);
        String slopeClass = slopeAngleDeg < 1 ? "Рівнина" : slopeAngleDeg < 3 ? "Пологий" :
                slopeAngleDeg < 8 ? "Похилий" : "Крутий";

        String[][] rows = {
                {"Висота центру ділянки", String.format("%.1f м н.р.м.", centerElev)},
                {"Мін. висота (кути)", String.format("%.1f м н.р.м.", minE)},
                {"Макс. висота (кути)", String.format("%.1f м н.р.м.", maxE)},
                {"Перепад висот", String.format("%.1f м", deltaH)},
                {"Клас рельєфу", String.format("%s (%.1f°)", slopeClass, slopeAngleDeg)}
        };
        for (int i = 0; i < rows.length; i++) {
            Color bg = (i % 2 == 0) ? Color.WHITE : COL_ROW_ALT;
            addCompact(t, rows[i][0], nf, bg, false);
            addCompact(t, rows[i][1], bf2, bg, true);
        }
        return t;
    }

    // 3 — DEM: ризик підтоплення за висотою
    public static PdfPTable buildFloodRiskExtras(double elevation, BaseFont bf,
                                                 Color main, Color accent) throws DocumentException {
        Font hf = new Font(bf, 10f, Font.BOLD, Color.WHITE);
        Font nf = new Font(bf, 10f, Font.NORMAL);
        Font bf2 = new Font(bf, 10f, Font.BOLD);

        String risk, action, terrain;
        Color riskColor;
        if (elevation < 50) {
            risk = "Високий";
            action = "Обов'язковий дренаж та підняття підошви";
            terrain = "Низовина";
            riskColor = COL_DANGER;
        } else if (elevation < 100) {
            risk = "Підвищений";
            action = "Рекомендований дренаж";
            terrain = "Передгір'я низовини";
            riskColor = COL_WARN;
        } else if (elevation < 200) {
            risk = "Помірний";
            action = "Стандартний фундамент";
            terrain = "Горбистий рельєф";
            riskColor = COL_WARN;
        } else if (elevation < 500) {
            risk = "Низький";
            action = "Без обмежень";
            terrain = "Височина";
            riskColor = accent;
        } else {
            risk = "Мінімальний";
            action = "Без обмежень";
            terrain = "Гірський рельєф";
            riskColor = accent;
        }

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{55, 45});
        t.setSpacingBefore(22f);
        t.setSpacingAfter(2f);
        addCompact(t, "Гідрологічний параметр", hf, main, false);
        addCompact(t, "Оцінка", hf, main, true);

        addCompact(t, "Абсолютна висота", nf, Color.WHITE, false);
        addCompact(t, String.format("%.0f м н.р.м.", elevation), bf2, Color.WHITE, true);
        addCompact(t, "Тип рельєфу", nf, COL_ROW_ALT, false);
        addCompact(t, terrain, bf2, COL_ROW_ALT, true);

        PdfPCell riskCell = new PdfPCell(new Phrase(risk, new Font(bf, 10f, Font.BOLD, riskColor)));
        riskCell.setPadding(7);
        riskCell.setBackgroundColor(Color.WHITE);
        riskCell.setBorderColor(COL_BORDER);
        riskCell.setBorderWidth(0.5f);
        riskCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        addCompact(t, "Ризик підтоплення", nf, Color.WHITE, false);
        t.addCell(riskCell);
        addCompact(t, "Рекомендації", nf, COL_ROW_ALT, false);
        addCompact(t, action, nf, COL_ROW_ALT, false);
        return t;
    }

    // 4 — NDVI: агрокліматичний індекс Де Мартонна
    public static PdfPTable buildAgroclimaticExtras(JsonObject climate, BaseFont bf,
                                                    Color main, Color accent) throws DocumentException {
        Font hf = new Font(bf, 10f, Font.BOLD, Color.WHITE);
        Font nf = new Font(bf, 10f, Font.NORMAL);
        Font bf2 = new Font(bf, 10f, Font.BOLD);

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{42, 22, 36});
        t.setSpacingBefore(22f);
        t.setSpacingAfter(2f);
        addCompact(t, "Агрокліматичний показник", hf, main, false);
        addCompact(t, "Значення", hf, main, true);
        addCompact(t, "Оцінка", hf, main, true);

        if (climate == null) {
            addCompact(t, "Кліматичні дані недоступні", nf, Color.WHITE, false);
            addCompact(t, "н/д", bf2, Color.WHITE, true);
            addCompact(t, "—", nf, Color.WHITE, true);
            return t;
        }

        double P = climate.has("annual_precipitation") ? climate.get("annual_precipitation").getAsDouble() : 0;
        double tmax = climate.has("max_temp") ? climate.get("max_temp").getAsDouble() : 20;
        double tmin = climate.has("min_temp") ? climate.get("min_temp").getAsDouble() : 0;
        double tmean = (tmax + tmin) / 2.0;
        double I = (tmean + 10 > 0) ? P / (tmean + 10) : 0;

        String iClass, crops;
        if (I < 10) {
            iClass = "Посушливий";
            crops = "соняшник, просо";
        } else if (I < 20) {
            iClass = "Напівпосушливий";
            crops = "пшениця, ячмінь";
        } else if (I < 30) {
            iClass = "Напівгумідний";
            crops = "зернові, кукурудза";
        } else if (I < 60) {
            iClass = "Гумідний";
            crops = "зернові, овочі, фрукти";
        } else {
            iClass = "Гіпергумідний";
            crops = "луки, ліс";
        }

        String[][] rows = {
                {"Річні опади (P)", String.format("%.0f мм", P), P > 400 && P < 900 ? "Норма" : "Відхилення"},
                {"Серед. температура (T̄)", String.format("%.1f °C", tmean), tmean > 5 ? "Оптимум" : "Знижена"},
                {"Індекс Де Мартонна (I)", String.format("%.1f", I), iClass},
                {"Агрокліматична зона", iClass, ""},
                {"Рекомендовані культури", crops, ""}
        };
        for (int i = 0; i < rows.length; i++) {
            Color bg = (i % 2 == 0) ? Color.WHITE : COL_ROW_ALT;
            addCompact(t, rows[i][0], nf, bg, false);
            addCompact(t, rows[i][1], bf2, bg, true);
            addCompact(t, rows[i][2], nf, bg, true);
        }
        return t;
    }

    // 5 — Супутник: річна кліматична діаграма
    public static PdfPTable buildAnnualChartExtras(File chartFile) throws Exception {
        if (chartFile == null || !chartFile.exists()) return null;
        Image chart = Image.getInstance(chartFile.getAbsolutePath());
        chart.scaleToFit(515, 125);
        chart.setAlignment(Element.ALIGN_CENTER);

        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(22f);
        t.setSpacingAfter(2f);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOX);
        c.setBorderColor(COL_BORDER);
        c.setBorderWidth(0.5f);
        c.setPadding(0);
        c.addElement(chart);
        t.addCell(c);
        return t;
    }

    // 6 — Схили: придатність за ухилом
    public static PdfPTable buildSlopeExtras(double[] ce, List<Coordinate> boundaries,
                                             BaseFont bf, Color main, Color accent) throws DocumentException {
        Font hf = new Font(bf, 10f, Font.BOLD, Color.WHITE);
        Font nf = new Font(bf, 10f, Font.NORMAL);
        Font bf2 = new Font(bf, 10f, Font.BOLD);

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{40, 20, 40});
        t.setSpacingBefore(22f);
        t.setSpacingAfter(2f);
        addCompact(t, "Напрямок використання", hf, main, false);
        addCompact(t, "Оцінка", hf, main, true);
        addCompact(t, "Коментар", hf, main, false);

        double slopeDeg = (ce != null && ce.length >= 4) ? estimateSlopeAngle(ce, boundaries) : -1;

        String buildRating, agriRating, erosionRisk, buildComment, agriComment;
        if (slopeDeg < 0) {
            buildRating = "н/д";
            agriRating = "н/д";
            erosionRisk = "н/д";
            buildComment = "Дані недоступні";
            agriComment = "Дані недоступні";
        } else if (slopeDeg < 1) {
            buildRating = "★★★★★";
            agriRating = "★★★★★";
            erosionRisk = "Мінімальний";
            buildComment = "Рівна поверхня — ідеально";
            agriComment = "Без обмежень";
        } else if (slopeDeg < 3) {
            buildRating = "★★★★☆";
            agriRating = "★★★★★";
            erosionRisk = "Низький";
            buildComment = "Сприятливо, стандартний фундамент";
            agriComment = "Без обмежень";
        } else if (slopeDeg < 8) {
            buildRating = "★★★☆☆";
            agriRating = "★★★★☆";
            erosionRisk = "Помірний";
            buildComment = "Потрібне вирівнювання";
            agriComment = "Рекомендований дренаж";
        } else if (slopeDeg < 15) {
            buildRating = "★★☆☆☆";
            agriRating = "★★★☆☆";
            erosionRisk = "Підвищений";
            buildComment = "Складне будівництво";
            agriComment = "Тераси або контурна оранка";
        } else {
            buildRating = "★☆☆☆☆";
            agriRating = "★★☆☆☆";
            erosionRisk = "Високий";
            buildComment = "Не рекомендовано";
            agriComment = "Лише пасовища або ліс";
        }

        Color erosionColor = erosionRisk.equals("Мінімальний") || erosionRisk.equals("Низький")
                ? accent : erosionRisk.equals("Помірний") ? COL_WARN : COL_DANGER;
        Font erosionFont = new Font(bf, 10f, Font.BOLD, erosionColor);

        addCompact(t, "Будівництво", nf, Color.WHITE, false);
        addCompact(t, buildRating, bf2, Color.WHITE, true);
        addCompact(t, buildComment, nf, Color.WHITE, false);
        addCompact(t, "Сільське господарство", nf, COL_ROW_ALT, false);
        addCompact(t, agriRating, bf2, COL_ROW_ALT, true);
        addCompact(t, agriComment, nf, COL_ROW_ALT, false);

        PdfPCell erosionCell = new PdfPCell(new Phrase(erosionRisk, erosionFont));
        erosionCell.setPadding(7);
        erosionCell.setBackgroundColor(Color.WHITE);
        erosionCell.setBorderColor(COL_BORDER);
        erosionCell.setBorderWidth(0.5f);
        erosionCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        addCompact(t, "Ризик ерозії", nf, Color.WHITE, false);
        t.addCell(erosionCell);
        String slopeStr = slopeDeg >= 0 ? String.format("Ухил ~%.1f°", slopeDeg) : "Дані недоступні";
        addCompact(t, slopeStr, nf, Color.WHITE, false);
        return t;
    }

    private static double estimateSlopeAngle(double[] ce, List<Coordinate> boundaries) {
        if (ce == null || ce.length < 4 || boundaries == null || boundaries.size() < 2) return 0;
        double deltaH = Arrays.stream(ce).max().getAsDouble() - Arrays.stream(ce).min().getAsDouble();
        double latMin = boundaries.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double latMax = boundaries.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double lonMin = boundaries.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double lonMax = boundaries.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);
        double diagM = haversineM(latMin, lonMin, latMax, lonMax);
        return diagM > 1 ? Math.toDegrees(Math.atan(deltaH / diagM)) : 0;
    }

    private static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
