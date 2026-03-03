package ua.edu.university.util;

import javafx.application.Platform;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.model.Coordinate;

import javax.imageio.ImageIO;
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
    private static final String TEMP_PATH_3D = "Reports/temp_3d_wireframe.png"; // Шлях для 3D моделі

    private final WebView webView;
    private final PdfReportService pdfService;
    private final GeminiAnalysisService geminiService;
    private final Map<String, String> aiAnalyses = new HashMap<>();

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
        this.geminiService = new GeminiAnalysisService();
    }

    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability,
                                     List<Coordinate> boundaries,
                                     double lat, double lon,
                                     Consumer<String> statusUpdater) {

        aiAnalyses.clear();

        CompletableFuture.runAsync(() -> {
            try {
                // КРОК 1: ПЛАН-СХЕМА
                updateStatus(statusUpdater, "1/6: Аналіз інфраструктури (Gemini)...");
                aiAnalyses.put("INFRASTRUCTURE", geminiService.getInfrastructureAnalysis(lat, lon));
                File imgScheme = captureSnapshotSync(TEMP_PATH_SCHEME);

                // КРОК 2: РЕЛЬЄФ
                updateStatus(statusUpdater, "2/6: Аналіз рельєфу (Gemini)...");
                aiAnalyses.put("TERRAIN", geminiService.getTerrainAnalysis(lat, lon));
                syncLayerChange("terrainGroup");
                File imgTerrain = captureSnapshotSync(TEMP_PATH_TERRAIN);

                // КРОК 3: МОДЕЛЬ ВИСОТ
                updateStatus(statusUpdater, "3/6: Модель висот (Gemini)...");
                aiAnalyses.put("DEM", geminiService.getDemAnalysis(lat, lon));
                syncLayerChange("demLayer");
                File imgDem = captureSnapshotSync(TEMP_PATH_DEM);

                // КРОК 4: ВЕГЕТАЦІЯ
                updateStatus(statusUpdater, "4/6: Індекс вегетації (Gemini)...");
                aiAnalyses.put("NDVI", geminiService.getNdviAnalysis(lat, lon));
                syncLayerChange("ndviLayer");
                File imgNdvi = captureSnapshotSync(TEMP_PATH_NDVI);

                // КРОК 5: СУПУТНИК
                updateStatus(statusUpdater, "5/6: Ретроспективний аналіз (Gemini)...");
                aiAnalyses.put("RETROSPECTIVE", geminiService.getSatelliteRetrospective(lat, lon));
                syncLayerChange("satellite");
                File imgSat = captureSnapshotSync(TEMP_PATH_SATELLITE);

                // КРОК 6: ГЕНЕРАЦІЯ СПРАВЖНЬОЇ 3D ВІЗУАЛІЗАЦІЇ
                updateStatus(statusUpdater, "6/6: Створення об'ємної моделі ділянки...");
                File img3d = capture3DModelSnapshot(boundaries, elevation);

                // ФІНАЛЬНИЙ ЕТАП: ЗБІРКА PDF
                updateStatus(statusUpdater, "Збірка фінального PDF звіту...");
                validateAnalyses();

                Platform.runLater(() -> {
                    try {
                        // Передаємо всі файли, включаючи img3d
                        pdfService.generateReport(pdfPath, title, area, priceUah, priceUsd,
                                elevation, suitability, boundaries,
                                imgScheme, imgTerrain, imgDem, imgNdvi, imgSat,
                                img3d, aiAnalyses);

                        finalizeAll(pdfPath, statusUpdater, imgScheme, imgTerrain, imgDem, imgNdvi, imgSat, img3d);
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

    /**
     * Створює 3D модель за допомогою LandParcel3dVisualizer і робить скріншот
     */
    private File capture3DModelSnapshot(List<Coordinate> boundaries, double elevation) throws Exception {
        CompletableFuture<File> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                // Створюємо Canvas
                javafx.scene.canvas.Canvas canvas = LandParcel3dVisualizer.create3dPlot(boundaries, elevation, 500, 300);

                if (canvas == null) {
                    future.complete(null);
                    return;
                }

                // Робимо Snapshot (Canvas завжди рендериться миттєво)
                WritableImage image = canvas.snapshot(null, null);

                File file = new File(TEMP_PATH_3D);
                file.getParentFile().mkdirs();

                javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
                future.complete(file);
                logger.info("3D-інфографіка успішно збережена.");
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get();
    }

    private void syncLayerChange(String layerVarName) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript("showLayer('" + layerVarName + "');");
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.0));
                pause.setOnFinished(event -> latch.countDown());
                pause.play();
            } catch (Exception e) {
                latch.countDown();
            }
        });
        latch.await();
    }

    private File captureSnapshotSync(String path) throws Exception {
        CompletableFuture<File> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                WritableImage image = webView.snapshot(null, null);
                File file = new File(path);
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
        // Очистка темпів
        for (File f : files) { if (f != null && f.exists()) f.delete(); }

        statusUpdater.accept("Готово! Звіт відкрито. Програма завершує роботу...");

        // Відкриваємо PDF
        try { java.awt.Desktop.getDesktop().open(new File(path)); } catch (Exception e) {}

        // ТАЙМЕР ВИХОДУ
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    logger.info("Автоматичне закриття системи.");
                    System.exit(0);
                });
            }
        }, 3000); // 3 секунди
    }
}