package ua.edu.university.util;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import ua.edu.university.model.Coordinate;
import java.util.List;

public class Land3DView {

    public static void show(List<Coordinate> boundaries, double elevation, String title) {
        // Використовуємо наш LandParcel3dVisualizer, який ми вже написали
        Canvas canvas = LandParcel3dVisualizer.create3dPlot(boundaries, elevation, 600, 400);

        if (canvas == null) return;

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: #fafafa; -fx-padding: 20;");

        Stage stage = new Stage();
        stage.setTitle("3D Візуалізація ділянки: " + title);
        stage.setScene(new Scene(root));

        // Додаємо іконку або стиль, якщо потрібно
        stage.show();
    }
}