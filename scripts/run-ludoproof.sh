#!/usr/bin/env bash
# Ctrl+Shift+B: incremental main-app build, install, cold launcher start.
# Unit tests and lint are deliberately separate from this user-invoked task.
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
android_dir="$repo_root/android"
package_name="com.rekixo.ludopaws"

fail() { printf '%s\n' "$*" >&2; exit 1; }

command -v java >/dev/null 2>&1 || [[ -x "${JAVA_HOME:-}/bin/java" ]] ||
  fail 'Java is missing. Install JDK 17 or newer and set JAVA_HOME.'

sdk_dir=""
for candidate in "${LUDOPROOF_ANDROID_SDK:-}" "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
  if [[ -n "$candidate" && -x "$candidate/platform-tools/adb" ]]; then
    sdk_dir="$candidate"
    break
  fi
done
[[ -n "$sdk_dir" ]] || fail 'Android SDK not found. Set ANDROID_HOME to the SDK containing platform-tools/adb.'
export ANDROID_HOME="$sdk_dir" ANDROID_SDK_ROOT="$sdk_dir"
adb="$sdk_dir/platform-tools/adb"

# Update only sdk.dir; preserve any other private local build properties.
local_properties="$android_dir/local.properties"
sdk_property="${sdk_dir//\\/\\\\}"
sdk_property="${sdk_property//:/\\:}"
sdk_property="${sdk_property// /\\ }"
sdk_property="${sdk_property//#/\\#}"
sdk_property="${sdk_property//!/\\!}"
temporary="$(mktemp "$android_dir/local.properties.XXXXXX")"
trap 'rm -f -- "$temporary"' EXIT
if [[ -f "$local_properties" ]]; then
  sed '/^[[:space:]]*sdk\.dir[[:space:]]*=/d' "$local_properties" > "$temporary"
fi
printf '\nsdk.dir=%s\n' "$sdk_property" >> "$temporary"
if [[ ! -f "$local_properties" ]] || ! cmp -s -- "$temporary" "$local_properties"; then
  mv -- "$temporary" "$local_properties"
fi

adb_args=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb_args=(-s "$ANDROID_SERIAL")
  [[ "$("$adb" "${adb_args[@]}" get-state)" == "device" ]] || fail 'Selected phone is not authorized/connected.'
else
  devices=()
  while IFS= read -r serial; do
    [[ -z "$serial" ]] || devices+=("$serial")
  done < <("$adb" devices | awk 'NR > 1 && $2 == "device" {print $1}')
  [[ ${#devices[@]} -gt 0 ]] || fail 'No authorized Android device. Connect the phone and approve USB/wireless debugging.'
  [[ ${#devices[@]} -eq 1 ]] || fail 'Multiple devices connected. Set ANDROID_SERIAL to choose one.'
  adb_args=(-s "${devices[0]}")
fi

# Keep iterative installs compatible with the previous phase-3 versionCode 10.
# Never use adb -d or uninstall to work around an incompatible installed build.
installed_code="$("$adb" "${adb_args[@]}" shell dumpsys package "$package_name" 2>/dev/null |
  sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' | head -n 1 || true)"
version_code="${ORG_GRADLE_PROJECT_LUDOPAWS_VERSION_CODE:-10}"
[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || fail 'LUDOPAWS_VERSION_CODE must be a positive integer.'
if [[ "$installed_code" =~ ^[1-9][0-9]*$ && "$installed_code" -gt "$version_code" ]]; then
  if [[ -n "${ORG_GRADLE_PROJECT_LUDOPAWS_VERSION_CODE:-}" ]]; then
    fail "Installed versionCode $installed_code is newer than requested $version_code; refusing a downgrade."
  fi
  version_code="$installed_code"
fi

printf 'Building the main Ludo Paws app for %s (no tests or lint)...\n' "${adb_args[1]}"
bash "$android_dir/gradlew" -p "$android_dir" :app:assembleDebug \
  "-PLUDOPAWS_VERSION_CODE=$version_code" \
  "-PLUDOPAWS_VERSION_NAME=${ORG_GRADLE_PROJECT_LUDOPAWS_VERSION_NAME:-1.0.0-local-main}" \
  "-PPLAY_GAMES_PROJECT_ID=${ORG_GRADLE_PROJECT_PLAY_GAMES_PROJECT_ID:-314526321614}" \
  --console=plain --build-cache --parallel

apk="$android_dir/app/build/outputs/apk/debug/app-debug.apk"
if ! "$adb" "${adb_args[@]}" install -r -t "$apk"; then
  fail 'Install failed. If signatures differ, use a compatible signing/Play update path. This task never uninstalls or clears game data.'
fi
"$adb" "${adb_args[@]}" shell am force-stop "$package_name"
"$adb" "${adb_args[@]}" shell am start -W -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER -n "$package_name/com.rekixo.ludopaws.HomeActivity"
printf '\nMain Ludo Paws installed and launched.\n'
