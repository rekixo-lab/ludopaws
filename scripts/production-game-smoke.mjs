import {
  randomUUID,
} from "node:crypto";

const baseUrl =
  String(
    process.env.LUDOPROOF_WORKER_URL ??
      "https://ludoproof-game-api.ai-8f3.workers.dev",
  ).replace(/\/+$/, "");

if (process.env.LUDOPROOF_AGE_SAFETY_MODE === "LOCKDOWN") {
  const guarded = [
    ["/api/matches", "POST"],
    ["/api/matchmaking/search", "POST"],
    ["/api/friends/register", "POST"],
    ["/api/friends/messages/send", "POST"],
    ["/api/store/purchases/verify", "POST"],
    ["/api/admob/ssv", "POST"],
  ];
  for (const [path, method] of guarded) {
    const response = await fetch(baseUrl + path, {
      method,
      headers: { "content-type": "application/json", "x-age": "adult" },
      body: JSON.stringify({}),
    });
    if (response.status !== 403) {
      throw new Error("Age lockdown bypass: " + path + " returned HTTP " + response.status);
    }
    const data = await response.json();
    if (data?.error !== "AGE_ACCESS_NOT_AVAILABLE") {
      throw new Error("Age lockdown unexpected error for " + path);
    }
  }
  console.log("Live age safety lockdown verified. Connected gameplay intentionally unavailable.");
  process.exit(0);
}

const ALGORITHM =
  "ludopaws-server-dice-v1";

const legacyUnranked =
  await api(
    "POST",
    "/api/matches",
    {
      body: {
        displayName:
          "Legacy Smoke",
        clientRequestId:
          randomUUID(),
        profileId:
          "550e8400-e29b-41d4-a716-446655440000",
      },
    },
  );

requireText(
  legacyUnranked.matchId,
  "legacyUnranked.matchId",
);
requireText(
  legacyUnranked.playerToken,
  "legacyUnranked.playerToken",
);

const hostProfile =
  await api(
    "POST",
    "/api/leaderboard/profile/register",
    {
      body: {
        clientRequestId:
          randomUUID(),
      },
    },
  );
const guestProfile =
  await api(
    "POST",
    "/api/leaderboard/profile/register",
    {
      body: {
        clientRequestId:
          randomUUID(),
      },
    },
  );

const hostProfileToken =
  requireText(
    hostProfile.profileToken,
    "hostProfile.profileToken",
  );
const guestProfileToken =
  requireText(
    guestProfile.profileToken,
    "guestProfile.profileToken",
  );
requireText(
  hostProfile.profileId,
  "hostProfile.profileId",
);
requireText(
  guestProfile.profileId,
  "guestProfile.profileId",
);

await api(
  "GET",
  "/api/leaderboard?limit=5",
  {
    token:
      hostProfileToken,
  },
);

const host =
  await api(
    "POST",
    "/api/matches",
    {
      token:
        hostProfileToken,
      body: {
        displayName:
          "Production Smoke Host",
        rulesetKey: "CLASSIC_V4",
        clientRequestId:
          randomUUID(),
      },
    },
  );

const matchId =
  requireText(
    host.matchId,
    "matchId",
  );
const hostPlayer =
  {
    playerId:
      requireText(
        host.playerId,
        "host.playerId",
      ),
    token:
      requireText(
        host.playerToken,
        "host.playerToken",
      ),
  };

const guest =
  await api(
    "POST",
    "/api/matches/" +
      encodeURIComponent(
        matchId,
      ) +
      "/join",
    {
      token:
        guestProfileToken,
      body: {
        displayName:
          "Production Smoke Guest",
        clientRequestId:
          randomUUID(),
      },
    },
  );

const guestPlayer =
  {
    playerId:
      requireText(
        guest.playerId,
        "guest.playerId",
      ),
    token:
      requireText(
        guest.playerToken,
        "guest.playerToken",
      ),
  };

let started =
  await api(
    "POST",
    "/api/matches/" +
      encodeURIComponent(
        matchId,
      ) +
      "/start",
    {
      token:
        hostPlayer.token,
      body: {},
    },
  );

let state =
  started.state;

if (
  state?.status !==
    "ACTIVE" ||
  !Array.isArray(
    state?.players,
  ) ||
  state.players.length !== 2
) {
  throw new Error(
    "Production smoke match did not enter ACTIVE state with two players.",
  );
}

const players =
  new Map([
    [
      hostPlayer.playerId,
      hostPlayer,
    ],
    [
      guestPlayer.playerId,
      guestPlayer,
    ],
  ]);

let verifiedRolls = 0;
let moveExercised = false;

for (
  let attempt = 0;
  attempt < 12;
  attempt += 1
) {
  const turnPlayer =
    state.players[
      state.turnSeat
    ];
  const identity =
    players.get(
      turnPlayer?.playerId,
    );

  if (!identity) {
    throw new Error(
      "Production smoke could not map the authoritative turn to a player credential.",
    );
  }

  const rollRequestId = randomUUID();
  const expectedEventIndex = state.randomEventIndex;
  const rollPath = "/api/matches/" + encodeURIComponent(matchId) + "/roll";
  const revealed = await api("POST", rollPath, {
    token: identity.token, body: { clientRequestId: rollRequestId, expectedEventIndex },
  });
  const eventIndex = revealed?.state?.pendingRoll?.eventIndex ??
    revealed?.state?.history?.at(-1)?.eventIndex;
  if (!Number.isSafeInteger(eventIndex)) throw new Error("Roll did not return its eventIndex");
  const retry = await api("POST", rollPath, {
    token: identity.token, body: { clientRequestId: rollRequestId, expectedEventIndex },
  });
  if (retry.replayed !== true || retry.outcome !== revealed.outcome ||
      retry.proofDigest !== revealed.proofDigest || retry.state.revision !== revealed.state.revision) {
    throw new Error("Roll retry changed the result or revision");
  }

  if (
    !Number.isInteger(
      revealed?.outcome,
    ) ||
    revealed.outcome < 1 ||
    revealed.outcome > 6
  ) {
    throw new Error(
      "Production smoke reveal returned an invalid dice outcome.",
    );
  }

  if (
    revealed?.proof
      ?.protocol !==
      ALGORITHM ||
    !/^[0-9a-f]{64}$/i.test(
      String(
        revealed?.proofDigest ??
          "",
      ),
    )
  ) {
    throw new Error(
      "Production smoke reveal did not return the server dice receipt contract.",
    );
  }

  verifiedRolls += 1;
  state =
    revealed.state;

  const legal =
    Array.isArray(
      revealed
        ?.legalTokenIndexes,
    )
      ? revealed
          .legalTokenIndexes
      : [];

  if (
    revealed
      ?.awaitingMove ===
        true &&
    legal.length > 0
  ) {
    const tokenIndex =
      legal[0];

    const moved =
      await api(
        "POST",
        "/api/matches/" +
          encodeURIComponent(
            matchId,
          ) +
          "/move",
        {
          token:
            identity.token,
          body: {
            tokenIndex,
            eventIndex,
            clientRequestId: randomUUID(),
          },
        },
      );

    if (
      moved?.state
        ?.pendingRoll !==
      null
    ) {
      throw new Error(
        "Production smoke move did not consume the verified roll.",
      );
    }

    state =
      moved.state;
    moveExercised = true;
    break;
  }

  if (
    state?.status !==
    "ACTIVE"
  ) {
    throw new Error(
      "Production smoke match left ACTIVE state unexpectedly.",
    );
  }
}

if (
  verifiedRolls < 1
) {
  throw new Error(
    "Production smoke did not complete a server dice roll.",
  );
}

const friendsSmoke =
  await verifyFriendsFlow();

console.log(
  JSON.stringify(
    {
      ok: true,
      profileAuth:
        {
          legacyUnrankedAccepted:
            true,
          hostProfileId:
            hostProfile.profileId,
          guestProfileId:
            guestProfile.profileId,
        },
      matchId,
      players: 2,
      verifiedRolls,
      moveExercised,
      finalRevision:
        state?.revision ?? null,
      ruleset:
        state?.rulesetId ??
        null,
      friends:
        friendsSmoke,
    },
  ),
);

async function verifyFriendsFlow() {
  const hostIdentity =
    await api(
      "POST",
      "/api/friends/register",
      {
        body: {
          displayName:
            "Smoke Friend A",
          clientRequestId:
            "11111111-1111-4111-8111-111111111111",
        },
      },
    );
  const guestIdentity =
    await api(
      "POST",
      "/api/friends/register",
      {
        body: {
          displayName:
            "Smoke Friend B",
          clientRequestId:
            "22222222-2222-4222-8222-222222222222",
        },
      },
    );

  const hostFriendId =
    requireText(
      hostIdentity.friendId,
      "friends.host.friendId",
    );
  const guestFriendId =
    requireText(
      guestIdentity.friendId,
      "friends.guest.friendId",
    );
  const hostFriendToken =
    requireText(
      hostIdentity.friendToken,
      "friends.host.friendToken",
    );
  const guestFriendToken =
    requireText(
      guestIdentity.friendToken,
      "friends.guest.friendToken",
    );

  if (
    !/^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/
      .test(
        hostFriendId,
      ) ||
    !/^LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/
      .test(
        guestFriendId,
      )
  ) {
    throw new Error(
      "Production friends smoke returned an invalid Friend ID.",
    );
  }

  try {
    await api(
      "POST",
      "/api/friends/remove",
      {
        token:
          hostFriendToken,
        body: {
          friendId:
            guestFriendId,
        },
      },
    );

    const sentRequest =
      await api(
        "POST",
        "/api/friends/request",
        {
          token:
            hostFriendToken,
          body: {
            friendId:
              guestFriendId,
          },
        },
      );

    if (
      sentRequest.status !==
        "PENDING" &&
      sentRequest.status !==
        "INCOMING_PENDING"
    ) {
      throw new Error(
        "Production friends smoke could not create a pending friend request.",
      );
    }

    const guestBeforeAccept =
      await api(
        "GET",
        "/api/friends/snapshot",
        {
          token:
            guestFriendToken,
        },
      );

    const incoming =
      Array.isArray(
        guestBeforeAccept
          ?.incomingRequests,
      )
        ? guestBeforeAccept
            .incomingRequests
        : [];

    const pendingRequest =
      incoming.find(
        (entry) =>
          entry?.friend
            ?.friendId ===
          hostFriendId,
      );

    if (
      !pendingRequest
        ?.requestId
    ) {
      throw new Error(
        "Production friends smoke did not expose the incoming friend request.",
      );
    }

    const accepted =
      await api(
        "POST",
        "/api/friends/request/respond",
        {
          token:
            guestFriendToken,
          body: {
            requestId:
              pendingRequest
                .requestId,
            accept:
              true,
          },
        },
      );

    if (
      accepted.status !==
      "ACCEPTED"
    ) {
      throw new Error(
        "Production friends smoke did not accept the friend request.",
      );
    }

    const hostSnapshot =
      await api(
        "GET",
        "/api/friends/snapshot",
        {
          token:
            hostFriendToken,
        },
      );

    const hostFriends =
      Array.isArray(
        hostSnapshot?.friends,
      )
        ? hostSnapshot
            .friends
        : [];

    if (
      !hostFriends.some(
        (friend) =>
          friend?.friendId ===
          guestFriendId,
      )
    ) {
      throw new Error(
        "Production friends smoke did not persist the friendship.",
      );
    }

    const room =
      await api(
        "POST",
        "/api/matches",
        {
          token:
            hostFriendToken,
          body: {
            displayName:
              "Smoke Friend A",
            clientRequestId:
              randomUUID(),
            targetPlayerCount:
              2,
            matchMode:
              "FRIENDS",
          },
        },
      );

    const friendMatchId =
      requireText(
        room.matchId,
        "friends.matchId",
      );
    const hostRoomToken =
      requireText(
        room.playerToken,
        "friends.hostRoomToken",
      );

    const invitation =
      await api(
        "POST",
        "/api/friends/invite",
        {
          token:
            hostFriendToken,
          roomToken:
            hostRoomToken,
          body: {
            friendId:
              guestFriendId,
            matchId:
              friendMatchId,
            clientRequestId:
              randomUUID(),
          },
        },
      );

    const inviteId =
      requireText(
        invitation.inviteId,
        "friends.inviteId",
      );

    const guestWithInvite =
      await api(
        "GET",
        "/api/friends/snapshot",
        {
          token:
            guestFriendToken,
        },
      );

    const invites =
      Array.isArray(
        guestWithInvite?.invites,
      )
        ? guestWithInvite
            .invites
        : [];

    if (
      !invites.some(
        (invite) =>
          invite?.inviteId ===
            inviteId &&
          invite?.matchId ===
            friendMatchId,
      )
    ) {
      throw new Error(
        "Production friends smoke did not expose the private-room invite.",
      );
    }

    let bypassBlocked =
      false;
    try {
      await api(
        "POST",
        "/api/matches/" +
          encodeURIComponent(
            friendMatchId,
          ) +
          "/join",
        {
          body: {
            displayName:
              "Bypass Attempt",
            clientRequestId:
              randomUUID(),
          },
        },
      );
    } catch (error) {
      bypassBlocked =
        String(
          error?.message ??
            error,
        )
          .includes(
            "FRIEND_JOIN_TOKEN_REQUIRED",
          );
    }

    if (
      !bypassBlocked
    ) {
      throw new Error(
        "Production friends smoke allowed a private-room join without an accepted invite credential.",
      );
    }

    const friendJoinRequestId =
      randomUUID();

    const acceptedInvite =
      await api(
        "POST",
        "/api/friends/invite/respond",
        {
          token:
            guestFriendToken,
          body: {
            inviteId,
            accept:
              true,
            clientRequestId:
              friendJoinRequestId,
          },
        },
      );

    if (
      acceptedInvite.status !==
        "ACCEPTED" ||
      acceptedInvite.matchId !==
        friendMatchId
    ) {
      throw new Error(
        "Production friends smoke did not accept the private-room invite.",
      );
    }

    const friendJoinToken =
      requireText(
        acceptedInvite
          .friendJoinToken,
        "friends.friendJoinToken",
      );

    const joined =
      await api(
        "POST",
        "/api/matches/" +
          encodeURIComponent(
            friendMatchId,
          ) +
          "/join",
        {
          friendJoinToken,
          body: {
            displayName:
              "Smoke Friend B",
            clientRequestId:
              friendJoinRequestId,
          },
        },
      );

    requireText(
      joined.playerToken,
      "friends.guestRoomToken",
    );

    const startedFriendRoom =
      await api(
        "POST",
        "/api/matches/" +
          encodeURIComponent(
            friendMatchId,
          ) +
          "/start",
        {
          token:
            hostRoomToken,
          body: {},
        },
      );

    const friendState =
      startedFriendRoom
        ?.state;

    if (
      friendState?.status !==
        "ACTIVE" ||
      friendState?.matchMode !==
        "FRIENDS" ||
      friendState?.targetPlayerCount !==
        2 ||
      !Array.isArray(
        friendState?.players,
      ) ||
      friendState.players.length !==
        2
    ) {
      throw new Error(
        "Production friends smoke private room did not start with the locked two-player contract.",
      );
    }

    return {
      ok: true,
      hostFriendId,
      guestFriendId,
      matchId:
        friendMatchId,
      players:
        friendState
          .players
          .length,
      matchMode:
        friendState
          .matchMode,
      targetPlayerCount:
        friendState
          .targetPlayerCount,
    };
  } finally {
    await api(
      "POST",
      "/api/friends/remove",
      {
        token:
          hostFriendToken,
        body: {
          friendId:
            guestFriendId,
        },
      },
    ).catch(
      () => {},
    );
  }
}

async function api(
  method,
  path,
  {
    token = null,
    roomToken = null,
    friendJoinToken = null,
    body = null,
  } = {},
) {
  const headers = {
    accept:
      "application/json",
    "user-agent":
      "ludoproof-production-smoke/1",
  };

  if (token) {
    headers.authorization =
      "Bearer " + token;
  }

  if (roomToken) {
    headers[
      "x-ludoproof-room-token"
    ] =
      roomToken;
  }

  if (friendJoinToken) {
    headers[
      "x-ludoproof-friend-join-token"
    ] =
      friendJoinToken;
  }

  let payload;
  if (body !== null) {
    headers["content-type"] =
      "application/json";
    payload =
      JSON.stringify(body);
  }

  const response =
    await fetch(
      baseUrl + path,
      {
        method,
        headers,
        body: payload,
        redirect: "error",
        signal:
          AbortSignal.timeout(
            20_000,
          ),
      },
    );

  const text =
    await readLimited(
      response,
      1024 * 1024,
    );

  let value;
  try {
    value =
      text
        ? JSON.parse(text)
        : {};
  } catch {
    throw new Error(
      method +
        " " +
        path +
        " returned non-JSON data.",
    );
  }

  if (!response.ok) {
    throw new Error(
      method +
        " " +
        path +
        " failed with HTTP " +
        response.status +
        " (" +
        String(
          value?.error ??
            "UNKNOWN",
        ) +
        ").",
    );
  }

  return value;
}

async function readLimited(
  response,
  maxBytes,
) {
  if (!response.body) {
    return "";
  }

  const reader =
    response.body
      .getReader();
  const decoder =
    new TextDecoder();
  let total = 0;
  let text = "";

  try {
    for (;;) {
      const {
        done,
        value,
      } =
        await reader.read();
      if (done) break;

      total +=
        value.byteLength;
      if (
        total >
        maxBytes
      ) {
        await reader.cancel();
        throw new Error(
          "Production smoke response exceeded the safety limit.",
        );
      }

      text +=
        decoder.decode(
          value,
          {
            stream: true,
          },
        );
    }

    text +=
      decoder.decode();
    return text;
  } finally {
    reader.releaseLock();
  }
}

function requireText(
  value,
  label,
) {
  if (
    typeof value !==
      "string" ||
    value.length < 1
  ) {
    throw new Error(
      "Production smoke response is missing " +
        label +
        ".",
    );
  }
  return value;
}
