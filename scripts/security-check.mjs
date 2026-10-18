import fs from "node:fs";
import path from "node:path";

const roots = [
  "server/src",
  "android/app/src/main",
  ".github/workflows",
];

const findings = [];
const files = [];

for (const root of roots) {
  walk(root);
}

for (const file of files.sort()) {
  const content =
    fs.readFileSync(
      file,
      "utf8",
    );

  for (const rule of [
    [
      "non-cryptographic RNG",
      /\bMath\.random\s*\(/g,
    ],
    [
      "dynamic eval",
      /(^|[^A-Za-z0-9_$])eval\s*\(/g,
    ],
    [
      "dynamic Function constructor",
      /\bnew\s+Function\s*\(/g,
    ],
    [
      "cleartext Android traffic",
      /cleartextTrafficPermitted\s*=\s*"true"/g,
    ],
  ]) {
    for (
      const match of
      content.matchAll(rule[1])
    ) {
      findings.push(
        file +
          ":" +
          lineFor(
            content,
            match.index ?? 0,
          ) +
          ": " +
          rule[0],
      );
    }
  }

  if (
    file.startsWith(
      ".github/workflows/",
    )
  ) {
    for (
      const match of
      content.matchAll(
        /^\s*uses:\s+([^\s]+)\s*$/gm,
      )
    ) {
      const value =
        match[1];
      if (
        !/@[0-9a-f]{40}(?:\s*#.*)?$/i.test(
          value,
        )
      ) {
        findings.push(
          file +
            ":" +
            lineFor(
              content,
              match.index ?? 0,
            ) +
            ": GitHub action is not pinned to a 40-character commit SHA",
        );
      }
    }
  }
}

const gitignore =
  fs.readFileSync(
    ".gitignore",
    "utf8",
  );
for (const pattern of [
  "*.jks",
  "*.keystore",
  "key.properties",
  "keystore.properties",
]) {
  if (!gitignore.includes(pattern)) {
    findings.push(
      ".gitignore: missing Android signing-secret pattern " +
        pattern,
    );
  }
}

const serverPackage =
  JSON.parse(
    fs.readFileSync(
      "server/package.json",
      "utf8",
    ),
  );
const androidBuild =
  fs.readFileSync(
    "android/app/build.gradle.kts",
    "utf8",
  );
const androidVersion =
  androidBuild.match(
    /versionName\s*=\s*"([^"]+)"/,
  )?.[1] ?? null;
if (
  !androidVersion ||
  serverPackage.version !==
    androidVersion
) {
  findings.push(
    "release version drift between server and Android",
  );
}

for (const file of [
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/local/PendingRollStore.kt",
  "android/app/src/main/java/com/rekixo/ludopaws/feature/online/data/local/SecureSessionStore.kt",
]) {
  const content =
    fs.readFileSync(
      file,
      "utf8",
    );
  if (
    !content.includes(
      "cipher.updateAAD(",
    )
  ) {
    findings.push(
      file +
        ": encrypted metadata is not AAD-bound",
    );
  }
}

const ci =
  fs.readFileSync(
    ".github/workflows/ci.yml",
    "utf8",
  );
for (const invariant of [
  ":app:testDebugUnitTest",
  ":app:bundleRelease",
  "hash-android-release.mjs",
]) {
  if (!ci.includes(invariant)) {
    findings.push(
      ".github/workflows/ci.yml: missing release gate " +
        invariant,
    );
  }
}


const nativeServerDice = "server/src/dice-fast.js";
const nativeAndroidDice = "android/app/src/main/java/com/rekixo/ludopaws/feature/proof/domain/core/NativeDiceEngine.kt";
const offlineEngine = "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/engine/OfflineGameEngine.kt";
for (const file of [nativeServerDice, nativeAndroidDice, offlineEngine]) {
  if (!fs.existsSync(file)) findings.push(file + ": native dice implementation is missing");
}
if (fs.existsSync(nativeServerDice)) {
  const source = fs.readFileSync(nativeServerDice, "utf8");
  for (const invariant of ["crypto.getRandomValues", "ACCEPT_LIMIT", "createFastDiceReceipt"]) {
    if (!source.includes(invariant)) findings.push(nativeServerDice + ": missing secure dice invariant " + invariant);
  }
}
if (fs.existsSync(nativeAndroidDice)) {
  const source = fs.readFileSync(nativeAndroidDice, "utf8");
  for (const invariant of ["SecureRandom", "ACCEPT_LIMIT", "rejection sampling", "fun verify"]) {
    if (!source.includes(invariant)) findings.push(nativeAndroidDice + ": missing native dice invariant " + invariant);
  }
}
if (fs.existsSync(offlineEngine)) {
  const source = fs.readFileSync(offlineEngine, "utf8");
  if (!source.includes("NativeDiceEngine.createReceipt") || !source.includes("audit.verify()")) {
    findings.push(offlineEngine + ": offline rolls must use and verify native dice receipts");
  }
}
const deploymentWorkflowPath = ".github/workflows/deploy-cloudflare.yml";
if (fs.existsSync(deploymentWorkflowPath)) {
  const workflow = fs.readFileSync(deploymentWorkflowPath, "utf8");
  for (const invariant of ["workflow_dispatch:", "scripts/cloudflare-deploy-preflight.mjs", "scripts/security-check.mjs", "production-game-smoke.mjs"]) {
    if (!workflow.includes(invariant)) findings.push(deploymentWorkflowPath + ": missing production deployment gate " + invariant);
  }
}

// Scan repository text for any remaining name of the retired external dice provider.
const retiredName = "entro" + "nex";
const candidateTextFiles = [];
for (const root of ["server", "android/app/src", ".github/workflows", "scripts", "docs", "README.md"]) {
  walk(root, candidateTextFiles);
}
for (const file of new Set(candidateTextFiles)) {
  if (!fs.existsSync(file)) continue;
  const content = fs.readFileSync(file, "utf8");
  if (content.toLowerCase().includes(retiredName)) {
    findings.push(file + ": retired dice-provider reference remains");
  }
 
}

if (findings.length > 0) {
  console.error(
    "LudoProof security gate failed:",
  );
  for (const finding of findings) {
    console.error(
      "- " + finding,
    );
  }
  process.exit(2);
}

console.log(
  "LudoProof security gate passed (" +
    files.length +
    " files inspected).",
);

function walk(current, target = files) {
  if (!fs.existsSync(current)) {
    return;
  }
  const stat =
    fs.statSync(current);
  if (stat.isFile()) {
    if (
      /\.(?:js|mjs|kt|xml|yml|yaml|json|md|txt|kts|gradle|properties|sh)$/.test(current) ||
      current.endsWith(".env.example")
    ) {
      target.push(
        current.replaceAll(
          "\\",
          "/",
        ),
      );
    }
    return;
  }

  for (
    const entry of
    fs.readdirSync(
      current,
      {
        withFileTypes: true,
      },
    )
  ) {
    if (
      entry.name ===
        "build" ||
      entry.name ===
        ".git" ||
      entry.name ===
        "node_modules" ||
      entry.name ===
        ".gradle"
    ) {
      continue;
    }
    walk(
      path.join(
        current,
        entry.name,
      ),
      target,
    );
  }
}

function lineFor(
  content,
  offset,
) {
  return (
    content
      .slice(0, offset)
      .split("\n")
      .length
  );
}
