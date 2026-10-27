import { googlePlayFetch, googlePlayBillingEnabled } from "./google-play-billing.js";
import { sha256Hex } from "./crypto.js";

/** One serialized coordinator, with a durable continuation across cron/alarm runs.
 * Raw purchase tokens are used only in memory and are never stored or logged.
 */
export class BillingReconciliation {
  constructor(ctx, env) { this.ctx = ctx; this.env = env; this.tail = Promise.resolve(); }
  async fetch(request) {
    if (request.method === "GET" && new URL(request.url).pathname === "/status") {
      return Response.json({ lastCompletedAt: (await this.ctx.storage.get("progress:v1"))?.completedThrough ?? null,
        lastFailureAt: await this.ctx.storage.get("last-failure:v1") ?? null });
    }
    if (request.method !== "POST" || new URL(request.url).pathname !== "/run")
      return Response.json({ error: "NOT_FOUND" }, { status: 404 });
    return this.run().then(result => Response.json(result));
  }
  alarm() { return this.run(); }
  run() {
    const task = this.tail.then(() => this.reconcile()).catch(async error => {
      await this.ctx.storage.put("last-failure:v1", Date.now());
      throw error;
    });
    this.tail = task.catch(() => {});
    return task;
  }
  async reconcile(fetchImpl = fetch, now = Date.now()) {
    if (!googlePlayBillingEnabled(this.env)) return { skipped: true };
    const prior = await this.ctx.storage.get("progress:v1");
    const oldest = now - 29 * 24 * 60 * 60 * 1000;
    const window = prior?.pageToken && prior.start >= now - 30 * 24 * 60 * 60 * 1000 + 60000 ? prior : {
      start: Math.max(oldest, (prior?.completedThrough ?? oldest) - 24 * 60 * 60 * 1000), end: now,
    };
    const url = new URL("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.rekixo.ludopaws/purchases/voidedpurchases");
    url.search = new URLSearchParams({ startTime: String(window.start), endTime: String(window.end), maxResults: "10", type: "0",
      ...(window.pageToken ? { token: window.pageToken } : {}) }).toString();
    const response = await googlePlayFetch(this.env, url, {}, fetchImpl);
    if (!response.ok) throw new Error("Play voided-purchase reconciliation failed: HTTP " + response.status);
    const body = await response.json();
    if (body.voidedPurchases != null && !Array.isArray(body.voidedPurchases)) throw new Error("Invalid Play voided-purchase page");
    let revoked = 0, legacyUnbound = 0;
    for (const item of body.voidedPurchases ?? []) {
      if (typeof item?.purchaseToken !== "string" || item.purchaseToken.length < 8 || item.purchaseToken.length > 2048)
        throw new Error("Invalid Play voided-purchase token");
      const ledgerId = await sha256Hex("ludoproof:google-play-purchase:v1:" + item.purchaseToken);
      const ledger = this.env.LUDOPROOF_STORE_PURCHASES.get(this.env.LUDOPROOF_STORE_PURCHASES.idFromName(ledgerId));
      const result = await ledger.fetch(new Request("https://store-purchase/revoke", { method: "POST", body: "{}" }));
      if (!result.ok) throw new Error("Purchase revocation ledger unavailable");
      const { purchase } = await result.json();
      if (!purchase) continue; // Never granted here; do not create an account or a new ledger entry.
      if (!purchase.ownerAccountId) { legacyUnbound++; continue; }
      const owner = this.env.LUDOPROOF_PLAYER_ACCOUNTS.get(this.env.LUDOPROOF_PLAYER_ACCOUNTS.idFromName(purchase.ownerAccountId));
      const wallet = await owner.fetch(new Request("https://player-account/wallet/play-revoke", { method: "POST",
        headers: { "content-type": "application/json" }, body: JSON.stringify({ accountId: purchase.ownerAccountId, purchaseLedgerId: ledgerId }) }));
      if (!wallet.ok) {
        const error = await wallet.json();
        if (!["ACCOUNT_DELETED", "ACCOUNT_DELETION_IN_PROGRESS", "ACCOUNT_NOT_FOUND"].includes(error?.error))
          throw new Error("Purchase revocation wallet unavailable");
      }
      revoked++;
    }
    const next = body.tokenPagination?.nextPageToken;
    if (next != null && (typeof next !== "string" || next.length > 4096)) throw new Error("Invalid Play continuation token");
    await this.ctx.storage.put("progress:v1", next ? { ...window, pageToken: next } : { completedThrough: window.end });
    if (next) await this.ctx.storage.setAlarm(Date.now() + 30000);
    else await this.ctx.storage.delete("last-failure:v1");
    // Counts only: no player IDs, purchase tokens or credentials in operational logs.
    console.log(JSON.stringify({ event: "billing-reconciliation", revoked, legacyUnbound, continued: Boolean(next) }));
    return { revoked, legacyUnbound, continued: Boolean(next) };
  }
}
