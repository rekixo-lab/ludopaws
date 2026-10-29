#!/usr/bin/env node
// Release preparation only. No product update/activation API is implemented.
import { readFile } from 'node:fs/promises';
import { GOOGLE_PLAY_PRODUCTS } from '../server/src/google-play-billing.js';

const args = process.argv.slice(2);
if (args.some(arg => arg !== '--preview') || args.length > 1) {
  console.error('Usage: node scripts/prepare-v11-pricing.mjs [--preview]');
  process.exit(1);
}

try {
  const plan = JSON.parse(await readFile(new URL('../release/v11/pricing-plan.json', import.meta.url), 'utf8'));
  const client = await readFile(new URL('../android/app/src/main/java/com/rekixo/ludopaws/feature/store/domain/StoreCatalog.kt', import.meta.url), 'utf8');
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  assert(plan.schemaVersion === 1 && plan.packageName === 'com.rekixo.ludopaws' && plan.release === 'v11', 'Unexpected release identity');
  assert(plan.status === 'STAGED_NOT_APPLIED' && plan.applyTiming === 'WITH_FINAL_V11_RELEASE', 'Prices must remain staged for final v11 release');
  assert(plan.referenceCurrency === 'USD' && plan.otherProducts === 'UNCHANGED', 'Unexpected pricing scope');
  const core = Object.entries(GOOGLE_PLAY_PRODUCTS).filter(([, product]) => !product.cosmeticId);
  assert(Array.isArray(plan.products) && plan.products.length === core.length, 'Core product coverage mismatch');
  const constants = new Map([...client.matchAll(/const val (\w+)\s*=\s*"([^"]+)"/g)].map(match => [match[1], match[2]]));
  const clientGrants = new Map([...client.matchAll(/StoreProduct\(([\s\S]*?)\n\s*\)/g)].map(match => {
    const constant = /productId\s*=\s*(\w+)/.exec(match[1])?.[1];
    const amount = /gemAmount\s*=\s*([\d_]+)/.exec(match[1])?.[1];
    return [constants.get(constant), Number(amount?.replaceAll('_', ''))];
  }));
  const seen = new Set();
  let priorAmount = 0;
  let priorCents = 0;
  const requests = plan.products.map(product => {
    const actual = GOOGLE_PLAY_PRODUCTS[product.productId];
    assert(actual && !actual.cosmeticId && !seen.has(product.productId), 'Unknown or duplicate core product');
    seen.add(product.productId);
    assert(product.gemAmount === actual.gemAmount && product.gemAmount === clientGrants.get(product.productId), `Grant mapping mismatch: ${product.productId}`);
    assert(product.consumable === actual.consumable && product.removeAds === actual.removeAds, `Entitlement mismatch: ${product.productId}`);
    const cents = product.targetUsdCents;
    assert(Number.isSafeInteger(cents) && cents > 0 && cents <= 100000, 'Invalid USD cents');
    if (product.productId === 'remove_ads') assert(cents === 100, 'Remove Ads target must be USD 1.00');
    if (product.productId === 'gems_300') assert(cents === 50, 'Starter target must be USD 0.50');
    if (product.consumable) {
      assert(product.gemAmount > priorAmount && cents > priorCents, 'Packs must increase in amount and price');
      assert(!priorAmount || product.gemAmount * priorCents > priorAmount * cents, 'Bulk packs must improve Gems per dollar');
      priorAmount = product.gemAmount;
      priorCents = cents;
    }
    return { productId: product.productId, gemAmount: product.gemAmount,
      body: { price: { currencyCode: 'USD', units: String(Math.floor(cents / 100)), nanos: (cents % 100) * 10000000 } } };
  });
  assert(core.every(([id]) => seen.has(id)), 'Missing core product');
  const output = { release: plan.release, status: plan.status, generatedAt: new Date().toISOString(),
    appliedToPlay: false, warning: 'Reference-price preparation only. Local taxes, pricing limits, availability and rounding require Play review. Checkout must use current ProductDetails.formattedPrice.', requests };
  if (args.includes('--preview')) {
    const token = process.env.ANDROID_PUBLISHER_ACCESS_TOKEN;
    assert(token && !/[\r\n]/.test(token), 'Set ANDROID_PUBLISHER_ACCESS_TOKEN in your private environment to request regional previews');
    const endpoint = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(plan.packageName)}/pricing:convertRegionPrices`;
    for (const request of requests) {
      // This conversion utility does not change any live product or price.
      const response = await fetch(endpoint, { method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30000),
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(request.body) });
      assert(response.ok, `Regional preview failed for ${request.productId} (HTTP ${response.status}); no prices were applied`);
      const preview = await response.json();
      assert(preview.convertedRegionPrices && preview.regionVersion, `Incomplete regional preview: ${request.productId}`);
      request.regionalPreview = preview;
    }
  }
  console.log(JSON.stringify(output, null, 2));
} catch (error) {
  console.error(`Pricing preparation stopped: ${error.message}`);
  process.exitCode = 1;
}
