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
 * Field-granular environment update. Reducers reject duplicate [EnvironmentChangeV1.field] values
 * in one batch, validate every value before committing, and retain before/after values for history
 * and compensation. Stale writes conflict independently per field.
 */
@Serializable
@SerialName("updateEnvironment")
public data class UpdateEnvironmentMutationV1(public val changes: List<EnvironmentChangeV1>) :
  DesignMutationV1

/** Closed environment change set; reset is explicit and exists only for nullable fields. */
@Serializable
public sealed interface EnvironmentChangeV1 {
  public val field: EnvironmentFieldV1
}

@Serializable
public enum class EnvironmentFieldV1 {
  @SerialName("widthDp") WIDTH_DP,
  @SerialName("heightDp") HEIGHT_DP,
  @SerialName("density") DENSITY,
  @SerialName("theme") THEME,
  @SerialName("dynamicColor") DYNAMIC_COLOR,
  @SerialName("locale") LOCALE,
  @SerialName("fontScale") FONT_SCALE,
  @SerialName("layoutDirection") LAYOUT_DIRECTION,
  @SerialName("windowPosture") WINDOW_POSTURE,
  @SerialName("browserZoomPercent") BROWSER_ZOOM_PERCENT,
  @SerialName("fixedTime") FIXED_TIME,
  @SerialName("animations") ANIMATIONS,
  @SerialName("networkAccess") NETWORK_ACCESS,
  @SerialName("background") BACKGROUND,
}

@Serializable
@SerialName("setWidthDp")
public data class SetWidthDpEnvironmentChangeV1(public val value: Int) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.WIDTH_DP
}

@Serializable
@SerialName("setHeightDp")
public data class SetHeightDpEnvironmentChangeV1(public val value: Int) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.HEIGHT_DP
}

@Serializable
@SerialName("setDensity")
public data class SetDensityEnvironmentChangeV1(public val value: Double) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.DENSITY
}

@Serializable
@SerialName("setTheme")
public data class SetThemeEnvironmentChangeV1(public val value: ThemeV1) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.THEME
}

@Serializable
@SerialName("setLocale")
public data class SetLocaleEnvironmentChangeV1(public val value: String) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.LOCALE
}

@Serializable
@SerialName("setFontScale")
public data class SetFontScaleEnvironmentChangeV1(public val value: Double) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.FONT_SCALE
}

@Serializable
@SerialName("setLayoutDirection")
public data class SetLayoutDirectionEnvironmentChangeV1(public val value: LayoutDirectionV1) :
  EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.LAYOUT_DIRECTION
}

@Serializable
@SerialName("setDynamicColor")
public data class SetDynamicColorEnvironmentChangeV1(public val value: Boolean) :
  EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.DYNAMIC_COLOR
}

@Serializable
@SerialName("resetDynamicColor")
public data object ResetDynamicColorEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.DYNAMIC_COLOR
}

@Serializable
@SerialName("setWindowPosture")
public data class SetWindowPostureEnvironmentChangeV1(public val value: WindowPostureV1) :
  EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.WINDOW_POSTURE
}

@Serializable
@SerialName("resetWindowPosture")
public data object ResetWindowPostureEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.WINDOW_POSTURE
}

@Serializable
@SerialName("setBrowserZoomPercent")
public data class SetBrowserZoomPercentEnvironmentChangeV1(public val value: Int) :
  EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.BROWSER_ZOOM_PERCENT
}

@Serializable
@SerialName("resetBrowserZoomPercent")
public data object ResetBrowserZoomPercentEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.BROWSER_ZOOM_PERCENT
}

@Serializable
@SerialName("setFixedTime")
public data class SetFixedTimeEnvironmentChangeV1(public val value: String) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.FIXED_TIME
}

@Serializable
@SerialName("resetFixedTime")
public data object ResetFixedTimeEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.FIXED_TIME
}

@Serializable
@SerialName("setAnimations")
public data class SetAnimationsEnvironmentChangeV1(public val value: AnimationStateV1) :
  EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.ANIMATIONS
}

@Serializable
@SerialName("resetAnimations")
public data object ResetAnimationsEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.ANIMATIONS
}

@Serializable
@SerialName("setNetworkAccess")
public data class SetNetworkAccessEnvironmentChangeV1(public val value: Boolean) :
  EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.NETWORK_ACCESS
}

@Serializable
@SerialName("resetNetworkAccess")
public data object ResetNetworkAccessEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.NETWORK_ACCESS
}

@Serializable
@SerialName("setBackground")
public data class SetBackgroundEnvironmentChangeV1(public val value: UiValueV1) :
  EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.BACKGROUND
}

@Serializable
@SerialName("resetBackground")
public data object ResetBackgroundEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.BACKGROUND
}

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
  public val environmentField: EnvironmentFieldV1? = null,
) : CommandOutcomeV1 {
  /** Preserves the v1 JVM constructor used before environment rejection locations were added. */
  public constructor(
    operationId: String,
    currentRevision: Long,
    code: RejectionCodeV1,
    message: String,
    operationIndex: Int?,
    nodeId: String?,
    field: String?,
  ) : this(operationId, currentRevision, code, message, operationIndex, nodeId, field, null)
}

/** Located non-fatal conflict notice attached to an accepted convergent operation. */
@Serializable
public data class CommandConflictV1(
  public val code: ConflictCodeV1,
  public val nodeId: String?,
  public val field: String? = null,
  public val overwrittenRevision: Long,
  /** Exactly one of [nodeId] and [environmentField] must be set. */
  public val environmentField: EnvironmentFieldV1? = null,
) {
  /** Preserves the v1 JVM constructor used by existing node-conflict consumers. */
  public constructor(
    code: ConflictCodeV1,
    nodeId: String,
    field: String?,
    overwrittenRevision: Long,
  ) : this(code, nodeId, field, overwrittenRevision, null)
}

@Serializable
public enum class ConflictCodeV1 {
  @SerialName("stalePropertyWrite") STALE_PROPERTY_WRITE,
  @SerialName("staleMove") STALE_MOVE,
  @SerialName("staleEnvironmentWrite") STALE_ENVIRONMENT_WRITE,
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
