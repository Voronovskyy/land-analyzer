package ua.edu.university.web.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.edu.university.api.*;
import ua.edu.university.model.Coordinate;
import ua.edu.university.util.*;
import ua.edu.university.web.dto.ReportRequest;
import ua.edu.university.web.dto.SearchResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Веб-аналог ReportCoordinator: збирає дані, генерує графіки,
 * завантажує статичні карти і збирає PDF без залежностей від JavaFX.
 */
@Service
public class WebReportService {
    private static final Logger logger = LoggerFactory.getLogger(WebReportService.class);

    private final GeoApiService geoApiService = new GeoApiService();
    private final CadastreApiService cadastreApiService = new CadastreApiService();
    private final ExchangeRateService exchangeRateService = new ExchangeRateService();
    private final ElevationApiService elevationApiService = new ElevationApiService();
    private final DomRiaApiService domRiaService = new DomRiaApiService();
    private final WeatherApiService weatherService = new WeatherApiService();
    private final OverpassApiService overpassService = new OverpassApiService();
    private final InsolationApiService insolationService = new InsolationApiService();
    private final CountryContextService countryService = new CountryContextService();
    private final ClaudeAnalysisService claudeService = new ClaudeAnalysisService();
    private final PdfReportService pdfService = new PdfReportService();
    private final StaticMapService mapService;

    public WebReportService(StaticMapService mapService) {
        this.mapService = mapService;
    }

    // ── Search ────────────────────────────────────────────────────────────

    public SearchResponse search(String query) throws Exception {
        Coordinate coordinate = performSearch(query.trim());
        if (coordinate == null) return null;

        List<Coordinate> boundaries = resolveBoundaries(coordinate);
        double areaSqM = computeArea(coordinate, boundaries);

        double elevation = elevationApiService.getElevation(
                coordinate.getLatitude(), coordinate.getLongitude());
        coordinate.setAverageElevation(elevation);
        coordinate.setSuitabilityScore(SuitabilityCalculator.calculateFromElevation(elevation));

        double rate = exchangeRateService.getCurrentUsdRate();
        double uahPrice = LandAnalysisService.calculateUahPrice(areaSqM);
        double usdPrice = LandAnalysisService.calculateUsdPrice(uahPrice, rate);

        OptionalDouble market = domRiaService.getMarketPricePerSqM(
                coordinate.getLatitude(), coordinate.getLongitude());
        if (market.isPresent()) {
            uahPrice = market.getAsDouble() * areaSqM;
            usdPrice = LandAnalysisService.calculateUsdPrice(uahPrice, rate);
        }

        SearchResponse resp = new SearchResponse();
        resp.setLat(coordinate.getLatitude());
        resp.setLon(coordinate.getLongitude());
        resp.setAreaSqM(areaSqM);
        resp.setAreaHa(String.format(Locale.US, "%.4f га", areaSqM / 10000));
        resp.setElevation(elevation);
        resp.setSuitability(coordinate.getSuitabilityScore());
        resp.setPriceUah(String.format(Locale.US, "%,.2f ₴", uahPrice));
        resp.setPriceUsd(String.format(Locale.US, "$%,.0f", usdPrice));
        resp.setRate(rate);
        resp.setGeoJson(coordinate.getGeoJson());

        if (boundaries != null) {
            resp.setBoundaries(boundaries.stream()
                    .map(c -> new SearchResponse.LatLon(c.getLatitude(), c.getLongitude()))
                    .collect(Collectors.toList()));
        }
        return resp;
    }

    // ── Report generation ────────────────────────────────────────────────

    public byte[] generateReport(ReportRequest req) throws Exception {
        String tempDir = "Reports/temp/" + UUID.randomUUID() + "/";
        new File(tempDir).mkdirs();

        try {
            double lat = req.getLat();
            double lon = req.getLon();
            List<Coordinate> boundaries = toCoordinates(req.getBoundaries());

            // Phase 1: collect API data
            logger.info("Collecting API data for ({}, {})", lat, lon);
            JsonObject climateData = weatherService.getClimateStatistics(lat, lon);
            JsonObject monthlyData = weatherService.getMonthlyData(lat, lon);
            JsonObject infraDetails = overpassService.getInfrastructureDetails(lat, lon);
            String dayLength = insolationService.getDayLength(lat, lon);
            JsonObject country = countryService.getRegionalContext();
            JsonObject geoAddress = geoApiService.getReverseGeocode(lat, lon);
            JsonArray poiData = overpassService.getNearbyPOIs(lat, lon);
            double[] cornerElevations = fetchCornerElevations(boundaries);

            JsonObject annualMonthly = weatherService.getYearlyMonthlyData(lat, lon);
            File annualChart = annualMonthly != null
                    ? MonthlyClimateChart.generate(annualMonthly, tempDir + "annual.png") : null;

            File weatherChart = null;
            if (monthlyData != null) {
                weatherChart = WeatherChartGenerator.generateFile(
                        monthlyData.has("daily") ? monthlyData.getAsJsonObject("daily") : monthlyData,
                        tempDir + "weather.png");
            }

            double suitability = SuitabilityCalculator.calculate(req.getElevation(), infraDetails, climateData);

            // Phase 2: map images + AI analyses
            Set<String> layers = new HashSet<>(req.getLayers() != null ? req.getLayers() : List.of());
            Map<String, String> aiAnalyses = new HashMap<>();

            StaticMapService.PlotInfo plotInfo = new StaticMapService.PlotInfo(
                    req.getTitle(), req.getAreaHa(), req.getPriceUah(), req.getPriceUsd(),
                    req.getElevation(), lat, lon);

            File imgScheme = null, imgInfraAnnotated = null;
            if (layers.contains("SCHEME")) {
                logger.info("Generating SCHEME map image");
                imgScheme = mapService.generate(lat, lon, "SCHEME", boundaries, tempDir + "scheme.png", plotInfo);
                if (req.isAiEnabled()) {
                    aiAnalyses.put("INFRASTRUCTURE", claudeService.getInfrastructureAnalysis(lat, lon));
                }
                if (imgScheme != null) {
                    imgInfraAnnotated = InfraAnnotator.annotate(
                            imgScheme, lat, lon, infraDetails, tempDir + "infra_annotated.png");
                }
            }

            File imgTerrain = null;
            if (layers.contains("TERRAIN")) {
                logger.info("Generating TERRAIN map image");
                imgTerrain = mapService.generate(lat, lon, "TERRAIN", boundaries, tempDir + "terrain.png", plotInfo);
                if (req.isAiEnabled()) {
                    aiAnalyses.put("TERRAIN", claudeService.getTerrainAnalysis(lat, lon));
                }
            }

            File imgDem = null;
            if (layers.contains("DEM")) {
                logger.info("Generating DEM map image");
                imgDem = mapService.generate(lat, lon, "DEM", boundaries, tempDir + "dem.png", plotInfo);
                if (req.isAiEnabled()) {
                    aiAnalyses.put("DEM", claudeService.getDemAnalysis(lat, lon));
                }
            }

            File imgNdvi = null;
            if (layers.contains("NDVI")) {
                logger.info("Generating NDVI map image");
                imgNdvi = mapService.generate(lat, lon, "NDVI", boundaries, tempDir + "ndvi.png", plotInfo);
                if (req.isAiEnabled()) {
                    aiAnalyses.put("NDVI", claudeService.getNdviAnalysis(lat, lon));
                }
            }

            File imgSatellite = null;
            if (layers.contains("SATELLITE")) {
                logger.info("Generating SATELLITE map image");
                imgSatellite = mapService.generate(lat, lon, "SATELLITE", boundaries, tempDir + "satellite.png", plotInfo);
                if (req.isAiEnabled()) {
                    aiAnalyses.put("RETROSPECTIVE", claudeService.getSatelliteRetrospective(lat, lon));
                }
            }

            File imgSlope = null;
            if (layers.contains("SLOPE")) {
                logger.info("Generating SLOPE map image");
                imgSlope = mapService.generate(lat, lon, "SLOPE", boundaries, tempDir + "slope.png", plotInfo);
                if (req.isAiEnabled()) {
                    aiAnalyses.put("SLOPE", claudeService.getSlopeAnalysis(lat, lon));
                }
            }

            File img3d = LandParcel3dImageRenderer.generate(boundaries, req.getElevation(), 1100, 660, tempDir + "model3d.png");

            for (String key : new String[]{"INFRASTRUCTURE", "TERRAIN", "DEM", "NDVI", "RETROSPECTIVE", "SLOPE"}) {
                aiAnalyses.putIfAbsent(key, "Дані наразі недоступні.");
            }

            // Phase 3: assemble PDF
            logger.info("Assembling PDF report");
            String pdfPath = FileUtil.generateReportPath(req.getTitle() != null ? req.getTitle() : "Звіт");
            pdfService.generateReportExtended(
                    pdfPath,
                    req.getTitle(), req.getAreaHa(),
                    req.getPriceUah(), req.getPriceUsd(),
                    req.getElevation(), suitability,
                    boundaries,
                    imgScheme, imgTerrain, imgDem,
                    imgNdvi, imgSatellite, img3d,
                    imgSlope, weatherChart, imgInfraAnnotated,
                    aiAnalyses, climateData, infraDetails,
                    dayLength, country, geoAddress,
                    poiData, cornerElevations, annualChart);

            return Files.readAllBytes(Paths.get(pdfPath));

        } finally {
            deleteDir(new File(tempDir));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Coordinate performSearch(String input) throws Exception {
        if (input.contains(",") && input.contains(";")) {
            return parseManualPoints(input);
        }
        if (input.matches("\\d{10}:\\d{2}:\\d{3}:\\d{4}")) {
            return cadastreApiService.getPlotByCadastralNumber(input);
        }
        return geoApiService.getCoordinates(input);
    }

    private Coordinate parseManualPoints(String input) {
        try {
            List<Coordinate> points = new ArrayList<>();
            double sumLat = 0, sumLon = 0;
            for (String pair : input.split(";")) {
                String[] parts = pair.trim().split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    points.add(new Coordinate(lat, lon));
                    sumLat += lat;
                    sumLon += lon;
                }
            }
            if (points.size() < 3) return null;
            Coordinate center = new Coordinate(sumLat / points.size(), sumLon / points.size(), null);
            center.setBoundaries(points);
            return center;
        } catch (Exception e) {
            logger.warn("Manual coordinate parse error: {}", e.getMessage());
            return null;
        }
    }

    private List<Coordinate> resolveBoundaries(Coordinate c) {
        if (c.getBoundaries() != null && !c.getBoundaries().isEmpty()) return c.getBoundaries();
        if (c.getGeoJson() != null) return parseGeoJson(c.getGeoJson());
        double d = 0.0006;
        double lat = c.getLatitude(), lon = c.getLongitude();
        return List.of(
                new Coordinate(lat + d, lon - d),
                new Coordinate(lat + d, lon + d),
                new Coordinate(lat - d, lon + d),
                new Coordinate(lat - d, lon - d));
    }

    private List<Coordinate> parseGeoJson(String geoJson) {
        List<Coordinate> coords = new ArrayList<>();
        try {
            if (geoJson.contains("\"coordinates\":[[[")) {
                String raw = geoJson.split("\\[\\[\\[")[1].split("\\]\\]\\]")[0];
                for (String p : raw.split("\\],\\[")) {
                    String[] pts = p.replace("[", "").replace("]", "").split(",");
                    coords.add(new Coordinate(Double.parseDouble(pts[1]), Double.parseDouble(pts[0])));
                }
            }
        } catch (Exception e) {
            logger.debug("GeoJSON parse error: {}", e.getMessage());
        }
        return coords;
    }

    private double computeArea(Coordinate coordinate, List<Coordinate> boundaries) {
        if (boundaries != null && !boundaries.isEmpty()) {
            return GeoAnalysisUtil.calculateAreaFromCoordinates(boundaries);
        }
        if (coordinate.getGeoJson() != null) {
            return GeoAnalysisUtil.calculateAreaFromGeoJson(coordinate.getGeoJson());
        }
        return 0.0;
    }

    private List<Coordinate> toCoordinates(List<SearchResponse.LatLon> list) {
        if (list == null) return List.of();
        return list.stream()
                .map(ll -> new Coordinate(ll.lat, ll.lon))
                .collect(Collectors.toList());
    }

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

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
