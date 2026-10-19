import fs from "node:fs";
import { createHash } from "node:crypto";

const server =
  JSON.parse(
    fs.readFileSync(
      "server/package.json",
      "utf8",
    ),
  );
const androidBuild =
  fs.readFileSync(
    "android/build.gradle.kts",
    "utf8",
  );
const agp =
  androidBuild.match(
    /com\.android\.application"\) version "([^"]+)"/,
  )?.[1] ?? "unknown";
const kotlin =
  androidBuild.match(
    /org\.jetbrains\.kotlin\.android"\) version "([^"]+)"/,
  )?.[1] ?? "unknown";

const bom = {
  bomFormat: "CycloneDX",
  specVersion: "1.5",
  version: 1,
  metadata: {
    component: {
      type: "application",
      name: "LudoProof",
      version:
        "1.0.0-rc1",
    },
    properties: [
      {
        name:
          "ludoproof.offline.entronex.sourceCommit",
        value:
          "d219148338b43e961689a7a7ab6f8ce3eb88e8de",
      },
    ],
  },
  components: [
    {
      type: "application",
      name: server.name,
      version:
        server.version,
      bomRef:
        "ludoproof-server",
    },
    {
      type: "framework",
      name:
        "Android Gradle Plugin",
      version: agp,
      bomRef:
        "android-gradle-plugin",
    },
    {
      type: "framework",
      name:
        "Kotlin Android",
      version: kotlin,
      bomRef:
        "kotlin-android",
    },
    {
      type: "library",
      name:
        "EntroNex v4 Android offline derivation",
      version:
        "d219148338b43e961689a7a7ab6f8ce3eb88e8de",
      purl:
        "pkg:github/rajkumarlahare/entronex@d219148338b43e961689a7a7ab6f8ce3eb88e8de",
      bomRef:
        "entronex-v4",
    },
  ],
};

const output =
  JSON.stringify(
    bom,
    null,
    2,
  ) + "\n";
fs.writeFileSync(
  "ludoproof-sbom.cdx.json",
  output,
);
fs.writeFileSync(
  "ludoproof-sbom.sha256",
  createHash("sha256")
    .update(output)
    .digest("hex") +
    "  ludoproof-sbom.cdx.json\n",
);
console.log(
  "Generated CycloneDX SBOM.",
);
