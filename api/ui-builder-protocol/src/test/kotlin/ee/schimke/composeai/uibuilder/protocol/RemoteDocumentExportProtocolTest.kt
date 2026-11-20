package ee.schimke.composeai.uibuilder.protocol

import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.Test

private data class Quad(
  val format: ExportFormatV1,
  val spelling: String,
  val encoding: ExportEncodingV1,
  val mediaType: String,
)

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

  @Test
  fun `watch face formats keep their spellings and travel as text or base64`() {
    for ((format, spelling, encoding, mediaType) in
      listOf(
        Quad(
          ExportFormatV1.WFF_XML,
          "wff-xml",
          ExportEncodingV1.UTF8,
          "application/xml",
        ),
        Quad(
          ExportFormatV1.WFF_BUNDLE,
          "wff-bundle",
          ExportEncodingV1.BASE64,
          "application/zip",
        ),
        Quad(
          ExportFormatV1.WFF_APK,
          "wff-apk",
          ExportEncodingV1.BASE64,
          "application/vnd.android.package-archive",
        ),
      )) {
      val request = ExportDesignRequestV1(designId = "face", revision = 7, format = format)
      val encoded = json.encodeToJsonElement(request).jsonObject
      assertEquals(JsonPrimitive(spelling), encoded["format"])
      assertEquals(request, json.decodeFromJsonElement<ExportDesignRequestV1>(encoded))
      val artifact = ExportArtifactV1(format, mediaType, encoding, "x", "digest")
      assertEquals(artifact, json.decodeFromString<ExportArtifactV1>(json.encodeToString(artifact)))
    }
  }

  @Test
  fun `a reader that predates the watch face formats rejects the name rather than guessing`() {
    // This is why a server answers a format only to a request that named it, and why support is a
    // capability that defaults to false: an old client never sees the new name.
    val wire =
      JsonObject(mapOf("designId" to JsonPrimitive("a"), "format" to JsonPrimitive("wff-xml")))
    assertEquals(
      ExportFormatV1.WFF_XML,
      json.decodeFromJsonElement<ExportDesignRequestV1>(wire).format,
    )
    val unknown = JsonObject(wire + ("format" to JsonPrimitive("wff-future")))
    assertFailsWith<SerializationException> {
      json.decodeFromJsonElement<ExportDesignRequestV1>(unknown)
    }
  }

  @Test
  fun `watch face capabilities default to absent and are advertised independently`() {
    val none = json.encodeToJsonElement(ExportCapabilitiesV1()).jsonObject
    assertNull(none["wffXml"])
    assertNull(none["wffBundle"])
    assertNull(none["wffApk"])
    val xmlOnly = ExportCapabilitiesV1(wffXml = true)
    val encoded = json.encodeToJsonElement(xmlOnly).jsonObject
    assertEquals(JsonPrimitive(true), encoded["wffXml"])
    assertNull(encoded["wffApk"])
    assertEquals(xmlOnly, json.decodeFromJsonElement<ExportCapabilitiesV1>(encoded))
    assertEquals(xmlOnly, xmlOnly.newBuilder().build())
  }

  @Test
  fun `kobento component json keeps its spelling and is gated on its own`() {
    val request =
      ExportDesignRequestV1(designId = "screen", revision = 7, format = ExportFormatV1.KOBENTO_JSON)
    val encoded = json.encodeToJsonElement(request).jsonObject
    assertEquals(JsonPrimitive("kobento-json"), encoded["format"])
    assertEquals(request, json.decodeFromJsonElement<ExportDesignRequestV1>(encoded))

    val artifact =
      ExportArtifactV1(
        ExportFormatV1.KOBENTO_JSON,
        "application/json",
        ExportEncodingV1.UTF8,
        "{}",
        "digest",
      )
    assertEquals(artifact, json.decodeFromString<ExportArtifactV1>(json.encodeToString(artifact)))

    val kobento = ExportCapabilitiesV1.Builder().apply { kobentoJson = true }.build()
    val advertised = json.encodeToJsonElement(kobento).jsonObject
    assertEquals(JsonPrimitive(true), advertised["kobentoJson"])
    assertNull(advertised["remoteJson"])
    assertNull(advertised["remoteDocument"])
    assertEquals(kobento, json.decodeFromJsonElement<ExportCapabilitiesV1>(advertised))
    assertTrue(kobento.newBuilder().build().kobentoJson)

    val old = """{"composeCode":false,"svg":false,"png":false,"bundle":false}"""
    assertFalse(json.decodeFromString<ExportCapabilitiesV1>(old).kobentoJson)
  }
}
