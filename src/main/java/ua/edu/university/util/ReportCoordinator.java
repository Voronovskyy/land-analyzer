package ua.edu.university.util;

import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class ReportCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(ReportCoordinator.class);

    // Шляхи до тимчасових файлів
    private static final String TEMP_PATH_SCHEME = "Reports/temp_scheme.png";
    private static final String TEMP_PATH_TERRAIN = "Reports/temp_terrain.png";
    private static final String TEMP_PATH_DEM = "Reports/temp_dem.png";
    private static final String TEMP_PATH_NDVI = "Reports/temp_ndvi.png";
    private static final String TEMP_PATH_SATELLITE = "Reports/temp_satellite.png";

    private final WebView webView;
    private final PdfReportService pdfService;
    private final GeminiAnalysisService geminiService;
    private final Map<String, String> aiAnalyses = new HashMap<>();

    // Час очікування провантаження тайлів карти (мс)
    private static final int RENDER_DELAY_MS = 1500;

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
        this.geminiService = new GeminiAnalysisService();
    }

    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability,
                                     List<Coordinate> boundaries,
                                     double lat, double lon, // Координати для ШІ
                                     Consumer<String> statusUpdater) {

        aiAnalyses.clear();

        // Запускаємо ланцюжок у фоновому потоці, щоб не блокувати UI JavaFX
        CompletableFuture.runAsync(() -> {
            try {
                // КРОК 1: ПЛАН-СХЕМА + ІНФРАСТРУКТУРА
                updateStatus(statusUpdater, "1/5: Аналіз інфраструктури (Gemini)...");
                aiAnalyses.put("INFRASTRUCTURE", geminiService.getInfrastructureAnalysis(lat, lon));
                File imgScheme = captureSnapshotSync(TEMP_PATH_SCHEME);

                // КРОК 2: РЕЛЬЄФ + ГЕОМОРФОЛОГІЯ
                updateStatus(statusUpdater, "2/5: Аналіз рельєфу (Gemini)...");
                aiAnalyses.put("TERRAIN", geminiService.getTerrainAnalysis(lat, lon));
                syncLayerChange("terrainGroup");
                File imgTerrain = captureSnapshotSync(TEMP_PATH_TERRAIN);

                // КРОК 3: МОДЕЛЬ ВИСОТ + ГІДРОЛОГІЯ
                updateStatus(statusUpdater, "3/5: Модель висот (Gemini)...");
                aiAnalyses.put("DEM", geminiService.getDemAnalysis(lat, lon));
                syncLayerChange("demLayer");
                File imgDem = captureSnapshotSync(TEMP_PATH_DEM);

                // КРОК 4: ВЕГЕТАЦІЯ + ЕКОЛОГІЯ
                updateStatus(statusUpdater, "4/5: Індекс вегетації (Gemini)...");
                aiAnalyses.put("NDVI", geminiService.getNdviAnalysis(lat, lon));
                syncLayerChange("ndviLayer");
                File imgNdvi = captureSnapshotSync(TEMP_PATH_NDVI);

                // КРОК 5: СУПУТНИК + РЕТРОСПЕКТИВА
                updateStatus(statusUpdater, "5/5: Ретроспективний аналіз (Gemini)...");
                aiAnalyses.put("RETROSPECTIVE", geminiService.getSatelliteRetrospective(lat, lon));
                syncLayerChange("satellite");
                File imgSat = captureSnapshotSync(TEMP_PATH_SATELLITE);

                // ФІНАЛЬНИЙ ЕТАП: ЗБІРКА PDF
                updateStatus(statusUpdater, "Збірка фінального PDF звіту...");

                // Перевірка на наявність усіх відповідей ШІ
                validateAnalyses();

                Platform.runLater(() -> {
                    try {
                        pdfService.generateReport(pdfPath, title, area, priceUah, priceUsd,
                                elevation, suitability, boundaries,
                                imgScheme, imgTerrain, imgDem, imgNdvi, imgSat,
                                aiAnalyses);

                        finalizeAll(pdfPath, statusUpdater, imgScheme, imgTerrain, imgDem, imgNdvi, imgSat);
                    } catch (Exception e) {
                        logger.error("Помилка фіналізації PDF", e);
                        statusUpdater.accept("Помилка створення PDF!");
                    }
                });

            } catch (Exception e) {
                logger.error("Помилка в ланцюжку звітності", e);
                updateStatus(statusUpdater, "Збій процесу: " + e.getMessage());
            }
        });
    }

    private void syncLayerChange(String layerVarName) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                // Виклик JS для зміни шару
                webView.getEngine().executeScript("showLayer('" + layerVarName + "');");

                // ВАЖЛИВО: Пауза в UI-потоці на 2 секунди, щоб карта встигла завантажити тайли
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
                pause.setOnFinished(event -> latch.countDown());
                pause.play();

            } catch (Exception e) {
                logger.error("Помилка JS при зміні шару: {}", e.getMessage());
                latch.countDown();
            }
        });

        // Блокуємо фоновий потік, поки UI-потік не відрахує 2 секунди
        if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            logger.warn("Таймаут очікування рендерингу шару: {}", layerVarName);
        }
    }

    /**
     * Робить знімок у потоці JavaFX і чекає на результат
     */
    private File captureSnapshotSync(String path) throws Exception {
        CompletableFuture<File> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                WritableImage image = webView.snapshot(null, null);
                File file = new File(path);
                // Створюємо папку, якщо її немає
                file.getParentFile().mkdirs();
                ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
                future.complete(file);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get();
    }

    private void validateAnalyses() {
        String[] keys = {"INFRASTRUCTURE", "TERRAIN", "DEM", "NDVI", "RETROSPECTIVE"};
        for (String key : keys) {
            aiAnalyses.putIfAbsent(key, "Аналітичні дані за даним параметром тимчасово недоступні.");
        }
    }

    private void updateStatus(Consumer<String> updater, String msg) {
        Platform.runLater(() -> updater.accept(msg));
    }

    private void finalizeAll(String path, Consumer<String> statusUpdater, File... files) {
        // 1. Повертаємо карту до дефолтного стану (про всяк випадок перед виходом)
        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript("if (typeof showLayer === 'function') { showLayer('osm'); }");
            } catch (Exception e) {
                logger.warn("Не вдалося скинути шар карти: {}", e.getMessage());
            }
        });

        // 2. Видаляємо тимчасові скріншоти
        for (File f : files) {
            if (f != null && f.exists()) {
                f.delete();
            }
        }

        statusUpdater.accept("Звіт успішно створено! Програма завершує роботу...");

        // 3. Відкриваємо готовий PDF
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(new File(path));
            } catch (Exception e) {
                logger.error("Не вдалося відкрити PDF", e);
            }
        }
        // 4. ЗАВЕРШЕННЯ ПРОГРАМИ
        // Даємо невелику затримку (2 секунди), щоб користувач встиг побачити статус "Успішно"
        // і щоб PDF-рідер встиг ініціалізувати відкриття файлу
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                logger.info("Генерація завершена. Автоматичне вимкнення системи.");
                Platform.exit();
                System.exit(0);
            }
        }, 2000);
    }
}