// Геометричні розрахунки для намальованої ділянки.
// Дзеркалить логіку LandParcel3dVisualizer.computeDimensions на бекенді,
// щоб площа в режимі малювання збігалася з тією, яку потім порахує сервер.

const M_PER_LAT = 111319.9;

/** Площа полігону в м² (Shoelace у локальних метричних координатах). */
export function polygonAreaM2(points) {
  if (!points || points.length < 3) return 0;

  const centerLat = points.reduce((s, p) => s + p.lat, 0) / points.length;
  const mPerLon = M_PER_LAT * Math.cos((centerLat * Math.PI) / 180);

  let area = 0;
  for (let i = 0; i < points.length; i++) {
    const j = (i + 1) % points.length;
    const xi = points[i].lng * mPerLon;
    const yi = points[i].lat * M_PER_LAT;
    const xj = points[j].lng * mPerLon;
    const yj = points[j].lat * M_PER_LAT;
    area += xi * yj - xj * yi;
  }
  return Math.abs(area) / 2;
}

/**
 * Формат, який розуміє POST /api/search:
 * "lat,lon; lat,lon; ..." — бекенд розпізнає його за наявністю ',' і ';'.
 * Шість знаків після коми — це ~11 см, більше не має сенсу.
 */
export function pointsToQuery(points) {
  return points.map(p => `${p.lat.toFixed(6)},${p.lng.toFixed(6)}`).join('; ');
}

/** Чи перетинаються самі себе сторони полігону (даватиме хибну площу). */
export function hasSelfIntersection(points) {
  const n = points.length;
  if (n < 4) return false;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      // Сусідні сторони завжди мають спільну вершину — це не перетин.
      if (j === i || (j + 1) % n === i || (i + 1) % n === j) continue;
      if (segmentsIntersect(points[i], points[(i + 1) % n], points[j], points[(j + 1) % n])) {
        return true;
      }
    }
  }
  return false;
}

function segmentsIntersect(p1, p2, p3, p4) {
  const d = (p2.lng - p1.lng) * (p4.lat - p3.lat) - (p2.lat - p1.lat) * (p4.lng - p3.lng);
  if (Math.abs(d) < 1e-12) return false;
  const t = ((p3.lng - p1.lng) * (p4.lat - p3.lat) - (p3.lat - p1.lat) * (p4.lng - p3.lng)) / d;
  const u = ((p3.lng - p1.lng) * (p2.lat - p1.lat) - (p3.lat - p1.lat) * (p2.lng - p1.lng)) / d;
  return t > 0 && t < 1 && u > 0 && u < 1;
}
