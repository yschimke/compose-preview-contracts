@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.catalog.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The preview server's **catalog read API**: the routes a client outside the server reads catalogs
 * and their previews from, by method and path.
 *
 * Level 1 was derived from compose-preview-server 3.192.0, which serves every level-1 route, so the
 * server conforms unchanged. Level 2 adds the capabilities, history and guidelines routes, the
 * `ETag` on every JSON read and [CatalogReadPreviewsV1.revision], typed from the bodies of the
 * server change that first serves them (compose-preview-server#2092). The route table, the
 * credentials, caching, the gaps (data the hosted catalog MCP reads in process that no route
 * serves) and how the set is versioned are in `docs/design/CATALOG_READ_API.md`. Path templates
 * name their parameters in braces; a client substitutes the percent-encoded path segment.
 *
 * Every JSON body here is decoded with `ignoreUnknownKeys = true`: the types are the part of each
 * body a client may rely on, and the server sends more (operator counters, engagement, caches).
 *
 * Shape only: no client, no server, no URL building.
 */
public object CatalogReadRoutesV1 {
  /**
   * The API name these routes are versioned under: `DeploymentApisV1.CATALOG_READ` in
   * `deployment-protocol`.
   */
  public const val API_NAME: String = "catalog-read"

  /**
   * [STATUS_PATH], [PREVIEWS_PATH] and [RENDER_PNG_PATH] (with [PIN_QUERY]): what every preview
   * server since 3.192.0 serves.
   */
  public const val API_LEVEL_1: Int = 1

  /**
   * Level 1 plus [CAPABILITIES_PATH], [HISTORY_PATH], [PREVIEW_HISTORY_PATH], [GUIDELINES_PATH],
   * [ETAG_HEADER] / [IF_NONE_MATCH_HEADER] revalidation on those four and [PREVIEWS_PATH], and
   * [CatalogReadPreviewsV1.revision]. Only additions: a level-1 reader reads a level-2 server.
   */
  public const val API_LEVEL_2: Int = 2

  /** The level of [API_NAME] these routes describe: [API_LEVEL_2]. */
  public const val API_LEVEL: Int = API_LEVEL_2

  /**
   * The `serveSchema` a preview server's `/version` reports when it serves this API, at level 1 or
   * 2: the server did not change it for level 2. Until the server reports `apis["catalog-read"]`
   * itself, this says level 1 at least, and an [ETAG_HEADER] on [PREVIEWS_PATH] says level 2.
   */
  public const val SERVE_SCHEMA: String = "compose-preview-serve/v3"

  /** The path parameter naming one catalog (the server calls it a system, or a session). */
  public const val SYSTEM_PARAMETER: String = "system"

  /** The path parameter naming one preview of a catalog. */
  public const val PREVIEW_ID_PARAMETER: String = "previewId"

  /**
   * `GET`: every configured catalog, its load state and provenance, as a [CatalogReadStatusV1].
   * Never resumes an idle catalog, so a catalog not resident since the server started reports no
   * preview count.
   */
  public const val STATUS_PATH: String = "/status.json"

  /**
   * `GET`: one catalog's previews and their published metadata, as a [CatalogReadPreviewsV1].
   * Resumes the catalog if it is idle. `503` with `Retry-After` while the catalog is still loading
   * after a restart, `404` for a catalog the server does not have.
   */
  public const val PREVIEWS_PATH: String = "/{system}/api/previews"

  /**
   * The same body as [PREVIEWS_PATH] for the catalog [SESSION_QUERY] names, or the host's default
   * catalog. On a host serving one catalog as a top-level site, the site's catalog wins over
   * [SESSION_QUERY].
   */
  public const val ROOT_PREVIEWS_PATH: String = "/api/previews"

  /** `?session=<system>` on [ROOT_PREVIEWS_PATH] and [ROOT_RENDER_PNG_PATH]. */
  public const val SESSION_QUERY: String = "session"

  /**
   * `GET` / `HEAD`: one preview's published render, as `image/png` bytes. Without render parameters
   * this replays the published snapshot and needs only `preview` scope; any override parameter
   * makes it a live render, which is the render contract's, not this one's.
   */
  public const val RENDER_PNG_PATH: String = "/{system}/render/{previewId}.png"

  /** [RENDER_PNG_PATH] for the catalog [SESSION_QUERY] names. */
  public const val ROOT_RENDER_PNG_PATH: String = "/render/{previewId}.png"

  /**
   * `?at=<commit>` on [RENDER_PNG_PATH]: the preview's render as published at that delivery-branch
   * commit, 7 to 40 lower-case hex, never a ref. `404` when that publish has no render for the
   * preview; `400` together with a render parameter.
   */
  public const val PIN_QUERY: String = "at"

  /**
   * `?gen=<generation>` on [RENDER_PNG_PATH]: the catalog generation a page was drawn from. A
   * current one makes the published render cacheable as `immutable` on a public host.
   */
  public const val GENERATION_QUERY: String = "gen"

  /**
   * The response header naming the lane that answered a render: `baked` for published bytes,
   * otherwise the live lane (`daemon`, …). An informational value, not a cache key.
   */
  public const val GENERATION_HEADER: String = "X-Compose-Preview-Generation"

  /** The `Retry-After` seconds a still-loading catalog is answered with. */
  public const val LOADING_RETRY_AFTER_SECONDS: Int = 3

  /**
   * Level 2. `GET`: what each preview of one catalog can answer, as a [CatalogReadCapabilitiesV1].
   * Leases the catalog as [PREVIEWS_PATH] does, with the same `503` and `404`.
   */
  public const val CAPABILITIES_PATH: String = "/{system}/api/capabilities"

  /** [CAPABILITIES_PATH] for the catalog [SESSION_QUERY] names, or the host's default. */
  public const val ROOT_CAPABILITIES_PATH: String = "/api/capabilities"

  /**
   * Level 2. `GET`: where one catalog's history lives and the delivery commit it serves, as a
   * [CatalogReadHistoryV1].
   */
  public const val HISTORY_PATH: String = "/{system}/api/history"

  /** [HISTORY_PATH] for the catalog [SESSION_QUERY] names, or the host's default. */
  public const val ROOT_HISTORY_PATH: String = "/api/history"

  /**
   * Level 2. `GET`: one preview's render timeline, as a [CatalogReadPreviewHistoryV1]. `404`, text
   * `no such preview`, for a preview the catalog does not have.
   */
  public const val PREVIEW_HISTORY_PATH: String = "/{system}/api/history/{previewId}"

  /** [PREVIEW_HISTORY_PATH] for the catalog [SESSION_QUERY] names, or the host's default. */
  public const val ROOT_PREVIEW_HISTORY_PATH: String = "/api/history/{previewId}"

  /**
   * Level 2. `GET`: one preview's published guidelines result, as a [CatalogReadGuidelinesV1].
   * `404`, text `no such preview`, for a preview the catalog does not have; `200` with `found:
   * false` for one with no result.
   */
  public const val GUIDELINES_PATH: String = "/{system}/api/guidelines/{previewId}"

  /** [GUIDELINES_PATH] for the catalog [SESSION_QUERY] names, or the host's default. */
  public const val ROOT_GUIDELINES_PATH: String = "/api/guidelines/{previewId}"

  /**
   * Level 2. The validator on every `200` of [PREVIEWS_PATH], [CAPABILITIES_PATH], [HISTORY_PATH],
   * [PREVIEW_HISTORY_PATH] and [GUIDELINES_PATH]: always weak, `W/"…"`, and opaque. A refusal never
   * carries one.
   */
  public const val ETAG_HEADER: String = "ETag"

  /**
   * Level 2. A request header naming [ETAG_HEADER] values the client holds; a match (weak
   * comparison, or `*`) answers `304 Not Modified` with no body and the same [ETAG_HEADER].
   */
  public const val IF_NONE_MATCH_HEADER: String = "If-None-Match"

  /**
   * Level 2. The `Cache-Control` on [CAPABILITIES_PATH], [HISTORY_PATH], [PREVIEW_HISTORY_PATH] and
   * [GUIDELINES_PATH]: keep a copy, revalidate it every time, never in a shared cache.
   * [PREVIEWS_PATH] sends no `Cache-Control`.
   */
  public const val READ_CACHE_CONTROL: String = "private, no-cache"
}

/**
 * How a non-browser client presents a credential to the catalog read routes. Every name is the one
 * the preview server reads.
 *
 * On a `--public` host the routes need no credential. Otherwise a request needs one of: the
 * operator token, a browser's [BROWSE_COOKIE], or an agent grant with `preview` scope. A request
 * holding none is answered `404` with text `not found`, never `401`, so a gated host does not
 * reveal what it serves.
 */
public object CatalogReadCredentialsV1 {
  /** The operator token or an agent grant token, as a header. */
  public const val TOKEN_HEADER: String = "X-Compose-Preview-Token"

  /**
   * The operator token or an agent grant token, as a query parameter. When present it is the only
   * place the operator token is looked for: a wrong `?token=` is not rescued by a right
   * [TOKEN_HEADER].
   */
  public const val TOKEN_QUERY: String = "token"

  /**
   * The scheme an agent grant token is presented under in `Authorization`. The operator token is
   * never accepted as a bearer.
   */
  public const val BEARER_SCHEME: String = "Bearer"

  /** The cookie a browser holds after exchanging the operator token's `?token=` link. */
  public const val BROWSE_COOKIE: String = "cp_browse"

  /** The cookie a browser holds after exchanging an agent grant's link. */
  public const val AGENT_GRANT_COOKIE: String = "cp_agent_grant"

  /** The agent grant scope every route here needs on a gated host. */
  public const val PREVIEW_SCOPE: String = "preview"
}

/**
 * The part of `GET` [CatalogReadRoutesV1.STATUS_PATH] a catalog reader relies on: which host
 * answered and its catalogs.
 *
 * The server's body is its operator status (`compose-preview-serve/status/v1`) and carries much
 * more: daemons, configuration, caches, grant counts. Those are monitoring detail, not this
 * contract, and a reader ignores them.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadStatusV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  /** The server's released version, as `/version` reports it. */
  public val version: String,
  /** Whether the host serves token-free. */
  public val public: Boolean,
  /** [STATUS_OK], or [STATUS_DEGRADED] while any catalog load or daemon start has failed. */
  public val status: String,
  /**
   * Every catalog the host is configured with, listed or not, in configuration order. On a host
   * serving one catalog as a top-level site, only that catalog.
   */
  @EncodeDefault public val catalogList: List<CatalogReadCatalogV1> = emptyList(),
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(version: String, public: Boolean) {
    public var schema: String = SCHEMA
    public var version: String = version
    public var public: Boolean = public
    public var status: String = STATUS_OK
    public var catalogList: List<CatalogReadCatalogV1> = emptyList()

    public fun build(): CatalogReadStatusV1 =
      CatalogReadStatusV1(schema, version, public, status, catalogList)
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(version, public).also {
      it.schema = schema
      it.status = status
      it.catalogList = catalogList
    }

  public companion object {
    /** The schema id the preview server's status has always sent. */
    public const val SCHEMA: String = "compose-preview-serve/status/v1"

    public const val STATUS_OK: String = "ok"
    public const val STATUS_DEGRADED: String = "degraded"
  }
}

/**
 * One catalog on [CatalogReadStatusV1.catalogList].
 *
 * Read without resuming the catalog: a catalog that is idle reports what it last reported while
 * resident ([metaStale]), and one never resident since the server started has no [previews] count.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadCatalogV1
internal constructor(
  /** The catalog's id: the `{system}` segment of every per-catalog route. */
  public val id: String,
  /** Whether the front page lists it. An unlisted catalog is still served at its routes. */
  public val listed: Boolean,
  /** The catalog's display name; null when the server has not seen it resident. */
  @EncodeDefault public val title: String? = null,
  /**
   * Producer trust: `signature:<keyId>`, `branch:<repo>@<branch>`, `provenance:<id>` or
   * `unverified`. Null means unknown, never untrusted.
   */
  @EncodeDefault public val trust: String? = null,
  /** How many previews it serves; null when unknown without resuming it. */
  @EncodeDefault public val previews: Int? = null,
  /** Previews whose published render failed. */
  @EncodeDefault public val failedRenders: Int = 0,
  /** Previews published without a render, so every render of them is live. */
  @EncodeDefault public val deferredPreviews: Int = 0,
  /** Whether it has a live render lane (running now, or idle and resumable). */
  public val live: Boolean,
  /** Whether its live daemon is up right now. */
  public val running: Boolean,
  /** Why it serves published bytes only, when it does. */
  @EncodeDefault public val degradation: String? = null,
  /** The repository its delivery branch lives in, `owner/name`. */
  @EncodeDefault public val repo: String? = null,
  /** Its delivery branch. */
  @EncodeDefault public val branch: String? = null,
  /** When its producer generated the published catalog. */
  @EncodeDefault public val generatedAt: String? = null,
  /** The compose-ai-tools version that rendered it. */
  @EncodeDefault public val composeAiToolsVersion: String? = null,
  /** The design-parity exporter version, when that producer recorded one. */
  @EncodeDefault public val designParityVersion: String? = null,
  /** The catalog's canonical path, `/<id>/`. */
  public val path: String,
  /** Whether the descriptive fields above are a last-known snapshot of an idle catalog. */
  @EncodeDefault public val metaStale: Boolean = false,
  /** [LOADED], [STALE], [FAILED] or [PENDING]. */
  @EncodeDefault public val loadState: String = LOADED,
  /** The latest load error; null after a successful latest attempt. */
  @EncodeDefault public val loadError: String? = null,
  @EncodeDefault public val lastLoadAttemptEpochMillis: Long? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(id: String) {
    public var id: String = id
    public var listed: Boolean = true
    public var title: String? = null
    public var trust: String? = null
    public var previews: Int? = null
    public var failedRenders: Int = 0
    public var deferredPreviews: Int = 0
    public var live: Boolean = false
    public var running: Boolean = false
    public var degradation: String? = null
    public var repo: String? = null
    public var branch: String? = null
    public var generatedAt: String? = null
    public var composeAiToolsVersion: String? = null
    public var designParityVersion: String? = null
    public var path: String = "/$id/"
    public var metaStale: Boolean = false
    public var loadState: String = LOADED
    public var loadError: String? = null
    public var lastLoadAttemptEpochMillis: Long? = null

    public fun build(): CatalogReadCatalogV1 =
      CatalogReadCatalogV1(
        id,
        listed,
        title,
        trust,
        previews,
        failedRenders,
        deferredPreviews,
        live,
        running,
        degradation,
        repo,
        branch,
        generatedAt,
        composeAiToolsVersion,
        designParityVersion,
        path,
        metaStale,
        loadState,
        loadError,
        lastLoadAttemptEpochMillis,
      )
  }

  /** This value as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id).also {
      it.listed = listed
      it.title = title
      it.trust = trust
      it.previews = previews
      it.failedRenders = failedRenders
      it.deferredPreviews = deferredPreviews
      it.live = live
      it.running = running
      it.degradation = degradation
      it.repo = repo
      it.branch = branch
      it.generatedAt = generatedAt
      it.composeAiToolsVersion = composeAiToolsVersion
      it.designParityVersion = designParityVersion
      it.path = path
      it.metaStale = metaStale
      it.loadState = loadState
      it.loadError = loadError
      it.lastLoadAttemptEpochMillis = lastLoadAttemptEpochMillis
    }

  public companion object {
    /** A usable copy is registered and its latest load succeeded. */
    public const val LOADED: String = "loaded"

    /** A usable copy is registered, but the latest refresh failed ([loadError]). */
    public const val STALE: String = "stale"

    /** The latest load failed before any usable copy registered. The catalog is not served. */
    public const val FAILED: String = "failed"

    /** Configured, not loaded yet: normal for a minute after a restart. Retry before concluding. */
    public const val PENDING: String = "pending"
  }
}

/**
 * The body of `GET` [CatalogReadRoutesV1.PREVIEWS_PATH]: one catalog's previews, in the catalog's
 * order, unpaged.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadPreviewsV1
internal constructor(
  /** [CatalogReadRoutesV1.SERVE_SCHEMA]. */
  @EncodeDefault public val schema: String = CatalogReadRoutesV1.SERVE_SCHEMA,
  /** The catalog's label as its render host names it. */
  public val module: String,
  /**
   * The compose-ai-tools version that produced the catalog's published renders. Null means the
   * provenance is unknown, and a client must not substitute anything it compiled itself.
   */
  @EncodeDefault public val catalogVersion: String? = null,
  /** As [CatalogReadCatalogV1.trust]; null for a live, daemon-backed module. */
  @EncodeDefault public val trust: String? = null,
  /** Why the catalog serves published bytes only, if it does; empty when fully live. */
  @EncodeDefault public val degradations: List<CatalogReadDegradationV1> = emptyList(),
  /** Landing-page visits since the server started. Engagement, not identity. */
  @EncodeDefault public val views: Long = 0,
  @EncodeDefault public val previews: List<CatalogReadPreviewV1> = emptyList(),
  /**
   * Level 2. The delivery-branch commit the catalog was loaded from, the revision [previews]
   * describe; null for a catalog with none (an uploaded bundle, a local project, a daemon-backed
   * module) and absent from a level-1 server. Names the revision; the response's
   * [CatalogReadRoutesV1.ETAG_HEADER] is what to cache against.
   */
  @EncodeDefault public val revision: String? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(module: String) {
    public var schema: String = CatalogReadRoutesV1.SERVE_SCHEMA
    public var module: String = module
    public var catalogVersion: String? = null
    public var trust: String? = null
    public var degradations: List<CatalogReadDegradationV1> = emptyList()
    public var views: Long = 0
    public var previews: List<CatalogReadPreviewV1> = emptyList()
    public var revision: String? = null

    public fun build(): CatalogReadPreviewsV1 =
      CatalogReadPreviewsV1(
        schema,
        module,
        catalogVersion,
        trust,
        degradations,
        views,
        previews,
        revision,
      )
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(module).also {
      it.schema = schema
      it.catalogVersion = catalogVersion
      it.trust = trust
      it.degradations = degradations
      it.views = views
      it.previews = previews
      it.revision = revision
    }
}

/** One reason on [CatalogReadPreviewsV1.degradations]: a stable [code] and a sentence. */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadDegradationV1
internal constructor(public val code: String, public val detail: String) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(code: String, detail: String) {
    public var code: String = code
    public var detail: String = detail

    public fun build(): CatalogReadDegradationV1 = CatalogReadDegradationV1(code, detail)
  }

  /** This value as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(code, detail)
}

/** One preview on [CatalogReadPreviewsV1.previews]. */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadPreviewV1
internal constructor(
  /** The preview's id: the `{previewId}` segment of every per-preview route. */
  public val id: String,
  public val label: String,
  /** How its pixels are produced: [MODE_SNAPSHOT] (published), [MODE_LIVE] (rendered on demand). */
  @EncodeDefault public val modes: List<String> = emptyList(),
  /**
   * The editable knobs the preview's author declared (`compose/overrides`). Each entry is a
   * `PreviewOverrideDeclaration` from `data-preview-overrides-core`; [CatalogReadKnobV1] is the
   * part of it a catalog reader needs.
   */
  @EncodeDefault public val overrides: List<CatalogReadKnobV1> = emptyList(),
  /**
   * Remote Compose named-value knobs (`compose/remotecompose`), as `RemoteComposeKnobDeclaration`s
   * from compose-preview-daemon's `data-remotecompose-core`. Opaque here.
   */
  @EncodeDefault public val remoteComposeKnobs: List<JsonObject> = emptyList(),
  /** Whether a spatial scene is published for it. */
  @EncodeDefault public val spatial: Boolean = false,
  /** Whether it is published without a render, so every render of it is live. */
  @EncodeDefault public val liveOnly: Boolean = false,
  /** Viewer page opens since the server started. Engagement, not identity. */
  @EncodeDefault public val views: Long = 0,
  /** Whether a Remote Compose document is published for it. */
  @EncodeDefault public val remoteCompose: Boolean = false,
  /**
   * For a watch face, its complication slots (`slots`), user configurations (`userConfigurations`),
   * size, clip shape and initial state, as compose-ai-tools' `WatchFaceComplicationsPayload` writes
   * them. Null for any other preview. Opaque here: the shape belongs to the bundle format.
   */
  @EncodeDefault public val watchFace: JsonObject? = null,
  /** For a watch face, the renderer backends this host offers; empty for any other preview. */
  @EncodeDefault public val watchFaceRenderers: List<CatalogReadWatchFaceRendererV1> = emptyList(),
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(id: String, label: String) {
    public var id: String = id
    public var label: String = label
    public var modes: List<String> = emptyList()
    public var overrides: List<CatalogReadKnobV1> = emptyList()
    public var remoteComposeKnobs: List<JsonObject> = emptyList()
    public var spatial: Boolean = false
    public var liveOnly: Boolean = false
    public var views: Long = 0
    public var remoteCompose: Boolean = false
    public var watchFace: JsonObject? = null
    public var watchFaceRenderers: List<CatalogReadWatchFaceRendererV1> = emptyList()

    public fun build(): CatalogReadPreviewV1 =
      CatalogReadPreviewV1(
        id,
        label,
        modes,
        overrides,
        remoteComposeKnobs,
        spatial,
        liveOnly,
        views,
        remoteCompose,
        watchFace,
        watchFaceRenderers,
      )
  }

  /** This value as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id, label).also {
      it.modes = modes
      it.overrides = overrides
      it.remoteComposeKnobs = remoteComposeKnobs
      it.spatial = spatial
      it.liveOnly = liveOnly
      it.views = views
      it.remoteCompose = remoteCompose
      it.watchFace = watchFace
      it.watchFaceRenderers = watchFaceRenderers
    }

  public companion object {
    public const val MODE_SNAPSHOT: String = "snapshot"
    public const val MODE_LIVE: String = "live"
  }
}

/**
 * The part of a declared knob (`PreviewOverrideDeclaration`) a catalog reader needs: which key to
 * send and what kind of value it takes. The declaration also carries its default, current value,
 * suggestions and value set, which this type leaves to `data-preview-overrides-core`.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadKnobV1
internal constructor(
  /** The author's key. For a repeated knob the wire key is [seedKey]. */
  public val key: String,
  /** `string`, `int`, `float`, `bool`, `color`, … */
  public val type: String,
  /** The control's label; absent reads as [key]. */
  public val label: String? = null,
  /** Which instance of a repeated knob this is; null for an ordinary one. */
  public val index: Int? = null,
) {
  /** The key a render request names this knob by: [key], or `key[index]` for a repeated one. */
  public val seedKey: String
    get() = if (index == null) key else "$key[$index]"

  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(key: String, type: String) {
    public var key: String = key
    public var type: String = type
    public var label: String? = null
    public var index: Int? = null

    public fun build(): CatalogReadKnobV1 = CatalogReadKnobV1(key, type, label, index)
  }

  /** This value as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(key, type).also {
      it.label = label
      it.index = index
    }
}

/** One renderer backend on [CatalogReadPreviewV1.watchFaceRenderers]. */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadWatchFaceRendererV1
internal constructor(
  /** `dwf-rc`, `dwf-apk`, …: the `wfRenderer` a render request names. */
  public val id: String,
  public val label: String,
  /** A short label for a badge; absent from a host that predates it. */
  public val badgeLabel: String? = null,
  /** Whether this host can render with it now. */
  public val available: Boolean,
  /** Why [available] is false. */
  @EncodeDefault public val unavailableReason: String? = null,
  /** Whether it honours live overrides, rather than replaying published bytes. */
  @EncodeDefault public val supportsLiveOverrides: Boolean = false,
  /** Whether a render that names no `wfRenderer` uses it. */
  @EncodeDefault public val isDefault: Boolean = false,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(id: String, label: String) {
    public var id: String = id
    public var label: String = label
    public var badgeLabel: String? = null
    public var available: Boolean = false
    public var unavailableReason: String? = null
    public var supportsLiveOverrides: Boolean = false
    public var isDefault: Boolean = false

    public fun build(): CatalogReadWatchFaceRendererV1 =
      CatalogReadWatchFaceRendererV1(
        id,
        label,
        badgeLabel,
        available,
        unavailableReason,
        supportsLiveOverrides,
        isDefault,
      )
  }

  /** This value as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id, label).also {
      it.badgeLabel = badgeLabel
      it.available = available
      it.unavailableReason = unavailableReason
      it.supportsLiveOverrides = supportsLiveOverrides
      it.isDefault = isDefault
    }
}
