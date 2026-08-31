package ee.schimke.composeai.uibuilder.protocol

import java.io.File
import kotlin.test.assertFailsWith
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class UiBuilderProtocolCompatibilityTest {
  private val strictJson = Json {
    classDiscriminator = "type"
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = false
  }

  private val fixtures: Map<String, KSerializer<Any>> =
    mapOf(
      fixtureSerializer("catalog.json", CatalogCapabilityV1.serializer()),
      fixtureSerializer("design-state.json", DesignStateV1.serializer()),
      fixtureSerializer("http-commands-request.json", HttpRequestEnvelopeV1.serializer()),
      fixtureSerializer("http-conflict-response.json", HttpResponseEnvelopeV1.serializer()),
      fixtureSerializer("service-delta.json", ServiceDeltaV1.serializer()),
      fixtureSerializer("mcp-export-request.json", McpRequestEnvelopeV1.serializer()),
      fixtureSerializer("mcp-export-response.json", McpResponseEnvelopeV1.serializer()),
      fixtureSerializer("session-presence.json", SessionUpdateEnvelopeV1.serializer()),
    )

  @Test
  fun everyV1FixtureStrictlyRoundTrips() {
    fixtures.forEach { (name, serializer) ->
      val original = strictJson.parseToJsonElement(fixture(name))
      val decoded = strictJson.decodeFromJsonElement(serializer, original)
      val encoded = strictJson.encodeToJsonElement(serializer, decoded)
      assertEquals("round-trip mismatch for $name", original, encoded)
    }
  }

  @Test
  fun fixtureInventoryIsExplicit() {
    val present =
      fixturesDir().listFiles().orEmpty().filter { it.extension == "json" }.map { it.name }.toSet()
    assertEquals(fixtures.keys, present)
  }

  @Test
  fun unknownFieldsAreRejectedByStrictReaders() {
    val original = strictJson.parseToJsonElement(fixture("mcp-export-request.json")) as JsonObject
    val changed = JsonObject(original + ("futureField" to JsonPrimitive(true)))
    assertFailsWith<SerializationException> {
      strictJson.decodeFromJsonElement(McpRequestEnvelopeV1.serializer(), changed)
    }
  }

  @Test
  fun requiredEnvelopeFieldsCannotDisappear() {
    assertFailsWith<SerializationException> {
      strictJson.decodeFromString(
        HttpRequestEnvelopeV1.serializer(),
        """{"schemaVersion":1,"requestId":"r","request":{"type":"listCatalogs"}}""",
      )
    }
  }

  @Test
  fun schemaVersionIsEncodedEvenWhenDefaultsAreDisabled() {
    val encoded =
      strictJson.encodeToJsonElement(
        McpRequestEnvelopeV1.serializer(),
        McpRequestEnvelopeV1(
          callId = "call",
          actorId = "actor",
          request = ListCatalogsRequestV1,
        ),
      ) as JsonObject
    assertEquals(JsonPrimitive(UI_BUILDER_SCHEMA_VERSION_V1), encoded["schemaVersion"])
  }

  private fun fixture(name: String): String = File(fixturesDir(), name).readText()

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> fixtureSerializer(
    name: String,
    serializer: KSerializer<T>,
  ): Pair<String, KSerializer<Any>> = name to serializer as KSerializer<Any>

  private fun fixturesDir(): File {
    var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (directory != null) {
      val candidate = File(directory, "docs/ui-builder/protocol-fixtures/v1")
      if (candidate.isDirectory) return candidate
      directory = directory.parentFile
    }
    error("could not locate UI builder v1 fixtures from ${System.getProperty("user.dir")}")
  }
}
