import {
  MatchRoom as BaseMatchRoom,
} from "./match-room.js";
import {
  bearerToken,
  httpError,
  sha256Hex,
} from "./crypto.js";
import {
  actingSeatForTurn,
  isTeamUp,
  publicState,
  rulesetForState,
  teamIdForSeat,
} from "./game.js";
import {
  fairnessSummary,
  sealFairnessEvent,
} from "./fairness.js";

const STATE_KEY =
  "match-state";
const ACTIVE_IDLE_TTL_MS =
  7 * 24 * 60 * 60 * 1000;
const FINISHED_IDLE_TTL_MS =
  24 * 60 * 60 * 1000;
const PLAYER_TOKEN_HASH_DOMAIN =
  "ludoproof:player-token:v1:";

export class MatchRoom extends BaseMatchRoom {
  async fetch(request) {
    const url =
      new URL(
        request.url,
      );

    if (
      request.method ===
        "POST" &&
      url.pathname ===
        "/start" &&
      await isForfeitRequest(
        request,
      )
    ) {
      const response =
        await this.#enqueue(
          () =>
            this.#forfeit(
              request,
            ),
        );

      if (
        response.ok
      ) {
        const payload =
          await response
            .clone()
            .json();
        if (
          payload?.state
            ?.status ===
          "FINISHED"
        ) {
          await super.fetch(
            stateRequest(
              request,
            ),
          );
        }
      }

      return response;
    }

    const guarded =
      shouldGuard(
        request.method,
        url.pathname,
      );

    if (
      guarded ||
      url.pathname ===
        "/state"
    ) {
      await this.#enqueue(
        () =>
          this.#normalizeTurn(),
      );
    }

    if (
      guarded
    ) {
      const blocked =
        await this.#enqueue(
          () =>
            this.#forfeitedMutationResponse(
              request,
            ),
        );
      if (
        blocked
      ) {
        return blocked;
      }
    }

    const response =
      await super.fetch(
        request,
      );

    if (
      guarded &&
      response.ok
    ) {
      await this.#enqueue(
        () =>
          this.#normalizeTurn(),
      );
    }

    if (
      !response.ok
    ) {
      return response;
    }

    const stored =
      await this.ctx.storage.get(
        STATE_KEY,
      );
    if (
      !stored
    ) {
      return response;
    }

    return replaceResponseState(
      response,
      decoratedState(
        stored,
      ),
    );
  }

  #enqueue(work) {
    const run =
      this.mutationTail.then(
        work,
        work,
      );
    this.mutationTail =
      run.catch(
        () => {},
      );
    return run;
  }

  async #forfeit(request) {
    try {
      const state =
        await this.ctx.storage.get(
          STATE_KEY,
        );
      if (
        !state
      ) {
        throw httpError(
          404,
          "MATCH_NOT_FOUND",
          "match does not exist",
        );
      }

      const actor =
        await authenticatedPlayer(
          request,
          state,
        );
      if (
        isForfeitedPlayer(
          actor.player,
        )
      ) {
        return json(
          200,
          {
            ok: true,
            replayed: true,
            forfeitedPlayerId:
              actor.player
                .playerId,
            winnerPlayerId:
              state.winnerPlayerId ??
              null,
            state:
              decoratedState(
                state,
              ),
          },
        );
      }

      if (
        state.status !==
        "ACTIVE"
      ) {
        throw httpError(
          409,
          "INVALID_MATCH_STATUS",
          "only an active match can be forfeited",
        );
      }

      const now =
        Date.now();
      const previousActingSeat = actingSeatForTurn(state);
      const hadPendingRoll = state.pendingRoll != null;
      const next =
        structuredClone(
          state,
        );
      const player = next.players[actor.seat];

      if (isTeamUp(next)) {
        // The UI warns that an active Team Up exit forfeits the whole side.
        // Enforce that contract in authoritative state, including the partner.
        const losingTeam = teamIdForSeat(next, actor.seat);
        next.players.forEach((teammate, teammateSeat) => {
          if (teamIdForSeat(next, teammateSeat) !== losingTeam) return;
          teammate.forfeitedAt = now;
          teammate.forfeitReason =
            teammateSeat === actor.seat ? "PLAYER_EXIT" : "TEAMMATE_EXIT";
          teammate.tokens = [-1, -1, -1, -1];
          if (Array.isArray(next.consecutiveSixes)) {
            next.consecutiveSixes[teammateSeat] = 0;
          }
        });

        // If a roll was in flight for any seat, close it in the fairness chain
        // before finishing the team result; do not leave a dangling commitment.
        closePendingRollForTeamForfeit(next, now);
        next.status = "FINISHED";
        next.winnerPlayerId = null;
        next.winnerTeamId = losingTeam === "A" ? "B" : "A";
        next.finishedAt = now;
        next.leaderboardRecordedAt = now;
        next.turnDeadlineAt = null;
        next.turnDeadlineSeat = null;
      } else {
        player.forfeitedAt = now;
        player.forfeitReason = "PLAYER_EXIT";
        player.tokens = [-1, -1, -1, -1];
        if (Array.isArray(next.consecutiveSixes)) {
          next.consecutiveSixes[actor.seat] = 0;
        }

        clearPendingRollForSeat(next, actor.seat);
        finishOrAdvanceAfterForfeit(next, actor.seat, now);

        if (next.status !== "ACTIVE") {
          next.turnDeadlineAt = null;
          next.turnDeadlineSeat = null;
        } else {
          const timeoutMs = rulesetForState(next).turnRollTimeoutMs;
          const nextActingSeat = actingSeatForTurn(next);
          const actingSeatChanged = nextActingSeat !== previousActingSeat;
          const pendingRollEnded = hadPendingRoll && next.pendingRoll == null;
          if (
            Number.isSafeInteger(timeoutMs) &&
            timeoutMs > 0 &&
            (actingSeatChanged || pendingRollEnded)
          ) {
            next.turnDeadlineSeat = nextActingSeat;
            next.turnDeadlineAt = now + timeoutMs;
          } else if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) {
            next.turnDeadlineAt = null;
            next.turnDeadlineSeat = null;
          }
        }
      }
      touch(
        next,
        now,
      );
      await this.#persist(
        next,
      );

      return json(
        200,
        {
          ok: true,
          replayed: false,
          forfeitedPlayerId:
            player.playerId,
          winnerPlayerId:
            next.winnerPlayerId ??
            null,
          state:
            decoratedState(
              next,
            ),
        },
      );
    } catch (error) {
      return errorResponse(
        error,
      );
    }
  }

  async #forfeitedMutationResponse(
    request,
  ) {
    const state =
      await this.ctx.storage.get(
        STATE_KEY,
      );
    if (
      !state
    ) {
      return null;
    }

    let actor;
    try {
      actor =
        await authenticatedPlayer(
          request,
          state,
        );
    } catch {
      return null;
    }

    if (
      !isForfeitedPlayer(
        actor.player,
      )
    ) {
      return null;
    }

    return json(
      409,
      {
        error:
          "PLAYER_FORFEITED",
        message:
          "forfeited players cannot mutate this match",
      },
    );
  }

  async #normalizeTurn() {
    const state =
      await this.ctx.storage.get(
        STATE_KEY,
      );
    if (
      !state ||
      state.status !==
        "ACTIVE"
    ) {
      return false;
    }

    const activeSeats =
      activeSeatIndexes(
        state,
      );
    const now =
      Date.now();

    if (
      activeSeats.length <=
      1
    ) {
      const next =
        structuredClone(
          state,
        );
      finishWithRemainingPlayer(
        next,
        activeSeats,
        now,
      );
      touch(
        next,
        now,
      );
      await this.#persist(
        next,
      );
      return true;
    }

    if (
      !Number.isInteger(
        state.turnSeat,
      ) ||
      isForfeitedSeat(
        state,
        state.turnSeat,
      )
    ) {
      const next =
        structuredClone(
          state,
        );
      next.turnSeat =
        nextActiveSeat(
          next,
          Number.isInteger(
            state.turnSeat,
          )
            ? state.turnSeat
            : -1,
        );
      touch(
        next,
        now,
      );
      await this.#persist(
        next,
      );
      return true;
    }

    return false;
  }

  async #persist(state) {
    await this.ctx.storage.put(
      STATE_KEY,
      state,
    );

    if (
      typeof this.ctx.storage
        .setAlarm ===
      "function"
    ) {
      const ttl =
        state.status ===
          "FINISHED"
          ? FINISHED_IDLE_TTL_MS
          : ACTIVE_IDLE_TTL_MS;
      await this.ctx.storage
        .setAlarm(
          Number(
            state.updatedAt ??
              Date.now(),
          ) +
            ttl,
        );
    }

    broadcastStateChanged(
      this.ctx,
      state,
    );
  }
}

function stateRequest(
  request,
) {
  return new Request(
    "https://room/state",
    {
      method:
        "GET",
      headers: {
        authorization:
          request.headers.get(
            "authorization",
          ) ?? "",
      },
    },
  );
}

async function isForfeitRequest(
  request,
) {
  try {
    const body =
      await request
        .clone()
        .json();
    return body?.forfeit ===
      true;
  } catch {
    return false;
  }
}

function shouldGuard(
  method,
  path,
) {
  return (
    method ===
      "POST" &&
    [
      "/start",
      "/roll",
      "/move",
    ].includes(
      path,
    )
  );
}

async function authenticatedPlayer(
  request,
  state,
) {
  const token =
    bearerToken(
      request,
    );
  const tokenHash =
    await sha256Hex(
      PLAYER_TOKEN_HASH_DOMAIN +
        token,
    );
  const seat =
    state.players.findIndex(
      (player) =>
        player.tokenAuthHash ===
        tokenHash,
    );

  if (
    seat <
    0
  ) {
    throw httpError(
      403,
      "INVALID_PLAYER_TOKEN",
      "player token is not valid for this match",
    );
  }

  return {
    seat,
    player:
      state.players[
        seat
      ],
  };
}

function closePendingRollForTeamForfeit(state, now) {
  const pending = state.pendingRoll;
  if (!pending) return;

  const history = Array.isArray(state.history) ? [...state.history] : [];
  const alreadyArchived = history.some(
    (event) => event?.eventIndex === pending.eventIndex,
  );

  if (!alreadyArchived) {
    const eventIndex = Number(pending.eventIndex);
    if (Number.isSafeInteger(eventIndex) && eventIndex >= 0) {
      const currentEventIndex = Number(state.randomEventIndex ?? 0);
      state.randomEventIndex = Math.max(
        Number.isSafeInteger(currentEventIndex) && currentEventIndex >= 0
          ? currentEventIndex
          : 0,
        eventIndex + 1,
      );
      const fairnessEvent = sealFairnessEvent(history, {
        eventIndex,
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
      });
      state.history = [...history, fairnessEvent].slice(-200);
    } else {
      state.history = history;
    }
  }

  state.pendingRoll = null;
}

function clearPendingRollForSeat(
  state,
  seat,
) {
  const pending =
    state.pendingRoll;
  if (
    pending?.seat !==
    seat
  ) {
    return;
  }

  if (
    pending.status ===
      "CREATING" &&
    state.randomEventIndex ===
      pending.eventIndex
  ) {
    state.randomEventIndex +=
      1;
  }
  state.pendingRoll =
    null;
}

function finishOrAdvanceAfterForfeit(
  state,
  forfeitedSeat,
  now,
) {
  const activeSeats =
    activeSeatIndexes(
      state,
    );
  if (
    activeSeats.length <=
    1
  ) {
    finishWithRemainingPlayer(
      state,
      activeSeats,
      now,
    );
    return;
  }

  if (
    state.turnSeat ===
      forfeitedSeat ||
    isForfeitedSeat(
      state,
      state.turnSeat,
    )
  ) {
    state.turnSeat =
      nextActiveSeat(
        state,
        forfeitedSeat,
      );
  }
}

function finishWithRemainingPlayer(
  state,
  activeSeats,
  now,
) {
  const winnerSeat =
    activeSeats[0] ??
    null;
  state.status =
    "FINISHED";
  state.winnerPlayerId =
    winnerSeat == null
      ? null
      : state.players[
          winnerSeat
        ].playerId;
  state.turnSeat =
    winnerSeat;
  state.pendingRoll =
    null;
  state.finishedAt =
    now;
}

function activeSeatIndexes(
  state,
) {
  const result = [];
  for (
    let seat = 0;
    seat <
      state.players.length;
    seat += 1
  ) {
    if (
      !isForfeitedSeat(
        state,
        seat,
      )
    ) {
      result.push(
        seat,
      );
    }
  }
  return result;
}

function isForfeitedSeat(
  state,
  seat,
) {
  return (
    Number.isInteger(
      seat,
    ) &&
    seat >=
      0 &&
    seat <
      state.players.length &&
    isForfeitedPlayer(
      state.players[
        seat
      ],
    )
  );
}

function isForfeitedPlayer(
  player,
) {
  return Number.isSafeInteger(
    player?.forfeitedAt,
  );
}

function nextActiveSeat(
  state,
  fromSeat,
) {
  const playerCount =
    state.players.length;
  for (
    let offset = 1;
    offset <=
      playerCount;
    offset += 1
  ) {
    const seat =
      (
        fromSeat +
        offset +
        playerCount
      ) %
      playerCount;
    if (
      !isForfeitedSeat(
        state,
        seat,
      )
    ) {
      return seat;
    }
  }
  return null;
}

function decoratedState(
  state,
) {
  const history =
    (
      state.history ??
      []
    ).slice(
      -100,
    );
  const base =
    publicState(
      state,
    );

  return {
    ...base,
    players:
      base.players.map(
        (
          player,
          seat,
        ) => ({
          ...player,
          forfeited:
            isForfeitedPlayer(
              state.players?.[
                seat
              ],
            ),
          forfeitedAt:
            isForfeitedPlayer(
              state.players?.[
                seat
              ],
            )
              ? state.players[
                  seat
                ].forfeitedAt
              : null,
        }),
      ),
    history,
    fairness:
      fairnessSummary(
        history,
      ),
  };
}

function touch(
  state,
  now,
) {
  state.updatedAt =
    now;
  state.revision =
    Number(
      state.revision ??
        0,
    ) +
    1;
}

function broadcastStateChanged(
  ctx,
  state,
) {
  if (
    typeof ctx
      .getWebSockets !==
    "function"
  ) {
    return;
  }

  const payload =
    JSON.stringify({
      type:
        "STATE_CHANGED",
      matchId:
        state.matchId,
      revision:
        state.revision,
      status:
        state.status,
      playerCount:
        state.players
          ?.length ??
        0,
    });

  for (
    const socket of
      ctx.getWebSockets()
  ) {
    try {
      socket.send(
        payload,
      );
    } catch {
      try {
        socket.close(
          1011,
          "state sync failed",
        );
      } catch {
        // Socket is already gone.
      }
    }
  }
}

async function replaceResponseState(
  response,
  state,
) {
  if (
    !response.headers
      .get(
        "content-type",
      )
      ?.includes(
        "application/json",
      )
  ) {
    return response;
  }

  try {
    const payload =
      await response
        .clone()
        .json();
    if (
      !payload ||
      typeof payload !==
        "object" ||
      payload.state ==
        null
    ) {
      return response;
    }
    payload.state =
      state;
    return new Response(
      JSON.stringify(
        payload,
      ),
      {
        status:
          response.status,
        headers:
          response.headers,
      },
    );
  } catch {
    return response;
  }
}

function json(
  status,
  body,
) {
  return new Response(
    JSON.stringify(
      body,
    ),
    {
      status,
      headers: {
        "content-type":
          "application/json; charset=utf-8",
        "cache-control":
          "no-store",
        "x-content-type-options":
          "nosniff",
      },
    },
  );
}

function errorResponse(
  error,
) {
  const candidate =
    Number(
      error?.status ??
        500,
    );
  const status =
    Number.isInteger(
      candidate,
    ) &&
    candidate >=
      400 &&
    candidate <=
      599
      ? candidate
      : 500;

  return json(
    status,
    {
      error:
        error?.code ??
        "INTERNAL_ERROR",
      message:
        status >=
          500 &&
        !error?.code
          ? "internal server error"
          : String(
              error?.message ??
                "internal server error",
            ),
    },
  );
}
