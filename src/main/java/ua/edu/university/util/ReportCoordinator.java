package ua.edu.university.util;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.api.CountryContextService;
import ua.edu.university.api.InsolationApiService;
import ua.edu.university.api.OverpassApiService;
import ua.edu.university.api.WeatherApiService;
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
    private final WeatherApiService weatherService;
    private final OverpassApiService overpassService;
    private final InsolationApiService insolationService;
    private final CountryContextService countryService;

    private final Map<String, String> aiAnalyses = new HashMap<>();

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
        this.geminiService = new GeminiAnalysisService();
        this.weatherService = new WeatherApiService();
        this.overpassService = new OverpassApiService();
        this.insolationService = new InsolationApiService();
        this.countryService = new CountryContextService();
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

        CompletableFuture.runAsync(() -> {
            try {
                // --- БЛОК 1: ЗБІР ФІЗИЧНИХ ТА КЛІМАТИЧНИХ ДАНИХ (Паралельно) ---
                updateStatus(statusUpdater, "Збір кліматичних показників (Open-Meteo)...");
                JsonObject climateData = weatherService.getClimateStatistics(lat, lon);

                updateStatus(statusUpdater, "Аналіз інженерних мереж (Overpass API)...");
                JsonObject infraDetails = overpassService.getInfrastructureDetails(lat, lon);

                updateStatus(statusUpdater, "Розрахунок інсоляції та регіональних даних...");
                String dayLength = insolationService.getDayLength(lat, lon);
                JsonObject countryContext = countryService.getRegionalContext();

                // --- БЛОК 2: ВІЗУАЛЬНІ ШАРИ ТА ШІ ---

                // 1. Схема
                aiAnalyses.put("INFRASTRUCTURE", fetchAnalysis(isAiEnabled, "INFRASTRUCTURE", lat, lon, statusUpdater));
                File imgScheme = captureSnapshotSync(IMG_SCHEME);

                // 2. Рельєф
                syncLayerChange("terrainGroup");
                aiAnalyses.put("TERRAIN", fetchAnalysis(isAiEnabled, "TERRAIN", lat, lon, statusUpdater));
                File imgTerrain = captureSnapshotSync(IMG_TERRAIN);

                // 3. Висоти
                syncLayerChange("demLayer");
                aiAnalyses.put("DEM", fetchAnalysis(isAiEnabled, "DEM", lat, lon, statusUpdater));
                File imgDem = captureSnapshotSync(IMG_DEM);

                // 4. Вегетація
                syncLayerChange("ndviLayer");
                aiAnalyses.put("NDVI", fetchAnalysis(isAiEnabled, "NDVI", lat, lon, statusUpdater));
                File imgNdvi = captureSnapshotSync(IMG_NDVI);

                // 5. Супутник
                syncLayerChange("satellite");
                aiAnalyses.put("RETROSPECTIVE", fetchAnalysis(isAiEnabled, "RETROSPECTIVE", lat, lon, statusUpdater));
                File imgSat = captureSnapshotSync(IMG_SATELLITE);

                // 6. 3D Модель
                updateStatus(statusUpdater, "Генерація 3D-моделі...");
                File img3d = capture3DModelSnapshot(boundaries, elevation);

                // --- БЛОК 3: ФІНАЛЬНА ЗБІРКА PDF ---
                long duration = (System.currentTimeMillis() - totalStartTime) / 1000;
                updateStatus(statusUpdater, "Збірка комплексного звіту... (" + duration + "с)");

                validateAnalyses();

                Platform.runLater(() -> {
                    try {
                        // Оновлений виклик pdfService з новими даними
                        pdfService.generateReportExtended(pdfPath, title, area, priceUah, priceUsd,
                                elevation, suitability, boundaries,
                                imgScheme, imgTerrain, imgDem, imgNdvi, imgSat, img3d,
                                aiAnalyses, climateData, infraDetails, dayLength, countryContext);

                        finalizeAll(pdfPath, statusUpdater, imgScheme, imgTerrain, imgDem, imgNdvi, imgSat, img3d);
                    } catch (Exception e) {
                        logger.error("Помилка PDF: {}", e.getMessage());
                        statusUpdater.accept("Помилка створення PDF!");
                    }
                });

            } catch (Exception e) {
                logger.error("Критичний збій: {}", e.getMessage());
                updateStatus(statusUpdater, "Збій: " + e.getMessage());
            }
        });
    }

    // Методи captureSnapshotSync, syncLayerChange, fetchAnalysis залишаються без змін,
    // як у вашому оригіналі, але додаємо метод оновлення статусу для зручності.

    private String fetchAnalysis(boolean isAiEnabled, String type, double lat, double lon, Consumer<String> statusUpdater) {
        if (!isAiEnabled) return "[AI DISABLED] Технічний аналіз для " + type;
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

    private void updateStatus(Consumer<String> updater, String msg) {
        Platform.runLater(() -> updater.accept(msg));
    }

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

    /**
     * Очищає тимчасові файли та завершує роботу програми.
     */
    private void finalizeAll(String path, Consumer<String> statusUpdater, File... files) {
        for (File f : files) {
            if (f != null && f.exists()) f.delete();
        }
        statusUpdater.accept("Готово! Звіт відкрито.");
        try {
            java.awt.Desktop.getDesktop().open(new File(path));
        } catch (Exception e) {
        }
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> System.exit(0));
            }
        }, ConfigManager.getIntProperty("report.final.exit.delay.ms"));
    }
}