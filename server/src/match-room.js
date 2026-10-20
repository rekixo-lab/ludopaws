import { rulesetHashForState, rollResponse, publicStateWithHistory } from "./match-room-state.js";
import { optionalTargetPlayerCount, normalizeMatchMode, optionalLeaderboardProfileId } from "./match-room-validation.js";
import { readJson, json, errorResponse } from "./match-room-http.js";
import { createFastDiceProof, FAST_DICE_PROTOCOL } from "./dice-fast.js";
import { requireMoveIndexes } from "./match-command-input.js";
import {
  actingSeatForTurn,
  addPlayer,
  applyMove,
  attachHostAuth,
  attachRoundCommitment,
  authoritativeStateForRandomness,
  forfeitTimedOutRoll,
  forfeitTimedOutTurn,
  newMatch,
  publicState,
  registerResolvedRoll,
  reserveRoll,
  rulesetForState,
  startMatch,
} from "./game.js";
import {
  bearerToken,
  canonicalJson,
  derivePlayerIdentity,
  httpError,
  normalizeDisplayName,
  requireClientRequestId,
  sha256Hex,
  verifyFriendRoomJoinToken,
  verifyLeaderboardProfileAssertion,
} from "./crypto.js";
import {
  compactRollHistory,
  recordedRollEvent,
} from "./match-events.js";
import {
  normalizeLeaderboardProfileId,
} from "./leaderboard-core.js";
import {
  normalizeCharacterId,
} from "./ludo-paws-characters.js";
import {
  LEGACY_RULESET_KEY,
  LEGACY_TEAM_RULESET_KEY,
  RULESET_KEY,
  RULESET_V3_KEY,
  RULESET_V4_KEY,
  TEAM_RULESET_KEY,
  TEAM_RULESET_V3_KEY,
  TEAM_RULESET_V4_KEY,
  rulesetKeyForState,
} from "./rulesets.js";

const STATE_KEY = "match-state";
const ACTIVE_IDLE_TTL_MS =
  7 * 24 * 60 * 60 * 1000;
const FINISHED_IDLE_TTL_MS =
  24 * 60 * 60 * 1000;
const QUICK_CHAT_EMOJIS = new Set([
  "👍", "😂", "😮", "😭", "😠", "🥳",
  "😅", "😛", "😎", "🤔", "😘", "🤭",
  "🤦", "😈", "🔥", "💪", "🤡", "👑",
  "😵‍💫", "😞", "🤣", "😆", "😤", "😡",
]);
const QUICK_CHAT_COOLDOWN_MS = 700;
const MAX_VOICE_SIGNAL_LENGTH = 16_000;

export class MatchRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.mutationTail = Promise.resolve();
  }

  async fetch(request) {
    try {
      const url = new URL(request.url);
      const body =
        request.method === "POST"
          ? await readJson(request)
          : null;

      if (request.method === "POST" && url.pathname === "/create") {
        return await this.#mutate(() => this.#create(request, body));
      }
      if (request.method === "POST" && url.pathname === "/join") {
        return await this.#mutate(() => this.#join(request, body));
      }
      if (
        request.method === "POST" &&
        url.pathname === "/friend-host/assert"
      ) {
        return await this.#mutate(
          () => this.#assertFriendHost(request, body),
        );
      }
      if (request.method === "GET" && url.pathname === "/state") {
        return await this.#mutate(() => this.#state(request));
      }
      if (request.method === "GET" && url.pathname === "/events") {
        return await this.#mutate(() => this.#events(request));
      }
      if (request.method === "POST" && url.pathname === "/start") {
        return await this.#mutate(() => this.#start(request));
      }
      if (request.method === "POST" && url.pathname === "/roll") {
        return await this.#mutate(() => this.#roll(request, body));
      }
      if (request.method === "POST" && ["/roll/commit", "/roll/reveal"].includes(url.pathname)) {
        return json(410, {
          error: "ROLL_PROTOCOL_UPGRADE_REQUIRED",
          message: "use the single-call /roll endpoint with clientRequestId",
        });
      }
      if (request.method === "POST" && url.pathname === "/move") {
        return await this.#mutate(() => this.#move(request, body));
      }

      return json(404, { error: "NOT_FOUND", message: "route not found" });
    } catch (error) {
      return errorResponse(error);
    }
  }

  #mutate(work) {
    const run = this.mutationTail.then(work, work);
    this.mutationTail = run.catch(() => {});
    return run;
  }

  async alarm() {
    // Serialize timer expiry with gameplay mutations. Otherwise an alarm and a
    // near-simultaneous roll/move can both advance the match from one turn.
    return this.#mutate(async () => {
      let state = await this.ctx.storage.get(STATE_KEY);
      if (!state) return;

      const now = Date.now();
      const expired = await this.#expirePendingIfNeeded(state, now);
      state = expired.state;

      if (expired.changed) {
        await this.#persist(state);
        return;
      }

      const turnExpired = this.#expireTurnIfNeeded(state, now);
      if (turnExpired.changed) {
        await this.#persist(turnExpired.state);
        await this.#recordFinishedMatchIfNeeded(turnExpired.state);
        return;
      }

      const expiresAt =
        Number(state.updatedAt ?? state.createdAt ?? 0) +
        this.#idleTtl(state);

      if (now < expiresAt) {
        await this.#setAlarm(this.#nextAlarmAt(state, expiresAt));
        return;
      }

      if (typeof this.ctx.storage.deleteAll === "function") {
        await this.ctx.storage.deleteAll();
      }
    });
  }

  async #persist(state) {
    await this.ctx.storage.put(
      STATE_KEY,
      state,
    );
    const idleExpiresAt =
      Number(state.updatedAt ?? state.createdAt ?? Date.now()) +
      this.#idleTtl(state);
    await this.#setAlarm(
      this.#nextAlarmAt(
        state,
        idleExpiresAt,
      ),
    );
    this.#broadcastStateChanged(
      state,
    );
  }

  #broadcastStateChanged(
    state,
  ) {
    if (
      typeof this.ctx
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
        state: publicStateWithHistory(state),
        status:
          state.status,
        playerCount:
          state.players
            ?.length ??
          0,
      });

    for (
      const socket of
        this.ctx.getWebSockets()
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

      this.#broadcastVoicePeers(state);
    }
  }

  #nextAlarmAt(state, idleExpiresAt) {
    const deadlines = [idleExpiresAt];
    const revealDeadline = state.pendingRoll?.revealDeadlineAt;
    if (Number.isSafeInteger(revealDeadline)) deadlines.push(revealDeadline);
    const turnDeadline =
      state.status === "ACTIVE" && state.pendingRoll == null
        ? state.turnDeadlineAt
        : null;
    if (Number.isSafeInteger(turnDeadline)) deadlines.push(turnDeadline);
    return Math.min(...deadlines);
  }

  #idleTtl(state) {
    return state.status === "FINISHED"
      ? FINISHED_IDLE_TTL_MS
      : ACTIVE_IDLE_TTL_MS;
  }

  async #setAlarm(timestamp) {
    if (
      typeof this.ctx.storage.setAlarm === "function"
    ) {
      await this.ctx.storage.setAlarm(timestamp);
    }
  }

  async #create(
    request,
    body,
  ) {
    const displayName =
      normalizeDisplayName(
        body?.displayName,
      );
    const characterId =
      normalizeCharacterId(
        body?.characterId,
      );
    const matchId =
      String(
        body?.matchId ?? "",
      );
    const clientRequestId =
      requireClientRequestId(
        body?.clientRequestId,
      );
    const targetPlayerCount =
      optionalTargetPlayerCount(
        body?.targetPlayerCount,
      );
    const matchMode =
      normalizeMatchMode(
        body?.matchMode,
        targetPlayerCount,
      );
    const requestedRulesetKey =
      body?.rulesetKey == null
        ? null
        : String(body.rulesetKey).trim();
    if (requestedRulesetKey != null) {
      const acceptedRulesets =
        matchMode === "TEAM_UP"
          ? [LEGACY_TEAM_RULESET_KEY, TEAM_RULESET_KEY, TEAM_RULESET_V3_KEY, TEAM_RULESET_V4_KEY]
          : [LEGACY_RULESET_KEY, RULESET_KEY, RULESET_V3_KEY, RULESET_V4_KEY];
      if (!acceptedRulesets.includes(requestedRulesetKey)) {
        throw httpError(
          400,
          "RULESET_MODE_MISMATCH",
          "rulesetKey is not supported for this match mode",
        );
      }
    }
    const hostFriend =
      matchMode ===
        "FRIENDS"
        ? await this.#friendIdentity(
            request,
          )
        : null;

    if (
      !/^LP[A-Z2-9]{8}$/.test(
        matchId,
      )
    ) {
      throw httpError(
        400,
        "INVALID_MATCH_ID",
        "invalid match ID",
      );
    }

    const { profileId, playerType } =
      await this.#resolveLeaderboardProfile(
        request,
        {
          matchId,
          clientRequestId,
          matchMode,
        },
      );

    const identity =
      await derivePlayerIdentity(
        this.env,
        matchId,
        clientRequestId,
      );
    const existing =
      await this.ctx.storage.get(
        STATE_KEY,
      );

    if (existing) {
      if (
        existing.createRequestId ===
          clientRequestId &&
        existing.hostPlayerId ===
          identity.playerId &&
        (
          existing.matchMode !==
            "FRIENDS" ||
          existing.hostFriendId ===
            hostFriend?.friendId
        ) &&
        (
          requestedRulesetKey == null ||
          rulesetKeyForState(existing, {
            teamUp: existing.matchMode === "TEAM_UP",
          }) === requestedRulesetKey
        )
      ) {
        return json(200, {
          replayed: true,
          matchId,
          playerId:
            identity.playerId,
          playerToken:
            identity.playerToken,
          state:
            publicStateWithHistory(
              existing,
            ),
        });
      }
      throw httpError(
        409,
        "MATCH_EXISTS",
        "match already exists",
      );
    }

    const tokenAuthHash =
      await sha256Hex(
        "ludoproof:player-token:v1:" +
          identity.playerToken,
      );
    const now = Date.now();

    let state = newMatch({
      matchId,
      hostPlayerId:
        identity.playerId,
      hostDisplayName:
        displayName,
      hostCharacterId:
        characterId,
      hostPlayerType:
        playerType,
      now,
      targetPlayerCount,
      matchMode,
      rulesetKey: requestedRulesetKey ?? (matchMode === "TEAM_UP" ? TEAM_RULESET_V4_KEY : RULESET_V4_KEY),
    });
    state = attachHostAuth(
      state,
      tokenAuthHash,
      now,
    );
    state.createRequestId =
      clientRequestId;
    state.players[0]
      .joinRequestId =
      clientRequestId;
    state.players[0]
      .profileId =
      matchMode ===
        "FRIENDS"
        ? null
        : profileId;
    if (
      hostFriend
    ) {
      state.hostFriendId =
        hostFriend.friendId;
      state.players[0]
        .friendId =
        hostFriend.friendId;
    }
    state.history = [];
    await this.#persist(state);

    return json(201, {
      replayed: false,
      matchId,
      playerId:
        identity.playerId,
      playerToken:
        identity.playerToken,
      state:
        publicStateWithHistory(
          state,
        ),
    });
  }

  async #join(
    request,
    body,
  ) {
    const state =
      await this.#requireState();
    const displayName =
      normalizeDisplayName(
        body?.displayName,
      );
    const characterId =
      normalizeCharacterId(
        body?.characterId,
      );
    const clientRequestId =
      requireClientRequestId(
        body?.clientRequestId,
      );
    const { profileId, playerType } =
      await this.#resolveLeaderboardProfile(
        request,
        {
          matchId:
            state.matchId,
          clientRequestId,
          matchMode:
            state.matchMode,
        },
      );
    let friendJoin =
      null;
    if (
      state.matchMode ===
        "FRIENDS"
    ) {
      const token =
        request.headers.get(
          "x-ludoproof-friend-join-token",
        );
      if (
        typeof token !==
          "string" ||
        token.length ===
          0
      ) {
        throw httpError(
          401,
          "FRIEND_JOIN_TOKEN_REQUIRED",
          "accepted friend invite credential is required",
        );
      }

      friendJoin =
        await verifyFriendRoomJoinToken(
          this.env,
          token,
        );
      if (
        friendJoin.matchId !==
          state.matchId ||
        friendJoin.hostFriendId !==
          state.hostFriendId
      ) {
        throw httpError(
          403,
          "FRIEND_JOIN_TOKEN_MISMATCH",
          "friend-room join credential is not valid for this room",
        );
      }

      await this.#assertFriendInviteActive(
        friendJoin,
      );

      const existingInvitePlayer =
        state.players.find(
          (candidate) =>
            candidate.friendInviteId ===
              friendJoin.inviteId &&
            candidate.friendId ===
              friendJoin.friendId,
        );
      if (
        existingInvitePlayer
      ) {
        const replayIdentity =
          await derivePlayerIdentity(
            this.env,
            state.matchId,
            existingInvitePlayer
              .joinRequestId,
          );
        return json(200, {
          replayed: true,
          matchId:
            state.matchId,
          playerId:
            replayIdentity.playerId,
          playerToken:
            replayIdentity.playerToken,
          state:
            publicStateWithHistory(
              state,
            ),
        });
      }

      if (
        state.players.some(
          (candidate) =>
            candidate.friendId ===
              friendJoin.friendId,
        )
      ) {
        throw httpError(
          409,
          "FRIEND_ALREADY_JOINED",
          "this friend is already seated in the room",
        );
      }
      if (
        state.players.some(
          (candidate) =>
            candidate.friendInviteId ===
              friendJoin.inviteId,
        )
      ) {
        throw httpError(
          409,
          "FRIEND_INVITE_ALREADY_USED",
          "this friend invite already filled a room seat",
        );
      }
    }

    const identity =
      await derivePlayerIdentity(
        this.env,
        state.matchId,
        clientRequestId,
      );

    const existingPlayer =
      state.players.find(
        (candidate) =>
          candidate.playerId ===
            identity.playerId &&
          candidate.joinRequestId ===
            clientRequestId,
      );
    if (existingPlayer) {
      return json(200, {
        replayed: true,
        matchId:
          state.matchId,
        playerId:
          identity.playerId,
        playerToken:
          identity.playerToken,
        state:
          publicStateWithHistory(
            state,
          ),
      });
    }

    if (
      profileId != null &&
      state.players.some(
        (candidate) =>
          candidate.profileId ===
            profileId,
      )
    ) {
      throw httpError(
        409,
        "PROFILE_ALREADY_JOINED",
        "this leaderboard profile is already in the match",
      );
    }

    const tokenAuthHash =
      await sha256Hex(
        "ludoproof:player-token:v1:" +
          identity.playerToken,
      );

    const next = addPlayer(
      state,
      {
        playerId:
          identity.playerId,
        displayName,
        characterId,
        playerType,
        tokenAuthHash,
        now: Date.now(),
      },
    );
    next.players[
      next.players.length - 1
    ].joinRequestId =
      clientRequestId;
    next.players[
      next.players.length - 1
    ].profileId =
      profileId;
    if (
      friendJoin
    ) {
      next.players[
        next.players.length - 1
      ].friendId =
        friendJoin.friendId;
      next.players[
        next.players.length - 1
      ].friendInviteId =
        friendJoin.inviteId;
    }
    next.history =
      state.history ?? [];
    await this.#persist(next);

    return json(201, {
      replayed: false,
      matchId:
        next.matchId,
      playerId:
        identity.playerId,
      playerToken:
        identity.playerToken,
      state:
        publicStateWithHistory(
          next,
        ),
    });
  }

  async #state(request) {
    let state = await this.#requireState();
    const player = await this.#authorize(state, request);
    state =
      await this.#recordFinishedMatchIfNeeded(
        state,
      );
    return json(200, {
      playerId: player.playerId,
      state: publicStateWithHistory(state),
    });
  }

  async #start(request) {
    const state =
      await this.#requireState();
    const player =
      await this.#authorize(
        state,
        request,
      );

    if (
      state.status === "ACTIVE" &&
      state.hostPlayerId ===
        player.playerId
    ) {
      return json(200, {
        replayed: true,
        state:
          publicStateWithHistory(
            state,
          ),
      });
    }

    const next =
      startMatch(
        state,
        player.playerId,
        Date.now(),
      );
    next.history =
      state.history ?? [];
    await this.#persist(next);
    return json(200, {
      replayed: false,
      state:
        publicStateWithHistory(
          next,
        ),
    });
  }

  /** Dispatch a socket command using a server-derived authenticated session. */
  async realtimeRequestForPlayer(playerId, path, body) {
    if (!["/roll", "/move", "/start"].includes(path)) {
      throw httpError(400, "INVALID_REALTIME_COMMAND", "unsupported game command");
    }
    const state = await this.ctx.storage.get(STATE_KEY);
    const player = state?.players?.find((candidate) => candidate.playerId === playerId);
    if (!player?.joinRequestId) {
      throw httpError(403, "AUTH_INVALID", "player session is unavailable");
    }
    const identity = await derivePlayerIdentity(this.env, state.matchId, player.joinRequestId);
    const tokenHash = await sha256Hex("ludoproof:player-token:v1:" + identity.playerToken);
    if (identity.playerId !== playerId || tokenHash !== player.tokenAuthHash) {
      throw httpError(403, "AUTH_INVALID", "player session does not match this room");
    }
    return this.fetch(new Request("https://match-room" + path, {
      method: "POST",
      headers: {
        authorization: "Bearer " + identity.playerToken,
        "content-type": "application/json",
      },
      body: JSON.stringify(body),
    }));
  }

  async #roll(request, body) {
    const clientRequestId = requireClientRequestId(body?.clientRequestId);
    let state = await this.#requireState();
    const player = await this.#authorize(state, request);
    if (Number.isSafeInteger(player.forfeitedAt)) {
      throw httpError(403, "PLAYER_FORFEITED", "a forfeited player cannot roll");
    }
    const requestKey = player.playerId + ":" + clientRequestId;
    const previous = state.rollRequests?.[requestKey];
    if (previous) {
      return json(200, rollResponse(state, previous, true));
    }
    const eventIndex = state.randomEventIndex;
    if (!Number.isSafeInteger(body?.expectedEventIndex) || body.expectedEventIndex < 0) {
      throw httpError(400, "INVALID_EVENT_INDEX", "expectedEventIndex must be a non-negative integer");
    }
    if (body.expectedEventIndex !== eventIndex) {
      throw httpError(409, "EVENT_INDEX_DRIFT", "roll does not match the current random event index");
    }
    const now = Date.now();
    const actorHash = await sha256Hex("ludoproof:actor:v1:" + player.playerId);
    const previousStateHash = await sha256Hex(
      "ludopaws:game-state:v1:" + canonicalJson(authoritativeStateForRandomness(state)),
    );
    const rulesetHash = await rulesetHashForState(state);
    // Reserve and resolve in memory. The only durable write contains the final
    // result and request receipt together, so an interrupted request cannot reroll.
    state = reserveRoll(state, {
      playerId: player.playerId,
      clientCommitment: null,
      eventIndex,
      actorHash,
      previousStateHash,
      rulesetHash,
      now,
    });
    const proof = await createFastDiceProof({
      matchId: state.matchId,
      playerId: player.playerId,
      clientRequestId,
      eventIndex,
      previousStateHash,
      rulesetHash,
    });
    state = attachRoundCommitment(state, {
      roundId: proof.roundId,
      serverCommitment: proof.serverCommitment,
      now,
    });
    state.pendingRoll.diceProtocol = FAST_DICE_PROTOCOL;
    state.pendingRoll.clientRequestId = clientRequestId;
    state.pendingRoll.revealDeadlineAt = null;
    const pending = state.pendingRoll;
    const result = registerResolvedRoll(state, {
      outcome: proof.outcome,
      proofDigest: proof.proofDigest,
      now,
    });
    const outcome = result.effectiveOutcome ?? proof.outcome;
    const receipt = {
      clientRequestId,
      playerId: player.playerId,
      eventIndex,
      outcome,
      randomOutcome: proof.outcome,
      openingRollApplied: Boolean(result.openingRollApplied),
      proofDigest: proof.proofDigest,
      roundId: proof.roundId,
      legalTokenIndexes: result.legalTokenIndexes,
      awaitingMove: result.awaitingMove,
      forfeitedThirdSix: result.forfeitedThirdSix,
      proof,
    };
    const { proof: _proof, ...historyReceipt } = receipt;
    const event = recordedRollEvent(state.history ?? [], {
      ...pending,
      ...historyReceipt,
      color: state.players[pending.seat].color,
      effectiveOutcome: outcome,
      moveTokenIndex: null,
      captures: 0,
      status: "RESOLVED",
      resolvedAt: now,
    });
    const next = result.state;
    next.history = [...(state.history ?? []), event].slice(-64);
    // Bound reconnect receipts; a delayed retry beyond this window still fails
    // expectedEventIndex instead of generating a new random event.
    next.rollRequests = Object.fromEntries(
      [...Object.entries(state.rollRequests ?? {}), [requestKey, receipt]].slice(-64),
    );
    // Reserve + attach have already incremented the temporary revision twice.
    // The three in-memory core transitions are one observable mutation.
    next.revision = Number(state.revision) - 1;
    await this.#persist(next);
    return json(200, rollResponse(next, receipt, false));
  }

  async #move(request, body) {
    let state =
      await this.#requireState();
    const player =
      await this.#authorize(
        state,
        request,
      );
    const { tokenIndex, eventIndex } = requireMoveIndexes(body);

    const previous =
      [...(state.history ?? [])]
        .reverse()
        .find(
          (event) =>
            event.eventIndex ===
              eventIndex &&
            event.playerId ===
              player.playerId &&
            event.moveTokenIndex !=
              null,
        );

    if (previous) {
      if (
        previous.moveTokenIndex !==
        tokenIndex
      ) {
        throw httpError(
          409,
          "MOVE_ALREADY_APPLIED",
          "this verified roll was already applied to another token",
        );
      }
      state =
        await this.#recordFinishedMatchIfNeeded(
          state,
        );
      return json(200, {
        replayed: true,
        captures:
          previous.captures ?? 0,
        extraTurn:
          Boolean(
            previous.extraTurn,
          ),
        winnerPlayerId:
          previous.winnerPlayerId ??
          null,
        state:
          publicStateWithHistory(
            state,
          ),
      });
    }

    const pending =
      state.pendingRoll;
    if (!pending) {
      throw httpError(
        409,
        "NO_RESOLVED_ROLL",
        "a verified roll is required before moving",
      );
    }
    if (
      pending.eventIndex !==
      eventIndex
    ) {
      throw httpError(
        409,
        "EVENT_INDEX_DRIFT",
        "move does not match the currently resolved roll",
      );
    }

    const moved =
      applyMove(
        state,
        {
          playerId:
            player.playerId,
          tokenIndex,
          now: Date.now(),
        },
      );
    state = moved.state;

    const history = [
      ...(state.history ?? []),
    ];
    const entryIndex =
      history.findLastIndex(
        (event) =>
          event.eventIndex ===
            eventIndex &&
          event.playerId ===
            player.playerId,
      );
    if (entryIndex < 0) {
      throw httpError(
        500,
        "MOVE_HISTORY_MISSING",
        "verified roll history is missing",
      );
    }

    history[entryIndex] = {
      ...history[entryIndex],
      moveTokenIndex:
        tokenIndex,
      captures:
        moved.captures,
      extraTurn:
        moved.extraTurn,
      winnerPlayerId:
        moved.winnerPlayerId,
      movedAt:
        Date.now(),
    };
    state.history = history;
    await this.#persist(state);
    state =
      await this.#recordFinishedMatchIfNeeded(
        state,
      );

    return json(200, {
      replayed: false,
      captures: moved.captures,
      extraTurn: moved.extraTurn,
      winnerPlayerId:
        moved.winnerPlayerId,
      state:
        publicStateWithHistory(
          state,
        ),
    });
  }

  /**
   * Lifecycle uses the same idempotent terminal-result recording path when its
   * deadline alarm performs the state transition before the base alarm runs.
   */
  async recordFinishedMatchForLifecycle(state) {
    return this.#recordFinishedMatchIfNeeded(state);
  }

  async #recordFinishedMatchIfNeeded(
    state,
  ) {
    if (
      state.status !== "FINISHED" ||
      state.leaderboardRecordedAt != null
    ) {
      return state;
    }

    if (
      state.matchMode ===
      "FRIENDS"
    ) {
      const next =
        structuredClone(
          state,
        );
      next.leaderboardRecordedAt =
        Date.now();
      await this.#persist(
        next,
      );
      return next;
    }

    // Bot-filled games are casual/unranked; never let bot seats influence
    // competitive leaderboard submissions. Mark the decision idempotently.
    if (state.players.some((player) => player.playerType === "BOT")) {
      const next = structuredClone(state);
      next.leaderboardRecordedAt = Date.now();
      await this.#persist(next);
      return next;
    }

    const players =
      state.players
        .map((player) => ({
          profileId:
            normalizeLeaderboardProfileId(
              player.profileId,
            ),
          displayName:
            player.displayName,
          won:
            player.playerId ===
              state.winnerPlayerId,
        }))
        .filter(
          (player) =>
            player.profileId != null,
        );

    if (players.length === 0) {
      const next =
        structuredClone(state);
      next.leaderboardRecordedAt =
        Date.now();
      await this.#persist(next);
      return next;
    }

    if (!this.env.LUDOPROOF_LEADERBOARD) {
      return state;
    }

    try {
      const id =
        this.env.LUDOPROOF_LEADERBOARD
          .idFromName("global");
      const target =
        this.env.LUDOPROOF_LEADERBOARD
          .get(id);
      const response =
        await target.fetch(
          new Request(
            "https://leaderboard/record",
            {
              method: "POST",
              headers: {
                "content-type":
                  "application/json",
              },
              body: JSON.stringify({
                matchId:
                  state.matchId,
                players,
              }),
            },
          ),
        );

      if (!response.ok) {
        return state;
      }

      const next =
        structuredClone(state);
      next.leaderboardRecordedAt =
        Date.now();
      await this.#persist(next);
      return next;
    } catch {
      return state;
    }
  }

  async #events(
    request,
  ) {
    const upgrade =
      request.headers
        .get(
          "upgrade",
        );
    if (
      upgrade
        ?.toLowerCase() !==
      "websocket"
    ) {
      throw httpError(
        426,
        "WEBSOCKET_REQUIRED",
        "realtime events require a WebSocket upgrade",
      );
    }

    const state =
      await this.#requireState();
    const player =
      await this.#authorize(
        state,
        request,
      );

    if (
      typeof WebSocketPair ===
        "undefined" ||
      typeof this.ctx
        .acceptWebSocket !==
        "function"
    ) {
      throw httpError(
        503,
        "REALTIME_UNAVAILABLE",
        "realtime events are unavailable",
      );
    }

    const [
      client,
      server,
    ] =
      Object.values(
        new WebSocketPair(),
      );

    this.ctx.acceptWebSocket(
      server,
      [
        "player:" +
          player.playerId,
      ],
    );
    server.serializeAttachment({
      playerId: player.playerId,
      voiceJoined: false,
    });

    server.send(
      JSON.stringify({
        type:
          "SYNC",
        matchId:
          state.matchId,
        revision:
          state.revision,
        state: publicStateWithHistory(state),
        status:
          state.status,
        playerCount:
          state.players
            .length,
      }),
    );

    return new Response(
      null,
      {
        status:
          101,
        webSocket:
          client,
      },
    );
  }

  async webSocketMessage(
    socket,
    message,
  ) {
    if (typeof message !== "string") return;

    if (message === "ping") {
      try {
        socket.send(JSON.stringify({ type: "PONG", at: Date.now() }));
      } catch {
        // Hibernated socket may already be closing.
      }
      return;
    }

    if (message.length > MAX_VOICE_SIGNAL_LENGTH) return;
    let payload;
    try {
      payload = JSON.parse(message);
    } catch {
      return;
    }
    if (payload?.type === "QUICK_CHAT_SEND") {
      if (message.length > 512) return;
      await this.#mutate(() => this.#handleQuickChat(socket, payload));
      return;
    }
    if (
      payload?.type === "VOICE_JOIN" ||
      payload?.type === "VOICE_LEAVE" ||
      payload?.type === "VOICE_SIGNAL"
    ) {
      await this.#mutate(() => this.#handleVoiceMessage(socket, payload));
    }
  }

  async #handleVoiceMessage(socket, payload) {
    const playerId = this.#socketPlayerId(socket);
    if (!playerId) return;

    const state = await this.ctx.storage.get(STATE_KEY);
    if (
      !state ||
      state.matchMode !== "FRIENDS" ||
      state.status !== "ACTIVE" ||
      !Array.isArray(state.players)
    ) {
      if (payload.type === "VOICE_LEAVE") {
        this.#setVoiceJoined(socket, false);
      }
      return;
    }

    const player = state.players.find(
      (candidate) =>
        candidate?.playerId === playerId &&
        !Number.isSafeInteger(candidate.forfeitedAt),
    );
    if (!player) return;

    if (payload.type === "VOICE_JOIN") {
      this.#setVoiceJoined(socket, true);
      this.#broadcastVoicePeers(state);
      return;
    }

    if (payload.type === "VOICE_LEAVE") {
      this.#setVoiceJoined(socket, false);
      this.#broadcastVoicePeers(state);
      return;
    }

    if (!this.#isVoiceJoined(socket)) return;
    const targetPlayerId =
      typeof payload.toPlayerId === "string"
        ? payload.toPlayerId
        : "";
    const target = state.players.find(
      (candidate) =>
        candidate?.playerId === targetPlayerId &&
        !Number.isSafeInteger(candidate.forfeitedAt),
    );
    const signal = payload.signal;
    const signalType = signal?.type;
    if (
      targetPlayerId === playerId ||
      !target ||
      !this.#isVoicePlayerJoined(targetPlayerId) ||
      !signal ||
      typeof signal !== "object" ||
      !(
        (signalType === "offer" || signalType === "answer") &&
        typeof signal.sdp === "string" &&
        signal.sdp.length > 0 &&
        signal.sdp.length <= MAX_VOICE_SIGNAL_LENGTH
      ) &&
      !(
        signalType === "candidate" &&
        typeof signal.candidate === "string" &&
        signal.candidate.length > 0 &&
        signal.candidate.length <= 2_048 &&
        (signal.sdpMid == null || typeof signal.sdpMid === "string") &&
        (signal.sdpMLineIndex == null ||
          Number.isInteger(signal.sdpMLineIndex))
      )
    ) {
      return;
    }

    const outgoing = JSON.stringify({
      type: "VOICE_SIGNAL",
      matchId: state.matchId,
      fromPlayerId: playerId,
      signal,
    });
    for (const recipient of this.#voiceSockets(targetPlayerId)) {
      try {
        recipient.send(outgoing);
      } catch {
        try {
          recipient.close(1011, "voice signal delivery failed");
        } catch {
          // Socket is already closing.
        }
      }
    }
  }

  #socketPlayerId(socket) {
    const tags =
      typeof this.ctx.getTags === "function"
        ? this.ctx.getTags(socket)
        : [];
    const playerTag = Array.isArray(tags)
      ? tags.find(
          (tag) =>
            typeof tag === "string" &&
            tag.startsWith("player:"),
        )
      : null;
    return playerTag?.slice("player:".length) || null;
  }

  #setVoiceJoined(socket, joined) {
    try {
      const attachment = socket.deserializeAttachment?.() ?? {};
      socket.serializeAttachment?.({
        ...attachment,
        voiceJoined: joined,
      });
    } catch {
      // A socket that is already closing cannot rejoin voice.
    }
  }

  #isVoiceJoined(socket) {
    try {
      return socket.deserializeAttachment?.()?.voiceJoined === true;
    } catch {
      return false;
    }
  }

  #isVoicePlayerJoined(playerId) {
    return this.#voiceSockets(playerId).length > 0;
  }

  #voiceSockets(playerId) {
    if (typeof this.ctx.getWebSockets !== "function") return [];
    return this.ctx.getWebSockets().filter(
      (socket) =>
        this.#socketPlayerId(socket) === playerId &&
        this.#isVoiceJoined(socket),
    );
  }

  #broadcastVoicePeers(state) {
    if (typeof this.ctx.getWebSockets !== "function") return;
    const sockets = this.ctx.getWebSockets();
    if (
      state.matchMode !== "FRIENDS" ||
      state.status !== "ACTIVE" ||
      !Array.isArray(state.players)
    ) {
      for (const socket of sockets) {
        if (!this.#isVoiceJoined(socket)) continue;
        try {
          socket.send(JSON.stringify({ type: "VOICE_ENDED" }));
        } catch {
          // Socket is already closing.
        }
        this.#setVoiceJoined(socket, false);
      }
      return;
    }

    const playerIds = [...new Set(
      sockets
        .filter((socket) => this.#isVoiceJoined(socket))
        .map((socket) => this.#socketPlayerId(socket))
        .filter((playerId) =>
          state.players.some(
            (player) =>
              player?.playerId === playerId &&
              !Number.isSafeInteger(player.forfeitedAt),
          ),
        ),
    )];
    for (const socket of sockets) {
      if (!this.#isVoiceJoined(socket)) continue;
      const playerId = this.#socketPlayerId(socket);
      try {
        socket.send(JSON.stringify({
          type: "VOICE_PEERS",
          playerIds: playerIds.filter((id) => id !== playerId),
        }));
      } catch {
        // Socket is already closing.
      }
    }
  }

  async #handleQuickChat(socket, payload) {
    const emoji = payload?.emoji;
    if (typeof emoji !== "string" || !QUICK_CHAT_EMOJIS.has(emoji)) return;

    // Player identity comes only from the authenticated WebSocket's server-side tag.
    const tags =
      typeof this.ctx.getTags === "function"
        ? this.ctx.getTags(socket)
        : [];
    const playerTag =
      Array.isArray(tags)
        ? tags.find((tag) => typeof tag === "string" && tag.startsWith("player:"))
        : null;
    if (!playerTag) return;
    const playerId = playerTag.slice("player:".length);
    if (!playerId) return;

    const state = await this.ctx.storage.get(STATE_KEY);
    if (!state || state.status !== "ACTIVE" || !Array.isArray(state.players)) return;
    const player = state.players.find((candidate) => candidate?.playerId === playerId);
    if (!player || Number.isSafeInteger(player.forfeitedAt)) return;

    const now = Date.now();
    const cooldownKey = "quick-chat:last:" + playerId;
    const lastSentAt = await this.ctx.storage.get(cooldownKey);
    if (
      Number.isSafeInteger(lastSentAt) &&
      now - lastSentAt < QUICK_CHAT_COOLDOWN_MS
    ) {
      return;
    }
    await this.ctx.storage.put(cooldownKey, now);

    const outgoing = JSON.stringify({
      type: "QUICK_CHAT",
      matchId: state.matchId,
      playerId: player.playerId,
      displayName: player.displayName,
      emoji,
      sentAt: now,
    });
    if (typeof this.ctx.getWebSockets !== "function") return;
    for (const target of this.ctx.getWebSockets()) {
      // Sender already presents the reaction locally after the WebSocket accepts the send.
      if (target === socket) continue;
      try {
        target.send(outgoing);
      } catch {
        try {
          target.close(1011, "quick chat delivery failed");
        } catch {
          // Socket is already closing.
        }
      }
    }
  }

  async webSocketClose(
    socket,
    code,
    reason,
  ) {
    const wasVoiceJoined = this.#isVoiceJoined(socket);
    this.#setVoiceJoined(socket, false);
    try {
      socket.close(
        code,
        reason,
      );
    } catch {
      // Socket is already closed.
    }
    if (wasVoiceJoined) {
      const state = await this.ctx.storage.get(STATE_KEY);
      if (state) this.#broadcastVoicePeers(state);
    }
  }

  async webSocketError(
    socket,
  ) {
    try {
      socket.close(
        1011,
        "realtime socket error",
      );
    } catch {
      // Socket is already closed.
    }
  }

  async #resolveLeaderboardProfile(
    request,
    {
      matchId,
      clientRequestId,
      matchMode,
    },
  ) {
    if (matchMode === "FRIENDS") {
      return { profileId: null, playerType: "HUMAN" };
    }

    const assertion = request.headers.get("x-ludoproof-profile-assertion");
    if (assertion) {
      const claims = await verifyLeaderboardProfileAssertion(this.env, assertion);
      if (
        claims.matchId !== matchId ||
        claims.clientRequestId !== clientRequestId
      ) {
        throw httpError(
          403,
          "PROFILE_ASSERTION_MISMATCH",
          "leaderboard profile assertion is not valid for this match request",
        );
      }
      return {
        profileId: claims.profileId,
        playerType: claims.playerType ?? "HUMAN",
      };
    }

    const authorization = request.headers.get("authorization");
    if (authorization == null || authorization.trim() === "") {
      return { profileId: null, playerType: "HUMAN" };
    }
    if (!/^Bearer\s+lpp_[A-Za-z0-9_-]{32,}$/i.test(authorization)) {
      throw httpError(
        401,
        "PROFILE_AUTH_INVALID",
        "leaderboard profile credential is invalid",
      );
    }
    if (!this.env.LUDOPROOF_LEADERBOARD) {
      throw httpError(
        503,
        "LEADERBOARD_NOT_CONFIGURED",
        "leaderboard storage is not configured",
      );
    }

    const id = this.env.LUDOPROOF_LEADERBOARD.idFromName("global");
    const target = this.env.LUDOPROOF_LEADERBOARD.get(id);
    const response = await target.fetch(
      new Request("https://leaderboard/profile/identity", {
        method: "GET",
        headers: { authorization },
      }),
    );

    let value;
    try {
      value = await response.json();
    } catch {
      throw httpError(
        503,
        "PROFILE_AUTH_BAD_RESPONSE",
        "leaderboard profile authorization returned invalid data",
      );
    }

    const profileId = normalizeLeaderboardProfileId(value?.profileId);
    if (!response.ok || value?.ok !== true || profileId == null) {
      throw httpError(
        response.status === 401 ? 401 : 403,
        value?.error ?? "PROFILE_AUTH_INVALID",
        value?.message ?? "leaderboard profile credential is invalid",
      );
    }

    return { profileId, playerType: "HUMAN" };
  }

  async #assertFriendHost(
    request,
    body,
  ) {
    const state =
      await this.#requireState();
    const player =
      await this.#authorize(
        state,
        request,
      );
    const hostFriendId =
      String(
        body?.hostFriendId ??
          "",
      )
        .trim()
        .toUpperCase();

    if (
      !/^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/
        .test(
          hostFriendId,
        )
    ) {
      throw httpError(
        400,
        "INVALID_FRIEND_ID",
        "invalid host Friend ID",
      );
    }
    if (
      state.matchMode !==
        "FRIENDS"
    ) {
      throw httpError(
        409,
        "NOT_FRIEND_ROOM",
        "room is not a friends private room",
      );
    }
    if (
      state.status !==
        "WAITING"
    ) {
      throw httpError(
        409,
        "ROOM_NOT_WAITING",
        "room is no longer accepting invites",
      );
    }
    if (
      player.playerId !==
        state.hostPlayerId
    ) {
      throw httpError(
        403,
        "HOST_ONLY",
        "only the private room host can invite friends",
      );
    }
    if (
      state.hostFriendId !==
        hostFriendId ||
      player.friendId !==
        hostFriendId
    ) {
      throw httpError(
        403,
        "FRIEND_HOST_MISMATCH",
        "room host credential is not bound to this Friend ID",
      );
    }

    return json(
      200,
      {
        ok: true,
        matchId:
          state.matchId,
      },
    );
  }

  async #friendIdentity(
    request,
  ) {
    const authorization =
      request.headers.get(
        "authorization",
      );
    if (
      typeof authorization !==
        "string" ||
      !/^Bearer\s+lf_[A-Za-z0-9_-]{32,}$/i
        .test(
          authorization,
        )
    ) {
      throw httpError(
        401,
        "FRIEND_AUTH_REQUIRED",
        "friend credential is required to create a private room",
      );
    }
    if (
      !this.env
        .LUDOPROOF_FRIENDS
    ) {
      throw httpError(
        503,
        "FRIENDS_NOT_CONFIGURED",
        "friend directory is not configured",
      );
    }

    const id =
      this.env
        .LUDOPROOF_FRIENDS
        .idFromName(
          "GLOBAL:V1",
        );
    const target =
      this.env
        .LUDOPROOF_FRIENDS
        .get(id);
    const response =
      await target.fetch(
        new Request(
          "https://friends/identity",
          {
            method:
              "GET",
            headers: {
              authorization,
            },
          },
        ),
      );

    let value;
    try {
      value =
        await response.json();
    } catch {
      throw httpError(
        503,
        "FRIEND_AUTH_BAD_RESPONSE",
        "friend directory returned invalid authorization data",
      );
    }

    if (
      !response.ok ||
      value?.ok !== true ||
      !/^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/
        .test(
          String(
            value?.friendId ??
              "",
          ),
        )
    ) {
      throw httpError(
        response.status ===
          401
          ? 401
          : 403,
        value?.error ??
          "FRIEND_AUTH_INVALID",
        value?.message ??
          "friend credential is invalid",
      );
    }

    return {
      friendId:
        String(
          value.friendId,
        ),
      displayName:
        String(
          value.displayName ??
            "",
        ),
    };
  }

  async #assertFriendInviteActive(
    claims,
  ) {
    if (
      !this.env
        .LUDOPROOF_FRIENDS
    ) {
      throw httpError(
        503,
        "FRIENDS_NOT_CONFIGURED",
        "friend directory is not configured",
      );
    }

    const id =
      this.env
        .LUDOPROOF_FRIENDS
        .idFromName(
          "GLOBAL:V1",
        );
    const target =
      this.env
        .LUDOPROOF_FRIENDS
        .get(id);
    const response =
      await target.fetch(
        new Request(
          "https://friends/invite/join-check",
          {
            method:
              "POST",
            headers: {
              "content-type":
                "application/json",
            },
            body:
              JSON.stringify(
                claims,
              ),
          },
        ),
      );

    let value;
    try {
      value =
        await response.json();
    } catch {
      throw httpError(
        503,
        "FRIEND_INVITE_BAD_RESPONSE",
        "friend directory returned invalid invite authorization data",
      );
    }

    if (
      !response.ok ||
      value?.ok !== true
    ) {
      throw httpError(
        response.status,
        value?.error ??
          "FRIEND_INVITE_INVALID",
        value?.message ??
          "friend invite is not authorized for this room",
      );
    }
  }

  async #authorize(state, request) {
    const token = bearerToken(request);
    const digest = await sha256Hex(
      "ludoproof:player-token:v1:" + token,
    );
    const player = state.players.find(
      (candidate) => candidate.tokenAuthHash === digest,
    );
    if (!player) {
      throw httpError(403, "AUTH_INVALID", "player token is invalid for this match");
    }
    return player;
  }

  async #requireState() {
    let state = await this.ctx.storage.get(STATE_KEY);
    if (!state) throw httpError(404, "MATCH_NOT_FOUND", "match does not exist");
    if (!Array.isArray(state.history)) state.history = [];

    const expired =
      await this.#expirePendingIfNeeded(
        state,
        Date.now(),
      );
    state = expired.state;
    if (expired.changed) {
      await this.#persist(state);
    }

    // Apply the same deadline contract before a gameplay action is validated,
    // not just when the scheduled alarm happens to win the race.
    const turnExpired = this.#expireTurnIfNeeded(state, Date.now());
    if (turnExpired.changed) {
      state = turnExpired.state;
      await this.#persist(state);
      await this.#recordFinishedMatchIfNeeded(state);
    }
    return state;
  }

  #expireTurnIfNeeded(state, now) {
    if (state.status !== "ACTIVE" || state.pendingRoll != null) {
      return { state, changed: false };
    }

    const ruleset = rulesetForState(state);
    const timeoutMs = ruleset.turnRollTimeoutMs;
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) {
      return { state, changed: false };
    }

    // Safely initialize V4 matches restored from an older persisted schema.
    // Use actingSeatForTurn, because a Team Up turn can be handed to a partner.
    if (!Number.isSafeInteger(state.turnDeadlineAt)) {
      const next = structuredClone(state);
      const actingSeat = actingSeatForTurn(next);
      if (
        !Number.isInteger(actingSeat) ||
        actingSeat < 0 ||
        actingSeat >= next.players.length
      ) {
        return { state, changed: false };
      }
      next.turnDeadlineSeat = actingSeat;
      next.turnDeadlineAt = now + timeoutMs;
      next.updatedAt = now;
      next.revision = (Number.isSafeInteger(next.revision) ? next.revision : 0) + 1;
      return { state: next, changed: true };
    }

    if (now < state.turnDeadlineAt) {
      return { state, changed: false };
    }

    const result = forfeitTimedOutTurn(state, { now });
    return { state: result.state, changed: true };
  }

  async #expirePendingIfNeeded(
    state,
    now,
    reason = "REVEAL_TIMEOUT",
  ) {
    const pending = state.pendingRoll;
    if (
      !pending ||
      !["CREATING", "COMMITTED", "RESOLVING"]
        .includes(pending.status) ||
      !Number.isSafeInteger(
        pending.revealDeadlineAt,
      ) ||
      now < pending.revealDeadlineAt
    ) {
      return {
        state,
        changed: false,
      };
    }

    const result = forfeitTimedOutRoll(
      state,
      {
        now,
        reason,
      },
    );
    const timedOut =
      result.timedOutRoll;
    const player =
      state.players[timedOut.seat];

    const fairnessEvent =
      recordedRollEvent(
        state.history ?? [],
        {
          eventIndex: timedOut.eventIndex,
          eventId: timedOut.eventId,
          playerId: timedOut.playerId,
          color: player?.color ?? null,
          roundId: timedOut.roundId,
          serverCommitment:
            timedOut.serverCommitment,
          clientCommitment:
            timedOut.clientCommitment,
          actorHash: timedOut.actorHash,
          previousStateHash:
            timedOut.previousStateHash,
          rulesetHash:
            timedOut.rulesetHash,
          proofDigest: null,
          outcome: null,
          moveTokenIndex: null,
          captures: 0,
          status: "TIMED_OUT",
          timeoutReason:
            timedOut.timeoutReason,
          timedOutAt:
            timedOut.timedOutAt,
          replacementRoundAllowed: false,
        },
      );
    result.state.history = [
      ...(state.history ?? []),
      fairnessEvent,
    ].slice(-64);

    return {
      state: result.state,
      changed: true,
    };
  }
}


// Preserve existing named export for all callers.
export { rulesetHashForState } from "./match-room-state.js";
