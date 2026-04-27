#!/usr/bin/env sh
set -eu
if [ -z "${JAVA_HOME:-}" ] && [ -x "$HOME/.local/share/mise/installs/java/17.0.2/bin/java" ]; then
  export JAVA_HOME="$HOME/.local/share/mise/installs/java/17.0.2"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
exec gradle "$@"
