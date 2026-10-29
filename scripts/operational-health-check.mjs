const response = await fetch("https://api.ludopaws.rekixo.com/health/operations", {
  signal: AbortSignal.timeout(30_000), redirect: "error", cache: "no-store",
});
const body = await response.json();
const failures = Array.isArray(body.failures) && body.failures.every(code => /^[A-Z_]{1,80}$/.test(code))
  ? body.failures : ["INVALID_HEALTH_RESPONSE"];
console.log(JSON.stringify({ ok: response.ok && body.ok === true && failures.length === 0, failures }));
if (!response.ok || body.ok !== true || failures.length) process.exitCode = 1;
