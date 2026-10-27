export class StorePurchaseLedger {
  constructor(ctx, env = {}) {
    this.ctx = ctx;
    this.env = env;
    this.tail = Promise.resolve();
  }

  fetch(request) {
    const run = this.tail.then(
      () => this.#handle(request),
      () => this.#handle(request),
    );
    this.tail = run.catch(() => {});
    return run;
  }

  async #handle(request) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/status") {
      const purchase = await this.ctx.storage.get("purchase:v1");
      return json(200, { purchase: purchase ?? null });
    }
    if (request.method !== "POST") {
      return json(405, { error: "METHOD_NOT_ALLOWED" }, { allow: "GET, POST" });
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return json(400, { error: "INVALID_JSON" });
    }

    if (url.pathname === "/delete") {
      return this.#delete(body);
    }
    if (url.pathname === "/bind-owner") return this.#bindOwner(body);
    if (url.pathname === "/revoke") return this.#revoke();
    if (url.pathname === "/verified") return this.#verified(body);
    if (url.pathname === "/delivered") return this.#delivered(body);
    if (url.pathname === "/reserve") {
      return this.#reserve(body);
    }
    if (url.pathname === "/complete") {
      return this.#complete(body);
    }
    return json(404, { error: "NOT_FOUND" });
  }

  // The same serialized queue orders alarms, revocations and HTTP transitions.
  alarm() {
    const task = this.tail.then(() => this.#deliver(), () => this.#deliver());
    this.tail = task.catch(() => {});
    return task;
  }

  async #verified(body) {
    const purchase = await this.ctx.storage.get("purchase:v1");
    if (!purchase || purchase.accountId !== body?.accountId || purchase.productId !== body?.productId ||
        !isAccountId(body?.purchaseLedgerId) ||
        (purchase.purchaseLedgerId && purchase.purchaseLedgerId !== body.purchaseLedgerId)) return json(409, { error: "PURCHASE_RESERVATION_NOT_FOUND" });
    if (purchase.state === "REVOKED") return json(409, { error: "PLAY_PURCHASE_REVOKED" });
    const next = { ...purchase, verifiedAt: purchase.verifiedAt ?? Date.now(), purchaseLedgerId: body.purchaseLedgerId };
    // Arm first: a crash before put leaves a harmless alarm, never a verified
    // purchase without a retry. No raw Play token or client session is persisted.
    if (isOwner(next.ownerAccountId) && isAccountId(next.purchaseTokenHash) && !next.deliveredAt)
      await this.ctx.storage.setAlarm(Date.now() + 30_000);
    await this.ctx.storage.put("purchase:v1", next);
    return json(200, { ok: true });
  }

  async #delivered(body) {
    const purchase = await this.ctx.storage.get("purchase:v1");
    if (!purchase || purchase.accountId !== body?.accountId || purchase.productId !== body?.productId)
      return json(409, { error: "PURCHASE_RESERVATION_NOT_FOUND" });
    if (purchase.state === "REVOKED") return json(409, { error: "PLAY_PURCHASE_REVOKED" });
    if (!purchase.verifiedAt && purchase.state !== "FULFILLED") return json(409, { error: "PURCHASE_NOT_VERIFIED" });
    await this.ctx.storage.put("purchase:v1", { ...purchase, deliveredAt: purchase.deliveredAt ?? Date.now(), lastDeliveryError: null });
    await this.ctx.storage.deleteAlarm();
    return json(200, { ok: true });
  }

  async #deliver() {
    const purchase = await this.ctx.storage.get("purchase:v1");
    if (!purchase || purchase.state === "REVOKED" || purchase.deliveredAt ||
        !purchase.verifiedAt || !isOwner(purchase.ownerAccountId) || !isAccountId(purchase.purchaseLedgerId) ||
        !isAccountId(purchase.purchaseTokenHash)) return;
    const attempts = Math.min((purchase.deliveryAttempts ?? 0) + 1, 1000);
    // Schedule before remote I/O so an eviction/failure cannot lose delivery.
    await this.ctx.storage.setAlarm(Date.now() + Math.min(6 * 60 * 60_000, 30_000 * 2 ** Math.min(attempts, 10)));
    let deliveryCommitted = false;
    try {
      const accounts = this.env.LUDOPROOF_PLAYER_ACCOUNTS;
      const owner = accounts.get(accounts.idFromName(purchase.ownerAccountId));
      const response = await owner.fetch(new Request("https://player-account/wallet/play-delivery", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ accountId: purchase.ownerAccountId, productId: purchase.productId,
          purchaseTokenHash: purchase.purchaseTokenHash, purchaseLedgerId: purchase.purchaseLedgerId }),
      }));
      if (!response.ok) {
        const error = await response.json();
        if (["ACCOUNT_DELETED", "ACCOUNT_DELETION_IN_PROGRESS", "ACCOUNT_NOT_FOUND"].includes(error?.error)) {
          await this.ctx.storage.delete("purchase:v1");
          deliveryCommitted = true;
          await this.ctx.storage.deleteAlarm();
          return;
        }
        throw new Error("WALLET_DELIVERY_FAILED");
      }
      await this.ctx.storage.put("purchase:v1", { ...purchase, deliveryAttempts: attempts, deliveredAt: Date.now(), lastDeliveryError: null });
      deliveryCommitted = true;
      await this.ctx.storage.deleteAlarm();
    } catch (error) {
      // A failed timer cleanup must not resurrect deleted owner data or undo
      // a durable success marker. Alarm retry can safely observe that state.
      if (deliveryCommitted) throw error;
      await this.ctx.storage.put("purchase:v1", { ...purchase, deliveryAttempts: attempts, lastDeliveryError: "WALLET_DELIVERY_FAILED" });
      console.warn(JSON.stringify({ event: "billing-delivery-retry", attempts }));
    }
  }

  async #delete(body) {
    if (!isAccountId(body?.accountId)) return json(400, { error: "INVALID_PURCHASE" });
    const existing = await this.ctx.storage.get("purchase:v1");
    if (existing && existing.accountId !== body.accountId) {
      return json(409, { error: "PURCHASE_ACCOUNT_MISMATCH" });
    }
    if (existing) await this.ctx.storage.delete("purchase:v1");
    await this.ctx.storage.deleteAlarm();
    return json(200, { ok: true, deleted: Boolean(existing) });
  }

  async #reserve(body) {
    if (
      !isAccountId(body?.accountId) ||
      typeof body?.productId !== "string" ||
      !Number.isSafeInteger(body?.gemAmount) ||
      body.gemAmount < 0 ||
      typeof body?.removeAds !== "boolean"
    ) {
      return json(400, { error: "INVALID_PURCHASE" });
    }

    const existing = await this.ctx.storage.get("purchase:v1");
    if (existing) {
      if (
        existing.productId !== body.productId ||
        existing.accountId !== body.accountId
      ) {
        return json(409, { error: "PURCHASE_ALREADY_REDEEMED" });
      }
      return json(200, { created: false, purchase: existing });
    }

    const purchase = {
      accountId: body.accountId,
      productId: body.productId,
      gemAmount: body.gemAmount,
      removeAds: body.removeAds,
      state: "RESERVED",
      ...(isOwner(body?.ownerAccountId) && isAccountId(body?.purchaseTokenHash)
        ? { ownerAccountId: body.ownerAccountId, purchaseTokenHash: body.purchaseTokenHash } : {}),
    };
    await this.ctx.storage.put("purchase:v1", purchase);
    return json(200, { created: true, purchase });
  }

  async #bindOwner(body) {
    if (!isAccountId(body?.accountId) || !isOwner(body?.ownerAccountId) || !isAccountId(body?.purchaseTokenHash))
      return json(400, { error: "INVALID_PURCHASE" });
    const existing = await this.ctx.storage.get("purchase:v1");
    if (!existing || existing.accountId !== body.accountId ||
        (existing.ownerAccountId && existing.ownerAccountId !== body.ownerAccountId) ||
        (existing.purchaseTokenHash && existing.purchaseTokenHash !== body.purchaseTokenHash))
      return json(409, { error: "PURCHASE_ACCOUNT_MISMATCH" });
    await this.ctx.storage.put("purchase:v1", { ...existing, ownerAccountId: body.ownerAccountId,
      purchaseTokenHash: body.purchaseTokenHash });
    return json(200, { ok: true });
  }

  async #revoke() {
    const existing = await this.ctx.storage.get("purchase:v1");
    if (!existing) return json(200, { purchase: null });
    const purchase = { ...existing, state: "REVOKED" };
    await this.ctx.storage.put("purchase:v1", purchase);
    await this.ctx.storage.deleteAlarm();
    return json(200, { purchase });
  }

  async #complete(body) {
    if (
      !isAccountId(body?.accountId) ||
      typeof body?.productId !== "string"
    ) {
      return json(400, { error: "INVALID_PURCHASE" });
    }

    const existing = await this.ctx.storage.get("purchase:v1");
    if (
      !existing ||
      existing.productId !== body.productId ||
      existing.accountId !== body.accountId
    ) {
      return json(409, { error: "PURCHASE_RESERVATION_NOT_FOUND" });
    }

    if (existing.state === "REVOKED") return json(409, { error: "PLAY_PURCHASE_REVOKED" });
    const purchase = { ...existing, state: "FULFILLED" };
    await this.ctx.storage.put("purchase:v1", purchase);
    return json(200, { purchase });
  }
}

function isOwner(value) {
  return typeof value === "string" && /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i.test(value);
}

function isAccountId(value) {
  return typeof value === "string" && /^[a-f0-9]{64}$/.test(value);
}

function json(status, body, headers = {}) {
  return Response.json(body, {
    status,
    headers: {
      "cache-control": "no-store",
      ...headers,
    },
  });
}
