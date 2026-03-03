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
            return createErrorCanvas(width, height, "Недостатньо даних для моделювання");
        }

        Canvas canvas = new Canvas(width, height);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // 1. Пошук меж для масштабування
        double minLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double maxLat = boundaries.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double minLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double maxLon = boundaries.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);

        double deltaLat = maxLat - minLat;
        double deltaLon = maxLon - minLon;

        // Масштабування: займаємо 70% простору полотна
        double scale = (width * 0.7) / Math.max(deltaLat, deltaLon != 0 ? deltaLon : 0.0001);

        int n = boundaries.size();
        double[] xP = new double[n];
        double[] yP = new double[n];

        // Центрування та Ізометрична проекція
        for (int i = 0; i < n; i++) {
            Coordinate c = boundaries.get(i);
            double dx = (c.getLongitude() - (minLon + maxLon) / 2.0) * scale;
            double dy = (c.getLatitude() - (minLat + maxLat) / 2.0) * scale;

            // Математика аксонометрії
            xP[i] = (width / 2.0) + (dx - dy) * 0.8;
            yP[i] = (height / 2.0) + (dx + dy) * 0.4;
        }

        // 2. МАЛЮВАННЯ ПІДОШВИ (ФУНДАМЕНТ)
        double depth = 18.0; // Товщина шару ґрунту
        gc.setFill(Color.web("#2c3e50", 0.8)); // Антрацитовий колір основи
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            double[] xSide = {xP[i], xP[next], xP[next], xP[i]};
            double[] ySide = {yP[i], yP[next], yP[next] + depth, yP[i] + depth};
            gc.fillPolygon(xSide, ySide, 4);
        }

        // 3. МАЛЮВАННЯ ПОВЕРХНІ
        // Градієнтна заливка (імітація трав'яного покриву)
        LinearGradient grassGrad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2ecc71")),
                new Stop(1, Color.web("#27ae60")));
        gc.setFill(grassGrad);
        gc.fillPolygon(xP, yP, n);

        // 4. ТЕХНІЧНА СІТКА (ГІС-стиль)
        gc.setStroke(Color.web("#ffffff", 0.15));
        gc.setLineWidth(0.8);
        for (int i = -200; i <= 200; i += 20) {
            gc.strokeLine(0, (height/2.0) + i, width, (height/2.0) + i + 100); // Діагональна сітка
        }

        // 5. КОНТУР ТА ВЕРШИНИ
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.2);
        gc.strokePolygon(xP, yP, n);

        for (int i = 0; i < n; i++) {
            gc.setFill(Color.web("#f1c40f")); // Жовті маркери точок
            gc.fillOval(xP[i] - 2, yP[i] - 2, 4, 4);
        }

        // 6. ТЕКСТОВІ МЕТАДАНІ
        gc.setFill(Color.web("#2c3e50"));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        // Висота (H) над першою точкою
        gc.fillText(String.format("H: %.1f m", elevation), xP[0], yP[0] - 15);

        // Технічні підписи по кутах
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        gc.fillText("WGS84 Geometric Projection", 15, height - 15);
        gc.fillText("Scale: 1:" + String.format("%.0f", 1000000/scale), width - 120, height - 15);

        return canvas;
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