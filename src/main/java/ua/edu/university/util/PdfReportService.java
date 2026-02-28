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

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PdfReportService {
    private static final Logger logger = LoggerFactory.getLogger(PdfReportService.class);
    private static final Color MAIN_COLOR = new Color(44, 62, 80); // Темно-синій
    private static final Color ACCENT_COLOR = new Color(39, 174, 96); // Зелений
    private static final String FONT_PATH = "C:/Windows/Fonts/arial.ttf";

    public void generateReport(String filePath, String title, String area, String priceUah, String priceUsd, File mapScheme, File mapSat) {
        Document document = new Document(PageSize.A4, 40, 40, 50, 40);
        try {
            ensureDirectoryExists(filePath);
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // Ініціалізація шрифтів
            BaseFont bf = BaseFont.createFont(FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font titleFont = new Font(bf, 22, Font.BOLD, MAIN_COLOR);
            Font headerFont = new Font(bf, 12, Font.BOLD, Color.WHITE);
            Font normalFont = new Font(bf, 11, Font.NORMAL);
            Font boldFont = new Font(bf, 11, Font.BOLD);

            // Будуємо документ по частинах
            addHeader(document, bf);
            addTitleSection(document, title, titleFont, normalFont);
            addDataTable(document, area, priceUah, priceUsd, headerFont, normalFont, boldFont);
            addVisualsSection(document, mapScheme, mapSat, headerFont, normalFont, bf);
            addFooter(document, bf);

            logger.info("PDF звіт успішно сформовано за шляхом: {}", filePath);
        } catch (Exception e) {
            logger.error("Критична помилка при генерації PDF: {}", e.getMessage(), e);
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

    private void addDataTable(Document document, String area, String priceUah, String priceUsd, Font headerFont, Font normalFont, Font boldFont) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);

        // Заголовки таблиці
        addStyledCell(table, "ПАРАМЕТР", headerFont, MAIN_COLOR, true);
        addStyledCell(table, "ЗНАЧЕННЯ", headerFont, MAIN_COLOR, true);

        // Дані
        addStyledCell(table, "Загальна площа об'єкта", normalFont, Color.WHITE, false);
        addStyledCell(table, area, boldFont, Color.WHITE, false);

        addStyledCell(table, "Оціночна вартість (UAH)", normalFont, new Color(245, 245, 245), false);
        addStyledCell(table, priceUah, boldFont, new Color(245, 245, 245), false);

        addStyledCell(table, "Вартість за курсом НБУ (USD)", normalFont, Color.WHITE, false);
        addStyledCell(table, priceUsd, new Font(boldFont.getBaseFont(), 11, Font.BOLD, ACCENT_COLOR), Color.WHITE, false);

        document.add(table);
        document.add(new Paragraph(" "));
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
}
