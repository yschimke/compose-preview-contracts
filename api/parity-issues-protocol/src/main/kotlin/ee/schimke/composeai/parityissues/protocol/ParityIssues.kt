package ee.schimke.composeai.parityissues.protocol

import kotlinx.serialization.Serializable

/** A catalog-published snapshot of the GitHub issues joined to its previews. */
@Serializable
public data class ParityIssues(
  public val schema: String = SCHEMA,
  public val generatedAt: String? = null,
  public val issues: List<ParityIssue> = emptyList(),
) {
  public companion object {
    public const val SCHEMA: String = "compose-preview-issues/v1"
    public const val DIRECTORY: String = "parity"
    public const val FILE: String = "issues.json"
  }
}

/** One issue-to-preview join carried in [ParityIssues]. */
@Serializable
public data class ParityIssue(
  public val repository: String,
  public val number: Int,
  public val title: String,
  public val url: String,
  public val state: String,
  public val area: String? = null,
  public val parity: String? = null,
  public val system: String? = null,
  public val component: String? = null,
  /** `component` for every preview variant, or `variant` for only [previewIds]. */
  public val scope: String = COMPONENT_SCOPE,
  public val previewIds: List<String> = emptyList(),
  public val referenceIds: List<String> = emptyList(),
  public val acceptanceId: String? = null,
) {
  public companion object {
    public const val COMPONENT_SCOPE: String = "component"
    public const val VARIANT_SCOPE: String = "variant"
  }
}
