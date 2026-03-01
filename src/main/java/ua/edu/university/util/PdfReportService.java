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

public class PdfReportService {
    private static final Logger logger = LoggerFactory.getLogger(PdfReportService.class);
    private static final Color MAIN_COLOR = new Color(44, 62, 80); // Темно-синій
    private static final Color ACCENT_COLOR = new Color(39, 174, 96); // Зелений
    private static final String FONT_PATH = "C:/Windows/Fonts/arial.ttf";

    public void generateReport(String filePath, String title, String area,
                               String priceUah, String priceUsd,
                               double elevation, double suitability,
                               List<ua.edu.university.model.Coordinate> boundaries,
                               File mapScheme, File mapTerrain, File mapDem,
                               File mapNdvi, File mapSat) {

        // Встановлюємо формат A4 та стандартні відступи
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
            Font italicFont = new Font(bf, 9, Font.ITALIC, Color.GRAY);

            // --- СТОРІНКА 1: ТИТУЛ ТА ТЕХНІЧНІ ДАНІ ---
            addHeader(document, bf);
            addTitleSection(document, title, titleFont, normalFont);

            document.add(new Paragraph("ОСНОВНІ ХАРАКТЕРИСТИКИ ОБ'ЄКТА", sectionFont));
            document.add(new Paragraph(" ")); // Відступ

            // Таблиця з показниками (Площа, Ціна, Висота, Придатність)
            addDataTable(document, area, priceUah, priceUsd, elevation, suitability,
                    new Font(bf, 12, Font.BOLD, Color.WHITE), normalFont, boldFont);

            // --- СТОРІНКА 2: КАРТА №1 - СХЕМА ---
            document.newPage();
            addMapToDocument(document, "1. ГЕОГРАФІЧНЕ РОЗТАШУВАННЯ ТА МЕЖІ", mapScheme, boldFont);
            document.add(new Paragraph("Візуалізація меж на базі OpenStreetMap. Використовується для ідентифікації під'їзних шляхів та адміністративного положення.", italicFont));

            // --- СТОРІНКА 3: КАРТА №2 - РЕЛЬЄФ ---
            document.newPage();
            addMapToDocument(document, "2. ТОПОГРАФІЧНИЙ РЕЛЬЄФ ТА ГОРИЗОНТАЛІ", mapTerrain, boldFont);
            document.add(new Paragraph("Карта відображає морфологічні особливості поверхні та перепади висот (ізолінії).", italicFont));

            // --- СТОРІНКА 4: КАРТА №3 - МОДЕЛЬ ВИСОТ (DEM) ---
            document.newPage();
            addMapToDocument(document, "3. ЦИФРОВА МОДЕЛЬ ВИСОТ (SRTM DEM)", mapDem, boldFont);
            document.add(new Paragraph("Радарна модель висот, що демонструє вертикальну структуру рельєфу ділянки.", italicFont));

            // --- СТОРІНКА 5: КАРТА №4 - ВЕГЕТАЦІЯ (NDVI) ---
            document.newPage();
            addMapToDocument(document, "4. СТАН РОСЛИННОСТІ (ІНДЕКС NDVI)", mapNdvi, boldFont);
            document.add(new Paragraph("Аналіз здоров'я та щільності рослинного покриву на основі даних Sentinel-2.", italicFont));

            // --- СТОРІНКА 6: КАРТА №5 - СУПУТНИК ---
            document.newPage();
            addMapToDocument(document, "5. АКТУАЛЬНИЙ СУПУТНИКОВИЙ МОНІТОРИНГ", mapSat, boldFont);
            document.add(new Paragraph("Знімок високої роздільної здатності для верифікації фактичного стану землекористування.", italicFont));

            // --- СТОРІНКА 7: ТЕХНІЧНИЙ ДОДАТОК (КООРДИНАТИ) ---
            if (boundaries != null && !boundaries.isEmpty()) {
                document.newPage();
                document.add(new Paragraph("ТЕХНІЧНИЙ ДОДАТОК: ГЕОДЕЗИЧНІ КООРДИНАТИ", sectionFont));
                document.add(new Paragraph(" "));
                addCoordinatesTable(document, boundaries, new Font(bf, 12, Font.BOLD, Color.WHITE), normalFont);
            }

            addFooter(document, bf);

        } catch (Exception e) {
            logger.error("Помилка генерації багатосторінкового PDF", e);
        } finally {
            if (document.isOpen()) document.close();
        }
    }

    private void addHeader(Document document, BaseFont bf) throws DocumentException {
        Paragraph header = new Paragraph("LAND PLOT ANALYZER v1.0 | ЕКСПЕРТНА СИСТЕМА", new Font(bf, 9, Font.ITALIC, Color.GRAY));
        header.setAlignment(Element.ALIGN_RIGHT);
        document.add(header);
        document.add(new Paragraph(" "));
    }

    private void addTitleSection(Document document, String title, Font titleFont, Font normalFont) throws DocumentException {
        Paragraph pTitle = new Paragraph("ЕКСПЕРТНИЙ ЗВІТ", titleFont);
        pTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(pTitle);

        Paragraph subTitle = new Paragraph("Аналіз земельних ресурсів та геопросторова верифікація", normalFont);
        subTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(subTitle);

        document.add(new Paragraph(" "));
        document.add(new Paragraph("Ідентифікатор об'єкта: " + title, new Font(titleFont.getBaseFont(), 12, Font.BOLD)));
        document.add(new Paragraph("Дата звіту: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), normalFont));
        document.add(new LineSeparator(1f, 100, MAIN_COLOR, Element.ALIGN_CENTER, -2));
        document.add(new Paragraph(" "));
    }

    /**
     * Універсальний метод для додавання зображення карти в документ
     */
    private void addMapToDocument(Document document, String title, File mapFile, Font font) throws Exception {
        document.add(new Paragraph(" "));
        document.add(new Paragraph(title, font));
        document.add(new Paragraph(" "));

        if (mapFile != null && mapFile.exists()) {
            Image img = Image.getInstance(mapFile.getAbsolutePath());
            img.scaleToFit(480, 320); // Оптимальний розмір для A4
            img.setAlignment(Element.ALIGN_CENTER);
            img.setBorder(Rectangle.BOX);
            img.setBorderWidth(1f);
            img.setBorderColor(Color.LIGHT_GRAY);
            document.add(img);
        } else {
            document.add(new Paragraph("[Зображення відсутнє або не завантажене]", new Font(font.getBaseFont(), 10, Font.NORMAL, Color.RED)));
        }
    }

    /**
     * Оновлена таблиця даних, що тепер включає висоту та придатність
     */
    private void addDataTable(Document document, String area, String priceUah, String priceUsd,
                              double elevation, double suitability,
                              Font headerFont, Font normalFont, Font boldFont) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(15f);

        // Заголовки
        addStyledCell(table, "ПАРАМЕТР АНАЛІЗУ", headerFont, MAIN_COLOR, true);
        addStyledCell(table, "РЕЗУЛЬТАТ", headerFont, MAIN_COLOR, true);

        // Дані
        addStyledCell(table, "Географічна площа", normalFont, Color.WHITE, false);
        addStyledCell(table, area, boldFont, Color.WHITE, false);

        addStyledCell(table, "Середня висота (н.р.м.)", normalFont, new Color(245, 245, 245), false);
        addStyledCell(table, String.format("%.1f м", elevation), boldFont, new Color(245, 245, 245), false);

        addStyledCell(table, "Індекс придатності (Suitability)", normalFont, Color.WHITE, false);
        String suitText = String.format("%.0f%%", suitability * 100);
        addStyledCell(table, suitText, new Font(boldFont.getBaseFont(), 11, Font.BOLD, ACCENT_COLOR), Color.WHITE, false);

        addStyledCell(table, "Оціночна вартість (UAH)", normalFont, new Color(245, 245, 245), false);
        addStyledCell(table, priceUah, boldFont, new Color(245, 245, 245), false);

        addStyledCell(table, "Вартість за курсом (USD)", normalFont, Color.WHITE, false);
        addStyledCell(table, priceUsd, boldFont, Color.WHITE, false);

        document.add(table);
    }

    private void addVisualsSection(Document document, File mapScheme, File mapSat, Font headerFont, Font normalFont, BaseFont bf) throws DocumentException, java.io.IOException {
        Font sectionFont = new Font(bf, 14, Font.BOLD, MAIN_COLOR);
        document.add(new Paragraph("ВІЗУАЛІЗАЦІЯ ОБ'ЄКТА", sectionFont));
        document.add(new Paragraph(" "));

        if (mapScheme != null && mapScheme.exists()) {
            document.add(new Paragraph("1. Топографічна схема (OSM Layer):", normalFont));
            addImageToDocument(document, mapScheme);
        }

        document.newPage(); // Супутник завжди на новій сторінці для масштабу

        if (mapSat != null && mapSat.exists()) {
            document.add(new Paragraph("2. Супутникова верифікація (Esri Satellite):", normalFont));
            addImageToDocument(document, mapSat);
        }
    }

    private void addImageToDocument(Document document, File imageFile) throws DocumentException, java.io.IOException {
        Image img = Image.getInstance(imageFile.getAbsolutePath());
        img.scaleToFit(500, 300);
        img.setAlignment(Element.ALIGN_CENTER);
        img.setBorder(Rectangle.BOX);
        img.setBorderWidth(1f);
        img.setBorderColor(Color.LIGHT_GRAY);
        document.add(img);
        document.add(new Paragraph(" "));
    }

    private void addFooter(Document document, BaseFont bf) throws DocumentException {
        document.add(new LineSeparator(0.5f, 100, Color.LIGHT_GRAY, Element.ALIGN_CENTER, 0));
        Paragraph footer = new Paragraph("Звіт згенеровано автоматично. Дані про ціну базуються на нормативній оцінці лісових угідь.\n© Forestry Technical University, Computer Science Dept.", new Font(bf, 7, Font.NORMAL, Color.GRAY));
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private void addStyledCell(PdfPTable table, String text, Font font, Color bgColor, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setBackgroundColor(bgColor);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (isHeader) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void ensureDirectoryExists(String filePath) {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private void addCoordinatesTable(Document document, List<Coordinate> boundaries, Font headerFont, Font normalFont) throws DocumentException {
        document.newPage();
        document.add(new Paragraph("ТЕХНІЧНИЙ ДОДАТОК: КООРДИНАТИ МЕЖ", new Font(headerFont.getBaseFont(), 14, Font.BOLD, MAIN_COLOR)));
        document.add(new Paragraph("Перелік поворотних точок меж земельної ділянки (WGS84):", normalFont));
        document.add(new Paragraph(" "));

        PdfPTable coordTable = new PdfPTable(3);
        coordTable.setWidthPercentage(100);

        addStyledCell(coordTable, "№ Точки", headerFont, MAIN_COLOR, true);
        addStyledCell(coordTable, "Широта (Latitude)", headerFont, MAIN_COLOR, true);
        addStyledCell(coordTable, "Довгота (Longitude)", headerFont, MAIN_COLOR, true);

        int count = 1;
        for (ua.edu.university.model.Coordinate point : boundaries) {
            addStyledCell(coordTable, String.valueOf(count++), normalFont, Color.WHITE, false);
            addStyledCell(coordTable, String.format("%.6f", point.getLatitude()), normalFont, Color.WHITE, false);
            addStyledCell(coordTable, String.format("%.6f", point.getLongitude()), normalFont, Color.WHITE, false);

            if (count > 50) break; // Обмежуємо, щоб не було занадто багато сторінок
        }

        document.add(coordTable);
    }
}
