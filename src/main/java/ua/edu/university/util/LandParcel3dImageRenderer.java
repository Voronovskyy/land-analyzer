package ua.edu.university.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Java2D-порт логіки малювання {@link LandParcel3dVisualizer} для середовищ
 * без JavaFX-потоку (веб-бекенд у Docker/Railway). Малює той самий
 * ізометричний вигляд ділянки, що й десктопна версія, але в
 * {@link BufferedImage} — без залежності від Canvas/Platform.runLater.
 * <p>
 * Навмисно не імпортує жоден клас з {@code javafx.*} (навіть опосередковано
 * через {@link LandParcel3dVisualizer}) — сам факт завантаження класу, що
 * посилається на JavaFX-типи, може впасти в headless Docker-образі, де
 * JavaFX graphics jar відсутній або непридатний для рендерингу.
 */
public class LandParcel3dImageRenderer {
    private static final Logger logger = LoggerFactory.getLogger(LandParcel3dImageRenderer.class);

    public record GeoDims(double widthM, double heightM, double perimeterM, double areaM2) {}

    public static File generate(List<Coordinate> boundaries, double elevation,
                                int w, int h, String outputPath) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (boundaries == null || boundaries.size() < 3) {
                drawError(g, w, h);
            } else {
                draw(g, boundaries, elevation, w, h);
            }
            g.dispose();

            File out = new File(outputPath);
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
            return out;
        } catch (Exception e) {
            logger.warn("Не вдалося згенерувати 3D-модель: {}", e.getMessage());
            return null;
        }
    }

    private static void draw(Graphics2D g, List<Coordinate> boundaries, double elevation, int w, int h) {
        // ── Фон ──────────────────────────────────────────────────────────
        g.setPaint(new GradientPaint(0, 0, web("#1a2533"), 0, h, web("#2c3e50")));
        g.fillRect(0, 0, w, h);

        // ── Сітка ────────────────────────────────────────────────────────
        g.setColor(web("#3d5166", 0.5));
        g.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < w; x += 40) g.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += 40) g.drawLine(0, y, w, y);

        // ── Геометрія ────────────────────────────────────────────────────
        GeoDims dims = computeDimensions(boundaries);

        double minLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double maxLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double minLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double maxLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);

        double centerLat = (minLat + maxLat) / 2.0;
        double centerLon = (minLon + maxLon) / 2.0;
        double span = Math.max(maxLat - minLat, maxLon - minLon);
        if (span < 1e-9) span = 1e-9;
        double scale = (w * 0.52) / span;

        int n = boundaries.size();
        double[] xP = new double[n];
        double[] yP = new double[n];

        double topPad = 18;
        double botBar = 38;
        double centerY = topPad + (h - topPad - botBar) * 0.48;

        for (int i = 0; i < n; i++) {
            Coordinate c = boundaries.get(i);
            double dx = (c.getLongitude() - centerLon) * scale;
            double dy = (c.getLatitude() - centerLat) * scale;
            xP[i] = w / 2.0 + (dx - dy) * 0.82;
            yP[i] = centerY - (dx + dy) * 0.40;
        }

        // ── Бічні грані (екструзія) ───────────────────────────────────────
        double depth = Math.min(28, Math.max(14, elevation / 20.0));
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            boolean visible = (yP[i] + yP[next]) / 2.0 > centerY - depth;
            if (!visible) continue;

            double brightness = 0.25 + 0.15 * Math.abs(xP[next] - xP[i]) / (w / 2.0);
            int[] fx = {round(xP[i]), round(xP[next]), round(xP[next]), round(xP[i])};
            int[] fy = {round(yP[i]), round(yP[next]), round(yP[next] + depth), round(yP[i] + depth)};

            g.setColor(web("#1a252f", brightness + 0.4));
            g.fillPolygon(fx, fy, 4);
            g.setColor(web("#0d1b2a", 0.6));
            g.setStroke(new BasicStroke(0.5f));
            g.drawPolygon(fx, fy, 4);
        }

        // ── Верхня поверхня ──────────────────────────────────────────────
        double minY = yP[0], maxY = yP[0];
        for (double y : yP) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        int[] tx = new int[n], ty = new int[n];
        for (int i = 0; i < n; i++) { tx[i] = round(xP[i]); ty[i] = round(yP[i]); }

        g.setPaint(new GradientPaint(0, (float) minY, web("#2ecc71", 0.95), 0, (float) maxY, web("#27ae60", 0.95)));
        g.fillPolygon(tx, ty, n);

        g.setColor(web("#a8f0c6", 0.8));
        g.setStroke(new BasicStroke(1.5f));
        g.drawPolygon(tx, ty, n);

        // ── Внутрішня сітка поверхні ────────────────────────────────────
        g.setColor(web("#ffffff", 0.12));
        g.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3, 3}, 0));
        for (int i = 0; i < n; i++) {
            int opp = (i + n / 2) % n;
            g.drawLine(round(xP[i]), round(yP[i]), round(xP[opp]), round(yP[opp]));
        }

        // ── Кутові точки ─────────────────────────────────────────────────
        g.setColor(Color.WHITE);
        for (int i = 0; i < n; i++) {
            g.fillOval(round(xP[i] - 3), round(yP[i] - 3), 6, 6);
        }

        // ── Розмірні підписи на гранях ────────────────────────────────────
        if (dims.perimeterM() > 0) {
            g.setColor(web("#f0f0f0", 0.85));
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                double segM = haversine(boundaries.get(i), boundaries.get(next));
                if (segM < 1) continue;
                double mx = (xP[i] + xP[next]) / 2.0;
                double my = (yP[i] + yP[next]) / 2.0 - 5;
                g.drawString(String.format("%.0f м", segM), (float) (mx - 10), (float) my);
            }
        }

        drawCompass(g, w - 44, 44);
        drawInfoBar(g, w, h, dims, elevation);
    }

    private static void drawCompass(Graphics2D g, double cx, double cy) {
        double r = 16;
        g.setColor(web("#aabbcc", 0.5));
        g.setStroke(new BasicStroke(1f));
        g.drawOval(round(cx - r), round(cy - r), round(r * 2), round(r * 2));

        double nx = cx, ny = cy - r * 0.75;
        double sx = cx, sy = cy + r * 0.6;

        g.setColor(web("#e74c3c"));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(round(cx), round(cy), round(nx), round(ny));
        g.setColor(web("#aabbcc", 0.6));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(round(cx), round(cy), round(sx), round(sy));

        g.setColor(web("#e74c3c"));
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString("N", (float) (cx - 4), (float) (cy - r * 0.9 + 4));
    }

    private static void drawInfoBar(Graphics2D g, int w, int h, GeoDims dims, double elevation) {
        int barH = 36;
        int y0 = h - barH;

        g.setColor(web("#0d1b2a", 0.85));
        g.fillRect(0, y0, w, barH);
        g.setColor(web("#27ae60", 0.6));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, y0, w, y0);

        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        double col = w / 4.0;
        String[] labels = {
                String.format("ШИРИНА  %.0f м", dims.widthM()),
                String.format("ДОВЖИНА  %.0f м", dims.heightM()),
                String.format("ПЕРИМЕТР  %.0f м", dims.perimeterM()),
                String.format("ВИСОТА  %.1f м", elevation)
        };
        for (int i = 0; i < 4; i++) {
            g.setColor(web("#95a5a6"));
            g.drawString(labels[i], (float) (col * i + 8), y0 + 14);
        }
        if (dims.areaM2() > 0) {
            g.setColor(web("#27ae60"));
            g.setFont(new Font("SansSerif", Font.BOLD, 9));
            g.drawString(String.format("ПЛОЩА  %.4f га", dims.areaM2() / 10000.0), 8, y0 + 28);
        }

        g.setColor(web("#4a6278"));
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        g.drawString("WGS84 · Isometric Projection", w - 145, y0 + 28);
    }

    private static GeoDims computeDimensions(List<Coordinate> pts) {
        if (pts == null || pts.isEmpty()) return new GeoDims(0, 0, 0, 0);
        double minLat = pts.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double maxLat = pts.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double minLon = pts.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double maxLon = pts.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);

        double centerLat = (minLat + maxLat) / 2.0;
        double mPerLat = 111319.9;
        double mPerLon = 111319.9 * Math.cos(Math.toRadians(centerLat));

        double widthM = (maxLon - minLon) * mPerLon;
        double heightM = (maxLat - minLat) * mPerLat;

        double perimeter = 0;
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            perimeter += haversine(pts.get(i), pts.get((i + 1) % n));
        }

        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double xi = pts.get(i).getLongitude() * mPerLon;
            double yi = pts.get(i).getLatitude() * mPerLat;
            double xj = pts.get(j).getLongitude() * mPerLon;
            double yj = pts.get(j).getLatitude() * mPerLat;
            area += xi * yj - xj * yi;
        }
        return new GeoDims(widthM, heightM, perimeter, Math.abs(area) / 2.0);
    }

    private static double haversine(Coordinate a, Coordinate b) {
        double R = 6371000.0;
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }

    private static void drawError(Graphics2D g, int w, int h) {
        g.setColor(web("#1a2533"));
        g.fillRect(0, 0, w, h);
        g.setColor(web("#e74c3c"));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("Недостатньо координат для 3D-моделі", (float) (w * 0.1), h / 2f);
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    private static Color web(String hex) {
        return Color.decode(hex);
    }

    private static Color web(String hex, double alpha) {
        Color base = Color.decode(hex);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), (int) Math.round(alpha * 255));
    }
}
