package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Protocol v2 makes the requested render surface explicit. */
public const val UI_BUILDER_RENDERER_PROTOCOL_SCHEMA_V2: String = "compose-ui-builder-renderer/v2"

public const val UI_BUILDER_RENDERER_PROTOCOL_VERSION_V2: Int = 2

/** Why and where one document render is being requested. */
@Serializable
public data class UiBuilderRendererSurfaceV2(
  public val mode: UiBuilderRendererSurfaceModeV2,
  public val widthDp: Float,
  public val heightDp: Float,
  public val density: Float,
  public val surfaceId: String,
)

@Serializable
public enum class UiBuilderRendererSurfaceModeV2 {
  @SerialName("authoring-unrolled") AUTHORING_UNROLLED,
  @SerialName("device") DEVICE,
}

/** Typed payload of a protocol-v2 `renderDocument` request. */
@Serializable
public data class UiBuilderRendererRenderDocumentV2(
  /**
   * The persisted UI-builder document, retained as JSON to keep this contract implementation-free.
   */
  public val document: JsonObject,
  public val surface: UiBuilderRendererSurfaceV2,
)

/** Envelope exchanged with a renderer that requires an explicit [UiBuilderRendererSurfaceV2]. */
@Serializable
public data class UiBuilderRendererMessageV2(
  public val schema: String = UI_BUILDER_RENDERER_PROTOCOL_SCHEMA_V2,
  public val protocolVersion: Int = UI_BUILDER_RENDERER_PROTOCOL_VERSION_V2,
  public val runtimeId: String,
  public val requestId: String,
  public val type: String,
  public val payload: JsonObject = JsonObject(emptyMap()),
)
