export default function PricePanel({ data }) {
  if (!data) return null;

  const suitabilityColor = (s) => {
    if (s >= 0.75) return '#27ae60';
    if (s >= 0.50) return '#f39c12';
    return '#e74c3c';
  };

  const suitabilityPct = (data.suitability * 100);

  const rows = [
    { label: 'Площа', value: data.areaHa },
    { label: 'Висота', value: `${data.elevation?.toFixed(1)} м` },
    { label: 'Ціна (UAH)', value: data.priceUah },
    { label: 'Ціна (USD)', value: data.priceUsd },
    { label: 'Курс USD', value: `₴${data.rate?.toFixed(2)}` },
  ];

  return (
    <div className="price-panel">
      <h3>Результат аналізу</h3>
      <div className="coords">
        {data.lat?.toFixed(6)}, {data.lon?.toFixed(6)}
      </div>

      <div className="suitability">
        <span>Придатність:</span>
        <span className="score" style={{ color: suitabilityColor(data.suitability) }}>
          {suitabilityPct.toFixed(1)}%
        </span>
        <div className="score-bar">
          <div
            className="score-fill"
            style={{
              width: `${suitabilityPct}%`,
              background: suitabilityColor(data.suitability),
            }}
          />
        </div>
      </div>

      <table className="info-table">
        <tbody>
          {rows.map(r => (
            <tr key={r.label}>
              <td className="label">{r.label}</td>
              <td className="value">{r.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
