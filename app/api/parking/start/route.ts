import { authorize, failure, readLatLon } from "@/lib/auth";
import { startParking } from "@/lib/cello";
import { where } from "@/lib/where";

export const dynamic = "force-dynamic";

/**
 * Start parking where the phone is. Body: { lat, lon, accuracyM?, zoneId?, confirmationCode? }.
 *
 * Without zoneId the zone is chosen from the location, and parking starts ONLY
 * when exactly one zone fits; otherwise 409 with the candidates, so the caller
 * (the app's buttons, or Ben asking the driver) picks. A zone is never guessed —
 * the wrong zone is a parking ticket.
 */
export async function POST(request: Request) {
  const denied = authorize(request);
  if (denied) return denied;
  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  const point = readLatLon(body?.lat, body?.lon);
  if (!point) return Response.json({ error: "lat and lon are required" }, { status: 400 });

  try {
    const place = await where(point.lat, point.lon);
    const cello = place.cello;
    if (cello.kind === "no-city") return Response.json({ outcome: "no-city", message: cello.message, place }, { status: 409 });

    let zoneId: number;
    let zoneName: string;
    if (body?.zoneId != null) {
      zoneId = Number(body.zoneId);
      const all = place.answer.kind === "no-city" ? [] : place.answer.city.zones;
      const chosen = all.find((z) => z.id === zoneId);
      if (!chosen) return Response.json({ error: `zone ${zoneId} is not a ${cello.city} zone` }, { status: 400 });
      zoneName = chosen.name;
    } else if (cello.kind === "one") {
      zoneId = cello.zone.id;
      zoneName = cello.zone.name;
    } else {
      return Response.json(
        { outcome: "choose-zone", message: cello.message, city: cello.city, candidates: cello.kind === "several" ? cello.candidates : cello.others },
        { status: 409 },
      );
    }

    const result = await startParking({
      cityId: cello.cityId,
      zoneId,
      lat: point.lat,
      lon: point.lon,
      accuracyM: body?.accuracyM != null ? Number(body.accuracyM) : undefined,
      confirmationCode: typeof body?.confirmationCode === "string" ? body.confirmationCode : undefined,
    });
    return Response.json({ outcome: result.kind, result, city: cello.city, zone: { id: zoneId, name: zoneName }, address: place.address });
  } catch (error) {
    return failure(error);
  }
}
