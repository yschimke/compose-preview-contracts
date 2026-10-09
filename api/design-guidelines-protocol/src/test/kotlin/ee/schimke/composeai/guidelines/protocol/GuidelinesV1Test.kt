package ee.schimke.composeai.guidelines.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class GuidelinesV1Test {
  /** Unknown keys fail: a fixture decoding here has no field these types do not declare. */
  private val strict = Json { ignoreUnknownKeys = false }

  /** How compose-ui-builder encodes these shapes today (`GUIDELINE_JSON`). */
  private val builderJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  private fun resource(name: String): String =
    checkNotNull(javaClass.getResource("/$name")) { name }.readText()

  /** Decodes [text] strictly, then checks a re-encoding decodes to the same value. */
  private fun <T> roundTrip(serializer: KSerializer<T>, text: String): T {
    val decoded = strict.decodeFromString(serializer, text)
    assertEquals(
      decoded,
      strict.decodeFromString(serializer, builderJson.encodeToString(serializer, decoded)),
    )
    return decoded
  }

  @Test
  fun `a catalog's published guidelines file decodes field for field`() {
    val guidelines =
      roundTrip(CatalogGuidelinesV1.serializer(), resource("catalog-guidelines-wear-m3.json"))
    assertEquals(CatalogGuidelinesV1.SCHEMA, guidelines.schema)
    assertEquals("wear-m3", guidelines.catalog)
    assertEquals("wear", guidelines.platform)
    assertEquals(
      listOf(GuidelineFrameV1.KIND_DEVICE, GuidelineFrameV1.KIND_UNROLLED),
      guidelines.frames.map { it.kind },
    )
    assertTrue(guidelines.frames[1].whenScrolls)
    assertTrue(guidelines.rules.isNotEmpty())
    guidelines.rules.forEach { rule ->
      assertTrue('?' in rule.check, rule.id)
      assertEquals(listOf(GuidelineRuleV1.SURFACE_SCREEN), rule.surfaces, rule.id)
    }
  }

  @Test
  fun `a request the preview server served decodes field for field`() {
    val request =
      roundTrip(GuidelineRequestV1.serializer(), resource("guidelines-request-server.json"))
    assertEquals(GuidelineRequestV1.SCHEMA, request.schema)
    assertEquals("golden-tiles-timer-1", request.designId)
    assertNull(request.previewId)
    assertEquals(
      listOf(GuidelinePictureV1.KIND_WIDGET_SAMSUNG, GuidelinePictureV1.KIND_WIDGET_PIXEL_WATCH),
      request.pictures.map { it.kind },
    )
    assertEquals(request.rules.asked.size, request.rules.forPlatform - request.rules.visualSkipped)
    assertTrue(request.responseSchema.isNotEmpty())
  }

  @Test
  fun `a record the preview server stored decodes field for field`() {
    val record =
      roundTrip(GuidelineRecordV1.serializer(), resource("guidelines-record-server.json"))
    assertEquals(GuidelineRecordV1.SCHEMA, record.schema)
    assertEquals("golden-tiles-timer-1", record.designId)
    assertTrue(record.verdicts.all { it.ruleId in record.asked })
    assertTrue(record.ranBy != null && record.recordedAtEpochMillis != null)
  }

  @Test
  fun `the schema is always written, and a subject's absent preview id never is`() {
    val record =
      GuidelineRecordV1.Builder(
          revision = 3,
          model = "m",
          rulesVersion = 1,
          asked = listOf("r"),
          verdicts = listOf(GuidelineVerdictV1.Builder("r", GuidelineVerdictV1.PASS).build()),
        )
        .also { it.designId = "d" }
        .build()
    // Even a host that encodes defaults must not send a null previewId.
    val withDefaults = Json { encodeDefaults = true }
    val json = withDefaults.encodeToJsonElement(GuidelineRecordV1.serializer(), record).jsonObject
    assertFalse("previewId" in json, json.toString())
    assertTrue(
      "schema" in builderJson.encodeToJsonElement(GuidelineRecordV1.serializer(), record).jsonObject
    )

    val request =
      GuidelineRequestV1.Builder(
          revision = 1,
          rules = GuidelineRequestRulesV1.Builder(1, "s", 0, emptyList()).build(),
          systemPrompt = "sys",
          userText = "user",
          responseSchema = JsonObject(emptyMap()),
        )
        .build()
    val requestJson =
      withDefaults.encodeToJsonElement(GuidelineRequestV1.serializer(), request).jsonObject
    assertFalse("previewId" in requestJson, requestJson.toString())
    assertEquals(
      GuidelineRequestV1.SCHEMA,
      builderJson
        .encodeToJsonElement(GuidelineRequestV1.serializer(), request)
        .jsonObject["schema"]
        .toString()
        .trim('"'),
    )
  }

  @Test
  fun `a request about a rendered preview carries its preview id`() {
    val request =
      GuidelineRequestV1.Builder(
          revision = 0,
          rules = GuidelineRequestRulesV1.Builder(2, "s", 1, emptyList()).build(),
          systemPrompt = "sys",
          userText = "user",
          responseSchema = buildJsonObject { put("type", "object") },
        )
        .also { it.previewId = "com.example.HomePreview" }
        .build()
    val text = builderJson.encodeToString(GuidelineRequestV1.serializer(), request)
    assertTrue("\"previewId\":\"com.example.HomePreview\"" in text, text)
    assertNull(strict.decodeFromString(GuidelineRequestV1.serializer(), text).designId)
    assertEquals(request, roundTrip(GuidelineRequestV1.serializer(), text))
  }

  @Test
  fun `builders round-trip every type`() {
    val rule =
      GuidelineRuleV1.Builder("id", GuidelineRuleV1.KIND_VISUAL, "warning", "g", "ok?", "https://x")
        .also {
          it.surfaces = listOf(GuidelineRuleV1.SURFACE_WIDGET)
          it.profiles = listOf("launcher-widgets-v7+experimental")
        }
        .build()
    assertEquals(rule, rule.newBuilder().build())
    val frame =
      GuidelineFrameV1.Builder(GuidelineFrameV1.KIND_WIDGET_HOST)
        .also {
          it.hostShape = "round"
          it.label = "Samsung"
        }
        .build()
    assertEquals(frame, frame.newBuilder().build())
    val catalog =
      CatalogGuidelinesV1.Builder("remote-m3", "wear", 2)
        .also {
          it.frames = listOf(frame)
          it.rules = listOf(rule)
        }
        .build()
    assertEquals(catalog, catalog.newBuilder().build())
    assertEquals(
      catalog,
      roundTrip(
        CatalogGuidelinesV1.serializer(),
        builderJson.encodeToString(CatalogGuidelinesV1.serializer(), catalog),
      ),
    )
    val set = GuidelineRuleSetV1.Builder("s", 1, listOf(rule)).build()
    assertEquals(set, set.newBuilder().build())
    val picture = GuidelinePictureV1.Builder("tablet", "d", 1280, 800).build()
    assertEquals(picture, picture.newBuilder().build())
    val verdict = GuidelineVerdictV1.Builder("id", GuidelineVerdictV1.FAIL).build()
    assertEquals(verdict, verdict.newBuilder().build())
  }
}
