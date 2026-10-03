// Zone choice against real Cello names (catalogue of 2026-10-03).
import { test } from "node:test";
import assert from "node:assert/strict";
import { chooseZone, areaNumbers, findCity, type Spot } from "../lib/zones.ts";
import type { CelloCity } from "../lib/cello.ts";

const z = (id: number, name: string) => ({ id, code: String(id), name, tariffType: 1 });
const CITIES: CelloCity[] = [
  { id: 28, name: "חולון", code: "6600", zones: [z(324, "רחבי העיר"), z(325, "אזור התעשייה")] },
  { id: 11, name: "באר שבע", code: "9000", zones: [
    z(24, "רחבי העיר"), z(25, "עיר העתיקה"), z(56, "חניון משכן לאומנויות הבמה"),
    z(251, "שד' רגר ורח' ליאונרד ברנשטיין"), z(268, "שכונה ב': אזור 13 רח' שניאור"),
  ] },
  { id: 99, name: "תל אביב-יפו", code: "5000", zones: [
    z(58, "רחבי העיר(אזורים 1,2,4,12,13)–חניה בתשלום עד 17:00 כולל שישי."),
    z(326, "מרכז העיר (אזורים 6,7,9,10) – חניה בתשלום עד 17:00 כולל שישי."),
    z(327, "מרכז העיר (אזורים 6,7,9,10)– חניה בתשלום עד 21:00. בששי עד 17:00"),
  ] },
  { id: 91, name: "רמת גן", code: "8600", zones: [z(21, "רחבי העיר"), z(393, "ביאליק והרצל")] },
];
const spot = (s: Partial<Spot>): Spot => ({ city: null, street: null, neighborhoods: [], parkingArea: null, industrial: false, ...s });

test("Holon outside the industrial area is the city-wide zone", () => {
  const a = chooseZone(CITIES, spot({ city: "חולון", street: "קדושי בלזן" }));
  assert.equal(a.kind, "one");
  assert.equal(a.kind === "one" && a.match.zone.id, 324);
});

test("Holon inside OSM industrial land is the industrial zone", () => {
  const a = chooseZone(CITIES, spot({ city: "חולון", street: "המלאכה", industrial: true }));
  assert.equal(a.kind === "one" && a.match.zone.id, 325);
});

test("an unknown industrial answer offers both rather than assuming outside", () => {
  const a = chooseZone(CITIES, spot({ city: "חולון", industrial: null }));
  assert.equal(a.kind, "several");
});

test("a zone naming one street of a neighbourhood does not claim the whole neighbourhood", () => {
  const a = chooseZone(CITIES, spot({ city: "באר שבע", street: "חיים נחמן ביאליק", neighborhoods: ["שכונה ב'"] }));
  assert.equal(a.kind === "one" && a.match.zone.id, 24);
});

test("a listed street matches, with or without a glued conjunction", () => {
  const a = chooseZone(CITIES, spot({ city: "באר שבע", street: "רגר" }));
  assert.equal(a.kind === "one" && a.match.zone.id, 251);
  const b = chooseZone(CITIES, spot({ city: "רמת גן", street: "הרצל" }));
  assert.equal(b.kind === "one" && b.match.zone.id, 393);
});

test("a street name inside a longer word is not a match", () => {
  const a = chooseZone(CITIES, spot({ city: "רמת גן", street: "הרצליה" }));
  assert.equal(a.kind === "one" && a.match.zone.id, 21);
});

test("Tel Aviv area numbers pick the zones, and the hours split is left to the sign", () => {
  const a = chooseZone(CITIES, spot({ city: "תל־אביב–יפו", parkingArea: 10 }));
  assert.equal(a.kind, "several");
  assert.deepEqual(a.kind === "several" && a.matches.map((m) => m.zone.id), [326, 327]);
  const b = chooseZone(CITIES, spot({ city: "תל אביב-יפו", parkingArea: 12 }));
  assert.equal(b.kind === "one" && b.match.zone.id, 58);
});

test("parking lots are never offered for the street", () => {
  const a = chooseZone(CITIES, spot({ city: "באר שבע", street: "משכן לאומנויות הבמה" }));
  assert.equal(a.kind === "one" && a.match.zone.id, 24);
});

test("a city Cello does not serve says so", () => {
  assert.equal(chooseZone(CITIES, spot({ city: "מדרשת בן גוריון" })).kind, "no-city");
});

test("area numbers and city names parse", () => {
  assert.deepEqual(areaNumbers("רחבי העיר(אזורים 1,2,4,12,13)–חניה"), [1, 2, 4, 12, 13]);
  assert.equal(findCity(CITIES, "תל־אביב–יפו")?.id, 99);
});
