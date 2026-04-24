package ua.edu.university.util;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import ua.edu.university.model.Coordinate;

import java.util.List;

/**
 * Ізометричний 3D-візуалізатор земельної ділянки.
 * create3dPlot() — для PDF (фіксований кут).
 * draw()         — для інтерактивного вікна (довільний кут обертання).
 */
public class LandParcel3dVisualizer {

    // ─── Публічне API ────────────────────────────────────────────────────

    /** Створює Canvas для PDF (кут = 0). */
    public static Canvas create3dPlot(List<Coordinate> boundaries, double elevation,
                                      double w, double h) {
        Canvas canvas = new Canvas(w, h);
        if (boundaries == null || boundaries.size() < 3) {
            drawError(canvas.getGraphicsContext2D(), w, h);
            return canvas;
        }
        draw(canvas.getGraphicsContext2D(), boundaries, elevation, w, h, 0.0);
        return canvas;
    }

    /**
     * Основний метод малювання — використовується як у PDF, так і в інтерактивному вікні.
     * @param rotAngle  кут обертання навколо вертикальної осі (радіани)
     */
    public static void draw(GraphicsContext gc, List<Coordinate> boundaries, double elevation,
                            double w, double h, double rotAngle) {
        if (boundaries == null || boundaries.size() < 3) {
            drawError(gc, w, h);
            return;
        }

        gc.clearRect(0, 0, w, h);

        // ── Фон ──────────────────────────────────────────────────────────
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#1a2533")),
                new Stop(1, Color.web("#2c3e50"))));
        gc.fillRect(0, 0, w, h);

        // ── Сітка ────────────────────────────────────────────────────────
        drawGrid(gc, w, h);

        // ── Геометрія ────────────────────────────────────────────────────
        GeoDims dims = computeDimensions(boundaries);

        double minLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double maxLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double minLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double maxLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);

        double centerLat = (minLat + maxLat) / 2.0;
        double centerLon = (minLon + maxLon) / 2.0;
        double span      = Math.max(maxLat - minLat, maxLon - minLon);
        if (span < 1e-9) span = 1e-9;
        double scale = (w * 0.52) / span;

        // Проекція з обертанням
        int n = boundaries.size();
        double[] xP = new double[n];
        double[] yP = new double[n];
        double cos = Math.cos(rotAngle);
        double sin = Math.sin(rotAngle);

        // Верхня частина панелі + нижній бар
        double topPad  = 18;
        double botBar  = 38;
        double centerY = topPad + (h - topPad - botBar) * 0.48;

        for (int i = 0; i < n; i++) {
            Coordinate c = boundaries.get(i);
            double dx = (c.getLongitude() - centerLon) * scale;
            double dy = (c.getLatitude()  - centerLat) * scale;
            double rdx =  dx * cos - dy * sin;
            double rdy =  dx * sin + dy * cos;
            xP[i] = w / 2.0 + (rdx - rdy) * 0.82;
            yP[i] = centerY  - (rdx + rdy) * 0.40;
        }

        // ── Бічні грані (екструзія = висота ∈ [14,28]) ───────────────────
        double depth = Math.min(28, Math.max(14, elevation / 20.0));
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            double avgX = (xP[i] + xP[next]) / 2.0;
            // Тільки "видимі" грані (спрощена перевірка: нижня половина полігону)
            boolean visible = (yP[i] + yP[next]) / 2.0 > centerY - depth;
            if (!visible) continue;

            // Тінь грані залежить від напрямку
            double brightness = 0.25 + 0.15 * Math.abs(xP[next] - xP[i]) / (w / 2.0);
            gc.setFill(Color.web("#1a252f", brightness + 0.4));
            gc.fillPolygon(
                    new double[]{xP[i], xP[next], xP[next], xP[i]},
                    new double[]{yP[i], yP[next], yP[next] + depth, yP[i] + depth}, 4);
            gc.setStroke(Color.web("#0d1b2a", 0.6));
            gc.setLineWidth(0.5);
            gc.strokePolygon(
                    new double[]{xP[i], xP[next], xP[next], xP[i]},
                    new double[]{yP[i], yP[next], yP[next] + depth, yP[i] + depth}, 4);
        }

        // ── Верхня поверхня ───────────────────────────────────────────────
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2ecc71", 0.95)),
                new Stop(1, Color.web("#27ae60", 0.95))));
        gc.fillPolygon(xP, yP, n);

        // Glow-контур
        gc.setStroke(Color.web("#a8f0c6", 0.8));
        gc.setLineWidth(1.5);
        gc.strokePolygon(xP, yP, n);

        // ── Внутрішня сітка поверхні ──────────────────────────────────────
        gc.setStroke(Color.web("#ffffff", 0.12));
        gc.setLineWidth(0.6);
        gc.setLineDashes(3, 3);
        for (int i = 0; i < n; i++) {
            int opp = (i + n / 2) % n;
            gc.strokeLine(xP[i], yP[i], xP[opp], yP[opp]);
        }
        gc.setLineDashes();

        // ── Кутові точки ─────────────────────────────────────────────────
        gc.setFill(Color.WHITE);
        for (int i = 0; i < n; i++) {
            gc.fillOval(xP[i] - 3, yP[i] - 3, 6, 6);
        }

        // ── Розмірні підписи на гранях ────────────────────────────────────
        if (dims.perimeterM > 0) {
            gc.setFill(Color.web("#f0f0f0", 0.85));
            gc.setFont(Font.font("Arial", FontWeight.NORMAL, 9));
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                // Відстань між вершинами в метрах
                double segM = haversine(boundaries.get(i), boundaries.get(next));
                if (segM < 1) continue;
                double mx = (xP[i] + xP[next]) / 2.0;
                double my = (yP[i] + yP[next]) / 2.0 - 5;
                gc.fillText(String.format("%.0f м", segM), mx - 10, my);
            }
        }

        // ── Компас ───────────────────────────────────────────────────────
        drawCompass(gc, w - 44, 44, rotAngle);

        // ── Нижня інфо-панель ─────────────────────────────────────────────
        drawInfoBar(gc, w, h, dims, elevation);
    }

    // ─── Фонова сітка ───────────────────────────────────────────────────

    private static void drawGrid(GraphicsContext gc, double w, double h) {
        gc.setStroke(Color.web("#3d5166", 0.5));
        gc.setLineWidth(0.5);
        double step = 40;
        for (double x = 0; x < w; x += step) gc.strokeLine(x, 0, x, h);
        for (double y = 0; y < h; y += step) gc.strokeLine(0, y, w, y);
    }

    // ─── Компас (обертається разом з моделлю) ────────────────────────────

    private static void drawCompass(GraphicsContext gc, double cx, double cy, double rotAngle) {
        double r = 16;
        // Коло
        gc.setStroke(Color.web("#aabbcc", 0.5));
        gc.setLineWidth(1);
        gc.strokeOval(cx - r, cy - r, r * 2, r * 2);

        // Стрілка Північ (обертається)
        double nx = cx + r * 0.75 * Math.sin(-rotAngle);
        double ny = cy - r * 0.75 * Math.cos(-rotAngle);
        double sx = cx - r * 0.6 * Math.sin(-rotAngle);
        double sy = cy + r * 0.6 * Math.cos(-rotAngle);

        gc.setStroke(Color.web("#e74c3c"));
        gc.setLineWidth(2);
        gc.strokeLine(cx, cy, nx, ny);
        gc.setStroke(Color.web("#aabbcc", 0.6));
        gc.setLineWidth(1);
        gc.strokeLine(cx, cy, sx, sy);

        // Літера N
        gc.setFill(Color.web("#e74c3c"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        gc.fillText("N", cx + r * 0.9 * Math.sin(-rotAngle) - 4,
                cy - r * 0.9 * Math.cos(-rotAngle) + 4);
    }

    // ─── Нижня інфо-панель ───────────────────────────────────────────────

    private static void drawInfoBar(GraphicsContext gc, double w, double h,
                                    GeoDims dims, double elevation) {
        double barH = 36;
        double y0   = h - barH;

        gc.setFill(Color.web("#0d1b2a", 0.85));
        gc.fillRect(0, y0, w, barH);
        gc.setStroke(Color.web("#27ae60", 0.6));
        gc.setLineWidth(1);
        gc.strokeLine(0, y0, w, y0);

        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 9));
        double col = w / 4.0;
        String[] labels = {
            String.format("ШИРИНА  %.0f м",     dims.widthM),
            String.format("ДОВЖИНА  %.0f м",    dims.heightM),
            String.format("ПЕРИМЕТР  %.0f м",   dims.perimeterM),
            String.format("ВИСОТА  %.1f м",      elevation)
        };
        for (int i = 0; i < 4; i++) {
            gc.setFill(Color.web("#95a5a6"));
            gc.fillText(labels[i], col * i + 8, y0 + 14);
        }
        if (dims.areaM2 > 0) {
            gc.setFill(Color.web("#27ae60"));
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            gc.fillText(String.format("ПЛОЩА  %.4f га", dims.areaM2 / 10000.0), 8, y0 + 28);
        }

        gc.setFill(Color.web("#4a6278"));
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 8));
        gc.fillText("WGS84 · Isometric Projection", w - 145, y0 + 28);
    }

    // ─── Геометричні розрахунки ──────────────────────────────────────────

    public static GeoDims computeDimensions(List<Coordinate> pts) {
        if (pts == null || pts.isEmpty()) return new GeoDims(0, 0, 0, 0);
        double minLat = pts.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double maxLat = pts.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double minLon = pts.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double maxLon = pts.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);

        double centerLat    = (minLat + maxLat) / 2.0;
        double mPerLat      = 111319.9;
        double mPerLon      = 111319.9 * Math.cos(Math.toRadians(centerLat));

        double widthM       = (maxLon - minLon) * mPerLon;
        double heightM      = (maxLat - minLat) * mPerLat;

        double perimeter = 0;
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            perimeter += haversine(pts.get(i), pts.get((i + 1) % n));
        }

        // Shoelace у метричних координатах
        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double xi = pts.get(i).getLongitude() * mPerLon;
            double yi = pts.get(i).getLatitude()  * mPerLat;
            double xj = pts.get(j).getLongitude() * mPerLon;
            double yj = pts.get(j).getLatitude()  * mPerLat;
            area += xi * yj - xj * yi;
        }
        return new GeoDims(widthM, heightM, perimeter, Math.abs(area) / 2.0);
    }

    private static double haversine(Coordinate a, Coordinate b) {
        double R    = 6371000.0;
        double dLat = Math.toRadians(b.getLatitude()  - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double s    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(a.getLatitude()))
                    * Math.cos(Math.toRadians(b.getLatitude()))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
    }

    // ─── Помилковий canvas ───────────────────────────────────────────────

    private static void drawError(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.web("#1a2533"));
        gc.fillRect(0, 0, w, h);
        gc.setFill(Color.web("#e74c3c"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        gc.fillText("Недостатньо координат для 3D-моделі", w * 0.1, h / 2);
    }

    // ─── Внутрішній DTO ──────────────────────────────────────────────────

    public record GeoDims(double widthM, double heightM, double perimeterM, double areaM2) {}
}
