package ee.schimke.composeai.parityissues.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ParityIssuesCompatibilityTest {
  private val json = Json { encodeDefaults = true }

  @Test
  fun `an older row without scope remains component wide`() {
    val issue =
      Json.decodeFromString<ParityIssue>(
        """{"repository":"o/r","number":1,"title":"x","url":"https://github.com/o/r/issues/1","state":"open"}"""
      )

    assertEquals(ParityIssue.COMPONENT_SCOPE, issue.scope)
  }

  @Test
  fun `variant scope round trips in the published artifact`() {
    val issue =
      ParityIssue(
        repository = "o/r",
        number = 2,
        title = "dark only",
        url = "https://github.com/o/r/issues/2",
        state = "open",
        component = "Button/Filled",
        scope = ParityIssue.VARIANT_SCOPE,
        previewIds = listOf("button__ideal__default__dark"),
      )

    assertEquals(issue, json.decodeFromString<ParityIssue>(json.encodeToString(issue)))
  }
}
