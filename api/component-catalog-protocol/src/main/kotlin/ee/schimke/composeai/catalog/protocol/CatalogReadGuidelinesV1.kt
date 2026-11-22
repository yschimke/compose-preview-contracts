@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.catalog.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The body of `GET` [CatalogReadRoutesV1.GUIDELINES_PATH] (`catalog-read` level 2): one preview's
 * published design-guidelines result, the answer to the MCP's `get_preview_data
 * kind=guidelines/result`.
 *
 * [record] is a `GuidelineRecordV1` from `design-guidelines-protocol`, as its own serializer writes
 * it. It is opaque here so that this module keeps no dependency on that one: a reader that wants
 * the typed record decodes it with `GuidelineRecordV1.serializer()` (`ignoreUnknownKeys = true`, as
 * for every body here). An unknown preview is `404`, text `no such preview`.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadGuidelinesV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  /**
   * The delivery commit the catalog was loaded from, as [CatalogReadPreviewsV1.revision]. Not the
   * record's own `revision`, an integer revision of the subject it judged.
   */
  @EncodeDefault public val revision: String? = null,
  /** Always [KIND]. */
  @EncodeDefault public val kind: String = KIND,
  public val previewId: String,
  /** False when the catalog publishes no result for this preview; [record] is then null. */
  public val found: Boolean,
  /** The `GuidelineRecordV1`, opaque; null exactly when [found] is false. */
  @EncodeDefault public val record: JsonObject? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(previewId: String) {
    public var schema: String = SCHEMA
    public var revision: String? = null
    public var kind: String = KIND
    public var previewId: String = previewId
    public var found: Boolean = false
    public var record: JsonObject? = null

    public fun build(): CatalogReadGuidelinesV1 =
      CatalogReadGuidelinesV1(schema, revision, kind, previewId, found, record)
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(previewId).also {
      it.schema = schema
      it.revision = revision
      it.kind = kind
      it.found = found
      it.record = record
    }

  public companion object {
    public const val SCHEMA: String = "compose-preview-serve/guidelines/v1"

    /** The data-product kind this body answers for. */
    public const val KIND: String = CatalogReadPreviewCapabilitiesV1.KIND_GUIDELINES_RESULT

    /** `GuidelineRecordV1.SCHEMA`: the `schema` a [record] carries. */
    public const val RECORD_SCHEMA: String = "compose-ui-builder/guidelines-result/v1"
  }
}
