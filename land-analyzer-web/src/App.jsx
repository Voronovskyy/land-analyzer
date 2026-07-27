import { useState } from 'react';
import SearchBar from './components/SearchBar';
import MapView from './components/MapView';
import PricePanel from './components/PricePanel';
import ReportPanel from './components/ReportPanel';
import ThreeDView from './components/ThreeDView';
import { searchPlot } from './api';
import './App.css';

export default function App() {
  const [searchData, setSearchData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [show3d, setShow3d] = useState(false);

  const handleSearch = async (query) => {
    setLoading(true);
    setError(null);
    try {
      const data = await searchPlot(query);
      setSearchData(data);
    } catch (e) {
      if (e.response?.status === 404) {
        setError('Ділянку не знайдено. Спробуйте іншу адресу або кадастровий номер.');
      } else {
        setError('Помилка з\'єднання з сервером. Переконайтесь, що backend запущений.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-content">
          <div className="header-titles">
            <h1>АНАЛІЗ ЗЕМЕЛЬНИХ ДІЛЯНОК</h1>
            <span className="subtitle">Інтелектуальна система геоаналізу</span>
          </div>
          <span className="phd-badge">PhD Research</span>
        </div>
      </header>

      <main className="app-main">
        <div className="search-section">
          <SearchBar onSearch={handleSearch} loading={loading} />
          {error && <div className="error-banner">{error}</div>}
        </div>

        <div className="content-grid">
          <div className="map-section">
            <MapView data={searchData} />
          </div>

          <aside className="side-panel">
            <PricePanel data={searchData} />
            {searchData && (
              <button className="threed-btn" onClick={() => setShow3d(true)}>
                3D Модель ділянки
              </button>
            )}
            <ReportPanel searchData={searchData} />
          </aside>
        </div>
      </main>

      {show3d && (
        <ThreeDView
          boundaries={searchData?.boundaries}
          elevation={searchData?.elevation ?? 0}
          onClose={() => setShow3d(false)}
        />
      )}

      <footer className="app-footer">
        LandPlot Analyzer &copy; 2024 &mdash; PhD Research Project
      </footer>
    </div>
  );
}
