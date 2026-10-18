import {
  MatchRoom as TeamMatchRoom,
} from "./match-room-team.js";
import {
  syncMatchReceipt,
} from "./match-receipt-archive.js";

const STATE_KEY =
  "match-state";

/**
 * Adds best-effort durable proof archival without changing authoritative
 * gameplay responses. Receipt storage is an evidence sink: gameplay remains
 * controlled by MatchRoom state and native dice receipt verification.
 */
export class MatchRoom extends TeamMatchRoom {
  async fetch(request) {
    const response =
      await super.fetch(
        request,
      );

    if (response.ok) {
      // Receipt archival is durable evidence work, not part of the dice response
      // critical path. Leave it queued behind serialized mutations, without awaiting it.
      this.#enqueueArchiveSync();
    }

    return response;
  }

  async alarm() {
    await super.alarm();
    await this.#enqueueArchiveSync();
  }

  #enqueueArchiveSync() {
    const run =
      this.mutationTail.then(
        () =>
          this.#syncArchive(),
        () =>
          this.#syncArchive(),
      );
    this.mutationTail =
      run.catch(
        () => {},
      );
    return run;
  }

  async #syncArchive() {
    const state =
      await this.ctx.storage.get(
        STATE_KEY,
      );
    if (
      !state ||
      ![
        "ACTIVE",
        "FINISHED",
      ].includes(
        state.status,
      )
    ) {
      return;
    }

    try {
      await syncMatchReceipt(
        this.env,
        state,
      );
    } catch (error) {
      // Proof archival must never rewrite or roll back authoritative gameplay.
      // A later successful state/mutation request retries the idempotent sync.
      console.error(
        "LudoProof receipt archive sync failed",
        {
          matchId:
            state.matchId,
          code:
            error?.code ??
            "MATCH_RECEIPT_SYNC_FAILED",
        },
      );
    }
  }
}
