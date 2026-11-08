import { familyProjection } from "./family-safety.js";
import {
  MatchRoom as SocialMatchRoom,
} from "./match-room-social.js";
import {
  actingSeatForTurn,
  applyMove,
  destinationForTokenRoll,
  legalTokenIndexes,
  forfeitTimedOutTurn,
  isTeamUp,
  publicState,
  rulesetForState,
} from "./game.js";
import { MOVE_SELECTION_TIMEOUT_MS } from "./rulesets.js";
import { compactRollHistory } from "./match-events.js";

const STATE_KEY = "match-state";
const ACTIVE_IDLE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const FINISHED_IDLE_TTL_MS = 24 * 60 * 60 * 1000;

// A resolved roll must not let a modified/disconnected client hold the room
// indefinitely. Strategy stays client-controlled inside this window; once it
// expires the move is forfeited and the authoritative turn advances.
export { MOVE_SELECTION_TIMEOUT_MS };
// After the client has time to render the proven dice, recover a truly forced
// move at the authoritative server. Manual choices retain the full 45 seconds.
export const FORCED_MOVE_RECOVERY_MS = 8 * 1000;
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
    await this.recordMatchGemResult();
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
    // Settlement may persist a newer version: broadcast that actual state.
    const finalState = result.state.status === "FINISHED"
      ? await this.recordFinishedMatchForLifecycle(result.state)
      : result.state;
    broadcastStateChanged(this.ctx, finalState, this.env);
    return true;
  }

  async #scheduleNextDeadline() {
    if (typeof this.ctx.storage.setAlarm !== "function") return false;
    const state = await this.ctx.storage.get(STATE_KEY);
    if (!state) {
      const referralAt = await this.scheduleReferralRetry();
      const gemAt = await this.scheduleGemRetry();
      const pendingAt = [referralAt, gemAt].filter(Number.isFinite);
      if (pendingAt.length) await this.ctx.storage.setAlarm(Math.min(...pendingAt));
      return false;
    }
    if (state.status !== "ACTIVE") {
      const ttl = state.status === "FINISHED" ? FINISHED_IDLE_TTL_MS : ACTIVE_IDLE_TTL_MS;
      const updatedAt = Number(state.updatedAt ?? state.createdAt ?? Date.now());
      const referralAt = state.status === "FINISHED" ? await this.scheduleReferralRetry() : null;
      const gemAt = state.status === "FINISHED" ? await this.scheduleGemRetry() : null;
      const leaderboardAt = state.status === "FINISHED" && state.leaderboardRecordedAt == null ? Date.now() + 60_000 : null;
      await this.ctx.storage.setAlarm(Math.min(updatedAt + ttl,
        ...[referralAt, gemAt, leaderboardAt].filter(Number.isFinite)));
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
      if (refreshed?.status === "FINISHED") return this.#scheduleNextDeadline();
      if (!Number.isSafeInteger(refreshed?.turnDeadlineAt)) return false;
      await this.ctx.storage.setAlarm(refreshed.turnDeadlineAt);
      return true;
    }
    if (deadline <= Date.now()) {
      const expired = await this.#expireTurnRollIfNeeded();
      return expired ? this.#scheduleNextDeadline() : false;
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
    if (deadline == null) return false;

    const forcedToken = forcedAutomaticTokenIndex(state);
    const forcedAt = Number(pending.resolvedAt) + FORCED_MOVE_RECOVERY_MS;
    if (forcedToken != null && now >= forcedAt) {
      // Same engine and serialized queue as a real /move. If a client move
      // raced with this alarm it has already changed the pending event; if it
      // arrives later /move returns the recorded receipt as a safe replay.
      const moved = applyMove(state, {
        playerId: pending.playerId,
        tokenIndex: forcedToken,
        now,
      });
      const next = moved.state;
      const history = Array.isArray(next.history) ? [...next.history] : [];
      const eventIndex = history.findLastIndex(event =>
        event?.eventIndex === pending.eventIndex &&
        event?.playerId === pending.playerId
      );
      // A missing receipt indicates corrupted state; never commit a move
      // without its matching verified roll history.
      if (eventIndex < 0) {
        throw new Error("FORCED_MOVE_HISTORY_MISSING");
      }
      history[eventIndex] = {
        ...history[eventIndex],
        moveTokenIndex: forcedToken,
        captures: moved.captures,
        extraTurn: moved.extraTurn,
        winnerPlayerId: moved.winnerPlayerId,
        movedAt: now,
        autoMoveSource: "SERVER_FORCED_MOVE_RECOVERY",
      };
      next.history = history;
      await this.#persistResolvedTurn(next);
      // Finished-match settlement can change the persisted leaderboard state.
      const finalState = next.status === "FINISHED"
        ? await this.recordFinishedMatchForLifecycle(next)
        : next;
      broadcastStateChanged(this.ctx, finalState, this.env);
      return true;
    }
    if (now < deadline) return false;

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
    const ruleset = rulesetForState(next);
    if (Number.isSafeInteger(ruleset.maxMissedRollsBeforeForfeit)) {
      // Missing a move consumes the same inactivity allowance as missing a roll.
      next.turnDeadlineAt = deadline;
      next.turnDeadlineSeat = seat;
      const { state: expired } = forfeitTimedOutTurn(next, { now, phase: "MOVE" });
      await this.#persistResolvedTurn(expired);
      const finalState = expired.status === "FINISHED"
        ? await this.recordFinishedMatchForLifecycle(expired)
        : expired;
      broadcastStateChanged(this.ctx, finalState, this.env);
      return true;
    }

    next.turnSeat = nextActiveSeat(
      next,
      Number.isInteger(next.turnSeat)
        ? next.turnSeat
        : seat,
    );
    next.turnDeadlineSeat = null;
    next.turnDeadlineAt = null;
    next.updatedAt = now;
    next.revision = Number(next.revision ?? 0) + 1;

    await this.#persistResolvedTurn(next);
    broadcastStateChanged(this.ctx, next, this.env);
    return true;
  }

  async #persistResolvedTurn(state) {
    await this.ctx.storage.put(STATE_KEY, state);
    if (typeof this.ctx.storage.setAlarm !== "function") return;
    const deadline = state.status === "FINISHED"
      ? Date.now() + 60_000
      : state.turnDeadlineAt ?? (state.updatedAt + ACTIVE_IDLE_TTL_MS);
    await this.ctx.storage.setAlarm(deadline);
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
    const now = Date.now();
    const forcedToken = forcedAutomaticTokenIndex(state);
    const forcedAt = forcedToken == null
      ? null
      : Number(pending.resolvedAt) + FORCED_MOVE_RECOVERY_MS;
    if (deadline <= now || (forcedAt != null && forcedAt <= now)) {
      if (await this.#expireResolvedMoveIfNeeded()) return this.#scheduleNextDeadline();
      return false;
    }

    await this.ctx.storage.setAlarm(
      forcedAt == null ? deadline : Math.min(deadline, forcedAt),
    );
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

// A server-side fail-closed equivalent of the Android effective-move policy.
// Only rulesets explicitly promising automatic moves participate. Always derive
// legality again from the authoritative tokens; never trust stale client indexes.
export function forcedAutomaticTokenIndex(state) {
  if (state?.status !== "ACTIVE" || state?.pendingRoll?.status !== "RESOLVED") return null;
  if (rulesetForState(state).singleLegalMovePolicy !== "AUTO_MOVE") return null;

  const pending = state.pendingRoll;
  const seat = pending.seat;
  if (!Number.isInteger(seat) || seat !== actingSeatForTurn(state)) return null;
  if (isTeamUp(state) && pending.scheduledSeat !== state.turnSeat) return null;
  if (!Number.isSafeInteger(pending.eventIndex) || pending.eventIndex < 0) return null;
  const player = state.players?.[seat];
  if (!player || player.playerType === "BOT" || player.playerId !== pending.playerId ||
      Number.isSafeInteger(player.forfeitedAt)) return null;
  // Recover only a verified, unresolved event with a durable history receipt.
  const event = state.history?.findLast(entry =>
    entry?.eventIndex === pending.eventIndex &&
    entry?.playerId === pending.playerId
  );
  if (!event || event.moveTokenIndex != null || event.proofDigest !== pending.proofDigest ||
      typeof pending.proofDigest !== "string" || pending.proofDigest.length === 0) return null;
  const roll = pending.outcome;
  if (!Number.isInteger(roll) || roll < 1 || roll > 6) return null;
  if (!Array.isArray(player.tokens) || player.tokens.length !== 4) return null;

  const advertised = pending.legalTokenIndexes;
  if (!Array.isArray(advertised) || !advertised.length ||
      advertised.some(index => !Number.isInteger(index) || index < 0 || index > 3) ||
      new Set(advertised).size !== advertised.length) return null;

  const canonical = legalTokenIndexes(state, seat, roll);
  if (canonical.length !== advertised.length ||
      !canonical.every(index => advertised.includes(index))) return null;

  const destinations = canonical.map(index => destinationForTokenRoll(state, seat, index, roll));
  if (destinations.some(destination => destination == null)) return null;
  if (new Set(destinations).size !== 1) return null;
  return Math.min(...canonical);
}
