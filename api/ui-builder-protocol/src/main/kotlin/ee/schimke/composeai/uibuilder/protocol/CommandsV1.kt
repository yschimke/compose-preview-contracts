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
 * Declare or redefine one state variable.
 *
 * The document has carried [StateVariableV1] declarations and nodes have carried [DesignActionV1]
 * bindings since v1, but no mutation reached either — so a design could *contain* state and no
 * client could author it. Every interactive fixture in this repository was written by hand, and an
 * editor could only ever reproduce the static half of a screen.
 *
 * Name-granular like [UpdateEnvironmentMutationV1], and for the same reason: two authors declaring
 * two different variables are not in conflict, and making the whole map one value would make them
 * so.
 *
 * Removal is [RemoveStateVariableMutationV1] rather than a null [declaration] here, following the
 * same rule [EnvironmentChangeV1] states for reset: strict readers are configured with
 * `explicitNulls = false`, so an absent field and a null one are the same bytes, and "remove this
 * variable" would be indistinguishable from "I sent you no declaration".
 */
@Serializable
@SerialName("setStateVariable")
public data class SetStateVariableMutationV1(
  public val name: String,
  public val declaration: StateVariableV1,
) : DesignMutationV1

/**
 * Remove one state variable.
 *
 * Explicit rather than encoded as an absent declaration, for the reason given on
 * [SetStateVariableMutationV1].
 *
 * Removal is the operation reducers must treat carefully: a property or an action may still name
 * the variable, and a document that keeps such a reference renders blank rather than failing. A
 * reducer rejects a removal that would leave one dangling instead of committing it.
 */
@Serializable
@SerialName("removeStateVariable")
public data class RemoveStateVariableMutationV1(public val name: String) : DesignMutationV1

/**
 * Replace the actions bound to one event on one node.
 *
 * Whole-list rather than per-action, because the actions on an event run in order and as a unit:
 * "select this category" then "close the sheet" is one handler, and two authors editing it are
 * editing the same thing. Per-action addressing would invent an identity for list positions that
 * the document does not have.
 *
 * An empty [actions] list unbinds the event, which is why the field has **no default**. Strict
 * readers run with `encodeDefaults = false`, so a defaulted empty list is dropped on encode and
 * "unbind this event" would reach the reducer as an absent field — the same ambiguity that keeps
 * removal off [SetStateVariableMutationV1]. Required, it is always on the wire, and empty is a
 * value rather than an absence.
 *
 * Reducers validate every action against the design's declared state — an action naming an
 * undeclared variable is the same defect as a property bound to one — and reject the batch rather
 * than commit a handler that does nothing at runtime.
 */
@Serializable
@SerialName("setEventBinding")
public data class SetEventBindingMutationV1(
  public val nodeId: String,
  public val event: String,
  public val actions: List<DesignActionV1>,
) : DesignMutationV1

/**
 * Replace every modifier on one node.
 *
 * [DesignNodeV1.modifiers] has been part of the document since v1 and is the whole of its layout
 * vocabulary — padding, size, the two fills, `matchParentSize` and `clip`. Like state and event
 * bindings, nothing could author it: [SetPropertyMutationV1] reaches `properties` and no mutation
 * reached this. A client could insert a node carrying modifiers, because [InsertNodeMutationV1]
 * carries a whole [DesignNodeV1], and then never change one — so padding a container was a matter
 * of deleting it and building it again.
 *
 * Whole-list rather than per-modifier, for a stronger version of the reason given on
 * [SetEventBindingMutationV1]: a modifier chain is **order-dependent by definition** — padding then
 * size is a different layout from size then padding — and the list has no per-element identity to
 * address. Two authors editing a node's chain are editing one thing.
 *
 * An empty [modifiers] list clears the chain, which is why the field has **no default**, for the
 * reason [SetEventBindingMutationV1.actions] states: `encodeDefaults = false` drops a defaulted
 * empty list, so "clear this node's modifiers" would arrive as an absent field.
 *
 * Reducers validate each modifier before committing. A [SizeModifierV1] whose dimensions are not
 * usable numbers, or a modifier a renderer does not implement, is not a value a design should be
 * allowed to hold: it is discovered at composition, where the cost is the screen rather than the
 * write.
 */
@Serializable
@SerialName("setModifiers")
public data class SetModifiersMutationV1(
  public val nodeId: String,
  public val modifiers: List<DesignModifierV1>,
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
  @SerialName("typeface") TYPEFACE,
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
 * Names the family for the document's type scale. The value is a family name, never a file or a URL
 * — see [DesignEnvironmentV1.typeface] for why the document carries a name and the renderer carries
 * the means to obtain it.
 */
@Serializable
@SerialName("setTypeface")
public data class SetTypefaceEnvironmentChangeV1(public val value: String) : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.TYPEFACE
}

/** Returns the document to the renderer's platform default face. */
@Serializable
@SerialName("resetTypeface")
public data object ResetTypefaceEnvironmentChangeV1 : EnvironmentChangeV1 {
  override val field: EnvironmentFieldV1 = EnvironmentFieldV1.TYPEFACE
}

/**
 * Replaces the document's catalog pin with the exact candidate accepted by a prior deterministic
 * preview. Both hashes and [previewDigest] prevent applying a result to different source state.
 *
 * This must be the only mutation in its [DesignCommandV1], keeping [targetDocumentHash]
 * unambiguous. Implementations reject mixed batches as invalid commands.
 *
 * A rollback is an ordinary compensating mutation with the pins reversed and
 * [compensatesCatalogUpgradeOperationId] naming the accepted upgrade operation. It is committed as
 * a new operation; prior history is never rewritten.
 */
@Serializable
@SerialName("upgradeCatalog")
public data class CatalogUpgradeMutationV1(
  public val sourceCatalogPin: CatalogReferenceV1,
  public val targetCatalogPin: CatalogReferenceV1,
  public val sourceDocumentHash: String,
  public val targetDocumentHash: String,
  public val previewDigest: String,
  public val compensatesCatalogUpgradeOperationId: String? = null,
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
