import {
  MatchRoom as TeamMatchRoom,
} from "./match-room-team.js";
import {
  syncRecentFriends,
} from "./friend-social.js";

const STATE_KEY = "match-state";

/**
 * Adds best-effort recent-player recording for finished FRIENDS matches.
 * Gameplay/proof state remains authoritative; social history is an idempotent sink.
 */
export class MatchRoom extends TeamMatchRoom {
  async fetch(request) {
    const response = await super.fetch(request);
    if (response.ok) {
      await this.#enqueueRecentSync();
    }
    return response;
  }

  async alarm() {
    await super.alarm();
    await this.#enqueueRecentSync();
  }

  #enqueueRecentSync() {
    const run = this.mutationTail.then(
      () => this.#syncRecent(),
      () => this.#syncRecent(),
    );
    this.mutationTail = run.catch(() => {});
    return run;
  }

  async #syncRecent() {
    const state = await this.ctx.storage.get(STATE_KEY);
    if (!state || state.status !== "FINISHED" || state.matchMode !== "FRIENDS") {
      return;
    }
    try {
      await syncRecentFriends(this.env, state);
    } catch (error) {
      console.error("LudoProof recent-friends sync failed", {
        matchId: state.matchId,
        code: error?.code ?? "FRIEND_RECENT_SYNC_FAILED",
      });
    }
  }
}
