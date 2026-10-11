package ee.schimke.composeai.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * Pins the wire shape of the three catalog files to real ones: a decode followed by an encode must
 * give back the same JSON tree. The fixtures are trimmed copies of files compose-ai-tools wrote
 * (`components.json`, `ui-builder.json`) and a catalog authored (`ui-builder.policy.json`), so a
 * renamed field, a dropped default or a changed `@SerialName` fails here before a consumer sees it.
 *
 * `ui-builder-remote-m3.json` was written by compose-ai-tools 2.35.0, before `insertContent`
 * (compose-ai-tools#5730) and `statusSemantics.newDesign` (compose-ai-tools#5735) existed; those
 * keys are added to it as `null`, which is what a current writer, encoding defaults, prints.
 */
class CatalogWireTest {
  /** How compose-ai-tools writes the generated files: every default spelled out. */
  private val generated = Json { encodeDefaults = true }

  /**
   * How a catalog's authored policy reads: defaults left out, and unknown keys ignored, because an
   * authored file annotates itself with `${'$'}comment_<field>` keys at every level.
   */
  private val authored = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
  }

  private fun resource(name: String): String =
    requireNotNull(javaClass.getResource("/$name")) { "missing fixture $name" }.readText()

  private fun <T> assertRoundTrips(
    json: Json,
    serializer: KSerializer<T>,
    text: String,
    normalize: (JsonElement) -> JsonElement = { it },
  ): T {
    val original = Json.parseToJsonElement(text)
    val value = json.decodeFromJsonElement(serializer, original)
    val expected = normalize(original)
    val actual = normalize(json.encodeToJsonElement(serializer, value))
    assertEquals(null, firstDifference(expected, actual, "$"), "round trip changed the JSON")
    return value
  }

  /** Where two JSON trees first disagree, so a failure names a path rather than two dumps. */
  private fun firstDifference(expected: JsonElement, actual: JsonElement, path: String): String? =
    when {
      expected is JsonObject && actual is JsonObject ->
        (expected.keys + actual.keys).firstNotNullOfOrNull { key ->
          val e = expected[key]
          val a = actual[key]
          when {
            e == null -> "$path.$key: unexpected ${a}"
            a == null -> "$path.$key: missing (expected ${e})"
            else -> firstDifference(e, a, "$path.$key")
          }
        }
      expected is JsonArray && actual is JsonArray ->
        if (expected.size != actual.size) "$path: ${expected.size} items, got ${actual.size}"
        else
          expected.indices.firstNotNullOfOrNull {
            firstDifference(expected[it], actual[it], "$path[$it]")
          }
      expected != actual -> "$path: expected $expected, got $actual"
      else -> null
    }

  /** Drops an authored file's `${'$'}comment…` annotations, which no type carries. */
  private fun withoutComments(element: JsonElement): JsonElement =
    when (element) {
      is JsonObject ->
        JsonObject(
          element
            .filterKeys { !it.startsWith("${'$'}comment") }
            .mapValues { withoutComments(it.value) }
        )
      is JsonArray -> JsonArray(element.map(::withoutComments))
      else -> element
    }

  @Test
  fun componentRecordRoundTrips() {
    val file =
      assertRoundTrips(
        generated,
        ComponentRecordFile.serializer(),
        resource("components-remote-m3.json"),
      )
    assertEquals(COMPONENT_RECORD_SCHEMA_VERSION, file.schemaVersion)
    assertTrue(file.components.isNotEmpty())
  }

  @Test
  fun builderCatalogRoundTrips() {
    val file =
      assertRoundTrips(
        generated,
        UiBuilderCatalogFile.serializer(),
        resource("ui-builder-remote-m3.json"),
      )
    assertEquals(UI_BUILDER_CATALOG_SCHEMA, file.schema)
    assertEquals(UI_BUILDER_RECORD_FILE, file.record.file)
  }

  @Test
  fun policyRoundTrips() {
    val policy =
      assertRoundTrips(
        authored,
        UiBuilderPolicyFile.serializer(),
        resource("ui-builder.policy-remote-widgets.json"),
        ::withoutComments,
      )
    assertEquals(UI_BUILDER_POLICY_SCHEMA, policy.schema)
  }

  @Test
  fun designTokensTravelVerbatimOnThePolicyAndTheSemantics() {
    val tokens =
      """{"tokens":[{"id":"color.primary","label":"Primary","kind":"color","default":null,""" +
        """"theme":[{"component":"wear-m3/screen-scaffold","property":"themePrimaryColor"}]}]}"""
    val policy =
      authored.decodeFromString(
        UiBuilderPolicyFile.serializer(),
        """{"schema":"$UI_BUILDER_POLICY_SCHEMA","platform":"wear","designTokens":$tokens}""",
      )
    assertEquals(authored.parseToJsonElement(tokens), policy.designTokens)
    assertEquals(policy, policy.newBuilder().build())
    val reencoded =
      authored.parseToJsonElement(authored.encodeToString(UiBuilderPolicyFile.serializer(), policy))
    assertEquals(authored.parseToJsonElement(tokens), (reencoded as JsonObject)["designTokens"])

    val semantics =
      UiBuilderStatusSemantics.Builder(
          "wear",
          "Wear",
          "wear-m3/",
          UiBuilderComponentMenu.Builder().build(),
        )
        .also { it.designTokens = policy.designTokens }
        .build()
    assertEquals(policy.designTokens, semantics.newBuilder().build().designTokens)
  }

  @Test
  fun templateEntryIsAPathOrAnObject() {
    val text =
      """
      {
        "schema": "compose-ui-builder-policy/v1",
        "platform": "wear",
        "templates": [
          "ui-builder/designs/list.json",
          {
            "path": "ui-builder/designs/hello.json",
            "id": "hello",
            "label": "Hello",
            "supportingText": "A first screen",
            "group": "Starters",
            "default": true,
            "order": 1
          },
          { "path": "ui-builder/designs/only-path.json" }
        ],
        "newDesign": { "label": "Wear OS", "order": 2 }
      }
      """
    val policy = authored.decodeFromString(UiBuilderPolicyFile.serializer(), text)
    val (bare, described, objectOnlyPath) = policy.templates

    assertEquals("list", bare.resolvedId)
    assertEquals(false, bare.describesItself)
    assertEquals("hello", described.resolvedId)
    assertEquals(true, described.default)
    assertEquals(1, described.order)
    assertEquals("only-path", objectOnlyPath.resolvedId)

    // A bare path, and an object that says nothing more than its path, both re-encode as a string.
    val templates =
      authored.encodeToJsonElement(UiBuilderPolicyFile.serializer(), policy).let {
        (it as JsonObject).getValue("templates").jsonArray
      }
    assertEquals(JsonPrimitive("ui-builder/designs/list.json"), templates[0])
    assertEquals(
      Json.parseToJsonElement(
        """{"path":"ui-builder/designs/hello.json","id":"hello","label":"Hello",""" +
          """"supportingText":"A first screen","group":"Starters","default":true,"order":1}"""
      ),
      templates[1],
    )
    assertEquals(JsonPrimitive("ui-builder/designs/only-path.json"), templates[2])
    assertEquals(
      UiBuilderNewDesign.Builder()
        .apply {
          label = "Wear OS"
          order = 2
        }
        .build(),
      policy.newDesign,
    )
  }

  @Test
  fun templateLocalsAndScopesAreAdditiveAndRoundTrip() {
    val legacy = """{"strategy":"templates","templates":{"list-item":"item${'$'}{key} {}"}}"""
    val bare = authored.decodeFromString(UiBuilderCode.serializer(), legacy)
    assertEquals(emptyMap(), bare.locals)
    assertEquals(emptyMap(), bare.scopes)
    assertEquals(
      Json.parseToJsonElement(legacy),
      authored.encodeToJsonElement(UiBuilderCode.serializer(), bare),
    )

    val wear =
      bare
        .newBuilder()
        .also {
          it.locals = mapOf("spec" to "androidx.wear.compose.material3.lazy.TransformationSpec")
          it.scopes =
            mapOf(
              "list-item" to "androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope"
            )
        }
        .build()
    val encoded = authored.encodeToString(UiBuilderCode.serializer(), wear)
    assertEquals(wear, authored.decodeFromString(UiBuilderCode.serializer(), encoded))
    assertEquals(wear, wear.newBuilder().build())
  }

  @Test
  fun newBuilderReproducesTheValue() {
    val file =
      generated.decodeFromString(
        ComponentRecordFile.serializer(),
        resource("components-remote-m3.json"),
      )
    assertEquals(file, file.newBuilder().build())
    file.components.forEach { record ->
      assertEquals(record, record.newBuilder().build())
      assertEquals(record.symbol, record.symbol.newBuilder().build())
      record.parameters.forEach { assertEquals(it, it.newBuilder().build()) }
      record.builder?.let { assertEquals(it, it.newBuilder().build()) }
    }

    val catalog =
      generated.decodeFromString(
        UiBuilderCatalogFile.serializer(),
        resource("ui-builder-remote-m3.json"),
      )
    assertEquals(catalog, catalog.newBuilder().build())
    assertEquals(catalog.statusSemantics, catalog.statusSemantics.newBuilder().build())
    catalog.statusSemantics.builtins.values.forEach { assertEquals(it, it.newBuilder().build()) }
    catalog.statusSemantics.components.values.forEach { assertEquals(it, it.newBuilder().build()) }
  }

  @Test
  fun aBuilderChangesOneField() {
    val entry = UiBuilderTemplateEntry.Builder("ui-builder/designs/a.json").build()
    val labelled = entry.newBuilder().apply { label = "A" }.build()
    assertEquals("ui-builder/designs/a.json", labelled.path)
    assertEquals("A", labelled.label)
    assertTrue(labelled.describesItself)
    val encoded: JsonElement =
      authored.encodeToJsonElement(UiBuilderTemplateEntry.serializer(), entry)
    assertEquals(JsonPrimitive("ui-builder/designs/a.json"), encoded)
  }

  @Test
  fun aRecordCarriesEveryOverloadAndRoundTrips() {
    val text = TargetParameter.Builder("text", "String").build()
    val slot = TargetParameter.Builder("content", "@Composable () -> Unit").build()
    val record =
      ComponentRecord.Builder(
          "app/a.ButtonKt.Button",
          ComponentSymbol.Builder("a.ButtonKt", "a.Button", "Button", ComponentOrigin.LIBRARY)
            .build(),
        )
        .apply {
          parameters = listOf(text)
          overloads =
            listOf(
              ComponentOverload.Builder()
                .apply {
                  descriptor = "(Ljava/lang/String;)V"
                  parameters = listOf(text)
                }
                .build(),
              ComponentOverload.Builder().apply { parameters = listOf(slot) }.build(),
            )
        }
        .build()
    val encoded = generated.encodeToJsonElement(ComponentRecord.serializer(), record)
    assertEquals(record, generated.decodeFromJsonElement(ComponentRecord.serializer(), encoded))
    assertEquals(record, record.newBuilder().build())
    record.overloads.forEach { assertEquals(it, it.newBuilder().build()) }
    // A record written before the field existed reads as "not recorded".
    val legacy =
      authored.decodeFromString(
        ComponentRecord.serializer(),
        authored.encodeToString(
          ComponentRecord.serializer(),
          record.newBuilder().apply { overloads = emptyList() }.build(),
        ),
      )
    assertTrue(legacy.overloads.isEmpty())
  }

  @Test
  fun anExportProjectionIsCarriedVerbatimAndLeftOutWhenAbsent() {
    val projection =
      Json.parseToJsonElement(
        """{"variantProperty":"variant","targets":[{"record":"app/a.ButtonKt.Button","parameters":{}}]}"""
      )
    val authoredComponent =
      UiBuilderAuthoredComponent.Builder().apply { exportProjection = projection }.build()
    val encoded =
      authored.encodeToJsonElement(UiBuilderAuthoredComponent.serializer(), authoredComponent)
    assertEquals(projection, (encoded as JsonObject)["exportProjection"])
    assertEquals(
      authoredComponent,
      authored.decodeFromJsonElement(UiBuilderAuthoredComponent.serializer(), encoded),
    )
    val published =
      UiBuilderComponentPolicy.Builder("app/a.ButtonKt.Button")
        .apply { exportProjection = projection }
        .build()
    assertEquals(projection, published.newBuilder().build().exportProjection)
    val unstated = UiBuilderAuthoredComponent.Builder().build()
    val bare = authored.encodeToJsonElement(UiBuilderAuthoredComponent.serializer(), unstated)
    assertEquals(null, (bare as JsonObject)["exportProjection"])
  }
}
