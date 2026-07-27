package ua.edu.university.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.edu.university.model.Coordinate;
import ua.edu.university.util.MapHtmlBuilder;
import ua.edu.university.web.dto.ReportRequest;
import ua.edu.university.web.dto.SearchRequest;
import ua.edu.university.web.dto.SearchResponse;
import ua.edu.university.web.service.WebReportService;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class LandAnalysisController {
    private static final Logger logger = LoggerFactory.getLogger(LandAnalysisController.class);

    @Value("${AI_ACCESS_PASSWORD:}")
    private String aiAccessPassword;

    private final WebReportService reportService;

    public LandAnalysisController(WebReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Шукає земельну ділянку за адресою, кадастровим номером або ручними координатами.
     * Повертає всі дані для відображення на карті та генерації звіту.
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest request) {
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            SearchResponse result = reportService.search(request.getQuery());
            if (result == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Search failed for '{}': {}", request.getQuery(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Повертає HTML-сторінку з інтерактивною Leaflet-картою.
     * Фронтенд може вставити цей HTML у <iframe> або відкрити в окремому вікні.
     */
    @PostMapping(value = "/map", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getMap(@RequestBody SearchResponse data) {
        try {
            List<Coordinate> boundaries = null;
            if (data.getBoundaries() != null) {
                boundaries = data.getBoundaries().stream()
                        .map(ll -> new Coordinate(ll.lat, ll.lon))
                        .collect(Collectors.toList());
            }

            double uah = parsePriceNumber(data.getPriceUah());
            double usd = parsePriceNumber(data.getPriceUsd());

            String html = MapHtmlBuilder.build(
                    data.getLat(), data.getLon(), 16,
                    data.getGeoJson(), boundaries,
                    uah, usd, data.getRate(),
                    data.getElevation(), data.getSuitability());

            return ResponseEntity.ok(html);
        } catch (Exception e) {
            logger.error("Map build failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Генерує PDF-звіт і повертає його як файл для завантаження.
     * Запит може тривати 30–90 секунд залежно від кількості шарів та AI аналізу.
     */
    @PostMapping(value = "/report", produces = "application/pdf")
    public ResponseEntity<byte[]> generateReport(@RequestBody ReportRequest request) {
        if (request.getLat() == 0 && request.getLon() == 0) {
            return ResponseEntity.badRequest().build();
        }
        if (request.isAiEnabled()) {
            if (aiAccessPassword.isBlank() || !aiAccessPassword.equals(request.getAiPassword())) {
                logger.warn("AI access denied: wrong password");
                return ResponseEntity.status(401).build();
            }
        }
        try {
            logger.info("Report generation started: lat={}, lon={}, layers={}",
                    request.getLat(), request.getLon(), request.getLayers());
            byte[] pdf = reportService.generateReport(request);
            String filename = "LandReport_" + System.currentTimeMillis() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            logger.error("Report generation failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health-check: перевіряє що сервер живий.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\"}");
    }

    private double parsePriceNumber(String price) {
        if (price == null) return 0;
        try {
            return Double.parseDouble(price.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
