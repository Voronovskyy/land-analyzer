package ua.edu.university.util;

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
    private static final Color MAIN_COLOR = new Color(44, 62, 80); // Темно-синій
    private static final Color ACCENT_COLOR = new Color(39, 174, 96); // Зелений
    private static final String FONT_PATH = "C:/Windows/Fonts/arial.ttf";

    /**
     * ГОЛОВНИЙ МЕТОД ГЕНЕРАЦІЇ ЗВІТУ
     * Тепер приймає Map<String, String> aiAnalyses для вставки текстів від Gemini
     */
    public void generateReport(String filePath, String title, String area,
                               String priceUah, String priceUsd,
                               double elevation, double suitability,
                               List<ua.edu.university.model.Coordinate> boundaries,
                               File mapScheme, File mapTerrain, File mapDem,
                               File mapNdvi, File mapSat,
                               Map<String, String> aiAnalyses) { // Додано параметр аналітики

        Document document = new Document(PageSize.A4, 40, 40, 50, 40);
        try {
            ensureDirectoryExists(filePath);
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // Ініціалізація шрифтів
            BaseFont bf = BaseFont.createFont(FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(bf, 22, Font.BOLD, MAIN_COLOR);
            Font sectionFont = new Font(bf, 16, Font.BOLD, MAIN_COLOR);
            Font boldFont = new Font(bf, 11, Font.BOLD);
            Font normalFont = new Font(bf, 11, Font.NORMAL);
            Font aiTextFont = new Font(bf, 10, Font.NORMAL, new Color(50, 50, 50)); // Шрифт для ШІ

            // --- СТОРІНКА 1: ТИТУЛ ТА ТЕХНІЧНІ ДАНІ ---
            addHeader(document, bf);
            addTitleSection(document, title, titleFont, normalFont);

            document.add(new Paragraph("ОСНОВНІ ХАРАКТЕРИСТИКИ ОБ'ЄКТА", sectionFont));
            document.add(new Paragraph(" "));

            addDataTable(document, area, priceUah, priceUsd, elevation, suitability,
                    new Font(bf, 12, Font.BOLD, Color.WHITE), normalFont, boldFont);

            // --- СЕРІЯ СТОРІНОК З КАРТАМИ ТА АНАЛІЗОМ GEMINI ---

            // 1. ПЛАН-СХЕМА + ІНФРАСТРУКТУРА
            addMapPageWithAiAnalysis(document, "1. ГЕОГРАФІЧНЕ РОЗТАШУВАННЯ ТА ІНФРАСТРУКТУРА",
                    mapScheme, aiAnalyses.getOrDefault("INFRASTRUCTURE", "Аналіз інфраструктури недоступний."),
                    boldFont, aiTextFont);

            // 2. РЕЛЬЄФ + ГЕОМОРФОЛОГІЯ
            addMapPageWithAiAnalysis(document, "2. ТОПОГРАФІЧНИЙ РЕЛЬЄФ ТА МОРФОЛОГІЯ",
                    mapTerrain, aiAnalyses.getOrDefault("TERRAIN", "Аналіз рельєфу недоступний."),
                    boldFont, aiTextFont);

            // 3. МОДЕЛЬ ВИСОТ + ГІДРОЛОГІЯ
            addMapPageWithAiAnalysis(document, "3. ЦИФРОВА МОДЕЛЬ ВИСОТ (DEM ANALYTICS)",
                    mapDem, aiAnalyses.getOrDefault("DEM", "Гідрологічний аналіз недоступний."),
                    boldFont, aiTextFont);

            // 4. ВЕГЕТАЦІЯ + ЕКОЛОГІЯ
            addMapPageWithAiAnalysis(document, "4. СТАН РОСЛИННОСТІ (NDVI MONITORING)",
                    mapNdvi, aiAnalyses.getOrDefault("NDVI", "Екологічний моніторинг недоступний."),
                    boldFont, aiTextFont);

            // 5. СУПУТНИК + РЕТРОСПЕКТИВА
            addMapPageWithAiAnalysis(document, "5. СУПУТНИКОВИЙ МОНІТОРИНГ ТА ДИНАМІКА",
                    mapSat, aiAnalyses.getOrDefault("RETROSPECTIVE", "Ретроспективний аналіз недоступний."),
                    boldFont, aiTextFont);

            // --- СТОРІНКА 7: ТЕХНІЧНИЙ ДОДАТОК (КООРДИНАТИ) ---
            if (boundaries != null && !boundaries.isEmpty()) {
                addCoordinatesTable(document, boundaries, new Font(bf, 12, Font.BOLD, Color.WHITE), normalFont);
            }

            addFooter(document, bf);

        } catch (Exception e) {
            logger.error("Критична помилка генерації PDF", e);
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    /**
     * ДОПОМІЖНИЙ МЕТОД: Сторінка з картою та блоком аналізу ШІ
     */
    private void addMapPageWithAiAnalysis(Document doc, String title, File mapFile,
                                          String aiText, Font titleFont, Font textFont) throws Exception {
        doc.newPage();
        doc.add(new Paragraph(title, titleFont));
        doc.add(new Paragraph(" "));

        // Вставка зображення (скріншота)
        if (mapFile != null && mapFile.exists()) {
            Image img = Image.getInstance(mapFile.getAbsolutePath());
            img.scaleToFit(500, 310);
            img.setAlignment(Element.ALIGN_CENTER);
            img.setBorder(Rectangle.BOX);
            img.setBorderWidth(0.5f);
            img.setBorderColor(Color.GRAY);
            doc.add(img);
        }

        doc.add(new Paragraph(" "));

        // Блок аналітики ШІ (сіра рамка)
        PdfPTable aiBox = new PdfPTable(1);
        aiBox.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        cell.setBackgroundColor(new Color(245, 247, 249));
        cell.setBorderColor(new Color(200, 200, 200));

        // Заголовок блоку ШІ
        Paragraph head = new Paragraph("ЕКСПЕРТНИЙ ВИСНОВОК GEMINI 3 FLASH:",
                new Font(titleFont.getBaseFont(), 9, Font.BOLD, ACCENT_COLOR));
        cell.addElement(head);

        // Текст від ШІ
        Paragraph content = new Paragraph(aiText, textFont);
        content.setAlignment(Element.ALIGN_JUSTIFIED);
        cell.addElement(content);

        aiBox.addCell(cell);
        doc.add(aiBox);
    }

    private void addHeader(Document document, BaseFont bf) throws DocumentException {
        Paragraph header = new Paragraph("LAND PLOT ANALYZER | PhD RESEARCH MODULE", new Font(bf, 9, Font.ITALIC, Color.GRAY));
        header.setAlignment(Element.ALIGN_RIGHT);
        document.add(header);
        document.add(new Paragraph(" "));
    }

    private void addTitleSection(Document document, String title, Font titleFont, Font normalFont) throws DocumentException {
        Paragraph pTitle = new Paragraph("ЕКСПЕРТНИЙ ЗВІТ ПО ОБ'ЄКТУ", titleFont);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(pTitle);

        document.add(new Paragraph(" "));
        document.add(new Paragraph("Адреса/ID: " + title, new Font(titleFont.getBaseFont(), 12, Font.BOLD)));
        document.add(new Paragraph("Сгенеровано: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), normalFont));
        document.add(new LineSeparator(1f, 100, MAIN_COLOR, Element.ALIGN_CENTER, -2));
        document.add(new Paragraph(" "));
    }

    private void addDataTable(Document document, String area, String priceUah, String priceUsd,
                              double elevation, double suitability,
                              Font headerFont, Font normalFont, Font boldFont) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        addStyledCell(table, "ПАРАМЕТР", headerFont, MAIN_COLOR, true);
        addStyledCell(table, "ЗНАЧЕННЯ", headerFont, MAIN_COLOR, true);

        addStyledCell(table, "Загальна площа", normalFont, Color.WHITE, false);
        addStyledCell(table, area, boldFont, Color.WHITE, false);

        addStyledCell(table, "Висота (н.р.м.)", normalFont, new Color(245, 245, 245), false);
        addStyledCell(table, String.format("%.1f м", elevation), boldFont, new Color(245, 245, 245), false);

        addStyledCell(table, "Коефіцієнт придатності", normalFont, Color.WHITE, false);
        addStyledCell(table, String.format("%.0f%%", suitability * 100), new Font(boldFont.getBaseFont(), 11, Font.BOLD, ACCENT_COLOR), Color.WHITE, false);

        addStyledCell(table, "Ціна (UAH)", normalFont, new Color(245, 245, 245), false);
        addStyledCell(table, priceUah, boldFont, new Color(245, 245, 245), false);

        addStyledCell(table, "Ціна (USD)", normalFont, Color.WHITE, false);
        addStyledCell(table, priceUsd, boldFont, Color.WHITE, false);

        document.add(table);
    }

    private void addStyledCell(PdfPTable table, String text, Font font, Color bgColor, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setBackgroundColor(bgColor);
        if (isHeader) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void ensureDirectoryExists(String filePath) {
        File parent = new File(filePath).getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }

    private void addFooter(Document document, BaseFont bf) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new LineSeparator(0.5f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
        Paragraph footer = new Paragraph("Аналіз проведено з використанням супутникових даних Sentinel-2 та моделі Gemini 3 Flash.\n© Лісотехнічний університет, Кафедра комп'ютерних наук.", new Font(bf, 7, Font.NORMAL, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addCoordinatesTable(Document document, List<Coordinate> boundaries, Font headerFont, Font normalFont) throws DocumentException {
        document.newPage();
        document.add(new Paragraph("ГЕОДЕЗИЧНИЙ ДОДАТОК: КООРДИНАТИ МЕЖ (WGS84)", new Font(headerFont.getBaseFont(), 14, Font.BOLD, MAIN_COLOR)));
        document.add(new Paragraph(" "));

        PdfPTable coordTable = new PdfPTable(3);
        coordTable.setWidthPercentage(100);

        addStyledCell(coordTable, "№", headerFont, MAIN_COLOR, true);
        addStyledCell(coordTable, "Latitude", headerFont, MAIN_COLOR, true);
        addStyledCell(coordTable, "Longitude", headerFont, MAIN_COLOR, true);

        int count = 1;
        for (ua.edu.university.model.Coordinate point : boundaries) {
            addStyledCell(coordTable, String.valueOf(count++), normalFont, Color.WHITE, false);
            addStyledCell(coordTable, String.format("%.6f", point.getLatitude()), normalFont, Color.WHITE, false);
            addStyledCell(coordTable, String.format("%.6f", point.getLongitude()), normalFont, Color.WHITE, false);
            if (count > 100) break;
        }
        document.add(coordTable);
    }
}