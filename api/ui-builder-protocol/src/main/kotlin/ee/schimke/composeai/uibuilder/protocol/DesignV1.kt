@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Persisted, revisioned semantic tree. Roots and every named slot retain authored order. */
@Serializable
public data class DesignDocumentV1(
  public val schema: String,
  public val id: String,
  public val title: String,
  public val revision: Long,
  public val catalogPin: CatalogReferenceV1,
  public val environment: DesignEnvironmentV1,
  public val stateVariables: Map<String, StateVariableV1> = emptyMap(),
  public val roots: List<String>,
  public val nodes: Map<String, DesignNodeV1>,
  public val assets: Map<String, AssetBindingV1> = emptyMap(),
  public val tokenBindings: Map<String, UiValueV1> = emptyMap(),
  public val createdAtEpochMillis: Long? = null,
  public val updatedAtEpochMillis: Long? = null,
)

/** One component instance; parentage is represented once, by roots and named slot child lists. */
@Serializable
public data class DesignNodeV1(
  public val id: String,
  public val componentId: String,
  @EncodeDefault public val properties: Map<String, UiValueV1> = emptyMap(),
  @EncodeDefault public val modifiers: List<DesignModifierV1> = emptyList(),
  @EncodeDefault public val slots: Map<String, List<String>> = emptyMap(),
  @EncodeDefault public val eventBindings: Map<String, List<DesignActionV1>> = emptyMap(),
  public val predicate: DesignPredicateV1? = null,
  public val accessibility: AccessibilityV1? = null,
  public val assetBindings: Map<String, String> = emptyMap(),
  public val tokenBindings: Map<String, String> = emptyMap(),
)

/** Complete deterministic render environment carried by the current builder document. */
@Serializable
public data class DesignEnvironmentV1(
  public val widthDp: Int,
  public val heightDp: Int,
  public val density: Double,
  public val theme: ThemeV1,
  public val dynamicColor: Boolean? = null,
  public val locale: String,
  public val fontScale: Double,
  public val layoutDirection: LayoutDirectionV1,
  public val windowPosture: WindowPostureV1? = null,
  public val browserZoomPercent: Int? = null,
  public val fixedTime: String? = null,
  public val animations: AnimationStateV1? = null,
  public val networkAccess: Boolean? = null,
  public val background: UiValueV1? = null,
  /**
   * Family name for the document's type scale, or null for the renderer's platform default.
   *
   * A **family name only** — never a file, a URL or a weight. The renderer decides how to obtain
   * it, and the two ways it can differ per host: a family the host vendors resolves offline, and
   * anything else is a Google Fonts family name the host downloads. Carrying a name rather than a
   * source is what lets one document render on a host that bundles the face and on one that fetches
   * it, without the document knowing which it is talking to.
   *
   * Appended last on purpose. The constructor is published ABI, so a new field goes on the end
   * rather than beside [fontScale] where it reads better.
   */
  public val typeface: String? = null,
)

@Serializable
public enum class ThemeV1 {
  @SerialName("light") LIGHT,
  @SerialName("dark") DARK,
  @SerialName("system") SYSTEM,
}

@Serializable
public enum class LayoutDirectionV1 {
  @SerialName("ltr") LTR,
  @SerialName("rtl") RTL,
}

@Serializable
public enum class WindowPostureV1 {
  @SerialName("flat") FLAT,
  @SerialName("book") BOOK,
  @SerialName("tabletop") TABLETOP,
}

@Serializable
public enum class AnimationStateV1 {
  @SerialName("settled") SETTLED,
  @SerialName("running") RUNNING,
  @SerialName("disabled") DISABLED,
}

/** Typed declared state; runtime values are preview state and do not mutate this definition. */
@Serializable
public data class StateVariableV1(
  public val type: StateVariableTypeV1,
  public val valueType: StateValueTypeV1? = null,
  public val nullable: Boolean? = null,
  /** Plain JSON scalar/null in the current authored fixtures, not a typed property wrapper. */
  public val initialValue: JsonElement,
  public val persistence: StatePersistenceV1,
)

@Serializable
public enum class StateVariableTypeV1 {
  @SerialName("text") TEXT,
  @SerialName("selection") SELECTION,
  @SerialName("value") VALUE,
}

@Serializable
public enum class StateValueTypeV1 {
  @SerialName("string") STRING,
  @SerialName("bool") BOOLEAN,
  @SerialName("int") INTEGER,
  @SerialName("float") DECIMAL,
}

@Serializable
public enum class StatePersistenceV1 {
  @SerialName("design") DESIGN,
  @SerialName("preview") PREVIEW,
  @SerialName("session") SESSION,
}

/** Ordered Compose modifier subset admitted by the v1 document. */
@Serializable public sealed interface DesignModifierV1

@Serializable @SerialName("fillMaxSize") public data object FillMaxSizeModifierV1 : DesignModifierV1

@Serializable
@SerialName("fillMaxWidth")
public data object FillMaxWidthModifierV1 : DesignModifierV1

@Serializable
@SerialName("matchParentSize")
public data object MatchParentSizeModifierV1 : DesignModifierV1

@Serializable
@SerialName("padding")
public data class PaddingModifierV1(
  @EncodeDefault public val startDp: JsonElement = JsonPrimitive(0),
  @EncodeDefault public val topDp: JsonElement = JsonPrimitive(0),
  @EncodeDefault public val endDp: JsonElement = JsonPrimitive(0),
  @EncodeDefault public val bottomDp: JsonElement = JsonPrimitive(0),
) : DesignModifierV1

@Serializable
@SerialName("size")
public data class SizeModifierV1(
  public val widthDp: JsonElement,
  public val heightDp: JsonElement,
) : DesignModifierV1

@Serializable
@SerialName("clip")
public data class ClipModifierV1(public val shape: String) : DesignModifierV1

/** Declarative event actions; arbitrary lambdas and Kotlin expressions are intentionally absent. */
@Serializable public sealed interface DesignActionV1

@Serializable
@SerialName("setText")
public data class SetTextActionV1(public val variable: String) : DesignActionV1

@Serializable
@SerialName("select")
public data class SelectActionV1(public val variable: String, public val value: JsonElement) :
  DesignActionV1

@Serializable
@SerialName("selectOrClear")
public data class SelectOrClearActionV1(
  public val variable: String,
  public val value: JsonElement,
) : DesignActionV1

@Serializable
@SerialName("set")
public data class SetValueActionV1(public val variable: String, public val value: JsonElement) :
  DesignActionV1

@Serializable
@SerialName("toggle")
public data class ToggleActionV1(public val variable: String) : DesignActionV1

@Serializable
@SerialName("increment")
public data class IncrementActionV1(
  public val variable: String,
  public val amount: JsonElement = JsonPrimitive(1),
) : DesignActionV1

@Serializable
@SerialName("navigatePage")
public data class NavigatePageActionV1(public val pageKey: String) : DesignActionV1

/** Optional deterministic composition predicate. */
@Serializable public sealed interface DesignPredicateV1

@Serializable
@SerialName("stateEquals")
public data class StateEqualsPredicateV1(
  public val variable: String,
  public val value: JsonElement,
) : DesignPredicateV1

@Serializable
@SerialName("stateTruthy")
public data class StateTruthyPredicateV1(public val variable: String) : DesignPredicateV1

@Serializable
@SerialName("not")
public data class NotPredicateV1(public val predicate: DesignPredicateV1) : DesignPredicateV1

@Serializable
@SerialName("all")
public data class AllPredicateV1(public val predicates: List<DesignPredicateV1>) : DesignPredicateV1

@Serializable
@SerialName("any")
public data class AnyPredicateV1(public val predicates: List<DesignPredicateV1>) : DesignPredicateV1

@Serializable
public data class AccessibilityV1(
  public val role: String? = null,
  public val label: String? = null,
  public val contentDescription: String? = null,
  public val stateDescription: String? = null,
  public val heading: Boolean = false,
  public val mergeDescendants: Boolean = false,
  public val traversalIndex: Double? = null,
)

@Serializable
public data class AssetBindingV1(
  public val mediaType: String,
  public val contentDigest: String,
  public val source: AssetSourceV1,
  public val widthPx: Int? = null,
  public val heightPx: Int? = null,
)

@Serializable public sealed interface AssetSourceV1

@Serializable
@SerialName("catalog")
public data class CatalogAssetSourceV1(public val assetKey: String) : AssetSourceV1

@Serializable
@SerialName("uploaded")
public data class UploadedAssetSourceV1(public val storageKey: String) : AssetSourceV1

@Serializable
@SerialName("embedded")
public data class EmbeddedAssetSourceV1(public val base64: String) : AssetSourceV1

/** Authoritative state at one durable event-sequence cursor. */
@Serializable
public data class DesignStateV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val lastSequence: Long,
  public val document: DesignDocumentV1,
)

/** Non-persisted collaborative cursor/selection state for one connected actor. */
@Serializable
public data class PresenceV1(
  public val actorId: String,
  public val clientId: String,
  public val displayName: String,
  public val colorArgbHex: String,
  public val selectedNodeIds: List<String> = emptyList(),
  public val pointer: PointerV1? = null,
  public val observedRevision: Long,
)

@Serializable public data class PointerV1(public val x: Double, public val y: Double)
