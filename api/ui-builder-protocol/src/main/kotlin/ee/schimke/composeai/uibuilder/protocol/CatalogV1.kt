@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The schema version encoded by every top-level v1 message and persisted state. */
public const val UI_BUILDER_SCHEMA_VERSION_V1: Int = 1

/** Exact catalog build a design was authored against. Digests prevent a mutable-version alias. */
@Serializable
public data class CatalogReferenceV1(
  public val systemId: String,
  public val catalogRevision: String,
  public val capabilityDigest: String,
  /** Version-addressed executable adapter required to render this catalog pin. */
  public val nativeRuntimeId: String,
)

/** Immutable catalog metadata and the component capabilities understood by an editor. */
@Serializable
@ConsistentCopyVisibility
public data class CatalogCapabilityV1
internal constructor(
  public val schema: String,
  public val benchmark: CatalogBenchmarkV1,
  public val statusSemantics: JsonObject = JsonObject(emptyMap()),
  public val components: List<ComponentCapabilityV1>,
  public val exportCapabilities: ExportCapabilitiesV1 = ExportCapabilitiesV1(),
  /** How the browser's read-only Preview renders this catalog, when it differs from the canvas. */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val browserPreview: BrowserPreviewCapabilityV1? = null,
  /** Declarative Compose-source adapter the catalog publishes, when it has one. */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val composeSourceExport: ComposeSourceExportCapabilityV1? = null,
) {
  /**
   * Builds a [CatalogCapabilityV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(
    schema: String,
    benchmark: CatalogBenchmarkV1,
    components: List<ComponentCapabilityV1>,
  ) {
    public var schema: String = schema
    public var benchmark: CatalogBenchmarkV1 = benchmark
    public var statusSemantics: JsonObject = JsonObject(emptyMap())
    public var components: List<ComponentCapabilityV1> = components
    public var exportCapabilities: ExportCapabilitiesV1 = ExportCapabilitiesV1()
    public var browserPreview: BrowserPreviewCapabilityV1? = null
    public var composeSourceExport: ComposeSourceExportCapabilityV1? = null

    public fun build(): CatalogCapabilityV1 =
      CatalogCapabilityV1(
        schema,
        benchmark,
        statusSemantics,
        components,
        exportCapabilities,
        browserPreview,
        composeSourceExport,
      )
  }

  /** This [CatalogCapabilityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(schema, benchmark, components).also {
      it.statusSemantics = statusSemantics
      it.exportCapabilities = exportCapabilities
      it.browserPreview = browserPreview
      it.composeSourceExport = composeSourceExport
    }
}

/**
 * A catalog-owned declaration of the adapter that emits a design as Compose source.
 *
 * The adapter name is an open, versioned capability id: a host resolves an id and version it knows,
 * and refuses source export with a diagnostic rather than guessing when it does not. The
 * declaration deliberately carries no executable Kotlin; catalogs choose a safe, shipped adapter
 * and the adapter interprets their published component/slot/property capabilities.
 */
@Serializable
@ConsistentCopyVisibility
public data class ComposeSourceExportCapabilityV1
internal constructor(
  public val adapter: String,
  public val version: Int,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(adapter: String, version: Int) {
    public var adapter: String = adapter
    public var version: Int = version

    public fun build(): ComposeSourceExportCapabilityV1 =
      ComposeSourceExportCapabilityV1(adapter, version)
  }

  /** This capability as a mutable builder, for deriving a modified catalog declaration. */
  public fun newBuilder(): Builder = Builder(adapter, version)
}

/**
 * The renderer a catalog asks the browser's read-only Preview to use.
 *
 * The editing canvas remains the component tree declared by [ComponentCapabilityV1.wasm]. A catalog
 * whose executable result is a compiled document can instead ask Preview to export the current
 * design and play that artifact. Keeping this on the catalog capability makes the choice a property
 * of the catalog rather than a component-id or platform-name convention in an editor.
 *
 * [renderer] is an open adapter id for the same reason [WasmCapabilityV1.canvas] is: an editor
 * resolves the names it knows and falls back to its canvas for an unknown one. [format] names the
 * export artifact handed to that adapter; a host must also declare support in
 * [CatalogCapabilityV1.exportCapabilities].
 */
@Serializable
@ConsistentCopyVisibility
public data class BrowserPreviewCapabilityV1
internal constructor(
  public val renderer: String = CANVAS_RENDERER,
  public val format: ExportFormatV1? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(renderer: String = CANVAS_RENDERER) {
    public var renderer: String = renderer
    public var format: ExportFormatV1? = null

    public fun build(): BrowserPreviewCapabilityV1 =
      BrowserPreviewCapabilityV1(renderer = renderer, format = format)
  }

  /** This capability as a mutable builder, for deriving a modified catalog declaration. */
  public fun newBuilder(): Builder = Builder(renderer).also { it.format = format }

  public companion object {
    /** Draw Preview with the same constrained component renderer as the editing canvas. */
    public const val CANVAS_RENDERER: String = "canvas"

    /** Play a compiled Remote Compose document exported from the current design. */
    public const val REMOTE_COMPOSE_DOCUMENT_RENDERER: String = "remote-compose-document"
  }
}

/** Source and runtime identity carried by the current catalog capability document. */
@Serializable
@ConsistentCopyVisibility
public data class CatalogBenchmarkV1
internal constructor(
  public val id: String,
  public val sourceRevision: String,
  public val catalogSystemId: String,
  public val catalogRevision: String,
  public val nativeRuntimeId: String,
) {
  /**
   * Builds a [CatalogBenchmarkV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(
    id: String,
    sourceRevision: String,
    catalogSystemId: String,
    catalogRevision: String,
    nativeRuntimeId: String,
  ) {
    public var id: String = id
    public var sourceRevision: String = sourceRevision
    public var catalogSystemId: String = catalogSystemId
    public var catalogRevision: String = catalogRevision
    public var nativeRuntimeId: String = nativeRuntimeId

    public fun build(): CatalogBenchmarkV1 =
      CatalogBenchmarkV1(id, sourceRevision, catalogSystemId, catalogRevision, nativeRuntimeId)
  }

  /** This [CatalogBenchmarkV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id, sourceRevision, catalogSystemId, catalogRevision, nativeRuntimeId)
}

/** What structural role a catalog component may play in a design tree. */
@Serializable
public enum class ComponentKindV1 {
  @SerialName("screen") SCREEN,
  @SerialName("scaffold") SCAFFOLD,
  @SerialName("container") CONTAINER,
  @SerialName("leaf") LEAF,
  @SerialName("composable") COMPOSABLE,
}

/** A component's editable surface. Validation policy remains in the owning implementation. */
@Serializable
@ConsistentCopyVisibility
public data class ComponentCapabilityV1
internal constructor(
  public val componentId: String,
  public val displayName: String,
  public val role: String,
  public val traits: List<String> = emptyList(),
  public val slots: List<SlotCapabilityV1> = emptyList(),
  public val properties: List<PropertyCapabilityV1> = emptyList(),
  public val modifierCapabilities: List<String> = emptyList(),
  public val wasm: WasmCapabilityV1,
  public val code: CodeCapabilityV1? = null,
  public val svg: SvgCapabilityV1? = null,
) {
  /**
   * Builds a [ComponentCapabilityV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(
    componentId: String,
    displayName: String,
    role: String,
    wasm: WasmCapabilityV1,
  ) {
    public var componentId: String = componentId
    public var displayName: String = displayName
    public var role: String = role
    public var traits: List<String> = emptyList()
    public var slots: List<SlotCapabilityV1> = emptyList()
    public var properties: List<PropertyCapabilityV1> = emptyList()
    public var modifierCapabilities: List<String> = emptyList()
    public var wasm: WasmCapabilityV1 = wasm
    public var code: CodeCapabilityV1? = null
    public var svg: SvgCapabilityV1? = null

    public fun build(): ComponentCapabilityV1 =
      ComponentCapabilityV1(
        componentId,
        displayName,
        role,
        traits,
        slots,
        properties,
        modifierCapabilities,
        wasm,
        code,
        svg,
      )
  }

  /** This [ComponentCapabilityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(componentId, displayName, role, wasm).also {
      it.traits = traits
      it.slots = slots
      it.properties = properties
      it.modifierCapabilities = modifierCapabilities
      it.code = code
      it.svg = svg
    }
}

/** Named child location exposed by a container or scaffold component. */
@Serializable
@ConsistentCopyVisibility
public data class SlotCapabilityV1
internal constructor(
  public val name: String,
  public val cardinality: SlotCardinalityV1,
  public val ordered: Boolean,
  public val acceptedRoles: List<String> = emptyList(),
  public val acceptedTraits: List<String> = emptyList(),
) {
  /**
   * Builds a [SlotCapabilityV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(name: String, cardinality: SlotCardinalityV1, ordered: Boolean) {
    public var name: String = name
    public var cardinality: SlotCardinalityV1 = cardinality
    public var ordered: Boolean = ordered
    public var acceptedRoles: List<String> = emptyList()
    public var acceptedTraits: List<String> = emptyList()

    public fun build(): SlotCapabilityV1 =
      SlotCapabilityV1(name, cardinality, ordered, acceptedRoles, acceptedTraits)
  }

  /** This [SlotCapabilityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(name, cardinality, ordered).also {
      it.acceptedRoles = acceptedRoles
      it.acceptedTraits = acceptedTraits
    }
}

@Serializable
@ConsistentCopyVisibility
public data class SlotCardinalityV1
internal constructor(public val min: Int = 0, public val max: Int? = null) {
  /**
   * Builds a [SlotCardinalityV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder {
    public var min: Int = 0
    public var max: Int? = null

    public fun build(): SlotCardinalityV1 = SlotCardinalityV1(min, max)
  }

  /** This [SlotCardinalityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.min = min
      it.max = max
    }
}

/** Stable JSON value categories a property editor can offer without loading Compose code. */
@Serializable
public enum class PropertyValueKindV1 {
  @SerialName("string") STRING,
  @SerialName("boolean") BOOLEAN,
  @SerialName("integer") INTEGER,
  @SerialName("decimal") DECIMAL,
  @SerialName("color") COLOR,
  @SerialName("dimension") DIMENSION,
  @SerialName("enum") ENUM,
  @SerialName("resource") RESOURCE,
  @SerialName("list") LIST,
  @SerialName("object") OBJECT,
  @SerialName("state") STATE,
  @SerialName("token") TOKEN,
  @SerialName("padding") PADDING,
}

/** Declarative metadata for one editable Compose argument or design property. */
@Serializable
@ConsistentCopyVisibility
public data class PropertyCapabilityV1
internal constructor(
  public val name: String,
  /** String or array JSON Schema `type`, retained without normalizing its authored spelling. */
  public val jsonType: JsonElement,
  public val required: Boolean = false,
  public val allowedValues: List<JsonElement> = emptyList(),
  public val notes: String? = null,
) {
  /**
   * Builds a [PropertyCapabilityV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(name: String, jsonType: JsonElement) {
    public var name: String = name
    public var jsonType: JsonElement = jsonType
    public var required: Boolean = false
    public var allowedValues: List<JsonElement> = emptyList()
    public var notes: String? = null

    public fun build(): PropertyCapabilityV1 =
      PropertyCapabilityV1(name, jsonType, required, allowedValues, notes)
  }

  /** This [PropertyCapabilityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(name, jsonType).also {
      it.required = required
      it.allowedValues = allowedValues
      it.notes = notes
    }
}

@Serializable
public enum class JsonValueTypeV1 {
  @SerialName("string") STRING,
  @SerialName("boolean") BOOLEAN,
  @SerialName("number") NUMBER,
  @SerialName("integer") INTEGER,
  @SerialName("object") OBJECT,
  @SerialName("array") ARRAY,
  @SerialName("null") NULL,
}

@Serializable
public data class EventCapabilityV1(
  public val eventName: String,
  public val payloadValueKind: PropertyValueKindV1? = null,
  public val allowedActions: List<String> = emptyList(),
)

@Serializable
@ConsistentCopyVisibility
public data class WasmCapabilityV1
internal constructor(
  /** Boolean today, with the current catalog's `"unverified"` spelling retained losslessly. */
  public val platformSupported: JsonElement,
  public val adapterStatus: WasmAdapterStatusV1,
  public val notes: String? = null,
  /**
   * The editing canvas's mock for this component, when its catalog declares one.
   *
   * A scrollable container drawn as itself cannot show a child past the frame's edge — the ninth
   * row of a lazy column, the fifth tab of a scrollable row, the pane a phone frame hides — so a
   * catalog may say how its children should be laid out while an author is inside it. The
   * **constrained** surfaces never see this: the preview pane, each device frame, the native lane
   * and every export draw the component itself, which is the split the editor's canvas has always
   * drawn between what is being edited and what a device shows.
   *
   * Null — the default, and every component today — keeps the component's own layout while editing.
   * An optional declaration rather than a required one is what lets a catalog adopt this one
   * component at a time.
   */
  public val unrolled: UnrolledMockV1? = null,
  /**
   * The canvas drawing a catalog names for this component, or null to key on the component id.
   *
   * The builder ships a registry of drawings — a Material 3 button, a Wear card, a round screen
   * frame — and a catalog that names one gets it, whatever its component id is called. Before this
   * field existed the id *was* the lookup: a Wear catalog's screen root was drawn as a round screen
   * because the builder recognised `wear-m3/screen-scaffold`, which meant a catalog published under
   * any other id could not ask for the same drawing, and every new one was a release of the
   * builder. `UI_BUILDER_CATALOG_CONTRACT.md` item 17 is that coupling's removal.
   *
   * The name is the **builder's** vocabulary rather than this contract's, exactly as
   * [UnrolledMockV1.layout] is: the builder resolves it against its own registry, and a name that
   * build does not know draws the component as itself. A closed enum here would make a new drawing
   * a release of this artifact before any catalog could name it.
   *
   * Null — the default, and every component today — keeps the id as the lookup, so a catalog adopts
   * this one component at a time.
   */
  public val canvas: String? = null,
  /** Property/slot normalization applied only while drawing [canvas]. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val canvasMapping: CanvasAdapterMappingV1? = null,
) {
  /**
   * Builds a [WasmCapabilityV1].
   *
   * The construction API: [WasmCapabilityV1]'s constructor is `internal`, and
   * `@ConsistentCopyVisibility` makes the generated `copy` internal with it, so neither is public
   * ABI. That matters because this type crosses a repository boundary as a compiled artifact:
   * adding a property to a data class REMOVES the old `<init>` and `copy$default` signatures, and a
   * consumer compiled against the previous release dies at its own call site. That is not
   * hypothetical here — adding [unrolled] did exactly that to compose-ui-builder's runtime, as
   * `NoSuchMethodError: WasmCapabilityV1.copy$default(...)` out of
   * `ProductionUiBuilderRuntime.remoteM3Catalog`. Neither signature is reachable now, so neither
   * can break.
   *
   * The rule that keeps that true: **a new property is always optional**, so it only ever adds a
   * setter here and never a parameter to this constructor.
   */
  public class Builder(platformSupported: JsonElement, adapterStatus: WasmAdapterStatusV1) {
    public var platformSupported: JsonElement = platformSupported
    public var adapterStatus: WasmAdapterStatusV1 = adapterStatus
    public var notes: String? = null
    public var unrolled: UnrolledMockV1? = null
    public var canvas: String? = null
    public var canvasMapping: CanvasAdapterMappingV1? = null

    public fun build(): WasmCapabilityV1 =
      WasmCapabilityV1(
        platformSupported,
        adapterStatus,
        notes,
        unrolled,
        canvas,
        canvasMapping,
      )
  }

  /** This [WasmCapabilityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(platformSupported, adapterStatus).also {
      it.notes = notes
      it.unrolled = unrolled
      it.canvas = canvas
      it.canvasMapping = canvasMapping
    }
}

/**
 * Adapts a catalog component's authored vocabulary to the canvas drawing it names.
 *
 * This changes no document and reaches no export. It is a read-only projection used only by the
 * editing canvas: [properties] maps a target property name to its source name, [slots] does the
 * same for slots, and [defaults] supplies target values absent from the source. A Remote component
 * can therefore name its real Wear counterpart without either catalog adopting the other's API
 * spelling or an editor recognising the Remote component id.
 */
@Serializable
@ConsistentCopyVisibility
public data class CanvasAdapterMappingV1
internal constructor(
  public val properties: Map<String, String> = emptyMap(),
  public val slots: Map<String, String> = emptyMap(),
  public val defaults: JsonObject = JsonObject(emptyMap()),
) {
  public class Builder {
    public var properties: Map<String, String> = emptyMap()
    public var slots: Map<String, String> = emptyMap()
    public var defaults: JsonObject = JsonObject(emptyMap())

    public fun build(): CanvasAdapterMappingV1 =
      CanvasAdapterMappingV1(properties = properties, slots = slots, defaults = defaults)
  }

  public fun newBuilder(): Builder =
    Builder().also {
      it.properties = properties
      it.slots = slots
      it.defaults = defaults
    }
}

/**
 * The layout a catalog asks the editing canvas to draw for a component while it is being edited.
 *
 * [layout] is the **builder's** vocabulary, not this contract's, exactly as a catalog's `canvas`
 * adapter name is: the builder resolves the name against its own registry, and a name that build
 * does not know is inert — the component draws as itself rather than as a broken mock. The names in
 * use are `stack` (a `Column`), `row` (a `Row`), `wrap` (a `FlowRow`) and `panes` (every pane a
 * pane scaffold declares). A closed enum here would make a new mock layout a release of this
 * artifact before any catalog could name it, which is the cost `canvas` already avoids.
 */
@Serializable
@ConsistentCopyVisibility
public data class UnrolledMockV1
internal constructor(
  public val layout: String,
  /** The width `wrap` and `row` give one cell; absent leaves it to the layout. */
  public val cellWidthDp: JsonElement? = null,
  /** The gap between cells; absent leaves it to the layout. */
  public val spacingDp: JsonElement? = null,
) {
  /**
   * Builds a [UnrolledMockV1]; see [WasmCapabilityV1.Builder] for why the constructor is internal.
   */
  public class Builder(layout: String) {
    public var layout: String = layout
    public var cellWidthDp: JsonElement? = null
    public var spacingDp: JsonElement? = null

    public fun build(): UnrolledMockV1 = UnrolledMockV1(layout, cellWidthDp, spacingDp)
  }

  /** This [UnrolledMockV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(layout).also {
      it.cellWidthDp = cellWidthDp
      it.spacingDp = spacingDp
    }
}

@Serializable
public enum class WasmAdapterStatusV1 {
  @SerialName("supported") SUPPORTED,
  @SerialName("planned") PLANNED,
  @SerialName("unsupported") UNSUPPORTED,
}

@Serializable
@ConsistentCopyVisibility
public data class CodeCapabilityV1
internal constructor(
  public val symbol: String,
  public val imports: List<String> = emptyList(),
) {
  /**
   * Builds a [CodeCapabilityV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder(symbol: String) {
    public var symbol: String = symbol
    public var imports: List<String> = emptyList()

    public fun build(): CodeCapabilityV1 = CodeCapabilityV1(symbol, imports)
  }

  /** This [CodeCapabilityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(symbol).also { it.imports = imports }
}

@Serializable
@ConsistentCopyVisibility
public data class SvgCapabilityV1
internal constructor(
  public val status: SvgCapabilityStatusV1,
  public val fallback: SvgFallbackV1,
  public val blocksExport: Boolean,
  public val notes: String? = null,
) {
  /**
   * Builds a [SvgCapabilityV1]; see [WasmCapabilityV1.Builder] for why the constructor is internal.
   */
  public class Builder(
    status: SvgCapabilityStatusV1,
    fallback: SvgFallbackV1,
    blocksExport: Boolean,
  ) {
    public var status: SvgCapabilityStatusV1 = status
    public var fallback: SvgFallbackV1 = fallback
    public var blocksExport: Boolean = blocksExport
    public var notes: String? = null

    public fun build(): SvgCapabilityV1 = SvgCapabilityV1(status, fallback, blocksExport, notes)
  }

  /** This [SvgCapabilityV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(status, fallback, blocksExport).also { it.notes = notes }
}

@Serializable
public enum class SvgCapabilityStatusV1 {
  @SerialName("verified") VERIFIED,
  @SerialName("unverified") UNVERIFIED,
  @SerialName("raster-fallback-required") RASTER_FALLBACK_REQUIRED,
  @SerialName("unsupported") UNSUPPORTED,
}

@Serializable
public enum class SvgFallbackV1 {
  @SerialName("none") NONE,
  @SerialName("embedded-raster") EMBEDDED_RASTER,
}

/** Export formats supported for designs using this exact catalog build. */
@Serializable
@ConsistentCopyVisibility
public data class ExportCapabilitiesV1
internal constructor(
  public val composeCode: Boolean = false,
  public val svg: Boolean = false,
  public val png: Boolean = false,
  /**
   * Whether this catalog's designs export as a bundle — source plus the picture bytes as files,
   * rather than the pictures inlined into the source ([ExportFormatV1.BUNDLE]).
   *
   * Defaults to false so that a server which cannot write one says so by saying nothing, and an
   * older server's capability document decodes here unchanged.
   */
  public val bundle: Boolean = false,
  /** Authoring JSON export is configured; individual designs can still report lowering errors. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val remoteJson: Boolean = false,
  /** A compatible compiler is configured to assemble binary Remote Compose documents. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val remoteDocument: Boolean = false,
) {
  /**
   * Builds an [ExportCapabilitiesV1]; see [WasmCapabilityV1.Builder] for why the constructor is
   * internal.
   */
  public class Builder {
    public var composeCode: Boolean = false
    public var svg: Boolean = false
    public var png: Boolean = false
    public var bundle: Boolean = false
    public var remoteJson: Boolean = false
    public var remoteDocument: Boolean = false

    public fun build(): ExportCapabilitiesV1 =
      ExportCapabilitiesV1(composeCode, svg, png, bundle, remoteJson, remoteDocument)
  }

  /** This [ExportCapabilitiesV1] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.composeCode = composeCode
      it.svg = svg
      it.png = png
      it.bundle = bundle
      it.remoteJson = remoteJson
      it.remoteDocument = remoteDocument
    }
}

/** Closed, language-neutral value tree used by node properties and session metadata. */
@Serializable public sealed interface UiValueV1

@Serializable
@SerialName("string")
public data class StringValueV1(public val value: String) : UiValueV1

@Serializable
@SerialName("bool")
public data class BooleanValueV1(public val value: Boolean) : UiValueV1

@Serializable
@SerialName("int")
public data class IntegerValueV1(public val value: Long) : UiValueV1

@Serializable
@SerialName("float")
public data class DecimalValueV1(public val value: Double) : UiValueV1

/** Literal color spelling retained from the authored document, for example `#FF6750A4`. */
@Serializable
@SerialName("color")
public data class ColorValueV1(public val value: String) : UiValueV1

@Serializable
@SerialName("colorToken")
public data class ColorTokenValueV1(public val value: String) : UiValueV1

/**
 * A read of one key of the dictionary in scope — the one value whose meaning depends on where it
 * stands.
 *
 * Everywhere else a value *is* what it says. This one says a key, and what it resolves to is
 * supplied by whatever introduced the scope it stands in:
 *
 * - inside a [DesignComponentV1] body, the instance's own [DesignComponentInstanceV1.arguments] —
 *   so the key becomes a parameter of the generated function;
 * - inside the template of a container that iterates rows, the row: an [ObjectValueV1] out of the
 *   [ListValueV1] that container names — so the key becomes a property of the generated data class.
 *
 * One reader for both because both scopes are the same thing, a dictionary with open keys, and the
 * difference between a parameter list and a data class is a difference in the code a generator
 * writes rather than in what the document holds. A key with no scope to read, or no such key in the
 * scope there is, is a refusal: the name resolves somewhere or it does not resolve.
 */
@Serializable
@SerialName("binding")
public data class BindingValueV1(public val value: String) : UiValueV1

@Serializable
@SerialName("dimension")
public data class DimensionValueV1(
  public val value: JsonElement,
  public val unit: DimensionUnitV1,
) : UiValueV1

@Serializable
public enum class DimensionUnitV1 {
  @SerialName("dp") DP,
  @SerialName("sp") SP,
  @SerialName("px") PX,
  @SerialName("percent") PERCENT,
}

@Serializable
@SerialName("enum")
public data class EnumValueV1(public val value: String) : UiValueV1

@Serializable
@SerialName("typographyToken")
public data class TypographyTokenValueV1(public val value: String) : UiValueV1

@Serializable
@SerialName("shapeToken")
public data class ShapeTokenValueV1(public val value: String) : UiValueV1

@Serializable
@SerialName("assetKey")
public data class AssetKeyValueV1(public val value: String) : UiValueV1

@Serializable
@SerialName("state")
public data class StateValueV1(public val variable: String) : UiValueV1

@Serializable
@SerialName("stateEquals")
public data class StateEqualsValueV1(
  public val variable: String,
  public val value: JsonElement,
) : UiValueV1

/** Four authored inset values in start/top/end/bottom order. */
@Serializable
@SerialName("insets")
public data class InsetsValueV1(public val value: List<JsonElement>) : UiValueV1

@Serializable
@SerialName("padding")
public data class PaddingValueV1(
  @EncodeDefault public val startDp: JsonElement = JsonPrimitive(0),
  @EncodeDefault public val topDp: JsonElement = JsonPrimitive(0),
  @EncodeDefault public val endDp: JsonElement = JsonPrimitive(0),
  @EncodeDefault public val bottomDp: JsonElement = JsonPrimitive(0),
) : UiValueV1

@Serializable
@SerialName("adaptiveGrid")
public data class AdaptiveGridValueV1(public val minimumCellWidthDp: JsonElement) : UiValueV1

@Serializable
@SerialName("resource")
public data class ResourceValueV1(
  public val resourceKey: String,
  public val contentDigest: String? = null,
) : UiValueV1

@Serializable
@SerialName("list")
public data class ListValueV1(public val values: List<UiValueV1>) : UiValueV1

@Serializable
@SerialName("object")
public data class ObjectValueV1(public val fields: Map<String, UiValueV1>) : UiValueV1

@Serializable @SerialName("null") public data object NullValueV1 : UiValueV1
