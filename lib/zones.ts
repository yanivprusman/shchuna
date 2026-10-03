// Which Cello zone the driver is standing in.
//
// Cello's catalogue gives every zone a NAME and no geometry (lib/cello.ts), so the
// zone is read off the name: names list the streets they cover, name the
// neighbourhood, number the municipal parking areas, or say "the whole city".
// Each kind of name is matched against a fact about the spot — the street and
// neighbourhood from OSM, the area number from the city's own GIS, whether OSM
// puts the spot inside an industrial area.
//
// Never a guess: when two zones match equally (Tel Aviv sells the same area under
// two closing hours; Jerusalem splits by the hour on the street sign) both are
// returned and the answer says the sign decides.

import type { CelloCity, CelloZone } from "./cello";

/** Facts about the spot that zone names can be matched against. */
export type Spot = {
  city: string | null;
  street: string | null;
  neighborhoods: string[];
  /** The city's own parking-area number for the spot (Tel Aviv GIS), when the city publishes one. */
  parkingArea: number | null;
  /** OSM puts the spot inside landuse=industrial; null when the map could not be asked. */
  industrial: boolean | null;
};

export type ZoneMatch = {
  zone: CelloZone;
  /** Why this zone — in Hebrew, shown to the user as-is. */
  reason: string;
  strength: "specific" | "city-wide";
};

export type ZoneAnswer =
  | { kind: "no-city"; message: string }
  | { kind: "one"; city: CelloCity; match: ZoneMatch; others: CelloZone[] }
  | { kind: "several"; city: CelloCity; matches: ZoneMatch[]; message: string; others: CelloZone[] }
  | { kind: "none"; city: CelloCity; message: string; others: CelloZone[] };

/** Letters and digits only: "תל אביב-יפו" ≡ "תל אביב יפו", "שכונה ב'" ≡ "שכונה ב". */
export function norm(s: string): string {
  return s
    .replace(/["'`׳״]/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .replace(/\s+/g, " ");
}

// Nominatim and Cello name a few cities differently.
const CITY_ALIASES: Record<string, string[]> = {
  "תל אביב יפו": ["תל אביב", "יפו"],
  "נוף הגליל נצרת עלית": ["נוף הגליל"],
  "פתח תקוה": ["פתח תקווה"],
  "קרית": ["קריית"],
};

export function findCity(cities: CelloCity[], cityName: string | null): CelloCity | null {
  if (!cityName) return null;
  const want = norm(cityName).replace(/קריית/g, "קרית").replace(/תקווה/g, "תקוה");
  for (const c of cities) {
    const have = norm(c.name).replace(/קריית/g, "קרית").replace(/תקווה/g, "תקוה");
    if (have === want) return c;
    if ((CITY_ALIASES[have] ?? []).some((a) => norm(a) === want)) return c;
    // "נוף הגליל (נצרת עלית)" — the part before the bracket is the city.
    if (norm(c.name.split("(")[0]) === want) return c;
  }
  return null;
}

/** Zones that are parking lots or subscriptions, not paying for the street you are on. */
function isOffStreet(name: string): boolean {
  return /חניון|חניוני|מנוי|חניה חודשית|חנייה חודשית|קמפוס/.test(name);
}

/** The zone name lists the streets it covers. */
function namesStreets(name: string): boolean {
  return /רח'|רחוב|רחובות|שד'|שדרות|\(.+,.+\)/.test(name);
}

function isCityWide(name: string): boolean {
  return /רחבי העיר|כל העיר|כלל העיר/.test(name);
}

function mentionsIndustrial(name: string): boolean {
  return /אזור(י)? ה?תעשי|איזור ה?תעשי|מסחר ותעשי/.test(name);
}

/** "אזורים 1,2,4,12,13" / "אזור 3" → the numbers a zone covers. */
export function areaNumbers(name: string): number[] {
  const m = name.match(/אזור(?:ים|י)?\s*([\d\s,ו-]+)/);
  if (!m) return [];
  return m[1].split(/[\s,ו-]+/).filter(Boolean).map(Number).filter((n) => Number.isInteger(n) && n > 0 && n < 100);
}

/** Whole-word containment on normalised text: "הרצל" is in "ביאליק והרצל" but not in "הרצליה". */
function containsWord(haystack: string, needle: string): boolean {
  const h = ` ${norm(haystack)} `;
  const n = norm(needle);
  if (n.length < 2) return false;
  // A Hebrew conjunction or preposition may be glued to the front: "והרצל", "בהרצל".
  return new RegExp(`[ ](?:[ובהל])?${n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}[ ]`).test(h);
}

export function chooseZone(cities: CelloCity[], spot: Spot): ZoneAnswer {
  const city = findCity(cities, spot.city);
  if (!city) {
    return {
      kind: "no-city",
      message: spot.city ? `Cello לא גובה חניה ב${spot.city}` : "לא ידוע באיזו עיר אתה",
    };
  }

  const street = city.zones.filter((z) => !isOffStreet(z.name));
  const specific: ZoneMatch[] = [];

  for (const zone of street) {
    if (isCityWide(zone.name) && areaNumbers(zone.name).length === 0) continue;

    if (spot.street && containsWord(zone.name, spot.street)) {
      specific.push({ zone, reason: `הרחוב ${spot.street} מופיע בשם האזור`, strength: "specific" });
      continue;
    }
    const nums = areaNumbers(zone.name);
    if (spot.parkingArea != null && nums.includes(spot.parkingArea)) {
      specific.push({ zone, reason: `לפי מפת העירייה אתה באזור חניה ${spot.parkingArea}`, strength: "specific" });
      continue;
    }
    // "שכונה ב': אזור 13 רח' שניאור" is one street of the neighbourhood, not all
    // of it: a zone that names streets matches only on those streets.
    const hood = namesStreets(zone.name) ? undefined : spot.neighborhoods.find((n) => containsWord(zone.name, n));
    if (hood) {
      specific.push({ zone, reason: `השכונה ${hood} מופיעה בשם האזור`, strength: "specific" });
      continue;
    }
    if (spot.industrial === true && mentionsIndustrial(zone.name)) {
      specific.push({ zone, reason: "לפי המפה אתה בתוך אזור תעשייה", strength: "specific" });
    }
  }

  const others = city.zones.filter((z) => !specific.some((m) => m.zone.id === z.id));

  if (specific.length === 1) return { kind: "one", city, match: specific[0], others };
  if (specific.length > 1) {
    return {
      kind: "several",
      city,
      matches: specific,
      message: "כמה אזורים מתאימים למקום הזה — השלט ברחוב קובע (בדרך כלל לפי שעות התשלום)",
      others,
    };
  }

  // Nothing names this spot. A city-wide zone covers it — unless the city has a
  // separate industrial zone and we could not tell whether we are inside it, in
  // which case "the whole city" is still right only outside it, which is what
  // spot.industrial === false says.
  const wide = street.filter((z) => isCityWide(z.name) && areaNumbers(z.name).length === 0);
  const industrialZones = street.filter((z) => mentionsIndustrial(z.name));
  if (spot.industrial === null && industrialZones.length > 0) {
    return {
      kind: "several",
      city,
      matches: [...wide, ...industrialZones].map((zone) => ({
        zone,
        reason: mentionsIndustrial(zone.name) ? "אם אתה בתוך אזור התעשייה" : "אם אתה מחוץ לאזור התעשייה",
        strength: "city-wide" as const,
      })),
      message: "מפת אזורי התעשייה לא ענתה עכשיו, אז אי אפשר לדעת אם אתה בתוכו — בחר לפי המקום",
      others,
    };
  }
  if (wide.length === 1) {
    const reason = street.some((z) => mentionsIndustrial(z.name))
      ? "לא באזור התעשייה ולא באף אזור מיוחד אחר — חל אזור רחבי העיר"
      : "אף אזור מיוחד לא חל כאן — חל אזור רחבי העיר";
    return { kind: "one", city, match: { zone: wide[0], reason, strength: "city-wide" }, others: others.filter((z) => z.id !== wide[0].id) };
  }
  if (wide.length > 1) {
    return {
      kind: "several",
      city,
      matches: wide.map((zone) => ({ zone, reason: "אזור כלל-עירוני", strength: "city-wide" as const })),
      message: "בעיר יש כמה אזורים כלל-עירוניים — השלט ברחוב קובע",
      others,
    };
  }
  return {
    kind: "none",
    city,
    message: `לא מצאתי איזה אזור של ${city.name} חל כאן — בחר לפי השלט`,
    others,
  };
}
