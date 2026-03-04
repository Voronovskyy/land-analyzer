package ua.edu.university.util;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import java.util.List;

/**
 * Клас для відображення тривимірної графічної моделі земельної ділянки.
 * Забезпечує створення та конфігурацію окремого вікна JavaFX для візуалізації
 * результатів геоморфологічного аналізу.
 */
public class Land3DView {
    private static final Logger logger = LoggerFactory.getLogger(Land3DView.class);

    /**
     * Створює та відображає вікно з 3D візуалізацією ділянки.
     *
     * @param boundaries Список координат меж ділянки для побудови полігону.
     * @param elevation  Середня висота ділянки над рівнем моря (н.р.м.).
     * @param title      Заголовок вікна (зазвичай адреса або кадастровий номер).
     */
    public static void show(List<Coordinate> boundaries, double elevation, String title) {
        logger.info("Підготовка 3D візуалізації для об'єкта: {}", title);

        try {
            // Генерація графічного полотна (Canvas) за допомогою спеціалізованого візуалізатора
            // Параметри 600x400 забезпечують оптимальне співвідношення сторін для звітів
            Canvas canvas = LandParcel3dVisualizer.create3dPlot(boundaries, elevation, 600, 400);

            if (canvas == null) {
                logger.warn("Не вдалося створити графічну модель: недостатньо геоданих.");
                return;
            }

            // Налаштування контейнера для Canvas
            StackPane root = new StackPane(canvas);
            // Використовуємо нейтральний фон #fafafa для кращого контрасту з 3D моделлю
            root.setStyle("-fx-background-color: #fafafa; -fx-padding: 20; -fx-border-color: #dcdcdc; -fx-border-width: 1;");

            // Налаштування та запуск вікна (Stage)
            Stage stage = new Stage();
            stage.setTitle("Система аналізу: 3D модель — " + title);

            // Забороняємо надто малий розмір вікна для збереження цілісності графіки
            stage.setMinWidth(620);
            stage.setMinHeight(440);

            Scene scene = new Scene(root);
            stage.setScene(scene);

            logger.info("Вікно візуалізації успішно ініціалізовано.");
            stage.show();
        } catch (Exception e) {
            logger.error("Критична помилка при відображенні 3D вікна: {}", e.getMessage());
        }
    }
}