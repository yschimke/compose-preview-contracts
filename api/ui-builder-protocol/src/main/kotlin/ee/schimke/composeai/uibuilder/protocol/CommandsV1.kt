@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Idempotent command submission with optimistic-concurrency metadata. */
@Serializable
public data class DesignOperationV1(
  @EncodeDefault public val schemaVersion: Int = UI_BUILDER_SCHEMA_VERSION_V1,
  public val operationId: String,
  public val actorId: String,
  public val baseRevision: Long,
  public val issuedAtEpochMillis: Long,
  public val command: DesignCommandV1,
)

/** Closed command set accepted by a v1 reducer. This module does not implement that reducer. */
@Serializable public sealed interface DesignCommandV1

@Serializable
@SerialName("insertNode")
public data class InsertNodeCommandV1(
  public val parentId: String,
  public val parentSlot: String,
  public val index: Int,
  public val node: DesignNodeV1,
) : DesignCommandV1

@Serializable
@SerialName("moveNode")
public data class MoveNodeCommandV1(
  public val nodeId: String,
  public val parentId: String,
  public val parentSlot: String,
  public val index: Int,
) : DesignCommandV1

@Serializable
@SerialName("deleteNode")
public data class DeleteNodeCommandV1(public val nodeId: String) : DesignCommandV1

/** Restores an exact deleted subtree; reducers decide whether its anchor remains valid. */
@Serializable
@SerialName("restoreSubtree")
public data class RestoreSubtreeCommandV1(
  public val parentId: String,
  public val parentSlot: String,
  public val index: Int,
  public val rootNodeId: String,
  public val nodes: Map<String, DesignNodeV1>,
) : DesignCommandV1

@Serializable
@SerialName("setProperty")
public data class SetPropertyCommandV1(
  public val nodeId: String,
  public val propertyKey: String,
  public val value: UiValueV1,
) : DesignCommandV1

@Serializable
@SerialName("removeProperty")
public data class RemovePropertyCommandV1(
  public val nodeId: String,
  public val propertyKey: String,
) : DesignCommandV1

@Serializable
@SerialName("setNodeState")
public data class SetNodeStateCommandV1(public val nodeId: String, public val state: NodeStateV1) :
  DesignCommandV1

@Serializable
@SerialName("setPresentation")
public data class SetPresentationCommandV1(public val presentation: DesignPresentationV1) :
  DesignCommandV1

@Serializable
@SerialName("renameDesign")
public data class RenameDesignCommandV1(public val name: String) : DesignCommandV1

/** Atomically accepted or rejected ordered command group. */
@Serializable
@SerialName("batch")
public data class BatchCommandV1(public val commands: List<DesignCommandV1>) : DesignCommandV1

/** Requests a compensating operation for a previously accepted actor-owned operation. */
@Serializable
@SerialName("undo")
public data class UndoCommandV1(public val targetOperationId: String) : DesignCommandV1

/** Reapplies an operation previously compensated by this actor. */
@Serializable
@SerialName("redo")
public data class RedoCommandV1(public val targetOperationId: String) : DesignCommandV1

/** Stable result of processing an operation, suitable for replay and idempotent retries. */
@Serializable
public sealed interface CommandOutcomeV1 {
  public val operationId: String
}

@Serializable
@SerialName("accepted")
public data class AcceptedOutcomeV1(
  override val operationId: String,
  public val revision: Long,
  public val sequence: Long,
  public val appliedCommand: DesignCommandV1,
  public val compensatesOperationId: String? = null,
) : CommandOutcomeV1

@Serializable
@SerialName("rejected")
public data class RejectedOutcomeV1(
  override val operationId: String,
  public val revision: Long,
  public val reason: RejectionReasonV1,
  public val message: String,
  public val conflicts: List<CommandConflictV1> = emptyList(),
) : CommandOutcomeV1

@Serializable
public enum class RejectionReasonV1 {
  @SerialName("invalidCommand") INVALID_COMMAND,
  @SerialName("invalidStructure") INVALID_STRUCTURE,
  @SerialName("unknownComponent") UNKNOWN_COMPONENT,
  @SerialName("unsupportedCapability") UNSUPPORTED_CAPABILITY,
  @SerialName("staleRevision") STALE_REVISION,
  @SerialName("conflict") CONFLICT,
  @SerialName("forbidden") FORBIDDEN,
  @SerialName("notFound") NOT_FOUND,
}

@Serializable
public data class CommandConflictV1(
  public val kind: ConflictKindV1,
  public val nodeId: String? = null,
  public val propertyKey: String? = null,
  public val conflictingOperationId: String? = null,
  public val expectedRevision: Long? = null,
  public val actualRevision: Long? = null,
  public val message: String,
)

@Serializable
public enum class ConflictKindV1 {
  @SerialName("revisionAdvanced") REVISION_ADVANCED,
  @SerialName("nodeChanged") NODE_CHANGED,
  @SerialName("nodeDeleted") NODE_DELETED,
  @SerialName("anchorChanged") ANCHOR_CHANGED,
  @SerialName("propertyChanged") PROPERTY_CHANGED,
  @SerialName("undoUnavailable") UNDO_UNAVAILABLE,
  @SerialName("redoUnavailable") REDO_UNAVAILABLE,
}
