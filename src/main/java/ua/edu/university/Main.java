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

/**
 * Головний клас додатка, що відповідає за життєвий цикл JavaFX системи.
 * Виконує початкову ініціалізацію, завантаження макета інтерфейсу
 * та конфігурацію головного вікна PhD модуля.
 */
public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Попередня ініціалізація перед створенням UI.
     * Використовується для завантаження важких ресурсів або перевірки підключень.
     */
    @Override
    public void init() {
        logger.info("--- [STARTING SYSTEM INITIALIZATION] ---");
        logger.info("Встановлення оточення PhD модуля аналізу земельних ділянок...");
    }

    /**
     * Точка входу в графічний інтерфейс.
     *
     * @param primaryStage Головне вікно додатка.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Завантаження компонентів графічного інтерфейсу (FXML)...");

            // Отримуємо шлях до макета з існуючої конфігурації
            String fxmlPath = ConfigManager.getProperty("app.fxml.main");
            if (fxmlPath == null || fxmlPath.isEmpty()) {
                throw new IllegalArgumentException("Параметр 'app.fxml.main' відсутній у конфігурації.");
            }

            URL fxmlLocation = getClass().getResource(fxmlPath);
            if (fxmlLocation == null) {
                throw new IllegalStateException("Критична помилка: файл макета за шляхом '" + fxmlPath + "' не знайдено.");
            }

            // Завантаження ієрархії об'єктів інтерфейсу
            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root = loader.load();

            // Конфігурація головної сцени
            Scene scene = new Scene(root);

            // Налаштування параметрів вікна
            String windowTitle = ConfigManager.getProperty("app.title");
            primaryStage.setTitle(windowTitle != null ? windowTitle : "Land Plot Analyzer | Research Tool");

            primaryStage.setScene(scene);

            // Встановлюємо вікно розгорнутим для оптимального огляду карт
            primaryStage.setMaximized(true);

            // Додаємо обробник закриття вікна (безпечний вихід)
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Користувач ініціював закриття головного вікна.");
                Platform.exit();
            });

            primaryStage.show();
            logger.info("Система успішно запущена. Головне вікно відображено.");

        } catch (Exception e) {
            logger.error("!!! [CRITICAL LAUNCH ERROR] !!!");
            logger.error("Деталі помилки запуску: {}", e.getMessage(), e);

            // Гарантоване закриття при збої ініціалізації
            Platform.exit();
            System.exit(1);
        }
    }

    /**
     * Викликається автоматично при зупинці роботи додатка.
     * Забезпечує безпечне вивільнення ресурсів.
     */
    @Override
    public void stop() {
        logger.info("--- [SYSTEM SHUTDOWN] ---");
        logger.info("Збереження логів та коректне завершення процесів...");
    }

    /**
     * Стандартна точка входу Java.
     *
     * @param args Аргументи командного рядка.
     */
    public static void main(String[] args) {
        launch(args);
    }
}