#!/usr/bin/env bash
# Upload this release's modules to Maven Central: the body of release.yml's "Publish to Maven
# Central" step, extracted so that its branches have tests (.github/scripts/tests/). Until it was,
# the empty-plan branch had never run anywhere — every release since #98 changed some module.
#
# Environment:
#   TAG_NAME         the `vX.Y.Z` tag being published
#   PUBLISH_PLANNED  `true` when maven-publish-plan.sh ran, `false` for a recovery run
#   PUBLISH_SET      the plan's comma-separated artifact ids; empty is a real answer when planned
#   GRADLEW          the Gradle launcher (default ./gradlew); tests substitute a fake
#
# Absent and empty publish sets mean different things and must not be collapsed. A planned empty set
# is "no artifact changed": nothing is uploaded and the step succeeds. An unplanned run with no tasks
# is a broken build (a bare `./gradlew` would only run `help`), so it fails.
set -euo pipefail

: "${TAG_NAME:?TAG_NAME is required}"
: "${PUBLISH_PLANNED:?PUBLISH_PLANNED is required}"
GRADLEW="${GRADLEW:-./gradlew}"

# Ask Gradle for the task paths rather than deriving them here: the plan enumerates modules by
# artifact id and Gradle addresses them by project path. A hand-kept mapping between the two is
# exactly the artefact that goes stale silently, and stale here means publishing a module twice or
# dropping one out of a release.
PLAN_ARGS=()
if [ "$PUBLISH_PLANNED" = "true" ]; then
  PLAN_ARGS+=("-Pcomposeai.publishSet=${PUBLISH_SET:-}")
fi
TASKS="$("$GRADLEW" -q printPublishTasks "${PLAN_ARGS[@]}" | cut -f1 | tr '\n' ' ')"
TASKS="${TASKS%"${TASKS##*[! ]}"}"
echo "publishing: ${TASKS:-<nothing>}"

if [ -z "$TASKS" ]; then
  if [ "$PUBLISH_PLANNED" = "true" ]; then
    echo "the publish plan found no changed module; nothing to upload"
    exit 0
  fi
  echo "::error::no publish tasks resolved"
  exit 1
fi

# shellcheck disable=SC2086 # TASKS is a space-separated task list
PLUGIN_VERSION="${TAG_NAME#v}" "$GRADLEW" --no-daemon $TASKS "${PLAN_ARGS[@]}"
