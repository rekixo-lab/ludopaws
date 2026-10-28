import { familyProjection } from "./family-safety.js";
import {
  MatchRoom as SocialMatchRoom,
} from "./match-room-social.js";
import {
  actingSeatForTurn,
  forfeitTimedOutTurn,
  isTeamUp,
  publicState,
  rulesetForState,
} from "./game.js";
import { compactRollHistory } from "./match-events.js";

const STATE_KEY = "match-state";
const ACTIVE_IDLE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const FINISHED_IDLE_TTL_MS = 24 * 60 * 60 * 1000;

// A resolved roll must not let a modified/disconnected client hold the room
// indefinitely. Strategy stays client-controlled inside this window; once it
// expires the move is forfeited and the authoritative turn advances.
export const MOVE_SELECTION_TIMEOUT_MS = 45 * 1000;
export const TURN_ROLL_TIMEOUT_MS = 15 * 1000;
export const MAX_TURN_ROLL_TIMEOUTS = 4;

export class MatchRoom extends SocialMatchRoom {
  async fetch(request) {
    await this.#enqueue(
      () => this.#expireTurnRollIfNeeded(),
    );
    await this.#enqueue(
      () => this.#expireResolvedMoveIfNeeded(),
    );

    const response = await super.fetch(request);

    await this.#enqueue(
      () => this.#scheduleNextDeadline(),
    );
    return response;
  }

  async alarm() {
    const referralState = await this.ctx.storage.get(STATE_KEY);
    await this.recordReferralResult(referralState);
    const turnExpired = await this.#enqueue(
      () => this.#expireTurnRollIfNeeded(),
    );
    if (turnExpired) {
      await this.#enqueue(() => this.#scheduleNextDeadline());
      return;
    }

    const moveExpired = await this.#enqueue(
      () => this.#expireResolvedMoveIfNeeded(),
    );
    if (moveExpired) {
      await this.#enqueue(() => this.#scheduleNextDeadline());
      return;
    }

    await super.alarm();

    await this.#enqueue(
      () => this.#scheduleNextDeadline(),
    );
  }

  #enqueue(work) {
    const run = this.mutationTail.then(work, work);
    this.mutationTail = run.catch(() => {});
    return run;
  }

  async #expireTurnRollIfNeeded() {
    const state = await this.ctx.storage.get(STATE_KEY);
    if (!state || state.status !== "ACTIVE" || state.pendingRoll != null) {
      return false;
    }

    // Inactivity handling is opt-in per ruleset. Existing matches retain their
    // original turn contract; V4 uses the deadline persisted by the game core.
    const timeoutMs = rulesetForState(state).turnRollTimeoutMs;
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) {
      return false;
    }

    const now = Date.now();
    const actingSeat = actingSeatForTurn(state);
    if (
      !Number.isInteger(actingSeat) ||
      actingSeat < 0 ||
      actingSeat >= state.players.length
    ) {
      return false;
    }

    // If persisted V4 timer metadata is missing, derive it from the last
    // authoritative turn-state write instead of restarting it on reconnect.
    let deadlineState = state;
    if (
      !Number.isSafeInteger(state.turnDeadlineAt) ||
      state.turnDeadlineSeat !== actingSeat
    ) {
      deadlineState = structuredClone(state);
      const turnStartedAt = Number(state.updatedAt ?? state.createdAt ?? now);
      const safeTurnStartedAt = Number.isSafeInteger(turnStartedAt) ? turnStartedAt : now;
      deadlineState.turnDeadlineAt = safeTurnStartedAt + timeoutMs;
      deadlineState.turnDeadlineSeat = actingSeat;
      if (now < deadlineState.turnDeadlineAt) {
        deadlineState.revision =
          (Number.isSafeInteger(deadlineState.revision) ? deadlineState.revision : 0) + 1;
        await this.ctx.storage.put(STATE_KEY, deadlineState);
        broadcastStateChanged(this.ctx, deadlineState, this.env);
        return false;
      }
    }

    if (now < deadlineState.turnDeadlineAt) return false;

    const result = forfeitTimedOutTurn(deadlineState, { now });
    await this.ctx.storage.put(STATE_KEY, result.state);
    broadcastStateChanged(this.ctx, result.state, this.env);
    if (result.state.status === "FINISHED") {
      await this.recordFinishedMatchForLifecycle(result.state);
    }
    return true;
  }

  async #scheduleNextDeadline() {
    if (typeof this.ctx.storage.setAlarm !== "function") return false;
    const state = await this.ctx.storage.get(STATE_KEY);
    if (!state) { await this.scheduleReferralRetry(); return false; }
    if (state.status !== "ACTIVE") {
      const ttl = state.status === "FINISHED" ? FINISHED_IDLE_TTL_MS : ACTIVE_IDLE_TTL_MS;
      const updatedAt = Number(state.updatedAt ?? state.createdAt ?? Date.now());
      const retryAt = state.status === "FINISHED" ? await this.scheduleReferralRetry() : null;
      await this.ctx.storage.setAlarm(retryAt != null ? Math.min(updatedAt + ttl, retryAt) : updatedAt + ttl);
      return false;
    }
    if (state.pendingRoll?.status === "RESOLVED") {
      return this.#scheduleResolvedMoveDeadline();
    }
    if (state.pendingRoll != null) return false;

    const timeoutMs = rulesetForState(state).turnRollTimeoutMs;
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) {
      const updatedAt = Number(state.updatedAt ?? state.createdAt ?? Date.now());
      if (!Number.isSafeInteger(updatedAt)) return false;
      await this.ctx.storage.setAlarm(updatedAt + ACTIVE_IDLE_TTL_MS);
      return true;
    }

    const deadline = state.turnDeadlineAt;
    if (!Number.isSafeInteger(deadline)) {
      await this.#expireTurnRollIfNeeded();
      const refreshed = await this.ctx.storage.get(STATE_KEY);
      if (!Number.isSafeInteger(refreshed?.turnDeadlineAt)) return false;
      await this.ctx.storage.setAlarm(refreshed.turnDeadlineAt);
      return true;
    }
    if (deadline <= Date.now()) {
      const expired = await this.#expireTurnRollIfNeeded();
      if (expired) {
        const refreshed = await this.ctx.storage.get(STATE_KEY);
        const nextDeadline = refreshed?.turnDeadlineAt;
        if (Number.isSafeInteger(nextDeadline)) {
          await this.ctx.storage.setAlarm(nextDeadline);
          return true;
        }
      }
      return false;
    }
    await this.ctx.storage.setAlarm(deadline);
    return true;
  }

  async #expireResolvedMoveIfNeeded() {
    const state = await this.ctx.storage.get(STATE_KEY);
    const pending = state?.pendingRoll;
    if (
      !state ||
      state.status !== "ACTIVE" ||
      pending?.status !== "RESOLVED"
    ) {
      return false;
    }

    const deadline = resolvedMoveDeadline(pending);
    const now = Date.now();
    if (deadline == null || now < deadline) {
      return false;
    }

    const next = structuredClone(state);
    const seat = next.pendingRoll.seat;
    if (
      Array.isArray(next.consecutiveSixes) &&
      Number.isInteger(seat) &&
      seat >= 0 &&
      seat < next.consecutiveSixes.length
    ) {
      next.consecutiveSixes[seat] = 0;
    }

    const history = Array.isArray(next.history)
      ? [...next.history]
      : [];
    const historyIndex = history.findLastIndex(
      (event) =>
        event?.eventIndex === next.pendingRoll.eventIndex &&
        event?.playerId === next.pendingRoll.playerId,
    );
    if (historyIndex >= 0) {
      history[historyIndex] = {
        ...history[historyIndex],
        moveTimedOutAt: now,
        moveTimeoutReason: "MOVE_SELECTION_TIMEOUT",
      };
      next.history = history;
    }

    next.pendingRoll = null;
    next.turnSeat = nextActiveSeat(
      next,
      Number.isInteger(next.turnSeat)
        ? next.turnSeat
        : seat,
    );
    const nextTimeoutMs = rulesetForState(next).turnRollTimeoutMs;
    const nextActingSeat = actingSeatForTurn(next);
    if (
      Number.isSafeInteger(nextTimeoutMs) &&
      nextTimeoutMs > 0 &&
      Number.isInteger(nextActingSeat) &&
      nextActingSeat >= 0 &&
      nextActingSeat < next.players.length
    ) {
      next.turnDeadlineSeat = nextActingSeat;
      next.turnDeadlineAt = now + nextTimeoutMs;
    } else {
      next.turnDeadlineSeat = null;
      next.turnDeadlineAt = null;
    }
    next.updatedAt = now;
    next.revision = Number(next.revision ?? 0) + 1;

    await this.ctx.storage.put(STATE_KEY, next);
    if (typeof this.ctx.storage.setAlarm === "function") {
      await this.ctx.storage.setAlarm(now + ACTIVE_IDLE_TTL_MS);
    }
    broadcastStateChanged(this.ctx, next, this.env);
    return true;
  }

  async #scheduleResolvedMoveDeadline() {
    if (typeof this.ctx.storage.setAlarm !== "function") {
      return false;
    }

    const state = await this.ctx.storage.get(STATE_KEY);
    const pending = state?.pendingRoll;
    if (
      !state ||
      state.status !== "ACTIVE" ||
      pending?.status !== "RESOLVED"
    ) {
      return false;
    }

    const deadline = resolvedMoveDeadline(pending);
    if (deadline == null) {
      return false;
    }
    if (deadline <= Date.now()) {
      return this.#expireResolvedMoveIfNeeded();
    }

    await this.ctx.storage.setAlarm(deadline);
    return true;
  }
}

function resolvedMoveDeadline(pending) {
  const resolvedAt = pending?.resolvedAt;
  if (!Number.isSafeInteger(resolvedAt) || resolvedAt <= 0) {
    return null;
  }
  return resolvedAt + MOVE_SELECTION_TIMEOUT_MS;
}

function nextActiveSeat(state, currentSeat) {
  const count = Array.isArray(state?.players)
    ? state.players.length
    : 0;
  if (count < 1) return currentSeat;

  const start = Number.isInteger(currentSeat)
    ? currentSeat
    : -1;
  for (let offset = 1; offset <= count; offset += 1) {
    const candidate = ((start + offset) % count + count) % count;
    const player = state.players[candidate];
    if (!Number.isSafeInteger(player?.forfeitedAt) &&
      !(state.finishOrderPlayerIds ?? []).includes(player?.playerId)) {
      return candidate;
    }
  }
  return start >= 0 && start < count ? start : 0;
}

function broadcastStateChanged(ctx, state, env) {
  if (typeof ctx.getWebSockets !== "function") return;
  const payload = JSON.stringify({
    type: "STATE_CHANGED",
    matchId: state.matchId,
    revision: state.revision,
    status: state.status,
    playerCount: state.players?.length ?? 0,
    state: familyProjection({
      ...publicState(state),
      ...compactRollHistory(state),
    }, env),
  });
  for (const socket of ctx.getWebSockets()) {
    try {
      socket.send(payload);
    } catch {
      try {
        socket.close(1011, "state sync failed");
      } catch {
        // Socket is already gone.
      }
    }
  }
}
