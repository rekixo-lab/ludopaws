import fs from "node:fs";
import { createHash } from "node:crypto";

const inventoryPath = process.env.LUDOPAWS_DEPENDENCY_INVENTORY ?? "android/build/reports/release-dependencies.json";
if (!fs.existsSync(inventoryPath)) {
  throw new Error("Resolved release inventory missing. Run gradle -p android :app:writeReleaseDependencyInventory first.");
}
const inventory = JSON.parse(fs.readFileSync(inventoryPath, "utf8"));
if (inventory.configuration !== "releaseRuntimeClasspath" || !inventory.versionName || !inventory.components?.length) {
  throw new Error("Expected a resolved release runtime dependency inventory");
}
const reference = item => `${item.group}:${item.name}:${item.version}`;
const components = inventory.components.map(item => ({
  type: "library", "bom-ref": reference(item), group: item.group, name: item.name, version: item.version,
  purl: `pkg:maven/${encodeURIComponent(item.group)}/${encodeURIComponent(item.name)}@${encodeURIComponent(item.version)}`,
  hashes: item.artifacts.map(artifact => ({ alg: "SHA-256", content: artifact.sha256 })),
  properties: item.artifacts.map(artifact => ({ name: "ludopaws.artifact", value: artifact.name })),
}));
const server = JSON.parse(fs.readFileSync("server/package.json", "utf8"));
components.push({ type: "application", "bom-ref": "ludopaws-worker", name: server.name, version: server.version });
const refs = new Set(components.map(item => item["bom-ref"]));
const bom = {
  bomFormat: "CycloneDX", specVersion: "1.5", version: 1,
  metadata: { component: { type: "application", "bom-ref": "ludopaws-android", name: "Ludo Paws", version: inventory.versionName },
    properties: [{ name: "ludopaws.sourceCommit", value: process.env.GITHUB_SHA ?? "local" },
      { name: "ludopaws.android.versionCode", value: String(inventory.versionCode) },
      { name: "ludopaws.inventoryScope", value: "Resolved Android release runtime artifacts; embedded native dependency internals and license review require separate analysis" },
      { name: "ludopaws.offline.entronex.sourceCommit", value: "d219148338b43e961689a7a7ab6f8ce3eb88e8de" }] },
  components,
  dependencies: [{ ref: "ludopaws-android", dependsOn: inventory.directDependencies.filter(ref => refs.has(ref)) },
    ...inventory.dependencies.filter(item => refs.has(item.ref)).map(item => ({ ref: item.ref, dependsOn: item.dependsOn.filter(ref => refs.has(ref)) }))],
};
const output = JSON.stringify(bom, null, 2) + "\n";
fs.writeFileSync("ludoproof-sbom.cdx.json", output);
fs.writeFileSync("ludoproof-sbom.sha256", createHash("sha256").update(output).digest("hex") + "  ludoproof-sbom.cdx.json\n");
console.log(`Generated resolved release SBOM with ${components.length} components for ${inventory.versionName}.`);
