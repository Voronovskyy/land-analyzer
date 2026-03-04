# 📑 Науково-технічний звіт про розробку інтелектуальної ГІС-системи
**Проєкт:** Land Plot Analyzer v2.0 (PhD Research Module)  
**Статус:** Рефакторинг завершено, впроваджено мультимодальний ШІ.

---

## 🏗 1. Архітектурна концепція
Система побудована як **Hybrid Geospatial Intelligence System**. Вона поєднує класичні методи геодезичних розрахунків з новітніми технологіями генеративного штучного інтелекту для автоматизації експертної оцінки територій.



## 🛠 2. Технологічний стек та бібліотеки
| Компонент | Технологія | Роль |
| :--- | :--- | :--- |
| **Core Language** | Java 21 (LTS) | Використання сучасних Record-типів, HttpClient та віртуальних потоків. |
| **UI Framework** | JavaFX 21 | Побудова багатопотокового GUI та Web-інтеграція. |
| **GIS Engine** | Leaflet.js + Turf.js | Клієнтський рендеринг карт та векторні операції в браузері. |
| **Math Engine** | JTS Topology Suite | Прецизійні розрахунки геометрії (проєкції, площі, центроїди). |
| **AI Integration** | Google GenAI SDK | Взаємодія з моделлю Gemini 2.0 Flash через API. |
| **Document Engine** | OpenPDF (iText fork) | Динамічне формування звітів із підтримкою Unicode (Arial). |
| **Logging** | SLF4J + Logback | Моніторинг асинхронних процесів та стану API. |

## 🌐 3. Джерела та методи отримання даних
Система реалізує ланцюжок збору даних (**Data Pipeline**) з відкритих джерел:
1. **Геопросторові дані:** OpenStreetMap (через Nominatim API) — отримати координати.
2. **Висоти (DEM):** Open-Elevation API (SRTM Data) — цифрова модель рельєфу.
3. **Кадастрова геометрія:** JSON-імітація ДЗК (GeoJSON стандарт).
4. **Супутникові знімки:** Esri World Imagery & Sentinel-2 Cloudless (NDVI).
5. **Фінанси:** PrivatBank API (JSON) — актуалізація вартості за курсом валют.



## 📐 4. Математичне та наукове обґрунтування
Для забезпечення точності PhD-рівня впроваджено:
* **Алгоритм площі Гаусса (Shoelace Formula):** реалізовано в `GeoAnalysisUtil` з корекцією на сферичність Землі ($R = 6371$ км).
* **3D Проєкція:** Власний двигун аксонометричного рендерингу для перетворення дискретних висотних точок у візуальну модель рельєфу (Wireframe).
* **Prompt Engineering:** Методологія "Zero-shot CoT" (Chain of Thought) для отримання експертних висновків ШІ за вузькими напрямками (Гідрологія, Екологія, Урбаністика).



## 📋 5. Список використаних джерел та документації
1. **JTS Topology Suite Documentation** — [https://locationtech.github.io/jts/](https://locationtech.github.io/jts/)
2. **Leaflet.js API Reference** — [https://leafletjs.com/reference.html](https://leafletjs.com/reference.html)
3. **Google Gemini API Documentation** — [https://ai.google.dev/docs](https://ai.google.dev/docs)
4. **OpenPDF Documentation** — [https://github.com/LibrePDF/OpenPDF](https://github.com/LibrePDF/OpenPDF)
5. **Nominatim Geocoding API** — [https://nominatim.org/release-docs/latest/api/Overview/](https://nominatim.org/release-docs/latest/api/Overview/)
6. **Esri Map Tile Services** — [https://developers.arcgis.com/documentation/mapping-apis-and-services/maps/services/](https://developers.arcgis.com/documentation/mapping-apis-and-services/maps/services/)
