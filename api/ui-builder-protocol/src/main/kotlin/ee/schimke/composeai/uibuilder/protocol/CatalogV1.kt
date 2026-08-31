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
public data class CatalogCapabilityV1(
  public val schema: String,
  public val benchmark: CatalogBenchmarkV1,
  public val statusSemantics: JsonObject = JsonObject(emptyMap()),
  public val components: List<ComponentCapabilityV1>,
  public val exportCapabilities: ExportCapabilitiesV1 = ExportCapabilitiesV1(),
)

/** Source and runtime identity carried by the current catalog capability document. */
@Serializable
public data class CatalogBenchmarkV1(
  public val id: String,
  public val sourceRevision: String,
  public val catalogSystemId: String,
  public val catalogRevision: String,
  public val nativeRuntimeId: String,
)

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
public data class ComponentCapabilityV1(
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
)

/** Named child location exposed by a container or scaffold component. */
@Serializable
public data class SlotCapabilityV1(
  public val name: String,
  public val cardinality: SlotCardinalityV1,
  public val ordered: Boolean,
  public val acceptedRoles: List<String> = emptyList(),
  public val acceptedTraits: List<String> = emptyList(),
)

@Serializable
public data class SlotCardinalityV1(public val min: Int = 0, public val max: Int? = null)

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
public data class PropertyCapabilityV1(
  public val name: String,
  /** String or array JSON Schema `type`, retained without normalizing its authored spelling. */
  public val jsonType: JsonElement,
  public val required: Boolean = false,
  public val allowedValues: List<JsonElement> = emptyList(),
  public val notes: String? = null,
)

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
public data class WasmCapabilityV1(
  /** Boolean today, with the current catalog's `"unverified"` spelling retained losslessly. */
  public val platformSupported: JsonElement,
  public val adapterStatus: WasmAdapterStatusV1,
  public val notes: String? = null,
)

@Serializable
public enum class WasmAdapterStatusV1 {
  @SerialName("supported") SUPPORTED,
  @SerialName("planned") PLANNED,
  @SerialName("unsupported") UNSUPPORTED,
}

@Serializable
public data class CodeCapabilityV1(
  public val symbol: String,
  public val imports: List<String> = emptyList(),
)

@Serializable
public data class SvgCapabilityV1(
  public val status: SvgCapabilityStatusV1,
  public val fallback: SvgFallbackV1,
  public val blocksExport: Boolean,
  public val notes: String? = null,
)

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
public data class ExportCapabilitiesV1(
  public val composeCode: Boolean = false,
  public val svg: Boolean = false,
  public val png: Boolean = false,
)

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
