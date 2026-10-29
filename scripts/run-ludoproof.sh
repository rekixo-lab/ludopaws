#!/usr/bin/env bash
# Compatibility entry: VS Code and terminal share the same main-app runner.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
exec node "$repo_root/scripts/android-local-testing.mjs" run "$@"
