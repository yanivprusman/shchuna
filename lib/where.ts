// The one answer the app shows: where you are, what the government knows about the
// town, and which Cello zone to pick.

import { cities } from "./cello";
import { locality, type Locality } from "./gov";
import { spotFacts } from "./spot";
import { chooseZone, type ZoneAnswer } from "./zones";

export type ZoneView = { id: number; name: string; reason?: string };

export type CelloView =
  | { kind: "no-city"; message: string }
  | { kind: "one"; cityId: number; city: string; zone: ZoneView; others: ZoneView[] }
  | { kind: "several"; cityId: number; city: string; candidates: ZoneView[]; message: string; others: ZoneView[] }
  | { kind: "none"; cityId: number; city: string; message: string; others: ZoneView[] };

export function celloView(answer: ZoneAnswer): CelloView {
  const plain = (zs: { id: number; name: string }[]) => zs.map((z) => ({ id: z.id, name: z.name }));
  switch (answer.kind) {
    case "no-city":
      return answer;
    case "one":
      return {
        kind: "one", cityId: answer.city.id, city: answer.city.name,
        zone: { id: answer.match.zone.id, name: answer.match.zone.name, reason: answer.match.reason },
        others: plain(answer.others),
      };
    case "several":
      return {
        kind: "several", cityId: answer.city.id, city: answer.city.name, message: answer.message,
        candidates: answer.matches.map((m) => ({ id: m.zone.id, name: m.zone.name, reason: m.reason })),
        others: plain(answer.others),
      };
    case "none":
      return { kind: "none", cityId: answer.city.id, city: answer.city.name, message: answer.message, others: plain(answer.others) };
  }
}

export async function where(lat: number, lon: number) {
  const facts = await spotFacts(lat, lon);
  const [catalogue, gov] = await Promise.all([
    cities(),
    facts.address.city ? locality(facts.address.city) : Promise.resolve<Locality | null>(null),
  ]);
  const answer = chooseZone(catalogue, facts.spot);
  return {
    point: { lat, lon },
    address: facts.address,
    neighborhoods: facts.spot.neighborhoods,
    landmarks: facts.areas.landmarks,
    industrial: facts.areas.industrial,
    parkingArea: facts.parkingArea,
    locality: gov,
    cello: celloView(answer),
    answer,
  };
}
