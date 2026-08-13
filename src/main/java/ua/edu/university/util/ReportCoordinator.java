package ua.edu.university.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.api.*;
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
 * Оркеструє повний конвеєр генерації PDF-звіту:
 * збір даних з усіх API (фаза 1), послідовне захоплення знімків
 * картографічних шарів через WebView (фаза 2) та збірка PDF (фаза 3).
 * Використовує {@code CompletableFuture} для асинхронного запуску
 * і {@code CountDownLatch} для синхронізації знімків на FX-потоці.
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
    private static final String IMG_SLOPE = TEMP_DIR + "slope.png";
    private static final String IMG_WEATHER_CHART = TEMP_DIR + "weather_chart.png";
    private static final String IMG_INFRA_ANNOTATED = TEMP_DIR + "infra_annotated.png";
    private static final String IMG_ANNUAL_CHART = TEMP_DIR + "annual_chart.png";
    private final WebView webView;
    private final PdfReportService pdfService;
    private final ClaudeAnalysisService geminiService;
    private final WeatherApiService weatherService;
    private final OverpassApiService overpassService;
    private final InsolationApiService insolationService;
    private final CountryContextService countryService;
    private final GeoApiService geoApiService;
    private final ElevationApiService elevationApiService;

    private final Map<String, String> aiAnalyses = new HashMap<>();

    // ─── Контейнери даних ────────────────────────────────────────

    private record ReportData(
            JsonObject climate, JsonObject infra,
            String dayLength, JsonObject country, JsonObject geoAddress,
            JsonArray poiData, double[] cornerElevations,
            File weatherChart, File annualChart, double suitability) {
    }

    private record CapturedLayers(
            File scheme, File terrain, File dem, File ndvi,
            File satellite, File model3d, File slope, File infraAnnotated) {
    }

    // ─── Конструктор ─────────────────────────────────────────────

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
        this.geminiService = new ClaudeAnalysisService();
        this.weatherService = new WeatherApiService();
        this.overpassService = new OverpassApiService();
        this.insolationService = new InsolationApiService();
        this.countryService = new CountryContextService();
        this.geoApiService = new GeoApiService();
        this.elevationApiService = new ElevationApiService();
    }

    // ─── Точка входу ─────────────────────────────────────────────

    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability,
                                     List<Coordinate> boundaries,
                                     double lat, double lon,
                                     Set<String> selectedLayers,
                                     boolean isAiEnabled,
                                     Consumer<String> statusUpdater) {
        aiAnalyses.clear();
        long startTime = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            try {
                ReportData data = collectApiData(lat, lon, boundaries, elevation, statusUpdater);
                String facts = PlotFactsBuilder.build(area, elevation, data.cornerElevations(),
                        boundaries, data.geoAddress(), data.infra(), data.climate(),
                        data.poiData(), data.dayLength());
                CapturedLayers layers = captureMapLayers(lat, lon, elevation, boundaries,
                        selectedLayers, isAiEnabled, data.infra(), facts, statusUpdater);

                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                updateStatus(statusUpdater, "Збірка комплексного звіту... (" + elapsed + "с)");
                validateAnalyses();

                Platform.runLater(() -> assemblePdf(pdfPath, title, area, priceUah, priceUsd,
                        elevation, boundaries, data, layers, statusUpdater));

            } catch (Exception e) {
                logger.error("Критичний збій: {}", e.getMessage());
                updateStatus(statusUpdater, "Збій: " + e.getMessage());
            }
        });
    }

    // ─── Блок 1: Збір даних з API ────────────────────────────────

    private ReportData collectApiData(double lat, double lon, List<Coordinate> boundaries,
                                      double elevation, Consumer<String> statusUpdater) throws Exception {
        updateStatus(statusUpdater, "Збір кліматичних показників (Open-Meteo)...");
        JsonObject climateData = weatherService.getClimateStatistics(lat, lon);

        updateStatus(statusUpdater, "Завантаження даних за останній місяць...");
        JsonObject monthlyData = weatherService.getMonthlyData(lat, lon);

        updateStatus(statusUpdater, "Аналіз інженерних мереж (Overpass API)...");
        JsonObject infraDetails = overpassService.getInfrastructureDetails(lat, lon);

        updateStatus(statusUpdater, "Розрахунок інсоляції та регіональних даних...");
        String dayLength = insolationService.getDayLength(lat, lon);
        JsonObject country = countryService.getRegionalContext();

        updateStatus(statusUpdater, "Визначення адресних даних...");
        JsonObject geoAddress = geoApiService.getReverseGeocode(lat, lon);

        updateStatus(statusUpdater, "Аналіз мікрооточення (POI 2 км)...");
        JsonArray poiData = overpassService.getNearbyPOIs(lat, lon);

        updateStatus(statusUpdater, "Висоти кутів ділянки...");
        double[] cornerElevations = fetchCornerElevations(boundaries);

        updateStatus(statusUpdater, "Річний кліматичний архів...");
        JsonObject annualMonthly = weatherService.getYearlyMonthlyData(lat, lon);
        File annualChartFile = (annualMonthly != null)
                ? MonthlyClimateChart.generate(annualMonthly, IMG_ANNUAL_CHART) : null;

        double finalSuitability = SuitabilityCalculator.calculate(elevation, infraDetails, climateData);

        File weatherChartFile = null;
        if (monthlyData != null) {
            updateStatus(statusUpdater, "Генерація кліматичного графіку...");
            weatherChartFile = captureWeatherChartSync(monthlyData);
        }

        return new ReportData(climateData, infraDetails, dayLength, country, geoAddress,
                poiData, cornerElevations, weatherChartFile, annualChartFile, finalSuitability);
    }

    // ─── Блок 2: Знімки картографічних шарів ────────────────────

    private CapturedLayers captureMapLayers(double lat, double lon, double elevation,
                                            List<Coordinate> boundaries,
                                            Set<String> selectedLayers, boolean isAiEnabled,
                                            JsonObject infraDetails, String facts,
                                            Consumer<String> statusUpdater) throws Exception {
        zoomOutForCapture();

        File imgScheme = null, imgInfraAnnotated = null;
        if (selectedLayers.contains("SCHEME")) {
            aiAnalyses.put("INFRASTRUCTURE", fetchAnalysis(isAiEnabled, "INFRASTRUCTURE", lat, lon, facts, statusUpdater));
            waitForTilesSync();
            imgScheme = captureSnapshotSync(IMG_SCHEME);

            closePopupSync();
            File imgClean = captureSnapshotSync(IMG_INFRA_ANNOTATED);
            openPopupSync();
            if (imgClean != null) {
                updateStatus(statusUpdater, "Анотація транспортної доступності...");
                imgInfraAnnotated = InfraAnnotator.annotate(imgClean, lat, lon, infraDetails, IMG_INFRA_ANNOTATED);
            }
        }

        File imgTerrain = null;
        if (selectedLayers.contains("TERRAIN")) {
            syncLayerChange("terrainGroup");
            aiAnalyses.put("TERRAIN", fetchAnalysis(isAiEnabled, "TERRAIN", lat, lon, facts, statusUpdater));
            imgTerrain = captureSnapshotSync(IMG_TERRAIN);
        }

        File imgDem = null;
        if (selectedLayers.contains("DEM")) {
            syncLayerChange("demLayer");
            aiAnalyses.put("DEM", fetchAnalysis(isAiEnabled, "DEM", lat, lon, facts, statusUpdater));
            imgDem = captureSnapshotSync(IMG_DEM);
        }

        File imgNdvi = null;
        if (selectedLayers.contains("NDVI")) {
            syncLayerChange("ndviLayer");
            aiAnalyses.put("NDVI", fetchAnalysis(isAiEnabled, "NDVI", lat, lon, facts, statusUpdater));
            imgNdvi = captureSnapshotSync(IMG_NDVI);
        }

        File imgSat = null;
        if (selectedLayers.contains("SATELLITE")) {
            syncLayerChange("satellite");
            aiAnalyses.put("RETROSPECTIVE", fetchAnalysis(isAiEnabled, "RETROSPECTIVE", lat, lon, facts, statusUpdater));
            imgSat = captureSnapshotSync(IMG_SATELLITE);
        }

        File img3d = null;
        if (selectedLayers.contains("3D")) {
            updateStatus(statusUpdater, "Генерація 3D-моделі...");
            img3d = capture3DModelSnapshot(boundaries, elevation);
        }

        File imgSlope = null;
        if (selectedLayers.contains("SLOPE")) {
            syncLayerChange("slopeLayer");
            aiAnalyses.put("SLOPE", fetchAnalysis(isAiEnabled, "SLOPE", lat, lon, facts, statusUpdater));
            imgSlope = captureSnapshotSync(IMG_SLOPE);
        }

        return new CapturedLayers(imgScheme, imgTerrain, imgDem, imgNdvi, imgSat, img3d, imgSlope, imgInfraAnnotated);
    }

    // ─── Блок 3: Збірка PDF та завершення ────────────────────────

    private void assemblePdf(String pdfPath, String title, String area,
                             String priceUah, String priceUsd,
                             double elevation, List<Coordinate> boundaries,
                             ReportData data, CapturedLayers layers,
                             Consumer<String> statusUpdater) {
        try {
            pdfService.generateReportExtended(
                    pdfPath, title, area, priceUah, priceUsd,
                    elevation, data.suitability(), boundaries,
                    layers.scheme(), layers.terrain(), layers.dem(),
                    layers.ndvi(), layers.satellite(), layers.model3d(),
                    layers.slope(), data.weatherChart(), layers.infraAnnotated(),
                    aiAnalyses, data.climate(), data.infra(), data.dayLength(),
                    data.country(), data.geoAddress(),
                    data.poiData(), data.cornerElevations(), data.annualChart());

            finalizeAll(pdfPath, statusUpdater,
                    layers.scheme(), layers.terrain(), layers.dem(), layers.ndvi(),
                    layers.satellite(), layers.model3d(), layers.slope(), layers.infraAnnotated(),
                    data.weatherChart(), data.annualChart());
        } catch (Exception e) {
            logger.error("Помилка PDF: {}", e.getMessage());
            statusUpdater.accept("Помилка створення PDF!");
        }
    }

    // ─── AI аналіз ───────────────────────────────────────────────

    private String fetchAnalysis(boolean isAiEnabled, String type,
                                 double lat, double lon, String facts,
                                 Consumer<String> statusUpdater) {
        if (!isAiEnabled) return "[AI DISABLED] Технічний аналіз для " + type;
        updateStatus(statusUpdater, "AI аналіз: " + type + "...");
        return switch (type) {
            case "INFRASTRUCTURE" -> geminiService.getInfrastructureAnalysis(lat, lon, facts);
            case "TERRAIN" -> geminiService.getTerrainAnalysis(lat, lon, facts);
            case "DEM" -> geminiService.getDemAnalysis(lat, lon, facts);
            case "NDVI" -> geminiService.getNdviAnalysis(lat, lon, facts);
            case "RETROSPECTIVE" -> geminiService.getSatelliteRetrospective(lat, lon, facts);
            case "SLOPE" -> geminiService.getSlopeAnalysis(lat, lon, facts);
            default -> "Дані відсутні.";
        };
    }

    private void validateAnalyses() {
        for (String key : new String[]{"INFRASTRUCTURE", "TERRAIN", "DEM", "NDVI", "RETROSPECTIVE", "SLOPE"}) {
            aiAnalyses.putIfAbsent(key, "Дані наразі недоступні.");
        }
    }

    // ─── WebView синхронізація ────────────────────────────────────

    private void zoomOutForCapture() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript(
                        "window.tileLoadComplete = false; window.tileLoadingCount = 0;" +
                                "map.setZoom(Math.max(10, map.getZoom() - 1));");
            } catch (Exception e) {
                logger.debug("Не вдалося виконати zoom-out: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        waitForTilesSync();
    }

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

    private void waitForTilesSync() throws Exception {
        int timeoutMs = ConfigManager.getIntProperty("report.layer.switch.delay.ms");
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> pollTilesReady(latch, System.currentTimeMillis(), timeoutMs));
        latch.await();
    }

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
        javafx.animation.PauseTransition pause =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
        pause.setOnFinished(e -> pollTilesReady(latch, startMs, timeoutMs));
        pause.play();
    }

    private void closePopupSync() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript("map.closePopup();");
            } catch (Exception e) {
                logger.debug("Не вдалося закрити popup: {}", e.getMessage());
            }
            latch.countDown();
        });
        latch.await();
        Thread.sleep(450);
    }

    private void openPopupSync() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                webView.getEngine().executeScript(
                        "if(window.plotLayer && window.plotLayer.openPopup) window.plotLayer.openPopup();");
            } catch (Exception e) {
                logger.debug("Не вдалося відкрити popup: {}", e.getMessage());
            }
            latch.countDown();
        });
        latch.await();
    }

    // ─── Знімки ──────────────────────────────────────────────────

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
                javafx.scene.canvas.Canvas canvas =
                        LandParcel3dVisualizer.create3dPlot(boundaries, elevation, 1100, 660);
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

    // ─── Утиліти ─────────────────────────────────────────────────

    private double[] fetchCornerElevations(List<Coordinate> boundaries) {
        if (boundaries == null || boundaries.size() < 3) return null;
        int n = boundaries.size();
        List<Coordinate> corners = List.of(
                boundaries.get(0),
                boundaries.get(n / 4),
                boundaries.get(n / 2),
                boundaries.get(3 * n / 4));
        try {
            return elevationApiService.getMultipleElevations(corners);
        } catch (Exception e) {
            logger.warn("Corner elevations fetch failed: {}", e.getMessage());
            return null;
        }
    }

    private void updateStatus(Consumer<String> updater, String msg) {
        Platform.runLater(() -> updater.accept(msg));
    }

    private void finalizeAll(String path, Consumer<String> statusUpdater, File... files) {
        for (File f : files) {
            if (f != null && f.exists()) f.delete();
        }
        statusUpdater.accept("Готово! Звіт відкрито.");
        try {
            java.awt.Desktop.getDesktop().open(new File(path));
        } catch (Exception e) {
            logger.warn("Не вдалося відкрити PDF: {}", e.getMessage());
        }
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> System.exit(0));
            }
        }, ConfigManager.getIntProperty("report.final.exit.delay.ms"));
    }
}
