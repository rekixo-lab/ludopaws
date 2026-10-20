export class StorePurchaseLedger {
  constructor(ctx) {
    this.ctx = ctx;
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
    if (url.pathname === "/reserve") {
      return this.#reserve(body);
    }
    if (url.pathname === "/complete") {
      return this.#complete(body);
    }
    return json(404, { error: "NOT_FOUND" });
  }

  async #delete(body) {
    if (!isAccountId(body?.accountId)) return json(400, { error: "INVALID_PURCHASE" });
    const existing = await this.ctx.storage.get("purchase:v1");
    if (existing && existing.accountId !== body.accountId) {
      return json(409, { error: "PURCHASE_ACCOUNT_MISMATCH" });
    }
    if (existing) await this.ctx.storage.delete("purchase:v1");
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
        (existing.ownerAccountId && existing.ownerAccountId !== body.ownerAccountId))
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
