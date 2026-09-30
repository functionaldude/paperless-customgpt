#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd -- "$repo_root"

java_command="java"
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_command="${JAVA_HOME}/bin/java"
fi

if ! java_version="$("$java_command" -version 2>&1)"; then
  echo "Codex setup requires a Java 25 JDK. Install Temurin 25 and configure JAVA_HOME or PATH." >&2
  exit 1
fi
if [[ ! "$java_version" =~ version\ \"25([.\"+-]) ]]; then
  echo "Codex setup requires Java 25; the selected Java has a different version." >&2
  echo "$java_version" >&2
  echo "Install Temurin 25 and configure JAVA_HOME or PATH." >&2
  exit 1
fi

./gradlew --no-daemon test bootJar
