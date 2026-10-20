import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";

const roots = [
  "android/app/build/outputs/apk/release",
  "android/app/build/outputs/bundle/release",
];

const files = [];
for (const root of roots) {
  collect(root);
}

const artifacts =
  files
    .filter((file) =>
      /\.(?:apk|aab)$/i.test(file),
    )
    .sort()
    .map((file) => ({
      path: file.replaceAll("\\", "/"),
      bytes:
        fs.statSync(file).size,
      sha256:
        sha256File(file),
      uploadSignature: verifyUploadSignature(file),
    }));

if (artifacts.length < 2) {
  throw new Error(
    "Expected release APK and AAB artifacts",
  );
}

// AGP output metadata records the values actually used for this built variant,
// including release-workflow version overrides; source regexes cannot do that.
const apkMetadata = JSON.parse(fs.readFileSync("android/app/build/outputs/apk/release/output-metadata.json", "utf8"));
const variant = apkMetadata.elements?.[0];
const versionName = variant?.versionName;
const versionCode = variant?.versionCode;
if (!versionName || !Number.isSafeInteger(versionCode) || versionCode < 1 ||
    apkMetadata.elements.some(item => item.versionName !== versionName || item.versionCode !== versionCode)) {
  throw new Error("Release APK version metadata is absent or inconsistent");
}
const inventory = JSON.parse(fs.readFileSync("android/build/reports/release-dependencies.json", "utf8"));
if (inventory.versionName !== versionName || inventory.versionCode !== versionCode) {
  throw new Error("Release APK and resolved dependency evidence refer to different versions");
}

const metadata = {
  schemaVersion: 1,
  product: "LudoProof",
  releaseChannel:
    "release-candidate",
  versionName,
  versionCode,
  gitCommit:
    process.env.GITHUB_SHA ??
    "local",
  generatedAt:
    new Date()
      .toISOString(),
  signing: {
    repositoryContainsSigningKey: false,
    ciArtifactIsStoreSigned: false,
    uploadSignaturesVerified: artifacts.every(artifact => artifact.uploadSignature.verified),
    note: "Upload signatures are checked per artifact. Google Play distribution signing is separate and is not asserted by this metadata.",
  },
  artifacts,
};

const output =
  JSON.stringify(
    metadata,
    null,
    2,
  ) + "\n";
fs.writeFileSync(
  "ludoproof-android-release.json",
  output,
);

const sums =
  artifacts
    .map(
      (artifact) =>
        artifact.sha256 +
        "  " +
        artifact.path,
    )
    .join("\n") +
  "\n";
fs.writeFileSync(
  "ludoproof-android-release.sha256",
  sums,
);

console.log(
  "Generated Android release evidence for " +
    artifacts.length +
    " artifacts.",
);

function collect(current) {
  if (!fs.existsSync(current)) {
    return;
  }
  const stat =
    fs.statSync(current);
  if (stat.isFile()) {
    files.push(current);
    return;
  }
  for (
    const entry of
    fs.readdirSync(current)
  ) {
    collect(
      path.join(
        current,
        entry,
      ),
    );
  }
}

function sha256File(file) {
  return createHash(
    "sha256",
  )
    .update(
      fs.readFileSync(file),
    )
    .digest("hex");
}

function verifyUploadSignature(file) {
  try {
    if (file.toLowerCase().endsWith(".apk")) {
      const sdkRoot = process.env.ANDROID_SDK_ROOT ?? process.env.ANDROID_HOME;
      if (!sdkRoot) return { verified: false, reason: "android-sdk-not-configured" };
      const toolsRoot = path.join(sdkRoot, "build-tools");
      const version = fs.readdirSync(toolsRoot).filter(v => fs.existsSync(path.join(toolsRoot, v, "apksigner")))
        .sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).at(-1);
      if (!version) return { verified: false, reason: "apksigner-unavailable" };
      const result = execFileSync(path.join(toolsRoot, version, "apksigner"), ["verify", "--print-certs", file], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
      const certificateSha256 = result.match(/Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]+)/)?.[1]?.toLowerCase();
      return { verified: true, ...(certificateSha256 ? { certificateSha256 } : {}) };
    }
    const result = execFileSync("jarsigner", ["-verify", file], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
    return { verified: /jar verified\./i.test(result) && !/jar is unsigned/i.test(result) };
  } catch {
    return { verified: false, reason: "unsigned-or-verification-failed" };
  }
}
