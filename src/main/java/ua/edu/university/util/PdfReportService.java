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
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static ua.edu.university.util.MapExtrasBuilder.*;
import static ua.edu.university.util.PdfCellHelper.*;

/**
 * Генерує багатосторінковий PDF-звіт аналізу земельної ділянки.
 * Звіт містить титульну сторінку, сторінки картографічних шарів зі знімками
 * WebView та аналізом Claude AI, зведені таблиці інфраструктури, клімату
 * і придатності. Використовує OpenPDF; примітиви стилів — з {@link PdfCellHelper},
 * таблиці для сторінок карт — з {@link MapExtrasBuilder}.
 */
public class PdfReportService {
    private static final Logger logger = LoggerFactory.getLogger(PdfReportService.class);

    private static final String FOOTER =
            "Аналіз проведено з використанням супутникових даних Sentinel-2, Open-Meteo та Overpass API.\n" +
                    "© Лісотехнічний університет, Кафедра комп'ютерних наук.";

    public void generateReportExtended(String filePath, String title, String area,
                                       String priceUah, String priceUsd,
                                       double elevation, double suitability,
                                       List<Coordinate> boundaries,
                                       File mapScheme, File mapTerrain, File mapDem,
                                       File mapNdvi, File mapSat, File map3d,
                                       File mapSlope, File weatherChart, File infraAnnotated,
                                       Map<String, String> aiAnalyses,
                                       JsonObject climate, JsonObject infra,
                                       String dayLength, JsonObject country, JsonObject geoAddress,
                                       JsonArray poiData, double[] cornerElevations, File annualChartFile) {

        Document document = new Document(PageSize.A4, 40, 40, 45, 40);

        try {
            ensureDirectoryExists(filePath);
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            BaseFont bf = createBaseFont();
            Color mainColor = Color.decode(ConfigManager.getProperty("pdf.color.main"));
            Color accentColor = Color.decode(ConfigManager.getProperty("pdf.color.accent"));

            // --- СТОРІНКА 1: ОБКЛАДИНКА ---
            buildCoverPage(document, title, area, priceUah, priceUsd,
                    elevation, suitability, map3d, bf, mainColor, accentColor);

            // --- СТОРІНКА 2: КЛІМАТ ТА ТЕХНІЧНІ МЕРЕЖІ ---
            document.newPage();
            addSectionBanner(document, "КЛІМАТИЧНИЙ ТА ТЕХНІКО-ГЕОГРАФІЧНИЙ ПАСПОРТ", bf, mainColor, 2);
            addExtendedDataTables(document, climate, infra, dayLength, country, geoAddress,
                    weatherChart, infraAnnotated, elevation, suitability, bf, mainColor, accentColor);

            // --- КАРТОГРАФІЧНІ СТОРІНКИ ---
            if (mapScheme != null)
                addMapPage(document, "1.  ГЕОГРАФІЧНЕ РОЗТАШУВАННЯ ТА ІНФРАСТРУКТУРА", mapScheme, aiAnalyses.get("INFRASTRUCTURE"), bf, accentColor,
                        buildPoiExtras(poiData, bf, mainColor));
            if (mapTerrain != null)
                addMapPage(document, "2.  ТОПОГРАФІЧНИЙ РЕЛЬЄФ ТА МОРФОЛОГІЯ", mapTerrain, aiAnalyses.get("TERRAIN"), bf, accentColor,
                        buildCornerElevExtras(cornerElevations, elevation, boundaries, bf, mainColor));
            if (mapDem != null)
                addMapPage(document, "3.  ЦИФРОВА МОДЕЛЬ ВИСОТ  (DEM ANALYTICS)", mapDem, aiAnalyses.get("DEM"), bf, accentColor,
                        buildFloodRiskExtras(elevation, bf, mainColor, accentColor));
            if (mapNdvi != null)
                addMapPage(document, "4.  СТАН РОСЛИННОСТІ  (NDVI MONITORING)", mapNdvi, aiAnalyses.get("NDVI"), bf, accentColor,
                        buildAgroclimaticExtras(climate, bf, mainColor, accentColor));
            if (mapSat != null)
                addMapPage(document, "5.  СУПУТНИКОВИЙ МОНІТОРИНГ ТА ДИНАМІКА", mapSat, aiAnalyses.get("RETROSPECTIVE"), bf, accentColor,
                        buildAnnualChartExtras(annualChartFile));
            if (mapSlope != null)
                addMapPage(document, "6.  АНАЛІЗ СХИЛІВ  (HILLSHADE — ESRI ELEVATION)", mapSlope, aiAnalyses.get("SLOPE"), bf, accentColor,
                        buildSlopeExtras(cornerElevations, boundaries, bf, mainColor, accentColor));

            // --- ДОДАТОК: КООРДИНАТИ ---
            if (boundaries != null && !boundaries.isEmpty()) {
                addCoordinatesTable(document, boundaries, bf, mainColor);
            }

            addFooter(document, bf);
            logger.info("PDF звіт сформовано: {}", filePath);

        } catch (Exception e) {
            logger.error("Помилка генерації PDF: {}", e.getMessage());
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ОБКЛАДИНКА
    // ─────────────────────────────────────────────────────────────

    private void buildCoverPage(Document doc, String title, String area, String priceUah,
                                String priceUsd, double elevation, double suitability,
                                File map3d, BaseFont bf, Color main, Color accent) throws Exception {

        addHeader(doc, bf);

        PdfPTable banner = singleCellTable(main, 0, 0);
        PdfPCell bc = banner.getRow(0).getCells()[0];
        bc.setPaddingTop(22);
        bc.setPaddingBottom(18);
        Paragraph bannerTitle = new Paragraph("АНАЛІЗ ЗЕМЕЛЬНОЇ ДІЛЯНКИ",
                new Font(bf, 22, Font.BOLD, Color.WHITE));
        bannerTitle.setAlignment(Element.ALIGN_CENTER);
        Paragraph bannerSub = new Paragraph(
                "Інтелектуальний геоаналітичний звіт · Open-Meteo  ·  Overpass  ·  OSM",
                new Font(bf, 8, Font.NORMAL, new Color(189, 195, 199)));
        bannerSub.setAlignment(Element.ALIGN_CENTER);
        bannerSub.setSpacingBefore(4f);
        bc.addElement(bannerTitle);
        bc.addElement(bannerSub);
        doc.add(banner);

        PdfPTable infoRow = new PdfPTable(2);
        infoRow.setWidthPercentage(100);
        infoRow.setSpacingBefore(0);
        infoRow.setSpacingAfter(0);

        PdfPCell objCell = noBoderCell(COL_BG_LIGHT, 10);
        objCell.addElement(label("ОБ'ЄКТ АНАЛІЗУ", bf));
        objCell.addElement(new Paragraph(title, new Font(bf, 11, Font.BOLD, main)));
        infoRow.addCell(objCell);

        PdfPCell dateCell = noBoderCell(COL_BG_LIGHT, 10);
        dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph dateLabel = label("ДАТА ФОРМУВАННЯ", bf);
        dateLabel.setAlignment(Element.ALIGN_RIGHT);
        Paragraph dateVal = new Paragraph(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm")),
                new Font(bf, 11, Font.BOLD, main));
        dateVal.setAlignment(Element.ALIGN_RIGHT);
        dateCell.addElement(dateLabel);
        dateCell.addElement(dateVal);
        infoRow.addCell(dateCell);
        doc.add(infoRow);

        doc.add(new LineSeparator(2.5f, 100, accent, Element.ALIGN_CENTER, -2));
        doc.add(spacer(8));

        Font statLabel = new Font(bf, 7, Font.NORMAL, Color.GRAY);
        doc.add(statsGrid(area, priceUah, priceUsd, elevation, bf, statLabel, main, accent));
        doc.add(spacer(6));

        doc.add(suitabilityRow(suitability, bf, main, accent));
        doc.add(spacer(10));

        if (map3d != null && map3d.exists()) {
            addGraphicModel(doc, map3d, new Font(bf, 8, Font.ITALIC, Color.GRAY));
        }
    }

    private PdfPTable statsGrid(String area, String priceUah, String priceUsd,
                                double elevation, BaseFont bf,
                                Font labelFont, Color main, Color accent) throws DocumentException {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setSpacingBefore(0);
        t.setSpacingAfter(0);

        Font valFont = new Font(bf, 13, Font.BOLD, main);
        Font valSmall = new Font(bf, 11, Font.BOLD, main);
        Font valAccent = new Font(bf, 13, Font.BOLD, accent);

        addStatBox(t, "ПЛОЩА", area, labelFont, valFont);
        addStatBox(t, "ВИСОТА Н.Р.М.", String.format("%.1f м", elevation), labelFont, valFont);
        addStatBox(t, "ВАРТІСТЬ", priceUah, labelFont, valSmall);
        addStatBox(t, "USD ЕКВІВАЛЕНТ", priceUsd, labelFont, valAccent);
        return t;
    }

    private void addStatBox(PdfPTable table, String labelTxt, String valueTxt,
                            Font labelFont, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(Color.WHITE);
        cell.setPadding(12);
        cell.setPaddingTop(10);
        cell.setPaddingBottom(12);
        cell.setBorderColor(COL_BORDER);
        cell.setBorderWidth(0.7f);
        Paragraph lp = new Paragraph(labelTxt, labelFont);
        Paragraph vp = new Paragraph(valueTxt, valueFont);
        vp.setSpacingBefore(5f);
        cell.addElement(lp);
        cell.addElement(vp);
        table.addCell(cell);
    }

    private PdfPTable suitabilityRow(double suitability, BaseFont bf,
                                     Color main, Color accent) throws DocumentException {
        Color suitColor = suitability >= 0.9 ? accent
                : suitability >= 0.65 ? COL_WARN
                : COL_DANGER;
        String suitText = suitability >= 0.9 ? "ВИСОКА ПРИДАТНІСТЬ ДО ВИКОРИСТАННЯ"
                : suitability >= 0.65 ? "СЕРЕДНЯ ПРИДАТНІСТЬ ДО ВИКОРИСТАННЯ"
                : "ЗНИЖЕНА ПРИДАТНІСТЬ ДО ВИКОРИСТАННЯ";

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{68, 32});

        PdfPCell left = noBoderCell(COL_BG_LIGHT, 10);
        left.addElement(label("КОЕФІЦІЄНТ ПРИДАТНОСТІ ДІЛЯНКИ", bf));
        left.addElement(new Paragraph(suitText, new Font(bf, 11, Font.BOLD, suitColor)));
        t.addCell(left);

        PdfPCell right = noBoderCell(suitColor, 10);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Paragraph pct = new Paragraph(String.format("%.0f%%", suitability * 100),
                new Font(bf, 22, Font.BOLD, Color.WHITE));
        pct.setAlignment(Element.ALIGN_CENTER);
        right.addElement(pct);
        t.addCell(right);
        return t;
    }

    // ─────────────────────────────────────────────────────────────
    //  КАРТОГРАФІЧНІ СТОРІНКИ
    // ─────────────────────────────────────────────────────────────

    private void addMapPage(Document doc, String title, File mapFile,
                            String aiText, BaseFont bf, Color accent,
                            PdfPTable extras) throws Exception {
        doc.newPage();
        addSectionBanner(doc, title, bf, Color.decode("#34495E"), 2);

        if (mapFile != null && mapFile.exists()) {
            Image img = Image.getInstance(mapFile.getAbsolutePath());
            img.scaleToFit(515, 220);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setBorder(Rectangle.BOX);
            img.setBorderWidth(0.8f);
            img.setBorderColor(COL_BORDER);
            doc.add(img);
        }

        String displayText = (aiText != null && aiText.length() > 1200)
                ? aiText.substring(0, 1197) + "…" : aiText;

        PdfPTable aiBox = new PdfPTable(1);
        aiBox.setWidthPercentage(100);
        aiBox.setSpacingBefore(8f);
        aiBox.setSpacingAfter(0f);

        PdfPCell headerCell = noBoderCell(accent, 7);
        headerCell.setPaddingLeft(12);
        headerCell.addElement(new Paragraph("ЕКСПЕРТНИЙ ВИСНОВОК",
                new Font(bf, 9, Font.BOLD, Color.WHITE)));
        aiBox.addCell(headerCell);

        PdfPCell textCell = noBoderCell(COL_AI_BG, 12);
        addAiTextFormatted(textCell, displayText, bf);
        aiBox.addCell(textCell);
        doc.add(aiBox);

        if (extras != null) doc.add(extras);
    }

    private void addMapPage(Document doc, String title, File mapFile,
                            String aiText, BaseFont bf, Color accent) throws Exception {
        addMapPage(doc, title, mapFile, aiText, bf, accent, null);
    }

    // ─────────────────────────────────────────────────────────────
    //  КЛІМАТИЧНІ / ІНФРАСТРУКТУРНІ ТАБЛИЦІ
    // ─────────────────────────────────────────────────────────────

    private void addExtendedDataTables(Document document, JsonObject climate, JsonObject infra,
                                       String dayLength, JsonObject country, JsonObject geoAddress,
                                       File weatherChart, File infraAnnotated,
                                       double elevation, double suitability,
                                       BaseFont bf, Color mainColor, Color accentColor) throws Exception {
        Font hFont = new Font(bf, 10, Font.BOLD, Color.WHITE);
        Font nFont = new Font(bf, 10, Font.NORMAL);
        Font bFont = new Font(bf, 10, Font.BOLD);

        // 1. Кліматична таблиця
        document.add(new Paragraph("1.  Метеорологічні показники (за минулий рік):", bFont));
        PdfPTable climateTable = new PdfPTable(2);
        climateTable.setWidthPercentage(100);
        climateTable.setSpacingBefore(5f);
        climateTable.setSpacingAfter(8f);
        addStyledCell(climateTable, "Параметр", hFont, mainColor, true);
        addStyledCell(climateTable, "Середньорічне значення", hFont, mainColor, true);
        if (climate != null) {
            addStyledCell(climateTable, "Річна норма опадів", nFont, Color.WHITE, false);
            addStyledCell(climateTable, climate.get("annual_precipitation").getAsString() + " мм", bFont, Color.WHITE, false);
            addStyledCell(climateTable, "Температурний максимум", nFont, COL_ROW_ALT, false);
            addStyledCell(climateTable, climate.get("max_temp").getAsString() + " °C", bFont, COL_ROW_ALT, false);
            addStyledCell(climateTable, "Температурний мінімум", nFont, Color.WHITE, false);
            addStyledCell(climateTable, climate.get("min_temp").getAsString() + " °C", bFont, Color.WHITE, false);
        }
        document.add(climateTable);

        // Графік погоди за останні 30 днів
        if (weatherChart != null && weatherChart.exists()) {
            Image chart = Image.getInstance(weatherChart.getAbsolutePath());
            chart.scaleToFit(515, 195);
            chart.setAlignment(Element.ALIGN_CENTER);
            chart.setBorder(Rectangle.BOX);
            chart.setBorderWidth(0.5f);
            chart.setBorderColor(COL_BORDER);
            document.add(chart);
            Paragraph chartCaption = new Paragraph(
                    "Рис.  Динаміка температури (T max / T min) та опадів за останні 30 днів",
                    new Font(bf, 8, Font.ITALIC, Color.GRAY));
            chartCaption.setAlignment(Element.ALIGN_CENTER);
            chartCaption.setSpacingBefore(3f);
            chartCaption.setSpacingAfter(10f);
            document.add(chartCaption);
        }

        // 2. Інфраструктурна таблиця
        document.add(new Paragraph("2.  Аналіз прилеглої інфраструктури (OSM Overpass):", bFont));
        PdfPTable infraTable = new PdfPTable(3);
        infraTable.setWidthPercentage(100);
        infraTable.setWidths(new int[]{35, 20, 45});
        infraTable.setSpacingBefore(5f);
        infraTable.setSpacingAfter(14f);

        addStyledCell(infraTable, "Об'єкт", hFont, mainColor, true);
        addStyledCell(infraTable, "Статус", hFont, mainColor, true);
        addStyledCell(infraTable, "Деталі", hFont, mainColor, true);

        if (infra != null) {
            boolean power = safeBoolean(infra, "power_nearby");
            boolean water = safeBoolean(infra, "water_nearby");
            boolean road = safeBoolean(infra, "road_nearby");
            boolean cemetery = safeBoolean(infra, "cemetery_100m");
            boolean industrial = safeBoolean(infra, "industrial_100m");
            boolean station = safeBoolean(infra, "station_100m");

            Font statusFound = new Font(bf, 10, Font.BOLD, accentColor);
            Font statusMissing = new Font(bf, 10, Font.BOLD, COL_DANGER);
            Font statusWarn = new Font(bf, 10, Font.BOLD, COL_WARN);

            addStyledCell(infraTable, "Лінії електропередач", nFont, Color.WHITE, false);
            addStyledCell(infraTable, power ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО",
                    power ? statusFound : statusMissing, Color.WHITE, true);
            addStyledCell(infraTable, buildInfraDetail(infra, "power", power), nFont, Color.WHITE, false);

            addStyledCell(infraTable, "Водні об'єкти", nFont, COL_ROW_ALT, false);
            addStyledCell(infraTable, water ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО",
                    water ? statusFound : statusMissing, COL_ROW_ALT, true);
            addStyledCell(infraTable, buildWaterDetail(infra, water), nFont, COL_ROW_ALT, false);

            addStyledCell(infraTable, "Автодороги", nFont, Color.WHITE, false);
            addStyledCell(infraTable, road ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО",
                    road ? statusFound : statusMissing, Color.WHITE, true);
            addStyledCell(infraTable, buildInfraDetail(infra, "road", road), nFont, Color.WHITE, false);

            addStyledCell(infraTable, "Кладовище (100 м)", nFont, COL_ROW_ALT, false);
            addStyledCell(infraTable, cemetery ? "ВИЯВЛЕНО" : "ВІДСУТНЄ",
                    cemetery ? statusMissing : statusFound, COL_ROW_ALT, true);
            addStyledCell(infraTable, cemetery ? "кладовище у радіусі 100 м" : "не виявлено у радіусі 100 м",
                    nFont, COL_ROW_ALT, false);

            addStyledCell(infraTable, "Промислова зона (100 м)", nFont, Color.WHITE, false);
            addStyledCell(infraTable, industrial ? "ВИЯВЛЕНО" : "ВІДСУТНЯ",
                    industrial ? statusMissing : statusFound, Color.WHITE, true);
            addStyledCell(infraTable, industrial ? "промзона у радіусі 100 м" : "не виявлено у радіусі 100 м",
                    nFont, Color.WHITE, false);

            addStyledCell(infraTable, "Залізнична станція (100 м)", nFont, COL_ROW_ALT, false);
            addStyledCell(infraTable, station ? "ВИЯВЛЕНО" : "ВІДСУТНЯ",
                    station ? statusWarn : statusFound, COL_ROW_ALT, true);
            addStyledCell(infraTable, station ? "станція у радіусі 100 м" : "не виявлено у радіусі 100 м",
                    nFont, COL_ROW_ALT, false);
        }
        document.add(infraTable);

        // 3. Геофізичні та адресні дані
        document.add(new Paragraph("3.  Додаткові геофізичні відомості:", bFont));
        PdfPTable geoTable = new PdfPTable(2);
        geoTable.setWidthPercentage(100);
        geoTable.setSpacingBefore(5f);

        if (geoAddress != null && geoAddress.size() > 0) {
            String road = safeStr(geoAddress, "road");
            String suburb = safeStr(geoAddress, "suburb");
            String city = safeStr(geoAddress, "city");
            String county = safeStr(geoAddress, "county");
            String state = safeStr(geoAddress, "state");
            String post = safeStr(geoAddress, "postcode");

            if (!road.isEmpty()) {
                addStyledCell(geoTable, "Вулиця / дорога", nFont, Color.WHITE, false);
                addStyledCell(geoTable, road, bFont, Color.WHITE, false);
            }
            if (!suburb.isEmpty()) {
                addStyledCell(geoTable, "Район", nFont, COL_ROW_ALT, false);
                addStyledCell(geoTable, suburb, bFont, COL_ROW_ALT, false);
            }
            if (!city.isEmpty()) {
                addStyledCell(geoTable, "Населений пункт", nFont, Color.WHITE, false);
                addStyledCell(geoTable, city, bFont, Color.WHITE, false);
            }
            if (!county.isEmpty()) {
                addStyledCell(geoTable, "Адм. район", nFont, COL_ROW_ALT, false);
                addStyledCell(geoTable, county, bFont, COL_ROW_ALT, false);
            }
            if (!state.isEmpty()) {
                addStyledCell(geoTable, "Область", nFont, Color.WHITE, false);
                addStyledCell(geoTable, state, bFont, Color.WHITE, false);
            }
            if (!post.isEmpty()) {
                addStyledCell(geoTable, "Поштовий індекс", nFont, COL_ROW_ALT, false);
                addStyledCell(geoTable, post, bFont, COL_ROW_ALT, false);
            }
        }

        addStyledCell(geoTable, "Тривалість світлового дня", nFont, COL_ROW_ALT, false);
        addStyledCell(geoTable, dayLength != null ? dayLength : "н/д", bFont, COL_ROW_ALT, false);
        if (country != null && country.has("country")) {
            addStyledCell(geoTable, "Юрисдикція", nFont, Color.WHITE, false);
            addStyledCell(geoTable, country.get("country").getAsString(), bFont, Color.WHITE, false);
        }
        document.add(geoTable);

        // 4. Методологія розрахунку КП
        document.add(new Paragraph("4.  Методологія розрахунку коефіцієнта придатності (КП):", bFont));
        addSuitabilityBreakdown(document, elevation, infra, climate, suitability, bf, mainColor, accentColor);

        // 5. Аналіз транспортної доступності
        if (infraAnnotated != null && infraAnnotated.exists()) {
            document.add(new Paragraph("5.  Аналіз транспортної доступності:", bFont));
            Image annotatedImg = Image.getInstance(infraAnnotated.getAbsolutePath());
            annotatedImg.scaleToFit(515, 220);
            annotatedImg.setAlignment(Element.ALIGN_CENTER);
            annotatedImg.setBorder(Rectangle.BOX);
            annotatedImg.setBorderWidth(0.5f);
            annotatedImg.setBorderColor(COL_BORDER);
            annotatedImg.setSpacingBefore(5f);
            document.add(annotatedImg);

            Paragraph imgCaption = new Paragraph(
                    "Рис.  Напрямок та відстань до найближчої дороги (червона точка — центр ділянки, стрілка — напрямок до дороги)",
                    new Font(bf, 8, Font.ITALIC, Color.GRAY));
            imgCaption.setAlignment(Element.ALIGN_CENTER);
            imgCaption.setSpacingBefore(3f);
            imgCaption.setSpacingAfter(6f);
            document.add(imgCaption);

            Paragraph conclusion = new Paragraph(buildTransportConclusion(infra),
                    new Font(bf, 10, Font.NORMAL, new Color(44, 62, 80)));
            conclusion.setSpacingAfter(8f);
            document.add(conclusion);
        }
    }

    private void addSuitabilityBreakdown(Document document, double elevation,
                                         JsonObject infra, JsonObject climate, double totalScore,
                                         BaseFont bf, Color mainColor, Color accentColor) throws DocumentException {
        Font hFont = new Font(bf, 9, Font.BOLD, Color.WHITE);
        Font nFont = new Font(bf, 9, Font.NORMAL);
        Font bFont = new Font(bf, 9, Font.BOLD);
        Font naFont = new Font(bf, 9, Font.ITALIC, Color.GRAY);

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new int[]{42, 12, 16, 30});
        t.setSpacingBefore(5f);
        t.setSpacingAfter(6f);

        addStyledCell(t, "Чинник", hFont, mainColor, false);
        addStyledCell(t, "Вага", hFont, mainColor, true);
        addStyledCell(t, "Оцінка", hFont, mainColor, true);
        addStyledCell(t, "Внесок", hFont, mainColor, true);

        double[] scores = SuitabilityCalculator.getFactorScores(elevation, infra, climate);
        double[] weights = SuitabilityCalculator.FACTOR_WEIGHTS;
        String[] names = SuitabilityCalculator.FACTOR_NAMES;

        for (int i = 0; i < names.length; i++) {
            Color rowBg = (i % 2 == 0) ? Color.WHITE : COL_ROW_ALT;
            addStyledCell(t, names[i], nFont, rowBg, false);
            addStyledCell(t, String.format("%.0f%%", weights[i] * 100), nFont, rowBg, true);

            if (scores[i] < 0) {
                addStyledCell(t, "—", naFont, rowBg, true);
                addStyledCell(t, "н/д", naFont, rowBg, true);
            } else {
                Color scoreColor = scores[i] >= 0.85 ? accentColor
                        : scores[i] >= 0.60 ? COL_WARN : COL_DANGER;
                addStyledCell(t, String.format("%.0f%%", scores[i] * 100),
                        new Font(bf, 9, Font.BOLD, scoreColor), rowBg, true);
                addStyledCell(t, String.format("+%.2f", weights[i] * scores[i]), nFont, rowBg, true);
            }
        }

        Color totalColor = totalScore >= 0.9 ? accentColor : totalScore >= 0.65 ? COL_WARN : COL_DANGER;
        addStyledCell(t, "ПІДСУМКОВИЙ КП", new Font(bf, 9, Font.BOLD), mainColor, false);
        addStyledCell(t, "100%", new Font(bf, 9, Font.BOLD, Color.WHITE), mainColor, true);
        addStyledCell(t, String.format("%.0f%%", totalScore * 100), new Font(bf, 9, Font.BOLD, totalColor), mainColor, true);
        addStyledCell(t, String.format("= %.2f", totalScore), new Font(bf, 9, Font.BOLD, Color.WHITE), mainColor, true);

        document.add(t);
    }

    // ─────────────────────────────────────────────────────────────
    //  ІНФРАСТРУКТУРНІ ТЕКСТОВІ ХЕЛПЕРИ
    // ─────────────────────────────────────────────────────────────

    private String buildTransportConclusion(JsonObject infra) {
        if (infra == null || !safeBoolean(infra, "road_nearby")) {
            return "У радіусі 1000 м від ділянки автодоріг не виявлено. " +
                    "Транспортна доступність є обмеженою, що може суттєво вплинути на вартість " +
                    "та практичність господарського використання земельної ділянки.";
        }
        long dist = safeLong(infra, "road_distance_m");
        String type = safeStr(infra, "road_type");

        String level, comment;
        if (dist <= 0) {
            level = "достатньою";
            comment = "Рекомендовано уточнити відстань на місці.";
        } else if (dist <= 150) {
            level = "відмінною";
            comment = "Ділянка має безпосередній вихід на дорогу, що є значною перевагою при будівництві та експлуатації.";
        } else if (dist <= 400) {
            level = "доброю";
            comment = "Відстань до дороги є прийнятною для облаштування під'їзного шляху без значних витрат.";
        } else if (dist <= 700) {
            level = "задовільною";
            comment = "Слід врахувати витрати на прокладення під'їзної дороги у кошторисі будівництва.";
        } else {
            level = "обмеженою";
            comment = "Значна відстань до дороги потребує суттєвих капіталовкладень у транспортну інфраструктуру.";
        }

        String typeDesc = type.isEmpty() ? "автодорога" : type;
        String distStr = dist > 0 ? " на відстані ~" + dist + " м" : "";

        return "Найближча дорога (" + typeDesc + ") знаходиться" + distStr + " від ділянки. " +
                "Транспортна доступність оцінюється як " + level + ". " + comment;
    }

    private String buildInfraDetail(JsonObject infra, String prefix, boolean found) {
        if (!found) return "не виявлено в радіусі " + getRadius(prefix) + " м";
        if ("power".equals(prefix)) return "виявлено в радіусі " + getRadius(prefix) + " м";
        long dist = safeLong(infra, prefix + "_distance_m");
        String type = safeStr(infra, prefix + "_type");
        StringBuilder sb = new StringBuilder();
        if (!type.isEmpty()) sb.append(type);
        if (dist > 0) sb.append(sb.length() > 0 ? " · ~" : "~").append(dist).append(" м");
        return sb.length() > 0 ? sb.toString() : "дані присутні";
    }

    private String buildWaterDetail(JsonObject infra, boolean found) {
        if (!found) return "не виявлено в радіусі 1500 м";
        long dist = safeLong(infra, "water_distance_m");
        String name = safeStr(infra, "water_name");
        String type = safeStr(infra, "water_type");
        StringBuilder sb = new StringBuilder();
        if (!name.isEmpty()) sb.append(name);
        if (!type.isEmpty()) sb.append(name.isEmpty() ? type : " (" + type + ")");
        if (dist > 0) sb.append(" · ~").append(dist).append(" м");
        return sb.length() > 0 ? sb.toString() : "дані присутні";
    }

    private String getRadius(String prefix) {
        return switch (prefix) {
            case "power" -> "500";
            case "road" -> "1000";
            default -> "1500";
        };
    }

    // ─────────────────────────────────────────────────────────────
    //  СТРУКТУРНІ ЕЛЕМЕНТИ СТОРІНКИ
    // ─────────────────────────────────────────────────────────────

    private void addSectionBanner(Document doc, String text, BaseFont bf, Color bg) throws DocumentException {
        addSectionBanner(doc, text, bf, bg, 10);
    }

    private void addSectionBanner(Document doc, String text, BaseFont bf, Color bg,
                                  float spacingAfter) throws DocumentException {
        PdfPTable t = singleCellTable(bg, 0, spacingAfter);
        PdfPCell c = t.getRow(0).getCells()[0];
        c.setPaddingLeft(14);
        c.addElement(new Paragraph(text, new Font(bf, 12, Font.BOLD, Color.WHITE)));
        doc.add(t);
    }

    private void addAiTextFormatted(PdfPCell cell, String rawText, BaseFont bf) {
        Color textColor = new Color(44, 62, 80);
        Font normal = new Font(bf, 10, Font.NORMAL, textColor);
        Font bold = new Font(bf, 10, Font.BOLD, textColor);
        Font italic = new Font(bf, 10, Font.ITALIC, Color.GRAY);

        if (rawText == null || rawText.isBlank()) {
            cell.addElement(new Paragraph("Дані аналізу відсутні.", italic));
            return;
        }

        for (String raw : rawText.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.matches("^#{1,3}\\s+.*")) {
                line = stripInlineMarkdown(line.replaceAll("^#{1,3}\\s+", ""));
                Paragraph p = new Paragraph(line, bold);
                p.setLeading(14f);
                p.setSpacingBefore(4f);
                p.setSpacingAfter(0);
                cell.addElement(p);
                continue;
            }
            if (line.matches("^[-•]\\s+.*")) {
                line = stripInlineMarkdown(line.substring(2).trim());
                Paragraph p = new Paragraph("•  " + line, normal);
                p.setLeading(14f);
                p.setSpacingBefore(0);
                p.setSpacingAfter(0);
                p.setIndentationLeft(10f);
                cell.addElement(p);
                continue;
            }
            if (line.matches("^\\d+\\.\\s+.*")) {
                Paragraph p = new Paragraph(stripInlineMarkdown(line), normal);
                p.setLeading(14f);
                p.setSpacingBefore(0);
                p.setSpacingAfter(0);
                p.setIndentationLeft(10f);
                cell.addElement(p);
                continue;
            }
            Paragraph p = new Paragraph(stripInlineMarkdown(line), normal);
            p.setLeading(14f);
            p.setSpacingBefore(0);
            p.setSpacingAfter(0);
            cell.addElement(p);
        }
    }

    private String stripInlineMarkdown(String text) {
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("_(.+?)_", "$1")
                .replaceAll("`(.+?)`", "$1")
                .trim();
    }

    private void addHeader(Document document, BaseFont bf) throws DocumentException {
        String headerText = ConfigManager.getProperty("pdf.report.header");
        Paragraph header = new Paragraph(headerText, new Font(bf, 8, Font.ITALIC, Color.GRAY));
        header.setAlignment(Element.ALIGN_RIGHT);
        document.add(header);
    }

    private void addGraphicModel(Document document, File map3d, Font labelFont) throws Exception {
        Image img = Image.getInstance(map3d.getAbsolutePath());
        img.scaleToFit(515, 309);
        img.setAlignment(Element.ALIGN_CENTER);
        img.setBorder(Rectangle.BOX);
        img.setBorderWidth(0.5f);
        img.setBorderColor(COL_BORDER);
        document.add(spacer(4));
        document.add(img);
        Paragraph lbl = new Paragraph(
                "Рис 1.  Об'ємна геодезична модель ділянки (Axonometric Projection)", labelFont);
        lbl.setAlignment(Element.ALIGN_CENTER);
        lbl.setSpacingBefore(4f);
        document.add(lbl);
    }

    private void addFooter(Document document, BaseFont bf) throws DocumentException {
        document.add(spacer(6));
        document.add(new LineSeparator(0.5f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
        Paragraph footer = new Paragraph(FOOTER, new Font(bf, 7, Font.NORMAL, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(4f);
        document.add(footer);
    }

    private void addCoordinatesTable(Document document, List<Coordinate> boundaries,
                                     BaseFont bf, Color mainColor) throws DocumentException {
        document.newPage();
        addSectionBanner(document, "ГЕОДЕЗИЧНИЙ ДОДАТОК:  КООРДИНАТИ МЕЖ (WGS 84)", bf, mainColor, 2);

        PdfPTable coordTable = new PdfPTable(3);
        coordTable.setWidthPercentage(100);
        Font hFont = new Font(bf, 10, Font.BOLD, Color.WHITE);
        Font nFont = new Font(bf, 9, Font.NORMAL);
        addStyledCell(coordTable, "№", hFont, mainColor, true);
        addStyledCell(coordTable, "Latitude", hFont, mainColor, true);
        addStyledCell(coordTable, "Longitude", hFont, mainColor, true);

        int count = 1;
        for (Coordinate point : boundaries) {
            Color rowBg = (count % 2 == 0) ? COL_ROW_ALT : Color.WHITE;
            addStyledCell(coordTable, String.valueOf(count),
                    nFont, rowBg, true);
            addStyledCell(coordTable, String.format("%.6f", point.getLatitude()),
                    nFont, rowBg, false);
            addStyledCell(coordTable, String.format("%.6f", point.getLongitude()),
                    nFont, rowBg, false);
            if (++count > 50) break;
        }
        document.add(coordTable);
    }

    private BaseFont createBaseFont() throws Exception {
        return BaseFont.createFont(
                ConfigManager.getProperty("pdf.font.path"),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED);
    }

    private void ensureDirectoryExists(String filePath) {
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }
}
