import { useState } from 'react';
import { generateReport } from '../api';

const LAYERS = [
  {
    key: 'SCHEME',
    label: 'Схема (OSM)',
    info: 'Топографічна схема з OpenStreetMap: дороги, будівлі, межі. Включає аналіз інфраструктури та анотовану карту з позначками транспортної доступності.',
  },
  {
    key: 'SATELLITE',
    label: 'Супутник',
    info: 'Супутниковий знімок високої роздільності від ESRI. Включає ретроспективний аналіз змін на ділянці за допомогою Claude AI.',
  },
  {
    key: 'TERRAIN',
    label: 'Рельєф',
    info: 'Топографічна карта рельєфу з горизонталями від ESRI. Включає аналіз придатності для будівництва та сільськогосподарського використання.',
  },
  {
    key: 'DEM',
    label: 'Цифрова модель висот',
    info: 'Digital Elevation Model від OpenTopoMap. Показує висотні відмітки поверхні. Включає аналіз рельєфу та ризиків підтоплення.',
  },
  {
    key: 'NDVI',
    label: 'Вегетаційний індекс (NDVI)',
    info: 'Знімок Sentinel-2 від EOX. Зелені зони — активна рослинність, коричневі — голий ґрунт. Включає агрокліматичний аналіз та оцінку родючості.',
  },
  {
    key: 'SLOPE',
    label: 'Ухил поверхні',
    info: 'Hillshade від ESRI Elevation Services. Показує напрямок та крутизну схилів. Включає аналіз ерозійних ризиків та придатності для обробітку.',
  },
];

function InfoTooltip({ text }) {
  const [visible, setVisible] = useState(false);
  return (
    <span className="info-tooltip-wrap">
      <span
        className="info-icon"
        onMouseEnter={() => setVisible(true)}
        onMouseLeave={() => setVisible(false)}
      >
        i
      </span>
      {visible && <span className="info-tooltip">{text}</span>}
    </span>
  );
}

function AiPasswordModal({ onConfirm, onCancel }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!password.trim()) {
      setError('Введіть пароль');
      return;
    }
    onConfirm(password);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-box">
        <h3 className="modal-title">Доступ до AI-аналізу</h3>
        <p className="modal-desc">
          Для генерації звіту з аналізом Claude AI введіть пароль доступу.
        </p>
        <form onSubmit={handleSubmit}>
          <input
            className="modal-input"
            type="password"
            placeholder="Пароль доступу"
            value={password}
            onChange={e => { setPassword(e.target.value); setError(null); }}
            autoFocus
          />
          {error && <div className="modal-error">{error}</div>}
          <div className="modal-actions">
            <button type="button" className="modal-cancel" onClick={onCancel}>
              Скасувати
            </button>
            <button type="submit" className="modal-confirm">
              Підтвердити
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

const progressLabel = (p, aiEnabled) => {
  if (p < 20) return 'Збираємо дані про ділянку...';
  if (p < 45) return 'Генеруємо карти шарів...';
  if (p < 75) return aiEnabled ? 'AI аналізує ділянку...' : 'Формуємо PDF...';
  if (p < 100) return 'Завершуємо звіт...';
  return 'Готово!';
};

export default function ReportPanel({ searchData }) {
  const [layers, setLayers] = useState(['SCHEME', 'SATELLITE', 'TERRAIN']);
  const [aiEnabled, setAiEnabled] = useState(false);
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState(null);
  const [showPasswordModal, setShowPasswordModal] = useState(false);

  if (!searchData) return null;

  const toggleLayer = (key) => {
    setLayers(prev =>
      prev.includes(key) ? prev.filter(l => l !== key) : [...prev, key]
    );
  };

  const handleGenerateClick = () => {
    if (layers.length === 0) {
      setError('Оберіть хоча б один шар карти');
      return;
    }
    setError(null);
    if (aiEnabled) {
      setShowPasswordModal(true);
    } else {
      runGenerate(null);
    }
  };

  const handlePasswordConfirm = (password) => {
    setShowPasswordModal(false);
    runGenerate(password);
  };

  const runGenerate = async (aiPassword) => {
    setLoading(true);
    setProgress(0);
    const interval = setInterval(() => {
      setProgress(p => Math.min(92, p + (92 - p) * 0.06 + 0.4));
    }, 400);
    try {
      await generateReport({
        lat: searchData.lat,
        lon: searchData.lon,
        title: `${searchData.lat.toFixed(6)}, ${searchData.lon.toFixed(6)}`,
        areaHa: searchData.areaHa,
        priceUah: searchData.priceUah,
        priceUsd: searchData.priceUsd,
        elevation: searchData.elevation,
        suitability: searchData.suitability,
        geoJson: searchData.geoJson,
        boundaries: searchData.boundaries,
        layers,
        aiEnabled,
        aiPassword,
      });
      clearInterval(interval);
      setProgress(100);
      await new Promise(r => setTimeout(r, 400));
    } catch (e) {
      clearInterval(interval);
      if (e.response?.status === 401) {
        setError('Невірний пароль. AI-аналіз недоступний.');
      } else {
        setError('Помилка генерації звіту. Спробуйте ще раз.');
      }
      console.error(e);
    } finally {
      setLoading(false);
      setProgress(0);
    }
  };

  return (
    <>
      <div className="report-panel">
        <h3>Генерація PDF-звіту</h3>

        <div className="layer-list">
          {LAYERS.map(l => (
            <div key={l.key} className="layer-item">
              <label className="layer-label">
                <input
                  type="checkbox"
                  checked={layers.includes(l.key)}
                  onChange={() => toggleLayer(l.key)}
                  disabled={loading}
                />
                {l.label}
              </label>
              <InfoTooltip text={l.info} />
            </div>
          ))}
        </div>

        <label className="ai-toggle">
          <input
            type="checkbox"
            checked={aiEnabled}
            onChange={e => setAiEnabled(e.target.checked)}
            disabled={loading}
          />
          Аналіз Claude AI (займає ~60 сек)
          <InfoTooltip text="Вмикає 6 аналізів від Claude AI: інфраструктура, рельєф, DEM, NDVI, ретроспектива, ухили. Кожен аналіз — окрема сторінка у PDF з текстовими висновками." />
        </label>

        {error && <div className="error-msg">{error}</div>}

        <button
          className="generate-btn"
          onClick={handleGenerateClick}
          disabled={loading}
        >
          {loading ? 'Генерація PDF...' : 'Завантажити PDF-звіт'}
        </button>

        {loading && (
          <div className="progress-wrap">
            <div className="progress-bar">
              <div className="progress-fill" style={{ width: `${progress}%` }} />
            </div>
            <div className="progress-text">{progressLabel(progress, aiEnabled)}</div>
          </div>
        )}
      </div>

      {showPasswordModal && (
        <AiPasswordModal
          onConfirm={handlePasswordConfirm}
          onCancel={() => setShowPasswordModal(false)}
        />
      )}
    </>
  );
}
