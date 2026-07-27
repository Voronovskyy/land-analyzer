package ua.edu.university.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.edu.university.model.Coordinate;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
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

    private static final int ZOOM = 14;
    private static final int TILE_SIZE = 256;
    private static final int GRID_W = 4;
    private static final int GRID_H = 3;
    private static final int STITCH_W = GRID_W * TILE_SIZE;   // 1024
    private static final int STITCH_H = GRID_H * TILE_SIZE;   // 768
    private static final int OUT_W = 820;
    private static final int OUT_H = 500;
    private static final int CROP_X = (STITCH_W - OUT_W) / 2; // 102
    private static final int CROP_Y = (STITCH_H - OUT_H) / 2; // 134

    private static final Map<String, String> TILE_URLS = Map.of(
            "SCHEME",    "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "TERRAIN",   "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}",
            "DEM",       "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            "NDVI",      "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2020_3857/default/g/{z}/{y}/{x}.jpg",
            "SATELLITE", "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            "SLOPE",     "https://server.arcgisonline.com/ArcGIS/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}"
    );

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
        String tileUrl = TILE_URLS.getOrDefault(layer, TILE_URLS.get("SCHEME"));
        try {
            long cx = lonToTileX(lon, ZOOM);
            long cy = latToTileY(lat, ZOOM);
            long txStart = cx - 1;
            long tyStart = cy - 1;

            // Download 4×3 tiles in parallel
            List<List<CompletableFuture<BufferedImage>>> futures = new ArrayList<>();
            for (int row = 0; row < GRID_H; row++) {
                List<CompletableFuture<BufferedImage>> rowFutures = new ArrayList<>();
                for (int col = 0; col < GRID_W; col++) {
                    rowFutures.add(downloadTile(tileUrl, ZOOM, txStart + col, tyStart + row));
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
                drawPolygon(gs, polygon, txStart, tyStart);
            }
            gs.dispose();

            // Crop from center to OUT_W×OUT_H (no distortion)
            BufferedImage cropped = stitched.getSubimage(CROP_X, CROP_Y, OUT_W, OUT_H);
            BufferedImage result = new BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D gr = result.createGraphics();
            gr.drawImage(cropped, 0, 0, null);
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

    private void drawPolygon(Graphics2D g, List<Coordinate> polygon, long txStart, long tyStart) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (Coordinate c : polygon) {
            double px = (lonToTileXDouble(c.getLongitude(), ZOOM) - txStart) * TILE_SIZE;
            double py = (latToTileYDouble(c.getLatitude(), ZOOM) - tyStart) * TILE_SIZE;
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

    // Standard Web Mercator tile math
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
