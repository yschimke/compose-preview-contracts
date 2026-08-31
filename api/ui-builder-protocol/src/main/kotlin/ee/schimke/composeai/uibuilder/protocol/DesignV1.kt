@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Persisted, catalog-pinned design tree. Child ordering is explicit on each node. */
@Serializable
public data class DesignDocumentV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val designId: String,
  public val name: String,
  public val catalog: CatalogReferenceV1,
  public val rootNodeId: String,
  public val nodes: Map<String, DesignNodeV1>,
  public val presentation: DesignPresentationV1,
  public val createdAtEpochMillis: Long,
  public val updatedAtEpochMillis: Long,
)

/** One component instance. Parent and child links are both present for deterministic replay. */
@Serializable
public data class DesignNodeV1(
  public val nodeId: String,
  public val componentKey: String,
  public val parentId: String? = null,
  public val parentSlot: String? = null,
  public val childIds: List<String> = emptyList(),
  public val properties: Map<String, UiValueV1> = emptyMap(),
  public val state: NodeStateV1 = NodeStateV1(),
)

/** Persisted node presentation flags; transient selection/presence is not stored here. */
@Serializable
public data class NodeStateV1(
  public val visible: Boolean = true,
  public val enabled: Boolean = true,
  public val variant: String? = null,
)

/** Device and theme inputs that make a render reproducible. */
@Serializable
public data class DesignPresentationV1(
  public val viewport: ViewportV1,
  public val theme: ThemeV1 = ThemeV1.SYSTEM,
  public val localeTag: String? = null,
  public val fontScale: Double = 1.0,
)

@Serializable
public data class ViewportV1(
  public val widthPx: Int,
  public val heightPx: Int,
  public val density: Double,
)

@Serializable
public enum class ThemeV1 {
  @SerialName("light") LIGHT,
  @SerialName("dark") DARK,
  @SerialName("system") SYSTEM,
}

/** Authoritative state at one revision and event-sequence cursor. */
@Serializable
public data class DesignStateV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val revision: Long,
  public val lastSequence: Long,
  public val document: DesignDocumentV1,
)

/** Non-persisted collaborative cursor/selection state for one connected actor. */
@Serializable
public data class PresenceV1(
  public val actorId: String,
  public val displayName: String,
  public val colorArgbHex: String,
  public val selectedNodeIds: List<String> = emptyList(),
  public val pointer: PointerV1? = null,
  public val observedRevision: Long,
)

@Serializable public data class PointerV1(public val x: Double, public val y: Double)
