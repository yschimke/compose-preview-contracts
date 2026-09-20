package ee.schimke.composeai.uibuilder.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class CanvasAdapterMappingV1Test {
  private val json = Json { encodeDefaults = false }

  @Test
  fun `a canvas adapter can normalize names and defaults without changing the component`() {
    val mapping =
      CanvasAdapterMappingV1.Builder()
        .also {
          it.properties = mapOf("fontSizeSp" to "fontSize")
          it.slots = mapOf("content" to "label")
          it.defaults = JsonObject(mapOf("variant" to JsonPrimitive("outlined")))
        }
        .build()
    val capability =
      WasmCapabilityV1.Builder(
          platformSupported = JsonPrimitive(true),
          adapterStatus = WasmAdapterStatusV1.SUPPORTED,
        )
        .also {
          it.canvas = "wear-m3/button"
          it.canvasMapping = mapping
        }
        .build()

    val encoded = json.encodeToString(WasmCapabilityV1.serializer(), capability)
    val decoded = json.decodeFromString(WasmCapabilityV1.serializer(), encoded)

    assertEquals("wear-m3/button", decoded.canvas)
    assertEquals("fontSize", decoded.canvasMapping?.properties?.get("fontSizeSp"))
    assertEquals("label", decoded.canvasMapping?.slots?.get("content"))
    assertEquals(JsonPrimitive("outlined"), decoded.canvasMapping?.defaults?.get("variant"))
  }
}
