import test from "node:test";
import assert from "node:assert/strict";
import { BillingReconciliation } from "../src/billing-reconciliation.js";
import { StorePurchaseLedger } from "../src/store-purchase-ledger.js";

function context() {
  const values = new Map();
  return { storage: { get: async key => structuredClone(values.get(key)),
    put: async (key,value) => values.set(key, structuredClone(value)),
    delete: async key => values.delete(key), setAlarm: async time => values.set("alarm",time) }, values };
}
async function ledgerCall(ledger,path,body={}) {
  const response = await ledger.fetch(new Request("https://ledger"+path,{method:"POST",body:JSON.stringify(body)}));
  return {status:response.status,body:await response.json()};
}
const owner = "00000000-0000-4000-8000-000000000081";
test("revoked ledger cannot complete or change owners on retry", async () => {
  const ledger = new StorePurchaseLedger(context());
  const purchase = {accountId:"a".repeat(64),productId:"remove_ads",gemAmount:5,removeAds:true,ownerAccountId:owner,purchaseTokenHash:"b".repeat(64)};
  assert.equal((await ledgerCall(ledger,"/reserve",purchase)).status,200);
  assert.equal((await ledgerCall(ledger,"/revoke")).body.purchase.ownerAccountId,owner);
  assert.equal((await ledgerCall(ledger,"/complete",purchase)).status,409);
  assert.equal((await ledgerCall(ledger,"/bind-owner",{...purchase,ownerAccountId:owner.replace("081","082")})).status,409);
  assert.equal((await ledgerCall(ledger,"/reserve",purchase)).body.purchase.state,"REVOKED");
});
test("reconciliation remains idle with disabled billing", async () => {
  const coordinator = new BillingReconciliation(context(),{});
  assert.deepEqual(await coordinator.reconcile(()=>{throw Error("unexpected request");}),{skipped:true});
});
test("Play pagination resumes durably; wallet failures do not advance the cursor", async () => {
  const key = await crypto.subtle.generateKey({name:"RSASSA-PKCS1-v1_5",modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:"SHA-256"},true,["sign","verify"]);
  const pem = "-----BEGIN PRIVATE KEY-----\n" + Buffer.from(await crypto.subtle.exportKey("pkcs8",key.privateKey)).toString("base64") + "\n-----END PRIVATE KEY-----";
  const ledger = new StorePurchaseLedger(context());
  await ledgerCall(ledger,"/reserve",{accountId:"a".repeat(64),productId:"gems_300",gemAmount:300,removeAds:false,ownerAccountId:owner,purchaseTokenHash:"b".repeat(64)});
  let failWallet=true, wallets=0;
  const env={GOOGLE_PLAY_BILLING_ENABLED:"true",GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL:"billing@test.iam.gserviceaccount.com",
    GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY:pem,LUDOPROOF_STORE_PURCHASES:{idFromName:n=>n,get:()=>ledger},
    LUDOPROOF_PLAYER_ACCOUNTS:{idFromName:n=>n,get:()=>({fetch:async request=>{
      assert.equal(new URL(request.url).pathname,"/wallet/play-revoke");
      assert.equal((await request.json()).accountId,owner); wallets++;
      return Response.json(failWallet?{error:"TEMPORARY"}:{ok:true},{status:failWallet?503:200});
    }})}};
  const ctx=context(), coordinator = new BillingReconciliation(ctx,env), now=Date.now(), requests=[];
  const fetchImpl=async input=>{
    const url=String(input);requests.push(url);
    if(url.includes("oauth2.googleapis.com"))return Response.json({access_token:"test-only",expires_in:3600});
    if(url.includes("token=page-2"))return Response.json({voidedPurchases:[]});
    return Response.json({voidedPurchases:[{purchaseToken:"test-refunded-token"}],tokenPagination:{nextPageToken:"page-2"}});
  };
  await assert.rejects(coordinator.reconcile(fetchImpl,now),/wallet unavailable/);
  assert.equal(await ctx.storage.get("progress:v1"),undefined);
  failWallet=false;
  const first=await coordinator.reconcile(fetchImpl,now);
  assert.equal(first.revoked,1);assert.equal(first.continued,true);
  assert.equal((await ctx.storage.get("progress:v1")).pageToken,"page-2");
  assert.ok(await ctx.storage.get("alarm"));
  await coordinator.reconcile(fetchImpl,now+1000);
  assert.equal((await ctx.storage.get("progress:v1")).completedThrough,now);
  assert.ok(requests.some(url=>url.includes("token=page-2")));
  assert.equal(wallets,2);
});
