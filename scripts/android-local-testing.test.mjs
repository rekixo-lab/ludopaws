import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { main } from "./android-local-testing.mjs";

function fixture(context, options = {}) {
  const projectRoot = fs.mkdtempSync(path.join(os.tmpdir(), "ludopaws runner & "));
  context.after(() => fs.rmSync(projectRoot, { recursive: true, force: true }));
  const sdk = path.join(projectRoot, "SDK with spaces");
  fs.mkdirSync(path.join(sdk, "platform-tools"), { recursive: true });
  for (const name of ["adb", "adb.exe"]) fs.writeFileSync(path.join(sdk, "platform-tools", name), "fixture");
  fs.mkdirSync(path.join(projectRoot, "android"));
  fs.writeFileSync(path.join(projectRoot, "android", "local.properties"), "# Keep user settings\ncustom.setting=retained\nsdk.dir=/old/sdk\n");
  const configPath = path.join(projectRoot, "config", "testing.json");
  fs.mkdirSync(path.dirname(configPath));
  fs.writeFileSync(configPath, JSON.stringify({ GOOGLE_SIGN_IN_WEB_CLIENT_ID: "fixture.apps.googleusercontent.com", PLAY_GAMES_PROJECT_ID: "123456", LUDOPROOF_BILLING_ENABLED: "true", LUDOPROOF_ADS_ENABLED: "true", LUDOPROOF_INTERSTITIAL_ADS_ENABLED: "true", LUDOPAWS_AD_SAFETY_REVIEW_APPROVED: "true" }));
  const calls = [];
  const execute = (program, args, settings = {}) => {
    calls.push({ program, args, settings });
    if (program === "gh") return JSON.stringify([{ variables: [
      { name: "GOOGLE_SIGN_IN_WEB_CLIENT_ID", value: "fixture.apps.googleusercontent.com" },
      { name: "PRIVATE_KEY", value: "must-not-be-cached" },
    ] }]);
    if (args[0] === "devices") return options.devices ?? "List of devices attached\nphone-a\tdevice\n";
    if (args.includes("dumpsys")) return options.installed ?? "Unable to find package\n";
    if (program === "bash" || program === "powershell.exe") {
      if (options.buildFails) throw new Error("Gradle failed");
      if (!options.missingApk) {
        const apk = path.join(projectRoot, "android", "app", "build", "outputs", "apk", "debug", "app-debug.apk");
        fs.mkdirSync(path.dirname(apk), { recursive: true }); fs.writeFileSync(apk, "fixture");
      }
    }
    if (args.includes("install") && options.installFails) throw new Error("INSTALL_FAILED_UPDATE_INCOMPATIBLE");
    return args.includes("shell") ? options.launchOutput ?? "Status: ok\n" : "";
  };
  return { calls, configPath, projectRoot, environment: { ANDROID_HOME: sdk, ...options.environment }, platform: options.platform ?? "linux", execute };
}

test("VS Code and terminal main app share a single entry point", () => {
  const tasks = JSON.parse(fs.readFileSync(new URL("../.vscode/tasks.json", import.meta.url)));
  assert.equal(tasks.tasks.length, 1);
  assert.equal(tasks.tasks[0].command, "node");
  assert.deepEqual(tasks.tasks[0].args, ["${workspaceFolder}/scripts/android-local-testing.mjs", "run"]);
  assert.equal(tasks.tasks[0].group.isDefault, true);
  assert.equal(fs.existsSync(new URL("./run-ludoproof.ps1", import.meta.url)), false);
});

test("main app installs on the exact selected device with published feature flags", context => {
  const setup = fixture(context, { devices: "phone-a\tdevice\nphone-b\tdevice", environment: { ANDROID_SERIAL: "phone-b" } });
  main(["run"], setup);
  const build = setup.calls.find(call => call.program === "bash");
  assert.equal(build.args.some(value => /LUDOPAWS_(?:DEV_BUILD|QA_BUILD|TEST_ADS)/.test(value)), false);
  assert.ok(build.args.includes("-PLUDOPROOF_BILLING_ENABLED=true"));
  assert.ok(build.args.includes("-PLUDOPROOF_ADS_ENABLED=true"));
  assert.equal(build.args.some(value => /Release|installDebug/.test(value)), false);
  const operations = setup.calls.filter(call => call.args[0] === "-s" && !call.args.includes("dumpsys"));
  assert.deepEqual(operations[0].args.slice(0, 4), ["-s", "phone-b", "install", "-r"]);
  assert.equal(operations.find(call => call.args.includes("start")).args.at(-1), "com.rekixo.ludopaws/com.rekixo.ludopaws.HomeActivity");
  assert.ok(operations.every(call => call.args[1] === "phone-b"));
  assert.match(fs.readFileSync(path.join(setup.projectRoot, "android", "local.properties"), "utf8"), /custom.setting=retained/);
});

for (const devices of ["", "phone-a\tunauthorized", "phone-a\toffline", "phone-a\tdevice\nphone-b\tdevice"]) {
  test(`unsafe device selection stops before build/install: ${JSON.stringify(devices)}`, context => {
    const setup = fixture(context, { devices });
    assert.throws(() => main(["run"], setup), /authorized|Multiple/);
    assert.equal(setup.calls.length, 1);
  });
}

test("an explicit unavailable serial never falls back to another phone", context => {
  const setup = fixture(context, { environment: { ANDROID_SERIAL: "missing" } });
  assert.throws(() => main(["run"], setup), /missing.*not connected/);
  assert.equal(setup.calls.length, 1);
});

test("assemble plus verify runs tests/lint without contacting any device", context => {
  const setup = fixture(context, { devices: "" });
  main(["run", "--assemble", "--verify"], setup);
  assert.equal(setup.calls.length, 1);
  assert.deepEqual(setup.calls[0].args.slice(1, 4), [":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"]);
});

test("removed preview mode cannot build or install an alternate app", context => {
  const setup = fixture(context);
  assert.throws(() => main(["preview"], setup), /Invalid arguments/);
  assert.equal(setup.calls.length, 0);
});

for (const options of [{ buildFails: true }, { installFails: true }, { missingApk: true }]) {
  test(`build/install failure never uninstalls, clears data or launches: ${JSON.stringify(options)}`, context => {
    const setup = fixture(context, options);
    assert.throws(() => main(["run"], setup), /failed|stopped|expected debug APK/);
    assert.equal(setup.calls.some(call => call.args.includes("start") || call.args.includes("uninstall") || call.args.includes("-d")), false);
  });
}

test("launch errors are reported even when adb exits successfully", context => {
  const setup = fixture(context, { launchOutput: "Error type 3\nError: Activity class does not exist" });
  assert.throws(() => main(["run"], setup), /could not open/);
});

test("Windows uses the checked-in batch wrapper, not bash or a Gradle downloader", context => {
  const setup = fixture(context, { platform: "win32" });
  main(["run", "--assemble"], setup);
  assert.equal(setup.calls.length, 1);
  const build = setup.calls[0];
  assert.equal(build.program, "powershell.exe");
  assert.equal(build.settings.env.LUDOPAWS_LOCAL_GRADLE_WRAPPER, path.join(setup.projectRoot, "android", "gradlew.bat"));
  assert.ok(JSON.parse(build.settings.env.LUDOPAWS_LOCAL_GRADLE_ARGS).includes(":app:assembleDebug"));
  assert.equal(build.args.at(-1).includes(setup.projectRoot), false);
});

test("configure saves only allowlisted public values outside the repo", context => {
  const setup = fixture(context); main(["configure"], setup);
  assert.deepEqual(JSON.parse(fs.readFileSync(setup.configPath, "utf8")), { GOOGLE_SIGN_IN_WEB_CLIENT_ID: "fixture.apps.googleusercontent.com" });
  assert.equal(setup.calls.length, 2);
  assert.equal(setup.calls.some(call => call.program !== "gh"), false);
});

test("invalid options and missing Google configuration cannot start a build", context => {
  const setup = fixture(context);
  assert.throws(() => main(["run", "--release"], setup), /Invalid arguments/);
  const saved = JSON.parse(fs.readFileSync(setup.configPath, "utf8"));
  delete saved.GOOGLE_SIGN_IN_WEB_CLIENT_ID;
  fs.writeFileSync(setup.configPath, JSON.stringify(saved));
  assert.throws(() => main(["run", "--assemble"], setup), /Google configuration is missing/);
  assert.equal(setup.calls.length, 0);
});

test("missing public feature configuration refreshes safely without starting a build", context => {
  const setup = fixture(context);
  fs.writeFileSync(setup.configPath, "{}");
  assert.throws(() => main(["run", "--assemble"], setup), /Missing or invalid public feature configuration/);
  assert.equal(setup.calls.length, 2);
  assert.ok(setup.calls.every(call => call.program === "gh"));
});

test("installed Play version 10 produces local version 10, never the default 4", context => {
  const setup = fixture(context, { installed: "versionCode=10 minSdk=28\ninstallerPackageName=com.android.vending" });
  main(["run"], setup);
  const build = setup.calls.find(call => call.program === "bash");
  assert.ok(build.args.includes("-PLUDOPAWS_VERSION_CODE=10"));
  assert.ok(build.args.includes("-PLUDOPAWS_VERSION_NAME=1.0.0-local.10"));
  const inspect = setup.calls.find(call => call.args.includes("dumpsys"));
  assert.equal(inspect.args[1], "phone-a");
  assert.equal(inspect.args.at(-1), "com.rekixo.ludopaws");
});

test("cached release version does not force a downgrade of an installed main app", context => {
  const setup = fixture(context, { installed: "versionCode=19" });
  const config = JSON.parse(fs.readFileSync(setup.configPath, "utf8"));
  fs.writeFileSync(setup.configPath, JSON.stringify({ ...config, LUDOPAWS_VERSION_CODE: "10" }));
  main(["run"], setup);
  assert.ok(setup.calls.find(call => call.program === "bash").args.includes("-PLUDOPAWS_VERSION_CODE=19"));
});

test("an explicitly requested lower version fails before Gradle or installation", context => {
  const setup = fixture(context, { installed: "versionCode=10", environment: { ORG_GRADLE_PROJECT_LUDOPAWS_VERSION_CODE: "4" } });
  assert.throws(() => main(["run"], setup), /older than installed 10/);
  assert.equal(setup.calls.some(call => call.program === "bash" || call.args.includes("install")), false);
});
