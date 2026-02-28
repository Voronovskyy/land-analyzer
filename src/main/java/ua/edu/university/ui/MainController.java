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
    private Coordinate lastSearchResult;
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
        loadMap(defLat, defLon, defZoom, null, 0.0, 0.0, initialRate);
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
        String areaText = String.format(Locale.US, "%.4f га", currentArea / 10000);
        String priceUahStr = String.format("%,.2f ₴", lastUahPrice);
        String priceUsdStr = String.format("$%,.0f", lastUsdPrice);

        ReportCoordinator coordinator = new ReportCoordinator(mapWebView);
        coordinator.runReportingSequence(
                FileUtil.generateReportPath(addressInputField.getText()),
                addressInputField.getText(),
                areaText,
                priceUahStr,
                priceUsdStr,
                status -> resultLabel.setText(status)
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
            if (coordinate.getGeoJson() != null) {
                this.currentArea = GeoAnalysisUtil.calculateAreaFromGeoJson(coordinate.getGeoJson());
            }
            double lastRate = exchangeRateService.getCurrentUsdRate();
            this.lastUahPrice = LandAnalysisService.calculateUahPrice(currentArea);
            this.lastUsdPrice = LandAnalysisService.calculateUsdPrice(lastUahPrice, lastRate);
            int zoom = ConfigManager.getIntProperty("map.search.zoom");
            loadMap(coordinate.getLatitude(), coordinate.getLongitude(), zoom,
                    coordinate.getGeoJson(), lastUahPrice, lastUsdPrice, lastRate);
        }
    }

    private void loadMap(double lat, double lon, int zoom, String geoJson, double uah, double usd, double rate) {
        String html = MapHtmlBuilder.build(lat, lon, zoom, geoJson, uah, usd, rate);
        mapWebView.getEngine().loadContent(html);
    }

    private void setUIProcessing(boolean isProcessing) {
        searchButton.setDisable(isProcessing);
        addressInputField.setDisable(isProcessing);
    }
}