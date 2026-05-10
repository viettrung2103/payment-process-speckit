#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

print() {
  printf "%s %s\n" "$1" "$2"
}

find_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "${JAVA_HOME}/bin/java" ]]; then
    echo "$JAVA_HOME"
    return 0
  fi

  if [[ "$OSTYPE" == darwin* ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      local candidate
      candidate=$( /usr/libexec/java_home -v 21 2>/dev/null || true )
      if [[ -n "$candidate" ]] && [[ -x "$candidate/bin/java" ]]; then
        echo "$candidate"
        return 0
      fi
    fi
  fi

  if command -v java >/dev/null 2>&1; then
    local java_cmd
    java_cmd=$(command -v java)
    local candidate
    candidate=$(dirname "$(dirname "$java_cmd")")
    if [[ -x "$candidate/bin/java" ]]; then
      echo "$candidate"
      return 0
    fi
  fi

  return 1
}

if java_home_value=$(find_java_home); then
  export JAVA_HOME="$java_home_value"
  print "[INFO]" "Using JAVA_HOME=$JAVA_HOME"
else
  print "[ERROR]" "JAVA_HOME is not defined or not valid."
  if [[ "$OSTYPE" == darwin* ]]; then
    print "[INFO]" "Attempting to set JAVA_HOME using /usr/libexec/java_home..."
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      if java_home_value=$( /usr/libexec/java_home -v 21 2>/dev/null ); then
        export JAVA_HOME="$java_home_value"
        print "[INFO]" "Auto-set JAVA_HOME=$JAVA_HOME"
      fi
    fi
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  print "[ERROR]" "Could not find a valid Java 21 installation."
  print "[ERROR]" "Please install Java 21 or set JAVA_HOME to a JDK installation."
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  print "[ERROR]" "Maven is not installed or not on PATH."
  exit 1
fi

exec mvn "$@"
