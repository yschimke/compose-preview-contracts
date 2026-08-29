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

// ---- published, but not wire contracts ------------------------------------------------------
//
// `:daemon-protocol` depends on NONE of these. Until 2.1.0 it `api`-exported the four `data-*-core`
// modules, so a client deserialising one message resolved 9,111 lines of ABI across five
// coordinates to reach 21 types; those types now live in `:daemon-protocol` itself.
//
// They stay HERE, and stay published, because `compose-preview serve` depends on all five and
// `docs/design/PREVIEW_SERVER_SPLIT.md` in compose-ai-tools requires them to resolve **by
// coordinate, from a repository** — its `preview-server/` build is deliberately not
// `includeBuild`-ed so that a missing coordinate is missed. Un-publishing them would keep that
// probe green (it publishes to Maven Local) while the real coordinate stopped advancing.
//
// So the bar for this section is not "is it a wire contract" — it is "does an extracted preview
// server need it". Nothing may move from here into `:daemon-protocol`'s dependencies.

// The differs, planners and stores that PRODUCE the wire shapes. The shapes themselves moved to
// `:daemon-protocol`; these four take it as `api` now, which is the arrow the other way round.
include(":data-render-core")

project(":data-render-core").projectDir = file("data/render/core")

include(":data-layoutinspector-core")

project(":data-layoutinspector-core").projectDir = file("data/layoutinspector/core")

include(":data-theme-core")

project(":data-theme-core").projectDir = file("data/theme/core")

include(":data-preview-overrides-core")

project(":data-preview-overrides-core").projectDir = file("data/preview-overrides/core")

// Okio path/IO helpers. `:data-render-core` takes it as `implementation`; `:daemon-bta` no longer
// does (it used one alias for `FileSystem.SYSTEM` and now names okio directly).
include(":common-io")

project(":common-io").projectDir = file("common/io")
