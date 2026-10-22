import fs from "node:fs";
import assert from "node:assert/strict";

const base = "android/app/src/main/java/com/rekixo/ludopaws/";
const read = name => fs.readFileSync(base + name, "utf8");
const offline = read("feature/offline/presentation/setup/OfflineSetupScreenUi.kt");
const online = read("feature/online/presentation/components/OnlineLobbyUi.kt");
const friends = read("feature/friends/presentation/FriendsActivity.kt");
const kit = read("feature/friends/presentation/FriendsVisualKit.kt");
const opponent = read("feature/online/presentation/components/OnlineMatchmakingSearchView.kt");
const hero = read("feature/online/presentation/art/OnlineLobbyArtView.kt");

function region(text, start, end, label) {
  const from = text.indexOf(start);
  const to = text.indexOf(end, from + start.length);
  assert.ok(from >= 0 && to > from, "Missing " + label);
  return text.slice(from, to);
}

assert.match(offline, /background = null/, "Robot and Pass & Play keep unboxed page content");
for (const part of ["characterPanel()", "playersPanel()", "colorPanel()"]) {
  assert.ok(offline.includes(part), "Local setup is unchanged: " + part);
}
const setup = region(online, "matchmakingSetupPanel =", "addView(\n                    onlineModePanelHeader()", "Online setup");
const searching = region(online, "matchmakingSearchPanel =", "addView(\n                    onlineModePanelHeader()", "Online searching");
for (const [name, group] of [["setup", setup], ["searching", searching]]) {
  assert.match(group, /background\s*=\s*null/, name + " must not draw a surrounding card");
  assert.match(group, /elevation\s*=\s*0f/, name + " must not cast card shadow");
  assert.doesNotMatch(group, /plywoodBackground|recessedPlywoodBackground|hudPanelDrawable/, name + " must remain unboxed");
}
assert.doesNotMatch(online, /matchmakingOpponentArea|outer recessed plywood pocket/, "Opponent rail should be a direct child");
assert.match(online, /addView\(\s*matchmakingOpponentRail,/, "Opponent slots must remain displayed");
assert.match(opponent, /canvas\.drawRoundRect\(/, "Opponent slots retain their own backgrounds");
for (const part of ["playerCountSelectionBackground(", "playGamesSignInBackground(", "selectionOwnAvatar",
  "matchmakingSetupPanel", "matchmakingSearchPanel", "beginPublicMatchmaking()", "cancelPublicMatchmaking()"]) {
  assert.ok(online.includes(part), "Online feature must remain operational: " + part);
}
assert.ok(online.includes("gameMode != GameMode.ONLINE"), "Team Up hero must have correct top spacing");
const heroArtwork = region(hero, "override fun onDraw(", "private fun drawShield(", "Team Up hero drawing");
assert.doesNotMatch(heroArtwork, /LinearGradient\(/, "Team Up hero must not repaint a panel background");
assert.doesNotMatch(heroArtwork, /canvas\.drawRoundRect\(/, "Team Up hero must not draw a rectangular card border");
assert.match(heroArtwork, /canvas\.drawCircle\(/, "Standalone Team Up globe artwork must remain");


const page = region(friends, "val content = LinearLayout(this).apply", "contentHost.addView(content, contentLayoutParams)", "Friends page host");
assert.match(page, /background\s*=\s*null/, "Friends must not have a page-wide background");
assert.match(page, /Gravity\.TOP or Gravity\.CENTER_HORIZONTAL/, "Friends page must top-align its sections");
assert.doesNotMatch(page, /plywoodBackground|recessedPlywoodBackground|hudPanelDrawable/, "Friends page shell must be invisible");
const section = region(kit, "fun flatSection(", "fun rowSurface(", "flat Friends section factory");
assert.match(section, /background\s*=\s*null/);
assert.match(section, /elevation\s*=\s*0f/);
assert.doesNotMatch(section, /plywoodBackground|recessedPlywoodBackground|hudPanelDrawable/);
assert.doesNotMatch(friends, /FriendsVisualKit\.compactCard\(|FriendsVisualKit\.card\(/);
assert.match(friends, /FriendsVisualKit\.rowSurface\(this@FriendsActivity\)/, "Request and invite actions keep a clickable surface");
for (const item of ["buildIdentitySection()", "buildAddFriendSection()", "roomContainer = FriendsVisualKit.flatSection",
  "friendsContainer = FriendsVisualKit.flatSection", "inboxAction(", "renderRoom()", "renderInboxCards()",
  "renderFriends()", "playerCountSelectionBackground(", "showRequestsDialog()", "showInvitesDialog()"]) {
  assert.ok(friends.includes(item), "Friends page behavior/component missing: " + item);
}
for (const part of ["background = inputSurface(context)", "background = buttonBackground(context, style, enabled)"]) {
  assert.ok(kit.includes(part), "Interactive inputs/buttons must keep their styles");
}

// Online has one title, fixed to the same top control bar as Robot, during both
// player selection and searching. Keep the independent connection-status row
// inside each state, but never repeat the big ONLINE heading in scroll content.
const onlineScreen = read("feature/online/presentation/MainActivityScreen.kt");
const fixedStart = region(online, "internal fun MainActivity.onlineFixedTitle()", "private fun MainActivity.onlineModePanelHeader()", "fixed ONLINE title");
assert.match(fixedStart, /text = "ONLINE"/, "ONLINE fixed title text");
assert.match(fixedStart, /ModeArtView\.Mode\.ONLINE/, "ONLINE title icon");
assert.match(fixedStart, /background = null/, "ONLINE title must stay unboxed");
assert.match(fixedStart, /15f else 19f/, "ONLINE title matches Robot size");
const topBar = region(onlineScreen, "val topActionsBar =", "setContentView(root)", "fixed Online Back/Settings bar");
assert.match(topBar, /if \(gameMode == GameMode\.ONLINE\)/, "fixed heading must be Online-only");
assert.match(topBar, /onlineFixedTitle\(\)/, "title is attached to fixed top bar");
assert.match(topBar, /Gravity\.TOP or Gravity\.CENTER_HORIZONTAL/, "title is centered between Back and Settings");
assert.match(topBar, /topMargin = dp\(14\)/, "title aligns with Back/Settings vertically");
assert.match(onlineScreen, /topActionsBar\.translationY = safeTopInset\.toFloat\(\)/, "title follows safe cutout inset");
const scrollingHeader = region(online, "private fun MainActivity.onlineModePanelHeader()", "internal fun MainActivity.onlineLobbyPanel()", "scrolling header");
assert.match(scrollingHeader, /if \(gameMode != GameMode\.ONLINE\)/, "only Team Up keeps scrolling title");
assert.match(scrollingHeader, /this@onlineModePanelHeader\.connectionTexts\.add\(statusLabel\)/, "connection status still updates");
assert.equal((online.match(/onlineModePanelHeader\(\),/g) || []).length, 2,
  "initial setup and searching retain status rows without duplicated title");
console.log("Unboxed lobbies and fixed Online title gate passed; setup and matchmaking share the Robot-style top row.");
