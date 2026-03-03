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
 * Візуалізатор об'ємної моделі земельної ділянки для PhD модуля.
 * Використовує Canvas для створення ізометричної інфографіки.
 */
public class LandParcel3dVisualizer {

    public static Canvas create3dPlot(List<Coordinate> boundaries, double elevation, double width, double height) {
        if (boundaries == null || boundaries.size() < 3) {
            return createErrorCanvas(width, height, "Недостатньо даних");
        }

        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // 1. Пошук меж
        double minLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double maxLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double minLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double maxLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);

        double scale = (width * 0.7) / Math.max(maxLat - minLat, maxLon - minLon != 0 ? maxLon - minLon : 0.0001);

        int n = boundaries.size();
        double[] xP = new double[n];
        double[] yP = new double[n];

        // 2. Розрахунок координат з інверсією Y (тепер північ зверху)
        for (int i = 0; i < n; i++) {
            Coordinate c = boundaries.get(i);
            double dx = (c.getLongitude() - (minLon + maxLon) / 2.0) * scale;
            double dy = (c.getLatitude() - (minLat + maxLat) / 2.0) * scale;

            xP[i] = (width / 2.0) + (dx - dy) * 0.8;
            yP[i] = (height / 2.0) - (dx + dy) * 0.4; // ІНВЕРСІЯ ТУТ: змінено + на -
        }

        // 3. Малювання основи (товщина землі)
        double depth = 18.0;
        gc.setFill(Color.web("#2c3e50", 0.9));
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            gc.fillPolygon(
                    new double[]{xP[i], xP[next], xP[next], xP[i]},
                    new double[]{yP[i], yP[next], yP[next] + depth, yP[i] + depth},
                    4
            );
        }

        // 4. Малювання поверхні з градієнтом
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2ecc71")), new Stop(1, Color.web("#27ae60"))));
        gc.fillPolygon(xP, yP, n);

        // Контур
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.2);
        gc.strokePolygon(xP, yP, n);

        // 5. МАЛЮВАННЯ КОМПАСА (Північ)
        drawNorthArrow(gc, width - 50, 50);

        // 6. Текстова інформація
        gc.setFill(Color.web("#2c3e50"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        gc.fillText(String.format("H: %.1f m", elevation), xP[0], yP[0] - 15);

        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        gc.fillText("WGS84 Projection", 15, height - 15);

        return canvas;
    }

    /**
     * Малює стилізовану стрілку компаса
     */
    private static void drawNorthArrow(GraphicsContext gc, double x, double y) {
        gc.setStroke(Color.web("#2c3e50"));
        gc.setLineWidth(2);

        // Стрілка вгору (Північ)
        gc.strokeLine(x, y + 20, x, y - 10); // вертикальна лінія
        gc.strokeLine(x, y - 10, x - 5, y);   // ліве крило
        gc.strokeLine(x, y - 10, x + 5, y);   // праве крило

        gc.setFill(Color.web("#c0392b")); // Червоний колір для літери N
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        gc.fillText("N", x - 5, y - 15);
    }

    private static Canvas createErrorCanvas(double width, double height, String message) {
        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#ecf0f1"));
        gc.fillRect(0, 0, width, height);
        gc.setFill(Color.RED);
        gc.fillText(message, width/4, height/2);
        return canvas;
    }
}