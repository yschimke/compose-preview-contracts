package ee.schimke.composeai.catalog.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `catalog-read` level-2 bodies, exactly as compose-preview-server#2092 writes them.
 *
 * As in [CatalogReadTest], each golden is first pinned to the server's serializer through the
 * test-only DTO copies in [CatalogReadServerBodies.kt]. These bodies are typed whole, so they are
 * then decoded **strictly** (no `ignoreUnknownKeys`): every key the server sends has a field. The
 * decoded value re-encodes to the golden's bytes under both the server's `Json` and a plain one,
 * and decodes the same with every `null` left out, as a writer without `encodeDefaults` sends it.
 */
class CatalogReadLevel2Test {
  private val serverJson = Json { encodeDefaults = true }
  private val strictJson = Json
  private val readerJson = Json { ignoreUnknownKeys = true }

  @Test
  fun `goldens are what the server writes`() {
    val goldens = CatalogReadLevel2Goldens
    assertEquals(
      goldens.CAPABILITIES,
      serverJson.encodeToString(
        ServerCatalogCapabilitiesResponse.serializer(),
        goldens.serverCapabilities(),
      ),
    )
    goldens.serverHistories().forEach { (golden, dto) ->
      assertEquals(
        golden,
        serverJson.encodeToString(ServerCatalogHistoryResponse.serializer(), dto),
      )
    }
    goldens.serverPreviewHistories().forEach { (golden, dto) ->
      assertEquals(
        golden,
        serverJson.encodeToString(ServerPreviewHistoryResponse.serializer(), dto),
      )
    }
    goldens.serverGuidelines().forEach { (golden, dto) ->
      assertEquals(
        golden,
        serverJson.encodeToString(ServerPreviewGuidelinesResponse.serializer(), dto),
      )
    }
  }

  @Test
  fun `capabilities decode strictly`() {
    val body =
      decodeWhole(CatalogReadCapabilitiesV1.serializer(), CatalogReadLevel2Goldens.CAPABILITIES)
    assertEquals(CatalogReadCapabilitiesV1.SCHEMA, body.schema)
    assertEquals(CatalogReadGoldens.SERVED, body.revision)
    val (card, other) = body.previews
    assertEquals("card", card.id)
    assertEquals(listOf("a11y/atf", "compose/theme"), card.dataProductKinds)
    assertTrue(CatalogReadPreviewCapabilitiesV1.KIND_A11Y_HIERARCHY in card.productKinds)
    assertTrue(CatalogReadPreviewCapabilitiesV1.KIND_GUIDELINES_RESULT in card.productKinds)
    assertTrue(card.svgAvailable && card.a11yOverlay && card.guidelinesResult)
    assertEquals("pressed", card.state)
    assertEquals("dark", card.theme)
    assertNull(other.state)
    assertTrue(other.scrollAvailable && other.publishedTypography)
    assertEquals(
      listOf(CatalogReadPreviewCapabilitiesV1.KIND_COMPOSE_ANNOTATIONS),
      other.productKinds,
    )

    val built =
      CatalogReadCapabilitiesV1.Builder()
        .apply {
          revision = CatalogReadGoldens.SERVED
          previews =
            listOf(
              CatalogReadPreviewCapabilitiesV1.Builder("card")
                .apply {
                  dataProductKinds = listOf("a11y/atf", "compose/theme")
                  productKinds =
                    listOf("a11y/atf", "a11y/hierarchy", "compose/theme", "guidelines/result")
                  svgAvailable = true
                  state = "pressed"
                  theme = "dark"
                  a11yOverlay = true
                  guidelinesResult = true
                }
                .build(),
              other.newBuilder().build(),
            )
        }
        .build()
    assertEquals(body, built)
    assertEquals(body, body.newBuilder().build())
  }

  @Test
  fun `catalog history decodes strictly in every mode`() {
    val (published, local, none) =
      CatalogReadLevel2Goldens.serverHistories().map { (golden, _) ->
        decodeWhole(CatalogReadHistoryV1.serializer(), golden)
      }
    assertEquals(CatalogReadHistoryV1.MODE_PUBLISHED, published.mode)
    assertEquals(CatalogReadGoldens.SERVED, published.commit)
    assertEquals("yschimke/design", published.repo)
    assertEquals("design/m3", published.branch)
    assertTrue(published.publishesManifest)
    assertTrue(published.renderUrlTemplate!!.contains(CatalogReadHistoryV1.COMMIT_PLACEHOLDER))
    assertTrue(published.renderUrlTemplate!!.contains(CatalogReadHistoryV1.PATH_PLACEHOLDER))
    assertNull(published.reason)

    assertEquals(CatalogReadHistoryV1.MODE_LOCAL, local.mode)
    assertNull(local.commit)
    assertEquals(false, local.publishesManifest)

    assertEquals(CatalogReadHistoryV1.MODE_NONE, none.mode)
    assertTrue(none.reason!!.isNotEmpty())
    assertEquals(none, CatalogReadHistoryV1.Builder("none").apply { reason = none.reason }.build())
    assertEquals(published, published.newBuilder().build())
  }

  @Test
  fun `a preview timeline decodes strictly, and no timeline is null, not empty`() {
    val (timeline, unindexed, localEmpty) =
      CatalogReadLevel2Goldens.serverPreviewHistories().map { (golden, _) ->
        decodeWhole(CatalogReadPreviewHistoryV1.serializer(), golden)
      }
    assertEquals(CatalogReadPreviewHistoryV1.SCHEMA, timeline.schema)
    assertEquals("ProfilePreview", timeline.previewId)
    assertEquals("images/profile/ideal__default__dark.png", timeline.path)
    assertEquals(2, timeline.observations)
    assertEquals(true, timeline.unstable)
    assertEquals(3, timeline.flapCount)
    val versions = timeline.versions ?: fail("versions missing")
    assertEquals(listOf("a".repeat(40), "b".repeat(40)), versions.map { it.blob })
    assertEquals(listOf(1, 2), versions.map { it.commits })
    assertEquals("2ef7b877", versions[0].sourceSha)
    assertEquals(2, versions[1].occurrences)
    assertTrue(versions[0].renderUrl.startsWith("https://raw.githubusercontent.com/"))
    assertEquals(versions[1], versions[1].newBuilder().build())

    assertNull(unindexed.versions, "an empty list would read as never changed")
    assertTrue(unindexed.reason!!.isNotEmpty())
    assertNull(unindexed.path)

    assertEquals(CatalogReadHistoryV1.MODE_LOCAL, localEmpty.mode)
    assertEquals(emptyList(), localEmpty.versions)
    assertTrue(localEmpty.reason!!.isNotEmpty())
    assertEquals(timeline, timeline.newBuilder().build())
  }

  @Test
  fun `the guidelines body carries the record opaquely`() {
    val (found, missing) =
      CatalogReadLevel2Goldens.serverGuidelines().map { (golden, _) ->
        decodeWhole(CatalogReadGuidelinesV1.serializer(), golden)
      }
    assertEquals(CatalogReadGuidelinesV1.SCHEMA, found.schema)
    assertEquals(CatalogReadGuidelinesV1.KIND, found.kind)
    assertEquals("guidelines/result", found.kind)
    assertTrue(found.found)
    val record = found.record ?: fail("record missing")
    assertEquals(
      CatalogReadGuidelinesV1.RECORD_SCHEMA,
      record.getValue("schema").jsonPrimitive.content,
    )
    // The body's `revision` is the delivery commit; the record's is its own integer.
    assertEquals(CatalogReadGoldens.SERVED, found.revision)
    assertEquals(JsonPrimitive(0), record.getValue("revision"))

    assertEquals(false, missing.found)
    assertNull(missing.record)
    assertEquals(
      missing,
      CatalogReadGuidelinesV1.Builder("other").apply { revision = missing.revision }.build(),
    )
  }

  @Test
  fun `a body missing a required field is refused`() {
    assertFailsWith<SerializationException> {
      readerJson.decodeFromString(CatalogReadHistoryV1.serializer(), """{"schema":"x"}""")
    }
    assertFailsWith<SerializationException> {
      readerJson.decodeFromString(
        CatalogReadHistoryVersionV1.serializer(),
        """{"commit":"1","date":"d","blob":"b","commits":1}""",
      )
    }
  }

  @Test
  fun `the routes name their parameters`() {
    val perPreview =
      listOf(CatalogReadRoutesV1.PREVIEW_HISTORY_PATH, CatalogReadRoutesV1.GUIDELINES_PATH)
    val perCatalog = listOf(CatalogReadRoutesV1.CAPABILITIES_PATH, CatalogReadRoutesV1.HISTORY_PATH)
    (perPreview + perCatalog).forEach {
      assertTrue(it.startsWith("/{${CatalogReadRoutesV1.SYSTEM_PARAMETER}}/api/"), it)
    }
    perPreview.forEach {
      assertTrue(it.endsWith("/{${CatalogReadRoutesV1.PREVIEW_ID_PARAMETER}}"), it)
    }
    val roots =
      listOf(
        CatalogReadRoutesV1.ROOT_CAPABILITIES_PATH to CatalogReadRoutesV1.CAPABILITIES_PATH,
        CatalogReadRoutesV1.ROOT_HISTORY_PATH to CatalogReadRoutesV1.HISTORY_PATH,
        CatalogReadRoutesV1.ROOT_PREVIEW_HISTORY_PATH to CatalogReadRoutesV1.PREVIEW_HISTORY_PATH,
        CatalogReadRoutesV1.ROOT_GUIDELINES_PATH to CatalogReadRoutesV1.GUIDELINES_PATH,
      )
    roots.forEach { (root, path) -> assertEquals(path, "/{system}$root") }
  }

  /**
   * Decodes [golden] strictly, then checks it re-encodes to the same bytes with and without
   * `encodeDefaults`, and that a body with every `null` left out decodes to the same value.
   */
  private fun <T> decodeWhole(serializer: KSerializer<T>, golden: String): T {
    val value = strictJson.decodeFromString(serializer, golden)
    assertEquals(golden, serverJson.encodeToString(serializer, value), "server encoder")
    assertEquals(golden, strictJson.encodeToString(serializer, value), "plain encoder")
    val absent = withoutNulls(Json.parseToJsonElement(golden))
    assertEquals(value, strictJson.decodeFromJsonElement(serializer, absent), "nulls absent")
    return value
  }

  /**
   * [element] with every `null` dropped, except inside an opaque `record` (not this contract's).
   */
  private fun withoutNulls(element: JsonElement): JsonElement =
    when (element) {
      is JsonObject ->
        JsonObject(
          element
            .filterValues { it != JsonNull }
            .mapValues { (key, value) -> if (key == "record") value else withoutNulls(value) },
        )
      is JsonArray -> JsonArray(element.map(::withoutNulls))
      else -> element
    }
}

/**
 * #2092's bodies, built from the values its `ServeCatalogReadRoutesTest` serves, in the shapes
 * SERVER_UNBUNDLING.md step 4 documents.
 */
internal object CatalogReadLevel2Goldens {
  private const val SERVED = CatalogReadGoldens.SERVED
  private val NEWEST = "1".repeat(40)
  private val OLDER = "2".repeat(40)
  private val BLOB_A = "a".repeat(40)
  private val BLOB_B = "b".repeat(40)
  private const val REPO = "yschimke/design"
  private const val BRANCH = "design/m3"
  private const val MANIFEST_URL =
    "https://raw.githubusercontent.com/yschimke/design/design/m3/history.json"
  private const val RENDER_URL_TEMPLATE =
    "https://raw.githubusercontent.com/yschimke/design/{commit}/{path}"
  private const val PATH = "images/profile/ideal__default__dark.png"

  private const val NONE_REASON =
    "this catalog has no delivery-branch provenance and this server has no checkout to derive a " +
      "timeline from; an uploaded bundle carries no history"
  private const val UNINDEXED_REASON =
    "this catalog's publisher ships no history.json, or none naming this preview; " +
      "manifestUrl is where one would be if the branch grows one"
  private const val LOCAL_EMPTY_REASON =
    "the local delivery-branch timeline names fewer than two distinct renders for this " +
      "preview, so there is no change to show"

  fun serverCapabilities(): ServerCatalogCapabilitiesResponse =
    ServerCatalogCapabilitiesResponse(
      revision = SERVED,
      previews =
        listOf(
          ServerPreviewCapabilitiesDto(
            id = "card",
            dataProductKinds = listOf("a11y/atf", "compose/theme"),
            productKinds =
              listOf("a11y/atf", "a11y/hierarchy", "compose/theme", "guidelines/result"),
            svgAvailable = true,
            scrollAvailable = false,
            state = "pressed",
            theme = "dark",
            a11yOverlay = true,
            designAnnotations = false,
            publishedTypography = false,
            guidelinesResult = true,
          ),
          ServerPreviewCapabilitiesDto(
            id = "other",
            dataProductKinds = emptyList(),
            productKinds = listOf("compose/annotations"),
            svgAvailable = false,
            scrollAvailable = true,
            a11yOverlay = false,
            designAnnotations = false,
            publishedTypography = true,
            guidelinesResult = false,
          ),
        ),
    )

  val CAPABILITIES: String =
    """{"schema":"compose-preview-serve/capabilities/v1","revision":"$SERVED","previews":[""" +
      """{"id":"card","dataProductKinds":["a11y/atf","compose/theme"],""" +
      """"productKinds":["a11y/atf","a11y/hierarchy","compose/theme","guidelines/result"],""" +
      """"svgAvailable":true,"scrollAvailable":false,"state":"pressed","theme":"dark",""" +
      """"a11yOverlay":true,"designAnnotations":false,"publishedTypography":false,""" +
      """"guidelinesResult":true},""" +
      """{"id":"other","dataProductKinds":[],"productKinds":["compose/annotations"],""" +
      """"svgAvailable":false,"scrollAvailable":true,"state":null,"theme":null,""" +
      """"a11yOverlay":false,"designAnnotations":false,"publishedTypography":true,""" +
      """"guidelinesResult":false}]}"""

  /** Published, local and none, in that order. */
  fun serverHistories(): List<Pair<String, ServerCatalogHistoryResponse>> =
    listOf(
      ("""{"schema":"compose-preview-serve/history/v1","mode":"published","repo":"$REPO",""" +
        """"branch":"$BRANCH","commit":"$SERVED","manifestUrl":"$MANIFEST_URL",""" +
        """"renderUrlTemplate":"$RENDER_URL_TEMPLATE","publishesManifest":true,"reason":null}""") to
        ServerCatalogHistoryResponse(
          mode = "published",
          repo = REPO,
          branch = BRANCH,
          commit = SERVED,
          manifestUrl = MANIFEST_URL,
          renderUrlTemplate = RENDER_URL_TEMPLATE,
          publishesManifest = true,
        ),
      ("""{"schema":"compose-preview-serve/history/v1","mode":"local","repo":null,"branch":null,""" +
        """"commit":null,"manifestUrl":null,"renderUrlTemplate":null,"publishesManifest":false,""" +
        """"reason":null}""") to ServerCatalogHistoryResponse(mode = "local"),
      ("""{"schema":"compose-preview-serve/history/v1","mode":"none","repo":null,"branch":null,""" +
        """"commit":null,"manifestUrl":null,"renderUrlTemplate":null,"publishesManifest":false,""" +
        """"reason":"$NONE_REASON"}""") to
        ServerCatalogHistoryResponse(mode = "none", reason = NONE_REASON),
    )

  private fun rawUrl(commit: String) = "https://raw.githubusercontent.com/$REPO/$commit/$PATH"

  /** A published timeline, a published preview with none, and a local one too short to diff. */
  fun serverPreviewHistories(): List<Pair<String, ServerPreviewHistoryResponse>> =
    listOf(
      ("""{"schema":"compose-preview-serve/preview-history/v1","previewId":"ProfilePreview",""" +
        """"mode":"published","repo":"$REPO","branch":"$BRANCH","commit":"$SERVED",""" +
        """"manifestUrl":"$MANIFEST_URL","renderUrlTemplate":"$RENDER_URL_TEMPLATE",""" +
        """"reason":null,"path":"$PATH","observations":2,"unstable":true,"flapCount":3,""" +
        """"versions":[{"commit":"$NEWEST","date":"2026-08-27T10:58:22Z","blob":"$BLOB_A",""" +
        """"commits":1,"sourceSha":"2ef7b877","sinceCommit":null,"occurrences":null,""" +
        """"renderUrl":"${rawUrl(NEWEST)}"},""" +
        """{"commit":"$OLDER","date":"2026-08-27T07:18:15Z","blob":"$BLOB_B","commits":2,""" +
        """"sourceSha":null,"sinceCommit":null,"occurrences":2,"renderUrl":"${rawUrl(OLDER)}"}]}""") to
        ServerPreviewHistoryResponse(
          previewId = "ProfilePreview",
          mode = "published",
          repo = REPO,
          branch = BRANCH,
          commit = SERVED,
          manifestUrl = MANIFEST_URL,
          renderUrlTemplate = RENDER_URL_TEMPLATE,
          path = PATH,
          observations = 2,
          unstable = true,
          flapCount = 3,
          versions =
            listOf(
              ServerHistoryVersionDto(
                commit = NEWEST,
                date = "2026-08-27T10:58:22Z",
                blob = BLOB_A,
                commits = 1,
                sourceSha = "2ef7b877",
                renderUrl = rawUrl(NEWEST),
              ),
              ServerHistoryVersionDto(
                commit = OLDER,
                date = "2026-08-27T07:18:15Z",
                blob = BLOB_B,
                commits = 2,
                occurrences = 2,
                renderUrl = rawUrl(OLDER),
              ),
            ),
        ),
      ("""{"schema":"compose-preview-serve/preview-history/v1","previewId":"Unindexed",""" +
        """"mode":"published","repo":"$REPO","branch":"$BRANCH","commit":"$SERVED",""" +
        """"manifestUrl":"$MANIFEST_URL","renderUrlTemplate":"$RENDER_URL_TEMPLATE",""" +
        """"reason":"$UNINDEXED_REASON","path":null,"observations":null,"unstable":null,""" +
        """"flapCount":null,"versions":null}""") to
        ServerPreviewHistoryResponse(
          previewId = "Unindexed",
          mode = "published",
          repo = REPO,
          branch = BRANCH,
          commit = SERVED,
          manifestUrl = MANIFEST_URL,
          renderUrlTemplate = RENDER_URL_TEMPLATE,
          reason = UNINDEXED_REASON,
        ),
      ("""{"schema":"compose-preview-serve/preview-history/v1","previewId":"card",""" +
        """"mode":"local","repo":null,"branch":null,"commit":null,"manifestUrl":null,""" +
        """"renderUrlTemplate":null,"reason":"$LOCAL_EMPTY_REASON","path":null,""" +
        """"observations":null,"unstable":null,"flapCount":null,"versions":[]}""") to
        ServerPreviewHistoryResponse(
          previewId = "card",
          mode = "local",
          versions = emptyList(),
          reason = LOCAL_EMPTY_REASON,
        ),
    )

  /**
   * #2092's test record, `GuidelineRecordV1.Builder(revision = 0, model = …, rulesVersion = 3, …)`
   * with one failing verdict, as design-guidelines-protocol's serializer writes it under the
   * server's `Json` (`encodeDefaults = true`): its `@EncodeDefault(NEVER)` fields left out.
   */
  const val RECORD: String =
    """{"schema":"compose-ui-builder/guidelines-result/v1","designId":null,"revision":0,""" +
      """"model":"deepseek/deepseek-v4.1-flash","rulesVersion":3,""" +
      """"asked":["wear.layout.responsive-width"],"verdicts":[""" +
      """{"ruleId":"wear.layout.responsive-width","verdict":"fail","confidence":0.0,""" +
      """"nodeIds":[],"reason":"Fixed width."}],"ranBy":null,"recordedAtEpochMillis":null}"""

  /** Found, then missing. */
  fun serverGuidelines(): List<Pair<String, ServerPreviewGuidelinesResponse>> =
    listOf(
      ("""{"schema":"compose-preview-serve/guidelines/v1","revision":"$SERVED",""" +
        """"kind":"guidelines/result","previewId":"card","found":true,"record":$RECORD}""") to
        ServerPreviewGuidelinesResponse(
          revision = SERVED,
          previewId = "card",
          found = true,
          record = Json.parseToJsonElement(RECORD),
        ),
      ("""{"schema":"compose-preview-serve/guidelines/v1","revision":"$SERVED",""" +
        """"kind":"guidelines/result","previewId":"other","found":false,"record":null}""") to
        ServerPreviewGuidelinesResponse(revision = SERVED, previewId = "other", found = false),
    )
}
