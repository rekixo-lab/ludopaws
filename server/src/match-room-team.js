import {
  MatchRoom as ForfeitMatchRoom,
} from "./match-room-forfeit.js";
import {
  isTeamUp,
  publicState,
  teamIdForSeat,
} from "./game.js";
import {
  bearerToken,
  sha256Hex,
} from "./crypto.js";
import {
  compactRollHistory,
  recordedRollEvent,
} from "./match-events.js";

const STATE_KEY = "match-state";
const PLAYER_TOKEN_HASH_DOMAIN = "ludoproof:player-token:v1:";
const FINISHED_IDLE_TTL_MS = 24 * 60 * 60 * 1000;

/**
 * Team-aware compatibility layer.
 *
 * TEAM_UP creation is handled directly by the base authoritative MatchRoom so
 * the final team state is constructed and persisted once. This layer remains
 * only for Team Up lifecycle behavior that is not yet part of the base room,
 * such as team forfeiture.
 */
export class MatchRoom extends ForfeitMatchRoom {
  async fetch(request) {
    const url = new URL(request.url);

    if (request.method === "POST" && url.pathname === "/start") {
      // Team forfeiture mutates the same authoritative room state as roll/move
      // requests, so it must participate in the shared mutation queue. Running
      // it outside mutationTail can overwrite a concurrent roll or move with a
      // stale snapshot.
      const teamForfeit = await this.#enqueue(
        () => this.#teamForfeit(request),
      );
      if (teamForfeit != null) return teamForfeit;
    }

    return super.fetch(request);
  }

  #enqueue(work) {
    const run = this.mutationTail.then(work, work);
    this.mutationTail = run.catch(() => {});
    return run;
  }

  async #teamForfeit(request) {
    let body;
    try {
      body = await request.clone().json();
    } catch {
      return null;
    }
    if (body?.forfeit !== true) return null;

    const state = await this.ctx.storage.get(STATE_KEY);
    if (!state || !isTeamUp(state)) return null;

    let token;
    try {
      token = bearerToken(request);
    } catch {
      return null;
    }
    const tokenHash = await sha256Hex(PLAYER_TOKEN_HASH_DOMAIN + token);
    const seat = state.players.findIndex(
      (player) => player?.tokenAuthHash === tokenHash,
    );
    if (seat < 0) {
      return json(403, {
        error: "INVALID_PLAYER_TOKEN",
        message: "player token is not valid for this match",
      });
    }

    if (state.status === "FINISHED") {
      return json(200, {
        ok: true,
        replayed: true,
        winnerTeamId: state.winnerTeamId ?? null,
        state: decoratedState(state),
      });
    }
    if (state.status !== "ACTIVE") {
      return json(409, {
        error: "INVALID_MATCH_STATUS",
        message: "only an active team match can be forfeited",
      });
    }

    const now = Date.now();
    const next = structuredClone(state);
    const actor = next.players[seat];
    const losingTeam = teamIdForSeat(next, seat);
    next.players.forEach((teammate, teammateSeat) => {
      if (teamIdForSeat(next, teammateSeat) !== losingTeam) return;
      teammate.forfeitedAt = now;
      teammate.forfeitReason =
        teammateSeat === seat ? "PLAYER_EXIT" : "TEAMMATE_EXIT";
      teammate.tokens = [-1, -1, -1, -1];
      if (Array.isArray(next.consecutiveSixes)) {
        next.consecutiveSixes[teammateSeat] = 0;
      }
    });

    // A terminal match must never expose a pending roll. If the team forfeit
    // interrupts an unresolved cryptographic round, close that event in the
    // fairness chain so the receipt event count stays complete and auditable.
    closePendingRollForTeamForfeit(next, now);

    const winnerTeamId = losingTeam === "A" ? "B" : "A";
    next.status = "FINISHED";
    next.winnerPlayerId = null;
    next.winnerTeamId = winnerTeamId;
    next.finishedAt = now;
    next.updatedAt = now;
    next.revision = Number(next.revision ?? 0) + 1;
    // Individual leaderboard semantics cannot represent two co-winners safely.
    next.leaderboardRecordedAt = now;
    next.turnDeadlineAt = null;
    next.turnDeadlineSeat = null;

    await this.ctx.storage.put(STATE_KEY, next);
    if (typeof this.ctx.storage.setAlarm === "function") {
      await this.ctx.storage.setAlarm(now + FINISHED_IDLE_TTL_MS);
    }
    broadcastStateChanged(this.ctx, next);

    return json(200, {
      ok: true,
      replayed: false,
      forfeitedPlayerId: actor.playerId,
      winnerPlayerId: null,
      winnerTeamId,
      state: decoratedState(next),
    });
  }
}

function closePendingRollForTeamForfeit(state, now) {
  const pending = state.pendingRoll;
  if (!pending) return;

  const history = Array.isArray(state.history)
    ? [...state.history]
    : [];
  const alreadyArchived = history.some(
    (event) => event?.eventIndex === pending.eventIndex,
  );

  if (!alreadyArchived) {
    state.randomEventIndex = Math.max(
      Number(state.randomEventIndex ?? 0),
      Number(pending.eventIndex ?? 0) + 1,
    );
    const fairnessEvent = recordedRollEvent(
      history,
      {
        eventIndex: pending.eventIndex,
        eventId: pending.eventId ?? null,
        playerId: pending.playerId,
        color: state.players?.[pending.seat]?.color ?? null,
        roundId: pending.roundId ?? null,
        serverCommitment: pending.serverCommitment ?? null,
        clientCommitment: pending.clientCommitment ?? null,
        actorHash: pending.actorHash ?? null,
        previousStateHash: pending.previousStateHash ?? null,
        rulesetHash: pending.rulesetHash ?? null,
        proofDigest: pending.proofDigest ?? null,
        outcome: pending.outcome ?? null,
        moveTokenIndex: null,
        captures: 0,
        status: "FORFEITED",
        timeoutReason: null,
        replacementRoundAllowed: false,
        resolvedAt: pending.resolvedAt ?? null,
        timedOutAt: null,
      },
    );
    state.history = [...history, fairnessEvent].slice(-64);
  }

  state.pendingRoll = null;
}

function decoratedState(state) {
  const base = publicState(state);
  return {
    ...base,
    players: base.players.map((player, seat) => ({
      ...player,
      forfeited: Number.isSafeInteger(state.players?.[seat]?.forfeitedAt),
      forfeitedAt: Number.isSafeInteger(state.players?.[seat]?.forfeitedAt)
        ? state.players[seat].forfeitedAt
        : null,
    })),
    ...compactRollHistory(state),
  };
}

function broadcastStateChanged(ctx, state) {
  if (typeof ctx.getWebSockets !== "function") return;
  const payload = JSON.stringify({
    type: "STATE_CHANGED",
    matchId: state.matchId,
    revision: state.revision,
    status: state.status,
    playerCount: state.players?.length ?? 0,
    state: decoratedState(state),
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

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}
