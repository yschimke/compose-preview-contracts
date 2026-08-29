# AGENTS.md

Published wire contracts, extracted from
[yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools). Read `README.md`
first — it says what is here, what is deliberately not, and why.

## The rules that matter here

**Every module is a published contract.** `explicitApi()` is on, and `checkKotlinAbi` is wired
into `check` for every module. An implicitly-public declaration is an API decision nobody made,
and an unrecorded ABI change is a break for a consumer in another repository that will not find
out until it bumps. If `checkKotlinAbi` fails, either the change was not intended to be public
or the dump needs updating *deliberately* — `./gradlew updateLegacyAbi`.

**Shape, never behaviour.** If a type reads a file, opens a socket or computes a result, it
does not belong here; it belongs in `:daemon:core` upstream. This is the whole basis of the
split, and the reason `render-session-api` is not here (README).

**Do not add a dependency without asking what it does to the consumers' floor.** Every
`ee.schimke.composeai` module on a POM here is a module a client must resolve. The four
`data-*-core` modules and `common-io` are here only because the wire contracts re-export them
as `api`; that is the bar.

**Run the formatter before committing.** `./gradlew ktfmtFormatAll` (or
`:<module>:ktfmtFormatMain :<module>:ktfmtFormatTest`). `check` runs `ktfmtCheck` and it is a
hard gate.

**Conventional commits** for commit subjects and PR titles (`fix:`, `feat:`, `docs:`, …) —
release-please reads them.

**Never attribute a commit to an AI agent.** Same rule as upstream: no `Co-authored-by:`
trailer naming an agent, and no agent identity as author or committer.

## Dependency updates

Renovate, configured in `renovate.json` at the repo root, extending the shared
preset [`yschimke/renovate-config`](https://github.com/yschimke/renovate-config).
The preset owns the schedule, the grouping-by-release-train philosophy, the
kotlinx `-compat` filter, `rangeStrategy: pin`, and automerge of minor/patch once
CI is green. Repo-specific rules go in this repo's own `packageRules`, which are
appended after the preset and therefore override it.

**One config file, at the root.** Renovate refuses to run at all if it finds more
than one of `renovate.json`, `.github/renovate.json`, `.renovaterc` and friends —
it fails with "Found multiple config file names" rather than picking one.

**Renovate also handles GitHub Actions** (the preset has a `github-actions`
group), so there is deliberately no `dependabot.yml` here. Upstream
`compose-ai-tools` splits the work the other way, because it predates the preset;
do not copy its `dependabot.yml` across.

**Gradle updates do not open PRs on their own here.** They queue on the
dependency dashboard and never auto-land, overriding the preset. Both reasons
expire at cutover:

1. **This repository is a copy, not yet the home.** These modules still build and
   publish from `compose-ai-tools`; CI checks the device-dimensions catalog
   against a checkout of it, and publishing is hard-blocked (see
   [`docs/VERSIONING.md`](docs/VERSIONING.md)). Letting the toolchain drift
   independently buys nothing while nothing here ships, and creates divergence to
   reconcile by hand later.
2. **The catalog is unpruned.** `gradle/libs.versions.toml` came across whole —
   174 entries, of which the nine modules here reference about 13. Renovate reads
   the catalog, not the build files, so unattended it would open PRs for ~160
   dependencies this repository does not consume.

**At cutover:** prune the catalog, then delete that rule. The Robolectric
`-SNAPSHOT` guard below it is written to keep applying, and
`gradle/libs.versions.toml` cites it by name.

## Cross-repo checks

`DeviceDimensionsCatalogDriftTest` compares a catalog duplicated in the upstream Gradle plugin.
It **skips** without a checkout. If you are changing `DeviceDimensions`, run it for real:

```sh
COMPOSE_AI_TOOLS_ROOT=../compose-ai-tools ./gradlew :daemon-devices:test
```

`docs/daemon/protocol-fixtures/` are the cross-language wire goldens, parsed here and by
compose-preview-vscode's TypeScript suite. Adding a protocol message means adding a fixture;
`MessagesTest.fixtureInventoryMatchesExpected` fails if you forget.
