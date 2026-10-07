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
      ExpressionValueV1(
        "mul",
        listOf(
          ExpressionValueV1(
            "mod",
            listOf(SystemValueV1("time.secondOfHour"), IntegerValueV1(60)),
          ),
          StateValueV1("speed"),
        ),
      ),
      value,
    )
    assertEquals(
      json.parseToJsonElement(wire),
      json.encodeToJsonElement(UiValueV1.serializer(), value) as JsonElement,
    )
  }
}
