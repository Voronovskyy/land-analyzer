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

    // Шляхи до тимчасових файлів
    private static final String TEMP_PATH_SCHEME = "Reports/temp_scheme.png";
    private static final String TEMP_PATH_TERRAIN = "Reports/temp_terrain.png";
    private static final String TEMP_PATH_DEM = "Reports/temp_dem.png";
    private static final String TEMP_PATH_NDVI = "Reports/temp_ndvi.png";
    private static final String TEMP_PATH_SLOPE = "Reports/temp_slope.png";
    private static final String TEMP_PATH_HYDRO = "Reports/temp_hydro.png";
    private static final String TEMP_PATH_SATELLITE = "Reports/temp_satellite.png";

    private final WebView webView;
    private final PdfReportService pdfService;
    private static final int LOAD_DELAY_MS = 5000;

    public ReportCoordinator(WebView webView) {
        this.webView = webView;
        this.pdfService = new PdfReportService();
    }

    public void runReportingSequence(String pdfPath, String title, String area,
                                     String priceUah, String priceUsd,
                                     double elevation, double suitability,
                                     List<Coordinate> boundaries,
                                     Consumer<String> statusUpdater) {

        // КРОК 1: СХЕМА (OSM)
        statusUpdater.accept("1/5: Знімок схеми...");
        File imgScheme = captureSnapshot(TEMP_PATH_SCHEME);

        // КРОК 2: РЕЛЬЄФ (Esri Topo)
        switchToLayerAndCapture("terrainGroup", TEMP_PATH_TERRAIN, "2/5: Аналіз рельєфу", statusUpdater, () -> {

            // КРОК 3: МОДЕЛЬ ВИСОТ (DEM)
            switchToLayerAndCapture("demLayer", TEMP_PATH_DEM, "3/5: Модель висот", statusUpdater, () -> {

                // КРОК 4: ВЕГЕТАЦІЯ (NDVI)
                switchToLayerAndCapture("ndviLayer", TEMP_PATH_NDVI, "4/5: Індекс вегетації", statusUpdater, () -> {

                    // КРОК 5: СУПУТНИК (Esri Satellite)
                    switchToLayerAndCapture("satellite", TEMP_PATH_SATELLITE, "5/5: Супутниковий знімок", statusUpdater, () -> {

                        // ФІНАЛЬНИЙ ЕТАП: ЗБІРКА PDF
                        statusUpdater.accept("Збірка PDF звіту...");
                        try {
                            File imgTerrain = new File(TEMP_PATH_TERRAIN);
                            File imgDem = new File(TEMP_PATH_DEM);
                            File imgNdvi = new File(TEMP_PATH_NDVI);
                            File imgSat = new File(TEMP_PATH_SATELLITE);

                            // Викликаємо метод генерації звіту (переконайтеся, що PdfReportService теж оновлено під 5 картинок)
                            pdfService.generateReport(pdfPath, title, area, priceUah, priceUsd,
                                    elevation, suitability, boundaries,
                                    imgScheme, imgTerrain, imgDem, imgNdvi, imgSat);

                            // Очищення та відкриття
                            finalizeAll(pdfPath, statusUpdater, imgScheme, imgTerrain, imgDem, imgNdvi, imgSat);

                        } catch (Exception e) {
                            logger.error("Критична помилка під час фіналізації звіту", e);
                            statusUpdater.accept("Помилка при створенні PDF!");
                        }
                    });
                });
            });
        });
    }

    private void switchToLayerAndCapture(String layerVarName, String path, String status,
                                         Consumer<String> statusUpdater, Runnable next) {
        // Оновлюємо текст у прогрес-барі інтерфейсу
        statusUpdater.accept(status + "...");

        Platform.runLater(() -> {
            try {
                // Викликаємо JS функцію setActiveLayer, передаючи назву змінної шару
                webView.getEngine().executeScript("setActiveLayer(" + layerVarName + ");");
            } catch (Exception e) {
                logger.error("JS Error for layer {}: {}", layerVarName, e.getMessage());
            }
        });

        // Чекаємо завантаження тайлів перед знімком
        CompletableFuture.delayedExecutor(LOAD_DELAY_MS, TimeUnit.MILLISECONDS)
                .execute(() -> Platform.runLater(() -> {
                    captureSnapshot(path);
                    next.run(); // Запускаємо наступний крок у ланцюжку
                }));
    }

    private void finalizeAll(String path, Consumer<String> statusUpdater, File... files) {
        // Повертаємо карту до початкового стану (OSM)
        webView.getEngine().executeScript("setActiveLayer(osm);");

        // Видаляємо лише ті 5 файлів, що були передані
        for (File f : files) {
            if (f != null && f.exists()) {
                f.delete();
            }
        }

        statusUpdater.accept("Звіт успішно створено!");
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(new File(path));
            } catch (Exception e) {
                logger.error("Не вдалося відкрити PDF файл", e);
            }
        }
    }

    private File captureSnapshot(String path) {
        try {
            WritableImage image = webView.snapshot(null, null);
            File file = new File(path);
            ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
            return file;
        } catch (Exception e) {
            return null;
        }
    }
}