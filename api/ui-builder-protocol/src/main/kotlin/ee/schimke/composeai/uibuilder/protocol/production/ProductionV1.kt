@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol.production

import ee.schimke.composeai.uibuilder.protocol.DesignDocumentV1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Explicit project-file opt-in; ordinary DesignDocumentV1 readers must refuse this wrapper. */
public const val PRODUCTION_UID_SCHEMA_V1: String = "compose-ui-builder-production/v1"

@Serializable
@ConsistentCopyVisibility
public data class ProductionUidFileV1
internal constructor(
  public val schema: String,
  public val imports: List<String> = emptyList(),
  public val catalogDigest: String? = null,
  public val models: List<ProductionModelV1> = emptyList(),
  public val entryPoint: ProductionEntryPointV1? = null,
  public val design: DesignDocumentV1? = null,
) {
  public fun newBuilder(): Builder =
    Builder(schema).also { builder ->
      builder.imports = imports
      builder.catalogDigest = catalogDigest
      builder.models = models
      builder.entryPoint = entryPoint
      builder.design = design
    }

  public class Builder(public var schema: String) {
    public var imports: List<String> = emptyList()
    public var catalogDigest: String? = null
    public var models: List<ProductionModelV1> = emptyList()
    public var entryPoint: ProductionEntryPointV1? = null
    public var design: DesignDocumentV1? = null

    public fun build(): ProductionUidFileV1 =
      ProductionUidFileV1(schema, imports, catalogDigest, models, entryPoint, design)
  }
}

@Serializable
@ConsistentCopyVisibility
public data class ProductionModelV1
internal constructor(
  public val id: String,
  public val kotlinType: String,
  public val ownership: ModelOwnershipV1,
  public val fields: List<ProductionFieldV1>,
) {
  public fun newBuilder(): Builder = Builder(id, kotlinType, ownership, fields)

  public class Builder(
    public var id: String,
    public var kotlinType: String,
    public var ownership: ModelOwnershipV1,
    public var fields: List<ProductionFieldV1>,
  ) {
    public fun build(): ProductionModelV1 = ProductionModelV1(id, kotlinType, ownership, fields)
  }
}

@Serializable
@ConsistentCopyVisibility
public data class ProductionFieldV1
internal constructor(
  public val name: String,
  public val type: ProductionTypeV1,
  public val property: String? = null,
) {
  public fun newBuilder(): Builder =
    Builder(name, type).also { builder -> builder.property = property }

  public class Builder(public var name: String, public var type: ProductionTypeV1) {
    public var property: String? = null

    public fun build(): ProductionFieldV1 = ProductionFieldV1(name, type, property)
  }
}

@Serializable
public enum class ModelOwnershipV1 {
  @SerialName("generated") GENERATED,
  @SerialName("external") EXTERNAL,
}

@Serializable
public enum class ScalarTypeV1 {
  @SerialName("string") STRING,
  @SerialName("boolean") BOOLEAN,
  @SerialName("int") INT,
  @SerialName("long") LONG,
  @SerialName("float") FLOAT,
  @SerialName("double") DOUBLE,
}

@Serializable
public enum class EntryPointKindV1 {
  @SerialName("screen") SCREEN,
  @SerialName("component") COMPONENT,
}

@Serializable
public enum class ProductionVisibilityV1 {
  @SerialName("public") PUBLIC,
  @SerialName("internal") INTERNAL,
}

/** Closed data type vocabulary. Paths carry field names, never executable expressions. */
@Serializable
@JsonClassDiscriminator("kind")
public sealed interface ProductionTypeV1 {
  public val nullable: Boolean

  @Serializable
  @SerialName("scalar")
  @ConsistentCopyVisibility
  public data class Scalar
  internal constructor(
    public val scalar: ScalarTypeV1,
    public override val nullable: Boolean = false,
  ) : ProductionTypeV1 {
    public fun newBuilder(): Builder =
      Builder(scalar).also { builder -> builder.nullable = nullable }

    public class Builder(public var scalar: ScalarTypeV1) {
      public var nullable: Boolean = false

      public fun build(): Scalar = Scalar(scalar, nullable)
    }
  }

  @Serializable
  @SerialName("model")
  @ConsistentCopyVisibility
  public data class Model
  internal constructor(
    public val modelId: String,
    public override val nullable: Boolean = false,
  ) : ProductionTypeV1 {
    public fun newBuilder(): Builder =
      Builder(modelId).also { builder -> builder.nullable = nullable }

    public class Builder(public var modelId: String) {
      public var nullable: Boolean = false

      public fun build(): Model = Model(modelId, nullable)
    }
  }

  @Serializable
  @SerialName("list")
  @ConsistentCopyVisibility
  public data class ListType
  internal constructor(
    public val element: ProductionTypeV1,
    public override val nullable: Boolean = false,
  ) : ProductionTypeV1 {
    public fun newBuilder(): Builder =
      Builder(element).also { builder -> builder.nullable = nullable }

    public class Builder(public var element: ProductionTypeV1) {
      public var nullable: Boolean = false

      public fun build(): ListType = ListType(element, nullable)
    }
  }
}

@Serializable
@ConsistentCopyVisibility
public data class ProductionEntryPointV1
internal constructor(
  public val id: String,
  public val kotlinFunction: String,
  public val kind: EntryPointKindV1,
  public val visibility: ProductionVisibilityV1,
  public val inputModel: String,
  public val root: String,
  public val events: List<ProductionEventV1> = emptyList(),
  public val bindings: List<ProductionBindingV1> = emptyList(),
  public val components: List<ProductionComponentUseV1> = emptyList(),
  public val eventBindings: List<ProductionEventBindingV1> = emptyList(),
) {
  public fun newBuilder(): Builder =
    Builder(id, kotlinFunction, kind, visibility, inputModel, root).also { builder ->
      builder.events = events
      builder.bindings = bindings
      builder.components = components
      builder.eventBindings = eventBindings
    }

  public class Builder(
    public var id: String,
    public var kotlinFunction: String,
    public var kind: EntryPointKindV1,
    public var visibility: ProductionVisibilityV1,
    public var inputModel: String,
    public var root: String,
  ) {
    public var events: List<ProductionEventV1> = emptyList()
    public var bindings: List<ProductionBindingV1> = emptyList()
    public var components: List<ProductionComponentUseV1> = emptyList()
    public var eventBindings: List<ProductionEventBindingV1> = emptyList()

    public fun build(): ProductionEntryPointV1 =
      ProductionEntryPointV1(
        id,
        kotlinFunction,
        kind,
        visibility,
        inputModel,
        root,
        events,
        bindings,
        components,
        eventBindings,
      )
  }
}

@Serializable
@ConsistentCopyVisibility
public data class ProductionEventV1
internal constructor(
  public val name: String,
  public val payload: ProductionTypeV1? = null,
) {
  public fun newBuilder(): Builder = Builder(name).also { builder -> builder.payload = payload }

  public class Builder(public var name: String) {
    public var payload: ProductionTypeV1? = null

    public fun build(): ProductionEventV1 = ProductionEventV1(name, payload)
  }
}

@Serializable
@ConsistentCopyVisibility
public data class ProductionEventBindingV1
internal constructor(
  public val nodeId: String,
  public val property: String,
  public val event: String,
  public val payloadPath: List<String>? = null,
) {
  public fun newBuilder(): Builder =
    Builder(nodeId, property, event).also { builder -> builder.payloadPath = payloadPath }

  public class Builder(
    public var nodeId: String,
    public var property: String,
    public var event: String,
  ) {
    public var payloadPath: List<String>? = null

    public fun build(): ProductionEventBindingV1 =
      ProductionEventBindingV1(nodeId, property, event, payloadPath)
  }
}

@Serializable
@ConsistentCopyVisibility
public data class ProductionBindingV1
internal constructor(
  public val nodeId: String,
  public val property: String,
  public val path: List<String>,
  public val expectedType: ProductionTypeV1,
) {
  public fun newBuilder(): Builder = Builder(nodeId, property, path, expectedType)

  public class Builder(
    public var nodeId: String,
    public var property: String,
    public var path: List<String>,
    public var expectedType: ProductionTypeV1,
  ) {
    public fun build(): ProductionBindingV1 =
      ProductionBindingV1(nodeId, property, path, expectedType)
  }
}

@Serializable
@ConsistentCopyVisibility
public data class ProductionComponentUseV1
internal constructor(
  public val nodeId: String,
  public val componentId: String,
  public val dataPath: List<String>,
  public val events: Map<String, String> = emptyMap(),
) {
  public fun newBuilder(): Builder =
    Builder(nodeId, componentId, dataPath).also { builder -> builder.events = events }

  public class Builder(
    public var nodeId: String,
    public var componentId: String,
    public var dataPath: List<String>,
  ) {
    public var events: Map<String, String> = emptyMap()

    public fun build(): ProductionComponentUseV1 =
      ProductionComponentUseV1(nodeId, componentId, dataPath, events)
  }
}
