// Доступ до Leaflet-карти, яка живе всередині <iframe>.
//
// Карта генерується на бекенді (MapHtmlBuilder) і вписується в iframe через
// document.write, тому це не React-компонент. Але iframe має
// sandbox="allow-scripts allow-same-origin" і документ пишеться з батьківської
// сторінки, тож origin спільний — глобали iframe (`map`, `L`) доступні ззовні.
// Саме так MapView уже дістає contentDocument, щоб записати HTML.

const POLL_INTERVAL_MS = 100;

/**
 * Чекає, доки Leaflet у iframe підвантажиться з CDN і створить карту.
 * Повертає { map, L } або кидає помилку після таймауту.
 */
export function waitForMap(iframe, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();

    const tick = () => {
      const win = iframe && iframe.contentWindow;
      if (win && win.map && win.L) {
        resolve({ map: win.map, L: win.L, win });
        return;
      }
      if (Date.now() - start > timeoutMs) {
        reject(new Error('Карта не ініціалізувалась вчасно'));
        return;
      }
      setTimeout(tick, POLL_INTERVAL_MS);
    };

    tick();
  });
}

/** Синхронний доступ — null, якщо карта ще не готова. */
export function getMap(iframe) {
  const win = iframe && iframe.contentWindow;
  if (!win || !win.map || !win.L) return null;
  return { map: win.map, L: win.L, win };
}

const DRAW_STYLE_ID = 'draw-vertex-style';

/**
 * Мітки вершин живуть у документі iframe, куди App.css не дістає,
 * тому стилі доводиться вставляти прямо туди.
 */
function ensureDrawStyles(win) {
  const doc = win.document;
  if (!doc || doc.getElementById(DRAW_STYLE_ID)) return;
  const style = doc.createElement('style');
  style.id = DRAW_STYLE_ID;
  style.textContent = `
    .draw-vertex-label {
      background: #2c3e50;
      color: #fff;
      border: none;
      border-radius: 9px;
      box-shadow: none;
      padding: 1px 6px;
      font: 700 10px/1.4 "Segoe UI", Arial, sans-serif;
      white-space: nowrap;
    }
    .draw-vertex-label::before { display: none; }
  `;
  doc.head.appendChild(style);
}

/**
 * Перемальовує шар із намальованими точками.
 * Попередній шар знімається — тримаємо посилання на ньому ж, у вікні iframe,
 * бо саме воно живе рівно стільки, скільки поточний вміст карти.
 */
export function renderDrawLayer(iframe, points) {
  const ctx = getMap(iframe);
  if (!ctx) return;
  const { map, L, win } = ctx;
  ensureDrawStyles(win);

  if (win.__drawLayer) {
    map.removeLayer(win.__drawLayer);
    win.__drawLayer = null;
  }
  if (!points.length) return;

  const group = L.layerGroup();
  const latLngs = points.map(p => [p.lat, p.lng]);

  if (points.length >= 3) {
    L.polygon(latLngs, {
      color: '#e74c3c', fillColor: '#e74c3c', fillOpacity: 0.15, weight: 2.5,
    }).addTo(group);
  } else if (points.length === 2) {
    L.polyline(latLngs, {
      color: '#e74c3c', weight: 2.5, dashArray: '6,6',
    }).addTo(group);
  }

  points.forEach((p, i) => {
    L.circleMarker([p.lat, p.lng], {
      radius: 6, color: '#fff', weight: 2, fillColor: '#e74c3c', fillOpacity: 1,
    })
      .bindTooltip(String(i + 1), {
        permanent: true, direction: 'top', offset: [0, -8], className: 'draw-vertex-label',
      })
      .addTo(group);
  });

  group.addTo(map);
  win.__drawLayer = group;
}

/** Прибирає шар малювання, не чіпаючи саму карту. */
export function clearDrawLayer(iframe) {
  const ctx = getMap(iframe);
  if (!ctx) return;
  if (ctx.win.__drawLayer) {
    ctx.map.removeLayer(ctx.win.__drawLayer);
    ctx.win.__drawLayer = null;
  }
}
