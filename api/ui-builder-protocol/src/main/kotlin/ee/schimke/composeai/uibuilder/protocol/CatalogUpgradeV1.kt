package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Deterministic, non-committing result of validating a catalog-pin transition at one document
 * revision. The lists are ordered by JSON pointer and then stable diagnostic code.
 *
 * [previewDigest] identifies this exact preview, including both pins, both document hashes,
 * [changes], and [issues]. Implementations define canonical JSON encoding, but must return the same
 * digest for the same inputs and migration implementation.
 */
@Serializable
public data class CatalogUpgradePreviewV1(
  public val designId: String,
  public val baseRevision: Long,
  public val sourceCatalogPin: CatalogReferenceV1,
  public val targetCatalogPin: CatalogReferenceV1,
  public val sourceDocumentHash: String,
  public val status: CatalogUpgradePreviewStatusV1,
  public val previewDigest: String,
  public val candidateDocument: DesignDocumentV1? = null,
  public val candidateDocumentHash: String? = null,
  public val changes: List<CatalogUpgradeChangeV1> = emptyList(),
  public val issues: List<CatalogUpgradeIssueV1> = emptyList(),
)

@Serializable
public enum class CatalogUpgradePreviewStatusV1 {
  /** A candidate document was produced and may be submitted using [CatalogUpgradeMutationV1]. */
  @SerialName("ready") READY,
  /** Validation prevented a candidate document from being produced. */
  @SerialName("blocked") BLOCKED,
}

/** Exact structural difference between source and candidate documents, using RFC 6901 pointers. */
@Serializable
public sealed interface CatalogUpgradeChangeV1 {
  public val path: String
}

@Serializable
@SerialName("add")
public data class AddCatalogUpgradeChangeV1(
  override val path: String,
  public val targetValue: JsonElement,
) : CatalogUpgradeChangeV1

@Serializable
@SerialName("remove")
public data class RemoveCatalogUpgradeChangeV1(
  override val path: String,
  public val sourceValue: JsonElement,
) : CatalogUpgradeChangeV1

@Serializable
@SerialName("replace")
public data class ReplaceCatalogUpgradeChangeV1(
  override val path: String,
  public val sourceValue: JsonElement,
  public val targetValue: JsonElement,
) : CatalogUpgradeChangeV1

/** Stable validation output; free-form [message] is explanatory rather than machine-actionable. */
@Serializable
public data class CatalogUpgradeIssueV1(
  public val severity: CatalogUpgradeIssueSeverityV1,
  public val code: String,
  public val path: String? = null,
  public val message: String,
)

@Serializable
public enum class CatalogUpgradeIssueSeverityV1 {
  @SerialName("info") INFO,
  @SerialName("warning") WARNING,
  @SerialName("error") ERROR,
}
