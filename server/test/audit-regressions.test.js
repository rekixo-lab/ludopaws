import assert from 'node:assert/strict';
import { familyProjection } from '../src/family-safety.js';
import worker from '../src/index.js';
import { MatchRoom } from '../src/match-room-realtime.js';

import test from 'node:test';
test('audit regressions: private leaderboard names, aggregate edge gate and durable result retry', async () => {
const report = {};
const projected = familyProjection({entries:[{rank:1,profileId:'profile-a',displayName:'First player',games:5,wins:3},{rank:2,profileId:'profile-b',displayName:'Second player',games:4,wins:2}]},{LUDOPROOF_AGE_SAFETY_MODE:'FAMILY_V2'});
report.leaderboardNameCollision = {names:projected.entries.map(e=>e.displayName),distinct:new Set(projected.entries.map(e=>e.displayName)).size};
assert.equal(report.leaderboardNameCollision.distinct,2);
assert.deepEqual(familyProjection(projected,{LUDOPROOF_AGE_SAFETY_MODE:'FAMILY_V2'}),projected);

let edgeGateCalls=0,roomCalls=0;
const env={LUDOPROOF_API_GATE:{idFromName:n=>n,get:()=>({fetch:async()=>{edgeGateCalls++;return Response.json({allowed:true});}})},LUDOPROOF_MATCHES:{idFromName:n=>n,get:()=>({fetch:async()=>{roomCalls++;return Response.json({error:'AUTH_INVALID'},{status:403});}})}};
await worker.fetch(new Request('https://audit.invalid/api/matches/LPABCDEFGH/state',{headers:{authorization:'Bearer invalid-test-token'}}),env);
report.invalidBearerEdgeGate={edgeGateCalls,roomCalls};
assert.equal(edgeGateCalls,1);assert.equal(roomCalls,1);

const values=new Map();let alarmAt=null,leaderboardCalls=0;
const now=Date.now();
const state={matchId:'LPABCDEFGH',status:'FINISHED',matchMode:'ONLINE',rulesetKey:'CLASSIC_V5',createdAt:now,updatedAt:now,revision:10,turnSeat:0,randomEventIndex:0,pendingRoll:null,consecutiveSixes:[0,0],winnerPlayerId:'one',players:[{playerId:'one',profileId:'00000000-0000-4000-8000-000000000001',displayName:'One',color:'RED',tokens:[57,57,57,57]},{playerId:'two',profileId:'00000000-0000-4000-8000-000000000002',displayName:'Two',color:'GREEN',tokens:[0,-1,-1,-1]}]};
values.set('match-state',structuredClone(state));
const ctx={storage:{get:async k=>structuredClone(values.get(k)),put:async(k,v)=>values.set(k,structuredClone(v)),delete:async k=>values.delete(k),deleteAll:async()=>values.clear(),setAlarm:async t=>{alarmAt=t;}},getWebSockets:()=>[]};
const room=new MatchRoom(ctx,{LUDOPROOF_BOT_TURNS_ENABLED:'false',LUDOPROOF_LEADERBOARD:{idFromName:n=>n,get:()=>({fetch:async()=>{leaderboardCalls++;return leaderboardCalls === 1 ? Response.json({error:'TEMPORARY'},{status:503}) : Response.json({ok:true});}})}});
await room.recordFinishedMatchForLifecycle(state);
const afterFailure=leaderboardCalls;
await room.alarm();
const afterEarlyAlarm=leaderboardCalls;
const originalNow=Date.now;
try{Date.now=()=>now+24*60*60*1000+100;await room.alarm();}finally{Date.now=originalNow;}
report.finishedLeaderboardDelivery={afterFailure,afterEarlyAlarm,afterExpiryAlarm:leaderboardCalls,stateDeleted:!values.has('match-state'),alarmAt};
assert.equal(leaderboardCalls,2);assert.equal(report.finishedLeaderboardDelivery.stateDeleted,true);


});
