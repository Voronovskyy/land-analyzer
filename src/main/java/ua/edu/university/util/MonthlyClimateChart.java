package ua.edu.university.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Генерує річну кліматичну діаграму (Java2D, без FX-потоку).
 * 12 місяців: стовпці опадів + лінії T max / T min.
 */
public class MonthlyClimateChart {
    private static final Logger logger = LoggerFactory.getLogger(MonthlyClimateChart.class);

    private static final int W = 820, H = 195;
    private static final int PAD_L = 44, PAD_R = 50;
    private static final int PAD_T = 24, PAD_B = 32;

    public static File generate(JsonObject monthly, String outputPath) {
        try {
            JsonArray mNames = monthly.getAsJsonArray("months");
            JsonArray tmaxArr = monthly.getAsJsonArray("tmax_avg");
            JsonArray tminArr = monthly.getAsJsonArray("tmin_avg");
            JsonArray precipArr = monthly.getAsJsonArray("precip_sum");

            int n = Math.min(12, mNames.size());
            double[] tmax = new double[n];
            double[] tmin = new double[n];
            double[] precip = new double[n];
            double maxT = -100, minT = 100, maxP = 1;

            for (int i = 0; i < n; i++) {
                tmax[i] = val(tmaxArr, i);
                tmin[i] = val(tminArr, i);
                precip[i] = val(precipArr, i);
                maxT = Math.max(maxT, tmax[i]);
                minT = Math.min(minT, tmin[i]);
                maxP = Math.max(maxP, precip[i]);
            }

            double tAxisMax = Math.ceil((maxT + 3) / 5) * 5;
            double tAxisMin = Math.floor((minT - 3) / 5) * 5;
            double tRange = Math.max(tAxisMax - tAxisMin, 1);
            double precipMax = Math.ceil(maxP / 20) * 20;

            int cW = W - PAD_L - PAD_R;
            int cH = H - PAD_T - PAD_B;
            double colW = (double) cW / n;

            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Background + border
            g.setColor(new Color(248, 249, 250));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(213, 216, 220));
            g.setStroke(new BasicStroke(0.8f));
            g.drawRect(PAD_L, PAD_T, cW, cH);

            // Horizontal grid + left temp labels
            g.setFont(new Font("Arial", Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            for (double t = tAxisMin; t <= tAxisMax + 0.1; t += 5) {
                int y = PAD_T + (int) ((tAxisMax - t) / tRange * cH);
                float[] dash = {4f, 3f};
                g.setColor(new Color(210, 215, 220));
                g.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, dash, 0));
                g.drawLine(PAD_L + 1, y, PAD_L + cW - 1, y);
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(90, 90, 90));
                String lbl = (int) t + "°";
                g.drawString(lbl, PAD_L - fm.stringWidth(lbl) - 3, y + fm.getAscent() / 2);
            }

            // Precipitation bars (semi-transparent blue)
            for (int i = 0; i < n; i++) {
                int barW = Math.max(2, (int) (colW * 0.62));
                int barH = (int) (precip[i] / precipMax * cH);
                int bx = PAD_L + (int) (i * colW + (colW - barW) / 2);
                int by = PAD_T + cH - barH;
                g.setStroke(new BasicStroke(1f));
                g.setColor(new Color(133, 193, 233, 155));
                g.fillRect(bx, by, barW, barH);
                g.setColor(new Color(93, 173, 226, 180));
                g.drawRect(bx, by, barW, barH);
            }

            // T max line (red solid)
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(231, 76, 60));
            drawPolyline(g, tmax, n, colW, cH, tAxisMax, tRange);

            // T min line (blue dashed)
            float[] dashT = {7f, 3f};
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dashT, 0));
            g.setColor(new Color(41, 128, 185));
            drawPolyline(g, tmin, n, colW, cH, tAxisMax, tRange);

            // Dots
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i < n; i++) {
                int cx = PAD_L + (int) ((i + 0.5) * colW);
                int yMax = PAD_T + (int) ((tAxisMax - tmax[i]) / tRange * cH);
                int yMin = PAD_T + (int) ((tAxisMax - tmin[i]) / tRange * cH);
                g.setColor(new Color(231, 76, 60));
                g.fillOval(cx - 3, yMax - 3, 6, 6);
                g.setColor(new Color(41, 128, 185));
                g.fillOval(cx - 3, yMin - 3, 6, 6);
            }

            // Month labels
            g.setFont(new Font("Arial", Font.BOLD, 10));
            fm = g.getFontMetrics();
            g.setColor(new Color(55, 55, 55));
            for (int i = 0; i < n; i++) {
                String m = mNames.get(i).getAsString();
                int x = PAD_L + (int) ((i + 0.5) * colW) - fm.stringWidth(m) / 2;
                g.drawString(m, x, PAD_T + cH + 17);
            }

            // Right axis: precip scale
            g.setFont(new Font("Arial", Font.PLAIN, 9));
            fm = g.getFontMetrics();
            g.setColor(new Color(41, 100, 180));
            for (int pv = 0; pv <= (int) precipMax; pv += 20) {
                int y = PAD_T + cH - (int) ((double) pv / precipMax * cH);
                g.drawString(pv + "мм", PAD_L + cW + 3, y + fm.getAscent() / 2);
            }

            // Legend
            g.setFont(new Font("Arial", Font.BOLD, 10));
            int lx = PAD_L + cW / 2 - 105;
            int ly = 14;
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(new Color(231, 76, 60));
            g.drawLine(lx, ly - 4, lx + 16, ly - 4);
            g.setColor(new Color(55, 55, 55));
            g.drawString("T max", lx + 19, ly);

            float[] dl = {7f, 3f};
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, dl, 0));
            g.setColor(new Color(41, 128, 185));
            g.drawLine(lx + 72, ly - 4, lx + 88, ly - 4);
            g.setColor(new Color(55, 55, 55));
            g.drawString("T min", lx + 91, ly);

            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(133, 193, 233, 200));
            g.fillRect(lx + 145, ly - 10, 12, 10);
            g.setColor(new Color(55, 55, 55));
            g.drawString("Опади", lx + 160, ly);

            g.dispose();

            File out = new File(outputPath);
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            return out;
        } catch (Exception e) {
            logger.error("MonthlyClimateChart error: {}", e.getMessage());
            return null;
        }
    }

    private static void drawPolyline(Graphics2D g, double[] vals, int n, double colW,
                                     int cH, double tAxisMax, double tRange) {
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double x = PAD_L + (i + 0.5) * colW;
            double y = PAD_T + (tAxisMax - vals[i]) / tRange * cH;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        g.draw(path);
    }

    private static double val(JsonArray arr, int i) {
        if (arr == null || i >= arr.size() || arr.get(i).isJsonNull()) return 0;
        return arr.get(i).getAsDouble();
    }
}
