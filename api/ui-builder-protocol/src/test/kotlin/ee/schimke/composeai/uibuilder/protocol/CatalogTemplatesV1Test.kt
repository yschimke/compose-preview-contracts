package ee.schimke.composeai.uibuilder.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogTemplatesV1Test {
  private val json = Json { encodeDefaults = true }
  private val pin = CatalogReferenceV1("custom-kit", "revision-7", "digest-7", "runtime-7")

  @Test
  fun `the version marker is emitted even with default encoding disabled`() {
    val encoded = Json.encodeToString(CatalogTemplateDocumentsV1.Builder(pin).build())
    assertTrue("\"schemaVersion\":1" in encoded, encoded)
  }

  @Test
  fun `empty publications retain all four pin fields through JSON and builders`() {
    val publication = CatalogTemplateDocumentsV1.Builder(pin).build()
    val decoded =
      json.decodeFromString<CatalogTemplateDocumentsV1>(json.encodeToString(publication))
    assertEquals(1, decoded.schemaVersion)
    assertEquals(pin, decoded.catalogPin)
    assertTrue(decoded.templates.isEmpty())
    assertEquals(decoded, decoded.newBuilder().build())
  }

  @Test
  fun `a capability digest change distinguishes publications with the same revision and runtime`() {
    val original = CatalogTemplateDocumentsV1.Builder(pin).build()
    val changed =
      original.newBuilder().also { it.catalogPin = pin.copy(capabilityDigest = "digest-8") }.build()
    assertNotEquals(original.catalogPin, changed.catalogPin)
    assertEquals(pin.catalogRevision, changed.catalogPin.catalogRevision)
    assertEquals(pin.nativeRuntimeId, changed.catalogPin.nativeRuntimeId)
  }
}
