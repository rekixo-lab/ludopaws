import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

class ProvisioningError extends Error {}

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const expectedProject = "rekixo-ludo-paws-511506";
const expectedIdentity = `ludopaws-referral-integrity@${expectedProject}.iam.gserviceaccount.com`;

async function main() {
  const argumentsList = process.argv.slice(2);
  const keyIndex = argumentsList.indexOf("--key-file");
  if (keyIndex < 0 || !argumentsList[keyIndex + 1] || argumentsList.some((argument, index) => argument !== "--apply" && argument !== "--key-file" && index !== keyIndex + 1)) {
    throw new ProvisioningError("Use --key-file /absolute/private/file.json; add --apply only to store the verified credentials in the existing Cloudflare Worker.");
  }
  const keyPath = argumentsList[keyIndex + 1];
  if (!path.isAbsolute(keyPath)) throw new ProvisioningError("The key file must be an absolute local path, not inline credential text.");
  const metadata = fs.statSync(keyPath);
  if (!metadata.isFile() || metadata.size > 32768 || (process.platform !== "win32" && (metadata.mode & 0o077))) {
    throw new ProvisioningError("Use a private regular JSON file under 32 KiB with owner-only permissions (chmod 600). Do not paste its contents into chat.");
  }
  let credential;
  try { credential = JSON.parse(fs.readFileSync(keyPath, "utf8")); }
  catch { throw new ProvisioningError("The local credential file is not valid service-account JSON."); }
  if (credential.type !== "service_account" || credential.project_id !== expectedProject || credential.client_email !== expectedIdentity ||
      credential.token_uri !== "https://oauth2.googleapis.com/token" || typeof credential.private_key !== "string") {
    throw new ProvisioningError("Use only the dedicated referral service-account credential from the linked Ludo Paws project, not the Billing identity.");
  }
  let privateKey;
  try { privateKey = crypto.createPrivateKey(credential.private_key); }
  catch { throw new ProvisioningError("The credential does not contain a valid private key."); }
  if (privateKey.asymmetricKeyType !== "rsa" || privateKey.asymmetricKeyDetails.modulusLength < 2048) throw new ProvisioningError("A Google-issued RSA key of at least 2048 bits is required.");
  const now = Math.floor(Date.now() / 1000);
  const encode = value => Buffer.from(JSON.stringify(value)).toString("base64url");
  const message = encode({ alg: "RS256", typ: "JWT", kid: credential.private_key_id }) + "." + encode({
    iss: expectedIdentity, scope: "https://www.googleapis.com/auth/playintegrity", aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 300,
  });
  const assertion = message + "." + crypto.sign("RSA-SHA256", Buffer.from(message), privateKey).toString("base64url");
  let response;
  try {
    response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST", signal: AbortSignal.timeout(15_000),
      body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
    });
  } catch { throw new ProvisioningError("Google OAuth could not be reached. No Cloudflare secrets were changed."); }
  if (!response.ok) throw new ProvisioningError(`Google rejected credential authentication (HTTP ${response.status}). No Cloudflare secrets were changed.`);
  const token = await response.json();
  if (typeof token.access_token !== "string" || Number(token.expires_in) <= 0) throw new ProvisioningError("Google did not return a usable OAuth token. No Cloudflare secrets were changed.");
  console.log("Dedicated service-account authentication verified with Google. No token or private key is printed.");
  console.log("This is not a real-device attestation, campaign approval or full token-decode acceptance result.");
  if (!argumentsList.includes("--apply")) { console.log("Check-only: no Cloudflare configuration changed."); return; }
  const bindings = JSON.stringify({ LUDOPAWS_INTEGRITY_SERVICE_ACCOUNT_EMAIL: expectedIdentity, LUDOPAWS_INTEGRITY_PRIVATE_KEY: credential.private_key });
  await new Promise((resolve, reject) => {
    const command = spawn("wrangler", ["secret", "bulk", "--config", path.join(root, "server", "wrangler.json")], {
      cwd: root, env: { ...process.env, WRANGLER_SEND_METRICS: "false" }, stdio: ["pipe", "ignore", "ignore"],
    });
    command.once("error", () => reject(new ProvisioningError("Could not start Wrangler. Check its installation and Cloudflare login.")));
    command.stdin.on("error", () => {});
    command.once("close", code => code === 0 ? resolve() : reject(new ProvisioningError("Cloudflare secret storage was not confirmed. Inspect secret-name metadata before retrying; do not print credential values.")));
    command.stdin.end(bindings);
  });
  console.log("Cloudflare accepted the two dedicated referral secret bindings. No application code was deployed and no feature flags were enabled.");
}

main().catch(error => { console.error(error instanceof ProvisioningError ? error.message : "Credential provisioning failed without exposing provider or credential payloads."); process.exitCode = 1; });
