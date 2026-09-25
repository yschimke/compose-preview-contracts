# Versioning

compose-ai-tools#4732 listed "decide the versioning story" as open, with two
options: independent versions with a compatibility range on the contracts, or
lockstep releases. This is that decision, and the cutover it was waiting on.

## The decision

**Independent versioning. This repository owns these coordinates.**

### Cutover: done

This repository publishes `ee.schimke.composeai:daemon-protocol` and its siblings
to Maven Central. `compose-ai-tools` no longer builds them — it consumes the
published artifacts.

Before cutover it could not publish at all, and `ComposeAiMavenPublishingPlugin`
refused every Central task to enforce that: this repository held a **copy** of
modules that still built and published upstream, and two repositories cannot own
one coordinate — whichever publishes second either collides with a version that
exists or silently replaces what the other shipped. That guard is deleted, which
is what cutover means. Do not reintroduce it.

### The version was re-based to 2.0.0

Not continued from `1.46.2`. That number was the upstream release these modules
were extracted from, and carrying it forward would imply a lineage this
repository does not have — the artifacts published under `1.4x` came from
`compose-ai-tools`, built from its tree.

**Re-basing had to go up, not down.** `1.46.2` is on Central already. A restart at
`1.0.0` would be ranked *older* by Gradle and Renovate, so "latest" would resolve
to the stale upstream-built artifact — a silent downgrade for anyone not pinning
exactly. `2.0.0` is a genuine re-base that is still unambiguously the newest
thing under these coordinates.

**2.0.0 is derived, not declared.** `.release-please-manifest.json` seeds
`1.46.2`, because release-please reads the manifest as *the version already
released* — not the one to release next. The cutover commit is a `feat!:`, so
the first release cut here computes to `2.0.0` on its own. Writing `2.0.0` into
the manifest instead said "2.0.0 is out already" and made release-please propose
the version *after* it.

`release-please-config.json` also pins `last-release-sha` to that `1.46.2`
release commit — the parent of the extraction. Without it release-please walks
all 709 inherited upstream commits and writes their history into this
repository's first changelog.

**`last-release-sha` is a root option, not a per-package one.** Nested inside
`packages["."]` it is silently ignored: the schema's package object accepts
unknown keys, so neither `jq` nor a schema validator objects, and release-please
walks the whole history regardless. That is what killed the second run — it died
with `other side closed` part-way through backfilling those commits. Only the
root `properties` list carries the key, and the only real evidence it is taking
effect is a run that stops walking.

One consequence, until the first release lands: `ComposeAiMavenPublishingPlugin`
derives dev snapshots from the same manifest, so a local `publishToMavenLocal`
stamps `1.46.3-SNAPSHOT`. Nothing consumes it, and it corrects itself the moment
`2.0.0` is released and the manifest moves.

### Independent, not lockstep

Three reasons, unchanged by cutover:

1. **Lockstep forces empty releases.** compose-ai-tools releases far more often
   than its wire contracts change — most releases touch renderers, the CLI, the
   daemon implementation. Keeping the numbers equal means cutting contract
   releases that contain nothing, and asking consumers to distinguish the real
   ones from the noise.
2. **Nothing can enforce it.** Two repositories with two release trains stay in
   step only by a discipline no gate applies. A version that is *supposed* to
   match and quietly does not is worse than one that never claimed to.
3. **Contracts are the stable half — that is the whole premise.** #4732's case
   for splitting rests on the contract surface being stable while the render path
   churns. A version that moves with the churn contradicts the reason for the
   split.

## What cutover cost, and what it bought

The price is **atomicity**. Before, a change to a wire contract and its consumers
was one pull request and one CI run. Now it is: change here → release here →
bump the coordinate in `compose-ai-tools` → adopt. Two repositories and a release
in between, for every contract change.

That cost was concentrated where the contracts were thinnest, and **the narrowing
is now done** — see below.

## The narrowing (2.1.0)

At cutover this repository published nine coordinates. Five of them were not wire
contracts:

| | published ABI | types `:daemon-protocol` actually used |
| --- | --- | --- |
| `data-layoutinspector-core` | 2,030 lines | 2 |
| `data-render-core` | 1,535 | 3 |
| `data-preview-overrides-core` | 371 | 1 |
| `data-theme-core` | 276 | 1 |
| `common-io` | 16 | — (see below) |

`:daemon-protocol` `api`-exported the first four, so a client deserialising a single
daemon message resolved **9,111 lines of ABI across five coordinates** to reach
seven types.

The fix was not to weaken the export — the export was correct, those types really
are protocol fields. It was to put the types where they belong. The transitive
closure of the seven roots is 21 types (plus two one-constant `*DiffProduct`
objects), and they now live in `:daemon-protocol` under
`ee.schimke.composeai.daemon.protocol`. A consumer resolves **one coordinate and
5,695 lines** — 37% less ABI, and `daemon-protocol`'s POM carries no
`ee.schimke.composeai` dependency at all. The arrow runs the other way now: the
four `data-*-core` modules take `:daemon-protocol` as `api`.

`common-io` was a different story, and the earlier version of this document had it
wrong: it was never `api`-re-exported. It was `implementation` in two places, used
by two files importing one symbol — `SystemFileSystem`, which is
`okio.FileSystem.SYSTEM` behind an alias. `:daemon-bta`'s call site now uses okio
directly; `:data-render-core` still takes it.

### The five stay published, and that is the point

They are not wire contracts, and an earlier draft of this change removed them from
this repository entirely. That was wrong. `compose-preview serve` depends on all
five, and compose-ai-tools' `docs/design/PREVIEW_SERVER_SPLIT.md` builds
`preview-server/` as a **separate Gradle build, deliberately not `includeBuild`-ed**,
precisely so every contract resolves "as a published artifact, by coordinate, from a
repository" and a missing one is *missed*.

Un-publishing them would have kept that probe green — it publishes to Maven Local at
a fixed probe version — while the real coordinate stopped advancing at 2.0.0. A gate
that stays green while the thing it guards rots is the failure mode that build exists
to prevent, so the narrowing stops at the re-export and leaves the coordinates alone.

The cost of that choice is the one this document already names: compose-ai-tools
keeps a cross-repo release hop for those five. For the preview-server split, their
being published coordinates is the goal rather than the cost.

## 2.19.0 ships an ABI break as a minor

`FigmaSvgText` and `FigmaSvgTextSpan` each gained a constructor parameter in #70, which changes
their primary constructors, defaults-synthetics and `copy()`. A consumer compiled against 2.18.x
and run against 2.19.0 takes a `NoSuchMethodError`. Its `feat!:` subject would have computed
`3.0.0`.

It ships as a minor anyway, and the reason is the last paragraph of [Consumers](#consumers): both
consumers pin a **point**, not a range. Nothing resolves a contracts version transitively today, so
the mixed-version failure a major is there to prevent needs someone to assemble it by hand. A major
would spend the disruption — every pin bumped, every range rewritten if any existed — to signal a
hazard the pinning model already forecloses.

That reasoning is load-bearing, not a blanket exemption. **The moment a consumer takes a range, an
ABI break needs its major back**, because then resolution can reach a version nobody chose. The
compatibility story that paragraph says this repository has not yet had to state is exactly the one
that would have to be written first.

It was cut with `release-as` in `release-please-config.json` rather than a
`Release-As:` footer in a commit. The footer route works only as long as the squashed message keeps
it at footer position: this repository does preserve commit bodies through a squash, so a
single-commit pull request would have carried one, but a two-commit pull request buries it
mid-message where release-please will not read it — and the failure is invisible until the wrong
version ships. A config key is data rather than a message, so no merge strategy can drop it.

That key is **sticky** — it pins every later run to the same version — so it was removed as soon as
2.19.0 was released. Anything reaching for it again should remove it in the same way, immediately
after the release it was added for.

## A release publishes only what changed

One version line does not mean every coordinate uploads at every tag. Each coordinate-publish is
twenty files against a Maven Central file-count limit shared by all of yschimke's publishing
repositories, so `release.yml` asks
[`.github/scripts/maven-publish-plan.sh`](../.github/scripts/maven-publish-plan.sh) which modules
this release has to upload. A skipped module keeps the version it last published at, and the POMs
and the BOM name it there (`PublishedVersions.resolve`). A module publishes when:

1. **something that ships from it changed** since the tag *it* last published at — its baseline is
   read from Maven Central, not from the previous release. A change confined to its test source
   sets (`src/test`, `src/jvmTest`, `src/commonTest`, … — anything ending in `Test`, which
   `src/testFixtures` does not) cannot reach its artifact and does not count;
2. **a module it depends on publishes**, transitively, so a POM never names a sibling version that
   was not uploaded; or
3. **a shared build input changed**: `build-logic/` (except `build-logic/src/test/`), `gradle/`,
   the wrapper, `settings.gradle.kts` or the root `build.gradle.kts`. These publish everything,
   with one narrowing — the version catalog.

**The version catalog is diffed entry by entry** (`gradle/libs.versions.toml` at the module's
baseline tag against the release). Every version, library, plugin and bundle is resolved — a
`version.ref` replaced by the version it names, a bundle by its libraries — so a changed version ref
changes every alias that uses it. A changed alias dirties only the published modules whose build
scripts name it (`libs.okio`, `libs.plugins.kotlin.jvm`, `libs.bundles.x`, `libs.versions.kotlin`,
or a literal `findVersion("…")`). Everything publishes instead when:

- a changed alias is named by a script that reaches every module — build-logic, the root build,
  settings, or a build script outside a published module (the BOM's). `kotlin` is always such an
  alias;
- a script looks the catalog up by a name the plan cannot read (a computed `find…()`, an alias
  enumeration); or
- the catalog cannot be parsed, or a section other than those four changed.

**AGP is the one build-logic input confined further.** Every published module applies
`composeai.android-conventions`, but that plugin touches AGP only inside
`withPlugin("com.android.…")`, so an AGP bump reaches only modules that apply an Android plugin —
of which there are none today. The plan re-proves that on every run rather than trusting it: AGP's
API may appear only in `ComposeAiAndroidConventionsPlugin.kt`, and the new AGP's POM must not ask
for a newer `kotlin-gradle-plugin` than the catalog's `kotlin`, which Gradle would otherwise put on
the convention classpath and so change the compiler every module is built with. Either check
failing, or the POM being unreadable, publishes everything.

Every uncertainty resolves to "publish". Central refuses a second upload of a version, so a needless
publish costs quota while a wrongly skipped one cannot be repaired.

Measured over 2026-09-20 to 09-25 (compose-ai-tools#5532, this repository's #111): seven releases
published 54 coordinates against a full baseline of 119, and both full publishes came from rule 3 —
v3.5.0 for an AGP bump, v3.7.0 for an edit to `printPublishTasks` in the root build script. The
catalog narrowing turns the first into a one-module release; the second is still a full publish,
because the root build script remains a shared input.

**An empty plan is a real answer.** `-Pcomposeai.publishSet=` (empty) makes `printPublishTasks` list
nothing, not even `:bom`, and
[`publish-to-central.sh`](../.github/scripts/publish-to-central.sh) then uploads nothing and succeeds;
the property's *absence* — a `workflow_dispatch` recovery run — still means "publish everything".
Both branches, and the plan's rules above, are tested in `.github/scripts/tests/`, which `ci.yml`
runs on every pull request, alongside a run of the real `printPublishTasks` for each shape of the
property.

## Consumers

| consumer | how it versions | how it pins |
| --- | --- | --- |
| [compose-preview-vscode](https://github.com/yschimke/compose-preview-vscode) | its own (`package.json`) | `composeAiPlugin` in `plugin-version.json`, a point pin on a compose-ai-tools **release** |
| compose-ai-tools | release-please, one version for that repository | `composeaiContractsVersion` in `gradle.properties`, a point pin on a release from here |

Both pin a point rather than a range today. A range is the eventual shape — it is
what lets a consumer take a patch without a pull request — but it needs a
compatibility story this repository has not yet had to state.
