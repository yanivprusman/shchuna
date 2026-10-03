# shchuna — שכונה

**A neighbourhood app first.** Which neighbourhood you are in (the headline), or —
type a street — which neighbourhoods that street runs through; plus street, city,
נפה/מחוז and the town's population-registry numbers. Parking is the second
feature: which Cello zone to pick, start/stop from the app or by voice via Ben.

The look follows that order on purpose: map teal/paper/amber everywhere, and the
blue-and-white kerb ONLY inside the parking card. An earlier build was blue-white
throughout and read as a parking app — the owner called it out (2026-10-03).

```
phone app (mobile/, Compose)  ──bearer──▶  Next.js API :3171 (desktop, 10.7.0.2, over WireGuard)
Ben (voice / WhatsApp)  ──▶  scripts/parking.mjs  ──▶  same API, location from the phone over adb
```

## Facts that are not obvious from the code

- **Cello has no zone geometry.** `Location/GetLocations` gives ~100 cities / ~360
  zones as *names*; `GeoLocation` is null on all of them and the app's own
  `GetLocationsByGeoLocation` answers 404 everywhere. The zone is read off the
  name in `lib/zones.ts` (listed streets, neighbourhood, municipal area number,
  industrial area, else "רחבי העיר"). When two zones fit equally it returns both —
  never guess: the wrong zone is a ticket.
- **There is no national map of parking zones.** Zones are set per city by bylaw.
  Where a city publishes GIS we use it: Tel Aviv layer 544 "אזורי חניה"
  (`lib/spot.ts`). Add other cities the same way.
- **Government town data** = population registry by locality on data.gov.il
  (`lib/gov.ts`). Cello's city `Code` is the same CBS locality code.
- **Cello API** was read out of the Android app (v12.31); `lib/cello.ts` documents
  each call. Login is phone + SMS code; `npm run cello:login` redoes it and reads
  the code off the phone over adb.
- **Secrets**: `/etc/automatelinux/shchuna.env` (600) — Cello token + App-Key and
  `SHCHUNA_API_TOKEN`. Read per request, not from `process.env` (Next inlines it).
  The phone app bakes the token from gitignored `mobile/.env`; the build fails
  without it. Starting parking spends money — the API is never open.
- Overpass (OSM "is it industrial land") sheds load; an outage is reported as
  "unknown" and turns a one-zone answer into a choice, never into "outside".

## Run / test

```bash
d startApp --app shchuna               # dev on 3171
npm test                                # zone-choice cases from real Cello names
node scripts/parking.mjs where          # nothing paid
cd mobile && ./gradlew assembleDevDebug # desktop only, never the NUC
/opt/automateLinux/utilities/chunked-adb-install.sh mobile/app/build/outputs/apk/dev/debug/app-dev-debug.apk 10.7.0.3:5555 com.automatelinux.shchuna.dev
```

Ben's instructions for parking live in `/root/.openclaw/workspace/TOOLS.md`.
