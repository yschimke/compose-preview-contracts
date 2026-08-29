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
Repo-specific rules go in this repo's own `packageRules`, which are appended
after the preset and therefore override it.

**One config file, at the root.** Renovate refuses to run at all if it finds more
than one of `renovate.json`, `.github/renovate.json`, `.renovaterc` and friends.
The preset also covers GitHub Actions, so there is deliberately no
`dependabot.yml`; do not copy `compose-ai-tools`' across.

**Gradle updates queue on the dependency dashboard and never auto-land.** Until
cutover this repository is a copy of modules that still build and publish from
`compose-ai-tools`; CI checks the device-dimensions catalog against a checkout of
it, and publishing is hard-blocked (see [`docs/VERSIONING.md`](docs/VERSIONING.md)).
Moving the toolchain independently is divergence for no benefit while nothing here
ships. Delete that rule at cutover.

### Keep the catalog minimal — it is a safety mechanism

`gradle/libs.versions.toml` is pruned to exactly what the modules here reference:
**9 versions, 3 libraries, 4 plugins**. It arrived from `compose-ai-tools` whole,
at 174 entries, and that mattered more than tidiness:

- Renovate reads the **catalog**, not the build files, so every unused entry is a
  dependency it will offer to bump.
- **The build cannot fail on such a bump**, because nothing compiles against it.
  Green CI is not evidence for an unused entry.
- Most of those entries are held at a deliberate compatibility floor by
  `compose-ai-tools`' `.github/renovate.json` — published-ABI floors for Compose,
  coil3, compottie, material-kolor, `androidx.core`, `slf4j-nop`. Those ceiling
  rules were not carried across at the split.

The result was eight Renovate PRs, each past one of those floors, each with green
CI. They were closed and the catalog pruned.

**So: adding an entry here is a commitment.** If a module needing Android, Compose
or Robolectric dependencies moves into this repository, port the matching ceiling
rule from `compose-ai-tools`' `.github/renovate.json` **in the same change**. The
Robolectric `-SNAPSHOT` guard is kept in `renovate.json` as the worked example,
even though the catalog no longer carries Robolectric.

## Cross-repo checks

`DeviceDimensionsCatalogDriftTest` compares a catalog duplicated in the upstream Gradle plugin.
It **skips** without a checkout. If you are changing `DeviceDimensions`, run it for real:

```sh
COMPOSE_AI_TOOLS_ROOT=../compose-ai-tools ./gradlew :daemon-devices:test
```

`docs/daemon/protocol-fixtures/` are the cross-language wire goldens, parsed here and by
compose-preview-vscode's TypeScript suite. Adding a protocol message means adding a fixture;
`MessagesTest.fixtureInventoryMatchesExpected` fails if you forget.
