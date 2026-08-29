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

That cost is concentrated where the contracts are thinnest. At cutover
`common-io` had 37 dependents upstream and `data-render-core` 28 — 65 of the ~70
call sites — while the four actual wire contracts had 8 between them. Neither of
those two is a wire contract; they are here only because the contracts re-export
them as `api`. **Narrowing that re-export is what would make this split pay**, and
it remains the open work.

## Consumers

| consumer | how it versions | how it pins |
| --- | --- | --- |
| [compose-preview-vscode](https://github.com/yschimke/compose-preview-vscode) | its own (`package.json`) | `composeAiPlugin` in `plugin-version.json`, a point pin on a compose-ai-tools **release** |
| compose-ai-tools | release-please, one version for that repository | `composeaiContractsVersion` in `gradle.properties`, a point pin on a release from here |

Both pin a point rather than a range today. A range is the eventual shape — it is
what lets a consumer take a patch without a pull request — but it needs a
compatibility story this repository has not yet had to state.
