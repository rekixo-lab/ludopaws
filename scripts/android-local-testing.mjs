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
  "LUDOPAWS_VERSION_CODE", "LUDOPAWS_VERSION_NAME",
  "LUDOPROOF_BILLING_ENABLED", "LUDOPROOF_ADS_ENABLED", "LUDOPROOF_INTERSTITIAL_ADS_ENABLED",
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
  node scripts/android-local-testing.mjs run [--assemble] [--verify]

Ctrl+Shift+B runs the exact same main-app command.
One original package, using the published production feature flags and ad IDs.
No developer UI, alternate app, or demo-ad mode.
--assemble: build only, without contacting a device.
--verify: unit tests and debug lint before building; no release tasks or Play uploads.
Set ANDROID_SERIAL when multiple phones/emulators are connected.
Local versionCode automatically matches or exceeds the installed app; release versions are unchanged.
Requires Node.js, a Gradle-compatible JDK and Android SDK; gh login for configure.
Configuration: ${configPath}. configure refreshes public GitHub variables.
Uses the checked-in Gradle wrapper on Windows, Linux and macOS.
No uninstall, data clear or downgrade. Play/debug signing conflicts stop installation.
Debug OAuth/signing and Google Play license tester setup still apply; no payment or ad is submitted by this runner.`);
    return;
  }
  if (!["configure", "run"].includes(mode) || options.some(value => !["--assemble", "--verify"].includes(value)) ||
      (mode === "configure" && options.length)) throw new Error("Invalid arguments; use --help. Use run for the main app.");
  if (mode === "configure") return configure(execute, configPath);
  const assembleOnly = options.includes("--assemble");
  const sdk = resolveSdk(environment, platform, projectRoot);
  const env = { ...environment, ANDROID_HOME: sdk, ANDROID_SDK_ROOT: sdk };
  const adb = path.join(sdk, "platform-tools", platform === "win32" ? "adb.exe" : "adb");
  const serial = assembleOnly ? null : selectDevice(execute(adb, ["devices"], { env }), environment.ANDROID_SERIAL);
  const packageName = "com.rekixo.ludopaws";
  const installed = serial ? execute(adb, ["-s", serial, "shell", "dumpsys", "package", packageName], { env }) : "";
  const installedCode = installed.match(/\bversionCode=(\d+)\b/)?.[1];
  let saved = fs.existsSync(configPath) ? JSON.parse(fs.readFileSync(configPath, "utf8")) : {};
  const featureKeys = ["LUDOPROOF_BILLING_ENABLED", "LUDOPROOF_ADS_ENABLED", "LUDOPROOF_INTERSTITIAL_ADS_ENABLED"];
  if (featureKeys.some(key => saved[key] === undefined && environment[`ORG_GRADLE_PROJECT_${key}`] === undefined)) {
    saved = configure(execute, configPath);
  }
  const properties = Object.fromEntries(keys.filter(key => typeof saved[key] === "string").map(key => [key, saved[key]]));
  for (const key of keys) {
    const override = environment[`ORG_GRADLE_PROJECT_${key}`];
    if (override !== undefined) properties[key] = override;
  }
  const requestedCode = properties.LUDOPAWS_VERSION_CODE;
  const validVersion = (value, minimum = 4) => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) >= minimum && Number(value) <= 2_100_000_000;
  if (requestedCode !== undefined && !validVersion(requestedCode)) throw new Error("LUDOPAWS_VERSION_CODE must be an integer between 4 and 2100000000.");
  if (installedCode !== undefined && !validVersion(installedCode, 1)) throw new Error("Installed versionCode could not be handled safely; no build or install was attempted.");
  if (environment.ORG_GRADLE_PROJECT_LUDOPAWS_VERSION_CODE !== undefined && requestedCode !== undefined && Number(requestedCode) < Number(installedCode ?? 0)) {
    throw new Error(`Requested local versionCode ${requestedCode} is older than installed ${installedCode}. Remove or increase ORG_GRADLE_PROJECT_LUDOPAWS_VERSION_CODE; no downgrade is attempted.`);
  }
  properties.LUDOPAWS_VERSION_CODE = String(Math.max(4, Number(requestedCode ?? 4), Number(installedCode ?? 0)));
  properties.LUDOPAWS_VERSION_NAME ||= `1.0.0-local.${properties.LUDOPAWS_VERSION_CODE}`;
  properties.LUDOPROOF_API_BASE_URL ||= "https://api.ludopaws.rekixo.com";
  properties.PLAY_GAMES_PROJECT_ID ||= "0";
  for (const key of featureKeys) {
    if (!["true", "false"].includes(properties[key])) {
      throw new Error(`Missing or invalid public feature configuration ${key}. Run configure; refusing to silently hide features.`);
    }
  }
  delete properties.GOOGLE_PLAY_BILLING_ENABLED;
  if (!properties.GOOGLE_SIGN_IN_WEB_CLIENT_ID?.endsWith(".apps.googleusercontent.com") || !/^[1-9][0-9]*$/.test(properties.PLAY_GAMES_PROJECT_ID)) {
    throw new Error("Google configuration is missing. Run configure; debug signing must match the registered Android credential.");
  }
  console.log(`Main app configuration: Billing=${properties.LUDOPROOF_BILLING_ENABLED}, Rewarded ads=${properties.LUDOPROOF_ADS_ENABLED}, Interstitial ads=${properties.LUDOPROOF_INTERSTITIAL_ADS_ENABLED}.`);
  syncSdkProperty(projectRoot, sdk);
  const androidDir = path.join(projectRoot, "android");
  const gradleArgs = [
    ...(options.includes("--verify") ? [":app:testDebugUnitTest", ":app:lintDebug"] : []),
    ":app:assembleDebug", ...Object.entries(properties).map(([key, value]) => `-P${key}=${value}`),
    "--console=plain", "--build-cache",
  ];
  console.log(`${mode}: ${assembleOnly ? "building only" : `building for device ${serial}`} debug APK; no release tasks or Play upload.`);
  console.log(`Local versionCode: ${properties.LUDOPAWS_VERSION_CODE}${installedCode ? ` (installed: ${installedCode})` : ""}.`);
  if (/installerPackageName=com\.android\.vending\b/.test(installed)) {
    console.log("Existing app was installed by Google Play. Matching versionCode does not make debug/Play signatures compatible; no uninstall or data clear will be attempted.");
  }
  console.log("Main app only; no developer UI or alternate package. Feature flags are printed above.");
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
    console.log("Installing debug APK on the selected device…");
    console.log(execute(adb, ["-s", serial, "install", "-r", apk], { env: selectedEnv }));
  } catch (error) {
    const guidance = /INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match/i.test(error.message)
      ? "The installed app and local debug APK use different signing keys. A higher versionCode cannot fix this. Use a matching Play-distributed build or a compatible signing key; this runner does not uninstall or clear data."
      : /INSTALL_FAILED_VERSION_DOWNGRADE/i.test(error.message)
        ? "The installed app may have updated after preflight. Run the command again to read its current versionCode."
        : "Check the Android install error above; a compatible device/build is required.";
    throw new Error(`${error.message}\nInstallation stopped. No app was uninstalled or downgraded. ${guidance}`);
  }
  execute(adb, ["-s", serial, "shell", "am", "force-stop", packageName], { env: selectedEnv });
  const launched = execute(adb, ["-s", serial, "shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", `${packageName}/com.rekixo.ludopaws.HomeActivity`], { env: selectedEnv });
  console.log(launched);
  if (/Error:|Error type \d|Status:\s*(?!ok\b)\S+/i.test(launched)) throw new Error("APK installed, but Android could not open HomeActivity. Check the launch output above.");
  console.log(`Installed and opened Ludo Paws on ${serial}.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
