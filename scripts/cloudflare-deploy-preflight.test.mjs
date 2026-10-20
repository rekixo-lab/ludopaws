import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { ACCOUNT_WEB_ORIGIN, buildCloudflareRuntimeVars } from "./cloudflare-runtime-config.mjs";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const preflightScript = path.join(repoRoot, "scripts", "cloudflare-deploy-preflight.mjs");
const adUnit = "ca-app-pub-1234567890123456/1234567890";
const appId = "ca-app-pub-1234567890123456~1234567890";

function runPreflight({ adsEnabled, billingEnabled = false, routeConfigured = true,
  operatorBilling = billingEnabled, operatorAds = adsEnabled }) {
  const baseConfig = JSON.parse(fs.readFileSync(path.join(repoRoot, "server", "wrangler.json"), "utf8"));
  const runtimeEnvironment = {
    LUDOPAWS_AD_SAFETY_REVIEW_APPROVED: "true",
    GOOGLE_SIGN_IN_WEB_CLIENT_ID: "1234567890-exampleclient.apps.googleusercontent.com",
    LUDOPROOF_ACCOUNT_WEB_ORIGIN: ACCOUNT_WEB_ORIGIN,
    GOOGLE_PLAY_BILLING_ENABLED: String(billingEnabled),
    LUDOPROOF_BILLING_ENABLED: String(billingEnabled),
    LUDOPROOF_ADMOB_SSV_ENABLED: String(adsEnabled),
    LUDOPROOF_ADS_ENABLED: String(adsEnabled),
    LUDOPROOF_ADMOB_REWARD_ITEM: "gems",
    ADMOB_APP_ID: appId,
    ADMOB_REWARDED_AD_UNIT_ID: adsEnabled ? adUnit : "",
    LUDOPROOF_ADMOB_REWARDED_AD_UNIT_ID: adsEnabled ? adUnit : "",
    GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL: billingEnabled
      ? "ludopaws-test@ludopaws-test.iam.gserviceaccount.com"
      : "",
  };
  const preparedConfig = {
    ...baseConfig,
    vars: { ...buildCloudflareRuntimeVars(baseConfig.vars ?? {}, {
      ...runtimeEnvironment, GOOGLE_PLAY_BILLING_ENABLED: "false", LUDOPROOF_BILLING_ENABLED: "false",
      LUDOPROOF_ADMOB_SSV_ENABLED: "false", LUDOPROOF_ADS_ENABLED: "false",
    }), GOOGLE_PLAY_BILLING_ENABLED: String(billingEnabled), LUDOPROOF_ADMOB_SSV_ENABLED: String(adsEnabled) },
  };
  if (!routeConfigured) delete preparedConfig.routes;
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "ludopaws-cloudflare-preflight-"));
  try {
    fs.mkdirSync(path.join(temporaryRoot, "server"), { recursive: true });
    fs.writeFileSync(
      path.join(temporaryRoot, "server", "wrangler.json"),
      JSON.stringify(preparedConfig, null, 2),
      "utf8",
    );
    return spawnSync(process.execPath, [preflightScript], {
      cwd: temporaryRoot,
      encoding: "utf8",
      env: {
        ...process.env,
        CLOUDFLARE_API_TOKEN: "test-token-not-used-by-preflight",
        CLOUDFLARE_ACCOUNT_ID: "8f31060fbcfee2b3e9309138cad7f12f",
        LUDOPROOF_SESSION_HMAC_KEY: "",
        LUDOPROOF_BILLING_ENABLED: String(operatorBilling),
        LUDOPROOF_ADS_ENABLED: String(operatorAds),
      },
    });
  } finally {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
  }
}

test("production preflight permits reviewed advertising in family V2", () => {
  const result = runPreflight({ adsEnabled: true, billingEnabled: false });
  assert.equal(result.status, 0, result.stderr);
});

test("production preflight permits configured billing in family V2", () => {
  const result = runPreflight({ adsEnabled: false, billingEnabled: true });
  assert.equal(result.status, 0, result.stderr);
});

test("production preflight passes family multiplayer configuration after validation", () => {
  const result = runPreflight({ adsEnabled: false, billingEnabled: false });
  assert.equal(result.status, 0, "stdout: " + result.stdout + " stderr: " + result.stderr);
});

test("production preflight requires the API hostname to target the deployed Worker", () => {
  const result = runPreflight({ adsEnabled: true, routeConfigured: false });
  assert.notEqual(result.status, 0, "stdout: " + result.stdout + " stderr: " + result.stderr);
  assert.match(result.stderr, /must explicitly route api\.ludopaws\.rekixo\.com/);
});


test("deployment workflow checks the Cloudflare private key before enabling Billing and verifies expected live flags", () => {
  const workflow = fs.readFileSync(path.join(repoRoot, ".github", "workflows", "deploy-cloudflare.yml"), "utf8");
  const secretStep = workflow.split("name: Verify Google Play service-account secret when Billing is enabled")[1]?.split("name: Wrangler production dry run")[0] ?? "";
  assert.ok(secretStep.includes("GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY"));
  assert.ok(secretStep.includes("env.GOOGLE_PLAY_BILLING_ENABLED == 'true'"));
  const statusStep = workflow.split("name: Verify store status route and feature flags")[1]?.split("name: Verify live production game flow")[0] ?? "";
  assert.ok(statusStep.includes('const expectedBilling = process.env.GOOGLE_PLAY_BILLING_ENABLED === "true";'));
  assert.ok(statusStep.includes("body.billingAvailable !== expectedBilling"));
  assert.ok(statusStep.includes("androidBillingEnabled !== expectedBilling"));
  assert.equal(statusStep.includes("body.subscriptionsAvailable !== expectedSubscriptions"), false);
  assert.equal(statusStep.includes("androidSubscriptionsEnabled !== expectedSubscriptions"), false);
});

test("production readiness verifier reads its response from the explicit READY_FILE environment variable", () => {
  const workflow = fs.readFileSync(path.join(repoRoot, ".github", "workflows", "deploy-cloudflare.yml"), "utf8");
  const readinessStep = workflow.split("name: Verify production readiness")[1]?.split("name: Verify store status route and feature flags")[0] ?? "";
  assert.ok(readinessStep.includes('READY_FILE="$ready_file" node -e'));
  assert.ok(readinessStep.includes('readFileSync(process.env.READY_FILE, "utf8")'));
  assert.equal(readinessStep.includes("readFileSync(process.argv[1]"), false);
  const scriptStartMarker = "READY_FILE=\"$ready_file\" node -e '";
  const scriptStart = readinessStep.indexOf(scriptStartMarker) + scriptStartMarker.length;
  const scriptEnd = readinessStep.indexOf("\n          '", scriptStart);
  const inlineNodeScript = readinessStep.slice(scriptStart, scriptEnd);
  assert.ok(scriptStart >= scriptStartMarker.length && scriptEnd > scriptStart, "readiness inline Node.js script must be present");
  assert.equal(inlineNodeScript.includes("'"), false, "single-quoted bash inline script must not contain unescaped apostrophes");
});


test("production preflight refuses stale Android billing/ads operator flags even with Worker flags disabled", () => {
  for (const override of [{ operatorBilling: true }, { operatorAds: true }]) {
    const result = runPreflight({ adsEnabled: false, billingEnabled: false, ...override });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /flags must match/);
  }
});

test("production deployment reads matching reviewed integration flags for family V2", () => {
  const workflow = fs.readFileSync(path.join(repoRoot, ".github", "workflows", "deploy-cloudflare.yml"), "utf8");
  for (const key of [
    "GOOGLE_PLAY_BILLING_ENABLED", "LUDOPROOF_BILLING_ENABLED",
    "LUDOPROOF_ADMOB_SSV_ENABLED", "LUDOPROOF_ADS_ENABLED",
  ]) {
    assert.ok(workflow.includes("      " + key + ": " + "$" + "{{ vars." + key + " }}"), key + " must use configured values");
  }
  assert.ok(workflow.includes("LUDOPROOF_AGE_SAFETY_MODE: FAMILY_V2"));
  assert.ok(workflow.includes("environment: production"));
  assert.ok(workflow.includes("workflow_dispatch:"));
});
