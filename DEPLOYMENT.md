# Інструкція з деплою — Land Analyzer Web

## Архітектура

```
Браузер (будь-який пристрій)
        │
        ▼
┌─────────────────────────────┐
│  FRONTEND                   │
│  Vercel.com                 │
│  land-analyzer-six.vercel.app│
│  React + Vite               │
└────────────┬────────────────┘
             │ HTTP REST API
             ▼
┌─────────────────────────────┐
│  BACKEND                    │
│  Railway.app                │
│  land-analyzer-production   │
│  .up.railway.app            │
│  Spring Boot (Java 17)      │
│  Docker контейнер           │
└─────────────────────────────┘
             │
             ▼
    Зовнішні API:
    - Nominatim (геокодування)
    - Open-Meteo (клімат)
    - Overpass (інфраструктура)
    - Open-Elevation (висоти)
    - DOM.RIA (ціни)
    - Claude AI (аналіз)
```

---

## Як запустити локально

### Backend (Spring Boot)
```powershell
$env:JAVA_HOME = "C:\Users\LENOVO\.jdks\corretto-17.0.9"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

cd C:\Users\LENOVO\IdeaProjects\PHD

mvn spring-boot:run `
  -Dspring-boot.run.mainClass=ua.edu.university.web.WebApplication `
  "-Dspring-boot.run.jvmArguments=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Сервер стартує на: `http://localhost:8080`

### Frontend (React)
```powershell
cd C:\Users\LENOVO\IdeaProjects\PHD\land-analyzer-web
npm run dev
```

Відкрити у браузері: `http://localhost:5173`

### Зупинити backend
```powershell
# Знайти PID процесу
netstat -ano | findstr :8080

# Вбити процес (замінити 12345 на реальний PID)
taskkill /PID 12345 /F
```

---

## Коли перезапускати backend?

| Змінив | Потрібен перезапуск? |
|--------|---------------------|
| Java файли (`.java`) | ТАК |
| `application.properties` | ТАК |
| React файли (`.jsx`, `.css`) | НІ — Vite оновлює автоматично |
| `Dockerfile` | Тільки для деплою |

---

## Як залити нові зміни на сайт

### Крок 1 — Закомітити зміни
```bash
cd C:\Users\LENOVO\IdeaProjects\PHD

# Додати змінені файли
git add .

# Закомітити
git commit -m "Опис змін"
```

### Крок 2 — Запушити на GitHub
```bash
git -c http.sslVerify=false push land-analyzer improvements:main
```

### Крок 3 — Автоматичний деплой
- **Railway** (backend) — автоматично починає rebuild після push (~4–6 хв)
- **Vercel** (frontend) — автоматично починає rebuild після push (~1 хв)

Прогрес деплою можна дивитись:
- Railway: `railway.app` → твій проект → вкладка **Deployments**
- Vercel: `vercel.com` → `land-analyzer` → вкладка **Deployments**

---

## Змінні середовища

### Railway (backend)
Налаштовуються в: `railway.app` → проект → вкладка **Variables**

| Змінна | Опис |
|--------|------|
| `ANTHROPIC_API_KEY` | Ключ Claude AI для аналізу |
| `AI_ACCESS_PASSWORD` | Пароль для AI-аналізу у звіті |

### Vercel (frontend)
Налаштовуються в: `vercel.com` → проект → **Settings** → **Environment Variables**

| Змінна | Значення |
|--------|----------|
| `VITE_API_URL` | `https://land-analyzer-production.up.railway.app` |

> **Важливо:** після зміни env-змінних на Vercel потрібно зробити **Redeploy** вручну.
> На Railway — зміни env підхоплюються автоматично з перезапуском.

---

## Пароль для AI-аналізу

Поточний пароль: задається через `AI_ACCESS_PASSWORD` на Railway.
Локально — значення в `src/main/resources/application.properties`:
```
AI_ACCESS_PASSWORD=land2024
```

Щоб змінити пароль на продакшні — оновити змінну на Railway (без перезапуску коду).

---

## Публічні URL

| Сервіс | URL |
|--------|-----|
| Сайт (frontend) | https://land-analyzer-six.vercel.app |
| API (backend) | https://land-analyzer-production.up.railway.app |
| Health check | https://land-analyzer-production.up.railway.app/api/health |

---

## Структура проекту

```
PHD/
├── src/main/java/ua/edu/university/
│   ├── api/          — API сервіси (погода, кадастр, ціни...)
│   ├── model/        — Моделі даних
│   ├── ui/           — JavaFX UI (десктопна версія)
│   ├── util/         — Утиліти (PDF, карти, графіки...)
│   └── web/          — Spring Boot веб-версія
│       ├── config/   — CORS конфігурація
│       ├── controller/ — REST endpoints
│       ├── dto/      — Request/Response об'єкти
│       └── service/  — Бізнес-логіка
│
├── land-analyzer-web/  — React frontend
│   └── src/
│       ├── components/ — SearchBar, MapView, PricePanel,
│       │                 ReportPanel, ThreeDView
│       ├── api.js      — HTTP запити до backend
│       └── App.jsx     — Головний компонент
│
├── Dockerfile          — Збірка для Railway
├── .dockerignore
└── DEPLOYMENT.md       — Ця інструкція
```

---

## Типові проблеми

| Проблема | Причина | Рішення |
|----------|---------|---------|
| Сайт не відповідає | Railway "заснув" | Зачекати 30 сек — перший запит "будить" |
| AI аналіз — "Невірний пароль" | Не той пароль | Перевірити `AI_ACCESS_PASSWORD` на Railway |
| AI аналіз — помилка ключа | `ANTHROPIC_API_KEY` не встановлено | Додати змінну на Railway |
| Карта не завантажується | Backend недоступний | Перевірити `/api/health` |
| Зміни не з'явились | Деплой ще йде | Почекати та перевірити вкладку Deployments |
