package ee.schimke.composeai.deployment.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The three ungated endpoints every deployable container serves, so one deploy tool can roll out
 * any of them. See `docs/design/DEPLOYMENT_PROTOCOL.md` for how a deploy tool reads them together.
 *
 * - [HEALTHZ_PATH]: liveness. `200` with [HEALTHZ_BODY] once the listener is up. Never the rollout
 *   gate.
 * - [READYZ_PATH]: readiness. `200` with [READY_BODY] once the component can serve its real
 *   traffic, `503` with [WARMING_BODY] until then. It latches: once ready, a replica stays ready
 *   for its lifetime. A component may start warming on the first poll, so a deploy tool polls it.
 * - [VERSION_PATH]: identity. `200` with a [DeployableVersionV1] body.
 *
 * All three are unauthenticated and carry no secrets.
 */
public object DeployableEndpointsV1 {
  public const val HEALTHZ_PATH: String = "/healthz"
  public const val READYZ_PATH: String = "/readyz"
  public const val VERSION_PATH: String = "/version"

  /** The body of a `200` [HEALTHZ_PATH]. Informational: the status code is the signal. */
  public const val HEALTHZ_BODY: String = "ok"

  /** The body of a `200` [READYZ_PATH]. Informational: the status code is the signal. */
  public const val READY_BODY: String = "ready"

  /** The body of a `503` [READYZ_PATH]. Informational: the status code is the signal. */
  public const val WARMING_BODY: String = "warming"
}

/**
 * Well-known [DeployableVersionV1.component] and [ReleaseManifestV1.component] names.
 *
 * Open vocabulary: a reader treats an unknown name as a component it does not know, never as an
 * error.
 */
public object DeploymentComponentsV1 {
  /** compose-preview-server: catalogs, previews, the host side of the UI builder. */
  public const val PREVIEW_SERVER: String = "preview-server"

  /** compose-ui-builder's own container: the editor and its routes. */
  public const val UI_BUILDER: String = "ui-builder"

  /** compose-preview-mcp's hosted catalog MCP. */
  public const val CATALOG_MCP: String = "catalog-mcp"
}

/**
 * Well-known API names for [DeployableVersionV1.apis], [ReleaseManifestV1.provides] and
 * [ReleaseManifestV1.requires]. Each names one surface two components must agree on, and its level
 * is an integer bumped when that surface changes incompatibly.
 *
 * Open vocabulary, like [DeploymentComponentsV1].
 */
public object DeploymentApisV1 {
  /**
   * The editor↔host HTTP route set (`/api/ui-builder/…`). The editor archive's
   * `ui-builder-web.json` already carries it as `serverApi`.
   */
  public const val UI_BUILDER_EDITOR: String = "ui-builder-editor"

  /**
   * Root-aware offline handlers that can share the host's notification registration. The editor
   * archive's `ui-builder-web.json` already carries it as `rootOffline` (`0` when absent).
   */
  public const val UI_BUILDER_ROOT_OFFLINE: String = "ui-builder-root-offline"

  /** The preview server's catalog read API; see DEPLOYMENT_PROTOCOL.md (not yet typed). */
  public const val CATALOG_READ: String = "catalog-read"

  /** The render / export request; see DEPLOYMENT_PROTOCOL.md (not yet typed). */
  public const val RENDER_EXPORT: String = "render-export"

  /** [SessionWhoamiV1] at [SessionWhoamiV1.PATH]. */
  public const val SESSION_WHOAMI: String = "session-whoami"
}

/**
 * The body of `GET /version` ([DeployableEndpointsV1.VERSION_PATH]).
 *
 * Decode with `ignoreUnknownKeys = true`: a component adds its own fields beside these. The preview
 * server's reply today, `{"schema":"compose-preview-serve/version/v1","version":…,"serveSchema":…,
 * "public":…}`, decodes as one with [schema] [PREVIEW_SERVER_SCHEMA], no [component] and no [apis].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class DeployableVersionV1
internal constructor(
  /**
   * Which body this is: [SCHEMA], or [PREVIEW_SERVER_SCHEMA] from a preview server that predates
   * this contract. Both name this shape.
   */
  public val schema: String,
  /** The component's released version, exactly as its release tag names it, without a `v`. */
  public val version: String,
  /**
   * Which component answered ([DeploymentComponentsV1]); null from a server that predates this
   * contract, which is always the preview server.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val component: String? = null,
  /**
   * The API levels this running build speaks, by [DeploymentApisV1] name. Empty from a component
   * that does not report them. A level a component speaks more than one of is reported at its
   * highest; the release manifest's [ReleaseManifestV1.provides] is the same map at build time.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val apis: Map<String, Int> = emptyMap(),
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` for why the constructor is
   * internal.
   */
  public class Builder(version: String) {
    public var schema: String = SCHEMA
    public var version: String = version
    public var component: String? = null
    public var apis: Map<String, Int> = emptyMap()

    public fun build(): DeployableVersionV1 = DeployableVersionV1(schema, version, component, apis)
  }

  /** This body as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(version).also {
      it.schema = schema
      it.component = component
      it.apis = apis
    }

  public companion object {
    /** The schema id a component written against this contract reports. */
    public const val SCHEMA: String = "compose-preview/deployable-version/v1"

    /** The schema id the preview server's `/version` has always reported; the same shape. */
    public const val PREVIEW_SERVER_SCHEMA: String = "compose-preview-serve/version/v1"

    /** Every [schema] value that names this shape. */
    public val SCHEMAS: Set<String> = setOf(SCHEMA, PREVIEW_SERVER_SCHEMA)
  }
}
