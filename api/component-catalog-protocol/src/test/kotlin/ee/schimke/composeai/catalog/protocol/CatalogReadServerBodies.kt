package ee.schimke.composeai.catalog.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
 * Test-only copies of compose-preview-server 3.192.0's private response DTOs, field for field and in
 * declaration order, so the goldens in [CatalogReadGoldens] are pinned to what the server's own
 * serializer writes (`ServeHttpServer`'s companion `JSON = Json { encodeDefaults = true }`).
 *
 * Copied from `server/src/main/kotlin/ee/schimke/composeai/cli/serve/ServeHttpServer.kt`:
 * `StatusResponse`, `CatalogSummaryDto`, `DaemonSummaryDto`, `ConfigDto`, `CatalogDto`,
 * `RunningServerDto`, `FailureDto`, `PreviewsResponse`, `DegradationDto`, `PreviewDto`; and from
 * render-host's `WatchFaceRendererInfo`. A nested type a catalog reader never reads (render and
 * frame counters, cache and pool snapshots, feature flags) is a `JsonElement?` left null, which the
 * server writes as `null` whenever that lane is off. `overrides` entries are
 * `PreviewOverrideDeclaration`s, held as the JSON data-preview-overrides-core's serializer writes for
 * them: depending on that module, even from a test, would make the release plan treat it as this
 * module's dependency.
 */

@Serializable
internal data class ServerStatusResponse(
  val schema: String = "compose-preview-serve/status/v1",
  val version: String,
  val public: Boolean,
  val status: String,
  val uptimeSeconds: Long,
  val catalogs: ServerCatalogSummaryDto,
  val daemons: ServerDaemonSummaryDto,
  val config: ServerConfigDto,
  val catalogList: List<ServerCatalogDto>,
  val runningServers: List<ServerRunningServerDto>,
  val recentDaemonFailures: List<ServerFailureDto>,
  val branchFetch: JsonElement? = null,
  val themeOptimizer: JsonElement? = null,
  val themeCache: JsonElement? = null,
  val catalogCache: JsonElement? = null,
  val renderStats: JsonElement? = null,
  val liveFrames: JsonElement? = null,
  val playground: JsonElement? = null,
  val agentAccess: JsonElement? = null,
  val uiBuilder: JsonElement? = null,
  val featureFlags: JsonElement? = null,
  val processes: JsonElement? = null,
)

@Serializable
internal data class ServerCatalogSummaryDto(
  val total: Int,
  val listed: Int,
  val unlisted: Int,
  val trusted: Int,
  val degraded: Int,
  val loaded: Int,
  val failed: Int,
  val pending: Int,
)

@Serializable
internal data class ServerDaemonSummaryDto(
  val known: Int,
  val running: Int,
  val activeStreams: Int,
  val liveSeatsTotal: Int,
  val liveSeatsAvailable: Int,
  val liveSeatsUnbounded: Boolean,
  val perPreviewSeatsTotal: Int = 0,
  val perPreviewSeatsAvailable: Int = 0,
  val liveSeatRefusals: Long = 0,
  val liveSeatRefusalsUnverified: Long = 0,
  val leasedSessions: List<String> = emptyList(),
  val busyLeasedSessions: List<String> = emptyList(),
  val spareSandboxes: JsonElement? = null,
  val rcPlayerDaemon: JsonElement? = null,
  val rcDesktopPlayerDaemon: JsonElement? = null,
)

@Serializable
internal data class ServerConfigDto(
  val host: String,
  val port: Int,
  val allowRenderTrusted: Boolean,
  val trustStore: Boolean,
  val acceptBundles: Boolean,
  val acceptDocs: Boolean = false,
  val docTtlSeconds: Long = 0,
  val acceptImages: Boolean = false,
  val imageTtlSeconds: Long = 0,
  val imageUploadRepository: String? = null,
  val imagesHeld: Int = 0,
  val imageBytesHeld: Long = 0,
  val catalogRefreshSeconds: Long,
  val catalogRegistries: List<JsonElement> = emptyList(),
  val uiBuilderCatalogProblems: Map<String, String> = emptyMap(),
  val maxConcurrentRenders: Int,
  val liveSeats: Int,
)

@Serializable
internal data class ServerCatalogDto(
  val id: String,
  val listed: Boolean,
  val title: String? = null,
  val trust: String? = null,
  val previews: Int? = null,
  val failedRenders: Int = 0,
  val deferredPreviews: Int = 0,
  val live: Boolean,
  val running: Boolean,
  val degradation: String? = null,
  val repo: String? = null,
  val branch: String? = null,
  val generatedAt: String? = null,
  val composeAiToolsVersion: String? = null,
  val designParityVersion: String? = null,
  val path: String,
  val metaStale: Boolean = false,
  val loadState: String = "loaded",
  val loadError: String? = null,
  val lastLoadAttemptEpochMillis: Long? = null,
  val themeOptimization: JsonElement? = null,
  val renderCache: JsonElement? = null,
)

@Serializable
internal data class ServerRunningServerDto(
  val id: String,
  val label: String,
  val backend: String,
  val seatWeight: Int,
  val activeStreams: Int,
  val uptimeSeconds: Long? = null,
  val renderStats: JsonElement? = null,
  val liveFrames: JsonElement? = null,
  val daemonPools: List<JsonElement> = emptyList(),
)

@Serializable
internal data class ServerFailureDto(
  val atEpochMillis: Long,
  val session: String,
  val reason: String,
)

@Serializable
internal data class ServerPreviewsResponse(
  val schema: String = "compose-preview-serve/v3",
  val module: String,
  val catalogVersion: String? = null,
  val trust: String? = null,
  val degradations: List<ServerDegradationDto> = emptyList(),
  val views: Long = 0,
  val previews: List<ServerPreviewDto>,
)

@Serializable internal data class ServerDegradationDto(val code: String, val detail: String)

@Serializable
internal data class ServerPreviewDto(
  val id: String,
  val label: String,
  val modes: List<String>,
  val overrides: List<JsonElement> = emptyList(),
  val remoteComposeKnobs: List<JsonElement> = emptyList(),
  val spatial: Boolean = false,
  val liveOnly: Boolean = false,
  val views: Long = 0,
  val remoteCompose: Boolean = false,
  val watchFace: JsonObject? = null,
  val watchFaceRenderers: List<ServerWatchFaceRendererInfo> = emptyList(),
)

/** render-host's `ee.schimke.composeai.cli.serve.WatchFaceRendererInfo`, in field order. */
@Serializable
internal data class ServerWatchFaceRendererInfo(
  val id: String,
  val label: String,
  val badgeLabel: String,
  val available: Boolean,
  val unavailableReason: String? = null,
  val supportsLiveOverrides: Boolean = false,
  val isDefault: Boolean = false,
)
