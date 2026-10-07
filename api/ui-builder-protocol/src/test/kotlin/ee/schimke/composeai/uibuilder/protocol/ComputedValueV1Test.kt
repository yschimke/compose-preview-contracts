package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

/** Computed values written by the builder survive the wire unchanged, nested trees included. */
class ComputedValueV1Test {
  private val json = Json { classDiscriminator = "type" }

  @Test
  fun expressionsAndSystemValuesRoundTrip() {
    val wire =
      """{"type":"expr","op":"mul","args":[{"type":"expr","op":"mod","args":""" +
        """[{"type":"system","value":"time.secondOfHour"},{"type":"int","value":60}]},""" +
        """{"type":"state","variable":"speed"}]}"""

    val value = json.decodeFromString(UiValueV1.serializer(), wire)

    assertEquals(
      ExpressionValueV1.Builder("mul")
        .apply {
          args =
            listOf(
              ExpressionValueV1.Builder("mod")
                .apply { args = listOf(SystemValueV1("time.secondOfHour"), IntegerValueV1(60)) }
                .build(),
              StateValueV1("speed"),
            )
        }
        .build(),
      value,
    )
    assertEquals(
      json.parseToJsonElement(wire),
      json.encodeToJsonElement(UiValueV1.serializer(), value) as JsonElement,
    )
  }

  @Test
  fun aRemoteCallModifierCarriesComputedArguments() {
    val wire =
      """{"type":"remoteCall","name":"graphicsLayer","args":{"rotationZ":""" +
        """{"type":"expr","op":"mul","args":[{"type":"state","variable":"turn"},""" +
        """{"type":"int","value":360}]},"alpha":{"type":"float","value":0.5}}}"""

    val modifier = json.decodeFromString(DesignModifierV1.serializer(), wire)

    val call = modifier as RemoteCallModifierV1
    assertEquals("graphicsLayer", call.name)
    assertEquals(DecimalValueV1(0.5), call.args["alpha"])
    assertEquals(
      json.parseToJsonElement(wire),
      json.encodeToJsonElement(DesignModifierV1.serializer(), modifier),
    )
  }
}
