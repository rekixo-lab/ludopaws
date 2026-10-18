import fs from "node:fs";

const read = (path) => fs.readFileSync(path, "utf8");
const requireText = (source, token, message) => {
  if (!source.includes(token)) throw new Error(message);
};
const rejectText = (source, token, message) => {
  if (source.includes(token)) throw new Error(message);
};

const strings = read("android/app/src/main/res/values/strings.xml");
const homeHero = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/home/presentation/components/HomeHeroUi.kt",
);
const fairDice = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/proof/domain/core/FairDiceExplainer.kt",
);
const fairDiceTest = read(
  "android/app/src/test/java/com/rekixo/ludopaws/feature/proof/domain/core/FairDiceExplainerTest.kt",
);
const profileActivity = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/ProfileActivity.kt",
);
const profileHeader = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/components/ProfileHeaderUi.kt",
);
const profileStats = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/profile/presentation/components/ProfileStatsUi.kt",
);
const storeActivity = read(
  "android/app/src/main/java/com/rekixo/ludopaws/feature/store/presentation/StoreActivity.kt",
);
const buildGradle = read("android/app/build.gradle.kts");
const wrangler = read("server/wrangler.json");

requireText(
  strings,
  "Verified Fair Dice",
  "Home/store-facing copy must expose the consumer-friendly Verified Fair Dice wording.",
);
requireText(
  strings,
  "talking animal pawns",
  "Home copy must keep the Ludo Paws character USP ahead of proof jargon.",
);
requireText(
  homeHero,
  "FairDiceExplainer.HOME_ACTION_LABEL",
  "Home must use the centralized Fair Dice action label.",
);
requireText(
  homeHero,
  "FairDiceExplainer.dialogBody()",
  "Home must route Fair Dice into the centralized explanation.",
);
requireText(
  fairDice,
  'HOME_ACTION_LABEL =\n        "FAIR DICE"',
  "Fair Dice must replace raw PROOFS wording in the consumer quick action.",
);
requireText(
  fairDice,
  "Advanced details:",
  "Advanced proof vocabulary must remain available behind the friendly summary.",
);
requireText(
  fairDice,
  "native dice engine",
  "Advanced Fair Dice details must retain the online proof mechanism name.",
);
requireText(
  fairDice,
  "HKDF",
  "Advanced Fair Dice details must retain the offline derivation vocabulary.",
);
rejectText(
  fairDice.match(/FRIENDLY_SUMMARY =[\s\S]*?ONLINE_SUMMARY =/)?.[0] ?? "",
  "HKDF",
  "Friendly Fair Dice summary must not front-load protocol jargon.",
);

requireText(
  profileActivity,
  "profileIdentityPanel(profile)",
  "Profile must surface the equipped Ludo Paws character in its main hero panel.",
);
rejectText(
  profileActivity,
  "profilePawIdentityPanel()",
  "Profile must not render a duplicate Paw identity panel.",
);
requireText(
  profileActivity,
  "override fun onRestart()",
  "Profile must refresh after returning from character/store selection.",
);
requireText(
  profileHeader,
  "CharacterSelectionStore",
  "The Profile hero Paw must come from the real persisted character selection.",
);
requireText(
  profileHeader,
  "importantForAccessibility",
  "The Profile hero character portrait must be exposed to accessibility services.",
);
requireText(
  profileHeader,
  "Character personality is presentation-only and never changes dice or legal moves.",
  "Profile must keep the cosmetic/gameplay trust boundary explicit.",
);
requireText(
  profileHeader,
  "StoreActivity.EXTRA_INITIAL_TAB",
  "The Change Paw control must deep-link to the Paws store instead of dropping users into an unrelated tab.",
);
requireText(
  profileHeader,
  "Change selected Paw",
  "The hero Change action must operate on the real selected Paw, not a profile-picture avatar.",
);
rejectText(
  profileHeader,
  "chooseProfilePhoto()",
  "Profile must not contain a separate profile-photo avatar workflow.",
);
rejectText(
  profileActivity,
  "profilePhotoUri",
  "Profile must not load a second profile photo over the selected Paw.",
);
rejectText(
  profileStats,
  "profile.rush",
  "Profile Game Stats must show only Classic.",
);
rejectText(
  profileStats,
  "profile.snakeLadder",
  "Profile Game Stats must not show Snakes & Ladders.",
);
requireText(
  storeActivity,
  "requestedInitialTab()",
  "Store must safely resolve direct-tab requests.",
);
requireText(
  storeActivity,
  "StoreTab.entries",
  "Store direct-tab routing must validate against known tabs.",
);

for (const marker of [
  "homeLabelUsesConsumerFriendlyFairDiceLanguage",
  "friendlySummaryAvoidsProtocolJargon",
  "advancedDetailsStillExposeAuditVocabulary",
  "dialogBodyKeepsFriendlyExplanationBeforeAdvancedDetails",
]) {
  requireText(
    fairDiceTest,
    marker,
    `Phase 15 Fair Dice unit coverage is missing: ${marker}`,
  );
}

requireText(
  buildGradle,
  'applicationId = "com.rekixo.ludopaws"',
  "The approved first-release Android application ID must remain Rekixo-branded.",
);
requireText(
  buildGradle,
  'namespace = "com.rekixo.ludopaws"',
  "Keep the legacy Kotlin namespace stable during the isolated application-ID migration.",
);
requireText(
  wrangler,
  '"name": "ludoproof-game-api"',
  "Phase 15 must not rename the deployed Cloudflare Worker.",
);

console.log(
  "Ludo Paws Phase 15 hardening gate passed: social character identity, Verified Fair Dice UX, accessibility hooks, deep-link safety and compatibility identifiers are preserved.",
);
