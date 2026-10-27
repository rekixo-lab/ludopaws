import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const required = ["server", "android", "windows-runner-script", "android-instrumentation"];
export function assertCompleteCiJobSet(jobs) {
  for (const name of required) {
    if (!jobs.some(job => job.name === name && job.status === "completed" && job.conclusion === "success"))
      throw Error("Exact release commit requires successful CI job: " + name);
  }
}
async function main() {
  const repository = process.env.GITHUB_REPOSITORY;
  const commit = process.env.GITHUB_SHA;
  const token = process.env.GH_TOKEN;
  if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository ?? "") || !/^[a-f0-9]{40}$/.test(commit ?? "") || !token)
    throw Error("GitHub release CI context is missing");
  async function get(path) {
    const response = await fetch("https://api.github.com/repos/" + repository + path, {
      headers: { authorization: "Bearer " + token, accept: "application/vnd.github+json", "x-github-api-version": "2022-11-28" },
      signal: AbortSignal.timeout(20_000), redirect: "error",
    });
    if (!response.ok) throw Error("Cannot verify release CI: HTTP " + response.status);
    return response.json();
  }
  const runs = await get("/actions/workflows/ci.yml/runs?per_page=100&head_sha=" + commit);
  for (const run of runs.workflow_runs ?? []) {
    if (run.head_sha !== commit || run.conclusion !== "success") continue;
    const result = await get("/actions/runs/" + run.id + "/jobs?per_page=100");
    try { assertCompleteCiJobSet(result.jobs ?? []); }
    catch { continue; }
    console.log("Exact release commit passed complete CI including isolated emulator instrumentation; run " + run.id);
    return;
  }
  throw Error("Run Ludo Paws CI for this exact commit with run_android_instrumentation=true before signing. No physical-device acceptance is implied.");
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
