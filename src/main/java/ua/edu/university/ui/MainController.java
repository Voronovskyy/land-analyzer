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

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // Сервіси
    private final GeoApiService geoApiService = new GeoApiService();
    private final CadastreApiService cadastreApiService = new CadastreApiService();
    private final ExchangeRateService exchangeRateService = new ExchangeRateService();
    private final ElevationApiService elevationApiService = new ElevationApiService();

    // Стан даних
    private Coordinate lastSearchResult;
    private double lastRate = 0;
    private double currentArea = 0;
    private double lastUahPrice = 0;
    private double lastUsdPrice = 0;

    @FXML private TextField addressInputField;
    @FXML private Button searchButton;
    @FXML private Button reportButton;
    @FXML private Label resultLabel;
    @FXML private WebView mapWebView;

    @FXML
    public void initialize() {
        logger.info("Ініціалізація системи...");
        double defLat = ConfigManager.getDoubleProperty("map.default.lat");
        double defLon = ConfigManager.getDoubleProperty("map.default.lon");
        int defZoom = ConfigManager.getIntProperty("map.default.zoom");
        this.lastRate = exchangeRateService.getCurrentUsdRate();

        // Початкове завантаження карти
        loadMap(defLat, defLon, defZoom, null, null, 0.0, 0.0, lastRate, 0.0, 0.0);

        addressInputField.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                handleSearch();
            }
        });
    }

    @FXML
    private void handleSearch() {
        String input = addressInputField.getText().trim();
        if (input.isEmpty()) {
            resultLabel.setText("Введіть адресу, кадастр або точки через ';'");
            return;
        }

        setUIProcessing(true);
        resultLabel.setText("Пошук об'єкта...");
        this.lastSearchResult = null;

        CompletableFuture.supplyAsync(() -> {
                    try {
                        return performSearch(input);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenAccept(coordinate -> Platform.runLater(() -> {
                    setUIProcessing(false);
                    if (coordinate != null) {
                        updateUIWithResult(coordinate);
                        resultLabel.setText("Об'єкт знайдено!");
                    } else {
                        resultLabel.setText("Об'єкт не знайдено.");
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setUIProcessing(false);
                        resultLabel.setText("Помилка системи.");
                    });
                    return null;
                });
    }

    @FXML
    private void handleShowVisualization() {
        if (lastSearchResult == null) return;
        List<Coordinate> boundaries = prepareBoundaries();
        String title = addressInputField.getText().trim();

        Platform.runLater(() -> {
            try {
                Land3DView.show(boundaries, lastSearchResult.getAverageElevation(), title);
            } catch (Exception e) {
                logger.error("Помилка 3D візуалізації", e);
            }
        });
    }

    @FXML
    private void handleGenerateReport() {
        if (lastSearchResult == null) return;

        String input = addressInputField.getText().trim();
        List<Coordinate> boundaries = prepareBoundaries();
        reportButton.setDisable(true);

        String pdfPath = FileUtil.generateReportPath(input);
        String areaText = String.format(Locale.US, "%.4f га", currentArea / 10000);
        String priceUahStr = String.format("%,.2f ₴", lastUahPrice);
        String priceUsdStr = String.format("$%,.0f", lastUsdPrice);

        ReportCoordinator coordinator = new ReportCoordinator(mapWebView);
        coordinator.runReportingSequence(
                pdfPath, input, areaText, priceUahStr, priceUsdStr,
                lastSearchResult.getAverageElevation(),
                lastSearchResult.getSuitabilityScore(),
                boundaries,
                lastSearchResult.getLatitude(),
                lastSearchResult.getLongitude(),
                status -> Platform.runLater(() -> {
                    resultLabel.setText(status);
                    if (status.contains("готовий") || status.contains("Помилка")) {
                        reportButton.setDisable(false);
                    }
                })
        );
    }

    private Coordinate performSearch(String input) throws Exception {
        // 1. Ручне введення точок ( lat,lon; lat,lon ... )
        if (input.contains(",") && input.contains(";")) {
            return handleManualPointsInput(input);
        }
        // 2. Кадастровий номер
        if (input.matches("\\d{10}:\\d{2}:\\d{3}:\\d{4}")) {
            return cadastreApiService.getPlotByCadastralNumber(input);
        }
        // 3. Звичайний пошук адреси
        return geoApiService.getCoordinates(input);
    }

    private void updateUIWithResult(Coordinate coordinate) {
        this.lastSearchResult = coordinate;

        // Розрахунок площі
        if (coordinate.getBoundaries() != null && !coordinate.getBoundaries().isEmpty()) {
            this.currentArea = GeoAnalysisUtil.calculateAreaFromCoordinates(coordinate.getBoundaries());
        } else if (coordinate.getGeoJson() != null) {
            this.currentArea = GeoAnalysisUtil.calculateAreaFromGeoJson(coordinate.getGeoJson());
        } else {
            this.currentArea = 0.0;
        }

        // Аналітика та висота
        double elevation = elevationApiService.getElevation(coordinate.getLatitude(), coordinate.getLongitude());
        coordinate.setAverageElevation(elevation);
        coordinate.setSuitabilityScore((elevation >= 200 && elevation <= 450) ? 0.95 : 0.65);

        // Ціни
        this.lastRate = exchangeRateService.getCurrentUsdRate();
        this.lastUahPrice = LandAnalysisService.calculateUahPrice(currentArea);
        this.lastUsdPrice = LandAnalysisService.calculateUsdPrice(lastUahPrice, lastRate);

        // Оновлення карти
        loadMap(coordinate.getLatitude(), coordinate.getLongitude(), 16,
                coordinate.getGeoJson(), coordinate.getBoundaries(),
                lastUahPrice, lastUsdPrice, lastRate,
                coordinate.getAverageElevation(), coordinate.getSuitabilityScore());
    }

    private Coordinate handleManualPointsInput(String input) {
        try {
            List<Coordinate> points = new java.util.ArrayList<>();
            String[] pairs = input.split(";");
            double sumLat = 0, sumLon = 0;

            for (String pair : pairs) {
                String[] pts = pair.trim().split(",");
                if (pts.length == 2) {
                    double lat = Double.parseDouble(pts[0].trim());
                    double lon = Double.parseDouble(pts[1].trim());
                    points.add(new Coordinate(lat, lon));
                    sumLat += lat; sumLon += lon;
                }
            }
            if (points.size() < 3) return null;

            Coordinate manual = new Coordinate(sumLat / points.size(), sumLon / points.size(), null);
            manual.setBoundaries(points);
            return manual;
        } catch (Exception e) { return null; }
    }

    private List<Coordinate> prepareBoundaries() {
        if (lastSearchResult.getBoundaries() != null && !lastSearchResult.getBoundaries().isEmpty()) {
            return lastSearchResult.getBoundaries();
        }
        if (lastSearchResult.getGeoJson() != null) {
            List<Coordinate> parsed = parseGeoJsonToCoordinates(lastSearchResult.getGeoJson());
            lastSearchResult.setBoundaries(parsed);
            return parsed;
        }
        // Дефолтний квадрат, якщо даних немає зовсім
        double l = lastSearchResult.getLatitude(), o = lastSearchResult.getLongitude(), d = 0.0006;
        return List.of(new Coordinate(l+d, o-d), new Coordinate(l+d, o+d), new Coordinate(l-d, o+d), new Coordinate(l-d, o-d));
    }

    private List<Coordinate> parseGeoJsonToCoordinates(String geoJson) {
        List<Coordinate> coords = new java.util.ArrayList<>();
        try {
            if (geoJson.contains("\"coordinates\":[[[")) {
                String raw = geoJson.split("\\[\\[\\[")[1].split("\\]\\]\\]")[0];
                for (String p : raw.split("\\],\\[")) {
                    String[] pts = p.replace("[", "").replace("]", "").split(",");
                    coords.add(new Coordinate(Double.parseDouble(pts[1]), Double.parseDouble(pts[0])));
                }
            }
        } catch (Exception ignored) {}
        return coords;
    }

    private void loadMap(double lat, double lon, int zoom, String geoJson, List<Coordinate> boundaries,
                         double uah, double usd, double rate, double elevation, double suitability) {
        String html = MapHtmlBuilder.build(lat, lon, zoom, geoJson, boundaries, uah, usd, rate, elevation, suitability);
        mapWebView.getEngine().loadContent(html);
    }

    private void setUIProcessing(boolean isProcessing) {
        searchButton.setDisable(isProcessing);
        addressInputField.setDisable(isProcessing);
    }
}