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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ReportCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(ReportCoordinator.class);
    private static final String TEMP_PATH_SCHEME = "Reports/temp_scheme.png";
    private static final String TEMP_PATH_SATELLITE = "Reports/temp_satellite.png";
    private static final String TEMP_PATH_TERRAIN = "Reports/temp_terrain.png";
    private final WebView webView;
    private final PdfReportService pdfService;
    private static final int LOAD_DELAY_MS = 5000;

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
    }

    /**
     * Запускає послідовність створення звіту: Схема -> Супутник -> PDF
     */
    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability,
                                     List<Coordinate> boundaries,
                                     Consumer<String> statusUpdater) {
        statusUpdater.accept("Підготовка: Знімок схеми...");
        File schemeImg = captureSnapshot(TEMP_PATH_SCHEME);

        // Крок 2: Перемикаємо на Рельєф
        statusUpdater.accept("Завантаження рельєфу (очікування 5 сек)...");
        webView.getEngine().executeScript("map.removeLayer(osm); terrainGroup.addTo(map);");

        CompletableFuture.delayedExecutor(LOAD_DELAY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> Platform.runLater(() -> {
                    File terrainImg = captureSnapshot(TEMP_PATH_TERRAIN);
                    // Крок 3: Перемикаємо на Супутник
                    statusUpdater.accept("Завантаження супутника (очікування 5 сек)...");
                    webView.getEngine().executeScript("map.removeLayer(terrainGroup); satellite.addTo(map);");

                    waitAndFinalize(pdfPath, title, area, priceUah, priceUsd,
                            elevation, suitability, boundaries,
                            schemeImg, terrainImg, statusUpdater);
                }));
    }

    private void waitAndFinalize(String pdfPath, String title, String area,
                                 String priceUah, String priceUsd,
                                 double elevation, double suitability,
                                 List<Coordinate> boundaries,
                                 File schemeImg, File terrainImg, Consumer<String> statusUpdater) {

        CompletableFuture.delayedExecutor(LOAD_DELAY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> Platform.runLater(() -> {
                    try {
                        statusUpdater.accept("Фіналізація звіту...");
                        File satImg = captureSnapshot(TEMP_PATH_SATELLITE);

                        pdfService.generateReport(pdfPath, title, area, priceUah, priceUsd,
                                elevation, suitability, boundaries,
                                schemeImg, terrainImg, satImg);

                        finalizeProcess(pdfPath, schemeImg, terrainImg, satImg, statusUpdater);
                    } catch (Exception e) {
                        logger.error("Finalization error", e);
                        statusUpdater.accept("Помилка при створенні PDF!");
                    }
                }));
    }

    private void finalizeProcess(String pdfPath, File img1, File img2, File img3, Consumer<String> statusUpdater) {
        webView.getEngine().executeScript("map.removeLayer(satellite); osm.addTo(map);");
        deleteFile(img1);
        deleteFile(img2);
        deleteFile(img3);
        statusUpdater.accept("Звіт готовий!");
        autoOpenFile(new File(pdfPath));
    }

    private void switchToSatelliteLayer() {
        webView.getEngine().executeScript("map.removeLayer(osm); satellite.addTo(map);");
    }

    private void switchToOsmLayer() {
        webView.getEngine().executeScript("map.removeLayer(satellite); osm.addTo(map);");
    }

    private File captureSnapshot(String path) {
        try {
            WritableImage image = webView.snapshot(null, null);
            File file = new File(path);
            ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
            logger.debug("Знімок збережено: {}", path);
            return file;
        } catch (Exception e) {
            logger.error("Помилка створення скріншота {}", path, e);
            return null;
        }
    }

    private void autoOpenFile(File file) {
        if (Desktop.isDesktopSupported() && file.exists()) {
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception e) {
                logger.error("Не вдалося відкрити PDF", e);
            }
        }
    }

    private void deleteFile(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                file.deleteOnExit();
            }
        }
    }
}