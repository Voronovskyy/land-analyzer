import { polygonAreaM2, hasSelfIntersection } from '../geo';

const MIN_POINTS = 3;   // жорстка вимога бекенду (parseManualPoints)

export default function DrawToolbar({ points, busy, onUndo, onClear, onFinish, onCancel }) {
  const enough = points.length >= MIN_POINTS;
  const areaHa = enough ? polygonAreaM2(points) / 10000 : 0;
  const selfIntersects = enough && hasSelfIntersection(points);

  return (
    <div className="draw-toolbar">
      <div className="draw-status">
        <span className="draw-badge">{points.length}</span>
        {enough
          ? <span>{areaHa.toFixed(4)} га</span>
          : <span>Клікайте по карті — потрібно ще {MIN_POINTS - points.length}</span>}
      </div>

      {selfIntersects && (
        <div className="draw-warning">
          Сторони перетинаються — площа буде хибною. Змініть порядок точок.
        </div>
      )}

      <div className="draw-actions">
        <button onClick={onUndo} disabled={!points.length || busy}>← Точка</button>
        <button onClick={onClear} disabled={!points.length || busy}>Очистити</button>
        <button className="draw-finish" onClick={onFinish} disabled={!enough || busy}>
          {busy ? 'Обробка...' : 'Готово'}
        </button>
        <button className="draw-cancel" onClick={onCancel} disabled={busy}>✕</button>
      </div>
    </div>
  );
}
