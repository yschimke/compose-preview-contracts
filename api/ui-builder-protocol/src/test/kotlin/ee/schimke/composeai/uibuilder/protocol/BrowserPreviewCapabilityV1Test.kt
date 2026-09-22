package ee.schimke.composeai.uibuilder.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class BrowserPreviewCapabilityV1Test {
  private val json = Json { encodeDefaults = false }

  @Test
  fun `a catalog can request an exported document preview`() {
    val catalog =
      catalog()
        .newBuilder()
        .also {
          it.browserPreview =
            BrowserPreviewCapabilityV1.Builder(
                BrowserPreviewCapabilityV1.REMOTE_COMPOSE_DOCUMENT_RENDERER
              )
              .also { preview -> preview.format = ExportFormatV1.RC }
              .build()
        }
        .build()

    val encoded = json.encodeToString(CatalogCapabilityV1.serializer(), catalog)
    val decoded = json.decodeFromString(CatalogCapabilityV1.serializer(), encoded)

    assertEquals(
      BrowserPreviewCapabilityV1.REMOTE_COMPOSE_DOCUMENT_RENDERER,
      decoded.browserPreview?.renderer,
    )
    assertEquals(ExportFormatV1.RC, decoded.browserPreview?.format)
  }

  @Test
  fun `an older catalog keeps the canvas preview`() {
    val encoded = json.encodeToString(CatalogCapabilityV1.serializer(), catalog())

    assertNull(json.decodeFromString(CatalogCapabilityV1.serializer(), encoded).browserPreview)
  }

  @Test
  fun `a catalog can declare its versioned Compose source adapter`() {
    val catalog =
      catalog()
        .newBuilder()
        .also {
          it.composeSourceExport =
            ComposeSourceExportCapabilityV1.Builder("compose-material3", 1).build()
        }
        .build()

    val encoded = json.encodeToString(CatalogCapabilityV1.serializer(), catalog)
    val decoded = json.decodeFromString(CatalogCapabilityV1.serializer(), encoded)

    assertEquals("compose-material3", decoded.composeSourceExport?.adapter)
    assertEquals(1, decoded.composeSourceExport?.version)
  }

  private fun catalog(): CatalogCapabilityV1 =
    CatalogCapabilityV1.Builder(
        schema = "compose-ui-builder-capability/v1",
        benchmark =
          CatalogBenchmarkV1.Builder(
              id = "fixture",
              sourceRevision = "source",
              catalogSystemId = "fixture",
              catalogRevision = "revision",
              nativeRuntimeId = "runtime",
            )
            .build(),
        components = emptyList(),
      )
      .also { it.statusSemantics = JsonObject(mapOf("platform" to JsonPrimitive("fixture"))) }
      .build()
}
