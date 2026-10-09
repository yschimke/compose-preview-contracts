package ee.schimke.composeai.discovery

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * `ui-builder.policy.json` — the **catalog-level** half of what a catalog tells a UI builder,
 * authored beside `catalog.spec.json`.
 *
 * Per-component policy is [BuilderPolicy], an annotation on the sticker, so a component is never
 * renamed in two places. What is here is what belongs to no component: the platform word, the
 * screen frame and its measured geometry, the structural code templates, the template designs. The
 * one exception is [builtins], and it is the exception that proves the rule — a screen scaffold or
 * a shape that is not a composable at all has no call site, so it cannot be in the record and there
 * is no sticker to annotate.
 *
 * Field-by-field documentation, including the shapes this class deliberately keeps as raw
 * [JsonElement], is in `scripts/design-artifacts/ui-builder.policy.schema.json`; the contract is
 * [UI_BUILDER_CATALOG_CONTRACT.md](https://github.com/yschimke/compose-preview-server/blob/main/docs/design/UI_BUILDER_CATALOG_CONTRACT.md).
 *
 * ### Why several fields are `JsonElement`
 *
 * [previewSurfaces], [browserPreview], [frame] and [colorTokens] are carried through to the
 * generated file **verbatim** rather than parsed into Kotlin. They are read by the preview server,
 * whose types own their shape; re-declaring them here would put a second definition of somebody
 * else's contract in the middle of the pipeline, where it would be the thing that has to be updated
 * for a field this generator never looks at. What this generator validates about them, it validates
 * structurally.
 *
 * `frame.geometry` in particular is written by the catalog's own Robolectric probe and asserted
 * against the committed file by that same test. Parsing it here would add a second opinion about
 * numbers that are measured, and the whole reason the block lives in the catalog repository is that
 * there be only one.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderPolicyFile
internal constructor(
  @SerialName("\$schema") public val jsonSchema: String? = null,
  @SerialName("\$comment") public val comment: String? = null,
  public val schema: String,
  /** The id the builder authors against; defaults to `catalog.spec.json`'s `system`. */
  public val catalogId: String? = null,
  /**
   * What a derived builder id is prefixed with, when it is not `<catalogId>/`.
   *
   * A component's builder id is the string a saved design stores in every node, so it is derived
   * rather than authored per component — but the derivation has to be able to produce the ids
   * designs *already* store. m3-catalog's are `m3/button` and `m3/card`, not `m3-catalog/button`:
   * the catalog is named for the repository and the components for the library, and no rename can
   * reconcile that without invalidating every saved design. An explicit `@BuilderComponent(id = …)`
   * still wins over this.
   */
  public val componentIdPrefix: String? = null,
  /** The platform word. Equality is compatibility; there is no enum. */
  public val platform: String,
  /** What the New design chooser prints over the group; defaults to [platform] title-cased. */
  public val platformLabel: String? = null,
  public val previewSurfaces: JsonElement? = null,
  /** Typed by the consuming UI-builder protocol; carried without a duplicate definition here. */
  public val browserPreview: JsonElement? = null,
  public val frame: JsonElement? = null,
  public val builtins: Map<String, UiBuilderBuiltin> = emptyMap(),
  public val menu: UiBuilderMenu? = null,
  public val code: UiBuilderCode? = null,
  /**
   * The safe, versioned Compose source adapter this catalog selects, if it exports Compose source.
   *
   * This is a declaration, not source code. The consumer resolves the id/version only against
   * adapters it ships; an unknown declaration refuses export rather than executing catalog data.
   */
  public val composeSourceExport: UiBuilderComposeSourceExport? = null,
  /**
   * The template designs offered in the New design chooser: each a branch-relative path, or an
   * object naming one with what the chooser says about it (label, supporting text, heading, order,
   * whether it is the default).
   */
  public val templates: List<UiBuilderTemplateEntry> = emptyList(),
  /** How the catalog appears in the New design chooser as a whole: its chip's label and order. */
  public val newDesign: UiBuilderNewDesign? = null,
  public val colorTokens: JsonElement? = null,
  public val assetRegistry: JsonElement? = null,
  /** Component/property/slot successor rules, carried verbatim for catalog-upgrade previews. */
  public val supersedes: JsonElement? = null,
  /**
   * Per-component policy the catalog states here rather than on a sticker, keyed by builder id.
   *
   * `@BuilderComponent` is the right place for what belongs to one sticker — its group, its variant
   * property, whether to exclude it. It is the wrong place for a component's **vocabulary**: the
   * properties a design may set, the slots it may fill and the modifiers it accepts are editorial
   * decisions about the catalog's shelf, they run to dozens of entries per component, and a catalog
   * can hold them without annotating anything. m3-catalog has 104 record components and **zero**
   * `@BuilderComponent` annotations, and the vocabulary it needs to publish is the one its frozen
   * capability document already states.
   *
   * Joined by [UiBuilderAuthoredComponent.record] when present, which lets the map key preserve the
   * published builder id across a source rename. Otherwise it is merged onto the annotation-derived
   * entry for the same id, so the two can be used together and neither has to carry the other's
   * concerns. An entry joining no record component is reported rather than dropped — see
   * `Diagnostics.POLICY_ORPHANED`'s sibling for the annotation case, and the same argument: a
   * policy naming nothing is a rename that got away.
   */
  public val components: Map<String, UiBuilderAuthoredComponent> = emptyMap(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var schema: String,
    public var platform: String,
  ) {
    public var jsonSchema: String? = null
    public var comment: String? = null
    public var catalogId: String? = null
    public var componentIdPrefix: String? = null
    public var platformLabel: String? = null
    public var previewSurfaces: JsonElement? = null
    public var browserPreview: JsonElement? = null
    public var frame: JsonElement? = null
    public var builtins: Map<String, UiBuilderBuiltin> = emptyMap()
    public var menu: UiBuilderMenu? = null
    public var code: UiBuilderCode? = null
    public var composeSourceExport: UiBuilderComposeSourceExport? = null
    public var templates: List<UiBuilderTemplateEntry> = emptyList()
    public var newDesign: UiBuilderNewDesign? = null
    public var colorTokens: JsonElement? = null
    public var assetRegistry: JsonElement? = null
    public var supersedes: JsonElement? = null
    public var components: Map<String, UiBuilderAuthoredComponent> = emptyMap()

    public fun build(): UiBuilderPolicyFile =
      UiBuilderPolicyFile(
        jsonSchema,
        comment,
        schema,
        catalogId,
        componentIdPrefix,
        platform,
        platformLabel,
        previewSurfaces,
        browserPreview,
        frame,
        builtins,
        menu,
        code,
        composeSourceExport,
        templates,
        newDesign,
        colorTokens,
        assetRegistry,
        supersedes,
        components,
      )
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(schema, platform).also {
      it.jsonSchema = jsonSchema
      it.comment = comment
      it.catalogId = catalogId
      it.componentIdPrefix = componentIdPrefix
      it.platformLabel = platformLabel
      it.previewSurfaces = previewSurfaces
      it.browserPreview = browserPreview
      it.frame = frame
      it.builtins = builtins
      it.menu = menu
      it.code = code
      it.composeSourceExport = composeSourceExport
      it.templates = templates
      it.newDesign = newDesign
      it.colorTokens = colorTokens
      it.assetRegistry = assetRegistry
      it.supersedes = supersedes
      it.components = components
    }
}

/** A catalog-owned selection of a shipped Compose source-export adapter. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderComposeSourceExport
internal constructor(
  public val adapter: String,
  public val version: Int,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var adapter: String,
    public var version: Int,
  ) {

    public fun build(): UiBuilderComposeSourceExport =
      UiBuilderComposeSourceExport(adapter, version)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(adapter, version)
}

/**
 * One component's authored policy.
 *
 * The three capability blocks are carried as raw JSON on purpose. Their shape is the UI builder's —
 * `PropertyCapabilityV1`, `SlotCapabilityV1` — and re-declaring it here would be a second
 * definition of a contract this repository does not own, which is exactly the drift the published
 * `ui-builder.json` exists to avoid. The preview server validates them when it composes the shelf
 * and refuses a catalog whose declarations it cannot serve; this carries them faithfully.
 *
 * Every field is nullable so that "not stated" and "stated as empty" stay different questions: a
 * catalog declaring `modifierCapabilities: []` means the component accepts none, and one omitting
 * it means the consumer should fall back.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderAuthoredComponent
internal constructor(
  /** The record's `canonicalId`, joining this policy to the inventory. */
  public val record: String? = null,
  /** The shelf this component appears on, as `@BuilderComponent(group = …)` would say it. */
  public val group: String? = null,
  public val displayName: String? = null,
  public val canvas: String? = null,
  /** Canvas-only property/slot projection, owned by the consuming UI-builder protocol. */
  public val canvasMapping: JsonElement? = null,
  /** The editing canvas's mock for this component — see [UiBuilderUnrolledMock]. */
  public val unrolled: UiBuilderUnrolledMock? = null,
  public val nativeOnly: Boolean? = null,
  public val traits: List<String>? = null,
  /** Kept off the shelf, with the stated reason. */
  public val excluded: String? = null,
  public val propertyCapabilities: List<JsonElement>? = null,
  public val slotCapabilities: List<JsonElement>? = null,
  public val modifierCapabilities: List<String>? = null,
  /**
   * What the component arrives holding when it is inserted from the builder's palette: encoded
   * `properties` and `slots` of child nodes, the UI builder's `insertContent` shape
   * (`UI_BUILDER_CATALOG_CONTRACT.md` § Catalog-published editor policy in compose-ui-builder). Raw
   * JSON for the reason the capability blocks are; not `@BuilderComponent(starter = …)`, which is
   * call-site argument text for the export.
   */
  public val insertContent: JsonElement? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var record: String? = null
    public var group: String? = null
    public var displayName: String? = null
    public var canvas: String? = null
    public var canvasMapping: JsonElement? = null
    public var unrolled: UiBuilderUnrolledMock? = null
    public var nativeOnly: Boolean? = null
    public var traits: List<String>? = null
    public var excluded: String? = null
    public var propertyCapabilities: List<JsonElement>? = null
    public var slotCapabilities: List<JsonElement>? = null
    public var modifierCapabilities: List<String>? = null
    public var insertContent: JsonElement? = null

    public fun build(): UiBuilderAuthoredComponent =
      UiBuilderAuthoredComponent(
        record,
        group,
        displayName,
        canvas,
        canvasMapping,
        unrolled,
        nativeOnly,
        traits,
        excluded,
        propertyCapabilities,
        slotCapabilities,
        modifierCapabilities,
        insertContent,
      )
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.record = record
      it.group = group
      it.displayName = displayName
      it.canvas = canvas
      it.canvasMapping = canvasMapping
      it.unrolled = unrolled
      it.nativeOnly = nativeOnly
      it.traits = traits
      it.excluded = excluded
      it.propertyCapabilities = propertyCapabilities
      it.slotCapabilities = slotCapabilities
      it.modifierCapabilities = modifierCapabilities
      it.insertContent = insertContent
    }
}

/**
 * The layout a catalog asks the editing canvas to draw for a component while it is being edited.
 *
 * A scrollable container drawn as itself cannot show a child past the frame's edge — the ninth row
 * of a lazy column, the fifth tab of a scrollable row, the pane a phone frame hides — so a catalog
 * says how its children should be laid out while an author is inside it. The **constrained**
 * surfaces never see this: the preview pane, each device frame, the native lane and every export
 * draw the component itself.
 *
 * [layout] is the builder's vocabulary, not this file's, exactly as
 * [UiBuilderAuthoredComponent.canvas] is: the builder resolves the name against its own registry,
 * and a name it does not know is inert — the component draws as itself rather than as a broken
 * mock. The names in use are `stack` (a `Column`), `row` (a `Row`), `wrap` (a `FlowRow`) and
 * `panes` (every pane a pane scaffold declares).
 *
 * The wire carries it nested under the component's `wasm` block — `WasmCapabilityV1.unrolled` —
 * because that is the canvas-lane block there. Here it sits beside `canvas`, which is the
 * declaration it belongs with: it is the same declaration for a record component and for a builtin,
 * and those two do not share a `wasm` block.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderUnrolledMock
internal constructor(
  public val layout: String,
  /** The width `wrap` and `row` give one cell; absent leaves it to the layout. */
  public val cellWidthDp: JsonElement? = null,
  /** The gap between cells; absent leaves it to the layout. */
  public val spacingDp: JsonElement? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var layout: String) {
    public var cellWidthDp: JsonElement? = null
    public var spacingDp: JsonElement? = null

    public fun build(): UiBuilderUnrolledMock =
      UiBuilderUnrolledMock(layout, cellWidthDp, spacingDp)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(layout).also {
      it.cellWidthDp = cellWidthDp
      it.spacingDp = spacingDp
    }
}

/**
 * A component the policy file may declare because the record cannot carry it: it has no call site,
 * so there is nothing to discover and no sticker to annotate.
 *
 * A builtin must name a structural [role], which is what tells the template engine which template
 * writes it. A component that *has* a call site belongs in the record; declaring one here would be
 * the second inventory this contract exists to avoid.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderBuiltin
internal constructor(
  public val role: String,
  public val displayName: String? = null,
  public val group: String? = null,
  public val canvas: String? = null,
  /** The editing canvas's mock for this builtin — see [UiBuilderUnrolledMock]. */
  public val unrolled: UiBuilderUnrolledMock? = null,
  /**
   * What this builtin IS, for the slot-acceptance rules — a component whose slot accepts
   * `AnyContent` cannot admit one that claims no traits at all.
   *
   * The consumer already reads it; without it here there was no way to write it, so every builtin a
   * catalog could publish arrived on the shelf with an empty list. The same was true of
   * [modifierCapabilities].
   */
  public val traits: List<String> = emptyList(),
  public val slots: Map<String, JsonElement> = emptyMap(),
  public val properties: List<JsonElement> = emptyList(),
  /** The modifiers this builtin accepts, or null for the consumer's structural default. */
  public val modifierCapabilities: List<String>? = null,
  /**
   * What this builtin arrives holding when inserted — see
   * [UiBuilderAuthoredComponent.insertContent].
   */
  public val insertContent: JsonElement? = null,
  /** Canonical id of the wrapper call site this catalog ships in its component record. */
  public val implementation: String? = null,
  /**
   * What this builtin IS on the shelf — `Scaffold`, `Container` or `Leaf` — or null to let the
   * consumer derive it.
   *
   * Two different words are spelled `role` in this contract and they are not the same vocabulary.
   * [role] above is the STRUCTURAL one: which template writes this component. This is the shelf's,
   * which decides what the editor calls it and which slots will take it, and it is the one a
   * capability document publishes as `role`.
   *
   * There was no way to state it, so a consumer derived it from whether the builtin had slots at
   * all — and a design ROOT with slots arrives as an ordinary `Container` rather than a `Scaffold`.
   * `screen-root` is the only structural role that implies the answer; `list` and `container` say
   * how a thing is WRITTEN and not what shape it is. Null keeps the derivation, which is right for
   * everything the derivation gets right.
   */
  public val shelfRole: String? = null,
  /**
   * What the canvas lane says about this builtin, overriding what [canvas] implies.
   *
   * A consumer computes the block from the adapter id, which can only produce "supported" or
   * "unsupported" with a sentence about the adapter. Two things a catalog knows and that cannot
   * say: an adapter that is `planned` rather than absent, and WHY a component draws the way it
   * does. The packaged vocabulary this contract is measured against carries both — `layout/box` is
   * `planned`, and `asset/image`'s note is four sentences about where the bytes come from — and a
   * catalog republishing those declarations had to drop them.
   *
   * It also matters per platform: a Wear palette rewrites every borrowed foundation component's
   * note to say that `androidx.compose.foundation` publishes one of these and not two, which says
   * the opposite of what a borrowed Material component's note said.
   */
  public val wasm: UiBuilderBuiltinWasm? = null,
  /**
   * The call this builtin exports as, when the catalog states it outright.
   *
   * [implementation] answers the same question by pointing at a record entry, and is the better
   * answer when there IS one: the record is discovered, so it cannot drift from the source. This is
   * for the component that has no call site anywhere in this catalog and still exports as a known
   * callable — `layout/box` writes `Box` and imports `androidx.compose.foundation.layout.Box`, and
   * nothing in a foundation catalog's record can say so, because discovery does not scope to
   * foundation symbols. Without it such a builtin published with no code capability at all.
   */
  public val code: UiBuilderBuiltinCode? = null,
  /**
   * Whether a structured-SVG export can draw this builtin, and what happens when it cannot.
   *
   * Not derivable from anything else in the declaration: whether the recorder has a vector for a
   * radial gradient is a fact about the recorder, and whether falling back to a raster is
   * acceptable is editorial. The packaged vocabulary states it for all seventeen components and a
   * catalog could not, so every republished declaration claimed nothing — which a consumer reads as
   * unverified rather than as the `verified` most of them are.
   */
  public val svg: UiBuilderBuiltinSvg? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var role: String) {
    public var displayName: String? = null
    public var group: String? = null
    public var canvas: String? = null
    public var unrolled: UiBuilderUnrolledMock? = null
    public var traits: List<String> = emptyList()
    public var slots: Map<String, JsonElement> = emptyMap()
    public var properties: List<JsonElement> = emptyList()
    public var modifierCapabilities: List<String>? = null
    public var insertContent: JsonElement? = null
    public var implementation: String? = null
    public var shelfRole: String? = null
    public var wasm: UiBuilderBuiltinWasm? = null
    public var code: UiBuilderBuiltinCode? = null
    public var svg: UiBuilderBuiltinSvg? = null

    public fun build(): UiBuilderBuiltin =
      UiBuilderBuiltin(
        role,
        displayName,
        group,
        canvas,
        unrolled,
        traits,
        slots,
        properties,
        modifierCapabilities,
        insertContent,
        implementation,
        shelfRole,
        wasm,
        code,
        svg,
      )
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(role).also {
      it.displayName = displayName
      it.group = group
      it.canvas = canvas
      it.unrolled = unrolled
      it.traits = traits
      it.slots = slots
      it.properties = properties
      it.modifierCapabilities = modifierCapabilities
      it.insertContent = insertContent
      it.implementation = implementation
      it.shelfRole = shelfRole
      it.wasm = wasm
      it.code = code
      it.svg = svg
    }
}

/**
 * The canvas-lane block, mirroring the consumer's `WasmCapabilityV1`.
 *
 * Every field nullable so "not stated" and "stated" stay different questions: a catalog overriding
 * only [notes] keeps the platform support and adapter status the consumer derived from `canvas`.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderBuiltinWasm
internal constructor(
  public val platformSupported: JsonElement? = null,
  /** `supported`, `planned` or `unsupported`. */
  public val adapterStatus: String? = null,
  public val notes: String? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var platformSupported: JsonElement? = null
    public var adapterStatus: String? = null
    public var notes: String? = null

    public fun build(): UiBuilderBuiltinWasm =
      UiBuilderBuiltinWasm(platformSupported, adapterStatus, notes)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.platformSupported = platformSupported
      it.adapterStatus = adapterStatus
      it.notes = notes
    }
}

/**
 * The export call for a builtin with no record entry. Mirrors the consumer's `CodeCapabilityV1`.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderBuiltinCode
internal constructor(
  public val symbol: String,
  public val imports: List<String> = emptyList(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var symbol: String) {
    public var imports: List<String> = emptyList()

    public fun build(): UiBuilderBuiltinCode = UiBuilderBuiltinCode(symbol, imports)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(symbol).also { it.imports = imports }
}

/** What a structured-SVG export makes of this builtin. Mirrors the consumer's `SvgCapabilityV1`. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderBuiltinSvg
internal constructor(
  public val status: String,
  public val fallback: String,
  public val blocksExport: Boolean = false,
  public val notes: String? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var status: String,
    public var fallback: String,
  ) {
    public var blocksExport: Boolean = false
    public var notes: String? = null

    public fun build(): UiBuilderBuiltinSvg =
      UiBuilderBuiltinSvg(status, fallback, blocksExport, notes)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(status, fallback).also {
      it.blocksExport = blocksExport
      it.notes = notes
    }
}

/**
 * How the builder shelves this catalog. Only the group ORDER is authored: the shelves themselves
 * come from `@CatalogGroup`, and no annotation on one component can state a total order over all of
 * them.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderMenu
internal constructor(public val groupOrder: List<String> = emptyList()) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var groupOrder: List<String> = emptyList()

    public fun build(): UiBuilderMenu = UiBuilderMenu(groupOrder)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder().also { it.groupOrder = groupOrder }
}

/** How this catalog's designs are written as source. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderCode
internal constructor(
  /**
   * `record` (the default): every node is a call site printed from the component record — all a
   * catalog of leaf components has to say. `templates`: the design has structure no record can
   * print, and the catalog supplies it in [templates].
   */
  public val strategy: String = "record",
  /** What the export produces, for the label a person reads (`kotlin`, `kotlin-remote-compose`). */
  public val language: String? = null,
  /** Imports every generated file needs. Per-component imports come from the record. */
  public val imports: List<String> = emptyList(),
  /** Structural Kotlin with named holes, keyed by role. A hole-filler, not a language. */
  public val templates: Map<String, String> = emptyMap(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var strategy: String = "record"
    public var language: String? = null
    public var imports: List<String> = emptyList()
    public var templates: Map<String, String> = emptyMap()

    public fun build(): UiBuilderCode = UiBuilderCode(strategy, language, imports, templates)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.strategy = strategy
      it.language = language
      it.imports = imports
      it.templates = templates
    }
}

/**
 * One `templates` entry of a policy: a template design, and what the New design chooser says about
 * it. Written as a bare branch-relative path when there is nothing to say — the form every policy
 * used before the chooser's copy had a field — or as an object.
 *
 * @property id the id a design URL names the template by; the file name without `.json` when
 *   absent, which is what a bare path has always meant.
 */
@Serializable(with = UiBuilderTemplateEntrySerializer::class)
@ConsistentCopyVisibility
public data class UiBuilderTemplateEntry
internal constructor(
  public val path: String,
  public val id: String? = null,
  public val label: String? = null,
  public val supportingText: String? = null,
  public val group: String? = null,
  public val default: Boolean = false,
  public val order: Int? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var path: String) {
    public var id: String? = null
    public var label: String? = null
    public var supportingText: String? = null
    public var group: String? = null
    public var default: Boolean = false
    public var order: Int? = null

    public fun build(): UiBuilderTemplateEntry =
      UiBuilderTemplateEntry(path, id, label, supportingText, group, default, order)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(path).also {
      it.id = id
      it.label = label
      it.supportingText = supportingText
      it.group = group
      it.default = default
      it.order = order
    }

  /** The id a design URL names this template by. */
  public val resolvedId: String
    get() = id ?: path.substringAfterLast('/').removeSuffix(".json")

  /** Whether the entry says anything beyond its path, so a bare path stays a bare path. */
  public val describesItself: Boolean
    get() =
      id != null ||
        label != null ||
        supportingText != null ||
        group != null ||
        default ||
        order != null
}

/** The object form of [UiBuilderTemplateEntry], which the bare-path form is a shorthand for. */
@Serializable
private data class UiBuilderTemplateObject(
  val path: String,
  val id: String? = null,
  val label: String? = null,
  val supportingText: String? = null,
  val group: String? = null,
  val default: Boolean = false,
  val order: Int? = null,
)

internal object UiBuilderTemplateEntrySerializer : KSerializer<UiBuilderTemplateEntry> {
  override val descriptor: SerialDescriptor = UiBuilderTemplateObject.serializer().descriptor

  override fun deserialize(decoder: Decoder): UiBuilderTemplateEntry {
    val json = (decoder as? JsonDecoder) ?: error("a templates entry is read from JSON")
    val element = json.decodeJsonElement()
    if (element is JsonPrimitive && element.isString) return UiBuilderTemplateEntry(element.content)
    val o = json.json.decodeFromJsonElement<UiBuilderTemplateObject>(element)
    return UiBuilderTemplateEntry(
      o.path,
      o.id,
      o.label,
      o.supportingText,
      o.group,
      o.default,
      o.order,
    )
  }

  override fun serialize(encoder: Encoder, value: UiBuilderTemplateEntry) {
    val json = (encoder as? JsonEncoder) ?: error("a templates entry is written as JSON")
    if (!value.describesItself) {
      json.encodeJsonElement(JsonPrimitive(value.path))
      return
    }
    json.encodeJsonElement(
      json.json.encodeToJsonElement(
        UiBuilderTemplateObject(
          value.path,
          value.id,
          value.label,
          value.supportingText,
          value.group,
          value.default,
          value.order,
        )
      )
    )
  }
}

/** How a catalog appears in the New design chooser as a whole: its chip's label and position. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderNewDesign
internal constructor(
  public val label: String? = null,
  public val order: Int? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var label: String? = null
    public var order: Int? = null

    public fun build(): UiBuilderNewDesign = UiBuilderNewDesign(label, order)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.label = label
      it.order = order
    }
}

/**
 * What a published catalog tells the New design chooser: its own chip, and the copy for each
 * template it offers. Published only when the policy authored any of it, so a catalog that names
 * bare paths and no chip publishes exactly what it did before.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderNewDesignSemantics
internal constructor(
  public val label: String? = null,
  public val order: Int? = null,
  public val templates: List<UiBuilderNewDesignTemplateSemantics> = emptyList(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var label: String? = null
    public var order: Int? = null
    public var templates: List<UiBuilderNewDesignTemplateSemantics> = emptyList()

    public fun build(): UiBuilderNewDesignSemantics =
      UiBuilderNewDesignSemantics(label, order, templates)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.label = label
      it.order = order
      it.templates = templates
    }
}

/** One template's chooser card, by the id a design URL names it by. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderNewDesignTemplateSemantics
internal constructor(
  public val id: String,
  public val path: String,
  public val label: String? = null,
  public val supportingText: String? = null,
  public val group: String? = null,
  public val default: Boolean = false,
  public val order: Int? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var id: String,
    public var path: String,
  ) {
    public var label: String? = null
    public var supportingText: String? = null
    public var group: String? = null
    public var default: Boolean = false
    public var order: Int? = null

    public fun build(): UiBuilderNewDesignTemplateSemantics =
      UiBuilderNewDesignTemplateSemantics(id, path, label, supportingText, group, default, order)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id, path).also {
      it.label = label
      it.supportingText = supportingText
      it.group = group
      it.default = default
      it.order = order
    }
}
