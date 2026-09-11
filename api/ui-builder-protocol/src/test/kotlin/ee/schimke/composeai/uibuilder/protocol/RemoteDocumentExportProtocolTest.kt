package ee.schimke.composeai.uibuilder.protocol

import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Test

class RemoteDocumentExportProtocolTest {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
  }

  @Test
  fun `old capabilities do not acquire new false fields`() {
    val old = """{"composeCode":true,"svg":false,"png":true,"bundle":false}"""
    val capabilities = json.decodeFromString<ExportCapabilitiesV1>(old)
    assertFalse(capabilities.remoteJson)
    assertFalse(capabilities.remoteDocument)
    assertEquals(Json.parseToJsonElement(old), json.encodeToJsonElement(capabilities))
  }

  @Test
  fun `new exports preserve revision and content encoding on the wire`() {
    for ((format, spelling, encoding) in
      listOf(
        Triple(ExportFormatV1.JSON, "json", ExportEncodingV1.UTF8),
        Triple(ExportFormatV1.RC, "rc", ExportEncodingV1.BASE64),
      )) {
      val request = ExportDesignRequestV1(designId = "screen", revision = 42, format = format)
      val encoded = json.encodeToJsonElement(request).jsonObject
      assertEquals(JsonPrimitive(spelling), encoded["format"])
      assertEquals(request, json.decodeFromJsonElement<ExportDesignRequestV1>(encoded))
      val artifact =
        ExportArtifactV1(
          format,
          if (format == ExportFormatV1.JSON) "application/json" else "application/octet-stream",
          encoding,
          "content",
          "digest",
        )
      assertEquals(artifact, json.decodeFromString<ExportArtifactV1>(json.encodeToString(artifact)))
    }
  }

  @Test
  fun `source and binary capabilities are independently advertised`() {
    val sourceOnly = ExportCapabilitiesV1(remoteJson = true)
    val encoded = json.encodeToJsonElement(sourceOnly).jsonObject
    assertEquals(JsonPrimitive(true), encoded["remoteJson"])
    assertNull(encoded["remoteDocument"])
    assertEquals(sourceOnly, json.decodeFromJsonElement<ExportCapabilitiesV1>(encoded))
  }
}
