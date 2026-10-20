import {
  deterministicMatchId,
  httpError,
  issueLeaderboardProfileAssertion,
  normalizeDisplayName,
  requireClientRequestId,
} from "./crypto.js";
import {
  normalizeLeaderboardProfileId,
} from "./leaderboard-core.js";
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

const STATE_KEY = "team-matchmaking-state-v1";
const QUEUE_TTL_MS = 2 * 60 * 1000;
const ASSIGNMENT_TTL_MS = 10 * 60 * 1000;
const PROFILE_ASSERTION_TTL_MS = 15 * 60 * 1000;
const TARGET_PLAYER_COUNT = 4;
const DEFAULT_RULESET_KEY = "TEAM_UP_V4";
const TEAM_RULESET_KEYS = ["TEAM_UP_V1", "TEAM_UP_V2", "TEAM_UP_V3", "TEAM_UP_V4", "TEAM_UP_V5"];

export class TeamMatchmakerQueue {
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

      // Give a four-human group a brief chance to improve character variety.
      // After the deadline, select the best available group and keep moving.
      for (const key of Object.keys(state.characterPreferences ?? {})) {
        state = this.#cleanup(await this.#load(), Date.now());
        if (state.pendingMatch) break;
        const preference = state.characterPreferences?.[key];
        if (!preference || Number(preference.readyAt ?? 0) > Date.now()) continue;
        const eligible = state.queue.filter(
          (candidate) =>
            (candidate.rulesetKey ?? DEFAULT_RULESET_KEY) === preference.rulesetKey,
        );
        const group = selectCharacterDiverseGroup(eligible, TARGET_PLAYER_COUNT);
        delete state.characterPreferences[key];
        if (group.length < TARGET_PLAYER_COUNT) {
          await this.#persist(state);
          continue;
        }
        state.pendingMatch = {
          matchMode: "TEAM_UP",
          playerCount: TARGET_PLAYER_COUNT,
          rulesetKey: preference.rulesetKey,
          participants: group,
          retryAt: Date.now() + botRetryDelayMs(this.env),
        };
        await this.#persist(state);
        const completed = await this.#resumePendingMatch(state, Date.now());
        if (!completed) return;
      }

      if (botMatchFillEnabled(this.env)) {
        state = this.#cleanup(await this.#load(), Date.now());
        if (!state.pendingMatch) {
          const byRuleset = new Map();
          [...state.queue]
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

          if (expiredGroup) {
            const { rulesetKey, waiting } = expiredGroup;
            const humanCount = Math.min(waiting.length, TARGET_PLAYER_COUNT);
            const realPlayers = selectCharacterDiverseGroup(waiting, humanCount);
            const bots = await createBotParticipants({
              count: TARGET_PLAYER_COUNT - realPlayers.length,
              playerCount: TARGET_PLAYER_COUNT,
              matchMode: "TEAM_UP",
              rulesetKey,
              existingNames: realPlayers.map((candidate) => candidate.displayName),
              existingCharacterIds: realPlayers.map((candidate) => candidate.characterId),
              now,
            });
            state.pendingMatch = {
              matchMode: "TEAM_UP",
              playerCount: TARGET_PLAYER_COUNT,
              rulesetKey,
              participants: [...realPlayers, ...bots],
              retryAt: now + botRetryDelayMs(this.env),
            };
            await this.#persist(state);
            const completed = await this.#resumePendingMatch(state, now);
            if (!completed) return;
          }
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
    const profileId = await this.#profileIdentity(request);
    const participant = normalizeParticipant(body, profileId, this.env);
    participant.locationHint = normalizeMatchLocationHint(request.headers.get(MATCH_LOCATION_HEADER));
    const now = Date.now();
    let state = this.#cleanup(await this.#load(), now);

    const assigned = state.assignments[participant.clientRequestId];
    if (assigned) {
      return this.#matchedResponse(
        {
          ...participant,
          characterId: normalizeCharacterId(assigned.characterId ?? participant.characterId),
        },
        assigned,
      );
    }

    const existingIndex = state.queue.findIndex(
      (candidate) => candidate.clientRequestId === participant.clientRequestId,
    );
    if (existingIndex >= 0) {
      const existing = state.queue[existingIndex];
      if (existing.profileId !== participant.profileId) {
        throw httpError(
          403,
          "TEAM_MATCHMAKING_PROFILE_MISMATCH",
          "team matchmaking request belongs to another authenticated profile",
        );
      }
      state.queue[existingIndex] = {
        ...existing,
        displayName: participant.displayName,
        characterId: participant.characterId,
        rulesetKey: participant.rulesetKey,
        lastSeenAt: now,
      };
      await this.#persist(state);
      return json(200, searchingPayload(state, now, participant.clientRequestId));
    }

    if (
      state.queue.some((candidate) => candidate.profileId === participant.profileId) ||
      Object.values(state.assignments).some(
        (assignment) => assignment.profileId === participant.profileId,
      )
    ) {
      throw httpError(
        409,
        "PROFILE_ALREADY_TEAM_QUEUED",
        "this profile is already searching for a team match",
      );
    }

    state.queue.push({
      ...participant,
      joinedAt: now,
      lastSeenAt: now,
    });

    const eligible = [...state.queue]
      .filter((candidate) =>
        (candidate.rulesetKey ?? DEFAULT_RULESET_KEY) === participant.rulesetKey
      )
      .sort((left, right) => left.joinedAt - right.joinedAt);

    if (eligible.length >= TARGET_PLAYER_COUNT && state.pendingMatch) {
      // Keep a completed Team Up group visible to the alarm/recovery path while
      // another room reservation is being finalized.
      const group = selectCharacterDiverseGroup(eligible, TARGET_PLAYER_COUNT);
      const key = characterPreferenceKey(TARGET_PLAYER_COUNT, participant.rulesetKey);
      const preferences = state.characterPreferences ?? {};
      const hasUniqueCharacters =
        group.length === TARGET_PLAYER_COUNT &&
        distinctCharacterCount(group) === TARGET_PLAYER_COUNT;
      const existing = preferences[key];
      if (!existing || (hasUniqueCharacters && Number(existing.readyAt ?? 0) > now)) {
        preferences[key] = {
          playerCount: TARGET_PLAYER_COUNT,
          rulesetKey: participant.rulesetKey,
          startedAt: existing?.startedAt ?? now,
          readyAt: hasUniqueCharacters ? now : now + CHARACTER_PREFERENCE_WINDOW_MS,
        };
      }
      state.characterPreferences = preferences;
      await this.#persist(state);
      return json(202, searchingPayload(state, now, participant.clientRequestId));
    }

    if (eligible.length >= TARGET_PLAYER_COUNT && !state.pendingMatch) {
      const group = selectCharacterDiverseGroup(eligible, TARGET_PLAYER_COUNT);
      const preferenceKey = characterPreferenceKey(
        TARGET_PLAYER_COUNT,
        participant.rulesetKey,
      );
      const preferences = state.characterPreferences ?? {};
      const preference = preferences[preferenceKey];
      const hasUniqueCharacters =
        group.length === TARGET_PLAYER_COUNT &&
        distinctCharacterCount(group) === TARGET_PLAYER_COUNT;

      if (
        !hasUniqueCharacters &&
        preference &&
        Number(preference.readyAt ?? 0) > now
      ) {
        await this.#persist(state);
        return json(202, searchingPayload(state, now, participant.clientRequestId));
      }

      if (!hasUniqueCharacters && !preference) {
        preferences[preferenceKey] = {
          playerCount: TARGET_PLAYER_COUNT,
          rulesetKey: participant.rulesetKey,
          startedAt: now,
          readyAt: now + CHARACTER_PREFERENCE_WINDOW_MS,
        };
        state.characterPreferences = preferences;
        await this.#persist(state);
        return json(202, searchingPayload(state, now, participant.clientRequestId));
      }

      delete preferences[preferenceKey];
      state.characterPreferences = preferences;
      state.pendingMatch = {
        matchMode: "TEAM_UP",
        playerCount: TARGET_PLAYER_COUNT,
        rulesetKey: participant.rulesetKey,
        participants: group,
        retryAt: now + botRetryDelayMs(this.env),
      };
      await this.#persist(state);
      const completed = await this.#resumePendingMatch(state, now);
      if (!completed) {
        throw httpError(
          503,
          "TEAM_MATCHMAKER_RECOVERY_PENDING",
          "team match setup is being retried; keep searching",
        );
      }

      const ownAssignment = state.assignments[participant.clientRequestId];
      if (!ownAssignment) {
        throw httpError(
          503,
          "TEAM_MATCHMAKER_ASSIGNMENT_MISSING",
          "team match was created but the player assignment is not available yet",
        );
      }
      return this.#matchedResponse(
        {
          ...participant,
          characterId: normalizeCharacterId(ownAssignment.characterId),
        },
        ownAssignment,
      );
    }

    await this.#persist(state);
    return json(202, searchingPayload(state, now, participant.clientRequestId));
  }

  async #status(request, body) {
    const profileId = await this.#profileIdentity(request);
    const clientRequestId = requireClientRequestId(body?.clientRequestId);
    const now = Date.now();
    const state = this.#cleanup(await this.#load(), now);

    const assignment = state.assignments[clientRequestId];
    if (assignment) {
      assertOwner(assignment, profileId);
      await this.#persist(state);
      return this.#matchedResponse(
        {
          clientRequestId,
          displayName: assignment.displayName,
          characterId: normalizeCharacterId(assignment.characterId),
          profileId,
        },
        assignment,
      );
    }

    const queued = state.queue.find(
      (candidate) => candidate.clientRequestId === clientRequestId,
    );
    if (!queued) {
      await this.#persist(state);
      return json(200, {
        status: "IDLE",
        matchMode: "TEAM_UP",
        playerCount: TARGET_PLAYER_COUNT,
        queuedPlayers: 0,
        targetPlayerCount: TARGET_PLAYER_COUNT,
      });
    }
    if (queued.profileId !== profileId) {
      throw httpError(
        403,
        "TEAM_MATCHMAKING_PROFILE_MISMATCH",
        "team matchmaking request belongs to another authenticated profile",
      );
    }
    queued.lastSeenAt = now;
    await this.#persist(state);
    return json(200, searchingPayload(state, now, clientRequestId));
  }

  async #cancel(request, body) {
    const profileId = await this.#profileIdentity(request);
    const clientRequestId = requireClientRequestId(body?.clientRequestId);
    const now = Date.now();
    const state = this.#cleanup(await this.#load(), now);

    const assignment = state.assignments[clientRequestId];
    if (assignment) {
      assertOwner(assignment, profileId);
      await this.#persist(state);
      return json(200, {
        status: "MATCHED",
        matchMode: "TEAM_UP",
        cancelled: false,
        targetPlayerCount: TARGET_PLAYER_COUNT,
        matchId: assignment.matchId,
      });
    }

    const reserved = state.pendingMatch?.participants.some(
      (candidate) =>
        candidate.playerType !== "BOT" &&
        candidate.clientRequestId === clientRequestId &&
        candidate.profileId === profileId,
    );
    if (reserved) {
      await this.#persist(state);
      return json(200, {
        status: "MATCHED",
        matchMode: "TEAM_UP",
        cancelled: false,
        pending: true,
        targetPlayerCount: TARGET_PLAYER_COUNT,
      });
    }

    const queued = state.queue.find(
      (candidate) => candidate.clientRequestId === clientRequestId,
    );
    if (queued && queued.profileId !== profileId) {
      throw httpError(
        403,
        "TEAM_MATCHMAKING_PROFILE_MISMATCH",
        "team matchmaking request belongs to another authenticated profile",
      );
    }

    const before = state.queue.length;
    state.queue = state.queue.filter(
      (candidate) => candidate.clientRequestId !== clientRequestId,
    );
    await this.#persist(state);
    return json(200, {
      status: "CANCELLED",
      matchMode: "TEAM_UP",
      cancelled: state.queue.length < before,
      targetPlayerCount: TARGET_PLAYER_COUNT,
    });
  }

  async #materialize(participants, reservation = {}) {
    const host = participants[0];
    for (let attempt = 0; attempt < 4; attempt += 1) {
      // ONLINE public matchmaking reserves attempts 0..3. Team Up uses 4..7,
      // remaining inside deterministicMatchId's reviewed attempt bounds.
      const matchId = reservation.matchId
        ?? await deterministicMatchId(host.clientRequestId, 4 + attempt);
      const target = room(this.env, matchId, host.locationHint);
      const createResponse = await target.fetch(
        new Request("https://room/create", {
          method: "POST",
          headers: {
            "content-type": "application/json",
            "x-ludoproof-profile-assertion": await issueAssertion(
              this.env,
              matchId,
              host,
            ),
          },
          body: JSON.stringify({
            matchId,
            displayName: host.displayName,
            characterId: normalizeCharacterId(host.characterId),
            clientRequestId: host.clientRequestId,
            matchmaking: true,
            targetPlayerCount: TARGET_PLAYER_COUNT,
            matchMode: "TEAM_UP",
            rulesetKey: host.rulesetKey ?? reservation.rulesetKey ?? DEFAULT_RULESET_KEY,
          }),
        }),
      );
      if (createResponse.status === 409) {
        if (reservation.matchId) {
          throw httpError(
            503,
            "TEAM_MATCH_RESERVED_ID_CONFLICT",
            "reserved Team Up match ID no longer belongs to its reservation",
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
              "x-ludoproof-profile-assertion": await issueAssertion(
                this.env,
                matchId,
                participant,
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
      "TEAM_MATCH_ID_EXHAUSTED",
      "could not allocate a team match",
    );
  }

  async #resumePendingMatch(state, now) {
    const pending = state.pendingMatch;
    if (!pending) return true;

    let assignment;
    try {
      assignment = await this.#materialize(pending.participants, {
        matchId: pending.matchId,
        onMatchReserved: async (matchId) => {
          pending.matchId = matchId;
          state.pendingMatch = pending;
          await this.#persist(state);
        },
      });
    } catch (error) {
      pending.retryAt = Date.now() + botRetryDelayMs(this.env);
      state.pendingMatch = pending;
      await this.#persist(state);
      console.error("Team Up reserved match could not be materialized; retry scheduled", {
        code: error?.code ?? "TEAM_MATCH_MATERIALIZE_FAILED",
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
          role: index === 0 ? "HOST" : "GUEST",
          seat: index,
          teamId: index % 2 === 0 ? "A" : "B",
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
    assertOwner(assignment, participant.profileId);
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
            targetPlayerCount: TARGET_PLAYER_COUNT,
            matchMode: "TEAM_UP",
            rulesetKey: assignment.rulesetKey ?? participant.rulesetKey ?? DEFAULT_RULESET_KEY,
          }
        : {}),
    };
    const response = await target.fetch(
      new Request("https://room/" + (host ? "create" : "join"), {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-ludoproof-profile-assertion": await issueAssertion(
            this.env,
            assignment.matchId,
            participant,
          ),
        },
        body: JSON.stringify(body),
      }),
    );
    const session = await requireJsonResponse(response);
    return json(200, {
      status: "MATCHED",
      matchMode: "TEAM_UP",
      targetPlayerCount: TARGET_PLAYER_COUNT,
      matchId: assignment.matchId,
      seat: assignment.seat,
      teamId: assignment.teamId,
      playerId: session.playerId,
      playerToken: session.playerToken,
      state: session.state,
    });
  }

  async #profileIdentity(request) {
    const authorization = request.headers.get("authorization");
    if (
      typeof authorization !== "string" ||
      !/^Bearer\s+lpp_[A-Za-z0-9_-]{32,}$/i.test(authorization)
    ) {
      throw httpError(
        401,
        "PROFILE_AUTH_REQUIRED",
        "authenticated leaderboard profile is required for Team Up",
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
    return profileId;
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
    next.assignments = next.assignments ?? {};
    for (const [requestId, assignment] of Object.entries(next.assignments)) {
      if (Number(assignment.expiresAt ?? 0) <= now) {
        delete next.assignments[requestId];
      }
    }
    const preferences = next.characterPreferences ?? {};
    for (const [key, preference] of Object.entries(preferences)) {
      const rulesetKey = String(preference?.rulesetKey ?? DEFAULT_RULESET_KEY);
      const eligibleCount = next.queue.filter(
        (candidate) => (candidate.rulesetKey ?? DEFAULT_RULESET_KEY) === rulesetKey,
      ).length;
      if (eligibleCount < TARGET_PLAYER_COUNT) delete preferences[key];
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

function normalizeParticipant(body, profileId, env) {
  return {
    clientRequestId: requireClientRequestId(body?.clientRequestId),
    displayName: normalizeDisplayName(body?.displayName, env),
    characterId: normalizeCharacterId(body?.characterId),
    profileId,
    rulesetKey: normalizeTeamRulesetKey(body?.rulesetKey),
    playerType: "HUMAN",
  };
}

function normalizeTeamRulesetKey(value) {
  const key = value == null ? DEFAULT_RULESET_KEY : String(value);
  if (!TEAM_RULESET_KEYS.includes(key)) {
    throw httpError(400, "INVALID_RULESET_KEY", "Team Up requires a Team Up ruleset");
  }
  return key;
}

function searchingPayload(state, now, requesterRequestId) {
  const allQueued = [...state.queue].sort((a, b) => a.joinedAt - b.joinedAt);
  const requester = allQueued.find(
    (candidate) => candidate.clientRequestId === requesterRequestId,
  );
  const rulesetKey = requester?.rulesetKey ?? DEFAULT_RULESET_KEY;
  const queued = allQueued.filter(
    (candidate) => (candidate.rulesetKey ?? DEFAULT_RULESET_KEY) === rulesetKey,
  );
  const orderedForRequester = requester
    ? [
        requester,
        ...queued.filter(
          (candidate) => candidate.clientRequestId !== requesterRequestId,
        ),
      ]
    : queued;
  const visibleParticipants = orderedForRequester.slice(0, TARGET_PLAYER_COUNT);
  return {
    status: "SEARCHING",
    matchMode: "TEAM_UP",
    playerCount: TARGET_PLAYER_COUNT,
    rulesetKey,
    queuedPlayers: Math.min(queued.length, TARGET_PLAYER_COUNT),
    targetPlayerCount: TARGET_PLAYER_COUNT,
    queuedCharacterIds: visibleParticipants.map((participant) =>
      normalizeCharacterId(participant.characterId),
    ),
    searchStartedAt: queued[0]?.joinedAt ?? now,
  };
}

function assertOwner(assignment, profileId) {
  if (assignment.profileId !== profileId) {
    throw httpError(
      403,
      "TEAM_MATCHMAKING_PROFILE_MISMATCH",
      "team matchmaking request belongs to another authenticated profile",
    );
  }
}

async function issueAssertion(env, matchId, participant) {
  return issueLeaderboardProfileAssertion(env, {
    matchId,
    profileId: participant.profileId,
    playerType: participant.playerType ?? "HUMAN",
    clientRequestId: participant.clientRequestId,
    expiresAt: Date.now() + PROFILE_ASSERTION_TTL_MS,
  });
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
      "TEAM_MATCHMAKER_BAD_RESPONSE",
      "match storage returned invalid data",
    );
  }
  if (!response.ok) {
    throw httpError(
      response.status,
      body?.error ?? "TEAM_MATCHMAKER_MATCH_ERROR",
      body?.message ?? "match storage rejected team matchmaking",
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
      throw new Error("object required");
    }
    return value;
  } catch {
    throw httpError(400, "INVALID_JSON", "request body must be a JSON object");
  }
}

function errorResponse(error) {
  const status =
    Number.isInteger(error?.status) && error.status >= 400 && error.status <= 599
      ? error.status
      : 500;
  return json(status, {
    error: error?.code ?? "INTERNAL_ERROR",
    message:
      status >= 500 && !error?.code
        ? "internal server error"
        : String(error?.message ?? "internal server error"),
  });
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
