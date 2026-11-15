#!/usr/bin/env bash
# libphext-kotlin publish step: there is NO registry publish for this port
# (no Maven Central / plugin portal release exists or is configured).
# Distribution is a git tag on the exo branch, as with libphext-cpp:
# this script tests + builds, then (only when PUBLISH_CONFIRM=1) creates
# and pushes the tag v<VERSION> at the current exo tip.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(grep -m1 '^version' build.gradle.kts | grep -o '"[^"]*"' | tr -d '"')"
echo "== libphext-kotlin publish: version ${VERSION} =="
echo "-- step 1/3: tests"
"$(dirname "$0")/test.sh"
echo "-- step 2/3: build"
"$(dirname "$0")/build.sh"
echo "-- step 3/3: tag"
echo "Will tag: v${VERSION} at $(git rev-parse --short HEAD) on branch $(git rev-parse --abbrev-ref HEAD), pushed to origin"
if [ "${PUBLISH_CONFIRM:-}" != "1" ]; then
  echo "STOP: dry run only. Re-run with PUBLISH_CONFIRM=1 to create and push the tag."
  exit 0
fi
git tag "v${VERSION}"
git push origin "v${VERSION}"
echo "Published libphext-kotlin v${VERSION} (git tag v${VERSION})."
