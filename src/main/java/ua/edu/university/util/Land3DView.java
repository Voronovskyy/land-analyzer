package ua.edu.university.util;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import java.util.List;

/**
 * JavaFX-вікно інтерактивної 3D-ізометричної візуалізації земельної ділянки.
 * Відображає wireframe-модель, побудовану {@link LandParcel3dVisualizer},
 * та панель геометричних характеристик (площа, периметр, висота). Обертання
 * здійснюється перетягуванням миші по горизонталі.
 */
public class Land3DView {
    private static final Logger logger = LoggerFactory.getLogger(Land3DView.class);

    private static final double CANVAS_W = 680;
    private static final double CANVAS_H = 500;
    private static final double PANEL_W = 190;

    public static void show(List<Coordinate> boundaries, double elevation, String title) {
        logger.info("Підготовка 3D візуалізації: {}", title);
        try {
            LandParcel3dVisualizer.GeoDims dims =
                    LandParcel3dVisualizer.computeDimensions(boundaries);

            Canvas canvas = new Canvas(CANVAS_W, CANVAS_H);
            canvas.setCursor(Cursor.HAND);

            double[] rotAngle = {0.0};
            double[] dragX = {0.0};

            Runnable redraw = () -> LandParcel3dVisualizer.draw(
                    canvas.getGraphicsContext2D(), boundaries, elevation,
                    CANVAS_W, CANVAS_H, rotAngle[0]);
            redraw.run();

            canvas.setOnMousePressed(e -> dragX[0] = e.getX());
            canvas.setOnMouseDragged(e -> {
                rotAngle[0] += (e.getX() - dragX[0]) * 0.012;
                dragX[0] = e.getX();
                redraw.run();
            });

            StackPane center = new StackPane(canvas);
            center.setStyle("-fx-background-color: #1a2533;");

            BorderPane root = new BorderPane();
            root.setCenter(center);
            root.setRight(buildInfoPanel(dims, elevation));

            Stage stage = new Stage();
            stage.setTitle("3D Модель — " + title);
            stage.setResizable(false);
            stage.setScene(new Scene(root, CANVAS_W + PANEL_W, CANVAS_H));
            stage.show();

            logger.info("Вікно 3D відображено.");
        } catch (Exception e) {
            logger.error("Помилка 3D вікна: {}", e.getMessage());
        }
    }

    private static VBox buildInfoPanel(LandParcel3dVisualizer.GeoDims dims, double elevation) {
        VBox box = new VBox();
        box.setPrefWidth(PANEL_W);
        box.setStyle("-fx-background-color: #0d1b2a; -fx-border-color: #27ae60; -fx-border-width: 0 0 0 1;");
        box.setPadding(new Insets(20, 14, 20, 16));

        box.getChildren().addAll(
                sectionTitle("ГЕОМЕТРІЯ"),
                gap(10),
                dataRow("Ширина (EW)", String.format("%.0f м", dims.widthM())),
                gap(8),
                dataRow("Довжина (NS)", String.format("%.0f м", dims.heightM())),
                gap(8),
                dataRow("Периметр", String.format("%.0f м", dims.perimeterM())),
                gap(8),
                dataRow("Площа", String.format("%.4f га", dims.areaM2() / 10_000.0)),
                gap(20),
                sectionTitle("РЕЛЬЄФ"),
                gap(10),
                dataRow("Висота н.р.м.", String.format("%.1f м", elevation)),
                gap(30),
                hint()
        );
        return box;
    }

    private static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 10px; -fx-font-weight: bold;");
        return l;
    }

    private static VBox dataRow(String key, String value) {
        Label k = new Label(key);
        k.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10px;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 14px; -fx-font-weight: bold;");
        return new VBox(1, k, v);
    }

    private static Label gap(int h) {
        Label l = new Label();
        l.setMinHeight(h);
        l.setPrefHeight(h);
        return l;
    }

    private static Label hint() {
        Label l = new Label("← Перетягніть мишею\n   для обертання →");
        l.setStyle("-fx-text-fill: #4a6278; -fx-font-size: 10px;");
        l.setWrapText(true);
        return l;
    }
}
