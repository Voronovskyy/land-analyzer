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
    private static final String FOOTER = "Аналіз проведено з використанням супутникових даних Sentinel-2, Open-Meteo та моделі Gemini.\n" +
            "© Лісотехнічний університет, Кафедра комп'ютерних наук.";

    /**
     * РОЗШИРЕНИЙ МЕТОД ГЕНЕРАЦІЇ ЗВІТУ
     * Додає нові дані з Кліматичного, Інфраструктурного та Регіонального API.
     */
    public void generateReportExtended(String filePath, String title, String area,
                                       String priceUah, String priceUsd,
                                       double elevation, double suitability,
                                       List<Coordinate> boundaries,
                                       File mapScheme, File mapTerrain, File mapDem,
                                       File mapNdvi, File mapSat, File map3d,
                                       Map<String, String> aiAnalyses,
                                       JsonObject climate, JsonObject infra,
                                       String dayLength, JsonObject country) {

        Document document = new Document(PageSize.A4, 40, 40, 50, 40);

        try {
            ensureDirectoryExists(filePath);
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            BaseFont bf = createBaseFont();
            Color mainColor = Color.decode(ConfigManager.getProperty("pdf.color.main"));
            Color accentColor = Color.decode(ConfigManager.getProperty("pdf.color.accent"));

            Font titleFont = new Font(bf, 22, Font.BOLD, mainColor);
            Font sectionFont = new Font(bf, 16, Font.BOLD, mainColor);
            Font boldFont = new Font(bf, 11, Font.BOLD);
            Font normalFont = new Font(bf, 11, Font.NORMAL);
            Font aiTextFont = new Font(bf, 10, Font.NORMAL, new Color(50, 50, 50));
            Font italicFont = new Font(bf, 8, Font.ITALIC, Color.GRAY);

            // --- СТОРІНКА 1: ВСТУП ---
            addHeader(document, bf);
            addTitleSection(document, title, titleFont, normalFont, mainColor);
            document.add(new Paragraph("ОСНОВНІ ХАРАКТЕРИСТИКИ ОБ'ЄКТА", sectionFont));
            document.add(new Paragraph(" "));
            addDataTable(document, area, priceUah, priceUsd, elevation, suitability, mainColor, accentColor, bf);

            if (map3d != null && map3d.exists()) {
                addGraphicModel(document, map3d, italicFont);
            }

            // --- НОВА СТОРІНКА: КЛІМАТ ТА ТЕХНІЧНІ МЕРЕЖІ ---
            document.newPage();
            document.add(new Paragraph("КЛІМАТИЧНИЙ ТА ТЕХНІКО-ГЕОГРАФІЧНИЙ ПАСПОРТ", sectionFont));
            document.add(new Paragraph(" "));
            addExtendedDataTables(document, climate, infra, dayLength, country, bf, mainColor, accentColor);

            // --- СЕРІЯ КАРТОГРАФІЧНИХ СТОРІНОК ---
            addMapPage(document, "1. ГЕОГРАФІЧНЕ РОЗТАШУВАННЯ ТА ІНФРАСТРУКТУРА", mapScheme, aiAnalyses.get("INFRASTRUCTURE"), boldFont, aiTextFont, accentColor);
            addMapPage(document, "2. ТОПОГРАФІЧНИЙ РЕЛЬЄФ ТА МОРФОЛОГІЯ", mapTerrain, aiAnalyses.get("TERRAIN"), boldFont, aiTextFont, accentColor);
            addMapPage(document, "3. ЦИФРОВА МОДЕЛЬ ВИСОТ (DEM ANALYTICS)", mapDem, aiAnalyses.get("DEM"), boldFont, aiTextFont, accentColor);
            addMapPage(document, "4. СТАН РОСЛИННОСТІ (NDVI MONITORING)", mapNdvi, aiAnalyses.get("NDVI"), boldFont, aiTextFont, accentColor);
            addMapPage(document, "5. СУПУТНИКОВИЙ МОНІТОРИНГ ТА ДИНАМІКА", mapSat, aiAnalyses.get("RETROSPECTIVE"), boldFont, aiTextFont, accentColor);

            // --- ДОДАТОК ---
            if (boundaries != null && !boundaries.isEmpty()) {
                addCoordinatesTable(document, boundaries, bf, mainColor);
            }

            addFooter(document, bf);
            logger.info("Розширений PDF звіт успішно сформовано: {}", filePath);

        } catch (Exception e) {
            logger.error("Помилка генерації розширеного PDF: {}", e.getMessage());
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    /**
     * Додає таблиці з даними Open-Meteo та Overpass API.
     */
    private void addExtendedDataTables(Document document, JsonObject climate, JsonObject infra,
                                       String dayLength, JsonObject country,
                                       BaseFont bf, Color mainColor, Color accentColor) throws DocumentException {

        Font hFont = new Font(bf, 11, Font.BOLD, Color.WHITE);
        Font nFont = new Font(bf, 10, Font.NORMAL);
        Font bFont = new Font(bf, 10, Font.BOLD);

        // 1. Кліматична таблиця
        document.add(new Paragraph("1. Метеорологічні показники (за минулий рік):", bFont));
        PdfPTable climateTable = new PdfPTable(2);
        climateTable.setWidthPercentage(100);
        climateTable.setSpacingBefore(5f);
        climateTable.setSpacingAfter(15f);

        addStyledCell(climateTable, "Параметр", hFont, mainColor, true);
        addStyledCell(climateTable, "Середньорічне значення", hFont, mainColor, true);

        if (climate != null) {
            addStyledCell(climateTable, "Річна норма опадів", nFont, Color.WHITE, false);
            addStyledCell(climateTable, climate.get("annual_precipitation").getAsString() + " мм", bFont, Color.WHITE, false);
            addStyledCell(climateTable, "Температурний максимум", nFont, new Color(245, 245, 245), false);
            addStyledCell(climateTable, climate.get("max_temp").getAsString() + " °C", bFont, new Color(245, 245, 245), false);
            addStyledCell(climateTable, "Температурний мінімум", nFont, Color.WHITE, false);
            addStyledCell(climateTable, climate.get("min_temp").getAsString() + " °C", bFont, Color.WHITE, false);
        }
        document.add(climateTable);

        // 2. Інфраструктурна таблиця (Overpass)
        document.add(new Paragraph("2. Аналіз інженерних мереж (радіус 1000м):", bFont));
        PdfPTable infraTable = new PdfPTable(2);
        infraTable.setWidthPercentage(100);
        infraTable.setSpacingBefore(5f);
        infraTable.setSpacingAfter(15f);

        addStyledCell(infraTable, "Мережа / Об'єкт", hFont, mainColor, true);
        addStyledCell(infraTable, "Статус наявності", hFont, mainColor, true);

        if (infra != null) {
            addStyledCell(infraTable, "Електромережі (Power Lines)", nFont, Color.WHITE, false);
            addStyledCell(infraTable, infra.get("power_nearby").getAsBoolean() ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО", bFont, Color.WHITE, false);
            addStyledCell(infraTable, "Водні артерії (Waterways)", nFont, new Color(245, 245, 245), false);
            addStyledCell(infraTable, infra.get("water_nearby").getAsBoolean() ? "ВИЯВЛЕНО" : "НЕ ЗНАЙДЕНО", bFont, new Color(245, 245, 245), false);
        }
        document.add(infraTable);

        // 3. Геофізичні дані
        document.add(new Paragraph("3. Додаткові геофізичні відомості:", bFont));
        PdfPTable geoTable = new PdfPTable(2);
        geoTable.setWidthPercentage(100);
        geoTable.setSpacingBefore(5f);

        addStyledCell(geoTable, "Тривалість світлового дня", nFont, Color.WHITE, false);
        addStyledCell(geoTable, dayLength, bFont, Color.WHITE, false);

        if (country != null) {
            addStyledCell(geoTable, "Регіональна юрисдикція", nFont, new Color(245, 245, 245), false);
            addStyledCell(geoTable, country.get("country").getAsString(), bFont, new Color(245, 245, 245), false);
        }
        document.add(geoTable);
    }

    // --- Решта методів (addHeader, addMapPage, addFooter тощо) залишаються ідентичними ---
    // (Я їх опускаю для стислості, але в коді вони мають бути)

    private BaseFont createBaseFont() throws Exception {
        return BaseFont.createFont(ConfigManager.getProperty("pdf.font.path"), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
    }

    private void addHeader(Document document, BaseFont bf) throws DocumentException {
        String headerText = ConfigManager.getProperty("pdf.report.header");
        Paragraph header = new Paragraph(headerText, new Font(bf, 9, Font.ITALIC, Color.GRAY));
        header.setAlignment(Element.ALIGN_RIGHT);
        document.add(header);
    }

    private void addTitleSection(Document document, String title, Font titleFont, Font normalFont, Color mainColor) throws DocumentException {
        Paragraph pTitle = new Paragraph("ЕКСПЕРТНИЙ ЗВІТ ПО ОБ'ЄКТУ", titleFont);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(pTitle);
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Об'єкт: " + title, new Font(titleFont.getBaseFont(), 12, Font.BOLD)));
        document.add(new Paragraph("Дата: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), normalFont));
        document.add(new LineSeparator(1f, 100, mainColor, Element.ALIGN_CENTER, -2));
    }

    private void addDataTable(Document document, String area, String priceUah, String priceUsd,
                              double elevation, double suitability, Color mainColor, Color accentColor, BaseFont bf) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);

        Font headerFont = new Font(bf, 12, Font.BOLD, Color.WHITE);
        Font boldFont = new Font(bf, 11, Font.BOLD);
        Font normalFont = new Font(bf, 11, Font.NORMAL);

        addStyledCell(table, "ПАРАМЕТР", headerFont, mainColor, true);
        addStyledCell(table, "ЗНАЧЕННЯ", headerFont, mainColor, true);

        addStyledCell(table, "Загальна площа", normalFont, Color.WHITE, false);
        addStyledCell(table, area, boldFont, Color.WHITE, false);
        addStyledCell(table, "Висота (н.р.м.)", normalFont, new Color(245, 245, 245), false);
        addStyledCell(table, String.format("%.1f м", elevation), boldFont, new Color(245, 245, 245), false);
        addStyledCell(table, "Коефіцієнт придатності", normalFont, Color.WHITE, false);
        addStyledCell(table, String.format("%.0f%%", suitability * 100), new Font(bf, 11, Font.BOLD, accentColor), Color.WHITE, false);

        document.add(table);
    }

    private void addMapPage(Document doc, String title, File mapFile, String aiText, Font titleFont, Font textFont, Color accentColor) throws Exception {
        doc.newPage();
        doc.add(new Paragraph(title, titleFont));
        doc.add(new Paragraph(" "));
        if (mapFile != null && mapFile.exists()) {
            Image img = Image.getInstance(mapFile.getAbsolutePath());
            img.scaleToFit(500, 310);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setBorder(Rectangle.BOX);
            img.setBorderWidth(0.5f);
            doc.add(img);
        }
        PdfPTable aiBox = new PdfPTable(1);
        aiBox.setWidthPercentage(100);
        aiBox.setSpacingBefore(10f);
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        cell.setBackgroundColor(Color.decode(ConfigManager.getProperty("pdf.color.background")));
        cell.addElement(new Paragraph("ЕКСПЕРТНИЙ ВИСНОВОК GEMINI:", new Font(titleFont.getBaseFont(), 9, Font.BOLD, accentColor)));
        cell.addElement(new Paragraph(aiText != null ? aiText : "Дані аналізу відсутні.", textFont));
        aiBox.addCell(cell);
        doc.add(aiBox);
    }

    private void addGraphicModel(Document document, File map3d, Font labelFont) throws Exception {
        Image img = Image.getInstance(map3d.getAbsolutePath());
        img.scaleToFit(400, 200);
        img.setAlignment(Element.ALIGN_CENTER);
        document.add(new Paragraph("\n"));
        document.add(img);
        Paragraph label = new Paragraph("Рис 1. Об'ємна геодезична модель ділянки (Axonometric Projection)", labelFont);
        label.setAlignment(Element.ALIGN_CENTER);
        document.add(label);
    }

    private void addStyledCell(PdfPTable table, String text, Font font, Color bgColor, boolean center) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setBackgroundColor(bgColor);
        if (center) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addFooter(Document document, BaseFont bf) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new LineSeparator(0.5f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
        Paragraph footer = new Paragraph(FOOTER, new Font(bf, 7, Font.NORMAL, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addCoordinatesTable(Document document, List<Coordinate> boundaries, BaseFont bf, Color mainColor) throws DocumentException {
        document.newPage();
        document.add(new Paragraph("ГЕОДЕЗИЧНИЙ ДОДАТОК: КООРДИНАТИ МЕЖ (WGS84)", new Font(bf, 14, Font.BOLD, mainColor)));
        document.add(new Paragraph(" "));
        PdfPTable coordTable = new PdfPTable(3);
        coordTable.setWidthPercentage(100);
        Font hFont = new Font(bf, 11, Font.BOLD, Color.WHITE);
        addStyledCell(coordTable, "№", hFont, mainColor, true);
        addStyledCell(coordTable, "Latitude", hFont, mainColor, true);
        addStyledCell(coordTable, "Longitude", hFont, mainColor, true);
        int count = 1;
        Font nFont = new Font(bf, 10, Font.NORMAL);
        for (Coordinate point : boundaries) {
            addStyledCell(coordTable, String.valueOf(count++), nFont, Color.WHITE, false);
            addStyledCell(coordTable, String.format("%.6f", point.getLatitude()), nFont, Color.WHITE, false);
            addStyledCell(coordTable, String.format("%.6f", point.getLongitude()), nFont, Color.WHITE, false);
            if (count > 50) break;
        }
        document.add(coordTable);
    }

    private void ensureDirectoryExists(String filePath) {
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }
}