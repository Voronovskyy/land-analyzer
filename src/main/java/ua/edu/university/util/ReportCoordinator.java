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
    private static final int SATELLITE_LOAD_DELAY_MS = 2500;
    private static final String TEMP_PATH_SCHEME = "Reports/temp_scheme.png";
    private static final String TEMP_PATH_SATELLITE = "Reports/temp_satellite.png";
    private final WebView webView;
    private final PdfReportService pdfService;

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
    }

    /**
     * Запускає послідовність створення звіту: Схема -> Супутник -> PDF
     */
    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability, // НОВЕ
                                     List<Coordinate> boundaries,         // НОВЕ
                                     Consumer<String> statusUpdater) {

        statusUpdater.accept("Підготовка: Знімок схеми...");
        File schemeImg = captureSnapshot(TEMP_PATH_SCHEME);

        statusUpdater.accept("Завантаження супутникових даних...");
        switchToSatelliteLayer();

        // Передаємо нові дані в наступний метод очікування
        waitAndFinalizeReport(pdfPath, title, area, priceUah, priceUsd,
                elevation, suitability, boundaries,
                schemeImg, statusUpdater);
    }

    private void waitAndFinalizeReport(String pdfPath, String title, String area,
                                       String priceUah, String priceUsd,
                                       double elevation, double suitability,
                                       List<Coordinate> boundaries,
                                       File schemeImg, Consumer<String> statusUpdater) {

        CompletableFuture.delayedExecutor(SATELLITE_LOAD_DELAY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> Platform.runLater(() -> {
                    try {
                        statusUpdater.accept("Фіксація: Знімок супутника...");
                        File satImg = captureSnapshot(TEMP_PATH_SATELLITE);

                        statusUpdater.accept("Генерація документу PDF...");

                        pdfService.generateReport(
                                pdfPath, title, area, priceUah, priceUsd,
                                elevation, suitability, boundaries,
                                schemeImg, satImg
                        );

                        finalizeProcess(pdfPath, schemeImg, satImg, statusUpdater);

                    } catch (Exception e) {
                        logger.error("Помилка під час фіналізації звіту", e);
                        statusUpdater.accept("Помилка створення звіту!");
                    }
                }));
    }

    private void finalizeProcess(String pdfPath, File img1, File img2, Consumer<String> statusUpdater) {
        switchToOsmLayer();
        deleteFile(img1);
        deleteFile(img2);

        File finalPdf = new File(pdfPath);
        statusUpdater.accept("Звіт готовий: " + finalPdf.getName());
        autoOpenFile(finalPdf);
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