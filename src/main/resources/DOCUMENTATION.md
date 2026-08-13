# Land Plot Analyzer — Технічна документація

## Інтелектуальна ГІС-система аналізу земельних ділянок

---

## 1. Загальна архітектура

Система має **дві незалежні точки входу поверх спільного ядра аналізу**:

| Варіант | Точка входу | Призначення |
|---------|-------------|-------------|
| **Desktop** | `ua.edu.university.Launcher` → `Main` (JavaFX) | Локальна робота, інтерактивний WebView + 3D-вікно |
| **Web** | `ua.edu.university.web.WebApplication` (Spring Boot) | REST API для React-фронтенду; працює headless у Docker |

Спільними для обох є пакети `api/`, `model/` і більшість `util/` — збір даних,
розрахунки, генерація PDF. Різниця лише в тому, **як отримуються зображення карт
і 3D-модель** (див. §4).

```
ua.edu.university
├── Main / Launcher              — точка входу JavaFX + JPMS-обхід
├── ui/
│   └── MainController           — єдиний FXML-контролер (main_view.fxml)
├── model/
│   └── Coordinate               — WGS-84 точка + межі полігону + висота + suitability
├── api/                         — усі класи наслідують BaseApiService
│   ├── BaseApiService           — HttpClient, логування, обробка HTTP-помилок
│   ├── GeoApiService            — геокодування + reverse (Nominatim)
│   ├── CadastreApiService       — кадастрові дані (cadastre_data.json mock)
│   ├── WeatherApiService        — клімат 30 д. + місячний та річний архів (Open-Meteo)
│   ├── ElevationApiService      — висота н.р.м. (5 спроб, пауза 3 с)
│   ├── OverpassApiService       — інфраструктура + POI (OSM Overpass)
│   ├── InsolationApiService     — тривалість світлового дня
│   ├── DomRiaApiService         — ринкова ціна м² (DOM.RIA)
│   ├── ExchangeRateService      — курс USD (ПриватБанк / НБУ)
│   └── CountryContextService    — регіональний контекст
├── util/                        — спільне ядро аналізу та звітності
│   ├── ConfigManager            — singleton; application.properties
│   ├── GeoAnalysisUtil          — площа: формула Shoelace + JTS
│   ├── LandAnalysisService      — оціночна вартість UAH / USD
│   ├── SuitabilityCalculator    — КП: 6 зважених чинників
│   ├── PromptLoader             — промти з /prompts/*.md + кеш
│   ├── ClaudeAnalysisService    — Claude API; 6 ролей аналізу + ретраї
│   ├── PdfReportService         — OpenPDF; збірка багатосторінкового звіту
│   ├── PdfCellHelper            — статичні фабрики комірок + кольори (import static)
│   ├── MapExtrasBuilder         — таблиці для сторінок карт у PDF (import static)
│   ├── MonthlyClimateChart      — Java2D: річна кліматична діаграма (12 міс.)
│   ├── InfraAnnotator           — анотація транспортної доступності на знімку
│   ├── FileUtil                 — генерація шляху до PDF
│   ├── MapHtmlBuilder           — Leaflet HTML (desktop WebView + web /api/map)
│   ├── LandParcel3dImageRenderer— 3D-модель через Java2D (web, headless)
│   ├── ReportCoordinator        — 3-фазний конвеєр звіту (desktop)
│   ├── LandParcel3dVisualizer   — 3D wireframe на JavaFX Canvas (desktop)
│   ├── Land3DView               — JavaFX Stage для 3D-перегляду (desktop)
│   └── WeatherChartGenerator    — графік погоди 30 днів (desktop, FX Canvas)
└── web/                         — Spring Boot шар
    ├── WebApplication           — @SpringBootApplication (DataSource вимкнено)
    ├── config/WebConfig         — CORS для /api/**
    ├── controller/
    │   └── LandAnalysisController — REST endpoints
    ├── dto/                     — SearchRequest, SearchResponse, ReportRequest
    └── service/
        ├── WebReportService     — веб-аналог ReportCoordinator
        └── StaticMapService     — зшивання тайлів у PNG (замість WebView)
```

Фронтенд (окремий модуль, не входить у JAR):

```
land-analyzer-web/               — React 19 + Vite
└── src/
    ├── App.jsx                  — головний компонент, стан пошуку
    ├── api.js                   — axios-клієнт (timeout звіту 180 с)
    └── components/
        ├── SearchBar.jsx        — поле пошуку
        ├── MapView.jsx          — iframe з HTML від /api/map + hero порожнього стану
        ├── PricePanel.jsx       — площа, висота, ціна, курс
        ├── ReportPanel.jsx      — вибір шарів, AI-чекбокс, прогрес-бар
        └── ThreeDView.jsx       — 3D-модель на HTML canvas (порт LandParcel3dVisualizer)
```

---

## 2. REST API (веб-версія)

Базовий шлях `/api`, CORS відкритий для всіх origin (`WebConfig`).

| Метод | Endpoint | Тіло / відповідь |
|-------|----------|------------------|
| `POST` | `/api/search` | `{query}` → `SearchResponse` (координати, площа, ціна, висота, межі, GeoJSON) |
| `POST` | `/api/map` | `SearchResponse` → HTML-сторінка Leaflet (`text/html`) |
| `POST` | `/api/report` | `ReportRequest` → PDF (`application/pdf`, attachment) |
| `GET` | `/api/health` | `{"status":"UP"}` |

**Захист AI-аналізу.** Якщо `ReportRequest.aiEnabled = true`, контролер звіряє
`aiPassword` зі значенням `AI_ACCESS_PASSWORD` (env-змінна на проді перекриває
властивість з `application.properties`). Розбіжність → `401`, звіт не генерується.

---

## 3. Конвеєр генерації звіту

### 3.1 Desktop — `ReportCoordinator`

| Фаза | Що відбувається |
|------|-----------------|
| **1. collectApiData** | Паралельний збір клімату, інфраструктури, POI, кутових висот, інсоляції, геоадреси |
| **2. captureMapLayers** | Послідовне перемикання шарів Leaflet + `CountDownLatch` + знімок WebView |
| **3. assemblePdf** | Виклик `PdfReportService` + видалення тимчасових файлів + відкриття PDF |

Передача даних між фазами — через private records `ReportData` і `CapturedLayers`.

### 3.2 Web — `WebReportService`

Той самий тришаровий поділ, але без JavaFX:

1. **Збір даних** — ті ж API-сервіси.
2. **Зображення** — `StaticMapService` (тайли) і `LandParcel3dImageRenderer` (3D)
   замість знімків WebView / FX Canvas.
3. **Збірка** — той самий `PdfReportService`.

**Паралелізм AI.** Усі AI-запити стартують одразу після визначення набору шарів
через `CompletableFuture.supplyAsync` на власному `ExecutorService`, а `join()`
виконується безпосередньо перед збіркою PDF — тобто AI рахується одночасно із
завантаженням карт.

> **Пул навмисно обмежений двома потоками.** Усі 6 запитів одночасно стабільно
> впирались у ліміт одночасних з'єднань Anthropic API: частина з'єднань рвалась
> без чистого `429`, тому ретраї не рятували. Два потоки — усе ще значно швидше
> за послідовний варіант, але без перевищення ліміту.

---

## 4. Ключові компоненти

### `StaticMapService` — карти для веб-версії

Замінює знімки WebView: завантажує сітку тайлів **5 × 4** паралельно, зшиває у
`BufferedImage`, малює полігон ділянки і обрізає до **820 × 500**.

**Центрування.** Кадр центрується на геометричному центрі полігону, а не на точці
геокодування (для адресного пошуку вони не збігаються). Обрізка рахується з
дробової тайлової позиції — інакше квантування по цілих тайлах давало зсув до
пів-тайла.

**Зум.** `calculateZoom` підбирає рівень так, щоб bounding box ділянки займав ~22 %
кадру (решта — контекст оточення); діапазон обмежено `MIN_ZOOM = 12` … `MAX_ZOOM = 17`.

**Покриття даними.** ESRI-сервіси відповідають `HTTP 200` із зображенням-заглушкою
«Map data not yet available» на тайли поза покриттям, причому глибина покриття
залежить від регіону (`World_Hillshade`: до z16 біля Львова, але лише до z13 біля
Києва). Заглушки позначені константним `ETag`, тому `resolveDataZoom()` пробує
центральний тайл і знижує зум, доки не прийдуть реальні дані. Невдала проба лишає
зум незмінним — інші тайл-провайдери не зачеплені.

Джерела тайлів: OSM (SCHEME), ESRI World_Topo_Map (TERRAIN), OpenTopoMap (DEM),
EOX Sentinel-2 cloudless (NDVI), ESRI World_Imagery (SATELLITE),
ESRI World_Hillshade (SLOPE).

### `LandParcel3dImageRenderer` — 3D без JavaFX

Java2D-порт логіки малювання `LandParcel3dVisualizer` для headless-середовищ.

> **Навмисно не імпортує жоден клас із `javafx.*`, навіть опосередковано.**
> Якщо клас лише *посилається* на JavaFX-типи, спроба його завантажити в JVM без
> JavaFX graphics jar кидає `NoClassDefFoundError` — навіть коли викликаний метод
> сам JavaFX не використовує. Тому геометричні хелпери продубльовано локально,
> а не перевикористано з `LandParcel3dVisualizer`.

### `ClaudeAnalysisService` — AI-аналіз

- Endpoint `https://api.anthropic.com/v1/messages`, версія API `2023-06-01`
- Ключ: змінна середовища `ANTHROPIC_API_KEY`
- Модель і ліміт токенів — з `application.properties`
- 6 методів: інфраструктура, рельєф, DEM, NDVI, супутник, схили

**Ретраї.** До 3 спроб із наростаючою затримкою (800 мс × номер спроби).
Повторюються лише `429`, `5xx` (включно з `529 overloaded`) і мережеві винятки;
`4xx` (напр. невірний ключ) не повторюються — повтор їх не виправить.

**Розбір відповіді.** Текст збирається з **усіх** блоків `content[]`, де є поле
`text`. Раніше код брав `content[0]` наосліп: якщо перший блок був не текстовий,
виникав `NullPointerException` **однаково на кожній спробі**, тож ретраї не
допомагали — це була систематична помилка парсингу, а не транзиєнтний збій.

### `PromptLoader` — управління промтами

Формат файлу `prompts/<name>.md`:
```
role: <текст ролі одним рядком>
---
<тіло задачі з плейсхолдерами %f %f для lat/lon>
```
Результат кешується у `ConcurrentHashMap`. Редагування промту не потребує
перекомпіляції — лише перезапуску.

### `SuitabilityCalculator` — коефіцієнт придатності

Зважена сума шести чинників; ваги нормалізуються, якщо частина даних відсутня
(`-1` = «немає даних»).

| Чинник | Вага |
|--------|------|
| Рельєф / висота | 15 % |
| Транспортна доступність | 20 % |
| Електрична інфраструктура | 15 % |
| Водні ресурси | 10 % |
| Екологічні ризики | 20 % |
| Кліматичні умови | 20 % |

`calculateFromElevation()` — швидка оцінка лише за висотою. **У веб-інтерфейсі
вона більше не показується**: точний відсоток одразу після пошуку, порахований
без інфраструктури й клімату, вводив в оману. Повний КП рахується лише у звіті.

### `ElevationApiService` — надійний запит висоти

До 5 спроб із паузою 3 с. Після останньої невдачі повертає `api.elevation.default`.

### `PdfReportService` — структура звіту

| Сторінка | Вміст |
|----------|-------|
| 1 | Обкладинка: координати, площа, висота, вартість UAH/USD, КП, знімок 3D-моделі |
| 2 | Кліматичний і техніко-географічний паспорт: метеотаблиця, графік погоди, інфраструктура, адресні дані, методологія КП, транспортна доступність |
| 3–8 | По одній сторінці на обраний шар (SCHEME → SLOPE): карта, експертний висновок Claude, тематична таблиця |
| остання | Геодезичний додаток: координати меж (WGS 84), до 50 точок |

Сторінки карт додаються лише для обраних шарів, тому обсяг змінний.
Шрифт вантажиться спершу з classpath (`/fonts/arial.ttf`) — це робить PDF
незалежним від ОС у Docker; шлях `pdf.font.path` лишається запасним для Windows.

---

## 5. Конфігурація (`application.properties`)

| Ключ | Призначення |
|------|-------------|
| `map.default.lat/lon/zoom` | Початковий центр карти (Україна) |
| `land.price.ngo_per_sqm` | Ставка НГО грн/м² для базової оцінки |
| `ai.claude.model` | ID моделі Claude (поточно `claude-sonnet-5`) |
| `ai.claude.max.tokens` | Ліміт токенів у відповіді |
| `ai.claude.language` | Мова відповіді |
| `ai.claude.sentence.limit` | Макс. кількість речень у відповіді |
| `analysis.ai.enabled` | Стан чекбокса AI за замовчуванням |
| `AI_ACCESS_PASSWORD` | Пароль доступу до AI (на проді — env-змінна) |
| `api.elevation.default` | Запасна висота після 5 невдалих запитів |
| `api.domria.key` | Ключ DOM.RIA для ринкової ціни |
| `report.layer.switch.delay.ms` | Таймаут очікування тайлів Leaflet (desktop) |
| `report.final.exit.delay.ms` | Затримка перед `System.exit(0)` після PDF (desktop) |
| `pdf.font.path` | Запасний шлях до TTF з кирилицею |
| `pdf.color.main/accent/background` | Палітра PDF |
| `reports.directory` / `reports.file.prefix` | Куди і з яким префіксом писати PDF |

**Секрети.** `ANTHROPIC_API_KEY` ніколи не зберігається у properties — лише
env-змінна (локально — у run configuration IDEA, на проді — Railway → Variables).

---

## 6. Технічні обмеження

- **`Locale.US` обов'язково** у всіх місцях форматування координат і чисел для
  API-запитів — інакше десяткова кома ламає запити.
- **JavaFX Application Thread** — WebView-snapshot, `WeatherChartGenerator` і
  `Land3DView` викликаються лише через `Platform.runLater`. У веб-версії ці
  компоненти не використовуються взагалі.
- **`CountDownLatch` у `captureMapLayers`** — не видаляти; гарантує завершення
  рендерингу тайлів перед знімком.
- **`CadastreApiService`** — використовує локальний `cadastre_data.json`;
  реальний API ДЗК не підключений.
- **Логування тіл відповідей обрізається** до 1000 символів: повні відповіді
  Overpass сягають сотень KB і при паралельних запитах переплітаються в потоці
  логів, роблячи їх нечитабельними.
- **Локальний запуск під Windows** потребує
  `-Djavax.net.ssl.trustStoreType=Windows-ROOT` — без нього Java `HttpClient`
  падає з `SSLHandshakeException: PKIX path building failed`, усі тайли не
  вантажаться і карта виходить сірою (легко сплутати з багом у коді).

---

## 7. Збірка та розгортання

```bash
mvn clean compile                     # компіляція
mvn javafx:run                        # desktop-версія
mvn spring-boot:run \                 # web-бекенд локально (порт 8080)
  -Dspring-boot.run.mainClass=ua.edu.university.web.WebApplication
cd land-analyzer-web && npm run dev    # web-фронтенд локально (порт 5173)
```

Продакшн: `Dockerfile` (multi-stage, Temurin 17) збирає `*-web.jar` і запускає
Spring Boot на Railway; фронтенд збирається Vite і хоститься на Vercel.
Детальніше — `DEPLOYMENT.md` у корені репозиторію.

Автоматизованих тестів немає — перевірка виконується вручну через запущений
застосунок і прямі HTTP-запити до `/api/*`.

---

*Актуально на 13.08.2026*
