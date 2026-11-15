#!/usr/bin/env bash
# libphext-kotlin build step: compile and assemble the jar (Release-equivalent).
# Prerequisite: JDK 21. Gradle route, in order: ./gradlew, gradle on PATH,
# ~/tools/gradle-*/bin/gradle (the ranch node-local install).
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -x ./gradlew ]; then
  GRADLE="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"
elif ls ~/tools/gradle-*/bin/gradle >/dev/null 2>&1; then
  GRADLE="$(ls -d ~/tools/gradle-*/bin/gradle | sort -V | tail -1)"
else
  echo "ERROR: no Gradle found (./gradlew, PATH, or ~/tools/gradle-*)." >&2
  exit 1
fi

"$GRADLE" --no-daemon jar
echo "Built: build/libs/libphext-$(grep -m1 '^version' build.gradle.kts | grep -o '"[^"]*"' | tr -d '"').jar"
