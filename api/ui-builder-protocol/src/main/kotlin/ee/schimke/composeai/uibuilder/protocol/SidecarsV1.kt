package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The three records that sit *beside* a UI-builder design: what it is for, what it reproduces, and
 * what is being said about it.
 *
 * ## Why these are here and not in the design document
 *
 * None of them is part of the design. A `pr` URL is not a thing that ships in generated Kotlin, a
 * reference photograph is not a node, and a review comment is not a mutation. [DesignMutationV1] is
 * a closed set with no mutation for any of them, and — the sharpest reason — a design's revision is
 * replayed, hashed, diffed for catalog upgrades and pushed to every subscriber on every edit, so
 * pasting an issue URL must not advance it.
 *
 * ## Why they are *here* rather than in the server
 *
 * Because they are wire shapes, and this is where wire shapes live. Each of these is the body of an
 * HTTP response, the payload of an MCP tool reply, and the file on disk, all at once — one shape
 * for all three, because the file *is* the response body and translating between two identical
 * declarations buys nothing. The server owns the storage, the validation and the access control;
 * this module owns only the shape, so a browser, an agent and a second implementation can agree on
 * it without agreeing on any of that.
 *
 * ## What must not change
 *
 * Every field name and `@SerialName` here is the JSON a shipped server already writes into its
 * `references/`, `comments/` and `links/` directories and already answers to clients. A host
 * upgrading must read what it wrote yesterday. Renaming a Kotlin type is free; renaming a JSON key
 * is a migration, and there is none.
 *
 * Nothing here computes anything. "Is this record empty", "has this actor caught up", "what does a
 * byte budget count" are all questions a host answers about these shapes, and a host is where they
 * stay — this module is shape and never behaviour, and a rule about a record is not part of it.
 *
 * Readers are expected to be **tolerant**: the editor's own mirror of these decodes leniently, and
 * that is what lets a payload gain a field without blanking somebody's panel mid-release.
 */
@Serializable
public data class DesignLinksV1(
  @EncodeDefault @SerialName("schemaVersion") public val schemaVersion: Int = SCHEMA_VERSION,
  public val designId: String = "",
  /** The tracker issue this design is for. */
  public val issue: String? = null,
  /** The frame in the design tool it reproduces — any tool; no host resolves them. */
  public val reference: String? = null,
  /** The pull request that implemented it. */
  public val pr: String? = null,
  /** The chat thread it is being discussed in, as a permalink. */
  public val thread: String? = null,
  /** The design on the same host that this one continues. */
  public val previous: String? = null,
  public val updatedAtEpochMillis: Long = 0,
) {
  public companion object {
    public const val SCHEMA_VERSION: Int = 1
  }
}

/**
 * The reference overlay: the picture a design is being drawn against, and what is drawn over it.
 *
 * [image] is the base, fitted to the frame; [pieces] are pictures placed at a point on it; [marks]
 * are annotations. Null [image] with pieces or marks present is an ordinary state, not an error.
 */
@Serializable
public data class DesignReferenceV1(
  @EncodeDefault @SerialName("schemaVersion") public val schemaVersion: Int = SCHEMA_VERSION,
  public val designId: String,
  public val image: DesignReferenceImageV1? = null,
  public val settings: DesignReferenceSettingsV1 = DesignReferenceSettingsV1(),
  public val pieces: List<DesignReferencePieceV1> = emptyList(),
  public val marks: List<DesignReferenceMarkV1> = emptyList(),
  public val updatedAtEpochMillis: Long = 0,
) {
  public companion object {
    public const val SCHEMA_VERSION: Int = 1
  }
}

@Serializable
public data class DesignReferenceImageV1(
  /** Content digest, assigned by the host. A client's proposal is overwritten, never trusted. */
  public val id: String = "",
  /** What an operator will recognise it by; the file name they chose, usually. */
  public val name: String = "reference",
  public val mediaType: String,
  /** Standard base64, with no data-URI prefix. */
  public val base64: String,
  /** Natural size where the format allows the host to read it; 0 when unknown. */
  public val widthPx: Int = 0,
  public val heightPx: Int = 0,
  /**
   * Where the picture came from, kept for provenance and **never fetched**.
   *
   * A design-tool node URL belongs here. A host holds no credential for any such tool and makes no
   * outbound call for a reference: this is the link back, not a fetch instruction.
   */
  public val sourceUrl: String? = null,
)

/**
 * How the overlay is drawn.
 *
 * A host is expected to clamp these before storing, because the store is reachable by anything
 * holding a write capability rather than only by the editor that also clamps them for drawing.
 * [KNOWN_MODES] is the set a reader should recognise; an unknown mode falls back to `overlay`
 * rather than failing, so a newer client cannot blank an older one's panel.
 */
@Serializable
public data class DesignReferenceSettingsV1(
  public val mode: String = "overlay",
  public val visible: Boolean = true,
  public val opacityPercent: Int = 50,
  public val offsetXDp: Float = 0f,
  public val offsetYDp: Float = 0f,
  public val scalePercent: Int = 100,
  public val splitPercent: Int = 50,
  public val alwaysShowBoxes: Boolean = false,
) {
  public companion object {
    public val KNOWN_MODES: Set<String> = setOf("overlay", "difference", "split", "boxes")

    public const val MIN_SCALE_PERCENT: Int = 10
    public const val MAX_SCALE_PERCENT: Int = 400
    public const val MAX_OFFSET_DP: Float = 4000f
  }
}

/**
 * A picture placed on the frame rather than fitted to it, in fractions of the frame.
 *
 * Fractions rather than dp so a piece survives a device-frame change: one over the top third of a
 * phone is still over the top third of the tablet an operator switches to.
 */
@Serializable
public data class DesignReferencePieceV1(
  public val id: String,
  public val image: DesignReferenceImageV1,
  public val left: Float,
  public val top: Float,
  public val right: Float,
  public val bottom: Float,
  public val opacityPercent: Int = 100,
  /**
   * The catalog component this piece is a picture of, when it is a picture of one.
   *
   * Provenance, never behaviour: nothing is drawn or resolved from it. It exists so a piece
   * rasterised out of a live preview can later be rebuilt as real nodes, rather than being a
   * picture nobody can trace.
   */
  public val componentId: String? = null,
)

/** One annotation. [points] alternates x and y, in frame fractions, tail first. */
@Serializable
public data class DesignReferenceMarkV1(
  public val id: String,
  public val kind: String,
  public val points: List<Float>,
  /** `0xAARRGGBB`. A Long because JSON has no unsigned integer and this one sets the top bit. */
  public val colorArgb: Long,
  public val strokeWidthDp: Float = 2f,
  /** The words a text mark draws, and the caption on an image placeholder. */
  public val text: String? = null,
)

/**
 * The discussion attached to one design.
 *
 * ### Why the whole board is the delta
 *
 * [sequence] rises by one on every accepted write, and a reader that quotes the sequence it last
 * saw is answered with the whole board rather than with the threads that changed since. That is
 * deliberate: a design's discussion is a few kilobytes of text, replaying it costs less than the
 * bookkeeping a per-thread log would need, and a client returning after a nap gets one answer that
 * is correct rather than a window it may have fallen out of. The design document's own event log
 * makes the opposite trade for the opposite reason — it is replayed into a reducer, and it is
 * large.
 */
@Serializable
public data class DesignCommentBoardV1(
  @EncodeDefault @SerialName("schemaVersion") public val schemaVersion: Int = SCHEMA_VERSION,
  public val designId: String,
  /**
   * Monotonic per design, never reused, and the only thing a watcher has to remember.
   *
   * Rises on every accepted write — a comment, an edit, a resolve, a delete — so "has anything
   * changed" is one comparison rather than a diff of the threads.
   */
  public val sequence: Long = 0,
  public val threads: List<DesignCommentThreadV1> = emptyList(),
  public val updatedAtEpochMillis: Long = 0,
) {
  public companion object {
    public const val SCHEMA_VERSION: Int = 1
  }
}

/**
 * One conversation, and where on the design it is about.
 *
 * A thread rather than a flat list, because a discussion has replies and because *resolved* is a
 * property of the question rather than of any one sentence in it.
 */
@Serializable
public data class DesignCommentThreadV1(
  public val id: String,
  /** Where this is pinned, or null for a thread about the design as a whole. */
  public val anchor: DesignCommentAnchorV1? = null,
  public val resolved: Boolean = false,
  /** Who resolved it, and when. Null while it is open. */
  public val resolvedBy: String? = null,
  public val resolvedAtEpochMillis: Long? = null,
  public val createdAtEpochMillis: Long = 0,
  public val updatedAtEpochMillis: Long = 0,
  /**
   * The board [DesignCommentBoardV1.sequence] this thread last *said* something at.
   *
   * The wall clock beside it is for a person reading a panel; this is what [acknowledgedBy] is
   * compared against, because acknowledgement has to be exact. A millisecond comparison would call
   * a reply acknowledged whenever it landed inside the same millisecond as the acknowledgement —
   * rare, silent, and exactly the failure this field exists to stop.
   *
   * Only what somebody *said* moves it: a comment, a resolve, a reopen. An acknowledgement and a
   * reaction deliberately do not, since both are one actor's own bookkeeping and bumping this would
   * make an agent's 👀 read to every other actor as new activity to catch up on.
   *
   * A thread stored before this field existed carries 0, which is below every acknowledgement and
   * so reads as unacknowledged. That is the safe direction: it resurfaces once, rather than being
   * silently marked as seen by somebody who never saw it.
   */
  public val updatedAtSequence: Long = 0,
  /**
   * Per actor, the board sequence at which they last acknowledged this thread.
   *
   * Acknowledgement is **per actor and is not resolution**: it says "I have read this", not "this
   * is settled", and the two are different claims. An agent that resolves a thread it has not fixed
   * is lying; an agent that stays silent is invisible. This is the third answer.
   *
   * Writing into a thread acknowledges it for the writer, so replying, resolving or reacting never
   * leaves an actor being nagged about their own words.
   */
  public val acknowledgedBy: Map<String, Long> = emptyMap(),
  public val comments: List<DesignCommentV1> = emptyList(),
)

/**
 * One thing somebody said.
 *
 * [authorId] is the authenticated actor, assigned by the host; a client's proposal is overwritten
 * rather than trusted, exactly as [DesignReferenceImageV1.id] is. The whole value of a discussion
 * between a person and an agent is that each line is attributed to whoever actually wrote it.
 */
@Serializable
public data class DesignCommentV1(
  public val id: String,
  public val authorId: String,
  /**
   * What the author's name reads as in a panel. Client-supplied and cosmetic: [authorId] is the
   * identity, and this is the label beside it.
   */
  public val displayName: String = "",
  /**
   * `human` or `agent`, as declared by the caller.
   *
   * Cosmetic in the same way: it decides an icon, never a permission. A host cannot tell a person's
   * browser from an agent's MCP session by the credential alone — both are grants — so asking is
   * more honest than guessing, and nothing downstream depends on the answer.
   */
  public val authorKind: String = AUTHOR_KIND_HUMAN,
  public val body: String,
  public val createdAtEpochMillis: Long = 0,
  public val editedAtEpochMillis: Long? = null,
  /**
   * Emoji to the actors who reacted with it, oldest first.
   *
   * A map rather than a list of rows, because that is how a panel draws it — one chip per emoji
   * with a count and a tooltip of who — and because it makes a second reaction from the same actor
   * idempotent by construction.
   *
   * Reactions exist for the large class of answers that do not deserve a reply: a person 👍-ing a
   * fix, an agent 👀-ing a comment it has just picked up. A reply to say either would be noise in a
   * thread somebody has to read.
   */
  public val reactions: Map<String, List<String>> = emptyMap(),
) {
  public companion object {
    public const val AUTHOR_KIND_HUMAN: String = "human"
    public const val AUTHOR_KIND_AGENT: String = "agent"

    public val KNOWN_AUTHOR_KINDS: Set<String> = setOf(AUTHOR_KIND_HUMAN, AUTHOR_KIND_AGENT)
  }
}

/**
 * Where a thread is pinned.
 *
 * Three ways of saying it, and a thread may use more than one at once because they answer different
 * questions. [markId] ties the discussion to a stroke drawn on the reference — the point of linking
 * comments to markup at all, since "this arrow, why?" is a sentence that needs the arrow. [nodeId]
 * ties it to a node, so it survives the reference being replaced. [x] and [y] are frame fractions,
 * the coordinate space [DesignReferenceMarkV1.points] uses, so a pin lands in the same place on a
 * phone frame and on the tablet an operator switches to.
 */
@Serializable
public data class DesignCommentAnchorV1(
  public val markId: String? = null,
  public val nodeId: String? = null,
  public val x: Float? = null,
  public val y: Float? = null,
)
