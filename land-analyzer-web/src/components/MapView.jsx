import { useEffect, useRef, useState } from 'react';
import { getMapHtml } from '../api';
import { waitForMap, getMap, renderDrawLayer, clearDrawLayer } from '../mapBridge';
import { pointsToQuery } from '../geo';
import DrawToolbar from './DrawToolbar';

// Порожня карта для малювання «з нуля»: /api/map без boundaries та geoJson
// віддає чисту карту — MapHtmlBuilder просто пропускає рендер полігону.
// Центр — той самий, що map.default.* на бекенді (Львів).
const BLANK_MAP = { lat: 49.8397, lon: 24.0297 };
// Контролер жорстко ставить зум 16 — для пошуку своєї ділянки це надто близько,
// тому відсуваємо камеру вже після завантаження, через міст до карти.
const BLANK_MAP_ZOOM = 13;

export default function MapView({ data, drawMode, busy, onStartDraw, onCancelDraw, onFinishDraw }) {
  const iframeRef = useRef(null);
  const [loading, setLoading] = useState(false);
  const [points, setPoints] = useState([]);
  const [mapReady, setMapReady] = useState(0);

  const source = data || (drawMode ? BLANK_MAP : null);
  const isBlank = !data && drawMode;

  // ── Завантаження HTML карти ────────────────────────────────────────────
  useEffect(() => {
    if (!source) return;
    let cancelled = false;
    setLoading(true);

    getMapHtml(source)
      .then(html => {
        const iframe = iframeRef.current;
        if (cancelled || !iframe) return;
        const doc = iframe.contentDocument || iframe.contentWindow.document;
        doc.open();
        doc.write(html);
        doc.close();
        // Вміст перезаписано — старі посилання на map/шар малювання мертві.
        return waitForMap(iframe).then(({ map }) => {
          if (cancelled) return;
          if (isBlank) map.setZoom(BLANK_MAP_ZOOM);
          setMapReady(v => v + 1);
        });
      })
      .catch(console.error)
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [source]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Клік по карті додає точку ──────────────────────────────────────────
  useEffect(() => {
    if (!drawMode || !mapReady) return;
    const ctx = getMap(iframeRef.current);
    if (!ctx) return;
    const { map } = ctx;

    const onClick = e => setPoints(prev => [...prev, { lat: e.latlng.lat, lng: e.latlng.lng }]);
    map.on('click', onClick);
    map.getContainer().style.cursor = 'crosshair';

    return () => {
      map.off('click', onClick);
      const el = map.getContainer();
      if (el) el.style.cursor = '';
    };
  }, [drawMode, mapReady]);

  // ── Перемальовування намальованого ─────────────────────────────────────
  useEffect(() => {
    if (!mapReady) return;
    if (drawMode) renderDrawLayer(iframeRef.current, points);
    else clearDrawLayer(iframeRef.current);
  }, [points, drawMode, mapReady]);

  // Вихід з режиму малювання — точки більше не потрібні.
  useEffect(() => {
    if (!drawMode) setPoints([]);
  }, [drawMode]);

  const handleFinish = async () => {
    // Точки лишаємо доти, доки пошук не вдався: інакше невдала спроба
    // змусила б малювати все заново.
    const ok = await onFinishDraw(pointsToQuery(points));
    if (ok) setPoints([]);
  };

  // ── Порожній стан ──────────────────────────────────────────────────────
  if (!source) {
    return (
      <div className="map-placeholder">
        <div className="map-hero">
          <svg className="map-hero-icon" width="52" height="52" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 22s7-7.58 7-13A7 7 0 0 0 5 9c0 5.42 7 13 7 13Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
            <circle cx="12" cy="9" r="2.5" stroke="currentColor" strokeWidth="1.5" />
          </svg>
          <h2>Знайдіть земельну ділянку</h2>
          <p>
            Введіть адресу, кадастровий номер або координати меж —
            отримаєте карту, оцінку вартості та PDF-звіт з аналізом
            рельєфу, клімату та інфраструктури.
          </p>
          <ul className="map-hero-examples">
            <li><span>Адреса</span>вул. Хрещатик 1, Київ</li>
            <li><span>Кадастровий номер</span>3221800000:04:001:0123</li>
            <li><span>Координати меж</span>49.77,24.04; 49.78,24.05; 49.77,24.06</li>
          </ul>
          <button className="hero-draw-btn" onClick={onStartDraw}>
            або намалюйте ділянку на карті
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="map-container">
      {loading && <div className="map-overlay">Завантаження карти...</div>}
      <iframe
        ref={iframeRef}
        className="map-iframe"
        title="Leaflet Map"
        sandbox="allow-scripts allow-same-origin"
      />
      {drawMode ? (
        <DrawToolbar
          points={points}
          busy={busy}
          onUndo={() => setPoints(prev => prev.slice(0, -1))}
          onClear={() => setPoints([])}
          onFinish={handleFinish}
          onCancel={onCancelDraw}
        />
      ) : (
        <button className="map-draw-entry" onClick={onStartDraw}>
          Намалювати ділянку
        </button>
      )}
    </div>
  );
}
