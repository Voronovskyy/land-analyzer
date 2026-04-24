# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LandPlotAnalyzer is a JavaFX desktop application for GIS-based land plot analysis in Ukraine. It integrates 10+ external REST APIs, Google Gemini AI (5 expert roles), and generates multi-page PDF reports with maps, climate data, infrastructure analysis, and 3D visualizations.

## Commands

**Build:**
```bash
mvn clean compile
```

**Run:**
```bash
mvn javafx:run
```

**Package (fat JAR):**
```bash
mvn clean package
```

There are no automated tests in this project — testing is manual via the running application.

## Architecture

### Entry Points
- `ua.edu.university.Launcher` — main class registered in pom.xml (JavaFX workaround for module system)
- `ua.edu.university.Main` — actual `javafx.application.Application` subclass; loads `main_view.fxml`

### Layer Structure

**UI Layer** (`ua.edu.university.ui`)
- `MainController` — single controller wired to `main_view.fxml`; handles address search, map rendering (WebView + Leaflet), 3D toggle, and report generation trigger

**Orchestration** (`ua.edu.university.util.ReportCoordinator`)
- Coordinates the full report pipeline: fires parallel `CompletableFuture` calls for weather, infrastructure, insolation, and regional context, then runs sequential map-layer screenshot captures (guarded by `CountDownLatch`), then invokes `GeminiAnalysisService` and `PdfReportService`

**API Layer** (`ua.edu.university.api`)
- All services extend `BaseApiService` which wraps `HttpClient`
- Key services: `WeatherApiService` (Open-Meteo), `GeoApiService` (Nominatim geocoding), `CadastreApiService` (local mock + `cadastre_data.json`), `ElevationApiService`, `OverpassApiService`, `InsolationApiService`, `GeminiAnalysisService`, `CountryContextService`, `ExchangeRateService`

**Utility Layer** (`ua.edu.university.util`)
- `ConfigManager` — singleton reading `application.properties`; all API URLs, styling constants, and output paths come from here
- `GeoAnalysisUtil` — Shoelace formula + JTS for area/geometry; use `Locale.US` for all coordinate formatting
- `PdfReportService` — OpenPDF-based 6–8 page report with embedded Cyrillic font
- `MapHtmlBuilder` — builds the Leaflet HTML injected into WebView; supports OSM, Satellite, Terrain, DEM, NDVI layers
- `LandParcel3dVisualizer` — isometric wireframe rendered on JavaFX `Canvas` (no 3D library)

**Models** (`ua.edu.university.model`)
- `Coordinate` — holds lat/lng, bounding polygon, elevation, and computed suitability score
- `ExtendedLandData` — aggregated result from all API calls, passed to report generation

### Configuration

`src/main/resources/application.properties` is the single source of truth for:
- API base URLs (Open-Meteo, Nominatim, Overpass, Open-Elevation, PrivatBank/NBU)
- Default map center (Ukraine: 49.8397, 24.0297)
- PDF styling (colors `#2C3E50` / `#27AE60`, font paths)
- Gemini model (`gemini-2.0-flash-preview`)
- Report output directory (`Reports/`)

The Gemini API key is supplied via the `GEMINI_API_KEY` environment variable (not stored in properties).

### Key Technical Constraints

- **`Locale.US` everywhere** — all coordinate/number formatting must use `Locale.US` to avoid decimal-comma issues in API requests
- **JavaFX threading** — all UI updates must run on the FX Application Thread; background work uses `CompletableFuture` with `.thenAccept(v -> Platform.runLater(...))`
- **Map screenshot synchronization** — `ReportCoordinator` uses a `CountDownLatch` to wait for each WebView layer to fully render before capturing; modifying this sequence requires preserving the latch logic
- **CadastreApiService** — Ukrainian cadastral data is served from the local `cadastre_data.json` mock; real integration is not yet implemented