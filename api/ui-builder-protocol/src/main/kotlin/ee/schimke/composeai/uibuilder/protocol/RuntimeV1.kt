package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Name of the manifest at the root of every catalog-owned renderer distribution. */
public const val UI_BUILDER_RUNTIME_MANIFEST_NAME_V1: String = "runtime-manifest.json"

/** Strict schema declared by [UiBuilderRuntimeManifestV1]. */
public const val UI_BUILDER_RUNTIME_MANIFEST_SCHEMA_V1: String = "compose-ui-builder-runtime/v1"

/** Moving path used by one catalog delivery generation. Immutable revisions preserve old bytes. */
public const val UI_BUILDER_RUNTIME_ARTIFACT_PATH_V1: String = "ui-builder/runtime.zip"

/** SHA-256 is applied to the framing emitted by [frameUiBuilderRuntimeTreeIntegrityV1]. */
public const val UI_BUILDER_RUNTIME_INTEGRITY_ALGORITHM_V1: String = "SHA-256"

/** Schema and protocol version spoken across the catalog renderer's opaque-origin iframe. */
public const val UI_BUILDER_RENDERER_PROTOCOL_SCHEMA_V1: String = "compose-ui-builder-renderer/v1"

public const val UI_BUILDER_RENDERER_PROTOCOL_VERSION_V1: Int = 1

/** Schema used by the revision-bound inspection payload in renderer responses. */
public const val UI_BUILDER_RENDERER_INSPECTION_SCHEMA_V1: String =
  "compose-ui-builder-inspection/v1"

/** Runtime metadata stored inside the verified ZIP. */
@Serializable
public data class UiBuilderRuntimeManifestV1(
  public val schema: String = UI_BUILDER_RUNTIME_MANIFEST_SCHEMA_V1,
  public val runtimeId: String,
  public val protocolVersion: Int,
  public val entrypoint: String,
  public val integritySha256: String,
)

/** Runtime declaration embedded in a catalog delivery manifest. */
@Serializable
public data class UiBuilderRuntimeArtifactV1(
  public val path: String = UI_BUILDER_RUNTIME_ARTIFACT_PATH_V1,
  public val runtimeId: String,
  public val protocolVersion: Int,
  public val integritySha256: String,
)

/** Exact runtime identity bound to an immutable catalog generation after installation. */
@Serializable
public data class UiBuilderRuntimeDescriptorV1(
  public val catalogSystemId: String,
  public val catalogRevision: String,
  public val capabilityDigest: String,
  public val runtimeId: String,
  public val protocolVersion: Int,
  /** Exact server path for this installed runtime, never a `current` or `latest` alias. */
  public val assetRoot: String,
  public val integritySha256: String,
  public val lifecycle: UiBuilderRuntimeLifecycleV1 = UiBuilderRuntimeLifecycleV1.RETAINED,
)

@Serializable
public enum class UiBuilderRuntimeLifecycleV1 {
  RETAINED,
  DEPRECATED,
  RETIRED,
}

/** Stable, machine-actionable problem in a runtime wire record. */
@Serializable
public data class UiBuilderRuntimeValidationIssueV1(
  public val field: String,
  public val code: String,
)

/**
 * Validates only the shared wire invariants; archive limits and retention policy remain host-owned.
 */
public fun UiBuilderRuntimeManifestV1.validateContract(
  assetPaths: Set<String>,
  actualIntegritySha256: String? = null,
): List<UiBuilderRuntimeValidationIssueV1> = buildList {
  if (schema != UI_BUILDER_RUNTIME_MANIFEST_SCHEMA_V1) addIssue("schema", "unsupported")
  if (!isValidUiBuilderRuntimeIdV1(runtimeId)) addIssue("runtimeId", "unsafeOrReserved")
  if (protocolVersion <= 0) addIssue("protocolVersion", "notPositive")
  if (normalizeUiBuilderRuntimeAssetPathV1(entrypoint) != entrypoint) {
    addIssue("entrypoint", "unsafe")
  } else if (entrypoint !in assetPaths) {
    addIssue("entrypoint", "missing")
  }
  if (!isValidUiBuilderRuntimeSha256V1(integritySha256)) {
    addIssue("integritySha256", "invalid")
  } else if (actualIntegritySha256 != null && integritySha256 != actualIntegritySha256) {
    addIssue("integritySha256", "mismatch")
  }
}

/** Validates the descriptor emitted beside a catalog generation. */
public fun UiBuilderRuntimeArtifactV1.validateContract(): List<UiBuilderRuntimeValidationIssueV1> =
  buildList {
    if (path != UI_BUILDER_RUNTIME_ARTIFACT_PATH_V1) addIssue("path", "unsupported")
    validateIdentity(runtimeId, protocolVersion, integritySha256)
  }

/** Validates the immutable catalog-to-runtime binding, not server installation or lease policy. */
public fun UiBuilderRuntimeDescriptorV1.validateContract():
  List<UiBuilderRuntimeValidationIssueV1> = buildList {
  if (catalogSystemId.isBlank()) addIssue("catalogSystemId", "blank")
  if (catalogRevision.isBlank()) addIssue("catalogRevision", "blank")
  if (capabilityDigest.isBlank()) addIssue("capabilityDigest", "blank")
  validateIdentity(runtimeId, protocolVersion, integritySha256)
  if (assetRoot != "/ui-builder/runtime/$runtimeId/") addIssue("assetRoot", "notVersionAddressed")
}

public fun isValidUiBuilderRuntimeIdV1(value: String): Boolean =
  value.isNotEmpty() &&
    value != "current" &&
    value != "latest" &&
    value.all { (it.isLetterOrDigit() && it.code < 128) || it == '.' || it == '_' || it == '-' }

public fun isValidUiBuilderRuntimeSha256V1(value: String): Boolean =
  value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

/** Returns the canonical relative asset path, or null when [path] is unsafe. */
public fun normalizeUiBuilderRuntimeAssetPathV1(path: String): String? {
  if (path.isEmpty() || path.startsWith('/') || '\\' in path || '\u0000' in path) return null
  val segments = path.split('/')
  if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
  return segments.joinToString("/")
}

/**
 * Emits the canonical tree-integrity bytes to [update].
 *
 * Assets are ordered by unsigned UTF-8 bytes. Each is framed as `pathUtf8, NUL,
 * decimalByteLengthUtf8, NUL, content`; the manifest itself is forbidden because it contains the
 * resulting digest. The caller applies [UI_BUILDER_RUNTIME_INTEGRITY_ALGORITHM_V1] to the emitted
 * chunks.
 */
public fun frameUiBuilderRuntimeTreeIntegrityV1(
  assets: Map<String, ByteArray>,
  update: (ByteArray) -> Unit,
) {
  assets.entries
    .map { entry -> entry to entry.key.encodeToByteArray() }
    .sortedWith { left, right -> compareUnsigned(left.second, right.second) }
    .forEach { (entry, pathBytes) ->
      require(
        normalizeUiBuilderRuntimeAssetPathV1(entry.key) == entry.key &&
          entry.key != UI_BUILDER_RUNTIME_MANIFEST_NAME_V1
      ) {
        "Unsafe runtime asset path '${entry.key}'"
      }
      update(pathBytes)
      update(byteArrayOf(0))
      update(entry.value.size.toString().encodeToByteArray())
      update(byteArrayOf(0))
      update(entry.value)
    }
}

/** Envelope exchanged between the editor host and a catalog renderer iframe. */
@Serializable
public data class UiBuilderRendererMessageV1(
  public val schema: String = UI_BUILDER_RENDERER_PROTOCOL_SCHEMA_V1,
  public val protocolVersion: Int = UI_BUILDER_RENDERER_PROTOCOL_VERSION_V1,
  public val runtimeId: String,
  public val requestId: String,
  public val type: String,
  public val payload: JsonObject = JsonObject(emptyMap()),
)

/** Semantic interaction request carried by a `dispatchAction` renderer message. */
@Serializable
public data class UiBuilderRendererActionV1(
  public val documentId: String,
  public val documentRevision: Int,
  public val nodeId: String,
  public val kind: String,
  public val deltaX: Double? = null,
  public val deltaY: Double? = null,
)

/** Revision-bound inspection returned after a render or semantic action. */
@Serializable
public data class UiBuilderRendererInspectionV1(
  public val schema: String = UI_BUILDER_RENDERER_INSPECTION_SCHEMA_V1,
  public val documentId: String,
  public val documentRevision: Int,
  public val coordinateSpace: String = "root-render-pixels",
  public val coordinatePrecision: String = "1/64px",
  public val generation: UiBuilderRendererInspectionGenerationV1,
  public val nodes: List<UiBuilderRendererNodeInspectionV1>,
  public val slots: List<UiBuilderRendererSlotInspectionV1>,
)

@Serializable
public data class UiBuilderRendererInspectionGenerationV1(
  public val key: String,
  public val completed: Boolean = false,
  public val stabilityFrames: Int = 2,
  public val expectedAuthoredNodeIds: List<String>,
  public val expectedAuthoredTextNodeIds: List<String>,
  public val measuredNodeIds: List<String>,
  public val measuredTextNodeIds: List<String>,
)

@Serializable
public data class UiBuilderRendererNodeInspectionV1(
  public val nodeId: String,
  public val componentId: String,
  public val bounds: UiBuilderRendererPixelBoundsV1? = null,
  public val text: UiBuilderRendererTextInspectionV1? = null,
  public val semantics: UiBuilderRendererSemanticsInspectionV1,
)

@Serializable
public data class UiBuilderRendererSlotInspectionV1(
  public val parentNodeId: String,
  public val slotName: String,
  public val childNodeIds: List<String>,
  public val measuredChildNodeIds: List<String>,
  public val bounds: UiBuilderRendererPixelBoundsV1? = null,
)

@Serializable
public data class UiBuilderRendererPixelBoundsV1(
  public val x: Float,
  public val y: Float,
  public val width: Float,
  public val height: Float,
)

@Serializable
public data class UiBuilderRendererTextInspectionV1(
  public val text: String,
  public val lineCount: Int,
  public val firstBaselineY: Float,
  public val lastBaselineY: Float,
)

@Serializable
public data class UiBuilderRendererSemanticsInspectionV1(
  public val source: String = "authored-node-properties",
  public val role: String,
  public val label: String? = null,
  public val contentDescription: String? = null,
  public val enabled: Boolean? = null,
  public val selected: Boolean? = null,
  public val actions: List<String> = emptyList(),
)

private fun MutableList<UiBuilderRuntimeValidationIssueV1>.validateIdentity(
  runtimeId: String,
  protocolVersion: Int,
  integritySha256: String,
) {
  if (!isValidUiBuilderRuntimeIdV1(runtimeId)) addIssue("runtimeId", "unsafeOrReserved")
  if (protocolVersion <= 0) addIssue("protocolVersion", "notPositive")
  if (!isValidUiBuilderRuntimeSha256V1(integritySha256)) {
    addIssue("integritySha256", "invalid")
  }
}

private fun MutableList<UiBuilderRuntimeValidationIssueV1>.addIssue(
  field: String,
  code: String,
) {
  add(UiBuilderRuntimeValidationIssueV1(field, code))
}

private fun compareUnsigned(left: ByteArray, right: ByteArray): Int {
  val shared = minOf(left.size, right.size)
  for (index in 0 until shared) {
    val difference = (left[index].toInt() and 0xff) - (right[index].toInt() and 0xff)
    if (difference != 0) return difference
  }
  return left.size - right.size
}
