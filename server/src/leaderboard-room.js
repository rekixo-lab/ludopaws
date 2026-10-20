import { isFamilyMode } from "./family-safety.js";
import {
  deriveLeaderboardIdentity,
  httpError,
  requireClientRequestId,
  sha256Hex,
} from "./crypto.js";
import {
  LEADERBOARD_FINISH_POINTS,
  LEADERBOARD_WIN_BONUS,
  leaderboardPointsForResult,
  normalizeLeaderboardProfileId,
} from "./leaderboard-core.js";

const MAX_LEADERBOARD_LIMIT = 50;
const DEFAULT_LEADERBOARD_LIMIT = 25;
const PROFILE_TOKEN =
  /^lpp_[A-Za-z0-9_-]{32,}$/;

export class LeaderboardRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;

    ctx.blockConcurrencyWhile(async () => {
      const sql = ctx.storage.sql;

      sql.exec(`
        CREATE TABLE IF NOT EXISTS leaderboard_profiles_v2 (
          profile_id TEXT PRIMARY KEY,
          token_hash TEXT NOT NULL UNIQUE,
          registration_request_id TEXT NOT NULL UNIQUE,
          created_at INTEGER NOT NULL,
          last_seen_at INTEGER NOT NULL
        )
      `);

      sql.exec("CREATE TABLE IF NOT EXISTS deleted_leaderboard_profiles (profile_id TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)");
      sql.exec("CREATE TABLE IF NOT EXISTS deleted_leaderboard_registration_requests (request_hash TEXT PRIMARY KEY, deleted_at INTEGER NOT NULL)");

      sql.exec(`
        CREATE TABLE IF NOT EXISTS leaderboard_entries_v2 (
          profile_id TEXT PRIMARY KEY,
          display_name TEXT NOT NULL,
          games INTEGER NOT NULL DEFAULT 0,
          wins INTEGER NOT NULL DEFAULT 0,
          points INTEGER NOT NULL DEFAULT 0,
          updated_at INTEGER NOT NULL
        )
      `);

      sql.exec(`
        CREATE TABLE IF NOT EXISTS leaderboard_matches_v2 (
          match_id TEXT PRIMARY KEY,
          recorded_at INTEGER NOT NULL
        )
      `);

      sql.exec(`
        CREATE INDEX IF NOT EXISTS idx_leaderboard_rank_v2
        ON leaderboard_entries_v2 (
          points DESC,
          wins DESC,
          games ASC,
          updated_at ASC,
          profile_id ASC
        )
      `);
    });
  }

  async fetch(request) {
    try {
      const url = new URL(request.url);

      if (
        request.method === "POST" &&
        url.pathname === "/profile/register"
      ) {
        return await this.#registerProfile(
          request,
        );
      }

      if (
        request.method === "GET" &&
        url.pathname === "/profile/identity"
      ) {
        return await this.#profileIdentity(
          request,
        );
      }

      // Internal-only recovery; only the authenticated account Worker route can call this.
      if (
        request.method === "POST" &&
        url.pathname === "/profile/recover"
      ) {
        return await this.#recoverProfileIdentity(request);
      }
      if (request.method === "POST" && url.pathname === "/profile/delete") return await this.#deleteProfile(request);

      if (
        request.method === "POST" &&
        url.pathname === "/record"
      ) {
        return await this.#record(
          request,
        );
      }

      if (
        request.method === "GET" &&
        url.pathname === "/list"
      ) {
        return await this.#list(
          request,
          url,
        );
      }

      return json(404, {
        error: "NOT_FOUND",
        message: "route not found",
      });
    } catch (error) {
      return json(
        Number.isInteger(error?.status)
          ? error.status
          : 500,
        {
          error:
            error?.code ??
            "INTERNAL_ERROR",
          message:
            Number.isInteger(error?.status)
              ? String(
                  error?.message ??
                    "request failed",
                )
              : "internal server error",
        },
      );
    }
  }

  async #registerProfile(
    request,
  ) {
    const body =
      await readJson(
        request,
      );
    const clientRequestId =
      requireClientRequestId(
        body?.clientRequestId,
      );
    const identity =
      await deriveLeaderboardIdentity(
        this.env,
        clientRequestId,
      );
    const tokenHash =
      await profileTokenHash(
        identity.profileToken,
      );
    const registrationRequestHash = await sha256Hex(
      "ludoproof:leaderboard-registration:v1:" + clientRequestId,
    );
    const deletedProfileRows = [...this.ctx.storage.sql.exec(
      "SELECT profile_id FROM deleted_leaderboard_profiles WHERE profile_id = ? LIMIT 1",
      identity.profileId,
    )];
    const deletedRequestRows = [...this.ctx.storage.sql.exec(
      "SELECT request_hash FROM deleted_leaderboard_registration_requests WHERE request_hash = ? LIMIT 1",
      registrationRequestHash,
    )];
    if (deletedProfileRows.length || deletedRequestRows.length) {
      throw httpError(410, "LEADERBOARD_PROFILE_DELETED", "this leaderboard profile has been deleted");
    }
    const now =
      Date.now();

    const existingRows = [
      ...this.ctx.storage.sql.exec(
        `
          SELECT
            profile_id,
            registration_request_id
          FROM leaderboard_profiles_v2
          WHERE
            profile_id = ?
            OR registration_request_id = ?
            OR token_hash = ?
          LIMIT 1
        `,
        identity.profileId,
        clientRequestId,
        tokenHash,
      ),
    ];

    if (
      existingRows.length >
      0
    ) {
      const existing =
        existingRows[0];

      if (
        String(
          existing.profile_id,
        ) !==
          identity.profileId ||
        String(
          existing.registration_request_id,
        ) !==
          clientRequestId
      ) {
        throw httpError(
          409,
          "PROFILE_IDENTITY_CONFLICT",
          "leaderboard identity could not be recovered",
        );
      }

      this.ctx.storage.sql.exec(
        `
          UPDATE leaderboard_profiles_v2
          SET
            token_hash = ?,
            last_seen_at = ?
          WHERE profile_id = ?
        `,
        tokenHash,
        now,
        identity.profileId,
      );
    } else {
      this.ctx.storage.sql.exec(
        `
          INSERT INTO leaderboard_profiles_v2 (
            profile_id,
            token_hash,
            registration_request_id,
            created_at,
            last_seen_at
          ) VALUES (?, ?, ?, ?, ?)
        `,
        identity.profileId,
        tokenHash,
        clientRequestId,
        now,
        now,
      );
    }

    return json(
      200,
      {
        ok: true,
        profileId:
          identity.profileId,
        profileToken:
          identity.profileToken,
      },
    );
  }

  async #profileIdentity(
    request,
  ) {
    const profile =
      await this.#authorizeProfile(
        request,
      );

    return json(
      200,
      {
        ok: true,
        profileId:
          profile.profileId,
      },
    );
  }

  async #recoverProfileIdentity(request) {
    const body = await readJson(request);
    const profileId = normalizeLeaderboardProfileId(body?.profileId);
    if (!profileId) throw badRequest("INVALID_PROFILE_ID", "leaderboard profile ID is invalid");
    const rows = [
      ...this.ctx.storage.sql.exec(
        "SELECT profile_id, registration_request_id FROM leaderboard_profiles_v2 WHERE profile_id = ? LIMIT 1",
        profileId,
      ),
    ];
    if (rows.length !== 1) {
      throw httpError(404, "LEADERBOARD_IDENTITY_NOT_FOUND", "linked leaderboard identity could not be recovered");
    }
    const registrationRequestId = String(rows[0].registration_request_id);
    const identity = await deriveLeaderboardIdentity(this.env, registrationRequestId);
    if (identity.profileId !== profileId) {
      throw httpError(409, "LEADERBOARD_IDENTITY_RECOVERY_CONFLICT", "linked leaderboard identity did not match its recovery record");
    }
    const tokenHash = await profileTokenHash(identity.profileToken);
    this.ctx.storage.sql.exec(
      "UPDATE leaderboard_profiles_v2 SET token_hash = ?, last_seen_at = ? WHERE profile_id = ?",
      tokenHash,
      Date.now(),
      profileId,
    );
    return json(200, {
      ok: true,
      profileId,
      profileToken: identity.profileToken,
      registrationRequestId,
    });
  }

  async #deleteProfile(request) {
    const body = await readJson(request), profileId = normalizeLeaderboardProfileId(body?.profileId);
    if (!profileId) throw badRequest("INVALID_PROFILE_ID", "leaderboard profile ID is invalid");
    const profileRows = [...this.ctx.storage.sql.exec(
      "SELECT registration_request_id FROM leaderboard_profiles_v2 WHERE profile_id = ? LIMIT 1",
      profileId,
    )];
    const registrationRequestHash = profileRows.length
      ? await sha256Hex("ludoproof:leaderboard-registration:v1:" + String(profileRows[0].registration_request_id))
      : null;
    const deletedAt = Date.now();
    this.ctx.storage.transactionSync(() => {
      this.ctx.storage.sql.exec(
        "INSERT OR IGNORE INTO deleted_leaderboard_profiles (profile_id, deleted_at) VALUES (?, ?)",
        profileId,
        deletedAt,
      );
      if (registrationRequestHash) {
        this.ctx.storage.sql.exec(
          "INSERT OR IGNORE INTO deleted_leaderboard_registration_requests (request_hash, deleted_at) VALUES (?, ?)",
          registrationRequestHash,
          deletedAt,
        );
      }
      this.ctx.storage.sql.exec("DELETE FROM leaderboard_entries_v2 WHERE profile_id = ?", profileId);
      this.ctx.storage.sql.exec("DELETE FROM leaderboard_profiles_v2 WHERE profile_id = ?", profileId);
    });
    return json(200, { ok: true, deleted: true });
  }

  async #record(request) {
    const body = await readJson(request);
    const matchId = String(
      body?.matchId ?? "",
    ).trim().toUpperCase();

    if (!/^LP[A-Z2-9]{8}$/.test(matchId)) {
      throw badRequest(
        "INVALID_MATCH_ID",
        "invalid match ID",
      );
    }

    if (
      !Array.isArray(body?.players) ||
      body.players.length < 1 ||
      body.players.length > 4
    ) {
      throw badRequest(
        "INVALID_PLAYERS",
        "players must contain one to four entries",
      );
    }

    const players = [];
    const seen = new Set();

    for (const raw of body.players) {
      const profileId =
        normalizeLeaderboardProfileId(
          raw?.profileId,
        );
      if (!profileId) {
        throw badRequest(
          "INVALID_PROFILE_ID",
          "leaderboard profile ID is invalid",
        );
      }
      if (seen.has(profileId)) {
        throw badRequest(
          "DUPLICATE_PROFILE",
          "a leaderboard profile may only appear once per match",
        );
      }

      const profileRows = [
        ...this.ctx.storage.sql.exec(
          `
            SELECT profile_id
            FROM leaderboard_profiles_v2
            WHERE profile_id = ?
            LIMIT 1
          `,
          profileId,
        ),
      ];
      if (
        profileRows.length !==
        1
      ) {
        const deleted = [...this.ctx.storage.sql.exec(
          "SELECT profile_id FROM deleted_leaderboard_profiles WHERE profile_id = ? LIMIT 1", profileId,
        )];
        if (deleted.length === 1) {
          seen.add(profileId);
          continue;
        }
        throw httpError(
          403,
          "PROFILE_NOT_AUTHENTICATED",
          "match result contains an unauthenticated leaderboard profile",
        );
      }

      seen.add(profileId);

      const displayName =
        normalizeDisplayName(
          raw?.displayName, this.env,
        );
      const won =
        raw?.won === true;

      players.push({
        profileId,
        displayName,
        won,
      });
    }

    const now = Date.now();
    let replayed = false;

    this.ctx.storage.transactionSync(() => {
      const inserted =
        this.ctx.storage.sql.exec(
          `
            INSERT OR IGNORE INTO leaderboard_matches_v2 (
              match_id,
              recorded_at
            ) VALUES (?, ?)
          `,
          matchId,
          now,
        );

      if (
        Number(inserted.rowsWritten ?? 0) !== 1
      ) {
        replayed = true;
        return;
      }

      for (const player of players) {
        const points =
          leaderboardPointsForResult(
            player.won,
          );
        const wins =
          player.won ? 1 : 0;

        this.ctx.storage.sql.exec(
          `
            INSERT INTO leaderboard_entries_v2 (
              profile_id,
              display_name,
              games,
              wins,
              points,
              updated_at
            ) VALUES (?, ?, 1, ?, ?, ?)
            ON CONFLICT(profile_id) DO UPDATE SET
              display_name = excluded.display_name,
              games = leaderboard_entries_v2.games + 1,
              wins = leaderboard_entries_v2.wins + excluded.wins,
              points = leaderboard_entries_v2.points + excluded.points,
              updated_at = excluded.updated_at
          `,
          player.profileId,
          player.displayName,
          wins,
          points,
          now,
        );
      }
    });

    return json(200, {
      ok: true,
      replayed,
      matchId,
    });
  }

  async #list(
    request,
    url,
  ) {
    const requestedLimit =
      Number(
        url.searchParams.get("limit") ?? DEFAULT_LEADERBOARD_LIMIT,
      );
    const limit =
      Number.isSafeInteger(requestedLimit)
        ? Math.min(
            MAX_LEADERBOARD_LIMIT,
            Math.max(1, requestedLimit),
          )
        : DEFAULT_LEADERBOARD_LIMIT;
    const viewer =
      await this.#optionalProfile(
        request,
      );
    const viewerId =
      viewer?.profileId ??
      null;

    const rows = [
      ...this.ctx.storage.sql.exec(
        `
          SELECT
            profile_id,
            display_name,
            games,
            wins,
            points
          FROM leaderboard_entries_v2
          ORDER BY
            points DESC,
            wins DESC,
            games ASC,
            updated_at ASC,
            profile_id ASC
          LIMIT ?
        `,
        limit,
      ),
    ];

    const entries =
      rows.map((row, index) => ({
        rank: index + 1,
        displayName:
          String(row.display_name),
        games:
          Number(row.games),
        wins:
          Number(row.wins),
        points:
          Number(row.points),
        isViewer:
          viewerId != null &&
          String(row.profile_id) ===
            viewerId,
      }));

    let viewerEntry = null;

    if (viewerId) {
      const viewerRows = [
        ...this.ctx.storage.sql.exec(
          `
            SELECT
              ranked.display_name,
              ranked.games,
              ranked.wins,
              ranked.points,
              ranked.rank
            FROM (
              SELECT
                profile_id,
                display_name,
                games,
                wins,
                points,
                ROW_NUMBER() OVER (
                  ORDER BY
                    points DESC,
                    wins DESC,
                    games ASC,
                    updated_at ASC,
                    profile_id ASC
                ) AS rank
              FROM leaderboard_entries_v2
            ) AS ranked
            WHERE ranked.profile_id = ?
            LIMIT 1
          `,
          viewerId,
        ),
      ];

      if (viewerRows.length === 1) {
        const row = viewerRows[0];
        viewerEntry = {
          rank:
            Number(row.rank),
          displayName:
            String(row.display_name),
          games:
            Number(row.games),
          wins:
            Number(row.wins),
          points:
            Number(row.points),
        };
      }
    }

    return json(200, {
      season:
        "authenticated-v2",
      scope:
        "verified-online-classic-authenticated",
      scoring: {
        finish:
          LEADERBOARD_FINISH_POINTS,
        winBonus:
          LEADERBOARD_WIN_BONUS,
      },
      entries,
      viewer:
        viewerEntry,
    });
  }

  async #optionalProfile(
    request,
  ) {
    const header =
      request.headers.get(
        "authorization",
      );
    if (
      header == null ||
      header.trim() ===
        ""
    ) {
      return null;
    }
    return this.#authorizeProfile(
      request,
    );
  }

  async #authorizeProfile(
    request,
  ) {
    const header =
      request.headers.get(
        "authorization",
      ) ?? "";
    const match =
      header.match(
        /^Bearer\s+(.+)$/i,
      );
    const token =
      match?.[1] ??
      "";

    if (
      !PROFILE_TOKEN.test(
        token,
      )
    ) {
      throw httpError(
        401,
        "PROFILE_AUTH_INVALID",
        "leaderboard profile credential is invalid",
      );
    }

    const tokenHash =
      await profileTokenHash(
        token,
      );
    const rows = [
      ...this.ctx.storage.sql.exec(
        `
          SELECT
            profile_id
          FROM leaderboard_profiles_v2
          WHERE token_hash = ?
          LIMIT 1
        `,
        tokenHash,
      ),
    ];

    if (
      rows.length !==
      1
    ) {
      throw httpError(
        401,
        "PROFILE_AUTH_INVALID",
        "leaderboard profile credential is invalid",
      );
    }

    const now =
      Date.now();
    const profileId =
      String(
        rows[0].profile_id,
      );
    this.ctx.storage.sql.exec(
      `
        UPDATE leaderboard_profiles_v2
        SET last_seen_at = ?
        WHERE profile_id = ?
      `,
      now,
      profileId,
    );

    return {
      profileId,
    };
  }
}

async function profileTokenHash(
  token,
) {
  return sha256Hex(
    "ludoproof:leaderboard-profile-token-hash:v1:" +
      token,
  );
}

function normalizeDisplayName(value, env) {
  if (isFamilyMode(env)) return "Player";
  const normalized =
    String(value ?? "")
      .trim()
      .replace(/\s+/g, " ")
      .slice(0, 24);

  if (normalized.length < 2) {
    throw badRequest(
      "INVALID_DISPLAY_NAME",
      "display name must contain 2 to 24 characters",
    );
  }
  return normalized;
}

async function readJson(request) {
  const type =
    request.headers.get(
      "content-type",
    ) ?? "";
  if (
    !type
      .toLowerCase()
      .startsWith(
        "application/json",
      )
  ) {
    throw httpError(
      415,
      "UNSUPPORTED_MEDIA_TYPE",
      "content-type must be application/json",
    );
  }

  try {
    const body = await request.json();
    if (
      !body ||
      typeof body !== "object" ||
      Array.isArray(body)
    ) {
      throw new Error(
        "object required",
      );
    }
    return body;
  } catch (error) {
    if (
      error?.code ===
      "UNSUPPORTED_MEDIA_TYPE"
    ) {
      throw error;
    }
    throw badRequest(
      "INVALID_JSON",
      "request body must be a JSON object",
    );
  }
}

function badRequest(code, message) {
  const error =
    new Error(message);
  error.status = 400;
  error.code = code;
  return error;
}

function json(status, body) {
  return new Response(
    JSON.stringify(body),
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
