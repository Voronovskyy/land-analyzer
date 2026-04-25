# Land Plot Analyzer — Технічна документація

## Інтелектуальна ГІС-система аналізу земельних ділянок

---

## 1. Архітектура системи

Проект побудований за шаруватою архітектурою без DI-контейнера. Усі залежності
створюються вручну в конструкторах контролерів/координаторів.

```
ua.edu.university
├── Main / Launcher          — точка входу JavaFX + JPMS-обхід
├── ui/
│   └── MainController       — єдиний FXML-контролер; прив'язаний до main_view.fxml
├── model/
│   └── Coordinate           — WGS-84 точка + межі полігону + висота + suitability
├── api/                     — усі класи наслідують BaseApiService
│   ├── BaseApiService        — HttpClient, логування, HTTP-помилки
│   ├── GeoApiService         — геокодування (Nominatim)
│   ├── CadastreApiService    — кадастрові дані (cadastre_data.json mock)
│   ├── WeatherApiService     — клімат 30 д. + місячний архів (Open-Meteo)
│   ├── ElevationApiService   — висота над рівнем моря (5 спроб, пауза 3 с)
│   ├── OverpassApiService    — інфраструктура + POI (OSM Overpass)
│   ├── InsolationApiService  — тривалість світлового дня
│   ├── DomRiaApiService      — ринкова ціна м² (DOM.RIA)
│   ├── ExchangeRateService   — курс USD (НБУ / ПриватБанк)
│   └── CountryContextService — регіональний контекст
└── util/
    ├── ConfigManager         — singleton; application.properties
    ├── GeoAnalysisUtil       — площа: формула Shoelace + JTS
    ├── LandAnalysisService   — оціночна вартість UAH / USD
    ├── SuitabilityCalculator — індекс придатності (висота + інфра + клімат)
    ├── MapHtmlBuilder        — генерація Leaflet HTML для WebView
    ├── LandParcel3dVisualizer— 3D wireframe на JavaFX Canvas
    ├── Land3DView            — JavaFX Stage для 3D-перегляду
    ├── WeatherChartGenerator — JavaFX Canvas: графік погоди 30 днів
    ├── MonthlyClimateChart   — Java2D: річна кліматична діаграма (12 міс.)
    ├── InfraAnnotator        — анотація транспортної доступності на знімку
    ├── PromptLoader          — завантаження промтів з /prompts/*.md + кеш
    ├── ClaudeAnalysisService — Claude API (Anthropic); 6 ролей аналізу
    ├── PdfCellHelper         — статичні фабрики комірок + кольори PDF
    ├── MapExtrasBuilder      — таблиці для 6 сторінок карт у PDF
    ├── PdfReportService      — OpenPDF; 6–8 сторінок зі шрифтом кирилиці
    ├── ReportCoordinator     — 3-фазний конвеєр звіту
    └── FileUtil              — генерація шляху до PDF
```

---

## 2. Ключові компоненти

### `ReportCoordinator` — оркестратор конвеєру

Виконує три фази послідовно:

| Фаза | Що відбувається |
|------|-----------------|
| **1. collectApiData** | Паралельний збір клімату, інфраструктури, POI, кутових висот, інсоляції, геоадреси |
| **2. captureMapLayers** | Послідовне перемикання шарів Leaflet + `CountDownLatch` + знімок WebView |
| **3. assemblePdf** | Виклик `PdfReportService` + видалення тимчасових файлів + відкриття PDF |

Передача даних між фазами — через private records `ReportData` і `CapturedLayers`.

### `ClaudeAnalysisService` — AI-аналіз

- Звертається до `https://api.anthropic.com/v1/messages`
- API-ключ: змінна середовища `ANTHROPIC_API_KEY`
- Модель і ліміт токенів — з `application.properties` (`ai.claude.model`, `ai.claude.max.tokens`)
- 6 методів аналізу: інфраструктура, рельєф, DEM, NDVI, супутник, схили
- Промти зберігаються в `src/main/resources/prompts/*.md`; завантажуються через `PromptLoader`

### `PromptLoader` — управління промтами

Формат файлу `prompts/<name>.md`:
```
role: <текст ролі одним рядком>
---
<тіло задачі з плейсхолдерами %f %f для lat/lon>
```
Результат кешується у `ConcurrentHashMap` — повторне читання з диску не відбувається.

### `ElevationApiService` — надійний запит висоти

При збої HTTP повторює запит до 5 разів з паузою 3 секунди між спробами.
Після 5-ї невдачі повертає значення `elevation.default` з `application.properties`.

### `PdfReportService` — структура PDF

| Сторінка | Вміст |
|----------|-------|
| 1 | Титульна: назва, площа, ціна UAH/USD, індекс придатності, висота |
| 2 | Схема інфраструктури + POI-таблиця + AI-аналіз (Claude) |
| 3 | Топографічний рельєф + таблиця перепаду висот кутів |
| 4 | DEM + ризик підтоплення за висотою |
| 5 | NDVI + агрокліматичний індекс Де Мартонна |
| 6 | Супутниковий знімок + річна кліматична діаграма |
| 7 | 3D-модель + графік погоди 30 днів |
| 8 | Карта схилів + аналіз придатності за ухилом |

Шаблонні примітиви (кольори, відступи, шрифти) — у `PdfCellHelper` (import static).
Таблиці для сторінок карт — у `MapExtrasBuilder` (import static).

---

## 3. Конфігурація (`application.properties`)

| Ключ | Призначення |
|------|-------------|
| `map.default.lat/lon/zoom` | Початковий центр карти (Україна) |
| `land.price.ngo_per_sqm` | Ставка НГО грн/м² для базової оцінки |
| `ai.claude.model` | ID моделі Claude (напр. `claude-opus-4-7`) |
| `ai.claude.max.tokens` | Ліміт токенів у відповіді |
| `ai.claude.language` | Мова відповіді (напр. `Відповідай українською`) |
| `ai.claude.sentence.limit` | Макс. кількість речень у відповіді |
| `analysis.ai.enabled` | `true/false` — стан чекбокса AI за замовчуванням |
| `elevation.default` | Запасна висота при 5 невдалих запитах |
| `report.layer.switch.delay.ms` | Таймаут очікування тайлів Leaflet |
| `report.final.exit.delay.ms` | Затримка перед `System.exit(0)` після PDF |
| `pdf.font.path` | Шлях до TTF-шрифту з кирилицею |

---

## 4. Технічні обмеження

- **`Locale.US` обов'язково** у всіх місцях форматування координат і чисел для API-запитів.
- **JavaFX Application Thread** — WebView-snapshot, WeatherChartGenerator і Land3DView викликаються лише через `Platform.runLater`.
- **CountDownLatch у captureMapLayers** — не видаляти; гарантує завершення рендерингу тайлів перед знімком.
- **CadastreApiService** — використовує локальний файл `cadastre_data.json`; реальний API ДЗК не підключений.

---

*Актуально на 25.04.2026*
