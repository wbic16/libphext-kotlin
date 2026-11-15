#!/usr/bin/env bash
# libphext-kotlin test step: run the full JUnit 5 suite against THIS checkout,
# including the shared 0.6 chain conformance fixtures (tests/fixtures/chains).
# Prerequisite: JDK 21. Gradle route as in scripts/build.sh.
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

"$GRADLE" --no-daemon test "$@"
