package ua.edu.university.util;

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

public class PdfReportService {
    private static final Logger logger = LoggerFactory.getLogger(PdfReportService.class);

    private static final Color COL_BG_LIGHT  = new Color(244, 246, 248);
    private static final Color COL_BORDER    = new Color(213, 216, 220);
    private static final Color COL_AI_BG     = new Color(235, 245, 251);
    private static final Color COL_ROW_ALT   = new Color(245, 245, 245);
    private static final Color COL_WARN      = new Color(243, 156, 18);
    private static final Color COL_DANGER    = new Color(231, 76, 60);

    private static final String FOOTER =
            "Аналіз проведено з використанням супутникових даних Sentinel-2, Open-Meteo та Overpass API.\n" +
            "© Лісотехнічний університет, Кафедра комп'ютерних наук.";

    public void generateReportExtended(String filePath, String title, String area,
                                       String priceUah, String priceUsd,
                                       double elevation, double suitability,
                                       List<Coordinate> boundaries,
                                       File mapScheme, File mapTerrain, File mapDem,
                                       File mapNdvi, File mapSat, File map3d,
                                       File mapSlope, File weatherChart,
                                       Map<String, String> aiAnalyses,
                                       JsonObject climate, JsonObject infra,
                                       String dayLength, JsonObject country) {

        Document document = new Document(PageSize.A4, 40, 40, 45, 40);

        try {
            ensureDirectoryExists(filePath);
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            BaseFont bf = createBaseFont();
            Color mainColor   = Color.decode(ConfigManager.getProperty("pdf.color.main"));
            Color accentColor = Color.decode(ConfigManager.getProperty("pdf.color.accent"));

            // --- СТОРІНКА 1: ОБКЛАДИНКА ---
            buildCoverPage(document, title, area, priceUah, priceUsd,
                    elevation, suitability, map3d, bf, mainColor, accentColor);

            // --- СТОРІНКА 2: КЛІМАТ ТА ТЕХНІЧНІ МЕРЕЖІ ---
            document.newPage();
            addSectionBanner(document, "КЛІМАТИЧНИЙ ТА ТЕХНІКО-ГЕОГРАФІЧНИЙ ПАСПОРТ", bf, mainColor, 2);
            addExtendedDataTables(document, climate, infra, dayLength, country, weatherChart, bf, mainColor, accentColor);

            // --- КАРТОГРАФІЧНІ СТОРІНКИ ---
            if (mapScheme  != null) addMapPage(document, "1.  ГЕОГРАФІЧНЕ РОЗТАШУВАННЯ ТА ІНФРАСТРУКТУРА", mapScheme,  aiAnalyses.get("INFRASTRUCTURE"), bf, accentColor);
            if (mapTerrain != null) addMapPage(document, "2.  ТОПОГРАФІЧНИЙ РЕЛЬЄФ ТА МОРФОЛОГІЯ",          mapTerrain, aiAnalyses.get("TERRAIN"),        bf, accentColor);
            if (mapDem     != null) addMapPage(document, "3.  ЦИФРОВА МОДЕЛЬ ВИСОТ  (DEM ANALYTICS)",       mapDem,     aiAnalyses.get("DEM"),             bf, accentColor);
            if (mapNdvi    != null) addMapPage(document, "4.  СТАН РОСЛИННОСТІ  (NDVI MONITORING)",         mapNdvi,    aiAnalyses.get("NDVI"),            bf, accentColor);
            if (mapSat     != null) addMapPage(document, "5.  СУПУТНИКОВИЙ МОНІТОРИНГ ТА ДИНАМІКА",         mapSat,     aiAnalyses.get("RETROSPECTIVE"),   bf, accentColor);
            if (mapSlope   != null) addMapPage(document, "6.  АНАЛІЗ СХИЛІВ  (HILLSHADE — ESRI ELEVATION)", mapSlope,   aiAnalyses.get("SLOPE"),           bf, accentColor);

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

        // Верхній рядок дати
        addHeader(doc, bf);

        // Головний банер
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

        // Рядок: об'єкт | дата
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

        // Акцентна лінія
        doc.add(new LineSeparator(2.5f, 100, accent, Element.ALIGN_CENTER, -2));
        doc.add(spacer(8));

        // Сітка показників (4 клітинки)
        Font statLabel = new Font(bf, 7, Font.NORMAL, Color.GRAY);
        doc.add(statsGrid(area, priceUah, priceUsd, elevation, bf, statLabel, main, accent));
        doc.add(spacer(6));

        // Рядок придатності
        doc.add(suitabilityRow(suitability, bf, main, accent));
        doc.add(spacer(10));

        // 3D модель (якщо є)
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

        Font valFont  = new Font(bf, 13, Font.BOLD, main);
        Font valSmall = new Font(bf, 11, Font.BOLD, main);
        Font valAccent= new Font(bf, 13, Font.BOLD, accent);

        addStatBox(t, "ПЛОЩА",           area,                          labelFont, valFont);
        addStatBox(t, "ВИСОТА Н.Р.М.",   String.format("%.1f м", elevation), labelFont, valFont);
        addStatBox(t, "ВАРТІСТЬ",         priceUah,                      labelFont, valSmall);
        addStatBox(t, "USD ЕКВІВАЛЕНТ",  priceUsd,                      labelFont, valAccent);
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

    private PdfPTable suitabilityRow(double suitability, BaseFont bf, Color main, Color accent) throws DocumentException {
        Color suitColor = suitability >= 0.9 ? accent
                        : suitability >= 0.65 ? COL_WARN
                        : COL_DANGER;
        String suitText = suitability >= 0.9  ? "ВИСОКА ПРИДАТНІСТЬ ДО ВИКОРИСТАННЯ"
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
                            String aiText, BaseFont bf, Color accent) throws Exception {
        doc.newPage();

        // Заголовочний банер сторінки
        addSectionBanner(doc, title, bf, Color.decode("#34495E"), 2);

        // Зображення карти
        if (mapFile != null && mapFile.exists()) {
            Image img = Image.getInstance(mapFile.getAbsolutePath());
            img.scaleToFit(515, 318);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setBorder(Rectangle.BOX);
            img.setBorderWidth(0.8f);
            img.setBorderColor(COL_BORDER);
            doc.add(img);
        }

        PdfPTable aiBox = new PdfPTable(1);
        aiBox.setWidthPercentage(100);
        aiBox.setSpacingBefore(10f);

        PdfPCell headerCell = noBoderCell(accent, 7);
        headerCell.setPaddingLeft(12);
        headerCell.addElement(new Paragraph("ЕКСПЕРТНИЙ ВИСНОВОК",
                new Font(bf, 9, Font.BOLD, Color.WHITE)));
        aiBox.addCell(headerCell);

        PdfPCell textCell = noBoderCell(COL_AI_BG, 12);
        addAiTextFormatted(textCell, aiText, bf);
        aiBox.addCell(textCell);

        doc.add(aiBox);
    }

    // ─────────────────────────────────────────────────────────────
    //  КЛІМАТИЧНІ / ІНФРАСТРУКТУРНІ ТАБЛИЦІ
    // ─────────────────────────────────────────────────────────────

    private void addExtendedDataTables(Document document, JsonObject climate, JsonObject infra,
                                       String dayLength, JsonObject country, File weatherChart,
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
        addStyledCell(climateTable, "Параметр",               hFont, mainColor, true);
        addStyledCell(climateTable, "Середньорічне значення", hFont, mainColor, true);
        if (climate != null) {
            addStyledCell(climateTable, "Річна норма опадів",    nFont, Color.WHITE,  false);
            addStyledCell(climateTable, climate.get("annual_precipitation").getAsString() + " мм", bFont, Color.WHITE, false);
            addStyledCell(climateTable, "Температурний максимум", nFont, COL_ROW_ALT, false);
            addStyledCell(climateTable, climate.get("max_temp").getAsString() + " °C",  bFont, COL_ROW_ALT, false);
            addStyledCell(climateTable, "Температурний мінімум",  nFont, Color.WHITE,  false);
            addStyledCell(climateTable, climate.get("min_temp").getAsString() + " °C",  bFont, Color.WHITE, false);
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

        // 2. Інфраструктурна таблиця (3 колонки: об'єкт | статус | деталі)
        document.add(new Paragraph("2.  Аналіз прилеглої інфраструктури (OSM Overpass):", bFont));
        PdfPTable infraTable = new PdfPTable(3);
        infraTable.setWidthPercentage(100);
        infraTable.setWidths(new int[]{35, 20, 45});
        infraTable.setSpacingBefore(5f);
        infraTable.setSpacingAfter(14f);

        addStyledCell(infraTable, "Об'єкт",  hFont, mainColor, true);
        addStyledCell(infraTable, "Статус",  hFont, mainColor, true);
        addStyledCell(infraTable, "Деталі",  hFont, mainColor, true);

        if (infra != null) {
            boolean power     = safeBoolean(infra, "power_nearby");
            boolean water     = safeBoolean(infra, "water_nearby");
            boolean road      = safeBoolean(infra, "road_nearby");
            boolean cemetery  = safeBoolean(infra, "cemetery_100m");
            boolean industrial= safeBoolean(infra, "industrial_100m");
            boolean station   = safeBoolean(infra, "station_100m");

            Font statusFound   = new Font(bf, 10, Font.BOLD, accentColor);
            Font statusMissing = new Font(bf, 10, Font.BOLD, COL_DANGER);
            Font statusWarn    = new Font(bf, 10, Font.BOLD, COL_WARN);

            // Рядок 1: ЛЕП
            addStyledCell(infraTable, "Лінії електропередач", nFont, Color.WHITE, false);
            addStyledCell(infraTable, power ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО",
                    power ? statusFound : statusMissing, Color.WHITE, true);
            addStyledCell(infraTable, buildInfraDetail(infra, "power", power), nFont, Color.WHITE, false);

            // Рядок 2: Водойми
            addStyledCell(infraTable, "Водні об'єкти",        nFont, COL_ROW_ALT, false);
            addStyledCell(infraTable, water ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО",
                    water ? statusFound : statusMissing, COL_ROW_ALT, true);
            addStyledCell(infraTable, buildWaterDetail(infra, water), nFont, COL_ROW_ALT, false);

            // Рядок 3: Дороги
            addStyledCell(infraTable, "Автодороги",            nFont, Color.WHITE, false);
            addStyledCell(infraTable, road ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО",
                    road ? statusFound : statusMissing, Color.WHITE, true);
            addStyledCell(infraTable, buildInfraDetail(infra, "road", road), nFont, Color.WHITE, false);

            // Рядок 4: Кладовище (100 м)
            addStyledCell(infraTable, "Кладовище (100 м)",    nFont, COL_ROW_ALT, false);
            addStyledCell(infraTable, cemetery ? "ВИЯВЛЕНО" : "ВІДСУТНЄ",
                    cemetery ? statusMissing : statusFound, COL_ROW_ALT, true);
            addStyledCell(infraTable, cemetery ? "кладовище у радіусі 100 м" : "не виявлено у радіусі 100 м",
                    nFont, COL_ROW_ALT, false);

            // Рядок 5: Промислова зона (100 м)
            addStyledCell(infraTable, "Промислова зона (100 м)", nFont, Color.WHITE, false);
            addStyledCell(infraTable, industrial ? "ВИЯВЛЕНО" : "ВІДСУТНЯ",
                    industrial ? statusMissing : statusFound, Color.WHITE, true);
            addStyledCell(infraTable, industrial ? "промзона у радіусі 100 м" : "не виявлено у радіусі 100 м",
                    nFont, Color.WHITE, false);

            // Рядок 6: Станція (100 м)
            addStyledCell(infraTable, "Залізнична станція (100 м)", nFont, COL_ROW_ALT, false);
            addStyledCell(infraTable, station ? "ВИЯВЛЕНО" : "ВІДСУТНЯ",
                    station ? statusWarn : statusFound, COL_ROW_ALT, true);
            addStyledCell(infraTable, station ? "станція у радіусі 100 м" : "не виявлено у радіусі 100 м",
                    nFont, COL_ROW_ALT, false);
        }
        document.add(infraTable);

        // 3. Геофізичні дані
        document.add(new Paragraph("3.  Додаткові геофізичні відомості:", bFont));
        PdfPTable geoTable = new PdfPTable(2);
        geoTable.setWidthPercentage(100);
        geoTable.setSpacingBefore(5f);
        addStyledCell(geoTable, "Тривалість світлового дня", nFont, Color.WHITE,  false);
        addStyledCell(geoTable, dayLength,                   bFont, Color.WHITE,  false);
        if (country != null) {
            addStyledCell(geoTable, "Регіональна юрисдикція",  nFont, COL_ROW_ALT, false);
            addStyledCell(geoTable, country.get("country").getAsString(), bFont, COL_ROW_ALT, false);
        }
        document.add(geoTable);
    }

    // ─────────────────────────────────────────────────────────────
    //  ДОПОМІЖНІ МЕТОДИ
    // ─────────────────────────────────────────────────────────────

    private void addAiTextFormatted(PdfPCell cell, String rawText, BaseFont bf) {
        Color textColor = new Color(44, 62, 80);
        Font normal = new Font(bf, 10, Font.NORMAL, textColor);
        Font bold   = new Font(bf, 10, Font.BOLD,   textColor);
        Font italic = new Font(bf, 10, Font.ITALIC, Color.GRAY);

        if (rawText == null || rawText.isBlank()) {
            cell.addElement(new Paragraph("Дані аналізу відсутні.", italic));
            return;
        }

        for (String raw : rawText.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Заголовки markdown (#, ##, ###)
            if (line.matches("^#{1,3}\\s+.*")) {
                line = stripInlineMarkdown(line.replaceAll("^#{1,3}\\s+", ""));
                Paragraph p = new Paragraph(line, bold);
                p.setLeading(14f);
                p.setSpacingBefore(4f);
                p.setSpacingAfter(0);
                cell.addElement(p);
                continue;
            }

            // Буліти (- або •)
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

            // Нумеровані списки (1. 2. ...)
            if (line.matches("^\\d+\\.\\s+.*")) {
                line = stripInlineMarkdown(line);
                Paragraph p = new Paragraph(line, normal);
                p.setLeading(14f);
                p.setSpacingBefore(0);
                p.setSpacingAfter(0);
                p.setIndentationLeft(10f);
                cell.addElement(p);
                continue;
            }

            // Звичайний текст
            Paragraph p = new Paragraph(stripInlineMarkdown(line), normal);
            p.setLeading(14f);
            p.setSpacingBefore(0);
            p.setSpacingAfter(0);
            cell.addElement(p);
        }
    }

    private String stripInlineMarkdown(String text) {
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")   // **bold**
                .replaceAll("__(.+?)__",           "$1")  // __bold__
                .replaceAll("\\*(.+?)\\*",          "$1") // *italic*
                .replaceAll("_(.+?)_",              "$1") // _italic_
                .replaceAll("`(.+?)`",              "$1") // `code`
                .trim();
    }

    private void addSectionBanner(Document doc, String text, BaseFont bf, Color bg) throws DocumentException {
        addSectionBanner(doc, text, bf, bg, 10);
    }

    private void addSectionBanner(Document doc, String text, BaseFont bf, Color bg, float spacingAfter) throws DocumentException {
        PdfPTable t = singleCellTable(bg, 0, spacingAfter);
        PdfPCell c = t.getRow(0).getCells()[0];
        c.setPaddingLeft(14);
        c.addElement(new Paragraph(text, new Font(bf, 12, Font.BOLD, Color.WHITE)));
        doc.add(t);
    }

    /** Створює таблицю з однією клітинкою без рамки з потрібним фоном. */
    private PdfPTable singleCellTable(Color bg, float spacingBefore, float spacingAfter) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(spacingBefore);
        t.setSpacingAfter(spacingAfter);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(bg);
        c.setPadding(11);
        c.setBorder(Rectangle.NO_BORDER);
        t.addCell(c);
        return t;
    }

    private PdfPCell noBoderCell(Color bg, float padding) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(bg);
        c.setPadding(padding);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private Paragraph label(String text, BaseFont bf) {
        return new Paragraph(text, new Font(bf, 7, Font.NORMAL, Color.GRAY));
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(height);
        p.setSpacingAfter(0);
        return p;
    }

    private void addStyledCell(PdfPTable table, String text, Font font, Color bgColor, boolean center) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(COL_BORDER);
        cell.setBorderWidth(0.5f);
        if (center) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
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
        Paragraph label = new Paragraph(
                "Рис 1.  Об'ємна геодезична модель ділянки (Axonometric Projection)", labelFont);
        label.setAlignment(Element.ALIGN_CENTER);
        label.setSpacingBefore(4f);
        document.add(label);
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
        addStyledCell(coordTable, "№",         hFont, mainColor, true);
        addStyledCell(coordTable, "Latitude",  hFont, mainColor, true);
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

    // ─────────────────────────────────────────────────────────────
    //  ФОРМУВАННЯ ДЕТАЛЕЙ ІНФРАСТРУКТУРИ
    // ─────────────────────────────────────────────────────────────

    private String buildInfraDetail(JsonObject infra, String prefix, boolean found) {
        if (!found) return "не виявлено в радіусі " + getRadius(prefix) + " м";
        if ("power".equals(prefix)) return "виявлено в радіусі " + getRadius(prefix) + " м";
        long dist  = safeLong(infra, prefix + "_distance_m");
        String type = safeStr(infra, prefix + "_type");
        StringBuilder sb = new StringBuilder();
        if (!type.isEmpty()) sb.append(type);
        if (dist > 0) sb.append(sb.length() > 0 ? " · ~" : "~").append(dist).append(" м");
        return sb.length() > 0 ? sb.toString() : "дані присутні";
    }

    private String buildWaterDetail(JsonObject infra, boolean found) {
        if (!found) return "не виявлено в радіусі 1500 м";
        long dist   = safeLong(infra, "water_distance_m");
        String name = safeStr(infra,  "water_name");
        String type = safeStr(infra,  "water_type");
        StringBuilder sb = new StringBuilder();
        if (!name.isEmpty()) sb.append(name);
        if (!type.isEmpty()) sb.append(name.isEmpty() ? type : " (" + type + ")");
        if (dist > 0) sb.append(" · ~").append(dist).append(" м");
        return sb.length() > 0 ? sb.toString() : "дані присутні";
    }

    private String getRadius(String prefix) {
        return switch (prefix) { case "power" -> "500"; case "road" -> "1000"; default -> "1500"; };
    }

    private boolean safeBoolean(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).getAsBoolean();
    }

    private int safeInt(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsInt() : 0;
    }

    private long safeLong(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsLong() : -1;
    }

    private String safeStr(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : "";
    }

    private void ensureDirectoryExists(String filePath) {
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }
}
