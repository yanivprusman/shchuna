import fs from "node:fs";

// Secrets live in /etc/automatelinux/shchuna.env (mode 600), read at request
// time. Not process.env: Next inlines process.env.* at build time, so a value set
// in a unit or .env.local can silently never reach a route (veggieBox hit this).
const CONFIG_FILE = "/etc/automatelinux/shchuna.env";

export type Config = {
  celloToken: string;
  celloAppKey: string;
  celloPhone: string;
  apiToken: string;
};

let cached: { config: Config; mtimeMs: number } | null = null;

export function config(): Config {
  let stat: fs.Stats;
  try {
    stat = fs.statSync(CONFIG_FILE);
  } catch {
    throw new Error(`${CONFIG_FILE} is missing — see README.md "Configuration"`);
  }
  if (cached && cached.mtimeMs === stat.mtimeMs) return cached.config;

  const values: Record<string, string> = {};
  for (const line of fs.readFileSync(CONFIG_FILE, "utf8").split("\n")) {
    const m = line.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);
    if (m) values[m[1]] = m[2];
  }
  const need = (key: string) => {
    const v = values[key];
    if (!v) throw new Error(`${CONFIG_FILE} has no ${key}`);
    return v;
  };
  const apiToken = need("SHCHUNA_API_TOKEN");
  if (apiToken.length < 32) throw new Error(`SHCHUNA_API_TOKEN in ${CONFIG_FILE} is shorter than 32 characters`);

  const result: Config = {
    celloToken: need("CELLO_TOKEN"),
    celloAppKey: need("CELLO_APP_KEY"),
    celloPhone: need("CELLO_PHONE"),
    apiToken,
  };
  cached = { config: result, mtimeMs: stat.mtimeMs };
  return result;
}
