// Government data about the town: the population registry's count by locality,
// published on data.gov.il (resource 64edd0ee… "אוכלוסייה לפי יישובים").
// One row per locality — total residents, the age split, נפה and the regional
// council for a village. Cached a day: the registry updates it a few times a year.

import { norm } from "./zones";

const RESOURCE = "64edd0ee-3d5d-43ce-8562-c336c24dbc1f";
const TTL_MS = 24 * 60 * 60 * 1000;

export type Locality = {
  code: number;
  name: string;
  subdistrict: string | null;
  regionalCouncil: string | null;
  population: number;
  ages: { label: string; count: number }[];
  source: string;
};

type Row = Record<string, string | number>;

const cache = new Map<string, { value: Locality | null; at: number }>();

export async function locality(name: string): Promise<Locality | null> {
  const key = norm(name);
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < TTL_MS) return hit.value;

  const url = `https://data.gov.il/api/3/action/datastore_search?resource_id=${RESOURCE}&q=${encodeURIComponent(name)}&limit=20`;
  const response = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!response.ok) throw new Error(`data.gov.il answered ${response.status}`);
  const body = (await response.json()) as { success: boolean; result: { records: Row[] } };
  if (!body.success) throw new Error("data.gov.il refused the query");

  // Full-text search also returns "חולון" for "אזור" etc.; keep only the exact name.
  const row = body.result.records.find((r) => norm(String(r["שם_ישוב"])) === key) ?? null;
  const value: Locality | null = row
    ? {
        code: Number(row["סמל_ישוב"]),
        name: String(row["שם_ישוב"]).trim(),
        subdistrict: String(row["נפה"] || "").trim() || null,
        regionalCouncil: String(row["מועצה_אזורית"] || "").trim() || null,
        population: Number(row["סהכ"]),
        ages: [
          { label: "0–5", count: Number(row["גיל_0_5"]) },
          { label: "6–18", count: Number(row["גיל_6_18"]) },
          { label: "19–45", count: Number(row["גיל_19_45"]) },
          { label: "46–55", count: Number(row["גיל_46_55"]) },
          { label: "56–64", count: Number(row["גיל_56_64"]) },
          { label: "65+", count: Number(row["גיל_65_פלוס"]) },
        ],
        source: "רשות האוכלוסין, data.gov.il",
      }
    : null;
  cache.set(key, { value, at: Date.now() });
  return value;
}
