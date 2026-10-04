#!/usr/bin/env node
// Ben's handle on parking — what "hey computer, start parking" ends up running.
//
//   node scripts/parking.mjs location         where the phone is — for anything, not just parking
//   node scripts/parking.mjs where            where the phone is + which Cello zone
//   node scripts/parking.mjs start [--zone <id>] [--confirm <code>]
//   node scripts/parking.mjs stop
//   node scripts/parking.mjs status
//
// The location is the main phone's own last fix, read over adb (the phone runs
// no app for this; Waze or Maps keep the fix fresh while driving, so it is
// seconds old when you park). A fix older than MAX_FIX_AGE_S is refused —
// parking in the zone you were in ten minutes ago is how you get a ticket.
// `location` is the exception: "find me somewhere open nearby" is still
// answerable from a twenty-minute-old fix, so it reports the age instead of
// refusing and leaves the judgement to whoever asked.
//
// Output is one JSON object on stdout: { ok, say, ...details }. `say` is a
// sentence for the driver; the rest is for whoever needs to act on it.
// Exit 0 = done, 2 = needs a decision from the driver (zone or a Cello warning),
// 1 = failed.

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { parseFixes, bestFix } from "./phoneFix.mjs";

const PHONE = "10.7.0.3:5555";
const API = "http://127.0.0.1:3171";
const MAX_FIX_AGE_S = 300;

function token() {
  const line = readFileSync("/etc/automatelinux/shchuna.env", "utf8").split("\n").find((l) => l.startsWith("SHCHUNA_API_TOKEN="));
  if (!line) throw new Error("SHCHUNA_API_TOKEN missing from /etc/automatelinux/shchuna.env");
  return line.slice("SHCHUNA_API_TOKEN=".length).trim();
}

function phoneFix() {
  const adb = (cmd) => execFileSync("adb", ["-s", PHONE, "shell", cmd], { encoding: "utf8", timeout: 20_000 });
  let dump;
  try {
    dump = adb("dumpsys location");
  } catch (e) {
    throw new Error(`cannot reach the phone over adb (${PHONE}): ${String(e.message).split("\n")[0]}`);
  }
  const uptime = Number(adb("cat /proc/uptime").split(" ")[0]);
  const fixes = parseFixes(dump, uptime);
  if (fixes.length === 0) throw new Error("the phone reports no location fix at all");
  return bestFix(fixes);
}

async function api(path, init) {
  const response = await fetch(API + path, {
    ...init,
    headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json" },
    signal: AbortSignal.timeout(90_000),
  });
  const body = await response.json();
  return { status: response.status, body };
}

function out(code, obj) {
  process.stdout.write(JSON.stringify(obj, null, 1) + "\n");
  process.exit(code);
}

/** How old a fix is, the way you would say it. */
function ageText(seconds) {
  if (seconds < 90) return `${Math.max(seconds, 0)} seconds`;
  if (seconds < 2 * 3600) return `${Math.round(seconds / 60)} minutes`;
  return `${Math.round(seconds / 3600)} hours`;
}

function zoneList(zones) {
  return zones.map((z) => `${z.name} (zone ${z.id})`).join("; ");
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const flag = (name) => {
    const i = rest.indexOf(`--${name}`);
    return i >= 0 ? rest[i + 1] : undefined;
  };

  if (command === "status") {
    const { status, body } = await api("/api/parking/status");
    if (status !== 200) out(1, { ok: false, say: `Could not read parking status: ${body.error}` });
    if (body.sessions.length === 0) out(0, { ok: true, say: "No parking is running.", sessions: [] });
    const s = body.sessions[0];
    out(0, { ok: true, say: `Parking is running in ${s.zoneName ?? "an unnamed zone"} since ${s.startedAt}.`, sessions: body.sessions });
  }

  if (command === "stop") {
    const { status, body } = await api("/api/parking/stop", { method: "POST", body: "{}" });
    if (body.outcome === "nothing-active") out(0, { ok: true, say: "There was no parking running." });
    if (status !== 200) out(body.outcome ? 2 : 1, { ok: false, say: body.message ?? body.error, ...body });
    out(0, { ok: true, say: `Parking stopped${body.session?.zoneName ? ` in ${body.session.zoneName}` : ""}.`, ...body });
  }

  if (command !== "location" && command !== "where" && command !== "start") {
    out(1, { ok: false, say: "usage: parking.mjs location | where | start [--zone <id>] [--confirm <code>] | stop | status" });
  }

  const fix = phoneFix();

  if (command === "location") {
    const { status, body } = await api(`/api/where?lat=${fix.lat}&lon=${fix.lon}`);
    if (status !== 200) out(1, { ok: false, say: body.error, fix });
    const a = body.address;
    const placeLine = [[a.street, a.houseNumber].filter(Boolean).join(" "), a.neighborhood, a.city].filter(Boolean).join(", ");
    out(0, {
      ok: true,
      say: `You are at ${placeLine}. That fix is ${ageText(fix.ageS)} old, good to ${fix.accuracyM} metres.`,
      fix,
      address: a,
    });
  }
  if (fix.ageS > MAX_FIX_AGE_S) {
    out(1, {
      ok: false,
      say: `The phone's last location is ${Math.round(fix.ageS / 60)} minutes old, too old to pick a parking zone. Open a map app for a few seconds and try again.`,
      fix,
    });
  }

  if (command === "where") {
    const { status, body } = await api(`/api/where?lat=${fix.lat}&lon=${fix.lon}`);
    if (status !== 200) out(1, { ok: false, say: body.error, fix });
    const a = body.address;
    const placeLine = [a.street, a.neighborhood, a.city].filter(Boolean).join(", ");
    const c = body.cello;
    const zoneLine =
      c.kind === "one" ? `Cello: ${c.city}, ${c.zone.name}.` :
      c.kind === "several" ? `Cello: ${c.city}, one of: ${zoneList(c.candidates)}. ${c.message}` :
      c.message;
    out(0, { ok: true, say: `You are at ${placeLine}. ${zoneLine}`, fix, ...body });
  }

  // start
  const zone = flag("zone");
  const confirm = flag("confirm");
  const { status, body } = await api("/api/parking/start", {
    method: "POST",
    body: JSON.stringify({ lat: fix.lat, lon: fix.lon, accuracyM: fix.accuracyM, zoneId: zone ? Number(zone) : undefined, confirmationCode: confirm }),
  });
  if (body.outcome === "choose-zone") {
    out(2, { ok: false, say: `In ${body.city} I can't tell the zone for sure. ${body.message}. Options: ${zoneList(body.candidates)}.`, fix, ...body });
  }
  if (body.outcome === "no-city") out(1, { ok: false, say: body.message, fix });
  if (status !== 200) out(1, { ok: false, say: `Cello did not start parking: ${body.error}`, fix });

  const r = body.result;
  const where = `${body.city}, ${body.zone.name}`;
  switch (r.kind) {
    case "started":
      out(0, { ok: true, say: `Parking started in ${where}.`, fix, ...body });
    case "already-active":
      out(0, { ok: true, say: `Parking was already running (${where}).`, fix, ...body });
    case "free-now":
      out(0, { ok: true, say: `Parking is free here right now, so nothing was started. Cello says: ${r.message}`, fix, ...body });
    case "needs-confirmation":
      out(2, {
        ok: false,
        say: `Cello wants a confirmation before parking in ${where}: ${r.message}. To accept, run start again with --zone ${body.zone.id} --confirm ${r.confirmationCode}.`,
        fix,
        ...body,
      });
    default:
      out(1, { ok: false, say: `Cello refused: ${r.message}`, fix, ...body });
  }
}

main().catch((e) => out(1, { ok: false, say: e.message }));
