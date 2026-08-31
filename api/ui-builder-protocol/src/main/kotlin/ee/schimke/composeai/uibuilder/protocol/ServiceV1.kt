@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Full synchronization point returned when a client opens or resynchronizes a design. */
@Serializable
public data class ServiceSnapshotV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val sessionId: String,
  public val state: DesignStateV1,
  public val catalog: CatalogCapabilityV1,
  public val retainedFromSequence: Long,
  public val presence: List<PresenceV1> = emptyList(),
)

/** One committed operation in the durable event sequence. Rejections are not committed. */
@Serializable
public data class CommittedOperationV1(
  public val sequence: Long,
  public val revision: Long,
  public val operation: DesignOperationV1,
  public val outcome: AcceptedOutcomeV1,
)

/** Ordered durable changes after an exclusive sequence cursor. */
@Serializable
public data class ServiceDeltaV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val sessionId: String,
  public val afterSequence: Long,
  public val throughSequence: Long,
  public val currentRevision: Long,
  public val retainedFromSequence: Long,
  public val operations: List<CommittedOperationV1>,
  public val hasMore: Boolean = false,
)

/** Ephemeral presence transition multiplexed alongside durable deltas. */
@Serializable public sealed interface PresenceUpdateV1

@Serializable
@SerialName("upsert")
public data class PresenceUpsertV1(public val presence: PresenceV1) : PresenceUpdateV1

@Serializable
@SerialName("leave")
public data class PresenceLeaveV1(public val actorId: String) : PresenceUpdateV1

/** Request payloads shared by HTTP handlers and MCP tools. */
@Serializable public sealed interface UiBuilderRequestV1

@Serializable
@SerialName("listCatalogs")
public data object ListCatalogsRequestV1 : UiBuilderRequestV1

@Serializable
@SerialName("createDesign")
public data class CreateDesignRequestV1(
  public val name: String,
  public val catalog: CatalogReferenceV1,
  public val rootNode: DesignNodeV1,
  public val presentation: DesignPresentationV1,
) : UiBuilderRequestV1

@Serializable
@SerialName("openDesign")
public data class OpenDesignRequestV1(public val designId: String) : UiBuilderRequestV1

@Serializable
@SerialName("applyOperation")
public data class ApplyOperationRequestV1(
  public val sessionId: String,
  public val operation: DesignOperationV1,
) : UiBuilderRequestV1

@Serializable
@SerialName("getSnapshot")
public data class GetSnapshotRequestV1(
  public val sessionId: String,
  public val revision: Long? = null,
) : UiBuilderRequestV1

@Serializable
@SerialName("getDelta")
public data class GetDeltaRequestV1(
  public val sessionId: String,
  public val afterSequence: Long,
  public val limit: Int = 256,
) : UiBuilderRequestV1

@Serializable
@SerialName("updatePresence")
public data class UpdatePresenceRequestV1(
  public val sessionId: String,
  public val presence: PresenceV1,
) : UiBuilderRequestV1

@Serializable
@SerialName("exportDesign")
public data class ExportDesignRequestV1(
  public val sessionId: String,
  public val revision: Long? = null,
  public val format: ExportFormatV1,
) : UiBuilderRequestV1

@Serializable
public enum class ExportFormatV1 {
  @SerialName("compose") COMPOSE,
  @SerialName("svg") SVG,
  @SerialName("png") PNG,
}

/** Response payloads shared by HTTP handlers and MCP tools. */
@Serializable public sealed interface UiBuilderResponseV1

@Serializable
@SerialName("catalogs")
public data class CatalogsResponseV1(public val catalogs: List<CatalogCapabilityV1>) :
  UiBuilderResponseV1

@Serializable
@SerialName("snapshot")
public data class SnapshotResponseV1(public val snapshot: ServiceSnapshotV1) : UiBuilderResponseV1

@Serializable
@SerialName("operationOutcome")
public data class OperationOutcomeResponseV1(public val outcome: CommandOutcomeV1) :
  UiBuilderResponseV1

@Serializable
@SerialName("delta")
public data class DeltaResponseV1(public val delta: ServiceDeltaV1) : UiBuilderResponseV1

@Serializable
@SerialName("presenceAccepted")
public data class PresenceAcceptedResponseV1(
  public val sessionId: String,
  public val actorId: String,
) : UiBuilderResponseV1

@Serializable
@SerialName("export")
public data class ExportResponseV1(public val artifact: ExportArtifactV1) : UiBuilderResponseV1

@Serializable
@SerialName("error")
public data class ErrorResponseV1(public val error: ServiceErrorV1) : UiBuilderResponseV1

/** Inline export result. `content` is UTF-8 text or base64 according to [encoding]. */
@Serializable
public data class ExportArtifactV1(
  public val format: ExportFormatV1,
  public val mediaType: String,
  public val encoding: ExportEncodingV1,
  public val content: String,
  public val contentDigest: String,
  public val diagnostics: List<ExportDiagnosticV1> = emptyList(),
)

@Serializable
public enum class ExportEncodingV1 {
  @SerialName("utf8") UTF8,
  @SerialName("base64") BASE64,
}

@Serializable
public data class ExportDiagnosticV1(
  public val severity: DiagnosticSeverityV1,
  public val code: String,
  public val message: String,
  public val nodeId: String? = null,
)

@Serializable
public enum class DiagnosticSeverityV1 {
  @SerialName("info") INFO,
  @SerialName("warning") WARNING,
  @SerialName("error") ERROR,
}

@Serializable
public data class ServiceErrorV1(
  public val code: ServiceErrorCodeV1,
  public val message: String,
  public val retryable: Boolean = false,
  public val currentRevision: Long? = null,
  public val retainedFromSequence: Long? = null,
)

@Serializable
public enum class ServiceErrorCodeV1 {
  @SerialName("badRequest") BAD_REQUEST,
  @SerialName("unauthorized") UNAUTHORIZED,
  @SerialName("forbidden") FORBIDDEN,
  @SerialName("notFound") NOT_FOUND,
  @SerialName("catalogUnavailable") CATALOG_UNAVAILABLE,
  @SerialName("migrationRequired") MIGRATION_REQUIRED,
  @SerialName("snapshotRequired") SNAPSHOT_REQUIRED,
  @SerialName("internal") INTERNAL,
}

/** HTTP request envelope. Authentication remains transport metadata, never a DTO field. */
@Serializable
public data class HttpRequestEnvelopeV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val requestId: String,
  public val actorId: String,
  public val request: UiBuilderRequestV1,
)

@Serializable
public data class HttpResponseEnvelopeV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val requestId: String,
  public val response: UiBuilderResponseV1,
)

/** MCP tool-call envelope; the MCP adapter maps tool names to the typed request variant. */
@Serializable
public data class McpRequestEnvelopeV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val callId: String,
  public val actorId: String,
  public val request: UiBuilderRequestV1,
)

@Serializable
public data class McpResponseEnvelopeV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val callId: String,
  public val response: UiBuilderResponseV1,
)

/** Server-pushed session message used by concurrent browser and MCP observers. */
@Serializable public sealed interface SessionUpdateV1

@Serializable
@SerialName("snapshot")
public data class SnapshotSessionUpdateV1(public val snapshot: ServiceSnapshotV1) : SessionUpdateV1

@Serializable
@SerialName("delta")
public data class DeltaSessionUpdateV1(public val delta: ServiceDeltaV1) : SessionUpdateV1

@Serializable
@SerialName("presence")
public data class PresenceSessionUpdateV1(public val update: PresenceUpdateV1) : SessionUpdateV1

@Serializable
@SerialName("outcome")
public data class OutcomeSessionUpdateV1(public val outcome: CommandOutcomeV1) : SessionUpdateV1

@Serializable
public data class SessionUpdateEnvelopeV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val sessionId: String,
  public val update: SessionUpdateV1,
)
