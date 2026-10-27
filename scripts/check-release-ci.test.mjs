import test from "node:test";
import assert from "node:assert/strict";
import { assertCompleteCiJobSet } from "./check-release-ci.mjs";
test("a release cannot substitute skipped emulator checks or incomplete jobs", () => {
  const names = ["server", "android", "windows-runner-script", "android-instrumentation"];
  const jobs = names.map(name => ({ name, status: "completed", conclusion: "success" }));
  assert.doesNotThrow(() => assertCompleteCiJobSet(jobs));
  assert.throws(() => assertCompleteCiJobSet(jobs.slice(0, 3)), /android-instrumentation/);
  assert.throws(() => assertCompleteCiJobSet(jobs.map(job => job.name === "android-instrumentation" ? { ...job, conclusion: "skipped" } : job)), /android-instrumentation/);
});
