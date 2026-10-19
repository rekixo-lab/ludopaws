import {
  deterministicMatchId,
  httpError,
  issueLeaderboardProfileAssertion,
  normalizeDisplayName,
  requireClientRequestId,
} from "./crypto.js";
import {
  normalizeCharacterId,
} from "./ludo-paws-characters.js";
import {
  CHARACTER_PREFERENCE_WINDOW_MS,
  characterPreferenceKey,
  distinctCharacterCount,
  selectCharacterDiverseGroup,
} from "./matchmaking-characters.js";
import {
  botFillDelayMs,
  botMatchFillEnabled,
  botRetryDelayMs,
  createBotParticipants,
} from "./matchmaking-bots.js";
import { MATCH_LOCATION_HEADER, normalizeMatchLocationHint } from "./match-location.js";

const STATE_KEY = "matchmaking-state";
const QUEUE_TTL_MS = 2 * 60 * 1000;
const ASSIGNMENT_TTL_MS = 10 * 60 * 1000;
const PROFILE_ASSERTION_TTL_MS = 15 * 60 * 1000;
const ALLOWED_PLAYER_COUNTS = new Set([2, 4]);
const DEFAULT_RULESET_KEY = "CLASSIC_V4";
const CLASSIC_RULESET_KEYS = ["CLASSIC_V1", "CLASSIC_V2", "CLASSIC_V3", "CLASSIC_V4"];

export class MatchmakerQueue {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.mutationTail = Promise.resolve();
  }

  async fetch(request) {
    try {
      const url = new URL(request.url);
      const body = request.method === "POST" ? await readJson(request) : null;
      if (request.method === "POST" && url.pathname === "/search") {
        return await this.#mutate(() => this.#search(request, body));
      }
      if (request.method === "POST" && url.pathname === "/status") {
        return await this.#mutate(() => this.#status(request, body));
      }
      if (request.method === "POST" && url.pathname === "/cancel") {
        return await this.#mutate(() => this.#cancel(request, body));
      }
      return json(404, { error: "NOT_FOUND", message: "route not found" });
    } catch (error) {
      return errorResponse(error);
    }
  }

  async alarm() {
    return this.#mutate(async () => {
      const now = Date.now();
      let state = this.#cleanup(await this.#load(), now);

      if (state.pendingMatch) {
        if (Number(state.pendingMatch.retryAt ?? 0) > now) {
          await this.#persist(state);
          return;
        }
        const completed = await this.#resumePendingMatch(state, now);
        if (!completed) return;
      }

      // Resolve expired character-preference windows before bot fallback. If a
      // diverse human group never arrived, choose the most diverse available
      // group and proceed instead of extending the search indefinitely.
      for (const key of Object.keys(state.characterPreferences ?? {})) {
        state = this.#cleanup(await this.#load(), Date.now());
        if (state.pendingMatch) break;
        const preference = state.characterPreferences?.[key];
        if (!preference || Number(preference.readyAt ?? 0) > Date.now()) continue;
        const eligible = state.queue.filter((candidate) =>
          candidate.playerCount === preference.playerCount &&
          (candidate.rulesetKey ?? DEFAULT_RULESET_KEY) === preference.rulesetKey
        );
        const group = selectCharacterDiverseGroup(eligible, preference.playerCount);
        delete state.characterPreferences[key];
        if (group.length < preference.playerCount) {
          await this.#persist(state);
          continue;
        }
        state.pendingMatch = {
          matchMode: "ONLINE",
          playerCount: preference.playerCount,
          rulesetKey: preference.rulesetKey,
          participants: group,
          retryAt: Date.now() + botRetryDelayMs(this.env),
        };
        await this.#persist(state);
        const completed = await this.#resumePendingMatch(state, Date.now());
        if (!completed) return;
      }

      if (botMatchFillEnabled(this.env)) {
        for (const playerCount of [2, 4]) {
          state = this.#cleanup(await this.#load(), Date.now());
          if (state.pendingMatch) break;
          const byRuleset = new Map();
          state.queue
            .filter((candidate) => candidate.playerCount === playerCount)
            .sort((left, right) => left.joinedAt - right.joinedAt)
            .forEach((candidate) => {
              const rulesetKey = candidate.rulesetKey ?? DEFAULT_RULESET_KEY;
              if (!byRuleset.has(rulesetKey)) byRuleset.set(rulesetKey, []);
              byRuleset.get(rulesetKey).push(candidate);
            });
          const expiredGroup = [...byRuleset.entries()]
            .map(([rulesetKey, waiting]) => ({
              rulesetKey,
              waiting,
              oldest: waiting[0],
              fallbackAt: Number(
                waiting[0].botFallbackAt ??
                waiting[0].joinedAt + botFillDelayMs(this.env),
              ),
            }))
            .filter((group) => group.oldest && group.fallbackAt <= now)
            .sort((left, right) => left.oldest.joinedAt - right.oldest.joinedAt)[0];
          if (!expiredGroup) continue;

          const { rulesetKey, waiting } = expiredGroup;
          const humanCount = Math.min(waiting.length, playerCount);
          const humans = selectCharacterDiverseGroup(waiting, humanCount);
          const bots = await createBotParticipants({
            count: playerCount - humans.length,
            playerCount,
            matchMode: "ONLINE",
            rulesetKey,
            existingNames: humans.map((candidate) => candidate.displayName),
            existingCharacterIds: humans.map((candidate) => candidate.characterId),
            now,
          });
          state.pendingMatch = {
            matchMode: "ONLINE",
            playerCount,
            rulesetKey,
            participants: [...humans, ...bots],
            retryAt: now + botRetryDelayMs(this.env),
          };
          await this.#persist(state);
          const completed = await this.#resumePendingMatch(state, now);
          if (!completed) return;
        }
      }

      state = this.#cleanup(await this.#load(), Date.now());
      await this.#persist(state);
    });
  }

  #mutate(work) {
    const run = this.mutationTail.then(work, work);
    this.mutationTail = run.catch(() => {});
    return run;
  }

  async #search(request, body) {
    const profile = await this.#profileIdentity(request);
    const participant = normalizeParticipant(body, profile.profileId);
    participant.locationHint = normalizeMatchLocationHint(request.headers.get(MATCH_LOCATION_HEADER));
    const now = Date.now();
    let state = this.#cleanup(await this.#load(), now);

    const assigned = state.assignments[participant.clientRequestId];
    if (assigned) {
      return await this.#matchedResponse(
        {
          ...participant,
          characterId: normalizeCharacterId(assigned.characterId ?? participant.characterId),
        },
        assigned,
      );
    }

    const finalizing = finalizingPayload(
      state,
      participant.clientRequestId,
      participant.profileId,
      participant.playerCount,
      now,
    );
    if (finalizing) {
      await this.#persist(state);
      return json(202, finalizing);
    }

    const existingIndex = state.queue.findIndex(
      (candidate) => candidate.clientRequestId === participant.clientRequestId,
    );
    if (existingIndex >= 0) {
      const existing = state.queue[existingIndex];
      if (existing.profileId !== participant.profileId) {
        throw httpError(
          403,
          "MATCHMAKING_PROFILE_MISMATCH",
          "matchmaking request belongs to another authenticated profile",
        );
      }
      state.queue[existingIndex] = {
        ...existing,
        displayName: participant.displayName,
        profileId: participant.profileId,
        characterId: participant.characterId,
        rulesetKey: participant.rulesetKey,
        lastSeenAt: now,
      };
      await this.#persist(state);
      return json(200, searchingPayload(state, participant.playerCount, now, participant.rulesetKey));
    }

    if (
      participant.profileId != null &&
      state.queue.some((candidate) => candidate.profileId === participant.profileId)
    ) {
      throw httpError(
        409,
        "PROFILE_ALREADY_QUEUED",
        "this profile is already searching",
      );
    }

    state.queue.push({
      ...participant,
      joinedAt: now,
      lastSeenAt: now,
    });

    const eligible = state.queue
      .filter((candidate) =>
        candidate.playerCount === participant.playerCount &&
        (candidate.rulesetKey ?? DEFAULT_RULESET_KEY) === participant.rulesetKey
      )
      .sort((left, right) => left.joinedAt - right.joinedAt);

    if (eligible.length >= participant.playerCount && state.pendingMatch) {
      // A previous reservation is being recovered. Persist the preference for
      // this newly-complete group so it is resolved as soon as that reservation
      // finishes rather than waiting for the normal bot-fill timer.
      const group = selectCharacterDiverseGroup(eligible, participant.playerCount);
      const key = characterPreferenceKey(participant.playerCount, participant.rulesetKey);
      const preferences = state.characterPreferences ?? {};
      const hasUniqueCharacters =
        group.length === participant.playerCount &&
        distinctCharacterCount(group) === participant.playerCount;
      const existing = preferences[key];
      if (!existing || (hasUniqueCharacters && Number(existing.readyAt ?? 0) > now)) {
        preferences[key] = {
          playerCount: participant.playerCount,
          rulesetKey: participant.rulesetKey,
          startedAt: existing?.startedAt ?? now,
          readyAt: hasUniqueCharacters ? now : now + CHARACTER_PREFERENCE_WINDOW_MS,
        };
      }
      state.characterPreferences = preferences;
      await this.#persist(state);
      return json(202, searchingPayload(
        state, participant.playerCount, now, participant.rulesetKey,
      ));
    }

    if (
      eligible.length >= participant.playerCount &&
      !state.pendingMatch
    ) {
      const group = selectCharacterDiverseGroup(eligible, participant.playerCount);
      const preferenceKey = characterPreferenceKey(
        participant.playerCount,
        participant.rulesetKey,
      );
      const preferences = state.characterPreferences ?? {};
      const preference = preferences[preferenceKey];
      const hasUniqueCharacters =
        group.length === participant.playerCount &&
        distinctCharacterCount(group) === participant.playerCount;

      if (
        !hasUniqueCharacters &&
        preference &&
        Number(preference.readyAt ?? 0) > now
      ) {
        await this.#persist(state);
        return json(202, searchingPayload(
          state, participant.playerCount, now, participant.rulesetKey,
        ));
      }

      if (!hasUniqueCharacters && !preference) {
        preferences[preferenceKey] = {
          playerCount: participant.playerCount,
          rulesetKey: participant.rulesetKey,
          startedAt: now,
          readyAt: now + CHARACTER_PREFERENCE_WINDOW_MS,
        };
        state.characterPreferences = preferences;
        await this.#persist(state);
        return json(202, searchingPayload(
          state, participant.playerCount, now, participant.rulesetKey,
        ));
      }

      delete preferences[preferenceKey];
      state.characterPreferences = preferences;
      state.pendingMatch = {
        matchMode: "ONLINE",
        playerCount: participant.playerCount,
        rulesetKey: participant.rulesetKey,
        participants: group,
        retryAt: now + botRetryDelayMs(this.env),
      };
      await this.#persist(state);
      const completed = await this.#resumePendingMatch(state, now);
      if (!completed) {
        throw httpError(
          503,
          "MATCHMAKER_RECOVERY_PENDING",
          "match setup is being retried; keep searching",
        );
      }

      const ownAssignment = state.assignments[participant.clientRequestId];
      if (!ownAssignment) {
        throw httpError(
          503,
          "MATCHMAKER_ASSIGNMENT_MISSING",
          "match was created but the player assignment is not available yet",
        );
      }
      return await this.#matchedResponse(
        {
          ...participant,
          characterId: normalizeCharacterId(ownAssignment.characterId),
        },
        ownAssignment,
      );
    }

    await this.#persist(state);
    return json(202, searchingPayload(state, participant.playerCount, now, participant.rulesetKey));
  }

  async #status(request, body) {
    const profile = await this.#profileIdentity(request);
    const clientRequestId = requireClientRequestId(body?.clientRequestId);
    const playerCount = requirePlayerCount(body?.playerCount);
    const now = Date.now();
    const state = this.#cleanup(await this.#load(), now);

    const assignment = state.assignments[clientRequestId];
    if (assignment) {
      if (assignment.profileId !== profile.profileId) {
        throw httpError(
          403,
          "MATCHMAKING_PROFILE_MISMATCH",
          "matchmaking request belongs to another authenticated profile",
        );
      }
      const participant = {
        clientRequestId,
        playerCount,
        displayName: assignment.displayName,
        characterId: normalizeCharacterId(assignment.characterId),
        profileId: assignment.profileId,
      };
      await this.#persist(state);
      return await this.#matchedResponse(participant, assignment);
    }

    const finalizing = finalizingPayload(
      state,
      clientRequestId,
      profile.profileId,
      playerCount,
      now,
    );
    if (finalizing) {
      await this.#persist(state);
      return json(200, finalizing);
    }

    const queued = state.queue.find(
      (candidate) => candidate.clientRequestId === clientRequestId,
    );
    if (!queued) {
      await this.#persist(state);
      return json(200, {
        status: "IDLE",
        playerCount,
        queuedPlayers: 0,
        targetPlayerCount: playerCount,
      });
    }
    if (queued.profileId !== profile.profileId) {
      throw httpError(
        403,
        "MATCHMAKING_PROFILE_MISMATCH",
        "matchmaking request belongs to another authenticated profile",
      );
    }

    queued.lastSeenAt = now;
    await this.#persist(state);
    return json(200, searchingPayload(
      state,
      playerCount,
      now,
      queued.rulesetKey ?? DEFAULT_RULESET_KEY,
    ));
  }

  async #cancel(request, body) {
    const profile = await this.#profileIdentity(request);
    const clientRequestId = requireClientRequestId(body?.clientRequestId);
    const playerCount = requirePlayerCount(body?.playerCount);
    const now = Date.now();
    const state = this.#cleanup(await this.#load(), now);

    const assignment = state.assignments[clientRequestId];
    if (assignment) {
      if (assignment.profileId !== profile.profileId) {
        throw httpError(
          403,
          "MATCHMAKING_PROFILE_MISMATCH",
          "matchmaking request belongs to another authenticated profile",
        );
      }
      await this.#persist(state);
      return json(200, {
        status: "MATCHED",
        cancelled: false,
        playerCount,
        matchId: assignment.matchId,
      });
    }

    const reserved = state.pendingMatch?.participants.some(
      (candidate) =>
        candidate.playerType !== "BOT" &&
        candidate.clientRequestId === clientRequestId &&
        candidate.profileId === profile.profileId,
    );
    if (reserved) {
      await this.#persist(state);
      return json(200, {
        status: "MATCHED",
        cancelled: false,
        pending: true,
        playerCount,
      });
    }

    const queued = state.queue.find(
      (candidate) => candidate.clientRequestId === clientRequestId,
    );
    if (queued && queued.profileId !== profile.profileId) {
      throw httpError(
        403,
        "MATCHMAKING_PROFILE_MISMATCH",
        "matchmaking request belongs to another authenticated profile",
      );
    }

    const before = state.queue.length;
    state.queue = state.queue.filter(
      (candidate) => candidate.clientRequestId !== clientRequestId,
    );
    await this.#persist(state);
    return json(200, {
      status: "CANCELLED",
      cancelled: state.queue.length < before,
      playerCount,
    });
  }

  async #materialize(participants, targetPlayerCount, reservation = {}) {
    const host = participants[0];
    for (let attempt = 0; attempt < 4; attempt += 1) {
      const matchId = reservation.matchId
        ?? await deterministicMatchId(host.clientRequestId, attempt);
      const target = room(this.env, matchId, host.locationHint);
      const createResponse = await target.fetch(
        new Request("https://room/create", {
          method: "POST",
          headers: {
            "content-type": "application/json",
            "x-ludoproof-profile-assertion": await issueLeaderboardProfileAssertion(
              this.env,
              {
                matchId,
                profileId: host.profileId,
                playerType: host.playerType ?? "HUMAN",
                clientRequestId: host.clientRequestId,
                expiresAt: Date.now() + PROFILE_ASSERTION_TTL_MS,
              },
            ),
          },
          body: JSON.stringify({
            matchId,
            displayName: host.displayName,
            characterId: normalizeCharacterId(host.characterId),
            clientRequestId: host.clientRequestId,
            matchmaking: true,
            targetPlayerCount,
            rulesetKey: host.rulesetKey ?? reservation.rulesetKey ?? DEFAULT_RULESET_KEY,
          }),
        }),
      );
      if (createResponse.status === 409) {
        if (reservation.matchId) {
          throw httpError(
            503,
            "MATCH_RESERVED_ID_CONFLICT",
            "reserved match ID no longer belongs to its reservation",
          );
        }
        continue;
      }
      const created = await requireJsonResponse(createResponse);
      if (!reservation.matchId) {
        reservation.matchId = matchId;
        if (reservation.onMatchReserved) await reservation.onMatchReserved(matchId);
      }

      for (let index = 1; index < participants.length; index += 1) {
        const participant = participants[index];
        const joinResponse = await target.fetch(
          new Request("https://room/join", {
            method: "POST",
            headers: {
              "content-type": "application/json",
              "x-ludoproof-profile-assertion": await issueLeaderboardProfileAssertion(
                this.env,
                {
                  matchId,
                  profileId: participant.profileId,
                  playerType: participant.playerType ?? "HUMAN",
                  clientRequestId: participant.clientRequestId,
                  expiresAt: Date.now() + PROFILE_ASSERTION_TTL_MS,
                },
              ),
            },
            body: JSON.stringify({
              displayName: participant.displayName,
              characterId: normalizeCharacterId(participant.characterId),
              clientRequestId: participant.clientRequestId,
            }),
          }),
        );
        await requireJsonResponse(joinResponse);
      }

      const startResponse = await target.fetch(
        new Request("https://room/start", {
          method: "POST",
          headers: {
            "content-type": "application/json",
            authorization: "Bearer " + created.playerToken,
          },
          body: JSON.stringify({}),
        }),
      );
      await requireJsonResponse(startResponse);
      return { matchId };
    }

    throw httpError(
      503,
      "MATCH_ID_EXHAUSTED",
      "could not allocate a public match",
    );
  }

  async #resumePendingMatch(state, now) {
    const pending = state.pendingMatch;
    if (!pending) return true;

    let assignment;
    try {
      assignment = await this.#materialize(
        pending.participants,
        pending.playerCount,
        {
          matchId: pending.matchId,
          onMatchReserved: async (matchId) => {
            pending.matchId = matchId;
            state.pendingMatch = pending;
            await this.#persist(state);
          },
        },
      );
    } catch (error) {
      pending.retryAt = Date.now() + botRetryDelayMs(this.env);
      state.pendingMatch = pending;
      await this.#persist(state);
      console.error("Online reserved match could not be materialized; retry scheduled", {
        code: error?.code ?? "MATCH_MATERIALIZE_FAILED",
      });
      return false;
    }

      const realParticipants = pending.participants.filter(
        (candidate) => candidate.playerType !== "BOT",
      );
      const realRequestIds = new Set(
        realParticipants.map((candidate) => candidate.clientRequestId),
      );
      state.queue = state.queue.filter(
        (candidate) => !realRequestIds.has(candidate.clientRequestId),
      );

      const assignedAt = Date.now();
      pending.participants.forEach((candidate, index) => {
        if (candidate.playerType === "BOT") return;
        state.assignments[candidate.clientRequestId] = {
          matchId: assignment.matchId,
          targetPlayerCount: pending.playerCount,
          role: index === 0 ? "HOST" : "GUEST",
          assignedAt,
          expiresAt: assignedAt + ASSIGNMENT_TTL_MS,
          displayName: candidate.displayName,
          characterId: normalizeCharacterId(candidate.characterId),
          profileId: candidate.profileId,
          rulesetKey: candidate.rulesetKey ?? DEFAULT_RULESET_KEY,
        };
      });
      delete state.pendingMatch;
      await this.#persist(state);
      return true;
  }

  async #matchedResponse(participant, assignment) {
    if (assignment.profileId !== participant.profileId) {
      throw httpError(
        403,
        "MATCHMAKING_PROFILE_MISMATCH",
        "matchmaking request belongs to another authenticated profile",
      );
    }
    if (assignment.targetPlayerCount !== participant.playerCount) {
      throw httpError(
        409,
        "MATCHMAKING_COUNT_MISMATCH",
        "assigned match uses a different player count",
      );
    }

    const target = room(this.env, assignment.matchId);
    const host = assignment.role === "HOST";
    const body = {
      displayName: participant.displayName,
      characterId: normalizeCharacterId(
        assignment.characterId ?? participant.characterId,
      ),
      clientRequestId: participant.clientRequestId,
      ...(host
        ? {
            matchId: assignment.matchId,
            matchmaking: true,
            targetPlayerCount: assignment.targetPlayerCount,
            rulesetKey: assignment.rulesetKey ?? participant.rulesetKey ?? DEFAULT_RULESET_KEY,
          }
        : {}),
    };
    const response = await target.fetch(
      new Request("https://room/" + (host ? "create" : "join"), {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-ludoproof-profile-assertion": await issueLeaderboardProfileAssertion(
            this.env,
            {
              matchId: assignment.matchId,
              profileId: participant.profileId,
              playerType: participant.playerType ?? "HUMAN",
              clientRequestId: participant.clientRequestId,
              expiresAt: Date.now() + PROFILE_ASSERTION_TTL_MS,
            },
          ),
        },
        body: JSON.stringify(body),
      }),
    );
    const session = await requireJsonResponse(response);
    return json(200, {
      status: "MATCHED",
      targetPlayerCount: assignment.targetPlayerCount,
      matchId: assignment.matchId,
      playerId: session.playerId,
      playerToken: session.playerToken,
      state: session.state,
    });
  }

  async #profileIdentity(request) {
    const authorization = request.headers.get("authorization");
    if (authorization == null || authorization.trim() === "") {
      return { profileId: null };
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

    if (
      !response.ok ||
      value?.ok !== true ||
      typeof value?.profileId !== "string" ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(
        value.profileId,
      )
    ) {
      throw httpError(
        response.status === 401 ? 401 : 403,
        value?.error ?? "PROFILE_AUTH_INVALID",
        value?.message ?? "leaderboard profile credential is invalid",
      );
    }
    return { profileId: value.profileId };
  }

  async #load() {
    return (
      (await this.ctx.storage.get(STATE_KEY)) ?? {
        schemaVersion: 1,
        queue: [],
        assignments: {},
        characterPreferences: {},
      }
    );
  }

  #cleanup(state, now) {
    const next = structuredClone(state);
    next.queue = (next.queue ?? []).filter(
      (participant) =>
        Number(participant.lastSeenAt ?? participant.joinedAt ?? 0) + QUEUE_TTL_MS > now,
    );
    const assignments = next.assignments ?? {};
    for (const [requestId, assignment] of Object.entries(assignments)) {
      if (Number(assignment.expiresAt ?? 0) <= now) {
        delete assignments[requestId];
      }
    }
    next.assignments = assignments;
    const preferences = next.characterPreferences ?? {};
    for (const [key, preference] of Object.entries(preferences)) {
      const playerCount = Number(preference?.playerCount);
      const rulesetKey = String(preference?.rulesetKey ?? DEFAULT_RULESET_KEY);
      const eligibleCount = next.queue.filter((candidate) =>
        candidate.playerCount === playerCount &&
        (candidate.rulesetKey ?? DEFAULT_RULESET_KEY) === rulesetKey
      ).length;
      if (!Number.isInteger(playerCount) || eligibleCount < playerCount) {
        delete preferences[key];
      }
    }
    next.characterPreferences = preferences;
    return next;
  }

  async #persist(state) {
    await this.ctx.storage.put(STATE_KEY, state);
    if (typeof this.ctx.storage.setAlarm !== "function") return;
    const deadlines = [
      ...(state.pendingMatch
        ? [Number(state.pendingMatch.retryAt ?? Date.now() + botRetryDelayMs(this.env))]
        : []),
      ...Object.values(state.characterPreferences ?? {}).map(
        (preference) => Number(preference.readyAt ?? Date.now()),
      ),
      ...state.queue.flatMap((participant) => {
        const queueExpiry = Number(
          participant.lastSeenAt ?? participant.joinedAt ?? Date.now(),
        ) + QUEUE_TTL_MS;
        const values = [queueExpiry];
        if (botMatchFillEnabled(this.env) && !state.pendingMatch) {
          const fallbackAt = Number(
            participant.botFallbackAt ?? participant.joinedAt + botFillDelayMs(this.env),
          );
          values.push(Math.max(Date.now() + 1, fallbackAt));
        }
        return values;
      }),
      ...Object.values(state.assignments).map(
        (assignment) => Number(assignment.expiresAt ?? Date.now() + ASSIGNMENT_TTL_MS),
      ),
    ].filter((deadline) => Number.isFinite(deadline) && deadline > Date.now());
    if (deadlines.length > 0) {
      await this.ctx.storage.setAlarm(Math.min(...deadlines));
    } else if (typeof this.ctx.storage.deleteAlarm === "function") {
      await this.ctx.storage.deleteAlarm();
    }
  }
}

function normalizeParticipant(body, profileId) {
  return {
    clientRequestId: requireClientRequestId(body?.clientRequestId),
    displayName: normalizeDisplayName(body?.displayName),
    characterId: normalizeCharacterId(body?.characterId),
    profileId,
    playerCount: requirePlayerCount(body?.playerCount),
    rulesetKey: normalizeClassicRulesetKey(body?.rulesetKey),
    playerType: "HUMAN",
  };
}

function normalizeClassicRulesetKey(value) {
  const key = value == null ? DEFAULT_RULESET_KEY : String(value);
  if (!CLASSIC_RULESET_KEYS.includes(key)) {
    throw httpError(400, "INVALID_RULESET_KEY", "public Online requires a classic ruleset");
  }
  return key;
}

function requirePlayerCount(value) {
  const count = Number(value);
  if (!Number.isInteger(count) || !ALLOWED_PLAYER_COUNTS.has(count)) {
    throw httpError(
      400,
      "INVALID_PLAYER_COUNT",
      "public matchmaking supports 2 or 4 players",
    );
  }
  return count;
}

function searchingPayload(state, playerCount, now, rulesetKey = DEFAULT_RULESET_KEY) {
  const queued = state.queue
    .filter((participant) =>
      participant.playerCount === playerCount &&
      (participant.rulesetKey ?? DEFAULT_RULESET_KEY) === rulesetKey
    )
    .sort((left, right) => left.joinedAt - right.joinedAt);
  return {
    status: "SEARCHING",
    playerCount,
    rulesetKey,
    queuedPlayers: Math.min(queued.length, playerCount),
    targetPlayerCount: playerCount,
    searchStartedAt: queued[0]?.joinedAt ?? now,
  };
}

function finalizingPayload(state, clientRequestId, profileId, playerCount, now) {
  const pending = state.pendingMatch;
  const participant = pending?.participants?.find(
    (candidate) => candidate.clientRequestId === clientRequestId,
  );
  if (!participant || participant.profileId !== profileId) return null;

  const targetPlayerCount = Number(pending.playerCount ?? playerCount);
  if (targetPlayerCount !== playerCount) return null;
  return {
    status: "FINALIZING",
    matchMode: "ONLINE",
    pending: true,
    playerCount: targetPlayerCount,
    targetPlayerCount,
    queuedPlayers: Math.min(pending.participants.length, targetPlayerCount),
    retryAt: Number(pending.retryAt ?? now),
    searchStartedAt: Number(participant.joinedAt ?? now),
  };
}

function room(env, matchId, preferredLocationHint = null) {
  if (!env.LUDOPROOF_MATCHES) {
    throw httpError(
      503,
      "MATCH_STORE_NOT_CONFIGURED",
      "match storage is not configured",
    );
  }
  const id = env.LUDOPROOF_MATCHES.idFromName(matchId);
  const locationHint = normalizeMatchLocationHint(env.LUDOPROOF_MATCH_LOCATION_HINT)
    ?? normalizeMatchLocationHint(preferredLocationHint);
  return locationHint ? env.LUDOPROOF_MATCHES.get(id, { locationHint }) : env.LUDOPROOF_MATCHES.get(id);
}

async function requireJsonResponse(response) {
  let body;
  try {
    body = await response.json();
  } catch {
    throw httpError(
      503,
      "MATCHMAKER_BAD_RESPONSE",
      "match storage returned invalid data",
    );
  }
  if (!response.ok) {
    throw httpError(
      response.status,
      body?.error ?? "MATCHMAKER_MATCH_ERROR",
      body?.message ?? "match storage rejected matchmaking",
    );
  }
  return body;
}

async function readJson(request) {
  const type = request.headers.get("content-type") ?? "";
  if (!type.toLowerCase().startsWith("application/json")) {
    throw httpError(
      415,
      "UNSUPPORTED_MEDIA_TYPE",
      "content-type must be application/json",
    );
  }
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > 8 * 1024) {
    throw httpError(413, "REQUEST_TOO_LARGE", "request body is too large");
  }
  try {
    const value = text.length === 0 ? {} : JSON.parse(text);
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("not object");
    }
    return value;
  } catch {
    throw httpError(400, "INVALID_JSON", "request body must be valid JSON");
  }
}

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function errorResponse(error) {
  const status = Number.isInteger(error?.status) ? error.status : 500;
  return json(status, {
    error: error?.code ?? "INTERNAL_ERROR",
    message:
      status === 500 && !error?.code
        ? "internal server error"
        : String(error?.message ?? "internal server error"),
  });
}
