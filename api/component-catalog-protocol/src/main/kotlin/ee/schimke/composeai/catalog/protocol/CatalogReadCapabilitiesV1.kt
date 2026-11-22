@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.catalog.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * The body of `GET` [CatalogReadRoutesV1.CAPABILITIES_PATH] (`catalog-read` level 2): what each
 * preview of one catalog can answer, in the catalog's order, unpaged.
 *
 * The server builds it from the same host calls its in-process MCP makes for `list_previews` and
 * `list_data_products` (compose-preview-server's `ServeCatalogRead.capabilities`), so a reader out
 * of process gets the same answer. Carries an `ETag`; see [CatalogReadRoutesV1.ETAG_HEADER].
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadCapabilitiesV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  /**
   * The delivery-branch commit these answers describe (the catalog's `provenance.commit`), or null
   * for a catalog with none: an uploaded bundle, a local project, a daemon-backed module.
   */
  @EncodeDefault public val revision: String? = null,
  public val previews: List<CatalogReadPreviewCapabilitiesV1>,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder {
    public var schema: String = SCHEMA
    public var revision: String? = null
    public var previews: List<CatalogReadPreviewCapabilitiesV1> = emptyList()

    public fun build(): CatalogReadCapabilitiesV1 =
      CatalogReadCapabilitiesV1(schema, revision, previews)
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.schema = schema
      it.revision = revision
      it.previews = previews
    }

  public companion object {
    public const val SCHEMA: String = "compose-preview-serve/capabilities/v1"
  }
}

/** One preview on [CatalogReadCapabilitiesV1.previews]. */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadPreviewCapabilitiesV1
internal constructor(
  /** The preview's id, as on [CatalogReadPreviewV1.id]. */
  public val id: String,
  /** The data-product kinds the preview declares, sorted: `list_previews`' `dataProductKinds`. */
  public val dataProductKinds: List<String>,
  /**
   * Every kind `list_data_products` reports, sorted: [dataProductKinds] plus
   * [KIND_WATCH_FACE_COMPLICATIONS], [KIND_A11Y_HIERARCHY], [KIND_GUIDELINES_RESULT] and
   * [KIND_COMPOSE_ANNOTATIONS] where they apply.
   */
  public val productKinds: List<String>,
  /** Whether the preview has an SVG export, so a render request for SVG can answer. */
  public val svgAvailable: Boolean,
  /** Whether the preview has a full-page scroll export (PNG or SVG). */
  public val scrollAvailable: Boolean,
  /** The preview's declared interaction state, if any (`pressed`, …). */
  @EncodeDefault public val state: String? = null,
  /** The preview's declared theme, if any (`dark`, …). */
  @EncodeDefault public val theme: String? = null,
  /** Whether a published accessibility overlay exists ([KIND_A11Y_HIERARCHY]). */
  public val a11yOverlay: Boolean,
  /** Whether published design annotations exist (one source of [KIND_COMPOSE_ANNOTATIONS]). */
  public val designAnnotations: Boolean,
  /** Whether published typography exists (the other source of [KIND_COMPOSE_ANNOTATIONS]). */
  public val publishedTypography: Boolean,
  /**
   * Whether [CatalogReadRoutesV1.GUIDELINES_PATH] has a record for this preview
   * ([CatalogReadGuidelinesV1.found]).
   */
  public val guidelinesResult: Boolean,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(id: String) {
    public var id: String = id
    public var dataProductKinds: List<String> = emptyList()
    public var productKinds: List<String> = emptyList()
    public var svgAvailable: Boolean = false
    public var scrollAvailable: Boolean = false
    public var state: String? = null
    public var theme: String? = null
    public var a11yOverlay: Boolean = false
    public var designAnnotations: Boolean = false
    public var publishedTypography: Boolean = false
    public var guidelinesResult: Boolean = false

    public fun build(): CatalogReadPreviewCapabilitiesV1 =
      CatalogReadPreviewCapabilitiesV1(
        id,
        dataProductKinds,
        productKinds,
        svgAvailable,
        scrollAvailable,
        state,
        theme,
        a11yOverlay,
        designAnnotations,
        publishedTypography,
        guidelinesResult,
      )
  }

  /** This value as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id).also {
      it.dataProductKinds = dataProductKinds
      it.productKinds = productKinds
      it.svgAvailable = svgAvailable
      it.scrollAvailable = scrollAvailable
      it.state = state
      it.theme = theme
      it.a11yOverlay = a11yOverlay
      it.designAnnotations = designAnnotations
      it.publishedTypography = publishedTypography
      it.guidelinesResult = guidelinesResult
    }

  public companion object {
    /** Derived for a watch face (a preview whose [CatalogReadPreviewV1.watchFace] is set). */
    public const val KIND_WATCH_FACE_COMPLICATIONS: String = "watchface/complications"

    /** Derived from [a11yOverlay]. */
    public const val KIND_A11Y_HIERARCHY: String = "a11y/hierarchy"

    /** Derived from [guidelinesResult]; the same string as [CatalogReadGuidelinesV1.KIND]. */
    public const val KIND_GUIDELINES_RESULT: String = "guidelines/result"

    /** Derived from [designAnnotations] or [publishedTypography]. */
    public const val KIND_COMPOSE_ANNOTATIONS: String = "compose/annotations"
  }
}
