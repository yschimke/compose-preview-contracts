@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Full synchronization point returned when a client opens or resynchronizes a design. */
@Serializable
public data class ServiceSnapshotV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val designId: String,
  public val state: DesignStateV1,
  public val catalog: CatalogCapabilityV1,
  public val retainedFromSequence: Long,
  public val presence: List<PresenceV1> = emptyList(),
  /** Kept separate from [state] so access changes never alter the canonical document hash. */
  public val access: DesignAccessControlV1? = null,
)

/** One committed operation in the durable event sequence. Rejections are not committed. */
@Serializable
public data class CommittedOperationV1(
  public val submission: DesignSubmissionV1,
  /** Sole authoritative committed revision and sequence for this event. */
  public val outcome: AcceptedOutcomeV1,
)

/** Ordered durable changes after an exclusive sequence cursor. */
@Serializable
public data class ServiceDeltaV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val designId: String,
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
public data class CreateDesignRequestV1(public val document: DesignDocumentV1) : UiBuilderRequestV1

@Serializable
@SerialName("listDesigns")
public data class ListDesignsRequestV1(
  public val cursor: String? = null,
  public val limit: Int = 50,
) : UiBuilderRequestV1

@Serializable
@SerialName("openDesign")
public data class OpenDesignRequestV1(public val designId: String) : UiBuilderRequestV1

@Serializable
@SerialName("getDesignAccess")
public data class GetDesignAccessRequestV1(public val designId: String) : UiBuilderRequestV1

/** Access mutations are applied atomically against their own revision. */
@Serializable
@SerialName("updateDesignAccess")
public data class UpdateDesignAccessRequestV1(
  public val designId: String,
  public val baseAccessRevision: Long,
  public val mutations: List<DesignAccessMutationV1>,
) : UiBuilderRequestV1

@Serializable
@SerialName("applyOperation")
public data class ApplyOperationRequestV1(public val submission: DesignSubmissionV1) :
  UiBuilderRequestV1

@Serializable
@SerialName("getSnapshot")
public data class GetSnapshotRequestV1(
  public val designId: String,
  public val revision: Long? = null,
) : UiBuilderRequestV1

@Serializable
@SerialName("getDelta")
public data class GetDeltaRequestV1(
  public val designId: String,
  public val afterSequence: Long,
  public val limit: Int = 256,
) : UiBuilderRequestV1

@Serializable
@SerialName("updatePresence")
public data class UpdatePresenceRequestV1(
  public val designId: String,
  public val presence: PresenceV1,
) : UiBuilderRequestV1

@Serializable
@SerialName("exportDesign")
public data class ExportDesignRequestV1(
  public val designId: String,
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
@SerialName("designs")
public data class DesignsResponseV1(
  public val designs: List<DesignListItemV1>,
  public val nextCursor: String? = null,
) : UiBuilderResponseV1

@Serializable
@SerialName("designAccess")
public data class DesignAccessResponseV1(
  public val designId: String,
  public val access: DesignAccessControlV1,
) : UiBuilderResponseV1

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
  public val designId: String,
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
  public val currentAccessRevision: Long? = null,
  public val retainedFromSequence: Long? = null,
)

@Serializable
public enum class ServiceErrorCodeV1 {
  @SerialName("badRequest") BAD_REQUEST,
  @SerialName("accessRevisionMismatch") ACCESS_REVISION_MISMATCH,
  @SerialName("unauthorized") UNAUTHORIZED,
  @SerialName("forbidden") FORBIDDEN,
  @SerialName("notFound") NOT_FOUND,
  @SerialName("catalogUnavailable") CATALOG_UNAVAILABLE,
  @SerialName("migrationRequired") MIGRATION_REQUIRED,
  @SerialName("snapshotRequired") SNAPSHOT_REQUIRED,
  @SerialName("internal") INTERNAL,
}

/**
 * HTTP request envelope. The transport authenticates [actorId]. Any nested requester actor ID (for
 * example a design command or presence update) must match it; ACL target IDs are not requesters.
 */
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

/**
 * MCP tool-call envelope; the adapter authenticates [actorId], applies the same nested-actor match
 * rule as HTTP, and maps tool names to typed request variants.
 */
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

/** Server-pushed design message used by concurrent browser and MCP observers. */
@Serializable public sealed interface DesignUpdateV1

@Serializable
@SerialName("snapshot")
public data class SnapshotDesignUpdateV1(public val snapshot: ServiceSnapshotV1) : DesignUpdateV1

@Serializable
@SerialName("delta")
public data class DeltaDesignUpdateV1(public val delta: ServiceDeltaV1) : DesignUpdateV1

@Serializable
@SerialName("presence")
public data class PresenceDesignUpdateV1(public val update: PresenceUpdateV1) : DesignUpdateV1

@Serializable
@SerialName("outcome")
public data class OutcomeDesignUpdateV1(public val outcome: CommandOutcomeV1) : DesignUpdateV1

@Serializable
public data class DesignUpdateEnvelopeV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val designId: String,
  public val update: DesignUpdateV1,
)
