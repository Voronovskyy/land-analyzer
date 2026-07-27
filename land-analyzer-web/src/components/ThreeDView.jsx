import { useEffect, useRef, useState } from 'react';

export default function ThreeDView({ boundaries, elevation, onClose }) {
  const canvasRef = useRef(null);
  const [rotAngle, setRotAngle] = useState(0);
  const dragRef = useRef(null);

  useEffect(() => {
    if (!canvasRef.current) return;
    draw(canvasRef.current, boundaries, elevation, rotAngle);
  }, [boundaries, elevation, rotAngle]);

  const handleMouseDown = (e) => {
    dragRef.current = { startX: e.clientX, startAngle: rotAngle };
  };
  const handleMouseMove = (e) => {
    if (!dragRef.current) return;
    const dx = e.clientX - dragRef.current.startX;
    setRotAngle(dragRef.current.startAngle + dx * 0.01);
  };
  const handleMouseUp = () => { dragRef.current = null; };

  if (!boundaries || boundaries.length < 3) return null;

  return (
    <div className="threed-overlay">
      <div className="threed-window">
        <div className="threed-header">
          <span>3D Модель ділянки</span>
          <span className="threed-hint">← перетягни для обертання →</span>
          <button className="threed-close" onClick={onClose}>✕</button>
        </div>
        <canvas
          ref={canvasRef}
          width={820}
          height={420}
          style={{ cursor: 'grab', display: 'block' }}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
        />
      </div>
    </div>
  );
}

// ── Canvas drawing (mirrors LandParcel3dVisualizer.java) ──────────────────

function draw(canvas, boundaries, elevation, rotAngle) {
  const W = canvas.width, H = canvas.height;
  const ctx = canvas.getContext('2d');

  // Background gradient
  const bg = ctx.createLinearGradient(0, 0, 0, H);
  bg.addColorStop(0, '#1a2533');
  bg.addColorStop(1, '#2c3e50');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  // Grid
  ctx.strokeStyle = 'rgba(61,81,102,0.5)';
  ctx.lineWidth = 0.5;
  for (let x = 0; x < W; x += 40) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke(); }
  for (let y = 0; y < H; y += 40) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke(); }

  const n = boundaries.length;
  const lats = boundaries.map(b => b.lat ?? b.latitude ?? b[0]);
  const lons = boundaries.map(b => b.lon ?? b.longitude ?? b[1]);

  const minLat = Math.min(...lats), maxLat = Math.max(...lats);
  const minLon = Math.min(...lons), maxLon = Math.max(...lons);
  const centerLat = (minLat + maxLat) / 2;
  const centerLon = (minLon + maxLon) / 2;
  const span = Math.max(maxLat - minLat, maxLon - minLon) || 1e-9;
  const scale = (W * 0.52) / span;

  const cos = Math.cos(rotAngle), sin = Math.sin(rotAngle);
  const topPad = 18, botBar = 38;
  const centerY = topPad + (H - topPad - botBar) * 0.48;

  const xP = [], yP = [];
  for (let i = 0; i < n; i++) {
    const dx = (lons[i] - centerLon) * scale;
    const dy = (lats[i] - centerLat) * scale;
    const rdx = dx * cos - dy * sin;
    const rdy = dx * sin + dy * cos;
    xP.push(W / 2 + (rdx - rdy) * 0.82);
    yP.push(centerY - (rdx + rdy) * 0.40);
  }

  const depth = Math.min(28, Math.max(14, elevation / 20));

  // Side faces
  for (let i = 0; i < n; i++) {
    const next = (i + 1) % n;
    const visible = (yP[i] + yP[next]) / 2 > centerY - depth;
    if (!visible) continue;
    const brightness = 0.25 + 0.15 * Math.abs(xP[next] - xP[i]) / (W / 2);
    ctx.fillStyle = `rgba(26,37,47,${brightness + 0.4})`;
    ctx.beginPath();
    ctx.moveTo(xP[i], yP[i]);
    ctx.lineTo(xP[next], yP[next]);
    ctx.lineTo(xP[next], yP[next] + depth);
    ctx.lineTo(xP[i], yP[i] + depth);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = 'rgba(13,27,42,0.6)';
    ctx.lineWidth = 0.5;
    ctx.stroke();
  }

  // Top surface gradient
  const grad = ctx.createLinearGradient(0, 0, 0, H);
  grad.addColorStop(0, 'rgba(46,204,113,0.95)');
  grad.addColorStop(1, 'rgba(39,174,96,0.95)');
  ctx.fillStyle = grad;
  ctx.beginPath();
  ctx.moveTo(xP[0], yP[0]);
  for (let i = 1; i < n; i++) ctx.lineTo(xP[i], yP[i]);
  ctx.closePath();
  ctx.fill();

  // Glow outline
  ctx.strokeStyle = 'rgba(168,240,198,0.8)';
  ctx.lineWidth = 1.5;
  ctx.stroke();

  // Inner grid lines
  ctx.strokeStyle = 'rgba(255,255,255,0.12)';
  ctx.lineWidth = 0.6;
  ctx.setLineDash([3, 3]);
  for (let i = 0; i < n; i++) {
    const opp = (i + Math.floor(n / 2)) % n;
    ctx.beginPath(); ctx.moveTo(xP[i], yP[i]); ctx.lineTo(xP[opp], yP[opp]); ctx.stroke();
  }
  ctx.setLineDash([]);

  // Corner dots
  ctx.fillStyle = '#fff';
  for (let i = 0; i < n; i++) {
    ctx.beginPath(); ctx.arc(xP[i], yP[i], 3, 0, Math.PI * 2); ctx.fill();
  }

  // Segment labels
  ctx.fillStyle = 'rgba(240,240,240,0.85)';
  ctx.font = '9px Arial';
  const mPerLon = 111319.9 * Math.cos((centerLat * Math.PI) / 180);
  for (let i = 0; i < n; i++) {
    const next = (i + 1) % n;
    const dlat = (lats[next] - lats[i]) * 111319.9;
    const dlon = (lons[next] - lons[i]) * mPerLon;
    const segM = Math.sqrt(dlat * dlat + dlon * dlon);
    if (segM < 1) continue;
    const mx = (xP[i] + xP[next]) / 2;
    const my = (yP[i] + yP[next]) / 2 - 5;
    ctx.fillText(`${segM.toFixed(0)} м`, mx - 10, my);
  }

  // Compass
  drawCompass(ctx, W - 44, 44, rotAngle);

  // Info bar
  drawInfoBar(ctx, W, H, lats, lons, elevation, centerLat);
}

function drawCompass(ctx, cx, cy, rotAngle) {
  const r = 16;
  ctx.strokeStyle = 'rgba(170,187,204,0.5)';
  ctx.lineWidth = 1;
  ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2); ctx.stroke();

  const nx = cx + r * 0.75 * Math.sin(-rotAngle);
  const ny = cy - r * 0.75 * Math.cos(-rotAngle);
  ctx.strokeStyle = '#e74c3c'; ctx.lineWidth = 2;
  ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(nx, ny); ctx.stroke();

  ctx.fillStyle = '#e74c3c'; ctx.font = 'bold 10px Arial';
  ctx.fillText('N', cx + r * 0.9 * Math.sin(-rotAngle) - 4, cy - r * 0.9 * Math.cos(-rotAngle) + 4);
}

function drawInfoBar(ctx, W, H, lats, lons, elevation, centerLat) {
  const barH = 36, y0 = H - barH;
  ctx.fillStyle = 'rgba(13,27,42,0.85)';
  ctx.fillRect(0, y0, W, barH);
  ctx.strokeStyle = 'rgba(39,174,96,0.6)'; ctx.lineWidth = 1;
  ctx.beginPath(); ctx.moveTo(0, y0); ctx.lineTo(W, y0); ctx.stroke();

  const mPerLat = 111319.9;
  const mPerLon = 111319.9 * Math.cos((centerLat * Math.PI) / 180);
  const widthM = (Math.max(...lons) - Math.min(...lons)) * mPerLon;
  const heightM = (Math.max(...lats) - Math.min(...lats)) * mPerLat;

  const n = lats.length;
  let perim = 0, area = 0;
  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n;
    const dlat = (lats[j] - lats[i]) * mPerLat;
    const dlon = (lons[j] - lons[i]) * mPerLon;
    perim += Math.sqrt(dlat * dlat + dlon * dlon);
    area += lons[i] * mPerLon * lats[j] * mPerLat - lons[j] * mPerLon * lats[i] * mPerLat;
  }
  area = Math.abs(area) / 2;

  ctx.font = '9px Arial'; ctx.fillStyle = '#95a5a6';
  const col = W / 4;
  [
    `ШИРИНА  ${widthM.toFixed(0)} м`,
    `ДОВЖИНА  ${heightM.toFixed(0)} м`,
    `ПЕРИМЕТР  ${perim.toFixed(0)} м`,
    `ВИСОТА  ${elevation.toFixed(1)} м`,
  ].forEach((t, i) => ctx.fillText(t, col * i + 8, y0 + 14));

  ctx.fillStyle = '#27ae60'; ctx.font = 'bold 9px Arial';
  ctx.fillText(`ПЛОЩА  ${(area / 10000).toFixed(4)} га`, 8, y0 + 28);
  ctx.fillStyle = '#4a6278'; ctx.font = '8px Arial';
  ctx.fillText('WGS84 · Isometric Projection', W - 155, y0 + 28);
}
