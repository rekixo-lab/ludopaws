const FRIEND_ID = /^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/;
const MATCH_ID = /^LP[A-Z2-9]{8}$/;
const REQUEST_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const CONVERSATION_KEY = "conversation:v1";
const RECENT_KEY = "recent:v1";
const MAX_MESSAGES = 200;
const MAX_LIST_MESSAGES = 50;
const MAX_RECENT = 30;
const MAX_MESSAGE_CHARS = 240;

export class FriendConversation {
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
    try {
      const url = new URL(request.url);
      if (request.method !== "POST") {
        return json(405, { error: "METHOD_NOT_ALLOWED", message: "method not allowed" }, { allow: "POST" });
      }
      const body = await readJson(request);
      if (url.pathname === "/send") return await this.#send(body);
      if (url.pathname === "/list") return await this.#list(body);
      // Internal-only; no public Worker route to this evidence lookup.
      if (url.pathname === "/moderation/lookup") return await this.#moderatorLookup(body);
      if (url.pathname === "/delete-for-friend") return await this.#deleteForFriend(body);
      return json(404, { error: "NOT_FOUND", message: "route not found" });
    } catch (error) {
      return errorResponse(error);
    }
  }

  async #moderatorLookup(body) {
    const senderId = normalizeFriendId(body?.senderId);
    const receiverId = normalizeFriendId(body?.receiverId);
    const messageId = String(body?.messageId ?? "").toLowerCase();
    if (senderId === receiverId || !REQUEST_ID.test(messageId)) {
      throw failure(400, "REPORTED_MESSAGE_INVALID", "invalid message evidence request");
    }
    const state = await this.ctx.storage.get(CONVERSATION_KEY);
    const pair = canonicalPair(senderId, receiverId);
    if (!state || !Array.isArray(state.pair) || state.pair[0] !== pair[0] || state.pair[1] !== pair[1]) {
      return json(404, { error: "REPORTED_MESSAGE_NOT_FOUND" });
    }
    const message = (state.messages ?? []).find((entry) =>
      entry.messageId === messageId && entry.senderId === senderId && entry.receiverId === receiverId,
    );
    if (!message) return json(404, { error: "REPORTED_MESSAGE_NOT_FOUND" });
    return json(200, { ok: true, evidence: {
      messageId: message.messageId,
      text: message.text,
      createdAt: message.createdAt,
      senderId, receiverId,
    } });
  }

  async #deleteForFriend(body) {
    const friendId = normalizeFriendId(body?.friendId), existing = await this.ctx.storage.get(CONVERSATION_KEY);
    if (!existing) return json(200, { ok: true, deleted: false });
    if (!Array.isArray(existing.pair) || !existing.pair.includes(friendId)) throw failure(403, "CONVERSATION_OWNER_MISMATCH", "deletion target is not part of this conversation");
    await this.ctx.storage.delete(CONVERSATION_KEY);
    return json(200, { ok: true, deleted: true });
  }

  async #send(body) {
    const senderId = normalizeFriendId(body?.senderId);
    const receiverId = normalizeFriendId(body?.receiverId);
    if (senderId === receiverId) throw failure(400, "INVALID_CONVERSATION", "cannot message yourself");
    const clientRequestId = String(body?.clientRequestId ?? "").toLowerCase();
    if (!REQUEST_ID.test(clientRequestId)) {
      throw failure(400, "INVALID_REQUEST_ID", "clientRequestId must be a UUID v4");
    }
    const text = normalizeMessage(body?.text);
    const state = await this.#stateFor(senderId, receiverId);
    const existing = state.messages.find((message) => message.messageId === clientRequestId);
    if (existing) {
      if (existing.senderId !== senderId || existing.receiverId !== receiverId || existing.text !== text) {
        throw failure(409, "MESSAGE_IDEMPOTENCY_CONFLICT", "message request ID was already used for different data");
      }
      return json(200, { ok: true, replayed: true, message: existing });
    }
    const message = {
      messageId: clientRequestId,
      senderId,
      receiverId,
      text,
      createdAt: Date.now(),
      readAt: null,
    };
    state.messages.push(message);
    if (state.messages.length > MAX_MESSAGES) {
      state.messages = state.messages.slice(-MAX_MESSAGES);
    }
    await this.ctx.storage.put(CONVERSATION_KEY, state);
    return json(201, { ok: true, replayed: false, message });
  }

  async #list(body) {
    const meId = normalizeFriendId(body?.meId);
    const otherId = normalizeFriendId(body?.otherId);
    if (meId === otherId) throw failure(400, "INVALID_CONVERSATION", "cannot open a conversation with yourself");
    const state = await this.#stateFor(meId, otherId);
    const now = Date.now();
    let changed = false;
    for (const message of state.messages) {
      if (message.receiverId === meId && message.readAt == null) {
        message.readAt = now;
        changed = true;
      }
    }
    if (changed) await this.ctx.storage.put(CONVERSATION_KEY, state);
    return json(200, {
      ok: true,
      friendId: otherId,
      messages: state.messages.slice(-MAX_LIST_MESSAGES),
    });
  }

  async #stateFor(firstId, secondId) {
    const pair = canonicalPair(firstId, secondId);
    const existing = await this.ctx.storage.get(CONVERSATION_KEY);
    if (!existing) {
      const created = { pair, messages: [] };
      await this.ctx.storage.put(CONVERSATION_KEY, created);
      return created;
    }
    if (!Array.isArray(existing.pair) || existing.pair[0] !== pair[0] || existing.pair[1] !== pair[1]) {
      throw failure(409, "CONVERSATION_PAIR_CONFLICT", "conversation storage is bound to a different friend pair");
    }
    if (!Array.isArray(existing.messages)) existing.messages = [];
    return existing;
  }
}

export class FriendRecentRoom {
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
    try {
      const url = new URL(request.url);
      if (request.method !== "POST") {
        return json(405, { error: "METHOD_NOT_ALLOWED", message: "method not allowed" }, { allow: "POST" });
      }
      const body = await readJson(request);
      if (url.pathname === "/record") return await this.#record(body);
      if (url.pathname === "/list") return await this.#list(body);
      if (url.pathname === "/delete-owner") return await this.#deleteOwner(body);
      if (url.pathname === "/purge-opponent") return await this.#purgeOpponent(body);
      return json(404, { error: "NOT_FOUND", message: "route not found" });
    } catch (error) {
      return errorResponse(error);
    }
  }

  async #deleteOwner(body) {
    const ownerFriendId = normalizeFriendId(body?.ownerFriendId), existing = await this.ctx.storage.get(RECENT_KEY);
    if (!existing) return json(200, { ok: true, deleted: false });
    if (existing.ownerFriendId !== ownerFriendId) throw failure(409, "RECENT_OWNER_CONFLICT", "recent-player storage is bound to another Friend ID");
    await this.ctx.storage.delete(RECENT_KEY);
    return json(200, { ok: true, deleted: true });
  }

  async #purgeOpponent(body) {
    const ownerFriendId = normalizeFriendId(body?.ownerFriendId), opponentFriendId = normalizeFriendId(body?.opponentFriendId);
    const existing = await this.ctx.storage.get(RECENT_KEY);
    if (!existing) return json(200, { ok: true, removed: 0 });
    if (existing.ownerFriendId !== ownerFriendId) throw failure(409, "RECENT_OWNER_CONFLICT", "recent-player storage is bound to another Friend ID");
    const before = Array.isArray(existing.entries) ? existing.entries.length : 0;
    existing.entries = (Array.isArray(existing.entries) ? existing.entries : []).filter((entry) => String(entry?.opponentFriendId ?? "").toUpperCase() !== opponentFriendId);
    const removed = before - existing.entries.length;
    if (!existing.entries.length) await this.ctx.storage.delete(RECENT_KEY);
    else if (removed) await this.ctx.storage.put(RECENT_KEY, existing);
    return json(200, { ok: true, removed });
  }

  async #record(body) {
    const ownerFriendId = normalizeFriendId(body?.ownerFriendId);
    const opponentFriendId = normalizeFriendId(body?.opponentFriendId);
    if (ownerFriendId === opponentFriendId) {
      throw failure(400, "INVALID_RECENT_PLAYER", "recent opponent must be another player");
    }
    const displayName = normalizeDisplayName(body?.displayName);
    const matchId = String(body?.matchId ?? "").toUpperCase();
    if (!MATCH_ID.test(matchId)) throw failure(400, "INVALID_MATCH_ID", "invalid match ID");
    const playedAt = Number(body?.playedAt);
    if (!Number.isSafeInteger(playedAt) || playedAt <= 0) {
      throw failure(400, "INVALID_PLAYED_AT", "playedAt must be a positive integer timestamp");
    }
    const state = await this.#stateFor(ownerFriendId);
    const next = state.entries.filter((entry) => entry.opponentFriendId !== opponentFriendId);
    next.unshift({ opponentFriendId, displayName, matchId, playedAt });
    state.entries = next
      .sort((a, b) => b.playedAt - a.playedAt)
      .slice(0, MAX_RECENT);
    await this.ctx.storage.put(RECENT_KEY, state);
    return json(200, { ok: true });
  }

  async #list(body) {
    const ownerFriendId = normalizeFriendId(body?.ownerFriendId);
    const state = await this.#stateFor(ownerFriendId);
    return json(200, { ok: true, recent: state.entries.slice(0, MAX_RECENT) });
  }

  async #stateFor(ownerFriendId) {
    const existing = await this.ctx.storage.get(RECENT_KEY);
    if (!existing) {
      const created = { ownerFriendId, entries: [] };
      await this.ctx.storage.put(RECENT_KEY, created);
      return created;
    }
    if (existing.ownerFriendId !== ownerFriendId) {
      throw failure(409, "RECENT_OWNER_CONFLICT", "recent-player storage is bound to another Friend ID");
    }
    if (!Array.isArray(existing.entries)) existing.entries = [];
    return existing;
  }
}

export async function syncRecentFriends(env, state) {
  if (state?.status !== "FINISHED" || state?.matchMode !== "FRIENDS") return;
  if (!env?.LUDOPROOF_FRIEND_RECENT) return;
  const players = (state.players ?? []).filter((player) => FRIEND_ID.test(String(player?.friendId ?? "")));
  if (players.length < 2) return;
  const playedAt = Number(state.finishedAt ?? state.updatedAt ?? state.createdAt ?? Date.now());
  const tasks = [];
  for (const owner of players) {
    for (const opponent of players) {
      if (owner.friendId === opponent.friendId) continue;
      const id = env.LUDOPROOF_FRIEND_RECENT.idFromName(owner.friendId);
      const target = env.LUDOPROOF_FRIEND_RECENT.get(id);
      tasks.push(
        target.fetch(
          new Request("https://recent/record", {
            method: "POST", headers: { "content-type": "application/json" },
            body: JSON.stringify({ ownerFriendId: owner.friendId, opponentFriendId: opponent.friendId, displayName: opponent.displayName, matchId: state.matchId, playedAt }),
          }),
        ),
      );
      if (env.LUDOPROOF_FRIENDS) {
        const friends = env.LUDOPROOF_FRIENDS, directory = friends.get(friends.idFromName("GLOBAL:V1"));
        tasks.push(directory.fetch(new Request("https://friends/recent/register", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ ownerFriendId: owner.friendId, opponentFriendId: opponent.friendId }) })));
      }
    }
  }
  await Promise.all(tasks);
}

function canonicalPair(firstId, secondId) {
  return [firstId, secondId].sort();
}

function normalizeFriendId(value) {
  const normalized = String(value ?? "").trim().toUpperCase();
  if (!FRIEND_ID.test(normalized)) throw failure(400, "INVALID_FRIEND_ID", "invalid Friend ID");
  return normalized;
}

function normalizeDisplayName(value) {
  const text = String(value ?? "").trim().replace(/\s+/g, " ");
  if (text.length < 1 || text.length > 32) throw failure(400, "INVALID_DISPLAY_NAME", "display name must be 1 to 32 characters");
  return text;
}

function normalizeMessage(value) {
  const text = String(value ?? "").trim();
  if (text.length < 1 || text.length > MAX_MESSAGE_CHARS) {
    throw failure(400, "INVALID_MESSAGE", `message must be 1 to ${MAX_MESSAGE_CHARS} characters`);
  }
  if (new TextEncoder().encode(text).byteLength > 1024) {
    throw failure(400, "INVALID_MESSAGE", "message is too large");
  }
  return text;
}

async function readJson(request) {
  const type = request.headers.get("content-type") ?? "";
  if (!type.toLowerCase().startsWith("application/json")) {
    throw failure(415, "UNSUPPORTED_MEDIA_TYPE", "content-type must be application/json");
  }
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > 8 * 1024) {
    throw failure(413, "REQUEST_TOO_LARGE", "request body is too large");
  }
  let value;
  try {
    value = text.length === 0 ? {} : JSON.parse(text);
  } catch {
    throw failure(400, "INVALID_JSON", "request body must be valid JSON");
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw failure(400, "INVALID_JSON", "request body must be a JSON object");
  }
  return value;
}

function failure(status, code, message) {
  const error = new Error(message);
  error.status = status;
  error.code = code;
  return error;
}

function errorResponse(error) {
  return json(
    Number.isInteger(error?.status) ? error.status : 500,
    {
      error: error?.code ?? "INTERNAL_ERROR",
      message: error?.code ? String(error.message) : "internal server error",
    },
  );
}

function json(status, body, extra = null) {
  const headers = new Headers({
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
  });
  if (extra) {
    for (const [name, value] of Object.entries(extra)) headers.set(name, value);
  }
  return new Response(JSON.stringify(body), { status, headers });
}
