import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const cache = path.join(process.env.XDG_CONFIG_HOME || path.join(os.homedir(), ".config"), "ludopaws", "android-testing.json");
const keys = [
  "LUDOPROOF_API_BASE_URL", "GOOGLE_SIGN_IN_WEB_CLIENT_ID", "PLAY_GAMES_PROJECT_ID",
  "ADMOB_APP_ID", "ADMOB_REWARDED_AD_UNIT_ID", "ADMOB_INTERSTITIAL_AD_UNIT_ID",
  "LUDOPAWS_AD_SAFETY_REVIEW_APPROVED", "GOOGLE_PLAY_BILLING_ENABLED",
];

function run(program, args, options = {}) {
  const result = spawnSync(program, args, { cwd: root, encoding: "utf8", ...options });
  if (result.error || result.status !== 0) {
    throw new Error(result.error?.message || result.stderr?.trim() || `${program} failed (${result.status})`);
  }
  return result.stdout;
}

function configure() {
  const values = {};
  // These are public Actions variables, never Actions secrets or release signing keys.
  for (const scope of ["actions", "environments/production"]) {
    const pages = JSON.parse(run("gh", ["api", "--paginate", "--slurp",
      `repos/rekixo-lab/ludopaws/${scope}/variables?per_page=100`]));
    for (const page of pages) {
      for (const { name, value } of page.variables || []) {
        if (keys.includes(name)) values[name] = value;
      }
    }
  }
  fs.mkdirSync(path.dirname(cache), { recursive: true, mode: 0o700 });
  const temporary = `${cache}.${process.pid}.tmp`;
  try {
    fs.writeFileSync(temporary, `${JSON.stringify(values, null, 2)}\n`, { mode: 0o600, flag: "wx" });
    fs.renameSync(temporary, cache);
  } finally {
    fs.rmSync(temporary, { force: true });
  }
  console.log(`Public build configuration saved outside the repository: ${cache}`);
  console.log(`Google linking: ${values.GOOGLE_SIGN_IN_WEB_CLIENT_ID ? "configured (debug OAuth credential still required)" : "missing"}`);
  console.log(`Play Games: ${values.PLAY_GAMES_PROJECT_ID && values.PLAY_GAMES_PROJECT_ID !== "0" ? "configured" : "missing project ID; feature remains unavailable"}`);
  return values;
}

function main() {
  const [mode, ...options] = process.argv.slice(2);
  if (!mode || mode === "--help") {
    console.log(`Usage:
  node scripts/android-local-testing.mjs configure
  node scripts/android-local-testing.mjs dev [--assemble]
  node scripts/android-local-testing.mjs integration [--assemble]
  node scripts/android-local-testing.mjs preview [--assemble]

dev / integration: LudoPaws Test, original package, Billing and Google configured,
  rewarded/interstitial Google test ads ON. Opens the installed app automatically.
preview: separate .dev package, visual previews only; Billing/Google/Store Ads OFF.
--assemble builds without installing. Otherwise installs on ANDROID_SERIAL/the connected device.
Configuration stays in ${cache}. configure refreshes public GitHub variables.
Integration does not uninstall a Play-signed app or clear its data. A different signing
certificate or a higher installed versionCode must be resolved explicitly.
Test ads exercise the UI but do not grant real gems or verified character unlocks.`);
    return;
  }
  if (!["configure", "dev", "integration", "preview"].includes(mode) || options.some(value => value !== "--assemble") ||
      (mode === "configure" && options.length)) throw new Error("Invalid arguments; use --help.");
  if (mode === "configure") { configure(); return; }
  const saved = fs.existsSync(cache) ? JSON.parse(fs.readFileSync(cache, "utf8")) : configure();
  const properties = Object.fromEntries(keys.filter(key => key in saved).map(key => [key, saved[key]]));
  for (const key of keys) {
    const override = process.env[`ORG_GRADLE_PROJECT_${key}`];
    if (override !== undefined) properties[key] = override;
  }
  properties.LUDOPROOF_API_BASE_URL ||= "https://api.ludopaws.rekixo.com";
  properties.PLAY_GAMES_PROJECT_ID ||= "0";
  properties.LUDOPAWS_QA_BUILD = "false";
  const fullTesting = mode !== "preview";
  properties.LUDOPAWS_DEV_BUILD = String(!fullTesting);
  properties.LUDOPAWS_TEST_ADS = String(fullTesting);
  properties.LUDOPROOF_BILLING_ENABLED = String(fullTesting);
  properties.LUDOPROOF_ADS_ENABLED = String(fullTesting);
  properties.LUDOPROOF_INTERSTITIAL_ADS_ENABLED = String(fullTesting);
  delete properties.GOOGLE_PLAY_BILLING_ENABLED;
  if (!fullTesting) {
    properties.GOOGLE_SIGN_IN_WEB_CLIENT_ID = "";
    properties.PLAY_GAMES_PROJECT_ID = "0";
  } else {
    if (!properties.GOOGLE_SIGN_IN_WEB_CLIENT_ID?.endsWith(".apps.googleusercontent.com") ||
        !/^[1-9][0-9]*$/.test(properties.PLAY_GAMES_PROJECT_ID)) {
      throw new Error("Google configuration is missing. Run configure, or provide the correct Google Web Client ID and Play Games project ID. Refusing to install another partly disabled build.");
    }
    properties.ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713";
    properties.ADMOB_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    properties.ADMOB_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
  }
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), "Android", "Sdk");
  const env = { ...process.env, ANDROID_HOME: sdk };
  console.log(`${mode}: ${options.includes("--assemble") ? "building" : "installing"} debug APK; no Play upload, no release tasks.`);
  console.log(fullTesting ?
    "FULL TESTING: LudoPaws Test — Billing ON, Google configured, rewarded/interstitial TEST ADS ON. Use a Play license tester account; debug OAuth certificate must match." :
    "PREVIEW ONLY: LudoPaws Preview — Billing and Google OFF. Use dev for full testing.");
  // Command-line properties take precedence over user/project gradle.properties.
  run("bash", ["android/gradlew", "-p", "android", options.includes("--assemble") ? ":app:assembleDebug" : ":app:installDebug",
    ...Object.entries(properties).map(([key, value]) => `-P${key}=${value}`), "--console=plain"], { env, stdio: "inherit" });
  if (!options.includes("--assemble")) {
    const adb = path.join(sdk, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
    const packageName = fullTesting ? "com.rekixo.ludopaws" : "com.rekixo.ludopaws.dev";
    run(adb, ["shell", "am", "start", "-n", `${packageName}/com.rekixo.ludopaws.HomeActivity`], { env, stdio: "inherit" });
  }
}

try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
