package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * A file of Remote Compose documents to render, each with its own data: what `compose-preview
 * render-cases` reads. A producer that knows what to draw (a server-driven UI catalog's every
 * template with each example, a design tool's export) writes one; the renderer plays each case as a
 * carried document ([RemoteComposeOverride.documentBase64]) on the platform's own players.
 *
 * Its own file format, not a daemon message: a [RemoteDocumentCaseV1.document] is a path relative
 * to this file. In the daemon's own vocabulary, so a producer and the renderer agree without either
 * knowing the other: [RemoteNamedValue] for values, [RemoteComposeProfile] for the profile.
 *
 * ```
 * { "schema": "compose-preview/remote-document-cases/v1",
 *   "cases": [ { "id": "banner/in-app/default/dark", "document": "documents/banner/in-app.rc",
 *                "widthDp": 360, "heightDp": 160, "dark": true, "profile": "androidx7",
 *                "namedValues": { "headline": { "kind": "string", "value": "Hello" } } } ] }
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class RemoteDocumentCasesV1
internal constructor(
  /** [SCHEMA]: a reader refuses a file that names another. Always written. */
  @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schema: String = SCHEMA,
  val cases: List<RemoteDocumentCaseV1>,
) {
  /** Builds a [RemoteDocumentCasesV1]; the constructor is internal so a field can be added. */
  public class Builder(cases: List<RemoteDocumentCaseV1>) {
    public var cases: List<RemoteDocumentCaseV1> = cases

    public fun build(): RemoteDocumentCasesV1 = RemoteDocumentCasesV1(SCHEMA, cases)
  }

  /** This file as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(cases)

  public companion object {
    public const val SCHEMA: String = "compose-preview/remote-document-cases/v1"
  }
}

/**
 * One document to render with its data.
 *
 * @property id names the case's image: `<id>.png`, a `/` making directories. Unique in its file.
 * @property document the `.rc` file, relative to the cases file.
 * @property widthDp the size it is drawn at.
 * @property dark the platform's dark mode, or null for the renderer's default.
 * @property profile the platform player it plays on (a launcher widget's, a watch's), or null for
 *   the renderer's default.
 * @property namedValues bound before the first frame, by their names in the document.
 * @property background `#AARRGGBB` drawn behind the document, which may have no background of its
 *   own, or null for the renderer's.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class RemoteDocumentCaseV1
internal constructor(
  val id: String,
  val document: String,
  val widthDp: Int,
  val heightDp: Int,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val dark: Boolean? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val profile: RemoteComposeProfile? = null,
  val namedValues: Map<String, RemoteNamedValue> = emptyMap(),
  @EncodeDefault(EncodeDefault.Mode.NEVER) val background: String? = null,
) {
  /** Builds a [RemoteDocumentCaseV1]; the constructor is internal so a field can be added. */
  public class Builder(id: String, document: String, widthDp: Int, heightDp: Int) {
    public var id: String = id
    public var document: String = document
    public var widthDp: Int = widthDp
    public var heightDp: Int = heightDp
    public var dark: Boolean? = null
    public var profile: RemoteComposeProfile? = null
    public var namedValues: Map<String, RemoteNamedValue> = emptyMap()
    public var background: String? = null

    public fun build(): RemoteDocumentCaseV1 =
      RemoteDocumentCaseV1(id, document, widthDp, heightDp, dark, profile, namedValues, background)
  }

  /** This case as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id, document, widthDp, heightDp).also {
      it.dark = dark
      it.profile = profile
      it.namedValues = namedValues
      it.background = background
    }
}
