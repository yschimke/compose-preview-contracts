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
MANIFEST=publishing-manifest.json
fail=0

# A publish set makes every skipped module read its version from `publishing-manifest.json`, which
# the release job's plan writes and nothing commits. Stand one in, and put back whatever was there.
backup=""
if [ -e "$MANIFEST" ]; then
  backup="$(mktemp)"
  cp "$MANIFEST" "$backup"
fi
restore() {
  if [ -n "$backup" ]; then mv "$backup" "$MANIFEST"; else rm -f "$MANIFEST"; fi
}
trap restore EXIT

# A Gradle failure must fail the test, not read as an empty task list — which is exactly the answer
# the empty case is looking for.
tasks() {
  local out
  out="$("$GRADLEW" -q printPublishTasks "$@")" || { echo "::error::printPublishTasks $* failed" >&2; exit 1; }
  printf '%s\n' "$out" | cut -f1 | sed '/^$/d'
}

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

rm -f "$MANIFEST"
all="$(tasks)"
expect "with no publish set lists the BOM" "yes" \
  "$(grep -qx ':bom:publishAndReleaseToMavenCentral' <<<"$all" && echo yes || echo no)"
expect "with no publish set lists common-io" "yes" \
  "$(grep -qx ':common-io:publishAndReleaseToMavenCentral' <<<"$all" && echo yes || echo no)"
modules="$(sed -e '/^:bom:/d' -e 's/:publishAndReleaseToMavenCentral$//' -e 's/^://' -e 's/:/-/g' <<<"$all")"
expect "with no publish set lists more than the BOM and one module" "yes" \
  "$([ "$(wc -l <<<"$modules")" -gt 1 ] && echo yes || echo no)"

# Every module recorded as already published, as the plan's `--write-manifest` would leave it.
{
  echo '{"modules": {'
  sed 's/.*/  "&": "0.0.1"/' <<<"$modules" | paste -sd, -
  echo '}}'
} > "$MANIFEST"

expect "with an empty publish set lists nothing" "" "$(tasks -Pcomposeai.publishSet=)"

expect "with one module lists it and the BOM" \
  "$(printf '%s\n' :bom:publishAndReleaseToMavenCentral :common-io:publishAndReleaseToMavenCentral)" \
  "$(tasks -Pcomposeai.publishSet=common-io)"

exit "$fail"
