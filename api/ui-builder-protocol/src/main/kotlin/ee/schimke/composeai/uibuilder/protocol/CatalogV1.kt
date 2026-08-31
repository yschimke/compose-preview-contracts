@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The schema version encoded by every top-level v1 message and persisted state. */
public const val UI_BUILDER_SCHEMA_VERSION_V1: Int = 1

/** Exact catalog build a design was authored against. Digests prevent a mutable-version alias. */
@Serializable
public data class CatalogReferenceV1(
  public val catalogId: String,
  public val catalogVersion: String,
  public val catalogDigest: String,
)

/** Immutable catalog metadata and the component capabilities understood by an editor. */
@Serializable
public data class CatalogCapabilityV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val reference: CatalogReferenceV1,
  public val displayName: String,
  public val components: List<ComponentCapabilityV1>,
  public val exportCapabilities: ExportCapabilitiesV1 = ExportCapabilitiesV1(),
)

/** What structural role a catalog component may play in a design tree. */
@Serializable
public enum class ComponentKindV1 {
  @SerialName("scaffold") SCAFFOLD,
  @SerialName("container") CONTAINER,
  @SerialName("composable") COMPOSABLE,
}

/** A component's editable surface. Validation policy remains in the owning implementation. */
@Serializable
public data class ComponentCapabilityV1(
  public val componentKey: String,
  public val displayName: String,
  public val kind: ComponentKindV1,
  public val slots: List<SlotCapabilityV1> = emptyList(),
  public val properties: List<PropertyCapabilityV1> = emptyList(),
  public val tags: List<String> = emptyList(),
  public val documentationUrl: String? = null,
)

/** Named child location exposed by a container or scaffold component. */
@Serializable
public data class SlotCapabilityV1(
  public val slotName: String,
  public val displayName: String,
  public val cardinality: SlotCardinalityV1,
  public val acceptedKinds: List<ComponentKindV1> = emptyList(),
  public val acceptedComponentKeys: List<String> = emptyList(),
)

@Serializable
public enum class SlotCardinalityV1 {
  @SerialName("zeroOrOne") ZERO_OR_ONE,
  @SerialName("exactlyOne") EXACTLY_ONE,
  @SerialName("zeroOrMore") ZERO_OR_MORE,
  @SerialName("oneOrMore") ONE_OR_MORE,
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
}

/** Declarative metadata for one editable Compose argument or design property. */
@Serializable
public data class PropertyCapabilityV1(
  public val propertyKey: String,
  public val displayName: String,
  public val valueKind: PropertyValueKindV1,
  public val required: Boolean = false,
  public val defaultValue: UiValueV1? = null,
  public val enumValues: List<String> = emptyList(),
  public val minimum: Double? = null,
  public val maximum: Double? = null,
)

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
@SerialName("boolean")
public data class BooleanValueV1(public val value: Boolean) : UiValueV1

@Serializable
@SerialName("integer")
public data class IntegerValueV1(public val value: Long) : UiValueV1

@Serializable
@SerialName("decimal")
public data class DecimalValueV1(public val value: Double) : UiValueV1

/** ARGB color encoded as an eight-digit hexadecimal string, for example `ff6750a4`. */
@Serializable
@SerialName("color")
public data class ColorValueV1(public val argbHex: String) : UiValueV1

@Serializable
@SerialName("dimension")
public data class DimensionValueV1(public val value: Double, public val unit: DimensionUnitV1) :
  UiValueV1

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
