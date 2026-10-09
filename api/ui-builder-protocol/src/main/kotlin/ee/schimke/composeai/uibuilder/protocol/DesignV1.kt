@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
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
  /**
   * Composables this design defines once and places many times, by the key an instance names.
   *
   * A design says a repeated thing by repeating its nodes, which is the only thing the tree can
   * say: twelve cells are twelve nodes, and a change to what a cell *is* has to be made twelve
   * times. A component is the other half of that — one body, placed by reference — and it is the
   * shape a generator can write as its own `@Composable` rather than as the same call inlined
   * again.
   *
   * The bodies live in [nodes] like everything else, reached from [DesignComponentV1.root]: a
   * component's subtree is made of the same components, validated by the same rules and drawn by
   * the same renderer, and putting it in a second map would be a second thing for every reader to
   * walk. What separates a body from the screen is only that no [roots] entry leads to it.
   *
   * Empty in every document written before this field, which is what the default says.
   */
  public val components: Map<String, DesignComponentV1> = emptyMap(),
  /** The design's canonical location, retained when it is opened or copied elsewhere. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val home: DesignHomeV1? = null,
)

/**
 * The authoritative location from which a host can open a design.
 *
 * The discriminator is deliberately `kind`, rather than the protocol-wide `type`: this is a
 * location reference, not one of the command or value vocabularies. Its values say whether a design
 * belongs to a running builder server or to a repository checkout. A copy retains this reference;
 * moving the home is an explicit operation rather than an inference from where the copy was opened.
 */
@Serializable
@JsonClassDiscriminator("kind")
public sealed class DesignHomeV1 {
  /** A design served by a UI Builder host. */
  @Serializable
  @SerialName("server")
  public data class Server(public val url: String, public val designId: String) : DesignHomeV1()

  /** A design stored in a repository checkout. */
  @Serializable @SerialName("repo") public data class Repo(public val path: String) : DesignHomeV1()
}

/**
 * One composable a design defines: what it is called and where its body starts.
 *
 * **No parameter list, deliberately.** What an instance passes is a dictionary
 * ([DesignComponentInstanceV1.arguments]) and what the body reads is a key of it
 * ([BindingValueV1]); the signature a generator writes is derived from those keys rather than
 * declared beside them. A declared list would be a second statement of the same fact, free to
 * disagree with the bodies and the instances it describes — and the disagreement would be found by
 * whoever generated the screen, not by whoever wrote it.
 *
 * A generator therefore reads the keys, gives each the type its values carry, and orders them the
 * one way that cannot drift between exports: sorted. Values that disagree about a key's type, and a
 * key an instance never passes, are refusals it can state precisely — which is the same standard
 * the rest of this vocabulary is held to.
 *
 * @property name the generated function's name, in the spelling a person would write —
 *   `ContributionCell`. A generator that cannot write this name refuses rather than renaming it.
 * @property root the node the body starts at: a node in [DesignDocumentV1.nodes] that no
 *   [DesignDocumentV1.roots] entry reaches, so a reader ignoring components sees a subtree nothing
 *   draws rather than a screen with strange extra content.
 * @property description what the component is for, for the person choosing it from a palette.
 * @property source where this body came from, when a project's library rather than this design
 *   defined it. Null for a component authored here, which is every component written before this
 *   field existed.
 */
@Serializable
public data class DesignComponentV1(
  public val name: String,
  public val root: String,
  public val description: String? = null,
  /**
   * Never encoded when absent, even by an encoder asking for defaults: the cross-language document
   * hash is taken over this shape, and a design whose components are its own must canonicalize to
   * exactly what it did before this field existed.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val source: ComponentSourceV1? = null,
)

/**
 * The published symbol a component's body was imported from, and what it looked like at the time.
 *
 * A project shares components between its designs by publishing them, and a design that uses one
 * holds the body — so it draws, exports and travels without asking anything. Holding the body is
 * what makes this a *copy* unless something records where it came from, and a copy is not reuse:
 * the second one stops tracking the first the moment either is edited.
 *
 * So the design records the reference beside the body. [digest] is the symbol's content digest as
 * it was when imported; a later read of the library that computes a different one has found the
 * library moved, which is reported as drift with a preview rather than silently redrawn. That is
 * the same bargain [CatalogReferenceV1] strikes for the catalog a design is pinned to, and it is
 * struck here for the same reason: a design must never quietly become a different design.
 *
 * @property system the project the symbol belongs to — the catalog system id of its library.
 * @property componentId the id that project publishes it under, which is `project/<componentId>`
 *   where a palette names it.
 * @property digest the symbol's content digest at import, over the component and its body only, so
 *   reformatting the published file is not drift and editing what it draws is.
 */
@Serializable
public data class ComponentSourceV1(
  public val system: String,
  public val componentId: String,
  public val digest: String,
)

/**
 * What makes a node an instance of a [DesignComponentV1] rather than of a catalog component.
 *
 * Carried as its own field rather than as a property of the node, because it is not a property: a
 * reader that has never heard of components can see that this node is one and draw a placeholder,
 * where a reserved key in the property bag would read as a component with a property nobody
 * declared. The node's [DesignNodeV1.componentId] is [DESIGN_COMPONENT_INSTANCE_COMPONENT_ID] for
 * the same reason.
 *
 * @property componentKey the key into [DesignDocumentV1.components]. A key that resolves to nothing
 *   is a refusal at the door, the way an `assetKey` nothing resolves already is.
 * @property arguments the dictionary the body reads, by key. Open: a key is whatever the author
 *   used, and it becomes a parameter of the generated function because the body reads it, not
 *   because anything here declared it. Content an instance supplies is *not* here — children are
 *   children, and arrive through the node's own [DesignNodeV1.slots].
 */
@Serializable
public data class DesignComponentInstanceV1(
  public val componentKey: String,
  @EncodeDefault public val arguments: Map<String, UiValueV1> = emptyMap(),
)

/**
 * The component id a node carries when it is an instance of a design's own component.
 *
 * Published here because it is where both sides of the wire meet: a host writes it and a renderer
 * reads it, and a constant they share is one fewer string to spell differently.
 */
public const val DESIGN_COMPONENT_INSTANCE_COMPONENT_ID: String = "design/component-instance"

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
  /**
   * Which of the design's own components this node places, or null for an ordinary catalog node.
   *
   * Appended last on purpose: the constructor is published ABI, so a new field goes on the end
   * rather than beside [componentId] where it reads better.
   */
  public val component: DesignComponentInstanceV1? = null,
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
  /**
   * Device ids this design is also exported as, beside the one frame [widthDp], [heightDp] and
   * [density] describe.
   *
   * A design is authored at one size and lives at several. The frame fields carry the size it is
   * *drawn* at — the canvas its author approved — and this carries the others its export should
   * cover, so "which devices does this screen claim to work on?" is a question the document answers
   * rather than one each exporter guesses from the catalog it happens to be generating for.
   *
   * Ids, not geometry, and deliberately: a device id is what a `@Preview(device = …)` resolves and
   * what the render lane's own catalog is keyed by, so a document that names one cannot describe a
   * frame no renderer produces. A width and height here would be a second copy of that catalog,
   * free to disagree with it.
   *
   * Empty means the design exports at its own frame alone, which is what every document written
   * before this field said and is why that is the default. The frame device itself need not appear
   * here; a consumer that renders both renders the frame from the fields above.
   *
   * Appended last, like [typeface] and for the same reason: the constructor is published ABI.
   */
  public val exportDevices: List<String> = emptyList(),
  /**
   * The Remote Compose profile this design's document is written for, or null for the consumer's
   * default for the document's kind: a Wear widget is [RemoteProfileTargetV1.WEAR_WIDGETS], a
   * launcher widget [RemoteProfileTargetV1.LAUNCHER_WIDGETS_V6], and any other Remote Compose
   * document [RemoteProfileTargetV1.ANDROIDX]. Meaningless for a design that is not exported as a
   * Remote Compose document.
   *
   * Appended last for the reason [exportDevices] was, and never encoded while null, so a document
   * that names no profile serialises exactly as it did before this field existed.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val remoteProfile: RemoteProfileV1? = null,
) {
  /**
   * Preserves the constructor that predates [remoteProfile], **including its default-argument
   * form**. Code compiled against earlier releases calls the synthetic `(..., Int,
   * DefaultConstructorMarker)` constructor when it leaves an optional field out, and a primary
   * constructor that only gained a parameter has no such constructor: the call fails with
   * `NoSuchMethodError` at runtime, in a jar nobody recompiled.
   */
  public constructor(
    widthDp: Int,
    heightDp: Int,
    density: Double,
    theme: ThemeV1,
    dynamicColor: Boolean? = null,
    locale: String,
    fontScale: Double,
    layoutDirection: LayoutDirectionV1,
    windowPosture: WindowPostureV1? = null,
    browserZoomPercent: Int? = null,
    fixedTime: String? = null,
    animations: AnimationStateV1? = null,
    networkAccess: Boolean? = null,
    background: UiValueV1? = null,
    typeface: String? = null,
    exportDevices: List<String> = emptyList(),
  ) : this(
    widthDp,
    heightDp,
    density,
    theme,
    dynamicColor,
    locale,
    fontScale,
    layoutDirection,
    windowPosture,
    browserZoomPercent,
    fixedTime,
    animations,
    networkAccess,
    background,
    typeface,
    exportDevices,
    null,
  )

  /** The `copy` of earlier releases, for the same reason; it carries [remoteProfile]. */
  public fun copy(
    widthDp: Int = this.widthDp,
    heightDp: Int = this.heightDp,
    density: Double = this.density,
    theme: ThemeV1 = this.theme,
    dynamicColor: Boolean? = this.dynamicColor,
    locale: String = this.locale,
    fontScale: Double = this.fontScale,
    layoutDirection: LayoutDirectionV1 = this.layoutDirection,
    windowPosture: WindowPostureV1? = this.windowPosture,
    browserZoomPercent: Int? = this.browserZoomPercent,
    fixedTime: String? = this.fixedTime,
    animations: AnimationStateV1? = this.animations,
    networkAccess: Boolean? = this.networkAccess,
    background: UiValueV1? = this.background,
    typeface: String? = this.typeface,
    exportDevices: List<String> = this.exportDevices,
  ): DesignEnvironmentV1 =
    copy(
      widthDp,
      heightDp,
      density,
      theme,
      dynamicColor,
      locale,
      fontScale,
      layoutDirection,
      windowPosture,
      browserZoomPercent,
      fixedTime,
      animations,
      networkAccess,
      background,
      typeface,
      exportDevices,
      this.remoteProfile,
    )
}

/**
 * The Remote Compose profile a design's document targets: which player, at which document API
 * level, with which operations. Shape only: this names the target, and the consumer decides what a
 * target permits (androidx `RcPlatformProfiles` is the reference for each).
 */
@Serializable
@ConsistentCopyVisibility
public data class RemoteProfileV1
internal constructor(
  public val target: RemoteProfileTargetV1,
  /**
   * Adds androidx `RcProfiles.PROFILE_EXPERIMENTAL` (`0x1`) to [target]'s profile: "the supported
   * set of additional operations … is not strongly defined", and may include features only some
   * players support.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val experimental: Boolean = false,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(target: RemoteProfileTargetV1) {
    public var target: RemoteProfileTargetV1 = target
    public var experimental: Boolean = false

    public fun build(): RemoteProfileV1 = RemoteProfileV1(target, experimental)
  }

  /** This profile as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(target).also { it.experimental = experimental }
}

/** The players a Remote Compose document can be written for, named as androidx names them. */
@Serializable
public enum class RemoteProfileTargetV1 {
  /**
   * Wear OS widgets: androidx `RcPlatformProfiles.WEAR_WIDGETS`, at
   * `CoreDocument.DOCUMENT_API_LEVEL` with `RcProfiles.PROFILE_WEAR_WIDGETS` (`0x800`), a
   * restricted set of operations within the baseline.
   */
  @SerialName("wear-widgets") WEAR_WIDGETS,

  /**
   * Launcher widgets on the Android 16 ("Baklava") platform player: androidx
   * `RcPlatformProfiles.WIDGETS_V6`, document API level 6 on the baseline profile (`0`).
   */
  @SerialName("launcher-widgets-v6") LAUNCHER_WIDGETS_V6,

  /**
   * Launcher widgets on the Android 17 ("Cinnamon Bun") platform player: androidx
   * `RcPlatformProfiles.WIDGETS_V7`, document API level 7 with `RcProfiles.PROFILE_WIDGETS`
   * (`0x100`).
   */
  @SerialName("launcher-widgets-v7") LAUNCHER_WIDGETS_V7,

  /**
   * The embedded AndroidX player: androidx `RcPlatformProfiles.ANDROIDX`, at
   * `CoreDocument.DOCUMENT_API_LEVEL` with `RcProfiles.PROFILE_ANDROIDX` (`0x200`).
   */
  @SerialName("androidx") ANDROIDX,
}

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
/**
 * A `RemoteModifier` call the typed modifiers above do not name, for documents a Remote Compose
 * catalog renders: [name] is the function (`graphicsLayer`, `border`, `visibility`, …) and [args]
 * its arguments by parameter name, each a value — so a literal, a state read or a computed value.
 *
 * Open by name on purpose. The released Remote Compose API is authoritative for which calls exist
 * and what they take; a catalog generates that vocabulary from it and validates a document against
 * it, as it already does for component ids and event names. A wire type per call would make every
 * new alpha a contracts release. Still declarative — no lambda, no `then` — and still ordered with
 * the rest of the chain. Interaction stays out, as above: behaviour is event bindings.
 */
@Serializable
@SerialName("remoteCall")
@ConsistentCopyVisibility
public data class RemoteCallModifierV1
internal constructor(
  public val name: String,
  public val args: Map<String, UiValueV1> = emptyMap(),
) : DesignModifierV1 {
  /**
   * Builds a [RemoteCallModifierV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(name: String) {
    public var name: String = name
    public var args: Map<String, UiValueV1> = emptyMap()

    public fun build(): RemoteCallModifierV1 = RemoteCallModifierV1(name, args)
  }

  /** This [RemoteCallModifierV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(name).also { it.args = args }
}

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

/**
 * One asset a design refers to.
 *
 * [sizeBytes] and [provenance] are host-supplied and both absent from every binding written before
 * they existed. Neither is ever encoded when absent, even by an encoder asking for defaults: the
 * cross-language document hash is taken over this shape, and a binding without them must
 * canonicalize to exactly what it did before these fields existed.
 *
 * @property sizeBytes the stored size of the asset, so a client can show what a design spends
 *   against its [AssetQuotaV1] without fetching the bytes.
 * @property provenance who added the asset, and when; see [AssetProvenanceV1].
 */
@Serializable
public data class AssetBindingV1(
  public val mediaType: String,
  public val contentDigest: String,
  public val source: AssetSourceV1,
  public val widthPx: Int? = null,
  public val heightPx: Int? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val sizeBytes: Long? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val provenance: AssetProvenanceV1? = null,
) {
  /**
   * Preserves the constructor that predates [sizeBytes] and [provenance], **including its
   * default-argument form**. Code compiled against earlier releases calls the synthetic `(..., Int,
   * DefaultConstructorMarker)` constructor when it leaves [widthPx] or [heightPx] out, and a
   * primary constructor that only gained parameters has no such constructor: the call fails with
   * `NoSuchMethodError` at runtime, in a jar nobody recompiled. Keeping this one keeps them
   * linking, and it wins over the primary constructor at a call site that names no newer field.
   */
  public constructor(
    mediaType: String,
    contentDigest: String,
    source: AssetSourceV1,
    widthPx: Int? = null,
    heightPx: Int? = null,
  ) : this(mediaType, contentDigest, source, widthPx, heightPx, null, null)

  /**
   * Builds an [AssetBindingV1]. Prefer this to a constructor: a field added later is a new property
   * here, and never replaces a signature a released consumer has already linked against. The
   * constructors stay public until the next major release, when they become `internal` as the other
   * evolving wire types' are.
   */
  public class Builder(mediaType: String, contentDigest: String, source: AssetSourceV1) {
    public var mediaType: String = mediaType
    public var contentDigest: String = contentDigest
    public var source: AssetSourceV1 = source
    public var widthPx: Int? = null
    public var heightPx: Int? = null
    public var sizeBytes: Long? = null
    public var provenance: AssetProvenanceV1? = null

    public fun build(): AssetBindingV1 =
      AssetBindingV1(mediaType, contentDigest, source, widthPx, heightPx, sizeBytes, provenance)
  }

  /** This binding as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(mediaType, contentDigest, source).also {
      it.widthPx = widthPx
      it.heightPx = heightPx
      it.sizeBytes = sizeBytes
      it.provenance = provenance
    }

  /**
   * The `copy` of earlier releases, for the same reason; it carries [sizeBytes] and [provenance].
   */
  public fun copy(
    mediaType: String = this.mediaType,
    contentDigest: String = this.contentDigest,
    source: AssetSourceV1 = this.source,
    widthPx: Int? = this.widthPx,
    heightPx: Int? = this.heightPx,
  ): AssetBindingV1 =
    copy(mediaType, contentDigest, source, widthPx, heightPx, this.sizeBytes, this.provenance)
}

/**
 * Who added an asset to a design, for display and audit.
 *
 * A host records what it knows; every field but [actorId] may be absent. It says nothing about
 * rights to the content, and nothing reads it to decide access.
 *
 * @property actorId the actor that added the asset, in the same form as any other actor id.
 * @property addedAtEpochMillis when the host accepted it.
 * @property originalName the file name the author supplied, when a host retains it. Display only:
 *   it is not an identity, and the content is addressed by [AssetBindingV1.contentDigest].
 */
@Serializable
public data class AssetProvenanceV1(
  public val actorId: String,
  public val addedAtEpochMillis: Long? = null,
  public val originalName: String? = null,
)

/**
 * How much of a design's asset budget is spent, so a client can say so before an upload is refused.
 *
 * The limits are the host's policy and are reported, never negotiated: a mutation that would exceed
 * them is rejected with [RejectionCodeV1.ASSET_QUOTA_EXCEEDED].
 *
 * @property usedBytes the total stored size of the design's assets.
 * @property limitBytes the most a design may store in total.
 * @property maxAssetBytes the most one asset may be, when the host limits that separately.
 */
@Serializable
public data class AssetQuotaV1(
  public val usedBytes: Long,
  public val limitBytes: Long,
  public val maxAssetBytes: Long? = null,
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
  /**
   * How much of the design's asset budget is spent, when the host enforces one. Absent means the
   * host reports no limit, not that there is none.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val assetQuota: AssetQuotaV1? = null,
) {
  /**
   * Preserves the constructor that predates [assetQuota], including its default-argument form: code
   * compiled against earlier releases calls `(Int, Long, DesignDocumentV1, Int,
   * DefaultConstructorMarker)` when it leaves [schemaVersion] out, and that descriptor only exists
   * while a constructor with this shape and a default does. Without it such a jar fails with
   * `NoSuchMethodError` the first time it builds a state.
   */
  public constructor(
    schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
    lastSequence: Long,
    document: DesignDocumentV1,
  ) : this(schemaVersion, lastSequence, document, null)

  /**
   * Builds a [DesignStateV1]. Prefer this to a constructor: a field added later is a new property
   * here, and never replaces a signature a released consumer has already linked against. The
   * constructors stay public until the next major release, when they become `internal`.
   */
  public class Builder(lastSequence: Long, document: DesignDocumentV1) {
    public var schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1
    public var lastSequence: Long = lastSequence
    public var document: DesignDocumentV1 = document
    public var assetQuota: AssetQuotaV1? = null

    public fun build(): DesignStateV1 =
      DesignStateV1(schemaVersion, lastSequence, document, assetQuota)
  }

  /** This state as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(lastSequence, document).also {
      it.schemaVersion = schemaVersion
      it.assetQuota = assetQuota
    }

  /** The `copy` of earlier releases, for the same reason; it carries [assetQuota]. */
  public fun copy(
    schemaVersion: Int = this.schemaVersion,
    lastSequence: Long = this.lastSequence,
    document: DesignDocumentV1 = this.document,
  ): DesignStateV1 = copy(schemaVersion, lastSequence, document, this.assetQuota)
}

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
