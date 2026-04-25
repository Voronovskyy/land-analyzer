package ua.edu.university.util;

import com.google.gson.JsonObject;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.*;

/**
 * Статичні фабричні методи та константи стилів для побудови PDF-комірок.
 * Використовується через {@code import static} у {@link PdfReportService}
 * і {@link MapExtrasBuilder}, щоб уникнути дублювання налаштувань шрифтів,
 * відступів і кольорів між сторінками звіту.
 */
public final class PdfCellHelper {

    public static final Color COL_BG_LIGHT = new Color(244, 246, 248);
    public static final Color COL_BORDER = new Color(213, 216, 220);
    public static final Color COL_AI_BG = new Color(235, 245, 251);
    public static final Color COL_ROW_ALT = new Color(245, 245, 245);
    public static final Color COL_WARN = new Color(243, 156, 18);
    public static final Color COL_DANGER = new Color(231, 76, 60);

    private PdfCellHelper() {
    }

    public static PdfPCell noBoderCell(Color bg, float padding) {
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(bg);
        c.setPadding(padding);
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    public static PdfPTable singleCellTable(Color bg, float spacingBefore, float spacingAfter) {
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

    public static void addStyledCell(PdfPTable table, String text, Font font, Color bgColor, boolean center) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(COL_BORDER);
        cell.setBorderWidth(0.5f);
        if (center) cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    public static void addCompact(PdfPTable t, String text, Font f, Color bg, boolean center) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setPadding(7);
        c.setBackgroundColor(bg);
        c.setBorderColor(COL_BORDER);
        c.setBorderWidth(0.5f);
        if (center) c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    public static Paragraph label(String text, BaseFont bf) {
        return new Paragraph(text, new Font(bf, 7, Font.NORMAL, Color.GRAY));
    }

    public static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(height);
        p.setSpacingAfter(0);
        return p;
    }

    public static boolean safeBoolean(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).getAsBoolean();
    }

    public static int safeInt(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsInt() : 0;
    }

    public static long safeLong(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsLong() : -1;
    }

    public static String safeStr(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : "";
    }
}
