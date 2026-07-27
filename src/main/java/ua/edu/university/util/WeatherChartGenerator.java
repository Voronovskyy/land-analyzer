package ua.edu.university.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Генерує JavaFX Canvas з графіком погоди за останні 30 днів:
 * стовпчаста діаграма опадів + лінії мін/макс температури.
 * Повинен викликатися виключно на JavaFX Application Thread.
 * Вхідні дані — відповідь Open-Meteo {@code /forecast} (поле {@code daily}).
 */
public class WeatherChartGenerator {

    private static final Logger logger = LoggerFactory.getLogger(WeatherChartGenerator.class);

    private static final int W = 820;
    private static final int H = 250;
    private static final int PAD_LEFT = 52;
    private static final int PAD_RIGHT = 16;
    private static final int PAD_TOP = 30;
    private static final int PAD_BOTTOM = 44;

    /**
     * Must be called on the JavaFX Application Thread.
     */
    public static Canvas createChart(JsonObject daily) {
        Canvas canvas = new Canvas(W, H);
        if (daily == null) return canvas;

        JsonArray dates = daily.has("time") ? daily.getAsJsonArray("time") : new JsonArray();
        JsonArray maxTemps = daily.has("temperature_2m_max") ? daily.getAsJsonArray("temperature_2m_max") : new JsonArray();
        JsonArray minTemps = daily.has("temperature_2m_min") ? daily.getAsJsonArray("temperature_2m_min") : new JsonArray();
        JsonArray precip = daily.has("precipitation_sum") ? daily.getAsJsonArray("precipitation_sum") : new JsonArray();

        int n = dates.size();
        if (n == 0) return canvas;

        double[] maxT = new double[n], minT = new double[n], rain = new double[n];
        double minVal = Double.MAX_VALUE, maxVal = -Double.MAX_VALUE, maxRain = 0;

        for (int i = 0; i < n; i++) {
            maxT[i] = getValue(maxTemps, i);
            minT[i] = getValue(minTemps, i);
            rain[i] = getValue(precip, i);
            minVal = Math.min(minVal, minT[i]);
            maxVal = Math.max(maxVal, maxT[i]);
            maxRain = Math.max(maxRain, rain[i]);
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        int cW = W - PAD_LEFT - PAD_RIGHT;
        int cH = H - PAD_TOP - PAD_BOTTOM;

        // Background
        gc.setFill(Color.web("#f8f9fa"));
        gc.fillRect(0, 0, W, H);

        // Horizontal grid lines (5 divisions)
        gc.setStroke(Color.web("#dee2e6"));
        gc.setLineWidth(0.6);
        for (int i = 0; i <= 4; i++) {
            double y = PAD_TOP + cH * i / 4.0;
            gc.strokeLine(PAD_LEFT, y, PAD_LEFT + cW, y);
        }

        // Precipitation bars (background, 40% of chart height max)
        double rainScale = maxRain > 0 ? cH * 0.40 / maxRain : 1;
        double barW = Math.max(2, (double) cW / n * 0.55);
        gc.setFill(Color.web("#74b9ff", 0.45));
        for (int i = 0; i < n; i++) {
            double x = PAD_LEFT + (i + 0.5) * cW / n - barW / 2;
            double bH = rain[i] * rainScale;
            gc.fillRect(x, PAD_TOP + cH - bH, barW, bH);
        }

        // Temperature scale with a small margin
        double tRange = maxVal - minVal;
        if (tRange < 1) tRange = 10;
        double margin = tRange * 0.18;
        double tMin = minVal - margin, tMax = maxVal + margin;
        tRange = tMax - tMin;

        // Max-temp line (red)
        drawLine(gc, maxT, n, cW, cH, tMin, tRange, Color.web("#e74c3c"), 2.2);
        // Min-temp line (blue)
        drawLine(gc, minT, n, cW, cH, tMin, tRange, Color.web("#3498db"), 2.2);

        // Axes
        gc.setStroke(Color.web("#495057"));
        gc.setLineWidth(1.2);
        gc.strokeLine(PAD_LEFT, PAD_TOP, PAD_LEFT, PAD_TOP + cH);
        gc.strokeLine(PAD_LEFT, PAD_TOP + cH, PAD_LEFT + cW, PAD_TOP + cH);

        // Y-axis labels (temperature)
        gc.setFill(Color.web("#495057"));
        gc.setFont(Font.font(10));
        gc.setTextAlign(TextAlignment.RIGHT);
        for (int i = 0; i <= 4; i++) {
            double temp = tMin + tRange * (4 - i) / 4.0;
            double y = PAD_TOP + cH * i / 4.0;
            gc.fillText(String.format("%.0f°", temp), PAD_LEFT - 4, y + 4);
        }

        // X-axis date labels (every 5 days)
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(Font.font(9));
        for (int i = 0; i < n; i += 5) {
            String date = dates.get(i).getAsString(); // yyyy-MM-dd
            String label = date.length() >= 10 ? date.substring(5).replace("-", ".") : date;
            double x = PAD_LEFT + (i + 0.5) * cW / n;
            gc.fillText(label, x, H - 8);
        }

        // Legend (top right)
        double lx = W - 220;
        double ly = 9;
        gc.setLineWidth(2.5);
        gc.setStroke(Color.web("#e74c3c"));
        gc.strokeLine(lx, ly + 4, lx + 16, ly + 4);
        gc.setFill(Color.web("#495057"));
        gc.setFont(Font.font(9));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("T max", lx + 20, ly + 8);

        gc.setStroke(Color.web("#3498db"));
        gc.strokeLine(lx + 72, ly + 4, lx + 88, ly + 4);
        gc.fillText("T min", lx + 92, ly + 8);

        gc.setFill(Color.web("#74b9ff", 0.7));
        gc.fillRect(lx + 148, ly, 12, 10);
        gc.setFill(Color.web("#495057"));
        gc.fillText("Опади", lx + 164, ly + 8);

        return canvas;
    }

    private static void drawLine(GraphicsContext gc, double[] vals, int n,
                                 int cW, int cH, double tMin, double tRange,
                                 Color color, double width) {
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.beginPath();
        for (int i = 0; i < n; i++) {
            double x = PAD_LEFT + (i + 0.5) * cW / n;
            double y = PAD_TOP + cH - (vals[i] - tMin) / tRange * cH;
            if (i == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    private static double getValue(JsonArray arr, int i) {
        if (i >= arr.size() || arr.get(i).isJsonNull()) return 0;
        return arr.get(i).getAsDouble();
    }

    // ── Java2D version (no JavaFX thread required) ────────────────────────

    public static File generateFile(JsonObject daily, String path) {
        try {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (daily == null) {
                g.setColor(new java.awt.Color(0xf8, 0xf9, 0xfa));
                g.fillRect(0, 0, W, H);
                g.dispose();
                return writeImg(img, path);
            }

            JsonArray dates = daily.has("time") ? daily.getAsJsonArray("time") : new JsonArray();
            JsonArray maxTemps = daily.has("temperature_2m_max") ? daily.getAsJsonArray("temperature_2m_max") : new JsonArray();
            JsonArray minTemps = daily.has("temperature_2m_min") ? daily.getAsJsonArray("temperature_2m_min") : new JsonArray();
            JsonArray precip = daily.has("precipitation_sum") ? daily.getAsJsonArray("precipitation_sum") : new JsonArray();

            int n = dates.size();
            if (n == 0) {
                g.dispose();
                return writeImg(img, path);
            }

            double[] maxT = new double[n], minT = new double[n], rain = new double[n];
            double minVal = Double.MAX_VALUE, maxVal = -Double.MAX_VALUE, maxRain = 0;
            for (int i = 0; i < n; i++) {
                maxT[i] = getValue(maxTemps, i);
                minT[i] = getValue(minTemps, i);
                rain[i] = getValue(precip, i);
                minVal = Math.min(minVal, minT[i]);
                maxVal = Math.max(maxVal, maxT[i]);
                maxRain = Math.max(maxRain, rain[i]);
            }

            int cW = W - PAD_LEFT - PAD_RIGHT;
            int cH = H - PAD_TOP - PAD_BOTTOM;

            g.setColor(new java.awt.Color(0xf8, 0xf9, 0xfa));
            g.fillRect(0, 0, W, H);

            g.setColor(new java.awt.Color(0xde, 0xe2, 0xe6));
            g.setStroke(new BasicStroke(0.6f));
            for (int i = 0; i <= 4; i++) {
                int y = (int) (PAD_TOP + cH * i / 4.0);
                g.drawLine(PAD_LEFT, y, PAD_LEFT + cW, y);
            }

            double rainScale = maxRain > 0 ? cH * 0.40 / maxRain : 1;
            double barW = Math.max(2, (double) cW / n * 0.55);
            g.setColor(new java.awt.Color(0x74, 0xb9, 0xff, (int) (0.45 * 255)));
            for (int i = 0; i < n; i++) {
                double x = PAD_LEFT + (i + 0.5) * cW / n - barW / 2;
                double bH = rain[i] * rainScale;
                g.fill(new Rectangle2D.Double(x, PAD_TOP + cH - bH, barW, bH));
            }

            double tRange = maxVal - minVal;
            if (tRange < 1) tRange = 10;
            double margin = tRange * 0.18;
            double tMin = minVal - margin, tMax = maxVal + margin;
            tRange = tMax - tMin;

            drawLine2D(g, maxT, n, cW, cH, tMin, tRange, new java.awt.Color(0xe7, 0x4c, 0x3c), 2.2f);
            drawLine2D(g, minT, n, cW, cH, tMin, tRange, new java.awt.Color(0x34, 0x98, 0xdb), 2.2f);

            g.setColor(new java.awt.Color(0x49, 0x50, 0x57));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(PAD_LEFT, PAD_TOP, PAD_LEFT, PAD_TOP + cH);
            g.drawLine(PAD_LEFT, PAD_TOP + cH, PAD_LEFT + cW, PAD_TOP + cH);

            g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new java.awt.Color(0x49, 0x50, 0x57));
            for (int i = 0; i <= 4; i++) {
                double temp = tMin + tRange * (4 - i) / 4.0;
                int y = (int) (PAD_TOP + cH * i / 4.0);
                String label = String.format("%.0f°", temp);
                g.drawString(label, PAD_LEFT - 4 - fm.stringWidth(label), y + fm.getAscent() / 2);
            }

            g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 9));
            fm = g.getFontMetrics();
            for (int i = 0; i < n; i += 5) {
                String date = dates.get(i).getAsString();
                String label = date.length() >= 10 ? date.substring(5).replace("-", ".") : date;
                double x = PAD_LEFT + (i + 0.5) * cW / n;
                g.drawString(label, (int) (x - fm.stringWidth(label) / 2.0), H - 8);
            }

            int lx = W - 220;
            int ly = 9;
            g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 9));
            fm = g.getFontMetrics();
            g.setColor(new java.awt.Color(0xe7, 0x4c, 0x3c));
            g.setStroke(new BasicStroke(2.5f));
            g.drawLine(lx, ly + 4, lx + 16, ly + 4);
            g.setColor(new java.awt.Color(0x49, 0x50, 0x57));
            g.drawString("T max", lx + 20, ly + 8);

            g.setColor(new java.awt.Color(0x34, 0x98, 0xdb));
            g.setStroke(new BasicStroke(2.5f));
            g.drawLine(lx + 72, ly + 4, lx + 88, ly + 4);
            g.setColor(new java.awt.Color(0x49, 0x50, 0x57));
            g.drawString("T min", lx + 92, ly + 8);

            g.setColor(new java.awt.Color(0x74, 0xb9, 0xff, (int) (0.7 * 255)));
            g.fillRect(lx + 148, ly, 12, 10);
            g.setColor(new java.awt.Color(0x49, 0x50, 0x57));
            g.drawString("Опади", lx + 164, ly + 8);

            g.dispose();
            return writeImg(img, path);
        } catch (Exception e) {
            logger.error("WeatherChartGenerator.generateFile error: {}", e.getMessage());
            return null;
        }
    }

    private static File writeImg(BufferedImage img, String path) throws Exception {
        File file = new File(path);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
        return file;
    }

    private static void drawLine2D(Graphics2D g, double[] vals, int n,
                                   int cW, int cH, double tMin, double tRange,
                                   java.awt.Color color, float width) {
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double x = PAD_LEFT + (i + 0.5) * cW / n;
            double y = PAD_TOP + cH - (vals[i] - tMin) / tRange * cH;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        g.draw(path);
    }
}
