package ua.edu.university;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.util.ConfigManager;

import java.net.URL;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Override
    public void init() {
        logger.info("Ініціалізація системи аналізу земельних ділянок...");
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Завантаження графічного інтерфейсу...");

            // Читаємо шлях до макета з конфігурації
            String fxmlPath = ConfigManager.getProperty("app.fxml.main");
            URL fxmlLocation = getClass().getResource(fxmlPath);
            if (fxmlLocation == null) {
                throw new IllegalStateException("Не вдалося знайти критичний файл макета: " + fxmlPath);
            }

            Parent root = FXMLLoader.load(fxmlLocation);
            Scene scene = new Scene(root);

            // Налаштування головного вікна
            String windowTitle = ConfigManager.getProperty("app.title");
            primaryStage.setTitle(windowTitle != null ? windowTitle : "Land Plot Analyzer");
            primaryStage.setScene(scene);
            primaryStage.setMaximized(true); // Розгортаємо на весь екран

            // Відображаємо вікно
            primaryStage.show();
            logger.info("Додаток успішно запущено та готовий до роботи.");

        } catch (Exception e) {
            logger.error("Критична помилка під час запуску додатку!", e);
            Platform.exit();
            System.exit(1);
        }
    }

    @Override
    public void stop() {
        logger.info("Роботу завершено. Збереження даних і зупинка системи...");
    }

    public static void main(String[] args) {
        launch(args);
    }
}