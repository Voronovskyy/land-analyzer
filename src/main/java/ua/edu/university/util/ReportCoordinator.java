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

/**
 * Координатор процесу створення комплексного звіту.
 * Керує послідовним перемиканням картографічних шарів, запитами до ШІ
 * та синхронізацією графічних знімків для фінального PDF.
 */
public class ReportCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(ReportCoordinator.class);
    private static final String TEMP_DIR = "Reports/temp/";
    private static final String IMG_SCHEME = TEMP_DIR + "scheme.png";
    private static final String IMG_TERRAIN = TEMP_DIR + "terrain.png";
    private static final String IMG_DEM = TEMP_DIR + "dem.png";
    private static final String IMG_NDVI = TEMP_DIR + "ndvi.png";
    private static final String IMG_SATELLITE = TEMP_DIR + "satellite.png";
    private static final String IMG_3D = TEMP_DIR + "3d_wireframe.png";
    private final WebView webView;
    private final PdfReportService pdfService;
    private final GeminiAnalysisService geminiService;
    private final Map<String, String> aiAnalyses = new HashMap<>();

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
        this.geminiService = new GeminiAnalysisService();
    }

    /**
     * Запускає асинхронний ланцюжок операцій для створення звіту.
     */
    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability,
                                     List<Coordinate> boundaries,
                                     double lat, double lon,
                                     Consumer<String> statusUpdater) {

        aiAnalyses.clear();
        long totalStartTime = System.currentTimeMillis();

        boolean isAiEnabled = ConfigManager.getBooleanProperty("analysis.ai.enabled");
        logger.info("Ініціалізація циклу звітності. Режим ШІ: {}", isAiEnabled ? "ON" : "OFF");

        CompletableFuture.runAsync(() -> {
            try {
                // ЕТАП 1: Інфраструктурний аналіз
                aiAnalyses.put("INFRASTRUCTURE", fetchAnalysis(isAiEnabled, "INFRASTRUCTURE", lat, lon, statusUpdater));
                File imgScheme = captureSnapshotSync(IMG_SCHEME);

                // ЕТАП 2: Геоморфологічний аналіз
                syncLayerChange("terrainGroup");
                aiAnalyses.put("TERRAIN", fetchAnalysis(isAiEnabled, "TERRAIN", lat, lon, statusUpdater));
                File imgTerrain = captureSnapshotSync(IMG_TERRAIN);

                // ЕТАП 3: Гідрологічний аналіз (DEM)
                syncLayerChange("demLayer");
                aiAnalyses.put("DEM", fetchAnalysis(isAiEnabled, "DEM", lat, lon, statusUpdater));
                File imgDem = captureSnapshotSync(IMG_DEM);

                // ЕТАП 4: Екологічний моніторинг (NDVI)
                syncLayerChange("ndviLayer");
                aiAnalyses.put("NDVI", fetchAnalysis(isAiEnabled, "NDVI", lat, lon, statusUpdater));
                File imgNdvi = captureSnapshotSync(IMG_NDVI);

                // ЕТАП 5: Ретроспективний аналіз
                syncLayerChange("satellite");
                aiAnalyses.put("RETROSPECTIVE", fetchAnalysis(isAiEnabled, "RETROSPECTIVE", lat, lon, statusUpdater));
                File imgSat = captureSnapshotSync(IMG_SATELLITE);

                // ЕТАП 6: Графічне 3D моделювання
                updateStatus(statusUpdater, "6/6: Побудова 3D моделі рельєфу...");
                File img3d = capture3DModelSnapshot(boundaries, elevation);

                // Завершення обробки даних
                long durationSeconds = (System.currentTimeMillis() - totalStartTime) / 1000;
                updateStatus(statusUpdater, "Формування PDF документа... (Час: " + durationSeconds + "с)");
                validateAnalyses();

                Platform.runLater(() -> {
                    try {
                        pdfService.generateReport(pdfPath, title, area, priceUah, priceUsd,
                                elevation, suitability, boundaries,
                                imgScheme, imgTerrain, imgDem, imgNdvi, imgSat,
                                img3d, aiAnalyses);

                        finalizeAll(pdfPath, statusUpdater, imgScheme, imgTerrain, imgDem, imgNdvi, imgSat, img3d);
                    } catch (Exception e) {
                        logger.error("Помилка при збірці PDF: {}", e.getMessage());
                        statusUpdater.accept("Помилка створення PDF!");
                    }
                });

            } catch (Exception e) {
                logger.error("Критичний збій у послідовності звітності: {}", e.getMessage());
                updateStatus(statusUpdater, "Збій процесу: " + e.getMessage());
            }
        });
    }

    /**
     * Отримує аналітичні висновки від ШІ або повертає технічну заглушку.
     */
    private String fetchAnalysis(boolean isAiEnabled, String type, double lat, double lon, Consumer<String> statusUpdater) {
        if (!isAiEnabled) {
            updateStatus(statusUpdater, "Збір технічних даних: " + type);
            return "[AI OFF] Автоматичний опис об'єкта за координатами: " + lat + ", " + lon;
        }

        updateStatus(statusUpdater, "Аналіз Gemini: " + type + "...");
        return switch (type) {
            case "INFRASTRUCTURE" -> geminiService.getInfrastructureAnalysis(lat, lon);
            case "TERRAIN" -> geminiService.getTerrainAnalysis(lat, lon);
            case "DEM" -> geminiService.getDemAnalysis(lat, lon);
            case "NDVI" -> geminiService.getNdviAnalysis(lat, lon);
            case "RETROSPECTIVE" -> geminiService.getSatelliteRetrospective(lat, lon);
            default -> "Дані відсутні.";
        };
    }

    /**
     * Створює знімок Canvas з 3D візуалізацією.
     */
    private File capture3DModelSnapshot(List<Coordinate> boundaries, double elevation) throws Exception {
        CompletableFuture<File> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.canvas.Canvas canvas = LandParcel3dVisualizer.create3dPlot(boundaries, elevation, 500, 300);
                if (canvas == null) {
                    future.complete(null);
                    return;
                }

                WritableImage image = canvas.snapshot(null, null);
                File file = new File(IMG_3D);
                file.getParentFile().mkdirs();
                ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
                future.complete(file);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get();
    }

    /**
     * Синхронізує перемикання шарів на карті з очікуванням рендерингу.
     */
    private void syncLayerChange(String layerVarName) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        int delay = ConfigManager.getIntProperty("report.layer.switch.delay.ms");

        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript("showLayer('" + layerVarName + "');");
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(delay));
                pause.setOnFinished(event -> latch.countDown());
                pause.play();
            } catch (Exception e) {
                latch.countDown();
            }
        });
        latch.await();
    }

    /**
     * Виконує миттєвий скріншот поточного стану WebView.
     */
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
            aiAnalyses.putIfAbsent(key, "Дані наразі недоступні.");
        }
    }

    private void updateStatus(Consumer<String> updater, String msg) {
        Platform.runLater(() -> updater.accept(msg));
    }

    /**
     * Очищає тимчасові файли та завершує роботу програми.
     */
    private void finalizeAll(String path, Consumer<String> statusUpdater, File... files) {
        for (File f : files) {
            if (f != null && f.exists()) f.delete();
        }

        statusUpdater.accept("Готово! Відкриття звіту...");

        try {
            java.awt.Desktop.getDesktop().open(new File(path));
        } catch (Exception e) {
            logger.error("Не вдалося автоматично відкрити PDF: {}", e.getMessage());
        }

        int exitDelay = ConfigManager.getIntProperty("report.final.exit.delay.ms");
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> System.exit(0));
            }
        }, exitDelay);
    }
}