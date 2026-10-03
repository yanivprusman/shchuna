// "Which neighbourhood is this street in?" — the other way round from /api/where.
//
// One Nominatim search with dedupe off returns each OSM way of a street as its
// own hit, and each way carries the neighbourhood it runs through; grouping the
// hits by (street, city) gives the street and every neighbourhood along it, each
// with a point to open in full. With a house number the answer is one spot.

import { searchAddressDetailed } from "@automatelinux/geo";
import { norm } from "./zones";

export type StreetPlace = { name: string | null; lat: number; lon: number };

export type StreetMatch = {
  label: string;
  street: string | null;
  houseNumber: string | null;
  city: string | null;
  /** Neighbourhoods along it, in the order the search returned them, each with a point inside. */
  neighborhoods: StreetPlace[];
  /** A point to open when no neighbourhood is mapped. */
  point: { lat: number; lon: number };
};

export async function lookupStreet(text: string): Promise<StreetMatch[]> {
  const hits = await searchAddressDetailed(text, 40);
  const groups = new Map<string, StreetMatch>();

  for (const hit of hits) {
    const a = hit.address;
    const name = a.street ?? a.place ?? a.neighborhood ?? a.city;
    if (!name) continue;
    const key = `${norm(name)}|${norm(a.houseNumber ?? "")}|${norm(a.city ?? "")}`;
    let group = groups.get(key);
    if (!group) {
      const label = [a.street ? [a.street, a.houseNumber].filter(Boolean).join(" ") : name, a.city].filter(Boolean).join(", ");
      group = { label, street: a.street, houseNumber: a.houseNumber, city: a.city, neighborhoods: [], point: { lat: hit.lat, lon: hit.lon } };
      groups.set(key, group);
    }
    const hood = a.neighborhood;
    if (!group.neighborhoods.some((n) => (n.name && hood ? norm(n.name) === norm(hood) : n.name === hood))) {
      group.neighborhoods.push({ name: hood, lat: hit.lat, lon: hit.lon });
    }
  }
  // A group whose only "neighbourhood" is unknown says nothing useful about one.
  for (const g of groups.values()) {
    if (g.neighborhoods.length > 1) g.neighborhoods = g.neighborhoods.filter((n) => n.name);
  }
  return [...groups.values()];
}
