@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * The UI builder's HTTP routes that a client outside the editor calls, by method and path.
 *
 * Derived from compose-preview-server 3.192.0, which serves them today; `ui-builder-server` (the UI
 * builder's own host) is to serve the same routes, at the same paths, with the same bodies. The
 * route table, the credential each route reads and the three error shapes are in
 * `docs/design/UI_BUILDER_HTTP_ROUTES.md`. Path templates name their parameters in braces; a client
 * substitutes the percent-encoded path segment.
 *
 * Shape only: no client, no server, no URL building.
 */
public object UiBuilderHttpRoutesV1 {
  /**
   * The API name these routes are versioned under: `DeploymentApisV1.UI_BUILDER_EDITOR` in
   * `deployment-protocol`, the editor archive's `serverApi`, and the preview server's
   * `SUPPORTED_SERVER_API`.
   */
  public const val API_NAME: String = "ui-builder-editor"

  /** The level of [API_NAME] these routes describe. */
  public const val API_LEVEL: Int = 2

  /** The path parameter every per-design route names. */
  public const val DESIGN_ID_PARAMETER: String = "designId"

  /**
   * `POST`: one [HttpRequestEnvelopeV1], answered with one [HttpResponseEnvelopeV1]. The required
   * capability depends on the request variant: read for listing, opening and access lookups, export
   * for `exportDesign`, write for everything that changes state.
   */
  public const val REQUESTS_PATH: String = "/api/ui-builder/v1/requests"

  /**
   * `GET` (read): the design's [DesignDocumentV1], at its latest committed revision or at
   * [REVISION_QUERY]. `PUT` (write): create the design from a [DesignDocumentV1] body whose `id` is
   * this path's design id; requires `If-None-Match: *` and never replaces.
   */
  public const val DESIGN_PATH: String = "/api/ui-builder/v1/designs/{designId}"

  /**
   * `GET` (export): the design compiled to a Remote Compose document, as bytes. Present only on a
   * host built with the Remote Compose exporter; a host without it answers `404`.
   */
  public const val DESIGN_EXPORT_RC_PATH: String = "/api/ui-builder/v1/designs/{designId}/export.rc"

  /**
   * `POST` (export): a supplied [DesignDocumentV1] compiled to a Remote Compose document, without
   * storing it or allocating a revision. Present only where [DESIGN_EXPORT_RC_PATH] is.
   */
  public const val DOCUMENT_EXPORT_RC_PATH: String = "/api/ui-builder/v1/documents/export.rc"

  /** `GET` (read): who the host decided the caller is, as a [UiBuilderIdentityV1]. */
  public const val IDENTITY_PATH: String = "/api/ui-builder/v1/identity"

  /**
   * `GET`: every design on the host, as a [UiBuilderAdminDesignsV1]. Gated by
   * [UiBuilderHttpCredentialsV1.ADMIN_TOKEN_HEADER] (the admin token or the read-only admin token),
   * not by the UI-builder capabilities.
   */
  public const val ADMIN_DESIGNS_PATH: String = "/admin/ui-builder/designs"

  /**
   * The browser link to one design's editor. The page shell is public; the editor's data calls
   * carry their own credential. A token-gated host also accepts
   * [UiBuilderHttpCredentialsV1.TOKEN_QUERY] here and exchanges it for a cookie.
   */
  public const val EDITOR_DESIGN_PATH: String = "/ui-builder/{designId}"

  /**
   * The largest request body [REQUESTS_PATH], [DESIGN_PATH] and [DOCUMENT_EXPORT_RC_PATH] read; a
   * larger one is refused with `413`.
   */
  public const val MAX_REQUEST_BYTES: Int = 8 * 1024 * 1024

  /** `?revision=N`: read or export a retained revision. A non-negative integer, else `400`. */
  public const val REVISION_QUERY: String = "revision"

  /**
   * `?download=1` on an export: `Content-Disposition: attachment` rather than `inline`. Any value
   * other than `0` or `false` counts.
   */
  public const val DOWNLOAD_QUERY: String = "download"

  /**
   * `?artifact=true` on [DOCUMENT_EXPORT_RC_PATH]: answer with the [ExportArtifactV1] as JSON,
   * diagnostics included, instead of its decoded bytes.
   */
  public const val ARTIFACT_QUERY: String = "artifact"

  /** The revision an export served, on its `200`, when the artifact records one. */
  public const val REVISION_HEADER: String = "X-UI-Builder-Revision"

  /** The precondition [DESIGN_PATH]'s `PUT` requires: `If-None-Match: *`. */
  public const val CREATE_PRECONDITION: String = "*"
}

/**
 * How a non-browser client presents a credential to the UI builder's routes, and the actor ids the
 * host resolves them to. Every name is the one the preview server reads.
 */
public object UiBuilderHttpCredentialsV1 {
  /**
   * The host's operator token, as a header. Also accepted as [TOKEN_QUERY]. Never as
   * `Authorization: Bearer`: a bearer is looked up only as an agent grant, so an operator token
   * sent that way is a `401`.
   */
  public const val TOKEN_HEADER: String = "X-Compose-Preview-Token"

  /** The operator token (or an agent grant token) as a query parameter. */
  public const val TOKEN_QUERY: String = "token"

  /** The scheme an agent grant token is presented under in `Authorization`. */
  public const val BEARER_SCHEME: String = "Bearer"

  /**
   * The admin token or the read-only admin token, for [UiBuilderHttpRoutesV1.ADMIN_DESIGNS_PATH].
   * Header only: the admin JSON routes never read it from a query.
   */
  public const val ADMIN_TOKEN_HEADER: String = "X-Compose-Preview-Admin-Token"

  /** The actor the operator token speaks as: what [HttpRequestEnvelopeV1.actorId] must say. */
  public const val OPERATOR_ACTOR_ID: String = "operator"

  /** The prefix of a GitHub-signed-in actor's id: `github:<login>`. */
  public const val GITHUB_ACTOR_PREFIX: String = "github:"

  /** The actor a signed-out visitor reads as on a host serving public designs. */
  public const val ANONYMOUS_ACTOR_ID: String = "anonymous:visitor"
}

/**
 * The `200` body of `GET` [UiBuilderHttpRoutesV1.IDENTITY_PATH]: who the host decided the caller
 * is. A client cannot derive its own actor id; it comes from the operator token, the session or the
 * grant the request carried.
 *
 * Every field after [actorId] is optional and absent from older hosts. [canWrite] is a hint for
 * what to offer, never a gate: the write routes decide.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderIdentityV1
internal constructor(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  /** Whether the host offers public/private design visibility. Always true from current hosts. */
  @EncodeDefault public val designVisibilitySupported: Boolean = true,
  /**
   * The authenticated actor: [UiBuilderHttpCredentialsV1.OPERATOR_ACTOR_ID], `github:<login>`, …
   */
  public val actorId: String,
  /**
   * Whether a person or the operator token is behind the request, rather than the anonymous reader
   * of a public host. Absent from a host that cannot say.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val signedIn: Boolean? = null,
  /** Whether this caller may create and edit designs. Absent (older host) reads as true. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val canWrite: Boolean? = null,
  /** Why [canWrite] is false, in words the person can act on. Absent when it is true. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val writeDeniedReason: String? = null,
  /**
   * Where to sign in and come back, on a host with GitHub sign-in and no signed-in session. A
   * same-origin path, e.g. `/auth/github/start?return=%2Fui-builder`.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val signInUrl: String? = null,
) {
  /**
   * Additive construction API; see [CatalogCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(actorId: String) {
    public var schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1
    public var designVisibilitySupported: Boolean = true
    public var actorId: String = actorId
    public var signedIn: Boolean? = null
    public var canWrite: Boolean? = null
    public var writeDeniedReason: String? = null
    public var signInUrl: String? = null

    public fun build(): UiBuilderIdentityV1 =
      UiBuilderIdentityV1(
        schemaVersion,
        designVisibilitySupported,
        actorId,
        signedIn,
        canWrite,
        writeDeniedReason,
        signInUrl,
      )
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(actorId).also {
      it.schemaVersion = schemaVersion
      it.designVisibilitySupported = designVisibilitySupported
      it.signedIn = signedIn
      it.canWrite = canWrite
      it.writeDeniedReason = writeDeniedReason
      it.signInUrl = signInUrl
    }
}

/**
 * The `401` body of `GET` [UiBuilderHttpRoutesV1.IDENTITY_PATH]: no credential the host accepts,
 * and where to sign in. The only `401` on these routes whose body is JSON.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderIdentityRefusalV1
internal constructor(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val message: String,
  /** As [UiBuilderIdentityV1.signInUrl]; absent on a host without GitHub sign-in. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val signInUrl: String? = null,
) {
  /**
   * Additive construction API; see [CatalogCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(message: String) {
    public var schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1
    public var message: String = message
    public var signInUrl: String? = null

    public fun build(): UiBuilderIdentityRefusalV1 =
      UiBuilderIdentityRefusalV1(schemaVersion, message, signInUrl)
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(message).also {
      it.schemaVersion = schemaVersion
      it.signInUrl = signInUrl
    }
}

/**
 * The `200` body of `GET` [UiBuilderHttpRoutesV1.ADMIN_DESIGNS_PATH]: every design on the host,
 * whoever owns it, oldest first, then any design whose stored files will not read, then the trash.
 *
 * Unlike `listDesigns` this is not actor-specific and carries no access detail beyond the owner and
 * a collaborator count.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderAdminDesignsV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  public val designs: List<UiBuilderAdminDesignV1> = emptyList(),
  /** Designs in the trash, whoever deleted them. */
  public val trash: List<UiBuilderTrashedDesignV1> = emptyList(),
) {
  /**
   * Additive construction API; see [CatalogCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder {
    public var schema: String = SCHEMA
    public var designs: List<UiBuilderAdminDesignV1> = emptyList()
    public var trash: List<UiBuilderTrashedDesignV1> = emptyList()

    public fun build(): UiBuilderAdminDesignsV1 = UiBuilderAdminDesignsV1(schema, designs, trash)
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.schema = schema
      it.designs = designs
      it.trash = trash
    }

  public companion object {
    /** The schema id the preview server has always sent; kept so its bytes stay this shape. */
    public const val SCHEMA: String = "compose-preview-serve/admin-ui-builder-designs/v1"
  }
}

/**
 * One design on [UiBuilderAdminDesignsV1.designs].
 *
 * A design whose stored files will not read still gets a row, so an operator can see it: its
 * [title] is its id, its counts and timestamps are zero, its [catalogSystemId] and [ownerActorId]
 * are empty, and [unusableReason] says why.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderAdminDesignV1
internal constructor(
  public val designId: String,
  public val title: String,
  public val revision: Long,
  public val catalogSystemId: String,
  public val ownerActorId: String,
  /** How many actors besides the owner the design is shared with. They are not named here. */
  public val collaborators: Int,
  public val createdAtEpochMillis: Long,
  public val updatedAtEpochMillis: Long,
  /** Live update subscriptions open on the design right now. */
  public val activeSubscribers: Int,
  /** Why the host cannot serve this design; null when it can. */
  public val unusableReason: String? = null,
  /** Why this still-editable design has lost catalog vocabulary; null when it has not. */
  public val degradedReason: String? = null,
  /** Whether the stored document can still be produced for download and repair. */
  public val documentAvailable: Boolean = true,
) {
  /**
   * Additive construction API; see [CatalogCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(designId: String, title: String) {
    public var designId: String = designId
    public var title: String = title
    public var revision: Long = 0
    public var catalogSystemId: String = ""
    public var ownerActorId: String = ""
    public var collaborators: Int = 0
    public var createdAtEpochMillis: Long = 0
    public var updatedAtEpochMillis: Long = 0
    public var activeSubscribers: Int = 0
    public var unusableReason: String? = null
    public var degradedReason: String? = null
    public var documentAvailable: Boolean = true

    public fun build(): UiBuilderAdminDesignV1 =
      UiBuilderAdminDesignV1(
        designId,
        title,
        revision,
        catalogSystemId,
        ownerActorId,
        collaborators,
        createdAtEpochMillis,
        updatedAtEpochMillis,
        activeSubscribers,
        unusableReason,
        degradedReason,
        documentAvailable,
      )
  }

  /** This row as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(designId, title).also {
      it.revision = revision
      it.catalogSystemId = catalogSystemId
      it.ownerActorId = ownerActorId
      it.collaborators = collaborators
      it.createdAtEpochMillis = createdAtEpochMillis
      it.updatedAtEpochMillis = updatedAtEpochMillis
      it.activeSubscribers = activeSubscribers
      it.unusableReason = unusableReason
      it.degradedReason = degradedReason
      it.documentAvailable = documentAvailable
    }
}

/** One design on [UiBuilderAdminDesignsV1.trash]: deleted, and restorable until a deadline. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderTrashedDesignV1
internal constructor(
  public val designId: String,
  public val title: String,
  public val ownerActorId: String,
  public val trashedAtEpochMillis: Long,
  public val trashedByActorId: String,
  public val restorableUntilEpochMillis: Long,
) {
  /**
   * Additive construction API; see [CatalogCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(designId: String, title: String, ownerActorId: String) {
    public var designId: String = designId
    public var title: String = title
    public var ownerActorId: String = ownerActorId
    public var trashedAtEpochMillis: Long = 0
    public var trashedByActorId: String = ""
    public var restorableUntilEpochMillis: Long = 0

    public fun build(): UiBuilderTrashedDesignV1 =
      UiBuilderTrashedDesignV1(
        designId,
        title,
        ownerActorId,
        trashedAtEpochMillis,
        trashedByActorId,
        restorableUntilEpochMillis,
      )
  }

  /** This entry as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(designId, title, ownerActorId).also {
      it.trashedAtEpochMillis = trashedAtEpochMillis
      it.trashedByActorId = trashedByActorId
      it.restorableUntilEpochMillis = restorableUntilEpochMillis
    }
}
