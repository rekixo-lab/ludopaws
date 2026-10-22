import fs from "node:fs";
import assert from "node:assert/strict";

const base = "android/app/src/main/java/com/rekixo/ludopaws/";
const read = name => fs.readFileSync(base + name, "utf8");
const offline = read("feature/offline/presentation/setup/OfflineSetupScreenUi.kt");
const online = read("feature/online/presentation/components/OnlineLobbyUi.kt");
const friends = read("feature/friends/presentation/FriendsActivity.kt");
const kit = read("feature/friends/presentation/FriendsVisualKit.kt");
const opponent = read("feature/online/presentation/components/OnlineMatchmakingSearchView.kt");

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
console.log("Unboxed Online/Team Up/Friends page gate passed; interactive controls, selectable tiles and page flows preserved.");
