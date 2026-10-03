// Everything known about one point: the address breakdown (OSM via
// @automatelinux/geo), the OSM areas the point is inside (industrial land,
// neighbourhood polygons), and — in Tel Aviv — the municipal parking-area number.

import { reverseGeocodeDetailed, type AddressBreakdown } from "@automatelinux/geo";
import { norm, type Spot } from "./zones";
import { describe } from "./errors";

const USER_AGENT = "automateLinux-shchuna/0.1 (yanivprusman@gmail.com)";

export type OsmAreas = {
  /** null when Overpass could not be asked — said so to the user, never read as "no". */
  industrial: boolean | null;
  /** Named place=neighbourhood / suburb / quarter polygons containing the point. */
  neighborhoods: string[];
  /** Named landuse or leisure the point is in (a park, a campus, a mall). */
  landmarks: string[];
  /** Why the areas are unknown, when they are. */
  unavailable?: string;
};

const areaCache = new Map<string, { value: OsmAreas; at: number }>();
const AREA_TTL_MS = 24 * 60 * 60 * 1000;

/** OSM areas containing the point, from Overpass `is_in` — one request, cached at ~10 m. */
export async function osmAreas(lat: number, lon: number): Promise<OsmAreas> {
  const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
  const hit = areaCache.get(key);
  if (hit && Date.now() - hit.at < AREA_TTL_MS) return hit.value;

  const query = `[out:json][timeout:15];is_in(${lat},${lon})->.a;area.a;out tags;`;
  const ask = () =>
    fetch("https://overpass-api.de/api/interpreter", {
      method: "POST",
      headers: { "User-Agent": USER_AGENT, "Content-Type": "application/x-www-form-urlencoded" },
      body: `data=${encodeURIComponent(query)}`,
      signal: AbortSignal.timeout(20_000),
    });
  // The public Overpass instance sheds load with 429/504 for a few seconds at a
  // time; one retry after a pause gets through most of those.
  let response = await ask();
  if (response.status === 429 || response.status === 504) {
    await new Promise((r) => setTimeout(r, 3000));
    response = await ask();
  }
  if (!response.ok) return { industrial: null, neighborhoods: [], landmarks: [], unavailable: `Overpass answered ${response.status}` };
  const body = (await response.json()) as { elements: { tags?: Record<string, string> }[] };

  const value: OsmAreas = { industrial: false, neighborhoods: [], landmarks: [] };
  for (const { tags } of body.elements) {
    if (!tags) continue;
    const name = tags["name:he"] || tags.name;
    if (tags.landuse === "industrial") value.industrial = true;
    if (name && ["neighbourhood", "suburb", "quarter"].includes(tags.place ?? "")) value.neighborhoods.push(name);
    if (name && (tags.leisure || tags.amenity || tags.landuse === "retail") && !tags.boundary) value.landmarks.push(name);
  }
  areaCache.set(key, { value, at: Date.now() });
  return value;
}

/**
 * Tel Aviv-Yafo's parking-area number (layer 544 "אזורי חניה" of the city's
 * public GIS) — the numbers Cello's Tel Aviv zone names list.
 */
export async function telAvivParkingArea(lat: number, lon: number): Promise<number | null> {
  const url =
    "https://gisn.tel-aviv.gov.il/arcgis/rest/services/IView2/MapServer/544/query" +
    `?geometry=${lon},${lat}&geometryType=esriGeometryPoint&inSR=4326&spatialRel=esriSpatialRelIntersects` +
    "&outFields=ms_ezor&returnGeometry=false&f=json";
  const response = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!response.ok) throw new Error(`Tel Aviv GIS answered ${response.status}`);
  const body = (await response.json()) as { features?: { attributes: { ms_ezor: number } }[]; error?: { message: string } };
  if (body.error) throw new Error(`Tel Aviv GIS: ${body.error.message}`);
  return body.features?.[0]?.attributes.ms_ezor ?? null;
}

/**
 * Two independent signs of an industrial area: OSM landuse=industrial around the
 * point, or the address itself naming one ("אזור תעשייה ג'"). Either is enough;
 * unknown only when Overpass was unreachable AND the address says nothing.
 */
function industrialFrom(areas: OsmAreas, neighborhoods: string[]): boolean | null {
  if (areas.industrial === true) return true;
  if (neighborhoods.some((n) => /אזור(י)? ה?תעשי|איזור ה?תעשי|פארק תעשי|קרית תעשי|קריית תעשי/.test(n))) return true;
  return areas.industrial;
}

export type SpotFacts = { address: AddressBreakdown; areas: OsmAreas; parkingArea: number | null; parkingAreaError: string | null; spot: Spot };

export async function spotFacts(lat: number, lon: number): Promise<SpotFacts> {
  const [address, areas] = await Promise.all([reverseGeocodeDetailed(lat, lon), osmAreas(lat, lon)]);
  const isTelAviv = address.city != null && norm(address.city).startsWith("תל אביב");
  // Tel Aviv's GIS only sharpens the zone; when it is down the zone choice says it
  // could not tell (no area number ⇒ no area-numbered zone matches) instead of the
  // whole answer failing.
  let parkingArea: number | null = null;
  let parkingAreaError: string | null = null;
  if (isTelAviv) {
    try {
      parkingArea = await telAvivParkingArea(lat, lon);
    } catch (e) {
      parkingAreaError = describe(e);
    }
  }

  const neighborhoods = [address.neighborhood, address.quarter, ...areas.neighborhoods].filter(
    (n, i, all): n is string => !!n && all.findIndex((m) => m && norm(m) === norm(n)) === i,
  );
  return {
    address,
    areas,
    parkingArea,
    parkingAreaError,
    spot: { city: address.city, street: address.street, neighborhoods, parkingArea, industrial: industrialFrom(areas, neighborhoods) },
  };
}
