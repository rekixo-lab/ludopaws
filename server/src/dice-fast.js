import { canonicalJson, sha256Hex } from "./crypto.js";

export const FAST_DICE_PROTOCOL = "ludopaws-server-dice-v1";
const DOMAIN = "ludopaws:server-dice:v1:";
const HEX_SEED = /^[0-9a-f]{64}$/;
const digest = sha256Hex;

/** Uniformly sample a die: 252 is the largest multiple of six below 256. */
export function rejectionSampleDie(nextByte) {
  for (;;) {
    const byte = nextByte();
    if (!Number.isInteger(byte) || byte < 0 || byte > 255) {
      throw new Error("dice entropy must contain bytes");
    }
    if (byte < 252) return (byte % 6) + 1;
  }
}

async function outcomeForSeed(serverSeed, context) {
  for (let counter = 0; ; counter += 1) {
    const block = await digest(
      DOMAIN + "sample:" + serverSeed + ":" + canonicalJson(context) + ":" + counter,
    );
    const bytes = block.match(/../g).map((hex) => Number.parseInt(hex, 16));
    const accepted = bytes.find((byte) => byte < 252);
    if (accepted !== undefined) return rejectionSampleDie(() => accepted);
  }
}

/** A compact server receipt, independently reproducible without a provider call.
 * It proves internal consistency; it does not prove the server did not reroll.
 */
export async function createFastDiceProof(context, serverSeed = secureSeedHex()) {
  if (!HEX_SEED.test(serverSeed)) throw new Error("dice seed must be 32 bytes");
  const core = {
    protocol: FAST_DICE_PROTOCOL,
    ...context,
    roundId: "roll:" + context.eventIndex + ":" + context.clientRequestId,
    serverSeed,
    serverCommitment: await digest(DOMAIN + "commit:" + serverSeed),
    outcome: await outcomeForSeed(serverSeed, context),
  };
  return { ...core, proofDigest: await digest(DOMAIN + "receipt:" + canonicalJson(core)) };
}

export async function verifyFastDiceProof(proof) {
  try {
    if (proof?.protocol !== FAST_DICE_PROTOCOL) return false;
    const { matchId, playerId, clientRequestId, eventIndex, previousStateHash, rulesetHash } = proof;
    const expected = await createFastDiceProof(
      { matchId, playerId, clientRequestId, eventIndex, previousStateHash, rulesetHash },
      proof.serverSeed,
    );
    return canonicalJson(proof) === canonicalJson(expected);
  } catch {
    return false;
  }
}

function secureSeedHex() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return [...bytes].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}
