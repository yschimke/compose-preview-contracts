package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Submission variants processed by the same authoritative collaboration reducer. */
@Serializable public sealed interface DesignSubmissionV1

/** Atomic ordered mutation batch matching the current reducer's `DesignCommand` semantics. */
@Serializable
@SerialName("batch")
public data class DesignCommandV1(
  public val designId: String,
  public val operationId: String,
  public val actorId: String,
  public val clientId: String,
  public val baseRevision: Long,
  public val operations: List<DesignMutationV1>,
) : DesignSubmissionV1

/** Requests a compensating operation for one actor-owned accepted batch. */
@Serializable
@SerialName("undo")
public data class UndoCommandV1(
  public val designId: String,
  public val operationId: String,
  public val actorId: String,
  public val clientId: String,
  public val baseRevision: Long,
  public val targetOperationId: String,
) : DesignSubmissionV1

/** Compensates an accepted undo; the target is the undo operation, not its original batch. */
@Serializable
@SerialName("redo")
public data class RedoCommandV1(
  public val designId: String,
  public val operationId: String,
  public val actorId: String,
  public val clientId: String,
  public val baseRevision: Long,
  public val targetUndoOperationId: String,
) : DesignSubmissionV1

/** Closed mutation set inside one atomic batch. */
@Serializable public sealed interface DesignMutationV1

@Serializable
@SerialName("insertNode")
public data class InsertNodeMutationV1(
  public val node: DesignNodeV1,
  public val location: NodeLocationV1,
) : DesignMutationV1

@Serializable
@SerialName("moveNode")
public data class MoveNodeMutationV1(
  public val nodeId: String,
  public val location: NodeLocationV1,
) : DesignMutationV1

@Serializable
@SerialName("deleteNode")
public data class DeleteNodeMutationV1(public val nodeId: String) : DesignMutationV1

/** Restores a reducer-retained tombstone, optionally overriding its retained location anchor. */
@Serializable
@SerialName("restoreNode")
public data class RestoreNodeMutationV1(
  public val nodeId: String,
  public val location: NodeLocationV1? = null,
) : DesignMutationV1

@Serializable
@SerialName("setProperty")
public data class SetPropertyMutationV1(
  public val nodeId: String,
  public val property: String,
  public val value: UiValueV1,
) : DesignMutationV1

/**
 * Parent slot or root list plus stable neighbour/position anchors; indexes are never transmitted.
 */
@Serializable
public data class NodeLocationV1(
  public val parent: ParentSlotV1? = null,
  public val afterNodeId: String? = null,
  public val beforeNodeId: String? = null,
)

@Serializable public data class ParentSlotV1(public val nodeId: String, public val slot: String)

/** Stable result returned for an accepted, rejected, undo, redo, or idempotent retry submission. */
@Serializable
public sealed interface CommandOutcomeV1 {
  public val operationId: String
}

@Serializable
@SerialName("accepted")
public data class AcceptedOutcomeV1(
  override val operationId: String,
  public val committedRevision: Long,
  public val sequence: Long,
  /** SHA-256 of the reducer's canonical document JSON; full state is carried by snapshots. */
  public val documentHash: String,
  public val idempotentReplay: Boolean,
  public val conflicts: List<CommandConflictV1> = emptyList(),
  /**
   * Authoritative [DesignDocumentV1.updatedAtEpochMillis] included in [documentHash]. Delta clients
   * need this server-owned value to reconstruct and verify the committed document without fetching
   * a snapshot. Null identifies an outcome produced before this additive field was available.
   */
  public val documentUpdatedAtEpochMillis: Long? = null,
) : CommandOutcomeV1

@Serializable
@SerialName("rejected")
public data class RejectedOutcomeV1(
  override val operationId: String,
  public val currentRevision: Long,
  public val code: RejectionCodeV1,
  public val message: String,
  public val operationIndex: Int? = null,
  public val nodeId: String? = null,
  public val field: String? = null,
) : CommandOutcomeV1

/** Located non-fatal conflict notice attached to an accepted convergent operation. */
@Serializable
public data class CommandConflictV1(
  public val code: ConflictCodeV1,
  public val nodeId: String,
  public val field: String? = null,
  public val overwrittenRevision: Long,
)

@Serializable
public enum class ConflictCodeV1 {
  @SerialName("stalePropertyWrite") STALE_PROPERTY_WRITE,
  @SerialName("staleMove") STALE_MOVE,
}

@Serializable
public enum class RejectionCodeV1 {
  @SerialName("designMismatch") DESIGN_MISMATCH,
  @SerialName("invalidCommand") INVALID_COMMAND,
  @SerialName("revisionMismatch") REVISION_MISMATCH,
  @SerialName("operationIdReused") OPERATION_ID_REUSED,
  @SerialName("unknownNode") UNKNOWN_NODE,
  @SerialName("deletedNode") DELETED_NODE,
  @SerialName("missingPropertyValidator") MISSING_PROPERTY_VALIDATOR,
  @SerialName("malformedProperty") MALFORMED_PROPERTY,
  @SerialName("invalidProperty") INVALID_PROPERTY,
  @SerialName("invalidDocument") INVALID_DOCUMENT,
  @SerialName("revisionNotRetained") REVISION_NOT_RETAINED,
  @SerialName("invalidLocation") INVALID_LOCATION,
  @SerialName("cycle") CYCLE,
  @SerialName("actorMismatch") ACTOR_MISMATCH,
  @SerialName("alreadyCompensated") ALREADY_COMPENSATED,
  @SerialName("unsafeCompensation") UNSAFE_COMPENSATION,
  @SerialName("unsupportedCompensation") UNSUPPORTED_COMPENSATION,
  @SerialName("unknownOperation") UNKNOWN_OPERATION,
  @SerialName("replayDivergence") REPLAY_DIVERGENCE,
}
