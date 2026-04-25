package ua.edu.university.util;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Накладає графічні мітки транспортної доступності на скріншот схеми карти.
 * Малює: червону точку (центр ділянки), стрілку у бік найближчої дороги, інфо-блок.
 */
public class InfraAnnotator {
    private static final Logger logger = LoggerFactory.getLogger(InfraAnnotator.class);

    private static final int ARROW_LEN  = 130;
    private static final int ARROW_HEAD = 18;
    private static final int ARROW_BACK = 30;
    private static final int DOT_R      = 10;

    private static final Color COL_ARROW  = new Color(230, 126, 34);
    private static final Color COL_DOT    = new Color(231,  76, 60);
    private static final Color COL_BOX_BG = new Color(  0,   0,  0, 170);

    public static File annotate(File sourceImage, double plotLat, double plotLon,
                                JsonObject infra, String outputPath) {
        try {
            BufferedImage src = ImageIO.read(sourceImage);
            if (src == null) return sourceImage;

            // Convert to ARGB so we can use alpha compositing
            BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int W  = img.getWidth();
            int H  = img.getHeight();
            int cx = W / 2;
            int cy = H / 2;

            boolean hasRoad = infra != null
                    && infra.has("road_nearby")
                    && infra.get("road_nearby").getAsBoolean();
            boolean hasCoords = hasRoad
                    && infra.has("road_lat")
                    && infra.has("road_lon")
                    && infra.get("road_lat").getAsDouble() != 0.0
                    && infra.get("road_lon").getAsDouble() != 0.0;

            // --- Direction arrow ---
            if (hasCoords) {
                double roadLat = infra.get("road_lat").getAsDouble();
                double roadLon = infra.get("road_lon").getAsDouble();
                double dLat    = roadLat - plotLat;
                double dLon    = (roadLon - plotLon) * Math.cos(Math.toRadians(plotLat));
                double bearing = Math.atan2(dLon, dLat);   // from north, clockwise

                int tipX = cx + (int)(Math.sin(bearing) * ARROW_LEN);
                int tipY = cy - (int)(Math.cos(bearing) * ARROW_LEN);

                g.setColor(COL_ARROW);
                g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(cx, cy, tipX, tipY);
                drawArrowhead(g, cx, cy, tipX, tipY);
            }

            // --- Red dot at plot center ---
            g.setColor(COL_DOT);
            g.fillOval(cx - DOT_R, cy - DOT_R, DOT_R * 2, DOT_R * 2);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(cx - DOT_R, cy - DOT_R, DOT_R * 2, DOT_R * 2);

            // --- Info box (bottom-right) ---
            drawInfoBox(g, infra, hasRoad, W, H);

            g.dispose();

            File out = new File(outputPath);
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            return out;

        } catch (Exception e) {
            logger.warn("InfraAnnotator: не вдалося анотувати зображення: {}", e.getMessage());
            return sourceImage;
        }
    }

    private static void drawArrowhead(Graphics2D g, int x1, int y1, int x2, int y2) {
        double dx  = x2 - x1;
        double dy  = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        double ux = dx / len, uy = dy / len;
        double px = -uy,      py = ux;

        int bx = (int)(x2 - ux * ARROW_BACK);
        int by = (int)(y2 - uy * ARROW_BACK);

        int[] xs = { x2, bx + (int)(px * ARROW_HEAD), bx - (int)(px * ARROW_HEAD) };
        int[] ys = { y2, by + (int)(py * ARROW_HEAD), by - (int)(py * ARROW_HEAD) };

        g.setColor(COL_ARROW);
        g.fillPolygon(xs, ys, 3);
    }

    private static void drawInfoBox(Graphics2D g, JsonObject infra, boolean hasRoad, int W, int H) {
        g.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();

        String line1, line2;
        if (!hasRoad || infra == null) {
            line1 = "Автодорога не виявлена";
            line2 = "у радіусі 1000 м";
        } else {
            long   dist = infra.has("road_distance_m") ? infra.get("road_distance_m").getAsLong() : -1;
            String type = infra.has("road_type")       ? infra.get("road_type").getAsString()      : "";
            line1 = dist > 0 ? "Найближча дорога: ~" + dist + " м" : "Найближча дорога: виявлено";
            line2 = type.isEmpty() ? "" : "Тип: " + type;
        }

        int PAD   = 10;
        int lineH = fm.getHeight() + 3;
        int lines = line2.isEmpty() ? 1 : 2;
        int boxW  = Math.max(fm.stringWidth(line1), line2.isEmpty() ? 0 : fm.stringWidth(line2)) + PAD * 2 + 4;
        int boxH  = lineH * lines + PAD * 2;

        int bx = W - boxW - 14;
        int by = H - boxH - 14;

        g.setColor(COL_BOX_BG);
        g.fillRoundRect(bx, by, boxW, boxH, 10, 10);
        g.setColor(new Color(230, 126, 34));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(bx, by, boxW, boxH, 10, 10);

        g.setColor(Color.WHITE);
        g.drawString(line1, bx + PAD, by + PAD + fm.getAscent());
        if (!line2.isEmpty()) {
            g.setColor(new Color(255, 200, 100));
            g.drawString(line2, bx + PAD, by + PAD + fm.getAscent() + lineH);
        }
    }
}
