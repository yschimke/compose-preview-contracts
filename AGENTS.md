# AGENTS.md

Published wire contracts, extracted from
[yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools). Read `README.md`
first — it says what is here, what is deliberately not, and why.

## The rules that matter here

**Every module is a published contract.** `explicitApi()` is on, and `checkKotlinAbi` is wired
into `check` for every module. An implicitly-public declaration is an API decision nobody made,
and an unrecorded ABI change is a break for a consumer in another repository that will not find
out until it bumps. If `checkKotlinAbi` fails, either the change was not intended to be public
or the dump needs updating *deliberately* — `./gradlew updateKotlinAbi`.

**Shape, never behaviour.** If a type reads a file, opens a socket or computes a result, it
does not belong here; it belongs in `:daemon:core` upstream. This is the whole basis of the
split, and the reason `render-session-api` is not here (README).

**Do not add a dependency without asking what it does to the consumers' floor.** Every
`ee.schimke.composeai` module on a POM here is a module a client must resolve.

**`:daemon-protocol` depends on no other module here, and must not start.** Until 2.1.0 it
`api`-exported the four `data-*-core` modules, so a client deserialising one message resolved
**9,111 lines of published ABI across five coordinates** to reach 21 types. Those types now live
in `:daemon-protocol` itself — a wire field's type belongs to the wire — and a consumer resolves
one coordinate and 5,695 lines. The arrow runs the other way now: the four `data-*-core` modules
take `:daemon-protocol` as `api`.

The five non-contract modules are still published, and that is deliberate. `compose-preview serve`
depends on all five, and `docs/design/PREVIEW_SERVER_SPLIT.md` upstream requires them to resolve
**by coordinate, from a repository** — its `preview-server/` build is deliberately not
`includeBuild`-ed so a missing coordinate is missed rather than silently satisfied from the
workspace. Un-publishing them would leave that probe green (it publishes to Maven Local at a probe
version) while the real coordinate stopped advancing: the exact failure that build exists to
prevent. So the bar for those five is not "is it a wire contract" but "does an extracted preview
server need it".

**This repository publishes to Maven Central, and nothing else does.** `compose-ai-tools`
consumes `ee.schimke.composeai:*` from here; it no longer builds these modules. A change to a
wire contract therefore reaches that repository only through a release — change here, release,
then bump `composeaiContractsVersion` there. That is the cost the split bought, and the reason
an ABI break is expensive: see [`docs/VERSIONING.md`](docs/VERSIONING.md).

**Run the formatter before committing.** `./gradlew ktfmtFormat` (or
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

Gradle updates follow the preset — the "queue everything on the dashboard until
cutover" rule is gone, because the cutover happened: this repository owns its
coordinates and versions on its own cadence (see
[`docs/VERSIONING.md`](docs/VERSIONING.md)).

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

## Releasing

Merging the `chore(main): release X.Y.Z` pull request is the whole release: it cuts the tag,
drafts the GitHub Release, publishes all thirteen coordinates from eleven modules to Maven Central, then
un-drafts. The UI builder's KMP module owns a metadata coordinate plus JVM and Wasm variants.

**A published coordinate is permanent.** Central does not accept a second upload of the same
GAV, and `publishToMavenCentral(automaticRelease = true)` promotes without a human looking. So
every check that can run before the upload does: `check` for all eleven modules, the tag against
`.release-please-manifest.json`, a `publishToMavenLocal` dry run asserting each module produced a
POM, and a credentials preflight that names any missing secret. The release stays a draft until
the upload succeeds, so a failure never leaves a release announcing artifacts that are not there.

### Secrets

Five, the same names `compose-ai-tools` uses:

| Secret | Maps to |
| --- | --- |
| `SIGNING_KEY` | `ORG_GRADLE_PROJECT_signingInMemoryKey` — the armoured GPG private key |
| `SIGNING_KEY_ID` | `ORG_GRADLE_PROJECT_signingInMemoryKeyId` |
| `SIGNING_KEY_PASSWORD` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` |
| `MAVEN_CENTRAL_USERNAME` | `ORG_GRADLE_PROJECT_mavenCentralUsername` — Central Portal token |
| `MAVEN_CENTRAL_PASSWORD` | `ORG_GRADLE_PROJECT_mavenCentralPassword` |

The signing three are needed by the **mavenLocal dry run as well**, not just the Central upload:
`ComposeAiMavenPublishingPlugin` signs every non-snapshot publication, so `publishToMavenLocal` at
a release version fails with `No configured signatory` without them. A snapshot skips signing,
which is why a local `./gradlew publishToMavenLocal` works with no keys at all.

### The manifest is the LAST released version

`.release-please-manifest.json` says what has already shipped, not what to ship next. It seeds
`1.46.2` and the cutover `feat!:` computes `2.0.0` from it; writing `2.0.0` there instead told
release-please 2.0.0 was already out and made it propose the version *after* one nothing had
tagged. `last-release-sha` in `release-please-config.json` keeps the 709 inherited upstream
commits out of this repository's first changelog. Both:
[`docs/VERSIONING.md`](docs/VERSIONING.md).

### release-please runs in two halves

`release-please.yml` invokes the action twice — once to cut (`skip-github-pull-request`) and once
afterwards for the PR (`skip-github-release`). Do not collapse them. A single invocation computes
the next candidate PR while the release it just cut is still a **tagless draft**, so that half
reads the previous release as its baseline and re-proposes the version being released. That is
compose-preview-vscode#5, which happened on that repository's first release.

## Cross-repo checks

`DeviceDimensionsCatalogDriftTest` compares a catalog duplicated in the upstream Gradle plugin.
It **skips** without a checkout. If you are changing `DeviceDimensions`, run it for real:

```sh
COMPOSE_AI_TOOLS_ROOT=../compose-ai-tools ./gradlew :daemon-devices:test
```

`docs/daemon/protocol-fixtures/` are the cross-language wire goldens, parsed here and by
compose-preview-vscode's TypeScript suite. Adding a protocol message means adding a fixture;
`MessagesTest.fixtureInventoryMatchesExpected` fails if you forget.
