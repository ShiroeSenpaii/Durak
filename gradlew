#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "gradle command not found. Install Gradle or use GitHub Actions setup-gradle step." >&2
  exit 1
fi
