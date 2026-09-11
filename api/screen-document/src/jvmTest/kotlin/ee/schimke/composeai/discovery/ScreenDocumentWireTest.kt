package ee.schimke.composeai.discovery

import kotlin.test.*
import kotlinx.serialization.json.*

class ScreenDocumentWireTest {
  @Test
  fun `legacy document retains its exact JSON and defaults`() {
    val wire =
      """{"name":"Legacy","root":{"componentId":"text","arguments":{"text":{"type":"ee.schimke.composeai.discovery.ScreenValue.Text","value":"Hello"}}}}"""
    val document = Json.decodeFromString<ScreenDocument>(wire)
    assertEquals(emptyList(), document.state)
    assertEquals(emptyList(), document.functions)
    assertNull(document.root.selection)
    assertNull(document.root.repetition)
    assertNull(document.root.function)
    assertEquals(wire, Json.encodeToString(document))
  }

  @Test
  fun `value discriminators retain the original generator package`() {
    val values =
      linkedMapOf(
        "Text" to ScreenValue.Text("Hello"),
        "Bool" to ScreenValue.Bool(true),
        "Whole" to ScreenValue.Whole(7),
        "Fractional" to ScreenValue.Fractional(1.25),
        "Fractional32" to ScreenValue.Fractional32(1.25f),
        "Lambda" to ScreenValue.Lambda(ScreenValue.Whole(1)),
        "ActionLambda" to ScreenValue.ActionLambda(listOf(ScreenAction.Toggle("enabled"))),
        "Reference" to ScreenValue.Reference("example.Theme", listOf("color"), "example.Color"),
        "Construct" to
          ScreenValue.Construct(
            "example.Color",
            listOf(ScreenValue.Whole(1)),
            typeFqn = "example.Color",
          ),
        "Chain" to
          ScreenValue.Chain(
            ScreenValue.Reference("example.Modifier", typeFqn = "example.Modifier"),
            listOf(ChainLink("example.padding", listOf(ScreenValue.Whole(8)))),
            "example.Modifier",
          ),
        "StateRead" to ScreenValue.StateRead("page", "kotlin.Int"),
        "RowRead" to ScreenValue.RowRead("id", "kotlin.Int"),
        "ParameterRead" to ScreenValue.ParameterRead("caption", "kotlin.String"),
      )
    for ((kind, value) in values) {
      val wire = Json.encodeToJsonElement(ScreenValue.serializer(), value)
      assertEquals(
        "ee.schimke.composeai.discovery.ScreenValue.$kind",
        wire.jsonObject.getValue("type").jsonPrimitive.content,
      )
      assertEquals(value, Json.decodeFromJsonElement(ScreenValue.serializer(), wire))
    }
  }

  @Test
  fun `scoped screen structures and ordered actions round trip without implementation dependencies`() {
    val document =
      ScreenDocument(
        name = "Scoped",
        state = listOf(ScreenState("page", "kotlin.Int", ScreenValue.Whole(10))),
        root =
          ScreenNode(
            "",
            repetition =
              ScreenRepetition(
                mapOf("id" to "kotlin.Int"),
                listOf(mapOf("id" to ScreenValue.Whole(20))),
              ),
            slots =
              mapOf(
                "body" to
                  listOf(
                    ScreenNode(
                      "",
                      function = "Choice",
                      arguments = mapOf("id" to ScreenValue.RowRead("id", "kotlin.Int")),
                      handlers =
                        mapOf(
                          "select" to
                            listOf(
                              ScreenAction.Set("page", ScreenValue.RowRead("id", "kotlin.Int")),
                              ScreenAction.Set("page", ScreenValue.Whole(10)),
                            )
                        ),
                    )
                  )
              ),
          ),
        functions =
          listOf(
            ScreenFunction(
              "Choice",
              listOf(ScreenParameter.Value("id", "kotlin.Int"), ScreenParameter.Callback("select")),
              ScreenNode(
                "",
                selection =
                  ScreenSelection(
                    ScreenValue.ParameterRead("id", "kotlin.Int"),
                    mapOf("selected" to ScreenValue.Whole(20)),
                  ),
                slots =
                  mapOf(
                    "selected" to
                      listOf(
                        ScreenNode(
                          "button",
                          arguments =
                            mapOf(
                              "onClick" to ScreenValue.ParameterRead("select", "kotlin.Function0")
                            ),
                        )
                      )
                  ),
              ),
            )
          ),
      )
    val wire = Json.encodeToString(document)
    assertEquals(document, Json.decodeFromString<ScreenDocument>(wire))
    assertTrue("ee.schimke.composeai.discovery.ScreenParameter.Callback" in wire)
    assertTrue("ee.schimke.composeai.discovery.ScreenAction.Set" in wire)
  }
}
