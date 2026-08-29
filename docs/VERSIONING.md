# Versioning

compose-ai-tools#4732 lists "decide the versioning story" as open, with two
options: independent versions with a compatibility range on the contracts, or
lockstep releases. This is that decision.

## The decision

**Independent versioning, at cutover. No publishing from here before then.**

Two parts, because they bind at different times.

### Now: this repository does not publish to Maven Central

It cannot, safely. It was seeded as a **copy** of the contract modules in
yschimke/compose-ai-tools, not as their new home — #4732 took the narrow cut, and
the cutover (that repository dropping these modules and consuming these artifacts
instead) has not happened. Both repositories therefore build
`ee.schimke.composeai:daemon-protocol` and its siblings.

Two repositories cannot own one coordinate. Whichever publishes second either
collides with a version that exists or silently replaces what the other shipped.
The seeded version sharpens it: `.release-please-manifest.json` starts at the
upstream release these modules were extracted from, so a release from here would
target versions **already on Central, published from there** — and
`publishToMavenCentral(automaticRelease = true)` promotes without review.

`ComposeAiMavenPublishingPlugin` refuses every Central publish task with that
explanation. `publishToMavenLocal` is untouched: it is how CI proves the POMs
resolve, and it reaches nobody.

Until cutover the version in this repository is **bookkeeping, not a contract**.
Nothing consumes it.

### At cutover: independent, with a declared compatibility range

Not lockstep, for three reasons.

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

So: this repository versions on its own cadence, driven by changes to the
contracts themselves, and consumers declare the range they work against.

### What cutover requires

- compose-ai-tools stops building these modules and depends on the published
  coordinates.
- **Delete the guard** in `ComposeAiMavenPublishingPlugin` — do not leave it
  behind a permanently-set flag. `-Pcomposeai.contracts.cutover=true` exists so
  an intentional dry run is possible, not as a setting.
- Re-base the version. Continuing from the seeded upstream number would imply a
  lineage this repository does not have.
- Consumers declare a range rather than a point pin.

## Consumers today

| consumer | how it versions | how it pins |
| --- | --- | --- |
| [compose-preview-vscode](https://github.com/yschimke/compose-preview-vscode) | its own (`package.json`), already independent | `composeAiPlugin` in `plugin-version.json`, a point pin on a compose-ai-tools **release** |
| compose-ai-tools | release-please, one version for that repository | builds its own copy of these modules; consumes nothing from here |

The extension is the shape this repository should take at cutover: its own
version, an explicit pin on what it consumes, and a gate that fails when the pin
and the vendored copy disagree.
