import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
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
    throw new Error(result.error?.message || result.stderr?.trim() || `${program} failed (${result.status ?? result.signal})`);
  }
  return result.stdout || "";
}

function configure(execute, configPath) {
  const values = {};
  // Public Actions variables only; no release signing keys or Actions secrets.
  for (const scope of ["actions", "environments/production"]) {
    const pages = JSON.parse(execute("gh", ["api", "--paginate", "--slurp",
      `repos/rekixo-lab/ludopaws/${scope}/variables?per_page=100`]));
    for (const page of pages) for (const { name, value } of page.variables || []) {
      if (keys.includes(name) && typeof value === "string") values[name] = value;
    }
  }
  fs.mkdirSync(path.dirname(configPath), { recursive: true, mode: 0o700 });
  const temporary = `${configPath}.${process.pid}.tmp`;
  try {
    fs.writeFileSync(temporary, `${JSON.stringify(values, null, 2)}\n`, { mode: 0o600, flag: "wx" });
    fs.renameSync(temporary, configPath);
  } finally {
    fs.rmSync(temporary, { force: true });
  }
  console.log(`Public build configuration saved outside the repository: ${configPath}`);
  return values;
}

function localSdk(projectRoot) {
  const file = path.join(projectRoot, "android", "local.properties");
  if (!fs.existsSync(file)) return null;
  const value = fs.readFileSync(file, "utf8").match(/^sdk\.dir\s*[=:]\s*(.+)$/m)?.[1]?.trim();
  return value?.replace(/\\u([a-f\d]{4})|\\(.)/gi, (_, hex, escaped) => hex ? String.fromCharCode(parseInt(hex, 16)) : escaped);
}

function resolveSdk(environment, platform, projectRoot) {
  const explicit = environment.LUDOPROOF_ANDROID_SDK || environment.ANDROID_HOME || environment.ANDROID_SDK_ROOT;
  const candidates = explicit ? [explicit] : [
    localSdk(projectRoot),
    platform === "win32" && environment.LOCALAPPDATA && path.join(environment.LOCALAPPDATA, "Android", "Sdk"),
    platform === "win32" && "C:\\Android\\Sdk",
    platform === "darwin" && path.join(os.homedir(), "Library", "Android", "sdk"),
    path.join(os.homedir(), "Android", "Sdk"),
  ];
  const sdk = candidates.find(candidate => candidate && fs.existsSync(path.join(candidate, "platform-tools", platform === "win32" ? "adb.exe" : "adb")));
  if (!sdk) throw new Error("Android SDK/platform-tools not found. Set ANDROID_HOME to your SDK directory. This helper does not download an SDK.");
  return path.resolve(sdk);
}

function syncSdkProperty(projectRoot, sdk) {
  const file = path.join(projectRoot, "android", "local.properties");
  const previous = fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
  const portable = sdk.replaceAll("\\", "/").replace(/[^\x20-\x7e]|[ :=#!]/g,
    character => character.charCodeAt(0) > 126 ? `\\u${character.charCodeAt(0).toString(16).padStart(4, "0")}` : `\\${character}`);
  const line = `sdk.dir=${portable}`;
  const newline = previous.includes("\r\n") ? "\r\n" : "\n";
  const next = /^sdk\.dir\s*[=:]/m.test(previous)
    ? previous.replace(/^sdk\.dir\s*[=:][^\r\n]*/m, () => line)
    : previous + (previous && !previous.endsWith("\n") ? newline : "") + line + newline;
  if (next !== previous) fs.writeFileSync(file, next, "utf8");
}

export function selectDevice(output, requested) {
  const devices = output.split(/\r?\n/).map(line => line.trim().split(/\s+/))
    .filter(([, state]) => ["device", "offline", "unauthorized"].includes(state));
  if (requested) {
    const selected = devices.find(([serial]) => serial === requested);
    if (!selected || selected[1] !== "device") throw new Error(`ANDROID_SERIAL device ${requested} is not connected and authorized. Unlock it and approve USB debugging.`);
    return requested;
  }
  if (devices.length > 1) throw new Error("Multiple Android devices found. Set ANDROID_SERIAL to the intended phone before installing.");
  if (devices.length !== 1 || devices[0][1] !== "device") throw new Error("No authorized Android device. Connect/unlock the phone and approve USB debugging; offline/unauthorized devices cannot be installed.");
  return devices[0][0];
}

export function main(argv = process.argv.slice(2), {
  environment = process.env, platform = process.platform, projectRoot = root,
  configPath = cache, execute = run,
} = {}) {
  const [mode, ...options] = argv;
  if (!mode || mode === "--help") {
    console.log(`One build/install runner for VS Code and the terminal:
  node scripts/android-local-testing.mjs configure
  node scripts/android-local-testing.mjs dev [--assemble] [--verify]
  node scripts/android-local-testing.mjs preview [--assemble] [--verify]

Ctrl+Shift+B runs the exact same dev command.
dev: LudoPaws Test, original package, Billing/Google configured, Google TEST ADS ON.
preview: separate .dev package; Billing/Google/Store Ads OFF.
--assemble: build only, without contacting a device.
--verify: unit tests and debug lint before building; no release tasks or Play uploads.
Set ANDROID_SERIAL when multiple phones/emulators are connected.
Requires Node.js, a Gradle-compatible JDK and Android SDK; gh login for configure.
Configuration: ${configPath}. configure refreshes public GitHub variables.
Uses the checked-in Gradle wrapper on Windows, Linux and macOS.
No uninstall, data clear or downgrade. Play/debug signing conflicts stop installation.
Test ads do not grant real gems/unlocks; debug OAuth and license tester setup still apply.`);
    return;
  }
  if (!["configure", "dev", "preview"].includes(mode) || options.some(value => !["--assemble", "--verify"].includes(value)) ||
      (mode === "configure" && options.length)) throw new Error("Invalid arguments; use --help. Use dev for full local testing.");
  if (mode === "configure") return configure(execute, configPath);
  const assembleOnly = options.includes("--assemble");
  const sdk = resolveSdk(environment, platform, projectRoot);
  const env = { ...environment, ANDROID_HOME: sdk, ANDROID_SDK_ROOT: sdk };
  const adb = path.join(sdk, "platform-tools", platform === "win32" ? "adb.exe" : "adb");
  const serial = assembleOnly ? null : selectDevice(execute(adb, ["devices"], { env }), environment.ANDROID_SERIAL);
  const fullTesting = mode === "dev";
  const saved = fs.existsSync(configPath) ? JSON.parse(fs.readFileSync(configPath, "utf8"))
    : fullTesting ? configure(execute, configPath) : {};
  const properties = Object.fromEntries(keys.filter(key => typeof saved[key] === "string").map(key => [key, saved[key]]));
  for (const key of keys) {
    const override = environment[`ORG_GRADLE_PROJECT_${key}`];
    if (override !== undefined) properties[key] = override;
  }
  properties.LUDOPROOF_API_BASE_URL ||= "https://api.ludopaws.rekixo.com";
  properties.PLAY_GAMES_PROJECT_ID ||= "0";
  properties.LUDOPAWS_QA_BUILD = "false";
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
    if (!properties.GOOGLE_SIGN_IN_WEB_CLIENT_ID?.endsWith(".apps.googleusercontent.com") || !/^[1-9][0-9]*$/.test(properties.PLAY_GAMES_PROJECT_ID)) {
      throw new Error("Google configuration is missing. Run configure, or provide the correct Google Web Client ID and Play Games project ID. Refusing to install a partly disabled build.");
    }
    properties.ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713";
    properties.ADMOB_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    properties.ADMOB_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
  }
  syncSdkProperty(projectRoot, sdk);
  const androidDir = path.join(projectRoot, "android");
  const gradleArgs = [
    ...(options.includes("--verify") ? [":app:testDebugUnitTest", ":app:lintDebug"] : []),
    ":app:assembleDebug", ...Object.entries(properties).map(([key, value]) => `-P${key}=${value}`),
    "--console=plain", "--build-cache",
  ];
  console.log(`${mode}: ${assembleOnly ? "building only" : `building for device ${serial}`} debug APK; no release tasks or Play upload.`);
  console.log(fullTesting ? "FULL TESTING: Billing/Google ON, Google TEST ADS ON; debug OAuth and Play license tester account required."
    : "PREVIEW ONLY: separate package; Billing, Google and Store Ads OFF.");
  if (platform === "win32") {
    // No path/property interpolation into shell code; reuse the checked-in wrapper.
    execute("powershell.exe", ["-NoProfile", "-NonInteractive", "-Command",
      "$gradleArguments = ConvertFrom-Json $env:LUDOPAWS_LOCAL_GRADLE_ARGS; & $env:LUDOPAWS_LOCAL_GRADLE_WRAPPER @gradleArguments; exit $LASTEXITCODE"], {
      cwd: androidDir, stdio: "inherit", env: { ...env,
        LUDOPAWS_LOCAL_GRADLE_WRAPPER: path.join(androidDir, "gradlew.bat"),
        LUDOPAWS_LOCAL_GRADLE_ARGS: JSON.stringify(gradleArgs),
      },
    });
  } else {
    execute("bash", [path.join(androidDir, "gradlew"), ...gradleArgs], { cwd: androidDir, env, stdio: "inherit" });
  }
  if (assembleOnly) return;
  const selectedEnv = { ...env, ANDROID_SERIAL: serial };
  const apk = path.join(androidDir, "app", "build", "outputs", "apk", "debug", "app-debug.apk");
  if (!fs.existsSync(apk)) throw new Error("Build completed without the expected debug APK; refusing to install another artifact.");
  try {
    execute(adb, ["-s", serial, "install", "-r", apk], { env: selectedEnv, stdio: "inherit" });
  } catch (error) {
    throw new Error(`${error.message}\nInstallation stopped. No app was uninstalled or downgraded. If Play/debug signatures or versionCodes conflict, use a compatible test device/build; preview uses a separate package.`);
  }
  const packageName = fullTesting ? "com.rekixo.ludopaws" : "com.rekixo.ludopaws.dev";
  const launched = execute(adb, ["-s", serial, "shell", "am", "start", "-W", "-n", `${packageName}/com.rekixo.ludopaws.HomeActivity`], { env: selectedEnv });
  console.log(launched);
  if (/Error:|Error type \d|Status:\s*(?!ok\b)\S+/i.test(launched)) throw new Error("APK installed, but Android could not open HomeActivity. Check the launch output above.");
  console.log(`Installed and opened ${fullTesting ? "LudoPaws Test" : "LudoPaws Preview"} on ${serial}.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
