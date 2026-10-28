#!/usr/bin/env bash
set -Eeuo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
AVD_HOME="${ANDROID_AVD_HOME:-/tmp/ludopaws-avd}"
AVD_NAME="ludopaws-ci"
EMULATOR="$SDK_ROOT/emulator/emulator"
PID_FILE="/tmp/ludopaws-emulator.pid"

if [[ -z "$SDK_ROOT" || ! -x "$EMULATOR" ]]; then
  echo "Android emulator executable was not found under SDK_ROOT='$SDK_ROOT'." >&2
  exit 2
fi

boot_emulator() {
  local wipe_data="${1:-false}"
  local log_file="${2:-/tmp/ludopaws-emulator-retry.log}"

  adb kill-server >/dev/null 2>&1 || true
  sleep 2
  rm -f "$AVD_HOME/$AVD_NAME.avd"/*.lock

  # Keep the retry AVD's software-rendered framebuffer aligned with the
  # lower-load settings configured by the workflow's initial boot.
  local config_file="$AVD_HOME/$AVD_NAME.avd/config.ini"
  if [[ -f "$config_file" ]]; then
    for setting in "hw.lcd.width=720" "hw.lcd.height=1600" "hw.lcd.density=320"; do
      local key="${setting%%=*}"
      if grep -q "^$key=" "$config_file"; then
        sed -i "s|^$key=.*|$setting|" "$config_file"
      else
        printf '%s\n' "$setting" >> "$config_file"
      fi
    done
  fi

  # Pin the SwiftShader backend rather than the generic software selector.
  # Emulator 37.2.12 selected swangle/lavapipe with -gpu software and
  # segfaulted mid-suite on the hosted Linux runner.
  local args=(
    -avd "$AVD_NAME"
    -no-window
    -noaudio
    -no-boot-anim
    -no-snapshot
    -camera-back none
    -camera-front none
    -gpu swiftshader
    -feature -Vulkan
  )
  if [[ "$wipe_data" == "true" ]]; then
    args+=(-wipe-data)
  fi

  nohup "$EMULATOR" "${args[@]}" > "$log_file" 2>&1 &
  local emulator_pid=$!
  echo "$emulator_pid" > "$PID_FILE"

  local device_ready=""
  for attempt in $(seq 1 90); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
      echo "Android emulator exited before ADB became ready."
      cat "$log_file"
      return 1
    fi
    if adb get-state 2>/dev/null | grep -q '^device$'; then
      device_ready="1"
      break
    fi
    sleep 2
  done

  if [[ "$device_ready" != "1" ]]; then
    echo "Android emulator did not become visible to ADB within 180 seconds."
    cat "$log_file"
    adb devices -l || true
    return 1
  fi

  local booted=""
  for attempt in $(seq 1 120); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
      echo "Android emulator exited before boot completed."
      cat "$log_file"
      return 1
    fi
    booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$booted" == "1" ]]; then
      break
    fi
    sleep 2
  done

  if [[ "$booted" != "1" ]]; then
    echo "Android emulator did not complete boot within 240 seconds after ADB connected."
    cat "$log_file"
    adb devices -l || true
    return 1
  fi

  adb shell input keyevent 82 || true
  adb shell settings put global window_animation_scale 0
  adb shell settings put global transition_animation_scale 0
  adb shell settings put global animator_duration_scale 0
  adb devices -l
}

run_instrumentation() {
  # Fail a stuck test with its JUnit evidence rather than spending the entire
  # job budget on a UI-idle wait. Full production-engine simulations get 5 min.
  gradle -p android :app:connectedDebugAndroidTest --stacktrace \
    -Pandroid.testInstrumentationRunnerArguments.timeout_msec=300000 "$@"
}

run_with_transport_retry() {
  local phase="$1"
  shift
  local first_status
  local retry_log="/tmp/ludopaws-emulator-retry-${phase//[^a-zA-Z0-9]/-}.log"

  echo "=== Instrumentation phase: $phase ==="
  set +e
  run_instrumentation "$@"
  first_status=$?
  set -e

  if [[ "$first_status" -eq 0 ]]; then
    return 0
  fi

  # If the emulator is still alive, preserve the actual assertion/build failure
  # rather than retrying and obscuring the useful first failure.
  if adb get-state 2>/dev/null | grep -q '^device$'; then
    echo "Instrumentation phase '$phase' failed while the emulator is still connected; not retrying." >&2
    return "$first_status"
  fi

  echo "Instrumentation phase '$phase' lost its emulator; restarting a clean emulator and retrying this phase once." >&2
  if [[ -f "$PID_FILE" ]]; then
    local old_pid
    old_pid="$(cat "$PID_FILE")"
    kill "$old_pid" >/dev/null 2>&1 || true
    for attempt in $(seq 1 10); do
      if ! kill -0 "$old_pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    kill -9 "$old_pid" >/dev/null 2>&1 || true
  fi

  boot_emulator true "$retry_log"
  run_instrumentation "$@"
}

# Run the activity-heavy remote match tests against a fresh emulator before the
# full suite's long offline match simulation can build up emulator/GPU pressure.
# Then run all other instrumentation tests without repeating this class.
run_with_transport_retry \
  "remote-ui-focused" \
  "-Pandroid.testInstrumentationRunnerArguments.class=com.rekixo.ludopaws.RemoteMatchUiInstrumentedTest"

run_with_transport_retry \
  "remaining-suite" \
  "-Pandroid.testInstrumentationRunnerArguments.notClass=com.rekixo.ludopaws.RemoteMatchUiInstrumentedTest"
