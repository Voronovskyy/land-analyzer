import { useEffect, useRef, useState } from 'react';
import { getMapHtml } from '../api';

export default function MapView({ data }) {
  const iframeRef = useRef(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!data) return;
    setLoading(true);
    getMapHtml(data)
      .then(html => {
        const iframe = iframeRef.current;
        if (!iframe) return;
        const doc = iframe.contentDocument || iframe.contentWindow.document;
        doc.open();
        doc.write(html);
        doc.close();
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [data]);

  if (!data) {
    return (
      <div className="map-placeholder">
        <div className="map-hero">
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
    </div>
  );
}
