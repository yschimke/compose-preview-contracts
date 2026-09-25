#!/usr/bin/env bash
# Runs the real `printPublishTasks` for the three shapes of `-Pcomposeai.publishSet` the release job
# passes, and checks what each returns. Run from the repository root; ci.yml runs it after `check`.
#
#   absent             no plan ran (a recovery run): every module AND the BOM
#   empty              the plan ran and found nothing: NO tasks, not even `:bom` — an identical BOM
#                      at a new version would spend quota to say nothing
#   one module         that module and the BOM, which indexes the new coordinate
#
# The empty case is the one that had never run: every release since the empty plan was introduced
# (#98) changed at least one module, so `publish-to-central.sh`'s `exit 0` had only ever been read.
set -euo pipefail

GRADLEW="${GRADLEW:-./gradlew}"
fail=0

tasks() { "$GRADLEW" -q printPublishTasks "$@" | cut -f1; }

expect() {
  local name="$1" want="$2" got="$3"
  if [ "$got" = "$want" ]; then
    echo "ok: $name"
  else
    echo "::error::printPublishTasks $name"
    echo "  want: $(printf '%s' "$want" | tr '\n' ' ')"
    echo "  got:  $(printf '%s' "$got" | tr '\n' ' ')"
    fail=1
  fi
}

all="$(tasks)"
expect "with no publish set lists the BOM" "yes" \
  "$(grep -qx ':bom:publishAndReleaseToMavenCentral' <<<"$all" && echo yes || echo no)"
expect "with no publish set lists every module" "yes" \
  "$(grep -qx ':daemon-protocol:publishAndReleaseToMavenCentral' <<<"$all" && [ "$(wc -l <<<"$all")" -gt 2 ] && echo yes || echo no)"

expect "with an empty publish set lists nothing" "" "$(tasks -Pcomposeai.publishSet=)"

expect "with one module lists it and the BOM" \
  "$(printf '%s\n' :bom:publishAndReleaseToMavenCentral :common-io:publishAndReleaseToMavenCentral)" \
  "$(tasks -Pcomposeai.publishSet=common-io)"

exit "$fail"
