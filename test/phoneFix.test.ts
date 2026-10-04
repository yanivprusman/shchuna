// The phone's location dump, as it actually read at 03:08 on 2026-10-04 —
// the night a live one-metre fix was reported to the driver as three days stale.
import { test } from "node:test";
import assert from "node:assert/strict";
import { elapsed, parseFixes, bestFix } from "../scripts/phoneFix.mjs";

const DUMP = `
    gps provider:
      last location=Location[gps 32.001302,34.820400 hAcc=1.0 et=+3d12h11m6s799ms alt=50.0 vAcc=1.0 mslAlt=30.72010495572748 mslAltAcc=1.0358089 vel=0.0 sAcc=0.01 {Bundle[{satellites=28, maxCn0=46, meanCn0=36}]}]
    network provider:
      last location=Location[network 32.004362,34.818777 hAcc=300.0 et=+3d12h10m56s266ms]
    fused provider:
      last location=Location[fused 32.001302,34.820400 hAcc=1.0 et=+3d12h11m6s799ms alt=50.0 vAcc=1.0]
`;
// The phone had been up 3d 12h 11m 08s when that dump was taken.
const UPTIME_S = 3 * 86400 + 12 * 3600 + 11 * 60 + 8;

test("a fix whose time is the last thing on its line is still read", () => {
  // The network line ends "…266ms]" — the bracket used to be taken as part of the time.
  const network = parseFixes(DUMP, UPTIME_S).find((f) => f.provider === "network");
  assert.deepEqual(network, { provider: "network", lat: 32.004362, lon: 34.818777, accuracyM: 300, ageS: 12 });
});

test("age is uptime minus et, not et itself", () => {
  const gps = parseFixes(DUMP, UPTIME_S).find((f) => f.provider === "gps");
  assert.equal(gps?.ageS, 1); // not 3.5 days
});

test("the freshest fix wins, and accuracy breaks a tie", () => {
  const best = bestFix(parseFixes(DUMP, UPTIME_S));
  assert.equal(best.ageS, 1);
  assert.equal(best.accuracyM, 1);
});

test("no location lines means no fixes, not a crash", () => {
  assert.deepEqual(parseFixes("gps provider:\n  no fix yet\n", UPTIME_S), []);
});

test("elapsed reads every shape Android prints", () => {
  assert.equal(elapsed("+3d5h8m5s607ms"), 3 * 86400 + 5 * 3600 + 8 * 60 + 5.607);
  assert.equal(elapsed("+41s12ms"), 41.012);
  assert.equal(elapsed("+7m0s3ms"), 420.003);
  assert.throws(() => elapsed("+3d12h11m31s574ms]"), /cannot read fix time/);
});
