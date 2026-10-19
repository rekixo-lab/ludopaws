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

const deployWorkflowPath =
  ".github/workflows/deploy-cloudflare.yml";
if (
  fs.existsSync(
    deployWorkflowPath,
  )
) {
  const deployWorkflow =
    fs.readFileSync(
      deployWorkflowPath,
      "utf8",
    );
  for (const invariant of [
    "CLOUDFLARE_DEPLOY_ENABLED",
    "CLOUDFLARE_API_TOKEN",
    "CLOUDFLARE_ACCOUNT_ID",
    "LUDOPROOF_SESSION_HMAC_KEY",
    "cloudflare-deploy-preflight.mjs",
    "secret list --format json",
    "randomBytes(48)",
    "/health",
    "/ready",
    "production-game-smoke.mjs",
  ]) {
    if (
      !deployWorkflow.includes(
        invariant,
      )
    ) {
      findings.push(
        deployWorkflowPath +
          ": missing production deploy invariant " +
          invariant,
      );
    }
  }
}

const wrangler =
  JSON.parse(
    fs.readFileSync(
      "server/wrangler.json",
      "utf8",
    ),
  );
if ((wrangler.services ?? []).some((entry) => entry.binding?.startsWith("ENTRONEX")) ||
    Object.keys(wrangler.vars ?? {}).some((key) => key.startsWith("ENTRONEX_"))) {
  findings.push("server/wrangler.json: obsolete online dice service dependency");
}

const offlineV4Files = {
  core: "android/app/src/main/java/com/rekixo/ludopaws/feature/proof/domain/core/EntroNexV4Local.kt",
  binding: "android/app/src/main/java/com/rekixo/ludopaws/feature/proof/domain/offline/OfflineLudoV4Binding.kt",
  gameplayBinding: "android/app/src/main/java/com/rekixo/ludopaws/feature/proof/domain/offline/OfflineLudoV3Binding.kt",
  engine: "android/app/src/main/java/com/rekixo/ludopaws/feature/offline/domain/engine/OfflineGameEngine.kt",
  fairness: "android/app/src/main/java/com/rekixo/ludopaws/feature/proof/domain/offline/OfflineFairnessChain.kt",
  conformance:
    "android/app/src/test/java/com/rekixo/ludopaws/EntroNexV4LocalConformanceTest.kt",
};

for (const file of Object.values(offlineV4Files)) {
  if (!fs.existsSync(file)) {
    findings.push(
      file +
        ": missing offline v4 invariant file",
    );
  }
}

if (
  Object.values(offlineV4Files).every(
    (file) =>
      fs.existsSync(
        file,
      ),
  )
) {
  const core =
    fs.readFileSync(
      offlineV4Files.core,
      "utf8",
    );
  for (const invariant of [
    "entronex-v4-dual-commit-hkdf-sha256-context-bound",
    "entronex:v4:client-commit:",
    "entronex:v4:server-commit:",
    "entronex:v4:transcript:",
    "entronex:v4:outcome",
    "entronex:v4:natural-world",
    "direct-outcome-selection",
    "entronex:v1:stream:",
    "281_474_976_710_656L",
    "balanced-swap-witness",
    "entronex:natural-world:v1:manifest:",
  ]) {
    if (!core.includes(invariant)) {
      findings.push(
        offlineV4Files.core +
          ": missing frozen v4 invariant " +
          invariant,
      );
    }
  }

  const binding =
    fs.readFileSync(
      offlineV4Files.binding,
      "utf8",
    );
  for (const invariant of [
    "cellsPerOutcome =",
    "16",
    "timelineTicks =",
    "512",
    "epochCount =",
    "8",
    "probeCount =",
    "3",
    "entronex:v4:game-state:",
    "entronex:v4:game-ruleset:",
    "ludoproof:actor:v1:",
    '"DICE_ROLL"',
  ]) {
    if (!binding.includes(invariant)) {
      findings.push(
        offlineV4Files.binding +
          ": missing Ludo v4 binding invariant " +
          invariant,
      );
    }
  }

  const gameplayBinding =
    fs.readFileSync(
      offlineV4Files.gameplayBinding,
      "utf8",
    );
  for (const invariant of [
    "OfflineLudoV3Binding",
    '"ludoproof-standard-v3"',
    "EntroNexV4Local.resolve",
    "entronex:v4:game-state:",
    "entronex:v4:game-ruleset:",
    "openingRollConsumed",
    '"GUARANTEED_SIX_ONCE_PER_PLAYER"',
    '"AUTO_MOVE"',
  ]) {
    if (!gameplayBinding.includes(invariant)) {
      findings.push(
        offlineV4Files.gameplayBinding +
          ": missing v3 gameplay proof-binding invariant " +
          invariant,
      );
    }
  }

  const engine =
    fs.readFileSync(
      offlineV4Files.engine,
      "utf8",
    );
  if (
    !engine.includes(
      "OfflineLudoV3Binding",
    ) ||
    !engine.includes(
      "EntroNexV4Local.verify",
    ) ||
    !engine.includes(
      "LudoTurnAutomationPolicy.effectiveOutcome",
    )
  ) {
    findings.push(
      offlineV4Files.engine +
        ": offline rolls do not fail closed through the versioned v4 proof binding/verifier",
    );
  }
  for (const forbidden of [
    "nextInt(6)",
    "nextInt(6) + 1",
  ]) {
    if (engine.includes(forbidden)) {
      findings.push(
        offlineV4Files.engine +
          ": direct local dice shortcut is forbidden: " +
          forbidden,
      );
    }
  }

  const fairness =
    fs.readFileSync(
      offlineV4Files.fairness,
      "utf8",
    );
  for (const invariant of [
    "ludoproof-roll-chain-v1",
    "ludoproof:fairness-receipt:v1:",
    "previousFairnessDigest",
    "fairnessDigest",
  ]) {
    if (!fairness.includes(invariant)) {
      findings.push(
        offlineV4Files.fairness +
          ": missing offline fairness-chain invariant " +
          invariant,
      );
    }
  }

  const conformance =
    fs.readFileSync(
      offlineV4Files.conformance,
      "utf8",
    );
  for (const invariant of [
    "bb2b3f384f6a1ef71c2680402c30c01bbe23a5e7faf2b025553095d7ab36d27e",
    "0cdf48f6a5c0afbf3837ff073a73963b57415397fb70339e3949be7e16caeffe",
    "eabd09e6dad8342bb5cc8724c9bcbb90ae95263fb5dc847449739fa14ac102a3",
  ]) {
    if (!conformance.includes(invariant)) {
      findings.push(
        offlineV4Files.conformance +
          ": frozen cross-language v4 vector drift",
      );
    }
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

function walk(current) {
  if (!fs.existsSync(current)) {
    return;
  }
  const stat =
    fs.statSync(current);
  if (stat.isFile()) {
    if (
      /\.(?:js|mjs|kt|xml|yml|yaml)$/.test(
        current,
      )
    ) {
      files.push(
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
        ".gradle"
    ) {
      continue;
    }
    walk(
      path.join(
        current,
        entry.name,
      ),
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
