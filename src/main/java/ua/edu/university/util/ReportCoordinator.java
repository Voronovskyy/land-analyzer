package ua.edu.university.util;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.api.CountryContextService;
import ua.edu.university.api.GeoApiService;
import ua.edu.university.api.InsolationApiService;
import ua.edu.university.api.OverpassApiService;
import ua.edu.university.api.WeatherApiService;
import ua.edu.university.model.Coordinate;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String IMG_SCHEME          = TEMP_DIR + "scheme.png";
    private static final String IMG_TERRAIN         = TEMP_DIR + "terrain.png";
    private static final String IMG_DEM             = TEMP_DIR + "dem.png";
    private static final String IMG_NDVI            = TEMP_DIR + "ndvi.png";
    private static final String IMG_SATELLITE       = TEMP_DIR + "satellite.png";
    private static final String IMG_3D              = TEMP_DIR + "3d_wireframe.png";
    private static final String IMG_SLOPE           = TEMP_DIR + "slope.png";
    private static final String IMG_WEATHER_CHART   = TEMP_DIR + "weather_chart.png";
    private static final String IMG_INFRA_ANNOTATED = TEMP_DIR + "infra_annotated.png";
    private final WebView webView;
    private final PdfReportService pdfService;
    private final ClaudeAnalysisService geminiService;
    private final WeatherApiService weatherService;
    private final OverpassApiService overpassService;
    private final InsolationApiService insolationService;
    private final CountryContextService countryService;
    private final GeoApiService geoApiService;

    private final Map<String, String> aiAnalyses = new HashMap<>();

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
        this.geminiService = new ClaudeAnalysisService();
        this.weatherService = new WeatherApiService();
        this.overpassService = new OverpassApiService();
        this.insolationService = new InsolationApiService();
        this.countryService = new CountryContextService();
        this.geoApiService = new GeoApiService();
    }

    /**
     * Запускає асинхронний ланцюжок операцій для створення звіту.
     */
    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability,
                                     List<Coordinate> boundaries,
                                     double lat, double lon,
                                     Set<String> selectedLayers,
                                     boolean isAiEnabled,
                                     Consumer<String> statusUpdater) {

        aiAnalyses.clear();
        long totalStartTime = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            try {
                // --- БЛОК 1: ЗБІР ФІЗИЧНИХ ТА КЛІМАТИЧНИХ ДАНИХ (Паралельно) ---
                updateStatus(statusUpdater, "Збір кліматичних показників (Open-Meteo)...");
                JsonObject climateData = weatherService.getClimateStatistics(lat, lon);

                updateStatus(statusUpdater, "Завантаження даних за останній місяць...");
                JsonObject monthlyData = weatherService.getMonthlyData(lat, lon);

                updateStatus(statusUpdater, "Аналіз інженерних мереж (Overpass API)...");
                JsonObject infraDetails = overpassService.getInfrastructureDetails(lat, lon);

                updateStatus(statusUpdater, "Розрахунок інсоляції та регіональних даних...");
                String dayLength = insolationService.getDayLength(lat, lon);
                JsonObject countryContext = countryService.getRegionalContext();

                updateStatus(statusUpdater, "Визначення адресних даних...");
                JsonObject geoAddress = geoApiService.getReverseGeocode(lat, lon);

                // Повний розрахунок КП на основі всіх зібраних даних
                final double finalSuitability = SuitabilityCalculator.calculate(elevation, infraDetails, climateData);

                // Графік погоди
                File weatherChartFile = null;
                if (monthlyData != null) {
                    updateStatus(statusUpdater, "Генерація кліматичного графіку...");
                    weatherChartFile = captureWeatherChartSync(monthlyData);
                }

                // --- БЛОК 2: ВІЗУАЛЬНІ ШАРИ ТА ШІ ---

                // Трохи віддаляємо карту перед серією скріншотів
                zoomOutForCapture();

                // 1. Схема
                File imgScheme = null;
                File imgInfraAnnotated = null;
                if (selectedLayers.contains("SCHEME")) {
                    aiAnalyses.put("INFRASTRUCTURE", fetchAnalysis(isAiEnabled, "INFRASTRUCTURE", lat, lon, statusUpdater));
                    waitForTilesSync();
                    imgScheme = captureSnapshotSync(IMG_SCHEME);   // з паспортом → карта стор.1

                    // Для секції 5: знімаємо без паспорту, потім відновлюємо
                    closePopupSync();
                    File imgClean = captureSnapshotSync(IMG_INFRA_ANNOTATED);
                    openPopupSync();

                    if (imgClean != null) {
                        updateStatus(statusUpdater, "Анотація транспортної доступності...");
                        imgInfraAnnotated = InfraAnnotator.annotate(imgClean, lat, lon, infraDetails, IMG_INFRA_ANNOTATED);
                    }
                }

                // 2. Рельєф
                File imgTerrain = null;
                if (selectedLayers.contains("TERRAIN")) {
                    syncLayerChange("terrainGroup");
                    aiAnalyses.put("TERRAIN", fetchAnalysis(isAiEnabled, "TERRAIN", lat, lon, statusUpdater));
                    imgTerrain = captureSnapshotSync(IMG_TERRAIN);
                }

                // 3. Висоти
                File imgDem = null;
                if (selectedLayers.contains("DEM")) {
                    syncLayerChange("demLayer");
                    aiAnalyses.put("DEM", fetchAnalysis(isAiEnabled, "DEM", lat, lon, statusUpdater));
                    imgDem = captureSnapshotSync(IMG_DEM);
                }

                // 4. Вегетація
                File imgNdvi = null;
                if (selectedLayers.contains("NDVI")) {
                    syncLayerChange("ndviLayer");
                    aiAnalyses.put("NDVI", fetchAnalysis(isAiEnabled, "NDVI", lat, lon, statusUpdater));
                    imgNdvi = captureSnapshotSync(IMG_NDVI);
                }

                // 5. Супутник
                File imgSat = null;
                if (selectedLayers.contains("SATELLITE")) {
                    syncLayerChange("satellite");
                    aiAnalyses.put("RETROSPECTIVE", fetchAnalysis(isAiEnabled, "RETROSPECTIVE", lat, lon, statusUpdater));
                    imgSat = captureSnapshotSync(IMG_SATELLITE);
                }

                // 6. 3D Модель
                File img3d = null;
                if (selectedLayers.contains("3D")) {
                    updateStatus(statusUpdater, "Генерація 3D-моделі...");
                    img3d = capture3DModelSnapshot(boundaries, elevation);
                }

                // 7. Hillshade / Slope
                File imgSlope = null;
                if (selectedLayers.contains("SLOPE")) {
                    syncLayerChange("slopeLayer");
                    aiAnalyses.put("SLOPE", fetchAnalysis(isAiEnabled, "SLOPE", lat, lon, statusUpdater));
                    imgSlope = captureSnapshotSync(IMG_SLOPE);
                }

                // --- БЛОК 3: ФІНАЛЬНА ЗБІРКА PDF ---
                long duration = (System.currentTimeMillis() - totalStartTime) / 1000;
                updateStatus(statusUpdater, "Збірка комплексного звіту... (" + duration + "с)");

                validateAnalyses();

                final File fScheme           = imgScheme;
                final File fTerrain          = imgTerrain;
                final File fDem              = imgDem;
                final File fNdvi             = imgNdvi;
                final File fSat              = imgSat;
                final File f3d               = img3d;
                final File fSlope            = imgSlope;
                final File fWeatherChart     = weatherChartFile;
                final File fInfraAnnotated   = imgInfraAnnotated;
                final JsonObject fGeoAddress = geoAddress;

                Platform.runLater(() -> {
                    try {
                        pdfService.generateReportExtended(pdfPath, title, area, priceUah, priceUsd,
                                elevation, finalSuitability, boundaries,
                                fScheme, fTerrain, fDem, fNdvi, fSat, f3d,
                                fSlope, fWeatherChart, fInfraAnnotated,
                                aiAnalyses, climateData, infraDetails, dayLength, countryContext, fGeoAddress);

                        finalizeAll(pdfPath, statusUpdater, fScheme, fTerrain, fDem, fNdvi, fSat, f3d, fSlope, fWeatherChart, fInfraAnnotated);
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
        updateStatus(statusUpdater, "AI аналіз: " + type + "...");
        return switch (type) {
            case "INFRASTRUCTURE" -> geminiService.getInfrastructureAnalysis(lat, lon);
            case "TERRAIN" -> geminiService.getTerrainAnalysis(lat, lon);
            case "DEM" -> geminiService.getDemAnalysis(lat, lon);
            case "NDVI" -> geminiService.getNdviAnalysis(lat, lon);
            case "RETROSPECTIVE" -> geminiService.getSatelliteRetrospective(lat, lon);
            case "SLOPE"         -> geminiService.getSlopeAnalysis(lat, lon);
            default -> "Дані відсутні.";
        };
    }

    private void updateStatus(Consumer<String> updater, String msg) {
        Platform.runLater(() -> updater.accept(msg));
    }

    private File captureWeatherChartSync(JsonObject dailyData) throws Exception {
        CompletableFuture<File> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.canvas.Canvas canvas = WeatherChartGenerator.createChart(dailyData);
                javafx.scene.image.WritableImage image = canvas.snapshot(null, null);
                File file = new File(IMG_WEATHER_CHART);
                file.getParentFile().mkdirs();
                ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
                future.complete(file);
            } catch (Exception e) {
                logger.warn("Не вдалося згенерувати кліматичний графік: {}", e.getMessage());
                future.complete(null);
            }
        });
        return future.get();
    }

    private File capture3DModelSnapshot(List<Coordinate> boundaries, double elevation) throws Exception {
        CompletableFuture<File> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                javafx.scene.canvas.Canvas canvas = LandParcel3dVisualizer.create3dPlot(boundaries, elevation, 1100, 660);
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
     * Зменшує зум на 2 рівні перед серією скріншотів та очікує завантаження тайлів.
     */
    private void zoomOutForCapture() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript(
                        "window.tileLoadComplete = false; window.tileLoadingCount = 0;" +
                        "map.setZoom(Math.max(10, map.getZoom() - 1));");
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        waitForTilesSync();
    }

    /**
     * Перемикає шар та очікує доки всі тайли повністю завантажаться.
     * Polling замість фіксованого delay: перевіряє window.tileLoadComplete кожні 300мс,
     * з таймаутом report.layer.switch.delay.ms як страховкою.
     */
    private void syncLayerChange(String layerVarName) throws Exception {
        int timeoutMs = ConfigManager.getIntProperty("report.layer.switch.delay.ms");
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript("showLayer('" + layerVarName + "');");
                pollTilesReady(latch, System.currentTimeMillis(), timeoutMs);
            } catch (Exception e) {
                latch.countDown();
            }
        });
        latch.await();
    }

    /**
     * Очікує завантаження тайлів поточного шару перед першим скріншотом (без зміни шару).
     */
    private void waitForTilesSync() throws Exception {
        int timeoutMs = ConfigManager.getIntProperty("report.layer.switch.delay.ms");
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> pollTilesReady(latch, System.currentTimeMillis(), timeoutMs));
        latch.await();
    }

    /**
     * Рекурсивний polling на FX-потоці: перевіряє window.tileLoadComplete кожні 300мс.
     * Завершується або при tileLoadComplete==true, або при перевищенні таймауту.
     */
    private void pollTilesReady(CountDownLatch latch, long startMs, int timeoutMs) {
        if (System.currentTimeMillis() - startMs >= timeoutMs) {
            logger.warn("Таймаут очікування тайлів ({}мс), продовжуємо", timeoutMs);
            latch.countDown();
            return;
        }
        try {
            boolean ready = Boolean.TRUE.equals(webView.getEngine().executeScript("!!window.tileLoadComplete"));
            if (ready) {
                latch.countDown();
                return;
            }
        } catch (Exception e) {
            latch.countDown();
            return;
        }
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
        pause.setOnFinished(e -> pollTilesReady(latch, startMs, timeoutMs));
        pause.play();
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

    private void closePopupSync() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { webView.getEngine().executeScript("map.closePopup();"); } catch (Exception ignored) {}
            latch.countDown();
        });
        latch.await();
        Thread.sleep(450);  // дати WebView час перемалювати без попапу
    }

    private void openPopupSync() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { webView.getEngine().executeScript(
                    "if(window.plotLayer && window.plotLayer.openPopup) window.plotLayer.openPopup();");
            } catch (Exception ignored) {}
            latch.countDown();
        });
        latch.await();
    }

    private void validateAnalyses() {
        String[] keys = {"INFRASTRUCTURE", "TERRAIN", "DEM", "NDVI", "RETROSPECTIVE", "SLOPE"};
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