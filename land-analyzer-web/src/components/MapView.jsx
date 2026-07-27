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
        <div className="map-hint">
          Введіть адресу або кадастровий номер для відображення карти
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
