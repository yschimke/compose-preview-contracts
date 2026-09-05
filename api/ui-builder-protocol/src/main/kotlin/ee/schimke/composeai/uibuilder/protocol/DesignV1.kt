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

/**
 * Ordered Compose modifier subset admitted by the v1 document.
 *
 * Closed and declarative: a modifier is a value a document can hold, a renderer can apply and a
 * generator can write out, which is why there is no lambda, no `Modifier.then` and no arbitrary
 * expression here. The chain is order-dependent — padding then size is a different layout from size
 * then padding — and is authored whole by `SetModifiersMutationV1`.
 *
 * Three deliberate absences, so their omission is a decision rather than an oversight:
 *
 * * **Fractional fills.** `fillMaxWidth(0.5f)` would mean a field on [FillMaxWidthModifierV1], and
 *   those three are published `data object`s whose encoded form is `{"type": …}` and nothing else.
 *   Giving them a field changes the bytes every stored document encodes to, and every canonical
 *   hash taken over one. [WidthModifierV1] and [WeightModifierV1] cover what the fraction is for.
 * * **`requiredSize` and the other constraint-defeating modifiers.** They ignore the parent's
 *   constraints, so a design authored with one overflows silently where the same design in an app
 *   would be clipped by its parent. A builder that offers it produces layouts that only look right
 *   in the builder.
 * * **`clickable` and the other interaction modifiers.** A node's behaviour is its event bindings
 *   ([DesignNodeV1.eventBindings]), which the reducer validates against declared state. A second
 *   way to say "this is tappable", one of them unvalidated, is how the two disagree.
 */
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
@SerialName("fillMaxHeight")
public data object FillMaxHeightModifierV1 : DesignModifierV1

/**
 * One dimension, where [SizeModifierV1] requires both.
 *
 * Not sugar: a card that fills the width of its column and is 96dp tall is `fillMaxWidth` then
 * `height`, and expressing it with `size` means inventing a width the design does not have.
 */
@Serializable
@SerialName("width")
public data class WidthModifierV1(public val widthDp: JsonElement) : DesignModifierV1

@Serializable
@SerialName("height")
public data class HeightModifierV1(public val heightDp: JsonElement) : DesignModifierV1

/**
 * A bound rather than a size, on one axis. A null edge is that edge unconstrained.
 *
 * The responsive half of the vocabulary: "at least 240dp, at most half the pane" is a constraint,
 * and writing it as a size pins the layout at one window width.
 */
@Serializable
@SerialName("widthIn")
public data class WidthInModifierV1(
  public val minDp: JsonElement? = null,
  public val maxDp: JsonElement? = null,
) : DesignModifierV1

@Serializable
@SerialName("heightIn")
public data class HeightInModifierV1(
  public val minDp: JsonElement? = null,
  public val maxDp: JsonElement? = null,
) : DesignModifierV1

/** Width to height, for the media a design lays out before it has the media. */
@Serializable
@SerialName("aspectRatio")
public data class AspectRatioModifierV1(public val ratio: JsonElement) : DesignModifierV1

/**
 * Take the space the parent offers and place the content inside it.
 *
 * A null [alignment] is the platform default, which is centre for both axes.
 */
@Serializable
@SerialName("wrapContentSize")
public data class WrapContentSizeModifierV1(public val alignment: AlignmentV1? = null) :
  DesignModifierV1

/**
 * A share of what is left on a row's or a column's main axis.
 *
 * Scoped: it means nothing outside a `Row` or a `Column`, and a renderer applies it in the scope it
 * is composing rather than as a plain modifier. [fill] false takes at most the share rather than
 * exactly it, which is Compose's own second parameter and the difference between a sidebar that
 * shrinks and one that does not.
 */
@Serializable
@SerialName("weight")
public data class WeightModifierV1(
  public val weight: JsonElement,
  public val fill: Boolean? = null,
) : DesignModifierV1

/**
 * Where a child sits inside a `Box`.
 *
 * Three alignment modifiers rather than one with a union of values, because the axes a scope allows
 * are not a matter of taste: a `Row` aligns its children vertically and a `Column` horizontally,
 * and a single `align` would carry values half of its uses must ignore.
 */
@Serializable
@SerialName("align")
public data class AlignModifierV1(public val alignment: AlignmentV1) : DesignModifierV1

/** Where a child sits across a `Column`'s cross axis. */
@Serializable
@SerialName("alignHorizontal")
public data class AlignHorizontalModifierV1(public val alignment: HorizontalAlignmentV1) :
  DesignModifierV1

/** Where a child sits across a `Row`'s cross axis. */
@Serializable
@SerialName("alignVertical")
public data class AlignVerticalModifierV1(public val alignment: VerticalAlignmentV1) :
  DesignModifierV1

/** Moves the drawing without moving the layout, in layout direction terms. */
@Serializable
@SerialName("offset")
public data class OffsetModifierV1(
  @EncodeDefault public val xDp: JsonElement = JsonPrimitive(0),
  @EncodeDefault public val yDp: JsonElement = JsonPrimitive(0),
) : DesignModifierV1

/** Draw order within a parent, where the tree's own order is not what the design wants. */
@Serializable
@SerialName("zIndex")
public data class ZIndexModifierV1(public val zIndex: JsonElement) : DesignModifierV1

/**
 * A fill behind the node, and the shape it takes.
 *
 * The colour is a [UiValueV1] rather than a string, so a background is a design token where the
 * document has one and a literal where it does not — the same choice every colour property makes. A
 * null [shape] is a rectangle.
 */
@Serializable
@SerialName("background")
public data class BackgroundModifierV1(
  public val color: UiValueV1,
  public val shape: String? = null,
) : DesignModifierV1

/** A stroke around the node, in the same shape vocabulary as [ClipModifierV1]. */
@Serializable
@SerialName("border")
public data class BorderModifierV1(
  public val widthDp: JsonElement,
  public val color: UiValueV1,
  public val shape: String? = null,
) : DesignModifierV1

/** Opacity of everything the node draws, 0..1. */
@Serializable
@SerialName("alpha")
public data class AlphaModifierV1(public val alpha: JsonElement) : DesignModifierV1

/**
 * Elevation, its shape, and whether the node's content is clipped to it.
 *
 * Separate from a card's own elevation property: this is elevation on a node that has no such
 * property, which is the case a design hits the moment it stops using cards for everything.
 */
@Serializable
@SerialName("shadow")
public data class ShadowModifierV1(
  public val elevationDp: JsonElement,
  public val shape: String? = null,
  public val clip: Boolean? = null,
) : DesignModifierV1

/** Clockwise degrees about the node's centre; drawing only, like [ScaleModifierV1]. */
@Serializable
@SerialName("rotate")
public data class RotateModifierV1(public val degrees: JsonElement) : DesignModifierV1

/** Draws the node larger or smaller without changing the space it takes. */
@Serializable
@SerialName("scale")
public data class ScaleModifierV1(
  public val scaleX: JsonElement,
  public val scaleY: JsonElement,
) : DesignModifierV1

/**
 * Scrolls content taller (or wider) than the space it is given.
 *
 * Stateful in Compose — a scroll position has to live somewhere — and that state is the renderer's
 * to hold, not the document's: two viewers of one design scroll independently, and a scroll offset
 * persisted into a design would be one person's reading position becoming everybody's.
 */
@Serializable
@SerialName("verticalScroll")
public data object VerticalScrollModifierV1 : DesignModifierV1

@Serializable
@SerialName("horizontalScroll")
public data object HorizontalScrollModifierV1 : DesignModifierV1

/**
 * The tag generated code exposes for a test to find this node by.
 *
 * Carried by the document rather than invented by the generator, because a test written against a
 * generated screen must not break when the generator's naming changes.
 */
@Serializable
@SerialName("testTag")
public data class TestTagModifierV1(public val tag: String) : DesignModifierV1

@Serializable
@SerialName("clip")
public data class ClipModifierV1(public val shape: String) : DesignModifierV1

/** Both axes at once, for a `Box` child and for [WrapContentSizeModifierV1]. */
@Serializable
public enum class AlignmentV1 {
  @SerialName("topStart") TOP_START,
  @SerialName("topCenter") TOP_CENTER,
  @SerialName("topEnd") TOP_END,
  @SerialName("centerStart") CENTER_START,
  @SerialName("center") CENTER,
  @SerialName("centerEnd") CENTER_END,
  @SerialName("bottomStart") BOTTOM_START,
  @SerialName("bottomCenter") BOTTOM_CENTER,
  @SerialName("bottomEnd") BOTTOM_END,
}

/** The axis a `Column` aligns its children across; start and end are layout-direction relative. */
@Serializable
public enum class HorizontalAlignmentV1 {
  @SerialName("start") START,
  @SerialName("centerHorizontally") CENTER_HORIZONTALLY,
  @SerialName("end") END,
}

/** The axis a `Row` aligns its children across. */
@Serializable
public enum class VerticalAlignmentV1 {
  @SerialName("top") TOP,
  @SerialName("centerVertically") CENTER_VERTICALLY,
  @SerialName("bottom") BOTTOM,
}

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
