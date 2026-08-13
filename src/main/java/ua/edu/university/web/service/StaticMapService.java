package ua.edu.university.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.edu.university.model.Coordinate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Завантажує тайли мап з публічних тайл-серверів і зшиває їх у PNG-зображення.
 * Замінює WebView-знімки десктопної версії для веб-бекенду.
 */
@Service
public class StaticMapService {
    private static final Logger logger = LoggerFactory.getLogger(StaticMapService.class);

    private static final int TILE_SIZE = 256;
    // Grid is sized with enough margin around the output crop to allow
    // sub-tile-precise centering (see the crop math in generate()) without
    // ever running off the stitched image edge.
    private static final int GRID_W = 5;
    private static final int GRID_H = 4;
    private static final int STITCH_W = GRID_W * TILE_SIZE;   // 1280
    private static final int STITCH_H = GRID_H * TILE_SIZE;   // 1024
    private static final int OUT_W = 820;
    private static final int OUT_H = 500;

    private static final int DEFAULT_ZOOM = 14;
    private static final int MAX_ZOOM = 17;
    private static final int MIN_ZOOM = 12;

    private static final Map<String, String> TILE_URLS = Map.of(
            "SCHEME",    "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "TERRAIN",   "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}",
            "DEM",       "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "NDVI",      "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2020_3857/default/g/{z}/{y}/{x}.jpg",
            "SATELLITE", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "SLOPE",     "https://server.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}"
    );

    public record PlotInfo(String title, String area, String priceUah, String priceUsd, double elevation,
                           double lat, double lon) {}

    private final HttpClient httpClient;
    private final ExecutorService executor;

    public StaticMapService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.executor = Executors.newFixedThreadPool(12);
    }

    public File generate(double lat, double lon, String layer,
                         List<Coordinate> polygon, String outputPath) {
        return generate(lat, lon, layer, polygon, outputPath, null);
    }

    public File generate(double lat, double lon, String layer,
                         List<Coordinate> polygon, String outputPath, PlotInfo info) {
        String tileUrl = TILE_URLS.getOrDefault(layer, TILE_URLS.get("SCHEME"));
        try {
            double[] center = resolveCenter(lat, lon, polygon);
            int zoom = calculateZoom(polygon);
            double cxD = lonToTileXDouble(center[1], zoom);
            double cyD = latToTileYDouble(center[0], zoom);
            long cx = (long) cxD;
            long cy = (long) cyD;
            long txStart = cx - GRID_W / 2;
            long tyStart = cy - GRID_H / 2;

            // Download tiles in parallel
            List<List<CompletableFuture<BufferedImage>>> futures = new ArrayList<>();
            for (int row = 0; row < GRID_H; row++) {
                List<CompletableFuture<BufferedImage>> rowFutures = new ArrayList<>();
                for (int col = 0; col < GRID_W; col++) {
                    rowFutures.add(downloadTile(tileUrl, zoom, txStart + col, tyStart + row));
                }
                futures.add(rowFutures);
            }

            // Stitch
            BufferedImage stitched = new BufferedImage(STITCH_W, STITCH_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D gs = stitched.createGraphics();
            gs.setColor(new Color(200, 200, 200));
            gs.fillRect(0, 0, STITCH_W, STITCH_H);

            for (int row = 0; row < GRID_H; row++) {
                for (int col = 0; col < GRID_W; col++) {
                    BufferedImage tile = futures.get(row).get(col).join();
                    if (tile != null) {
                        gs.drawImage(tile, col * TILE_SIZE, row * TILE_SIZE, null);
                    }
                }
            }

            // Draw polygon overlay
            if (polygon != null && polygon.size() >= 3) {
                drawPolygon(gs, polygon, txStart, tyStart, zoom);
            }
            gs.dispose();

            // Crop centered exactly on the resolved point (not just the
            // whole-tile grid center — a plain tile-index crop can be off
            // by up to half a tile since the point rarely falls exactly
            // on a tile boundary).
            double exactPxX = (cxD - txStart) * TILE_SIZE;
            double exactPxY = (cyD - tyStart) * TILE_SIZE;
            int cropX = clamp((int) Math.round(exactPxX - OUT_W / 2.0), 0, STITCH_W - OUT_W);
            int cropY = clamp((int) Math.round(exactPxY - OUT_H / 2.0), 0, STITCH_H - OUT_H);
            BufferedImage cropped = stitched.getSubimage(cropX, cropY, OUT_W, OUT_H);
            BufferedImage result = new BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D gr = result.createGraphics();
            gr.drawImage(cropped, 0, 0, null);

            // Draw passport panel if info provided
            if (info != null) {
                drawPassport(gr, info, layer);
            }

            gr.dispose();

            File out = new File(outputPath);
            out.getParentFile().mkdirs();
            ImageIO.write(result, "png", out);
            return out;

        } catch (Exception e) {
            logger.error("StaticMapService: failed to generate '{}' map: {}", layer, e.getMessage());
            return null;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    // ── Center resolution ────────────────────────────────────────────────

    /**
     * Центрує знімок на геометричному центрі полігону ділянки, а не на
     * точці геокодування (яка для адресного пошуку не завжди збігається
     * з центроїдом межі — через це ділянка раніше з'являлась не по центру).
     */
    private double[] resolveCenter(double lat, double lon, List<Coordinate> polygon) {
        if (polygon == null || polygon.size() < 3) return new double[]{lat, lon};
        double minLat = polygon.stream().mapToDouble(Coordinate::getLatitude).min().orElse(lat);
        double maxLat = polygon.stream().mapToDouble(Coordinate::getLatitude).max().orElse(lat);
        double minLon = polygon.stream().mapToDouble(Coordinate::getLongitude).min().orElse(lon);
        double maxLon = polygon.stream().mapToDouble(Coordinate::getLongitude).max().orElse(lon);
        return new double[]{(minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0};
    }

    // ── Zoom calculation ─────────────────────────────────────────────────

    private int calculateZoom(List<Coordinate> polygon) {
        if (polygon == null || polygon.size() < 2) return DEFAULT_ZOOM;

        double minLat = polygon.stream().mapToDouble(Coordinate::getLatitude).min().orElse(0);
        double maxLat = polygon.stream().mapToDouble(Coordinate::getLatitude).max().orElse(0);
        double minLon = polygon.stream().mapToDouble(Coordinate::getLongitude).min().orElse(0);
        double maxLon = polygon.stream().mapToDouble(Coordinate::getLongitude).max().orElse(0);

        double dLon = Math.max(maxLon - minLon, 0.00005);
        double dLat = Math.max(maxLat - minLat, 0.00005);

        // Target: polygon bounding box fits in 22% of image width/height
        // (was 35% — plot filled too much of the frame with too little
        // surrounding context, so the crop looked "wrong"/zoomed in too far)
        double targetFraction = 0.22;
        double effectiveTilesW = (double) OUT_W / TILE_SIZE;
        double effectiveTilesH = (double) OUT_H / TILE_SIZE;

        // Zoom for longitude: dLon fits in (targetFraction * OUT_W) pixels
        // At zoom Z: px_per_degree = 2^Z / 360 * TILE_SIZE
        // So: dLon * (2^Z / 360 * TILE_SIZE) <= targetFraction * OUT_W
        // => 2^Z <= targetFraction * OUT_W * 360 / (TILE_SIZE * dLon)
        double zForLon = Math.log(targetFraction * OUT_W * 360.0 / (TILE_SIZE * dLon)) / Math.log(2);

        // Zoom for latitude (Mercator approximation)
        double centerLat = (minLat + maxLat) / 2.0;
        double cosLat = Math.max(Math.cos(Math.toRadians(centerLat)), 0.01);
        double zForLat = Math.log(targetFraction * OUT_H * 360.0 / (TILE_SIZE * dLat) * cosLat) / Math.log(2);

        int zoom = (int) Math.min(zForLon, zForLat);
        return Math.max(MIN_ZOOM, Math.min(zoom, MAX_ZOOM));
    }

    // ── Passport info panel ──────────────────────────────────────────────

    private void drawPassport(Graphics2D g, PlotInfo info, String layer) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Semi-transparent dark panel at bottom
        int panelH = 72;
        int panelY = OUT_H - panelH;

        g.setColor(new Color(15, 25, 40, 210));
        g.fillRect(0, panelY, OUT_W, panelH);

        // Green left accent stripe
        g.setColor(new Color(39, 174, 96));
        g.fillRect(0, panelY, 4, panelH);

        // Layer badge top-right
        String layerLabel = layerName(layer);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics badgeFm = g.getFontMetrics();
        int badgeW = badgeFm.stringWidth(layerLabel) + 16;
        int badgeH = 20;
        int badgeX = OUT_W - badgeW - 10;
        int badgeY = panelY + 8;
        g.setColor(new Color(39, 174, 96, 200));
        g.fill(new RoundRectangle2D.Float(badgeX, badgeY, badgeW, badgeH, 8, 8));
        g.setColor(Color.WHITE);
        g.drawString(layerLabel, badgeX + 8, badgeY + 14);

        // Title line
        String title = info.title() != null ? truncate(info.title(), 70) : "Земельна ділянка";
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        g.drawString(title, 14, panelY + 20);

        // Data fields row
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(180, 220, 180));

        String coords = String.format(Locale.US, "%.5f°, %.5f°", info.lat(), info.lon());
        String area = info.area() != null ? info.area() : "";
        String elev = String.format(Locale.US, "Висота: %.0f м", info.elevation());
        String price = (info.priceUsd() != null && !info.priceUsd().isBlank())
                ? info.priceUsd() : "";

        drawField(g, "📍 " + coords, 14, panelY + 38);
        drawField(g, "📐 " + area, 14, panelY + 54);
        drawField(g, "⛰ " + elev, 260, panelY + 38);
        if (!price.isBlank()) {
            drawField(g, "💰 " + price, 260, panelY + 54);
        }
    }

    private void drawField(Graphics2D g, String text, int x, int y) {
        // Drop shadow
        g.setColor(new Color(0, 0, 0, 120));
        g.drawString(text, x + 1, y + 1);
        g.setColor(new Color(180, 220, 180));
        g.drawString(text, x, y);
    }

    private String layerName(String key) {
        return switch (key) {
            case "SCHEME"    -> "OpenStreetMap";
            case "TERRAIN"   -> "Рельєф";
            case "DEM"       -> "Висоти (DEM)";
            case "NDVI"      -> "NDVI / Вегетація";
            case "SATELLITE" -> "Супутник";
            case "SLOPE"     -> "Ухил";
            default          -> key;
        };
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ── Polygon drawing ──────────────────────────────────────────────────

    private void drawPolygon(Graphics2D g, List<Coordinate> polygon,
                             long txStart, long tyStart, int zoom) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (Coordinate c : polygon) {
            double px = (lonToTileXDouble(c.getLongitude(), zoom) - txStart) * TILE_SIZE;
            double py = (latToTileYDouble(c.getLatitude(), zoom) - tyStart) * TILE_SIZE;
            if (first) { path.moveTo(px, py); first = false; }
            else path.lineTo(px, py);
        }
        path.closePath();

        g.setColor(new Color(231, 76, 60, 55));
        g.fill(path);
        g.setColor(new Color(231, 76, 60));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
    }

    // ── Tile download ────────────────────────────────────────────────────

    private CompletableFuture<BufferedImage> downloadTile(String urlTemplate, int z, long x, long y) {
        return CompletableFuture.supplyAsync(() -> {
            String url = urlTemplate
                    .replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y));
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "LandPlotAnalyzer/1.0 (PhDResearch)")
                        .header("Accept", "image/*")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return ImageIO.read(new ByteArrayInputStream(response.body()));
                }
                logger.debug("Tile {} returned HTTP {}", url, response.statusCode());
            } catch (Exception e) {
                logger.debug("Tile download failed: {}", url);
            }
            return null;
        }, executor);
    }

    // ── Web Mercator math ────────────────────────────────────────────────

    private static long lonToTileX(double lon, int z) {
        return (long) ((lon + 180.0) / 360.0 * (1L << z));
    }

    private static long latToTileY(double lat, int z) {
        double lat_r = Math.toRadians(lat);
        return (long) ((1.0 - Math.log(Math.tan(lat_r) + 1.0 / Math.cos(lat_r)) / Math.PI) / 2.0 * (1L << z));
    }

    private static double lonToTileXDouble(double lon, int z) {
        return (lon + 180.0) / 360.0 * (1L << z);
    }

    private static double latToTileYDouble(double lat, int z) {
        double lat_r = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(lat_r) + 1.0 / Math.cos(lat_r)) / Math.PI) / 2.0 * (1L << z);
    }
}
