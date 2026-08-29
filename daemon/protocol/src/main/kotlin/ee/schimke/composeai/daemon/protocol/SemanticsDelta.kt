package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The semantics-diff wire shapes.
 *
 * Moved here from `data-layoutinspector-core` so a client that deserialises a daemon message does
 * not resolve that module's other ~60 public types to read four. `SemanticsDiff` — the algorithm
 * that produces a [SemanticsDelta] — is behaviour and stays in compose-ai-tools.
 */
public object SemanticsDiffProduct {
  public const val SCHEMA: String = "compose-semantics-diff/v1"
}

@Serializable
public data class SemanticsFieldChange(val field: String, val from: String?, val to: String?)

@Serializable
public data class SemanticsNodeChange(
  val ref: String,
  /** testTag/role anchor of the node, for human-readable output. */
  val anchor: String? = null,
  val changes: List<SemanticsFieldChange>,
)

@Serializable
public data class SemanticsNodeSummary(
  val ref: String,
  val role: String? = null,
  val testTag: String? = null,
  val text: String? = null,
  val label: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class SemanticsDelta(
  // `@EncodeDefault` so the versioned schema rides every wire surface — including JSON encoders
  // configured with `encodeDefaults = false` (the daemon's `history/diff mode=SEMANTICS` result and
  // the MCP `diff_semantics` payload). Without it an empty-or-default delta would serialize without
  // its `schema`, defeating the "versioned JSON delta" contract (issue #1785).
  @EncodeDefault val schema: String = SemanticsDiffProduct.SCHEMA,
  val added: List<SemanticsNodeSummary> = emptyList(),
  val removed: List<SemanticsNodeSummary> = emptyList(),
  val changed: List<SemanticsNodeChange> = emptyList(),
) {
  val isEmpty: Boolean
    get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()
}
