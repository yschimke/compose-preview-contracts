package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Derives a JSON Schema (draft 2020-12) from a serializer's descriptor.
 *
 * The published schemas are *generated from the serializers*, never written beside them, so they
 * cannot drift: `ProtocolJsonSchemaTest` regenerates them and fails on any difference from the
 * committed files. That is also why this lives in the tests rather than the published module - it
 * is tooling that produces a contract, not part of one.
 *
 * ## What the schema says
 *
 * * Objects are closed (`additionalProperties: false`), matching the strict configuration hosts
 *   decode with. A reader that tolerates unknown fields is more lenient than the schema, never
 *   less.
 * * A property with a default is optional; one without is `required`. Encoders in this protocol
 *   omit defaults and nulls (`encodeDefaults = false`, `explicitNulls = false`), and the schema
 *   accepts both the omitted and the explicit form.
 * * A sealed hierarchy is a `oneOf` over its subtypes, each carrying a `const` discriminator:
 *   `type` by default, or whatever `@JsonClassDiscriminator` names (`kind` for a design's home).
 * * `JsonElement` (an open value) is unconstrained.
 * * Named types go in `$defs`, keyed by the class's simple name, or `Parent.serialName` for a
 *   subtype whose `@SerialName` replaced its class name.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class ProtocolJsonSchema private constructor() {
  private val defs = sortedMapOf<String, JsonElement>()
  private val keyByDescriptor = mutableMapOf<String, String>()

  companion object {
    private const val DIALECT = "https://json-schema.org/draft/2020-12/schema"

    fun generate(title: String, serializer: KSerializer<*>): JsonObject {
      val generator = ProtocolJsonSchema()
      val root = generator.schemaFor(serializer.descriptor)
      return buildJsonObject {
        put("\$schema", DIALECT)
        put("title", title)
        put(
          "description",
          "Generated from the ee.schimke.composeai:ui-builder-protocol serializers; do not edit.",
        )
        root.forEach { (key, value) -> put(key, value) }
        put("\$defs", JsonObject(generator.defs))
      }
    }
  }

  private fun ref(key: String) = buildJsonObject { put("\$ref", "#/\$defs/$key") }

  private fun schemaFor(descriptor: SerialDescriptor): JsonObject {
    val base = nonNull(descriptor)
    return if (descriptor.isNullable) {
      buildJsonObject {
        putJsonArray("anyOf") {
          add(base)
          add(buildJsonObject { put("type", "null") })
        }
      }
    } else base
  }

  private fun nonNull(descriptor: SerialDescriptor): JsonObject {
    if (descriptor.isInline) return schemaFor(descriptor.getElementDescriptor(0))
    openValue(descriptor.serialName.removeSuffix("?"))?.let {
      return it
    }
    return when (val kind = descriptor.kind) {
      PrimitiveKind.STRING,
      PrimitiveKind.CHAR -> buildJsonObject { put("type", "string") }
      PrimitiveKind.BOOLEAN -> buildJsonObject { put("type", "boolean") }
      PrimitiveKind.BYTE,
      PrimitiveKind.SHORT,
      PrimitiveKind.INT,
      PrimitiveKind.LONG -> buildJsonObject { put("type", "integer") }
      PrimitiveKind.FLOAT,
      PrimitiveKind.DOUBLE -> buildJsonObject { put("type", "number") }
      SerialKind.ENUM -> named(descriptor) { enumSchema(descriptor) }
      StructureKind.LIST ->
        buildJsonObject {
          put("type", "array")
          put("items", schemaFor(descriptor.getElementDescriptor(0)))
        }
      StructureKind.MAP -> {
        require(descriptor.getElementDescriptor(0).kind == PrimitiveKind.STRING) {
          "${descriptor.serialName}: only string-keyed maps have a JSON object form"
        }
        buildJsonObject {
          put("type", "object")
          put("additionalProperties", schemaFor(descriptor.getElementDescriptor(1)))
        }
      }
      StructureKind.CLASS,
      StructureKind.OBJECT -> named(descriptor) { classSchema(descriptor, discriminator = null) }
      PolymorphicKind.SEALED -> named(descriptor) { sealedSchema(descriptor) }
      SerialKind.CONTEXTUAL,
      PolymorphicKind.OPEN -> JsonObject(emptyMap())
      else -> error("${descriptor.serialName}: unsupported kind $kind")
    }
  }

  /** `kotlinx.serialization.json` types are open JSON, not protocol classes. */
  private fun openValue(serialName: String): JsonObject? =
    when (serialName) {
      "kotlinx.serialization.json.JsonElement" -> JsonObject(emptyMap())
      "kotlinx.serialization.json.JsonObject" -> buildJsonObject { put("type", "object") }
      "kotlinx.serialization.json.JsonArray" -> buildJsonObject { put("type", "array") }
      "kotlinx.serialization.json.JsonPrimitive",
      "kotlinx.serialization.json.JsonLiteral" ->
        buildJsonObject {
          putJsonArray("type") {
            add(JsonPrimitive("string"))
            add(JsonPrimitive("number"))
            add(JsonPrimitive("boolean"))
            add(JsonPrimitive("null"))
          }
        }
      "kotlinx.serialization.json.JsonNull" -> buildJsonObject { put("type", "null") }
      else -> null
    }

  private fun named(descriptor: SerialDescriptor, build: () -> JsonObject): JsonObject {
    val identity = descriptor.serialName.removeSuffix("?")
    val existing = keyByDescriptor[identity]
    if (existing != null) return ref(existing)
    val key = keyFor(identity)
    keyByDescriptor[identity] = key
    defs[key] = JsonObject(emptyMap()) // Reserve the slot so a recursive type refers to itself.
    defs[key] = build()
    return ref(key)
  }

  private fun keyFor(serialName: String): String {
    val simple = serialName.substringAfterLast('.')
    val taken = defs.keys + keyByDescriptor.values
    return if (simple in taken) "$simple#${taken.count { it.startsWith(simple) }}" else simple
  }

  private fun enumSchema(descriptor: SerialDescriptor) = buildJsonObject {
    put("type", "string")
    putJsonArray("enum") { descriptor.elementNames.forEach { add(JsonPrimitive(it)) } }
  }

  private fun discriminatorOf(descriptor: SerialDescriptor): String =
    descriptor.annotations.filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator
      ?: "type"

  private fun sealedSchema(descriptor: SerialDescriptor): JsonObject {
    val discriminator = discriminatorOf(descriptor)
    // Sorted: the order a sealed descriptor lists its subtypes in is an implementation detail of
    // the
    // runtime, and a committed file must not change because of it.
    val subtypes = descriptor.getElementDescriptor(1).elementDescriptors.sortedBy { it.serialName }
    require(subtypes.isNotEmpty()) { "${descriptor.serialName} has no registered subtypes" }
    return buildJsonObject {
      putJsonArray("oneOf") {
        subtypes.forEach { subtype ->
          val key = keyFor(descriptor, subtype)
          val identity = subtype.serialName
          if (identity !in keyByDescriptor) {
            keyByDescriptor[identity] = key
            defs[key] = JsonObject(emptyMap())
            defs[key] =
              if (subtype.kind == PolymorphicKind.SEALED) sealedSchema(subtype)
              else classSchema(subtype, discriminator)
          }
          add(ref(keyByDescriptor.getValue(identity)))
        }
      }
    }
  }

  /** A subtype registered under a bare `@SerialName` is keyed beneath its parent's name. */
  private fun keyFor(parent: SerialDescriptor, subtype: SerialDescriptor): String {
    keyByDescriptor[subtype.serialName]?.let {
      return it
    }
    val simple = subtype.serialName.substringAfterLast('.')
    val bare = '.' !in subtype.serialName
    val parentName = parent.serialName.removeSuffix("?").substringAfterLast('.')
    val wanted = if (bare) "$parentName.$simple" else simple
    return if (wanted in defs.keys) "$wanted#${defs.keys.count { it.startsWith(wanted) }}"
    else wanted
  }

  private fun classSchema(descriptor: SerialDescriptor, discriminator: String?): JsonObject {
    val required = mutableListOf<String>()
    val properties = linkedMapOf<String, JsonElement>()
    if (discriminator != null) {
      required += discriminator
      properties[discriminator] = buildJsonObject { put("const", descriptor.serialName) }
    }
    for (index in 0 until descriptor.elementsCount) {
      val name = descriptor.getElementName(index)
      check(name != discriminator) {
        "${descriptor.serialName}.$name collides with its hierarchy's discriminator"
      }
      properties[name] = schemaFor(descriptor.getElementDescriptor(index))
      if (!descriptor.isElementOptional(index)) required += name
    }
    return buildJsonObject {
      put("type", "object")
      put("properties", JsonObject(properties))
      if (required.isNotEmpty()) {
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
      }
      put("additionalProperties", false)
    }
  }
}

/**
 * Checks a JSON value against a schema [ProtocolJsonSchema] produced.
 *
 * Deliberately only the subset that generator emits (`type`, `enum`, `const`, `properties`,
 * `required`, `additionalProperties`, `items`, `oneOf`, `anyOf`, `$ref`) - it exists so the
 * committed fixtures can be checked against the committed schemas without a validator dependency,
 * which this repository's catalog does not carry. Returns the violations, empty when valid.
 */
internal class SubsetSchemaValidator(private val root: JsonObject) {
  private val defs = root["\$defs"] as? JsonObject ?: JsonObject(emptyMap())

  fun validate(value: JsonElement): List<String> =
    mutableListOf<String>().also { check(root, value, "$", it) }

  private fun matches(schema: JsonObject, value: JsonElement): Boolean =
    mutableListOf<String>().also { check(schema, value, "$", it) }.isEmpty()

  private fun check(
    schema: JsonObject,
    value: JsonElement,
    path: String,
    out: MutableList<String>,
  ) {
    (schema["\$ref"] as? JsonPrimitive)?.let { reference ->
      val target =
        defs[reference.content.removePrefix("#/\$defs/")] as? JsonObject
          ?: return out.add("$path: unresolved ${reference.content}").let {}
      return check(target, value, path, out)
    }
    (schema["anyOf"] as? JsonArray)?.let { options ->
      if (options.none { matches(it as JsonObject, value) })
        out.add("$path: matches no anyOf branch")
      return
    }
    (schema["oneOf"] as? JsonArray)?.let { options ->
      val discriminating = options.map { it as JsonObject }.filter { branchAccepts(it, value) }
      when (discriminating.size) {
        1 -> check(discriminating.single(), value, path, out)
        0 -> out.add("$path: matches no oneOf branch (${describe(value)})")
        else -> out.add("$path: matches ${discriminating.size} oneOf branches")
      }
      return
    }
    schema["const"]?.let { if (value != it) out.add("$path: expected $it, was $value") }
    (schema["enum"] as? JsonArray)?.let { if (value !in it) out.add("$path: $value not in $it") }
    schema["type"]?.let { type ->
      val allowed =
        (type as? JsonArray)?.map { (it as JsonPrimitive).content }
          ?: listOf((type as JsonPrimitive).content)
      if (allowed.none { typeMatches(it, value) }) {
        out.add("$path: expected ${allowed.joinToString("|")}, was ${describe(value)}")
        return
      }
    }
    if (value is JsonObject) {
      val properties = schema["properties"] as? JsonObject
      (schema["required"] as? JsonArray)?.forEach { name ->
        if ((name as JsonPrimitive).content !in value) out.add("$path: missing ${name.content}")
      }
      value.forEach { (name, child) ->
        val declared = properties?.get(name) as? JsonObject
        val additional = schema["additionalProperties"]
        when {
          declared != null -> check(declared, child, "$path.$name", out)
          additional is JsonObject -> check(additional, child, "$path.$name", out)
          additional == JsonPrimitive(false) -> out.add("$path: unexpected property $name")
        }
      }
    }
    if (value is JsonArray) {
      (schema["items"] as? JsonObject)?.let { items ->
        value.forEachIndexed { index, child -> check(items, child, "$path[$index]", out) }
      }
    }
  }

  /**
   * Whether [branch] is the one [value] selects: by discriminator when it has one, else by shape.
   */
  private fun branchAccepts(branch: JsonObject, value: JsonElement): Boolean {
    val resolved = resolve(branch)
    val discriminators =
      (resolved["properties"] as? JsonObject)
        ?.filterValues { (it as? JsonObject)?.containsKey("const") == true }
        .orEmpty()
    if (value is JsonObject && discriminators.isNotEmpty()) {
      return discriminators.all { (name, spec) -> value[name] == (spec as JsonObject)["const"] }
    }
    return matches(resolved, value)
  }

  private fun resolve(schema: JsonObject): JsonObject {
    val reference = (schema["\$ref"] as? JsonPrimitive)?.content ?: return schema
    return resolve(defs.getValue(reference.removePrefix("#/\$defs/")) as JsonObject)
  }

  private fun typeMatches(type: String, value: JsonElement): Boolean =
    when (type) {
      "object" -> value is JsonObject
      "array" -> value is JsonArray
      "string" -> value is JsonPrimitive && value.isString
      "boolean" ->
        value is JsonPrimitive && !value.isString && value.content in setOf("true", "false")
      "integer" -> value is JsonPrimitive && !value.isString && value.content.toLongOrNull() != null
      "number" ->
        value is JsonPrimitive && !value.isString && value.content.toDoubleOrNull() != null
      "null" -> value is JsonNull
      else -> false
    }

  private fun describe(value: JsonElement) =
    when (value) {
      is JsonObject -> "object"
      is JsonArray -> "array"
      is JsonNull -> "null"
      is JsonPrimitive -> if (value.isString) "string" else "number/boolean"
    }
}

internal val schemaJson = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
}
