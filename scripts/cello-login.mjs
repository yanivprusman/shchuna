#!/usr/bin/env node
// Sign the backend in to Cello again (npm run cello:login) — when the API starts
// answering "Cello refused the saved sign-in (401)".
//
// The same three steps the Cello app takes: App/RegisterApp (an App-Key),
// Account/VerifyPhone (Cello texts a code), Account/Login with that code as
// SecretCode. The code is read off the main phone's SMS inbox over adb, so the
// owner does nothing. The new token and App-Key replace the old ones in
// /etc/automatelinux/shchuna.env; nothing else in the file changes.

import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

const ENV = "/etc/automatelinux/shchuna.env";
const BASE = "https://application.cellopark.co.il/smartphone-api/";
const PHONE_ADB = "10.7.0.3:5555";
// Hard-coded in the Cello app (AppConfigurationHelper.secretKey, v12.31).
const APP_SECRET = "o4/N0/nH2tL1Wak/D6/Pv4vcUW7KKt0oRy1f/o2RSAZtseOJTp0Ibd5m6fEjt86F-Ver1.0";

const env = readFileSync(ENV, "utf8");
const phone = env.match(/^CELLO_PHONE=(.+)$/m)?.[1].trim();
if (!phone) throw new Error(`${ENV} has no CELLO_PHONE`);

const base = { "Device-OS": "android", "Device-Name": "shchuna", "Accept-Language": "he-IL", "OS-Version": "35", "App-Version": "12.31", SessionID: "shchuna" };

async function cello(path, { method = "GET", body, appKey } = {}) {
  const r = await fetch(BASE + path, {
    method,
    headers: { ...base, ...(appKey ? { "App-Key": appKey } : {}), ...(body !== undefined ? { "Content-Type": "application/json" } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(20_000),
  });
  const data = await r.json();
  if (data.Status !== 200) throw new Error(`${path}: ${data.Message ?? `status ${data.Status}`}`);
  return data;
}

const adb = (cmd) => execFileSync("adb", ["-s", PHONE_ADB, "shell", cmd], { encoding: "utf8", timeout: 20_000 });

const { Key: appKey } = await cello("App/RegisterApp", { method: "POST", body: APP_SECRET });
const sentAfter = Date.now() - 5_000;
await cello(`Account/VerifyPhone?userName=${phone}`, { appKey });
console.log(`Cello is texting a code to ${phone} — reading it from the phone…`);

let code = null;
for (let i = 0; i < 40 && !code; i++) {
  await new Promise((r) => setTimeout(r, 3000));
  const rows = adb(`content query --uri content://sms/inbox --projection address:date:body --where "date>${sentAfter}" --sort "date DESC"`);
  code = rows.split("\n").filter((l) => /address=Cello/i.test(l)).map((l) => l.match(/body=\D*(\d{6})/)?.[1]).find(Boolean) ?? null;
}
if (!code) throw new Error("no Cello code arrived on the phone within two minutes");

const login = await cello("Account/Login", { method: "POST", body: { UserName: phone, Password: "", SecretCode: code } });
const token = login.UserTokenList?.[0]?.AuthorizationToken;
if (!token) throw new Error("Cello signed in but returned no token");

const updated = env
  .replace(/^CELLO_TOKEN=.*$/m, `CELLO_TOKEN=${token}`)
  .replace(/^CELLO_APP_KEY=.*$/m, `CELLO_APP_KEY=${appKey}`);
writeFileSync(ENV, updated, { mode: 0o600 });
console.log(`Signed in. New token saved to ${ENV}.`);
