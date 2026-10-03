import { timingSafeEqual } from "node:crypto";
import { config } from "./config";
import { describe } from "./errors";

/**
 * Every shchuna route needs the bearer token. The parking routes spend real
 * money on the owner's Cello account, and the server also listens on the LAN.
 */
export function authorize(request: Request): Response | null {
  const header = request.headers.get("authorization") ?? "";
  const given = Buffer.from(header.replace(/^Bearer\s+/i, ""));
  const want = Buffer.from(config().apiToken);
  if (given.length !== want.length || !timingSafeEqual(given, want)) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }
  return null;
}

export function readLatLon(lat: unknown, lon: unknown): { lat: number; lon: number } | null {
  const a = Number(lat);
  const b = Number(lon);
  if (!Number.isFinite(a) || !Number.isFinite(b) || Math.abs(a) > 90 || Math.abs(b) > 180) return null;
  return { lat: a, lon: b };
}

export function failure(error: unknown): Response {
  const message = describe(error);
  const status = (error as { status?: number }).status;
  return Response.json({ error: message }, { status: status && status >= 400 && status < 600 ? status : 502 });
}
