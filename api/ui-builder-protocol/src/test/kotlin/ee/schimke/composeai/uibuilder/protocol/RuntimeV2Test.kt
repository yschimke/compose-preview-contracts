package ee.schimke.composeai.uibuilder.protocol

import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class RuntimeV2Test {
  private val json = Json { encodeDefaults = true }

  @Test
  fun `render document carries an explicit authoring surface`() {
    val payload =
      UiBuilderRendererRenderDocumentV2(
        document = JsonObject(mapOf("id" to JsonPrimitive("design"))),
        surface =
          UiBuilderRendererSurfaceV2(
            mode = UiBuilderRendererSurfaceModeV2.AUTHORING_UNROLLED,
            widthDp = 192f,
            heightDp = 354f,
            density = 2f,
            surfaceId = "editor",
          ),
      )
    val message =
      UiBuilderRendererMessageV2(
        runtimeId = "wear-m3-p2-abcd",
        requestId = "render-1",
        type = "renderDocument",
        payload =
          json
            .encodeToJsonElement(UiBuilderRendererRenderDocumentV2.serializer(), payload)
            .jsonObject,
      )

    val encoded = json.encodeToString(UiBuilderRendererMessageV2.serializer(), message)
    val decoded = json.decodeFromString(UiBuilderRendererMessageV2.serializer(), encoded)
    val decodedPayload =
      json.decodeFromJsonElement(UiBuilderRendererRenderDocumentV2.serializer(), decoded.payload)

    assertEquals(message, decoded)
    assertEquals(payload, decodedPayload)
    assertEquals(UI_BUILDER_RENDERER_PROTOCOL_SCHEMA_V2, decoded.schema)
    assertEquals(UI_BUILDER_RENDERER_PROTOCOL_VERSION_V2, decoded.protocolVersion)
    assertEquals(UiBuilderRendererSurfaceModeV2.AUTHORING_UNROLLED, decodedPayload.surface.mode)
  }

  @Test
  fun `v1 remains independently decodable`() {
    val v1 =
      UiBuilderRendererMessageV1(
        runtimeId = "wear-m3-p1-abcd",
        requestId = "render-1",
        type = "renderDocument",
      )

    val decoded =
      json.decodeFromString(UiBuilderRendererMessageV1.serializer(), json.encodeToString(v1))

    assertEquals(UI_BUILDER_RENDERER_PROTOCOL_SCHEMA_V1, decoded.schema)
    assertEquals(UI_BUILDER_RENDERER_PROTOCOL_VERSION_V1, decoded.protocolVersion)
  }
}
