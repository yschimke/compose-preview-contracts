package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a host sends when somebody says something on a design's
 * [comment board][DesignCommentBoardV1].
 *
 * ## Why this is a contract and not a server detail
 *
 * A host can post comment activity to an outbound webhook. It renders that activity for Slack,
 * Teams or Google Chat, and those adapters are one-way functions whose output nobody parses — but
 * it also offers a `plain` format, which is this event verbatim, and a `plain` receiver is a
 * program somebody wrote against these field names. That makes it a wire contract in the only sense
 * that matters: two sides have to agree on it, and one of them is not in this codebase.
 *
 * [schema] carries the version and is always on the wire, so a receiver can branch on it rather
 * than guess.
 *
 * ## What it deliberately is not
 *
 * Not the comment board. A board is the durable record of a discussion; this is a single
 * notification *about* one change to it, shaped for somebody reading a chat window. It carries an
 * excerpt rather than a body, a sentence rather than an anchor's three fields, and a count rather
 * than the comments — because the reader is a person deciding whether to click, not a client
 * reconstructing state. A receiver that wants the discussion reads the board.
 *
 * Nothing here is a destination. [DesignCommentWebhookDesignV1.thread] is a chat permalink a reader
 * follows, and a host must never post to it: it is collaborator-writable metadata rather than an
 * operator's credential.
 */
@Serializable
public data class DesignCommentWebhookEventV1(
  @EncodeDefault public val schema: String = SCHEMA,
  /** `thread`, `reply`, `resolved` or `reopened`. */
  public val event: String,
  public val design: DesignCommentWebhookDesignV1,
  public val thread: DesignCommentWebhookThreadV1,
  public val comment: DesignCommentWebhookCommentV1,
  /** The thread permalink. The one field a person in a chat window actually uses. */
  public val url: String,
) {
  public companion object {
    public const val SCHEMA: String = "compose-preview/ui-builder-comment-event/v1"

    /** The four things that can happen to a thread, as [event] spells them. */
    public const val EVENT_THREAD: String = "thread"

    public const val EVENT_REPLY: String = "reply"

    public const val EVENT_RESOLVED: String = "resolved"

    public const val EVENT_REOPENED: String = "reopened"
  }
}

/**
 * Which design the comment is on, named the way a reader needs rather than the way a store does.
 */
@Serializable
public data class DesignCommentWebhookDesignV1(
  public val id: String,
  /** The design's own title, falling back to its id on a host that cannot name it. */
  public val title: String,
  /** The catalog it is pinned to — the `<catalog>` segment of [DesignCommentWebhookEventV1.url]. */
  public val catalog: String? = null,
  /**
   * The chat thread this design is being discussed in, from its [links][DesignLinksV1].
   *
   * **Carried, never posted to.** This is a permalink a person opens, not an endpoint a host may
   * call, and it is writable by any collaborator holding write access to the design. A relay that
   * knows how to talk to the chat platform can use it to choose a conversation; a host that treated
   * it as a destination would be letting a design grant aim it at any address that resolves.
   */
  public val thread: String? = null,
)

@Serializable
public data class DesignCommentWebhookThreadV1(
  public val id: String,
  /**
   * Where the thread is pinned, in words: the node id, `a mark`, or a point on the frame.
   *
   * A sentence rather than the anchor's three fields, because the reader is a person in a chat
   * window: "on node play-button" tells them what is being discussed and `{"markId": "m-4"}` does
   * not. Null for a thread about the design as a whole.
   */
  public val anchor: String? = null,
  public val comments: Int = 1,
  public val resolved: Boolean = false,
)

@Serializable
public data class DesignCommentWebhookCommentV1(
  /** The author's display name, else their actor id. Absent where the act has no named actor. */
  public val author: String? = null,
  /**
   * The actor the host's authorization layer established, which [author] is not.
   *
   * A display name is whatever the writer typed, so it is a label and never evidence. This is the
   * field a relay checks when it cares who really spoke.
   */
  @SerialName("authorId") public val authorId: String? = null,
  /** `human` or `agent`, as declared. Cosmetic here exactly as it is on the board. */
  @SerialName("authorKind") public val authorKind: String? = null,
  /** What was said, trimmed by the host to an excerpt rather than carried whole. */
  public val excerpt: String,
)
