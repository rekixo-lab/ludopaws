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
assert.equal((online.match(/topMargin = dp\(if \(isCompactOnline\(\)\) 64 else 72\)/g) || []).length, 2,
  "Both lobbies must start below fixed title and Back/Settings controls");
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
assert.match(fixedStart, /text = if \(isTeamUp\) "TEAM UP" else "ONLINE"/, "Both fixed title labels");
assert.match(fixedStart, /ModeArtView\.Mode\.TEAM_UP else ModeArtView\.Mode\.ONLINE/, "Both fixed mode icons");
assert.match(fixedStart, /background = null/, "ONLINE title must stay unboxed");
assert.match(fixedStart, /15f else 19f/, "ONLINE title matches Robot size");
const topBar = region(onlineScreen, "val topActionsBar =", "setContentView(root)", "fixed Online Back/Settings bar");
assert.match(topBar, /gameMode == GameMode\.ONLINE \|\| gameMode == GameMode\.TEAM_UP/, "fixed heading applies to both modes");
assert.match(topBar, /onlineFixedTitle\(\)/, "title is attached to fixed top bar");
assert.match(topBar, /Gravity\.TOP or Gravity\.CENTER_HORIZONTAL/, "title is centered between Back and Settings");
assert.match(topBar, /topMargin = dp\(14\)/, "title aligns with Back/Settings vertically");
assert.match(onlineScreen, /topActionsBar\.translationY = safeTopInset\.toFloat\(\)/, "title follows safe cutout inset");
const scrollingHeader = region(online, "private fun MainActivity.onlineModePanelHeader()", "internal fun MainActivity.onlineLobbyPanel()", "scrolling header");
assert.doesNotMatch(scrollingHeader, /ModeArtView|text = "TEAM UP"/, "Neither mode repeats its title in scrolling content");
assert.match(scrollingHeader, /this@onlineModePanelHeader\.connectionTexts\.add\(statusLabel\)/, "connection status still updates");
assert.equal((online.match(/onlineModePanelHeader\(\),/g) || []).length, 2,
  "initial setup and searching retain status rows without duplicated title");

const teamUpHeroCase = region(onlineScreen, "// The Team Up lobby has", "lobbyPanel =", "Team Up banner routing");
assert.match(teamUpHeroCase, /gameMode != GameMode\.ONLINE && gameMode != GameMode\.TEAM_UP/,
  "Team Up must not include the old oversized hero banner");
assert.equal((online.match(/teamUpDetails\(\),/g) || []).length, 2,
  "Team Up description must remain visible in both setup and search");
const details = region(online, "private fun MainActivity.teamUpDetails()", "private fun MainActivity.onlineModePanelHeader()", "flat Team Up details");
for (const snippet of ["Red + Yellow vs Green + Blue",
  "Remote server dice authority • exit forfeits your team",
  "background = null", "elevation = 0f"]) {
  assert.ok(details.includes(snippet), "Unboxed Team Up information missing: " + snippet);
}
const selectedAvatar = region(online, "val selectionOwnAvatar =", "addView(\n                    selectionOwnAvatar,", "Team Up setup character");
const searchAvatar = region(online, "val ownAvatar =", "addView(\n                    ownAvatar,", "Team Up searching character");
for (const [label, avatar] of [["setup", selectedAvatar], ["searching", searchAvatar]]) {
  assert.match(avatar, /if \(gameMode == GameMode\.TEAM_UP\)/,
    "Team Up " + label + " character must independently remove its plywood surface");
  assert.match(avatar, /background = null/, "Team Up " + label + " character needs no card");
  assert.match(avatar, /setPadding\(0, 0, 0, 0\)/, "Team Up " + label + " removes old recessed-card padding");
  assert.match(avatar, /else \{\s*background = recessedPlywoodBackground/,
    "Online " + label + " remains exactly as before");
}

// Critical routing regression: Home's Team Up entry opens TeamUpActivity, NOT
// MainActivity's generic Online lobby. Keep the actual Activity unboxed.
const teamSource = read("feature/team/presentation/TeamUpActivity.kt");
const teamUi = region(teamSource, "private fun buildUi()", "private fun teamCharacterTiles()", "real Team Up Activity");
assert.doesNotMatch(teamUi, /\bval teamPanel\b|teamPanel\.addView|\bval headerTitle\b/,
  "Actual Team Up screen must not construct the old plywood box or embedded heading");
assert.doesNotMatch(teamUi, /background\s*=\s*recessedPlywoodBackground/,
  "Team Up searching must not use recessed plywood behind the whole section");
assert.match(teamUi, /val fixedTeamTitle = LinearLayout\(this\)\.apply/,
  "Team Up title must be independent");
assert.match(teamUi, /text = "TEAM UP"/, "Correct Team Up title");
assert.match(teamUi, /if \(isCompactTeamLayout\(\)\) 15f else 19f/,
  "Team Up title must match Robot's text scale");
assert.match(teamUi, /topMargin = dp\(14\)/, "Team Up title must align with Back/Settings controls");
assert.match(teamUi, /Gravity\.TOP or Gravity\.CENTER_HORIZONTAL/,
  "Team Up title must sit between Back and Settings");
assert.match(teamUi, /topActionsBar\.translationY = safeTopInset\.toFloat\(\)/,
  "Fixed Team Up title must honor display cutout");
assert.match(teamUi, /content\.addView\(\s*setupContent,/, "Team Up setup must attach directly to page");
assert.match(teamUi, /content\.addView\(\s*searchPanel,/, "Team Up search must attach directly to page");
assert.match(teamUi, /content\.addView\(\s*statusText,/, "Team Up status must attach directly to page");
assert.match(teamUi, /val connectionStrip = LinearLayout\(this\)\.apply/,
  "Independent Team Up connection indicator retained");
assert.match(teamUi, /quickTeamButton = FrameLayout\(this\)\.apply/,
  "Find a Team button retained");
assert.match(teamUi, /teamSlotProgressView = TeamSlotProgressView/,
  "Team Up live searching progress retained");
assert.match(teamUi, /cancelButton = Button/,
  "Team Up cancel search button retained");
assert.match(teamSource, /setupContent\.visibility = if \(searching\) View\.GONE else View\.VISIBLE/,
  "Setup/search visibility state retained");
const teamAvatar = region(teamSource, "private fun teamCharacterTiles()", "private fun teamSetupPanel()", "independent team avatar");
assert.match(teamAvatar, /background = null/, "Team Up avatar needs no wooden card");
assert.doesNotMatch(teamAvatar, /recessedPlywoodBackground\(/);
const teamColors = region(teamSource, "private fun teamSelectionCard(", "private fun beginSearch()", "Team A/Team B illustrations");
assert.match(teamColors, /background = null/, "Static team color graphics must be independent");
assert.doesNotMatch(teamColors, /recessedPlywoodBackground\(/);
assert.match(teamSource, /Intent\(this, MainActivity::class\.java\)/,
  "Finished Team Up matchmaking must still enter the real gameplay Activity");
console.log("Flat lobbies and real TeamUpActivity fixed title/container regression checks passed.");
