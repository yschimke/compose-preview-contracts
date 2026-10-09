package ee.schimke.composeai.guidelines.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

  @Test
  fun `the published fixtures re-encode to the same JSON, with no batch field added`() {
    listOf(
        CatalogGuidelinesV1.serializer() to "catalog-guidelines-wear-m3.json",
        GuidelineRequestV1.serializer() to "guidelines-request-server.json",
        GuidelineRecordV1.serializer() to "guidelines-record-server.json",
      )
      .forEach { (serializer, name) ->
        @Suppress("UNCHECKED_CAST") val typed = serializer as KSerializer<Any>
        val text = resource(name)
        val decoded = strict.decodeFromString(typed, text)
        // Every key the re-encoding writes is one the published file already had: none of the
        // batch or evidence fields appears at its default. (The server's file also spells out a
        // few defaults, `visualSkipped: 0`, which the compact encoding leaves out; that is not a
        // difference in what the document says.)
        assertNoNewKeys(
          builderJson.parseToJsonElement(text),
          builderJson.encodeToJsonElement(typed, decoded),
          name,
        )
        assertEquals(
          decoded,
          strict.decodeFromString(typed, builderJson.encodeToString(typed, decoded)),
        )
      }
  }

  private fun assertNoNewKeys(published: JsonElement, encoded: JsonElement, path: String) {
    when (encoded) {
      is JsonObject -> {
        val original = published as JsonObject
        encoded.forEach { (key, value) ->
          assertTrue(key in original, "$path.$key is new")
          assertNoNewKeys(original.getValue(key), value, "$path.$key")
        }
      }
      is JsonArray ->
        encoded.forEachIndexed { index, value ->
          assertNoNewKeys((published as JsonArray)[index], value, "$path[$index]")
        }
      else -> assertEquals(published, encoded, path)
    }
  }

  @Test
  fun `one request judges three previews, and its record keeps a verdict per subject`() {
    val subjects =
      listOf("Home", "Settings", "Detail").mapIndexed { index, name ->
        GuidelineSubjectV1.Builder("com.example.${name}Preview", GuidelineSubjectV1.KIND_PREVIEW)
          .also {
            it.renderHash = "sha256:$index"
            it.label = name
          }
          .build()
      }
    val perSubject =
      GuidelineRuleV1.Builder(
          "wear.touch-target-48dp",
          GuidelineRuleV1.KIND_VISUAL,
          GuidelineRuleV1.SEVERITY_WARNING,
          "g",
          "ok?",
          "https://developer.android.com/x",
        )
        .build()
    val acrossTheSet =
      perSubject
        .newBuilder()
        .also {
          it.id = "wear.button.one-primary-across-samples"
          it.scope = GuidelineRuleV1.SCOPE_SET
        }
        .build()
    val request =
      GuidelineRequestV1.Builder(
          revision = 0,
          rules =
            GuidelineRequestRulesV1.Builder(1, "s", 2, listOf(perSubject, acrossTheSet)).build(),
          systemPrompt = "sys",
          userText = "user",
          responseSchema = buildJsonObject { put("type", "object") },
        )
        .also { builder ->
          builder.subjects = subjects
          builder.pictures = subjects.map { subject ->
            GuidelinePictureV1.Builder(GuidelinePictureV1.KIND_DEVICE, subject.label!!, 192, 192)
              .also { it.subjectId = subject.id }
              .build()
          }
        }
        .build()
    val requestText = builderJson.encodeToString(GuidelineRequestV1.serializer(), request)
    assertEquals(request, roundTrip(GuidelineRequestV1.serializer(), requestText))
    assertEquals(subjects.map { it.id }, request.pictures.map { it.subjectId })

    val verdicts =
      subjects.map { subject ->
        GuidelineVerdictV1.Builder(perSubject.id, GuidelineVerdictV1.PASS)
          .also { it.subjectId = subject.id }
          .build()
      } + GuidelineVerdictV1.Builder(acrossTheSet.id, GuidelineVerdictV1.FAIL).build()
    val record =
      GuidelineRecordV1.Builder(
          revision = 0,
          model = "m",
          rulesVersion = 1,
          asked = listOf(perSubject.id, acrossTheSet.id),
          verdicts = verdicts,
        )
        .also { it.subjects = subjects }
        .build()
    val recordText = builderJson.encodeToString(GuidelineRecordV1.serializer(), record)
    val decoded = roundTrip(GuidelineRecordV1.serializer(), recordText)
    assertEquals(record, decoded)
    assertEquals(3, decoded.verdicts.count { it.subjectId != null })
    assertNull(decoded.verdicts.single { it.ruleId == acrossTheSet.id }.subjectId)
    assertEquals(GuidelineRuleV1.SCOPE_SET, request.rules.asked[1].scope)
  }

  @Test
  fun `batch fields are never written at their defaults`() {
    val withDefaults = Json { encodeDefaults = true }
    val rule = GuidelineRuleV1.Builder("r", "structure", "info", "g", "ok?", "https://x").build()
    val ruleJson = withDefaults.encodeToJsonElement(GuidelineRuleV1.serializer(), rule).jsonObject
    assertFalse("scope" in ruleJson, ruleJson.toString())
    val verdict = GuidelineVerdictV1.Builder("r", GuidelineVerdictV1.PASS).build()
    assertFalse(
      "subjectId" in
        withDefaults.encodeToJsonElement(GuidelineVerdictV1.serializer(), verdict).jsonObject
    )
    val picture = GuidelinePictureV1.Builder("device", "d", 1, 1).build()
    assertFalse(
      "subjectId" in
        withDefaults.encodeToJsonElement(GuidelinePictureV1.serializer(), picture).jsonObject
    )
    val record =
      GuidelineRecordV1.Builder(0, "m", 1, emptyList(), emptyList())
        .also { it.designId = "d" }
        .build()
    assertFalse(
      "subjects" in
        withDefaults.encodeToJsonElement(GuidelineRecordV1.serializer(), record).jsonObject
    )
    val subject = GuidelineSubjectV1.Builder("p", GuidelineSubjectV1.KIND_PREVIEW).build()
    assertEquals(subject, subject.newBuilder().build())
  }

  @Test
  fun `a follow-up round carries the evidence a verdict asked for`() {
    val subject = "com.example.HomePreview"
    val asked =
      GuidelineVerdictV1.Builder("wear.actions-labelled", GuidelineVerdictV1.NEEDS_EVIDENCE)
        .also { verdict ->
          verdict.subjectId = subject
          verdict.needs =
            listOf(
              GuidelineEvidenceNeedV1.Builder(GuidelineEvidenceNeedV1.KIND_A11Y_HIERARCHY)
                .also { it.reason = "whether the icon-only button has a content description" }
                .build(),
              GuidelineEvidenceNeedV1.Builder(GuidelineEvidenceNeedV1.KIND_RENDER)
                .also {
                  it.theme = "dark"
                  it.reason = "the contrast of the icon on the dark surface"
                }
                .build(),
            )
        }
        .build()
    val verdictText = builderJson.encodeToString(GuidelineVerdictV1.serializer(), asked)
    val decodedVerdict = roundTrip(GuidelineVerdictV1.serializer(), verdictText)
    assertEquals(asked, decodedVerdict)
    assertEquals(
      listOf(GuidelineEvidenceNeedV1.KIND_A11Y_HIERARCHY, GuidelineEvidenceNeedV1.KIND_RENDER),
      decodedVerdict.needs.map { it.kind },
    )
    assertEquals("dark", decodedVerdict.needs[1].theme)

    val followUp =
      GuidelineRequestV1.Builder(
          revision = 0,
          rules = GuidelineRequestRulesV1.Builder(1, "s", 1, emptyList()).build(),
          systemPrompt = "sys",
          userText = "user",
          responseSchema = buildJsonObject { put("type", "object") },
        )
        .also { request ->
          request.round = 1
          request.evidenceAvailable =
            listOf(GuidelineEvidenceNeedV1.KIND_A11Y_HIERARCHY, GuidelineEvidenceNeedV1.KIND_RENDER)
          request.subjects =
            listOf(GuidelineSubjectV1.Builder(subject, GuidelineSubjectV1.KIND_PREVIEW).build())
          request.evidence =
            listOf(
              GuidelineEvidenceV1.Builder(
                  GuidelineEvidenceNeedV1.KIND_A11Y_HIERARCHY,
                  "application/json",
                  """{"role":"button","contentDescription":null}""",
                )
                .also { it.subjectId = subject }
                .build()
            )
          request.pictures =
            listOf(
              GuidelinePictureV1.Builder(GuidelinePictureV1.KIND_DEVICE, "dark", 192, 192)
                .also {
                  it.subjectId = subject
                  it.theme = "dark"
                  it.fontScale = 1.5
                }
                .build()
            )
        }
        .build()
    val text = builderJson.encodeToString(GuidelineRequestV1.serializer(), followUp)
    val decoded = roundTrip(GuidelineRequestV1.serializer(), text)
    assertEquals(followUp, decoded)
    assertEquals(1, decoded.round)
    assertEquals(subject, decoded.evidence.single().subjectId)
    assertEquals(1.5, decoded.pictures.single().fontScale)
  }

  @Test
  fun `evidence fields are never written at their defaults`() {
    val withDefaults = Json { encodeDefaults = true }
    val request =
      GuidelineRequestV1.Builder(
          revision = 0,
          rules = GuidelineRequestRulesV1.Builder(1, "s", 0, emptyList()).build(),
          systemPrompt = "sys",
          userText = "user",
          responseSchema = JsonObject(emptyMap()),
        )
        .build()
    val json = withDefaults.encodeToJsonElement(GuidelineRequestV1.serializer(), request).jsonObject
    listOf("evidence", "evidenceAvailable", "round", "subjects").forEach {
      assertFalse(it in json, "$it in $json")
    }
    val picture = GuidelinePictureV1.Builder("device", "d", 1, 1).build()
    val pictureJson =
      withDefaults.encodeToJsonElement(GuidelinePictureV1.serializer(), picture).jsonObject
    listOf("theme", "fontScale", "device", "locale", "layoutDirection", "scroll").forEach {
      assertFalse(it in pictureJson, "$it in $pictureJson")
    }
    val rule = GuidelineRuleV1.Builder("r", "structure", "info", "g", "ok?", "https://x").build()
    assertFalse(
      "evidence" in withDefaults.encodeToJsonElement(GuidelineRuleV1.serializer(), rule).jsonObject
    )
    val verdict = GuidelineVerdictV1.Builder("r", GuidelineVerdictV1.PASS).build()
    assertFalse(
      "needs" in
        withDefaults.encodeToJsonElement(GuidelineVerdictV1.serializer(), verdict).jsonObject
    )
    val evidence = GuidelineEvidenceV1.Builder("source", "text/plain", "x").build()
    assertEquals(evidence, evidence.newBuilder().build())
    val need = GuidelineEvidenceNeedV1.Builder("render").also { it.scroll = "end" }.build()
    assertEquals(need, need.newBuilder().build())
  }

  @Test
  fun `a record names the model that answered and how a router chose it`() {
    val record =
      GuidelineRecordV1.Builder(
          revision = 60,
          model = "typesafe/jev-router",
          rulesVersion = 6,
          asked = listOf("r"),
          verdicts = listOf(GuidelineVerdictV1.Builder("r", GuidelineVerdictV1.PASS).build()),
        )
        .also { builder ->
          builder.designId = "golden-tiles-timer-1"
          builder.servedModel = "deepseek/deepseek-v4.1-flash"
          builder.provider = "DeepSeek"
          builder.costUsd = 0.0074
          builder.generationId = "gen-123"
          builder.routing =
            GuidelineRoutingV1.Builder("typesafe/jev-router")
              .also {
                it.version = "1"
                it.reason = "initial"
                it.probability = 0.82
                it.scores = mapOf("big_model_gain" to 0.1, "visual_quality" to 0.4)
              }
              .build()
        }
        .build()
    val text = builderJson.encodeToString(GuidelineRecordV1.serializer(), record)
    val decoded = roundTrip(GuidelineRecordV1.serializer(), text)
    assertEquals(record, decoded)
    assertEquals("typesafe/jev-router", decoded.model)
    assertEquals("deepseek/deepseek-v4.1-flash", decoded.servedModel)
    assertEquals(0.4, decoded.routing!!.scores["visual_quality"])
    assertEquals(record.routing, record.routing!!.newBuilder().build())

    val plain = GuidelineRecordV1.Builder(0, "m", 1, emptyList(), emptyList()).build()
    val json = Json {
      encodeDefaults = true
    }
      .encodeToJsonElement(GuidelineRecordV1.serializer(), plain)
      .jsonObject
    listOf("servedModel", "provider", "costUsd", "generationId", "routing").forEach {
      assertFalse(it in json, "$it in $json")
    }
  }

  @Test
  fun `a verdict can point at nodes and at a region of a picture`() {
    val region =
      GuidelineRegionV1.Builder(x = 0.1, y = 0.2, width = 0.3, height = 0.25)
        .apply {
          subjectId = "ButtonPreview"
          pictureKind = "device"
          label = "cut by the edge"
        }
        .build()
    val verdict =
      GuidelineVerdictV1.Builder("wear.layout.no-clipping", GuidelineVerdictV1.FAIL)
        .apply {
          confidence = 0.8
          nodeIds = listOf("a11y-3")
          subjectId = "ButtonPreview"
          regions = listOf(region)
        }
        .build()
    val text = builderJson.encodeToString(GuidelineVerdictV1.serializer(), verdict)
    assertEquals(verdict, roundTrip(GuidelineVerdictV1.serializer(), text))
    assertEquals(verdict, verdict.newBuilder().build())
    assertEquals(region, region.newBuilder().build())

    val firstPicture = GuidelineRegionV1.Builder(0.0, 0.5, 1.0, 0.5).build()
    val firstText = builderJson.encodeToString(GuidelineRegionV1.serializer(), firstPicture)
    assertNull(roundTrip(GuidelineRegionV1.serializer(), firstText).pictureKind)
    val firstJson = Json {
      encodeDefaults = true
    }
      .encodeToJsonElement(GuidelineRegionV1.serializer(), firstPicture)
      .jsonObject
    listOf("subjectId", "pictureKind", "label").forEach {
      assertFalse(it in firstJson, "$it in $firstJson")
    }
  }

  @Test
  fun `a verdict with no regions writes none`() {
    val verdict =
      GuidelineVerdictV1.Builder("wear.touch-target-48dp", GuidelineVerdictV1.PASS).build()
    val json = Json {
      encodeDefaults = true
    }
      .encodeToJsonElement(GuidelineVerdictV1.serializer(), verdict)
      .jsonObject
    assertFalse("regions" in json, json.toString())
  }
}
