// The compose-preview wire contracts, extracted from yschimke/compose-ai-tools.
//
// What is here is the closure of the four client-facing protocol modules — the shapes a
// preview client (the VS Code extension, an extracted `compose-preview serve`, any other
// consumer) needs in order to speak to the daemon — and nothing that implements them.
// See README.md for why `render-session-api` is NOT here.
pluginManagement {
  includeBuild("build-logic")
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "compose-preview-contracts"

// ---- the wire contracts -------------------------------------------------------------------

// The daemon's JSON-RPC request/response/notification shapes, the stream frame header and the
// reason enums that ride on it. Shape, never behaviour.
include(":daemon-protocol")

project(":daemon-protocol").projectDir = file("daemon/protocol")

// The device catalog. A client resolves the same table the backend renders against.
include(":daemon-devices")

project(":daemon-devices").projectDir = file("daemon/devices")

// Build Tools API shapes — `CompileErrorDetail`, `SourceChangeSet` — that cross the wire when a
// client reports or consumes a compile result.
include(":daemon-bta")

project(":daemon-bta").projectDir = file("daemon/bta")

// The agent-grant vocabulary. Both ends of `--agent-grants` speak it: the server mints, the
// client asks.
include(":agent-grant-protocol")

project(":agent-grant-protocol").projectDir = file("api/agent-grant-protocol")

// ---- the closure ---------------------------------------------------------------------------
//
// These are not "wire contracts" by intent, but `:daemon-protocol` re-exports the first four as
// `api` — their types appear in protocol fields — and `:daemon-bta` / `:data-render-core` need
// `:common-io`. A contract repo that published `daemon-protocol` without them would publish a POM
// that cannot resolve, so the boundary follows the ABI rather than the label.

// Payload schemas that appear as protocol fields: `SemanticsDelta` and `ThemeDelta` are
// `HistoryDataDelta` fields, `PreviewOverrideValue` is a `PreviewOverrides.namedOverrides` value,
// and the render descriptors ride on the render and extension messages.
include(":data-render-core")

project(":data-render-core").projectDir = file("data/render/core")

include(":data-layoutinspector-core")

project(":data-layoutinspector-core").projectDir = file("data/layoutinspector/core")

include(":data-theme-core")

project(":data-theme-core").projectDir = file("data/theme/core")

include(":data-preview-overrides-core")

project(":data-preview-overrides-core").projectDir = file("data/preview-overrides/core")

// Okio path/IO helpers. `:daemon-bta` and `:data-render-core` both take it as `implementation`.
include(":common-io")

project(":common-io").projectDir = file("common/io")
