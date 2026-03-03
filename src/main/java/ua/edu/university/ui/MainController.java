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
            resultLabel.setText("Будь ласка, введіть адресу або кадастровий номер");
            return;
        }

        setUIProcessing(true);
        resultLabel.setText("Пошук об'єкта...");
        this.lastSearchResult = null;

        CompletableFuture.supplyAsync(() -> performSearch(input))
                .thenAccept(coordinate -> Platform.runLater(() -> {
                    setUIProcessing(false);
                    if (coordinate != null) {
                        updateUIWithResult(coordinate);
                        resultLabel.setText("Об'єкт знайдено успішно!");
                    } else {
                        resultLabel.setText("Помилка: Об'єкт не знайдено.");
                        addressInputField.requestFocus();
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setUIProcessing(false);
                        resultLabel.setText("Сталася системна помилка.");
                    });
                    return null;
                });
    }

    /**
     * НОВИЙ МЕТОД: Тільки для візуалізації об'єкта в окремому вікні
     */
    @FXML
    private void handleShowVisualization() {
        if (lastSearchResult == null) {
            resultLabel.setText("Спочатку знайдіть об'єкт на карті");
            return;
        }

        // Готуємо координати (та сама логіка з GeoJSON або квадратом)
        List<Coordinate> boundaries = prepareBoundaries();
        String input = addressInputField.getText().trim();

        Platform.runLater(() -> {
            try {
                logger.info("Відкриття вікна 3D візуалізації...");
                Land3DView.show(boundaries, lastSearchResult.getAverageElevation(), input);
            } catch (Exception e) {
                logger.error("Помилка візуалізації: ", e);
            }
        });
    }

    /**
     * ОНОВЛЕНИЙ МЕТОД ГЕНЕРАЦІЇ: Тепер тільки звіт
     */
    @FXML
    private void handleGenerateReport() {
        if (lastSearchResult == null) return;

        String input = addressInputField.getText().trim();
        List<Coordinate> boundaries = prepareBoundaries(); // Отримуємо ті ж дані для PDF

        reportButton.setDisable(true);
        String pdfPath = FileUtil.generateReportPath(input);

        String areaText = String.format(Locale.US, "%.4f га", currentArea / 10000);
        String priceUahStr = String.format("%,.2f ₴", lastUahPrice);
        String priceUsdStr = String.format("$%,.0f", lastUsdPrice);

        ReportCoordinator coordinator = new ReportCoordinator(mapWebView);

        coordinator.runReportingSequence(
                pdfPath,
                input,
                areaText,
                priceUahStr,
                priceUsdStr,
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

    /**
     * Винесена спільна логіка підготовки меж, щоб не дублювати код
     */
    private List<Coordinate> prepareBoundaries() {
        List<Coordinate> boundaries = lastSearchResult.getBoundaries();

        if ((boundaries == null || boundaries.isEmpty()) && lastSearchResult.getGeoJson() != null) {
            boundaries = parseGeoJsonToCoordinates(lastSearchResult.getGeoJson());
            lastSearchResult.setBoundaries(boundaries);
        }

        if (boundaries == null || boundaries.isEmpty()) {
            double lat = lastSearchResult.getLatitude();
            double lon = lastSearchResult.getLongitude();
            double offset = 0.0006;
            boundaries = java.util.List.of(
                    new Coordinate(lat + offset, lon - offset),
                    new Coordinate(lat + offset, lon + offset),
                    new Coordinate(lat - offset, lon + offset),
                    new Coordinate(lat - offset, lon - offset)
            );
            lastSearchResult.setBoundaries(boundaries);
        }
        return boundaries;
    }

    /**
     * Допоміжний метод для швидкого парсингу GeoJSON без сторонніх бібліотек.
     * Підходить для PhD модуля, де потрібна швидка обробка стандартних полігонів.
     */
    private List<Coordinate> parseGeoJsonToCoordinates(String geoJson) {
        List<Coordinate> coords = new java.util.ArrayList<>();
        try {
            // Знаходимо масив координат у рядку GeoJSON
            String searchPattern = "\"coordinates\":[[[";
            if (geoJson.contains(searchPattern)) {
                String rawCoords = geoJson.split("\\[\\[\\[")[1].split("\\]\\]\\]")[0];
                String[] pairs = rawCoords.split("\\],\\[");
                for (String pair : pairs) {
                    String[] parts = pair.replace("[", "").replace("]", "").split(",");
                    double lon = Double.parseDouble(parts[0]);
                    double lat = Double.parseDouble(parts[1]);
                    coords.add(new Coordinate(lat, lon));
                }
            }
        } catch (Exception e) {
            logger.error("Помилка парсингу GeoJSON: {}", e.getMessage());
        }
        return coords;
    }

    private Coordinate performSearch(String input) {
        try {
            if (input.matches("\\d{10}:\\d{2}:\\d{3}:\\d{4}")) {
                return cadastreApiService.getPlotByCadastralNumber(input);
            } else {
                return geoApiService.getCoordinates(input);
            }
        } catch (Exception e) {
            logger.error("Помилка під час пошуку", e);
            return null;
        }
    }

    private void updateUIWithResult(ua.edu.university.model.Coordinate coordinate) {
        if (coordinate != null) {
            this.lastSearchResult = coordinate;
            this.currentArea = (coordinate.getGeoJson() != null)
                    ? GeoAnalysisUtil.calculateAreaFromGeoJson(coordinate.getGeoJson())
                    : 0.0;

            double elevation = elevationApiService.getElevation(coordinate.getLatitude(), coordinate.getLongitude());
            coordinate.setAverageElevation(elevation);

            double score = (elevation >= 200 && elevation <= 450) ? 0.95 : 0.65;
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
                    coordinate.getAverageElevation(),
                    coordinate.getSuitabilityScore()
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