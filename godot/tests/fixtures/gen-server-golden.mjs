// Generates golden rule vectors from the live authoritative server engine.
// Output is replayed by res://tests/run_tests.gd in Godot to prove rule parity.
import * as game from "./src/game.js";

let seed = 20261110;
function rand() {
  // Deterministic LCG so the fixture is reproducible.
  seed = (seed * 1103515245 + 12345) % 2147483648;
  return seed / 2147483648;
}
function die() {
  // Bias toward sixes so opening/third-six/extra-turn paths are exercised often.
  return rand() < 0.28 ? 6 : 1 + Math.floor(rand() * 6);
}

function snapshot(state) {
  // Compact keys: s=finished?, t=turnSeat, k=tokens, c=consecutive sixes,
  // w=comeback waits, f=finish order seats, ws=winner seat, wt=winner team.
  const out = {
    s: state.status === "FINISHED" ? 1 : 0,
    t: state.turnSeat,
    k: state.players.map((p) => [...p.tokens]),
    c: [...state.consecutiveSixes],
    ws: state.winnerPlayerId == null ? -1 : state.players.findIndex((p) => p.playerId === state.winnerPlayerId),
  };
  if (state.comebackWaits) out.w = [...state.comebackWaits];
  if (state.finishOrderPlayerIds) out.f = state.finishOrderPlayerIds.map((id) => state.players.findIndex((p) => p.playerId === id));
  if (state.winnerTeamId) out.wt = state.winnerTeamId;
  return out;
}

function playGame({ rulesetKey, players, matchMode, targetPlayerCount, maxSteps = 900 }) {
  let now = 1_000;
  let state = game.newMatch({
    matchId: "m", hostPlayerId: "p0", hostDisplayName: "P0", now,
    targetPlayerCount, matchMode, rulesetKey,
  });
  for (let i = 1; i < players; i += 1) {
    state = game.addPlayer(state, { playerId: "p" + i, displayName: "P" + i, tokenAuthHash: "h", now });
  }
  state = game.startMatch(state, "p0", now);
  const colors = state.players.map((p) => p.color);
  const steps = [];
  for (let step = 0; step < maxSteps && state.status === "ACTIVE"; step += 1) {
    now += 10;
    const seat = game.actingSeatForTurn(state);
    const outcome = die();
    state = structuredClone(state);
    state.pendingRoll = {
      status: "COMMITTED", seat, scheduledSeat: state.turnSeat, playerId: state.players[seat].playerId,
      eventIndex: state.randomEventIndex, outcome: null, legalTokenIndexes: null,
    };
    const rolled = game.registerResolvedRoll(state, { outcome, proofDigest: "x", now });
    state = rolled.state;
    // r=random roll, e=effective roll, l=legal tokens, m=moved token, x=captures, et=extra turn
    const entry = {
      r: outcome,
      e: rolled.effectiveOutcome ?? outcome,
      l: [...rolled.legalTokenIndexes],
      m: -1, x: 0, et: 0,
    };
    if (rolled.awaitingMove) {
      const token = rolled.legalTokenIndexes[Math.floor(rand() * rolled.legalTokenIndexes.length)];
      now += 10;
      const moved = game.applyMove(state, { playerId: state.players[seat].playerId, tokenIndex: token, now });
      state = moved.state;
      entry.m = token;
      entry.x = moved.captures;
      entry.et = moved.extraTurn ? 1 : 0;
    }
    entry.a = snapshot(state);
    steps.push(entry);
  }
  return { rulesetKey, matchMode, players, colors, steps };
}

const games = [];
const plans = [];
for (const v of [2, 3, 4, 5, 6, 7, 8]) {
  for (const players of [2, 3, 4]) {
    plans.push({ rulesetKey: `CLASSIC_V${v}`, players, matchMode: "ONLINE", targetPlayerCount: players });
  }
}
for (const v of [2, 3, 4, 5, 6, 7]) {
  plans.push({ rulesetKey: `TEAM_UP_V${v}`, players: 4, matchMode: "TEAM_UP", targetPlayerCount: 4 });
}
for (const plan of plans) {
  for (let n = 0; n < 3; n += 1) games.push(playGame(plan));
}
const finished = games.filter((g) => g.steps.at(-1)?.a.s === 1).length;
process.stderr.write(`games=${games.length} finished=${finished} steps=${games.reduce((a, g) => a + g.steps.length, 0)}\n`);
process.stdout.write(JSON.stringify({ source: "ludopaws server/src/game.js", games }));
