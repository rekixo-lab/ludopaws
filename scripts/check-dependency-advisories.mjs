import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

/** Sends only public Maven coordinates, never application sources or secrets. */
export async function scanSbom(sbom, fetchImpl = fetch) {
  if (sbom?.bomFormat !== "CycloneDX" || !Array.isArray(sbom.components)) throw Error("Invalid CycloneDX SBOM");
  const packages = [...new Set(sbom.components.filter(c => c.type === "library").map(c => c.purl))];
  if (!packages.length || packages.some(p => typeof p !== "string" || !/^pkg:maven\/[^\s]+@[^\s]+$/.test(p)))
    throw Error("Expected versioned public Maven library coordinates");
  const findings = [];
  for (let offset = 0; offset < packages.length; offset += 100) {
    let pending = packages.slice(offset, offset + 100).map(purl => ({ package: { purl } }));
    for (let page = 0; pending.length; page++) {
      if (page >= 20) throw Error("OSV pagination exceeded safety bound");
      const response = await fetchImpl("https://api.osv.dev/v1/querybatch", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ queries: pending }), signal: AbortSignal.timeout(30_000), redirect: "error",
      });
      if (!response.ok) throw Error("OSV advisory service unavailable: HTTP " + response.status);
      const body = await response.json();
      if (!Array.isArray(body.results) || body.results.length !== pending.length) throw Error("Incomplete OSV results");
      const next = [];
      body.results.forEach((result, index) => {
        if (!result || typeof result !== "object" || (result.vulns != null && !Array.isArray(result.vulns))) throw Error("Invalid OSV result");
        for (const vuln of result.vulns ?? []) {
          if (!/^[A-Za-z0-9_.-]{1,160}$/.test(vuln?.id ?? "")) throw Error("Invalid advisory identifier");
          findings.push({ package: pending[index].package.purl, advisory: vuln.id });
        }
        if (result.next_page_token != null) {
          if (typeof result.next_page_token !== "string" || !result.next_page_token || result.next_page_token.length > 16384)
            throw Error("Invalid OSV pagination");
          next.push({ package: pending[index].package, page_token: result.next_page_token });
        }
      });
      pending = next;
    }
  }
  return { source: "OSV", checkedAt: new Date().toISOString(), packagesChecked: packages.length,
    findings: [...new Map(findings.map(f => [f.package + ":" + f.advisory, f])).values()],
    scope: "Resolved Android runtime Maven artifacts; no claim about unindexed vulnerabilities or embedded SDK internals" };
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length > 1) throw Error("Usage: node scripts/check-dependency-advisories.mjs [CycloneDX.json]");
  const report = await scanSbom(JSON.parse(await readFile(args[0] ?? "ludoproof-sbom.cdx.json", "utf8")));
  await writeFile("dependency-advisories.json", JSON.stringify(report, null, 2) + "\n");
  console.log(`OSV checked ${report.packagesChecked} resolved packages; ${report.findings.length} advisory matches.`);
  if (report.findings.length) {
    for (const finding of report.findings) console.error(finding.package + " " + finding.advisory);
    process.exitCode = 1;
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
