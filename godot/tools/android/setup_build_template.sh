#!/usr/bin/env bash
# Installs Godot's Android build template into android/build (git-ignored, ~1 GB with build
# output) and applies the project's tracked overlay (android_overlay/: single launch screen
# resources + GodotApp.java keeping the system splash until the engine runs).
# Usage: tools/android/setup_build_template.sh   (run from the project root)
set -euo pipefail
VERSION="4.7.2.stable"
TEMPLATES="${GODOT_TEMPLATES:-$HOME/.var/app/org.godotengine.Godot/data/godot/export_templates/$VERSION}"
if [ ! -f android/build/build.gradle ]; then
  mkdir -p android/build
  unzip -q -o "$TEMPLATES/android_source.zip" -d android/build
  echo "$VERSION" > android/.build_version
  touch android/build/.gdignore
fi
# Keep the launch resources in sync with assets/branding/launch (source of truth).
cp assets/branding/launch/launch_logo.png assets/branding/launch/launch_branding.png android_overlay/res/drawable-xxxhdpi/
cp -r android_overlay/res/. android/build/res/
cp android_overlay/src/main/java/com/godot/game/GodotApp.java android/build/src/main/java/com/godot/game/GodotApp.java
echo "Android build template ready (overlay applied)."
# Register the project's Android plugin (Godot v2 plugin discovery via manifest meta-data).
mkdir -p android/build/src/main/java/com/rekixo/ludopaws/platform
cp android_overlay/src/main/java/com/rekixo/ludopaws/platform/*.java android/build/src/main/java/com/rekixo/ludopaws/platform/
MANIFEST=android/build/src/main/AndroidManifest.xml
if ! grep -q "org.godotengine.plugin.v2.LudoPawsPlatform" "$MANIFEST"; then
  python3 - "$MANIFEST" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
anchor = 'tools:ignore="GoogleAppIndexingWarning" >'
meta = anchor + '\n        <meta-data\n            android:name="org.godotengine.plugin.v2.LudoPawsPlatform"\n            android:value="com.rekixo.ludopaws.platform.LudoPawsPlatformPlugin" />'
assert anchor in s, "manifest anchor not found"
open(p, "w").write(s.replace(anchor, meta, 1))
PY
fi
echo "LudoPawsPlatform plugin registered."
# Live Google dependencies (same versions as live app/build.gradle.kts).
GRADLE=android/build/build.gradle
if grep -q "ludopaws-live-deps" "$GRADLE" && ! grep -q "billingclient:billing" "$GRADLE"; then
  sed -i 's#    implementation "com.google.android.gms:play-services-games-v2:22.1.0"#&\n    implementation "com.android.billingclient:billing:9.1.0"#' "$GRADLE"
fi
if ! grep -q "ludopaws-live-deps" "$GRADLE"; then
  python3 - "$GRADLE" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
anchor = 'implementation "androidx.documentfile:documentfile:$versions.documentfileVersion"'
deps = anchor + '''
    // ludopaws-live-deps
    implementation "androidx.credentials:credentials:1.6.0"
    implementation "androidx.credentials:credentials-play-services-auth:1.6.0"
    implementation "com.google.android.libraries.identity.googleid:googleid:1.1.1"
    implementation "com.google.android.gms:play-services-games-v2:22.1.0"
    implementation "com.android.billingclient:billing:9.1.0"'''
assert anchor in s, "gradle anchor not found"
open(p, "w").write(s.replace(anchor, deps, 1))
PY
fi
# Play Games manifest entries exactly as live (init provider removed, profile creation suppressed).
if ! grep -q "com.google.android.gms.games.APP_ID" "$MANIFEST"; then
  python3 - "$MANIFEST" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
anchor = '        <meta-data\n            android:name="org.godotengine.plugin.v2.LudoPawsPlatform"'
add = '''        <provider android:name="com.google.android.gms.games.provider.PlayGamesInitProvider" tools:node="remove" />
        <meta-data
            android:name="com.google.android.gms.games.APP_ID"
            android:value="@string/game_services_project_id" />
        <meta-data
            android:name="com.google.android.gms.games.SUPPRESS_GAME_PROFILE_CREATION"
            android:value="true" />
'''
assert anchor in s, "plugin meta-data anchor not found"
open(p, "w").write(s.replace(anchor, add + anchor, 1))
PY
fi
# Public Play Games project id from the git-ignored local config (CI: PLAY_GAMES_PROJECT_ID).
GAMES_ID="${PLAY_GAMES_PROJECT_ID:-$(python3 -c "
import configparser,sys
c=configparser.ConfigParser(); c.read('config/release.local.cfg')
print(c.get('play','games_app_id',fallback='0').strip('\"'))" 2>/dev/null || echo 0)}"
case "$GAMES_ID" in (*[!0-9]*|'') GAMES_ID=0;; esac
cat > android/build/res/values/ludopaws_games.xml <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="game_services_project_id" translatable="false">$GAMES_ID</string>
</resources>
XML
echo "Live Google dependencies + Play Games entries applied."
# Live room links (RoomLinkActivity filters): verified https App Link + legacy ludopaws scheme,
# on their own alias (Godot's variant manifest merges the launcher alias attributes-only).
if ! grep -q 'RoomLinkAlias' "$MANIFEST"; then
  python3 - "$MANIFEST" <<'PY'
import re, sys
p = sys.argv[1]; s = open(p).read()
# Drop filters an older setup run placed on the launcher alias.
s = re.sub(r'\n            <intent-filter( android:autoVerify="true")?>\n                <action android:name="android.intent.action.VIEW" />.*?</intent-filter>', '', s, flags=re.S)
alias = """        <activity-alias
            android:name=".RoomLinkAlias"
            android:targetActivity=".GodotApp"
            android:exported="true">
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="api.ludopaws.rekixo.com" android:pathPrefix="/play/" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="ludopaws" android:host="api.ludopaws.rekixo.com" android:pathPrefix="/play/" />
            </intent-filter>
        </activity-alias>
    </application>"""
assert "</application>" in s
open(p, "w").write(s.replace("    </application>", alias, 1))
PY
fi
echo "Room link filters applied."
# Google Mobile Ads 25.5.0 + UMP 4.0.0 (live versions) and the live manifest rules: the SDK init
# provider is removed (ads start only after consent) and ad-id / topics / audio permissions are
# stripped. Test builds always use Google's TEST App ID; LUDOPAWS_RELEASE=1 (release workflow)
# uses the App ID from the git-ignored config / CI variable ADMOB_APP_ID.
if ! grep -q "com.google.android.play:app-update" "$GRADLE"; then
  sed -i 's#    implementation "com.android.billingclient:billing:9.1.0"#&\n    implementation "com.google.android.play:app-update:2.1.0"\n    implementation "com.google.android.play:review:2.0.2"\n    implementation "com.android.installreferrer:installreferrer:2.2"\n    implementation "com.google.android.play:integrity:1.6.0"#' "$GRADLE"
fi
if ! grep -q "play-services-ads" "$GRADLE"; then
  sed -i 's#    implementation "com.android.billingclient:billing:9.1.0"#&\n    implementation "com.google.android.gms:play-services-ads:25.5.0"\n    implementation "com.google.android.ump:user-messaging-platform:4.0.0"#' "$GRADLE"
fi
ADMOB_ID="ca-app-pub-3940256099942544~3347511713"
if [ "${LUDOPAWS_RELEASE:-0}" = "1" ]; then
  ADMOB_ID="${ADMOB_APP_ID:-$(python3 -c "
import configparser
c=configparser.ConfigParser(); c.read('config/release.local.cfg')
print(c.get('admob','app_id',fallback='').strip('\"'))" 2>/dev/null)}"
fi
python3 - "$MANIFEST" "$ADMOB_ID" <<'PY'
import re, sys
p, app_id = sys.argv[1], sys.argv[2]
s = open(p).read()
s = re.sub(r'\n        <meta-data\n            android:name="com.google.android.gms.ads.APPLICATION_ID"\n            android:value="[^"]*" />', '', s)
s = s.replace('<application ', '<application ', 1)
anchor = '        <meta-data\n            android:name="org.godotengine.plugin.v2.LudoPawsPlatform"'
add = '        <meta-data\n            android:name="com.google.android.gms.ads.APPLICATION_ID"\n            android:value="%s" />\n' % app_id
if 'com.google.android.gms.ads.MobileAdsInitProvider' not in s:
    add += '        <provider android:name="com.google.android.gms.ads.MobileAdsInitProvider" android:authorities="${applicationId}.mobileadsinitprovider" tools:node="remove" />\n'
s = s.replace(anchor, add + anchor, 1)
perms = ["com.google.android.gms.permission.AD_ID", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_ADSERVICES_AD_ID",
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION", "android.permission.ACCESS_ADSERVICES_TOPICS"]
for perm in perms:
    if perm not in s:
        s = s.replace('    <application', '    <uses-permission android:name="%s" tools:node="remove" />\n    <application' % perm, 1)
open(p, "w").write(s)
PY
echo "Ads dependencies and manifest rules applied (App ID: ${ADMOB_ID%%~*}~…)."
