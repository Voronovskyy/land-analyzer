package ua.edu.university.util;

import javafx.application.Platform;
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

    private static final String TEMP_PATH_SCHEME = "Reports/temp_scheme.png";
    private static final String TEMP_PATH_TERRAIN = "Reports/temp_terrain.png";
    private static final String TEMP_PATH_DEM = "Reports/temp_dem.png";
    private static final String TEMP_PATH_NDVI = "Reports/temp_ndvi.png";
    private static final String TEMP_PATH_SATELLITE = "Reports/temp_satellite.png";
    private static final String TEMP_PATH_3D = "Reports/temp_3d_wireframe.png";

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
        long totalStartTime = System.currentTimeMillis();

        boolean isAiEnabled = ConfigManager.getBooleanProperty("analysis.ai.enabled");
        logger.info("Початок генерації звіту. Режим ШІ: {}", isAiEnabled ? "УВІМКНЕНО" : "ВИМКНЕНО");

        CompletableFuture.runAsync(() -> {
            try {
                // КРОК 1: ПЛАН-СХЕМА
                aiAnalyses.put("INFRASTRUCTURE", fetchAnalysis(isAiEnabled, "INFRASTRUCTURE", lat, lon, statusUpdater));
                File imgScheme = captureSnapshotSync(TEMP_PATH_SCHEME);

                // КРОК 2: РЕЛЬЄФ
                syncLayerChange("terrainGroup");
                aiAnalyses.put("TERRAIN", fetchAnalysis(isAiEnabled, "TERRAIN", lat, lon, statusUpdater));
                File imgTerrain = captureSnapshotSync(TEMP_PATH_TERRAIN);

                // КРОК 3: МОДЕЛЬ ВИСОТ
                syncLayerChange("demLayer");
                aiAnalyses.put("DEM", fetchAnalysis(isAiEnabled, "DEM", lat, lon, statusUpdater));
                File imgDem = captureSnapshotSync(TEMP_PATH_DEM);

                // КРОК 4: ВЕГЕТАЦІЯ
                syncLayerChange("ndviLayer");
                aiAnalyses.put("NDVI", fetchAnalysis(isAiEnabled, "NDVI", lat, lon, statusUpdater));
                File imgNdvi = captureSnapshotSync(TEMP_PATH_NDVI);

                // КРОК 5: СУПУТНИК
                syncLayerChange("satellite");
                aiAnalyses.put("RETROSPECTIVE", fetchAnalysis(isAiEnabled, "RETROSPECTIVE", lat, lon, statusUpdater));
                File imgSat = captureSnapshotSync(TEMP_PATH_SATELLITE);

                // КРОК 6: 3D ВІЗУАЛІЗАЦІЯ
                updateStatus(statusUpdater, "6/6: Генерація 3D-інфографіки...");
                File img3d = capture3DModelSnapshot(boundaries, elevation);

                // ФІНАЛЬНА ЗБІРКА
                long duration = (System.currentTimeMillis() - totalStartTime) / 1000;
                updateStatus(statusUpdater, "Збірка PDF... (Загальний час: " + duration + " сек)");
                validateAnalyses();

                Platform.runLater(() -> {
                    try {
                        pdfService.generateReport(pdfPath, title, area, priceUah, priceUsd,
                                elevation, suitability, boundaries,
                                imgScheme, imgTerrain, imgDem, imgNdvi, imgSat,
                                img3d, aiAnalyses);

                        finalizeAll(pdfPath, statusUpdater, imgScheme, imgTerrain, imgDem, imgNdvi, imgSat, img3d);
                    } catch (Exception e) {
                        logger.error("Помилка PDF", e);
                        statusUpdater.accept("Помилка створення PDF!");
                    }
                });

            } catch (Exception e) {
                logger.error("Помилка координатора", e);
                updateStatus(statusUpdater, "Збій: " + e.getMessage());
            }
        });
    }

    /**
     * Оновлений метод-перемикач із підтримкою статусів
     */
    private String fetchAnalysis(boolean isAiEnabled, String type, double lat, double lon, Consumer<String> statusUpdater) {
        if (!isAiEnabled) {
            updateStatus(statusUpdater, "Отримання даних " + type + " (Технічний режим)...");
            return "[AI DISABLED] Технічний звіт за координатами: " + lat + ", " + lon;
        }

        // Виводимо статус у UI перед початком довгого запиту
        updateStatus(statusUpdater, "Запит до Gemini (" + type + ")...");

        try {
            return switch (type) {
                case "INFRASTRUCTURE" -> geminiService.getInfrastructureAnalysis(lat, lon);
                case "TERRAIN" -> geminiService.getTerrainAnalysis(lat, lon);
                case "DEM" -> geminiService.getDemAnalysis(lat, lon);
                case "NDVI" -> geminiService.getNdviAnalysis(lat, lon);
                case "RETROSPECTIVE" -> geminiService.getSatelliteRetrospective(lat, lon);
                default -> "Дані відсутні.";
            };
        } catch (Exception e) {
            logger.error("Gemini error for {}: {}", type, e.getMessage());
            return "Помилка ШІ аналізу.";
        }
    }

    private File capture3DModelSnapshot(List<Coordinate> boundaries, double elevation) throws Exception {
        CompletableFuture<File> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.canvas.Canvas canvas = LandParcel3dVisualizer.create3dPlot(boundaries, elevation, 500, 300);
                if (canvas == null) { future.complete(null); return; }

                WritableImage image = canvas.snapshot(null, null);
                File file = new File(TEMP_PATH_3D);
                file.getParentFile().mkdirs();
                ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
                future.complete(file);
            } catch (Exception e) { future.completeExceptionally(e); }
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
            } catch (Exception e) { latch.countDown(); }
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
            } catch (Exception e) { future.completeExceptionally(e); }
        });
        return future.get();
    }

    private void validateAnalyses() {
        String[] keys = {"INFRASTRUCTURE", "TERRAIN", "DEM", "NDVI", "RETROSPECTIVE"};
        for (String key : keys) {
            aiAnalyses.putIfAbsent(key, "Дані недоступні.");
        }
    }

    private void updateStatus(Consumer<String> updater, String msg) {
        Platform.runLater(() -> updater.accept(msg));
    }

    private void finalizeAll(String path, Consumer<String> statusUpdater, File... files) {
        for (File f : files) { if (f != null && f.exists()) f.delete(); }
        statusUpdater.accept("Готово! Звіт відкрито.");
        try { java.awt.Desktop.getDesktop().open(new File(path)); } catch (Exception e) {}

        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { Platform.runLater(() -> System.exit(0)); }
        }, 3000);
    }
}