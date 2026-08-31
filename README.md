# compose-preview-contracts

The wire contracts a Compose Preview client speaks — the daemon's JSON-RPC shapes, the
device catalog, the Build Tools API result shapes and the agent-grant vocabulary — published
to Maven Central under `ee.schimke.composeai`, without the daemon implementation behind them.

Extracted from [yschimke/compose-ai-tools](https://github.com/yschimke/compose-ai-tools)
(see [#4732](https://github.com/yschimke/compose-ai-tools/issues/4732)) with history.

## What is published

| coordinate | what it is |
| --- | --- |
| `daemon-protocol` | the `@Serializable` request / response / notification shapes, the stream frame header and the reason enums |
| `daemon-devices` | the device catalog and the `spec:` parser |
| `daemon-bta` | Build Tools API shapes — `CompileErrorDetail`, `SourceChangeSet` |
| `agent-grant-protocol` | the `--agent-grants` vocabulary; the server mints, the client asks |
| `ui-builder-protocol` | versioned catalog, design, command, collaboration and transport-envelope shapes shared by the UI builder's browser, server and MCP clients |

`daemon-protocol` depends on **no other `ee.schimke.composeai` module**: the payload schemas that
appear as protocol fields — `SemanticsDelta`, `ThemeDelta`, `PreviewOverrideValue`, the pipeline
and extension descriptors — are declared **in** `daemon-protocol`, because a wire field's type
belongs to the wire. Until 2.1.0 it `api`-exported four other modules, and deserialising one
message meant resolving 9,111 lines of ABI across five coordinates to reach 21 types.

Also published, and not wire contracts: `data-render-core`, `data-layoutinspector-core`,
`data-theme-core`, `data-preview-overrides-core` (the differs and planners that *produce* those
shapes) and `common-io`. They are here because `compose-preview serve` depends on all five and
must resolve them by coordinate — see
[`docs/VERSIONING.md`](docs/VERSIONING.md#the-five-stay-published-and-that-is-the-point).

`ui-builder-protocol` is deliberately independent of Compose and the preview server. Its v1 DTOs
describe catalog capabilities, persisted designs, typed edit commands, collaboration snapshots and
deltas, and HTTP/MCP envelopes. It contains no reducer, store, renderer or transport implementation.

Every module is `explicitApi()` with Kotlin ABI validation wired into `check`, because these
are contracts two repositories compile across.

## What is deliberately NOT here

**`render-session-api`.** It is on #4732's contract list and it was in the original scope for
this repo. It is not here because its `api` dependency is `:daemon:core` — the daemon
*implementation*, 122 source files with 48 dependents in the upstream build. Its own
`build.gradle.kts` says why: consumers see `RenderSession`'s parameters as
`ee.schimke.composeai.daemon.protocol.*` and it re-exposes them rather than duplicating DTOs.

Publishing it from here would therefore publish the daemon from here, which is the opposite of
what a contracts repo is for. Extracting it needs its ABI narrowed to `daemon-protocol` first,
and that is a change to the upstream module, not a packaging decision.

**Anything that reads a file, opens a socket, or computes a result.** The split is shape
versus behaviour. `HistoryDataDelta` (a shape) is here; `HistoryDataDiff` (which reads two
archived entries off disk to produce one) stays upstream.

## Cross-repo checks

Two things in this repo can only be verified against an upstream checkout, and both are wired
to do so rather than quietly dropped:

- **`docs/daemon/protocol-fixtures/`** — the cross-language wire goldens. The Kotlin suite here
  and the TypeScript suite in
  [compose-preview-vscode](https://github.com/yschimke/compose-preview-vscode) parse the same
  files; that shared parse is the drift check. This repo is now their canonical home.
- **`DeviceDimensionsCatalogDriftTest`** — `DeviceDimensions` is duplicated in
  `gradle-plugin/preview-discovery` upstream, and the catalog and `spec:` grammar have drifted
  before. The test looks for a checkout via `COMPOSE_AI_TOOLS_ROOT`, then a sibling
  `../compose-ai-tools`, and **skips** rather than fails when it finds neither — "you have not
  checked out the other repo" is not a drift signal. CI sets it, so the check still gates.

## Versioning

**Decided: independent versioning at cutover, and no publishing to Maven Central before
then.** See [docs/VERSIONING.md](docs/VERSIONING.md).

The short version: this repository holds a *copy* of modules yschimke/compose-ai-tools still
builds and publishes, so both would publish `ee.schimke.composeai:daemon-protocol` — and the
seeded version points at releases that already exist there. `ComposeAiMavenPublishingPlugin`
refuses every Central publish task until the cutover, with that explanation.
`publishToMavenLocal` still works and is what CI uses.

Until cutover the version here is bookkeeping. Nothing consumes it.

## Build

```sh
./gradlew check              # compile, test, ktfmt, checkKotlinAbi
./gradlew publishToMavenLocal
COMPOSE_AI_TOOLS_ROOT=../compose-ai-tools ./gradlew check   # includes the cross-repo drift check
```
