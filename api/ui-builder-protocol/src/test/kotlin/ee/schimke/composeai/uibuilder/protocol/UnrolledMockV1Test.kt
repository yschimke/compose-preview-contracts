package ee.schimke.composeai.uibuilder.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * `WasmCapabilityV1.unrolled`: the layout a catalog asks the editing canvas to draw for a component
 * while it is being edited.
 *
 * Three properties are the contract's whole job here, and each is asserted rather than assumed: a
 * declared mock survives a round trip with its layout and dimensions; a component that declares
 * none carries no field and an older payload parses as none (the field is optional, so a catalog
 * adopts it one component at a time); and the layout **word** is carried faithfully even when this
 * build has never heard of it, because the vocabulary belongs to the builder's registry exactly as
 * a `canvas` adapter name does.
 */
class UnrolledMockV1Test {

  private val json = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = false
  }

  @Test
  fun `a declared mock round-trips with its layout and dimensions`() {
    val capability =
      WasmCapabilityV1(
        platformSupported = JsonPrimitive(true),
        adapterStatus = WasmAdapterStatusV1.SUPPORTED,
        unrolled =
          UnrolledMockV1(
            layout = "wrap",
            cellWidthDp = JsonPrimitive(190),
            spacingDp = JsonPrimitive(4),
          ),
      )

    val text = json.encodeToString(WasmCapabilityV1.serializer(), capability)

    assertTrue("unrolled" in text, text)
    assertEquals(capability, json.decodeFromString(WasmCapabilityV1.serializer(), text))
  }

  @Test
  fun `a component with no mock carries no field, and an older payload parses as none`() {
    val capability =
      WasmCapabilityV1(
        platformSupported = JsonPrimitive(true),
        adapterStatus = WasmAdapterStatusV1.SUPPORTED,
      )

    val text = json.encodeToString(WasmCapabilityV1.serializer(), capability)

    assertFalse("unrolled" in text, text)
    // What every catalog served before this field existed sends.
    val older = """{"platformSupported":true,"adapterStatus":"supported"}"""
    assertNull(json.decodeFromString(WasmCapabilityV1.serializer(), older).unrolled)
  }

  @Test
  fun `a layout this build does not know is carried, not refused`() {
    val text =
      """{"platformSupported":true,"adapterStatus":"supported",""" +
        """"unrolled":{"layout":"carousel-of-cards"}}"""

    assertEquals(
      "carousel-of-cards",
      json.decodeFromString(WasmCapabilityV1.serializer(), text).unrolled?.layout,
    )
  }
}
