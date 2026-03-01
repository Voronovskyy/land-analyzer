package ua.edu.university.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.edu.university.api.CadastreApiService;
import ua.edu.university.api.ElevationApiService;
import ua.edu.university.api.ExchangeRateService;
import ua.edu.university.api.GeoApiService;
import ua.edu.university.model.Coordinate;
import ua.edu.university.util.*;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private final GeoApiService geoApiService = new GeoApiService();
    private final CadastreApiService cadastreApiService = new CadastreApiService();
    private final ExchangeRateService exchangeRateService = new ExchangeRateService();
    private final ElevationApiService elevationApiService = new ElevationApiService();
    private Coordinate lastSearchResult;
    private double lastRate = 0;
    private double currentArea = 0;
    private double lastUahPrice = 0;
    private double lastUsdPrice = 0;

    @FXML
    private TextField addressInputField;
    @FXML
    private Button searchButton;
    @FXML
    private Button reportButton;
    @FXML
    private Label resultLabel;
    @FXML
    private WebView mapWebView;

    @FXML
    public void initialize() {
        logger.info("Ініціалізація контролера та завантаження стартової карти...");
        double defLat = ConfigManager.getDoubleProperty("map.default.lat");
        double defLon = ConfigManager.getDoubleProperty("map.default.lon");
        int defZoom = ConfigManager.getIntProperty("map.default.zoom");
        double initialRate = exchangeRateService.getCurrentUsdRate();
        loadMap(defLat, defLon, defZoom, null, 0.0, 0.0, initialRate, 0.0, 0.0);
    }

    /**
     * Головний метод пошуку. Розділений на логіку запиту та логіку оновлення UI.
     */
    @FXML
    private void handleSearch() {
        String input = addressInputField.getText().trim();
        if (input.isEmpty()) return;
        setUIProcessing(true);
        resultLabel.setText("Пошук об'єкта...");
        CompletableFuture.supplyAsync(() -> performSearch(input))
                .thenAccept(coordinate -> Platform.runLater(() -> updateUIWithResult(coordinate)));
    }

    @FXML
    private void handleGenerateReport() {
        if (lastSearchResult == null) return;
        String input = addressInputField.getText().trim();
        String pdfPath = FileUtil.generateReportPath(input);
        String areaText = String.format(Locale.US, "%.4f га", currentArea / 10000);
        String priceUahStr = String.format("%,.2f ₴", lastUahPrice);
        String priceUsdStr = String.format("$%,.0f", lastUsdPrice);
        ReportCoordinator coordinator = new ReportCoordinator(mapWebView);
        reportButton.setDisable(true);

        coordinator.runReportingSequence(
                pdfPath,
                input,
                areaText,
                priceUahStr,
                priceUsdStr,
                lastSearchResult.getAverageElevation(),
                lastSearchResult.getSuitabilityScore(),
                lastSearchResult.getBoundaries(),
                status -> Platform.runLater(() -> {
                    resultLabel.setText(status);
                    if (status.contains("готовий") || status.contains("Помилка")) {
                        reportButton.setDisable(false);
                    }
                })
        );
    }

    private Coordinate performSearch(String input) {
        try {
            if (input.matches("\\d{10}:\\d{2}:\\d{3}:\\d{4}")) {
                return cadastreApiService.getPlotByCadastralNumber(input);
            } else {
                return geoApiService.getCoordinates(input);
            }
        } catch (Exception e) {
            logger.error("Помилка під час виконання пошукового запиту", e);
            return null;
        }
    }

    private void updateUIWithResult(Coordinate coordinate) {
        if (coordinate != null) {
            this.lastSearchResult = coordinate;
            this.currentArea = GeoAnalysisUtil.calculateAreaFromGeoJson(coordinate.getGeoJson());
            double elevation = elevationApiService.getElevation(coordinate.getLatitude(), coordinate.getLongitude());
            coordinate.setAverageElevation(elevation);

            // 3. Розрахунок придатності (Suitability)
            // Логіка: лісові культури найкраще ростуть на висотах 200-400м
            double score = (elevation >= 200 && elevation <= 450) ? 0.9 : 0.6;
            coordinate.setSuitabilityScore(score);
            this.lastRate = exchangeRateService.getCurrentUsdRate();
            this.lastUahPrice = LandAnalysisService.calculateUahPrice(currentArea);
            this.lastUsdPrice = LandAnalysisService.calculateUsdPrice(lastUahPrice, lastRate);
            loadMap(
                    coordinate.getLatitude(),
                    coordinate.getLongitude(),
                    16,
                    coordinate.getGeoJson(),
                    lastUahPrice,
                    lastUsdPrice,
                    lastRate,
                    coordinate.getAverageElevation() != null ? coordinate.getAverageElevation() : 0.0,
                    coordinate.getSuitabilityScore() != null ? coordinate.getSuitabilityScore() : 0.0
            );
        }
    }

    private void loadMap(double lat, double lon, int zoom, String geoJson, double uah, double usd, double rate, double elevation, double suitability) {
        String html = MapHtmlBuilder.build(lat, lon, zoom, geoJson, uah, usd, rate, elevation, suitability);
        mapWebView.getEngine().loadContent(html);
    }

    private void setUIProcessing(boolean isProcessing) {
        searchButton.setDisable(isProcessing);
        addressInputField.setDisable(isProcessing);
    }
}