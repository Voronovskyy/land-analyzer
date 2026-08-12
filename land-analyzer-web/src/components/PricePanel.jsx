export default function PricePanel({ data }) {
  if (!data) return null;

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

      <div className="suitability-pending">
        Придатність: буде розрахована у повному звіті
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
