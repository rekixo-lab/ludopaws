import test from "node:test";
import assert from "node:assert/strict";
import { scanSbom } from "./check-dependency-advisories.mjs";
const sbom = { bomFormat: "CycloneDX", components: [
  { type: "library", purl: "pkg:maven/test.group/library@1.0" },
  { type: "library", purl: "pkg:maven/test.group/other@2.0" },
  { type: "application", name: "private-application-not-sent" },
] };
test("advisory scan follows per-package pagination and deduplicates findings", async () => {
  let calls = 0;
  const report = await scanSbom(sbom, async (url, init) => {
    assert.equal(url, "https://api.osv.dev/v1/querybatch");
    assert.equal(init.body.includes("private-application"), false);
    const body = JSON.parse(init.body);
    calls++;
    if (calls === 1) return Response.json({ results: [{ vulns: [{ id: "GHSA-test-1" }], next_page_token: "next" }, {}] });
    assert.equal(body.queries.length, 1);
    assert.equal(body.queries[0].page_token, "next");
    return Response.json({ results: [{ vulns: [{ id: "GHSA-test-1" }, { id: "CVE-2026-1234" }] }] });
  });
  assert.equal(calls, 2); assert.equal(report.findings.length, 2); assert.equal(report.packagesChecked, 2);
});
test("missing packages, service failure and incomplete results cannot pass", async () => {
  await assert.rejects(scanSbom({ ...sbom, components: [] }), /Expected/);
  await assert.rejects(scanSbom(sbom, async () => new Response(null, { status: 503 })), /unavailable/);
  await assert.rejects(scanSbom(sbom, async () => Response.json({ results: [{}] })), /Incomplete/);
});
