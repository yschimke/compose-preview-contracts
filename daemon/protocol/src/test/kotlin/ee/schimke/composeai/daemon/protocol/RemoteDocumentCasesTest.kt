package ee.schimke.composeai.daemon.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

/** The cases file's wire shape: what a producer writes and `render-cases` reads. */
class RemoteDocumentCasesTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `a producer's file reads with the daemon's own value and profile names`() {
    val file =
      json.decodeFromString(
        RemoteDocumentCasesV1.serializer(),
        """
        { "schema": "compose-preview/remote-document-cases/v1",
          "cases": [ {
            "id": "banner/in-app/default/dark", "document": "documents/banner/in-app.rc",
            "widthDp": 360, "heightDp": 160, "dark": true, "profile": "androidx7",
            "background": "#FF121212",
            "namedValues": {
              "headline": { "kind": "string", "value": "Hello" },
              "theme.primary": { "kind": "color", "argb": "#FF6750A4" },
              "level": { "kind": "float", "value": 0.5 }
            },
            "design": "a producer's own field, ignored"
          } ] }
        """,
      )
    assertEquals(RemoteDocumentCasesV1.SCHEMA, file.schema)
    val case = file.cases.single()
    assertEquals("documents/banner/in-app.rc", case.document)
    assertEquals(RemoteComposeProfile.ANDROIDX7, case.profile)
    assertEquals(true, case.dark)
    assertEquals("#FF121212", case.background)
    assertEquals(RemoteNamedValue.ColorValue("#FF6750A4"), case.namedValues["theme.primary"])
    assertEquals(RemoteNamedValue.FloatValue(0.5f), case.namedValues["level"])
  }

  @Test
  fun `a built file round-trips, leaving out what it doesn't set`() {
    val case =
      RemoteDocumentCaseV1.Builder("tile/launcher/on", "documents/tile/launcher.rc", 200, 100)
        .apply {
          profile = RemoteComposeProfile.WIDGETS_V7
          namedValues = mapOf("is_on" to RemoteNamedValue.BooleanValue(true))
        }
        .build()
    val file = RemoteDocumentCasesV1.Builder(listOf(case)).build()
    val text = Json.encodeToString(RemoteDocumentCasesV1.serializer(), file)
    assertEquals(false, "\"dark\"" in text || "\"background\"" in text, text)
    assertEquals(true, RemoteDocumentCasesV1.SCHEMA in text, text)
    val back = Json.decodeFromString(RemoteDocumentCasesV1.serializer(), text)
    assertEquals(file, back)
    assertNull(back.cases.single().dark)
    assertEquals(case, case.newBuilder().build())
  }
}
