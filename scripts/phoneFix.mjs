// Reading the phone's last location fixes out of `dumpsys location`.
//
// Kept apart from parking.mjs so it can be tested without a phone: the parsing
// is the part that broke (2026-10-04), and it broke on text, not on adb.

/** "+3d5h8m5s607ms" → seconds. */
export function elapsed(et) {
  const m = et.match(/^\+?(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m(?!s))?(?:(\d+)s)?(?:(\d+)ms)?$/);
  if (!m) throw new Error(`cannot read fix time ${et}`);
  const [, d = 0, h = 0, min = 0, s = 0, ms = 0] = m.map((x) => (x === undefined ? 0 : Number(x)));
  return d * 86400 + h * 3600 + min * 60 + s + ms / 1000;
}

/**
 * Every `last location=` line in the dump, with its age worked out.
 *
 * `et` is the phone's uptime when the fix was taken — NOT how old the fix is.
 * "+3d12h" on a phone that has been up three and a half days is a fix from a
 * moment ago; the age is uptime minus `et`.
 *
 * The time is matched by its own alphabet rather than "up to the next space":
 * a `network` fix carries nothing after `et`, so its line ends `…et=+3d12h10m56s266ms]`
 * and a greedy match swallowed the bracket — which made a 22-second-old fix
 * unreadable and was reported to the driver as three days stale.
 */
export function parseFixes(dump, uptimeS) {
  return [...dump.matchAll(/last location=Location\[(\w+) (-?[\d.]+),(-?[\d.]+) hAcc=([\d.]+) et=(\+?[\ddhms]+)/g)].map((m) => ({
    provider: m[1],
    lat: Number(m[2]),
    lon: Number(m[3]),
    accuracyM: Math.round(Number(m[4])),
    ageS: Math.round(uptimeS - elapsed(m[5])),
  }));
}

/** Newest first; of two equally fresh, the more accurate. */
export function bestFix(fixes) {
  return [...fixes].sort((a, b) => a.ageS - b.ageS || a.accuracyM - b.accuracyM)[0];
}
