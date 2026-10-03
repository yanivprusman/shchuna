// Cello (formerly Cellopark) — the smartphone API its Android app talks to.
//
// Reverse-read from the app (air.com.cellogroup.Cellopark 12.31, 2026-10-03); none
// of this is a published API, so every call checks the shape it gets back and
// fails with the server's own message rather than guessing.
//
//   auth   "Authorization: Basic <AuthorizationToken>" from Account/Login, plus the
//          App-Key that App/RegisterApp hands out. Both live in shchuna.env.
//   zones  Location/GetLocations — ~100 cities, ~360 zones. A zone is a NAME and
//          nothing else: GeoLocation is null on every one, and the app's own
//          Location/GetLocationsByGeoLocation answers 404 everywhere. Choosing the
//          zone is therefore ours to do (lib/zones.ts), not Cello's.
//   park   ParkingSession/StartParking, /StopParking, /GetActiveParkingSessions.

import { config } from "./config";

const BASE = "https://application.cellopark.co.il/smartphone-api/";
// The app sends Source=2 for a start from the phone and MethodClose=-1 for a
// manual stop (StopParkingDetails.MANUAL → -1 in ParkingSessionManagerImpl).
const SOURCE_APP = 2;
const CLOSE_MANUAL = -1;

export type CelloZone = { id: number; code: string; name: string; tariffType: number | null };
export type CelloCity = { id: number; name: string; code: string; zones: CelloZone[] };

export type ActiveSession = {
  transactionId: number;
  cityId: number;
  zoneId: number | null;
  zoneName: string | null;
  carNumber: string;
  startedAt: string | null;
  amount: number | null;
};

type BaseResponse = { Status: number; Message: string | null; ConfirmCode?: string | null };

export class CelloError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

function headers(): Record<string, string> {
  const c = config();
  return {
    "Device-OS": "android",
    "Device-Name": "shchuna",
    "Accept-Language": "he-IL",
    "OS-Version": "35",
    "App-Version": "12.31",
    "App-Key": c.celloAppKey,
    // The app sends one SessionID per launch; the server only needs it present.
    SessionID: "shchuna",
    Authorization: `Basic ${c.celloToken}`,
  };
}

async function call<T extends BaseResponse>(path: string, init?: { method: "POST"; body: unknown }): Promise<T> {
  const response = await fetch(BASE + path, {
    method: init?.method ?? "GET",
    headers: { ...headers(), ...(init ? { "Content-Type": "application/json" } : {}) },
    body: init ? JSON.stringify(init.body) : undefined,
    signal: AbortSignal.timeout(20_000),
  });
  if (!response.ok) throw new CelloError(`Cello ${path} answered HTTP ${response.status}`, response.status);
  const data = (await response.json()) as T;
  // Cello reports failures inside a 200: the real status is the body's.
  if (data.Status === 401) {
    throw new CelloError("Cello refused the saved sign-in (401) — sign in again: npm run cello:login", 401);
  }
  if (data.Status !== 200) {
    throw new CelloError(data.Message || `Cello ${path} answered status ${data.Status}`, data.Status);
  }
  return data;
}

// ── Cities and zones ──────────────────────────────────────────────────────

type LocationsResponse = BaseResponse & {
  Cities: { ID: number; Name: string; Code: string; Zones: { ID: number; Name: string; Code: string; TariffType: number | null }[] }[];
};

const CATALOGUE_TTL_MS = 6 * 60 * 60 * 1000;
let catalogue: { cities: CelloCity[]; at: number } | null = null;

/** Every city and zone Cello sells parking in. Cached for six hours — it changes when a city signs up. */
export async function cities(): Promise<CelloCity[]> {
  if (catalogue && Date.now() - catalogue.at < CATALOGUE_TTL_MS) return catalogue.cities;
  const data = await call<LocationsResponse>("Location/GetLocations");
  const result = data.Cities.map((c) => ({
    id: c.ID,
    name: c.Name.trim(),
    code: c.Code,
    zones: c.Zones.map((z) => ({ id: z.ID, code: z.Code, name: z.Name.trim(), tariffType: z.TariffType })),
  }));
  catalogue = { cities: result, at: Date.now() };
  return result;
}

// ── Car ───────────────────────────────────────────────────────────────────

type CarsResponse = BaseResponse & { Cars: { Number: string; Nickname: string | null }[] };

/**
 * The car to park. The account has exactly one today; with more than one this
 * refuses instead of picking, because parking the wrong plate is a fine.
 */
export async function theCar(): Promise<string> {
  const data = await call<CarsResponse>("Car");
  const cars = data.Cars ?? [];
  if (cars.length === 0) throw new CelloError("The Cello account has no car", 409);
  if (cars.length > 1) {
    throw new CelloError(`The Cello account has ${cars.length} cars (${cars.map((c) => c.Number).join(", ")}) — choosing one is not built yet`, 409);
  }
  return cars[0].Number;
}

// ── Parking ───────────────────────────────────────────────────────────────

type SessionDTO = {
  ID: number; CityID: number; ZoneID: number | null; ZoneName: string | null;
  CarNumber: string; DateIn: string | null; Amount: number | null;
};

function toSession(s: SessionDTO): ActiveSession {
  return {
    transactionId: s.ID,
    cityId: s.CityID,
    zoneId: s.ZoneID,
    zoneName: s.ZoneName,
    carNumber: s.CarNumber,
    startedAt: s.DateIn,
    amount: s.Amount,
  };
}

export async function activeSessions(): Promise<ActiveSession[]> {
  const data = await call<BaseResponse & { ActiveParks: SessionDTO[] | null }>("ParkingSession/GetActiveParkingSessions");
  return (data.ActiveParks ?? []).map(toSession);
}

export type StartResult =
  | { kind: "started"; message: string | null; session: ActiveSession | null }
  | { kind: "already-active"; message: string | null }
  | { kind: "needs-confirmation"; message: string; confirmationCode: string }
  | { kind: "free-now"; message: string }
  | { kind: "refused"; message: string };

/**
 * Start parking. ParkStatus is what the app switches on: 1 started, 10 already
 * running, 5 (and 11 with a code) Cello wants the driver to confirm a warning
 * first, 11 without a code means parking is free right now. A confirmation is
 * handed back to the caller — never auto-accepted — and passed in again as
 * `confirmationCode` once the person agreed to what it says.
 */
export async function startParking(args: {
  cityId: number;
  zoneId: number;
  lat: number;
  lon: number;
  accuracyM?: number;
  confirmationCode?: string;
}): Promise<StartResult> {
  const car = await theCar();
  const data = await call<BaseResponse & { ParkStatus: number; Session?: SessionDTO | null }>("ParkingSession/StartParking", {
    method: "POST",
    body: {
      CarNumber: car,
      CityID: args.cityId,
      ZoneID: args.zoneId,
      GeoLocation: args.accuracyM != null ? `${args.lat},${args.lon}:${args.accuracyM}` : `${args.lat},${args.lon}`,
      ConfirmationCode: args.confirmationCode ?? null,
      Comment: null,
      MaxDateTime: null,
      Source: SOURCE_APP,
    },
  });
  const message = data.Message ?? null;
  switch (data.ParkStatus) {
    case 1:
      return { kind: "started", message, session: data.Session ? toSession(data.Session) : null };
    case 10:
      return { kind: "already-active", message };
    case 5:
    case 11:
      if (data.ConfirmCode) return { kind: "needs-confirmation", message: message ?? "", confirmationCode: data.ConfirmCode };
      if (data.ParkStatus === 11) return { kind: "free-now", message: message ?? "החניה כרגע ללא תשלום" };
      throw new CelloError("Cello asked for a confirmation but sent no code", 502);
    default:
      return { kind: "refused", message: message ?? `Cello did not start parking (ParkStatus ${data.ParkStatus})` };
  }
}

export async function stopParking(args: { transactionId: number; lat?: number; lon?: number }): Promise<string | null> {
  const data = await call<BaseResponse>("ParkingSession/StopParking", {
    method: "POST",
    body: {
      TransactionID: args.transactionId,
      MethodClose: CLOSE_MANUAL,
      GeoLocation: args.lat != null && args.lon != null ? `${args.lat},${args.lon}` : null,
      Source: SOURCE_APP,
    },
  });
  return data.Message ?? null;
}
