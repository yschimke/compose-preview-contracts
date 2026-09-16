package ee.schimke.composeai.data.layoutinspector

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PreviewSlotsPayload] is constructed by consumers in other repositories from a compiled artifact,
 * so its constructor is `internal` and `@ConsistentCopyVisibility` takes the generated `copy` with
 * it: adding a property to a data class removes the old `<init>` and `copy$default` signatures, and
 * a consumer compiled against the previous release dies at its own call site. Neither is reachable
 * now, so neither can break. The Builder is what construction goes through instead.
 */
class PreviewSlotsPayloadBuilderTest {

  @Test
  fun `newBuilder round-trips every property`() {
    // A property added to the class and forgotten in `newBuilder` fails here rather than being
    // silently dropped on the way to a consumer.
    val payload =
      PreviewSlotsPayload.Builder(previewId = "com.example.Filled", slots = emptyList()).build()

    assertEquals(payload, payload.newBuilder().build())
    assertEquals("com.example.Filled", payload.previewId)
    assertEquals(emptyList<PreviewSlot>(), payload.slots)
  }

  @Test
  fun `the builder derives a modified payload without touching the original`() {
    val original = PreviewSlotsPayload.Builder("com.example.Filled", emptyList()).build()
    val derived = original.newBuilder().also { it.previewId = "com.example.Outlined" }.build()

    assertEquals("com.example.Filled", original.previewId)
    assertEquals("com.example.Outlined", derived.previewId)
  }
}
