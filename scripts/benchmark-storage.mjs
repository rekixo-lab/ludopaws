import { DatabaseSync } from "node:sqlite";
import { performance } from "node:perf_hooks";
import { AccountDirectory } from "../server/src/account-directory.js";

// Synthetic, in-memory SQLite only. No network, credentials, player accounts,
// deployed Durable Objects or persistent databases are opened by this script.
const rows = Number(process.argv[2] ?? 10000);
if (!Number.isSafeInteger(rows) || rows < 1000 || rows > 100000 || process.argv.length > 3)
  throw Error("Usage: node scripts/benchmark-storage.mjs [rows: 1000..100000]");
const db = new DatabaseSync(":memory:");
const ctx = { storage: { sql: { exec: (query, ...args) => {
  const statement = db.prepare(query);
  return statement.columns().length ? statement.all(...args) : (statement.run(...args), []);
} } }, blockConcurrencyWhile: action => action() };
new AccountDirectory(ctx, {});
const insert = db.prepare("INSERT INTO google_nonces (nonce_hash, created_at, expires_at) VALUES (?, ?, ?)");
db.exec("BEGIN");
for (let i = 0; i < rows; i++) insert.run("synthetic-" + i, i, i % 10 === 0 ? 1 : 1000000);
db.exec("COMMIT");
const query = "SELECT COUNT(*) AS total FROM google_nonces WHERE expires_at < 100";
function measure() {
  const statement = db.prepare(query);
  for (let i = 0; i < 10; i++) statement.get();
  const times = [];
  for (let i = 0; i < 200; i++) {
    const start = performance.now(); statement.get(); times.push(performance.now() - start);
  }
  times.sort((a, b) => a - b);
  return { samples: times.length, medianMs: times[100], p95Ms: times[190],
    queryPlan: db.prepare("EXPLAIN QUERY PLAN " + query).all().map(row => row.detail) };
}
db.exec("DROP INDEX idx_google_nonces_expiry");
const before = measure();
new AccountDirectory(ctx, {});
const after = measure();
console.log(JSON.stringify({ scope: "Synthetic in-memory SQL microbenchmark; not deployed Worker capacity or device performance", rows, before, after }, null, 2));
db.close();
