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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The catalog read routes' bodies, exactly as compose-preview-server 3.192.0 writes them.
 *
 * Each golden is first pinned to the server's serializer: [CatalogReadServerBodies.kt] copies the
 * server's private DTOs, and encoding them with the server's `Json` must give the golden's bytes.
 * Then the golden is decoded as a contract type the way a reader decodes it (unknown keys ignored),
 * and [assertProjection] checks that every key the contract type writes is in the server's body
 * with the same value. Together: today's server already conforms, and the contract drops nothing
 * the server says about the fields it types.
 */
class CatalogReadTest {
  /** `ServeHttpServer`'s companion `JSON`: what writes both bodies. */
  private val serverJson = Json { encodeDefaults = true }

  /** How a catalog reader decodes: the server sends more than the contract types. */
  private val readerJson = Json { ignoreUnknownKeys = true }

  @Test
  fun `status golden is what the server writes`() {
    assertEquals(
      CatalogReadGoldens.STATUS,
      serverJson.encodeToString(
        ServerStatusResponse.serializer(),
        CatalogReadGoldens.serverStatus(),
      ),
    )
  }

  @Test
  fun `status decodes as the catalog list`() {
    val status = decodeProjected(CatalogReadStatusV1.serializer(), CatalogReadGoldens.STATUS)
    assertEquals(CatalogReadStatusV1.SCHEMA, status.schema)
    assertEquals("3.192.0", status.version)
    assertTrue(status.public)
    assertEquals(CatalogReadStatusV1.STATUS_DEGRADED, status.status)
    assertEquals(listOf("wear-m3", "remote-m3", "tiles"), status.catalogList.map { it.id })

    val (resident, idle, pending) = status.catalogList
    assertEquals("Wear Material 3", resident.title)
    assertEquals(412, resident.previews)
    assertEquals(CatalogReadCatalogV1.LOADED, resident.loadState)
    assertEquals("yschimke/compose-ai-tools", resident.repo)
    assertEquals("design-artifacts/wear-m3", resident.branch)

    // An idle catalog reports a last-known snapshot; one never resident has no count.
    assertTrue(idle.metaStale)
    assertNull(idle.previews)
    assertEquals(CatalogReadCatalogV1.STALE, idle.loadState)
    assertEquals("HTTP 502 reading catalog.json", idle.loadError)

    assertEquals(CatalogReadCatalogV1.PENDING, pending.loadState)
    assertEquals(false, pending.listed)
    assertEquals("/tiles/", pending.path)
  }

  @Test
  fun `previews golden is what the server writes`() {
    assertEquals(
      CatalogReadGoldens.PREVIEWS,
      serverJson.encodeToString(
        ServerPreviewsResponse.serializer(),
        CatalogReadGoldens.serverPreviews(),
      ),
    )
  }

  @Test
  fun `previews decode with knobs and watch-face renderers`() {
    val body = decodeProjected(CatalogReadPreviewsV1.serializer(), CatalogReadGoldens.PREVIEWS)
    assertEquals(CatalogReadRoutesV1.SERVE_SCHEMA, body.schema)
    assertEquals("Wear Material 3", body.module)
    assertEquals("2.103.0", body.catalogVersion)
    assertEquals("branch:yschimke/compose-ai-tools@design-artifacts/wear-m3", body.trust)
    assertEquals("no-live-bundle", body.degradations.single().code)

    val (button, watchFace) = body.previews
    assertEquals("ee.schimke.wear.ButtonPreviews.EdgeButton", button.id)
    assertEquals(listOf(CatalogReadPreviewV1.MODE_SNAPSHOT), button.modes)
    assertEquals(listOf("label", "items[0]"), button.overrides.map { it.seedKey })
    assertEquals(listOf("string", "int"), button.overrides.map { it.type })
    assertTrue(button.remoteCompose)
    assertNull(button.watchFace)

    val payload = watchFace.watchFace ?: fail("watch face payload missing")
    assertEquals(1, payload.getValue("userConfigurations").jsonArray.size)
    assertEquals(0, payload.getValue("slots").jsonArray.size)
    assertEquals(listOf("dwf-apk", "dwf-rc"), watchFace.watchFaceRenderers.map { it.id })
    val (apk, rc) = watchFace.watchFaceRenderers
    assertEquals(false, apk.available)
    assertTrue(apk.unavailableReason!!.startsWith("catalog-native-render-disabled"))
    assertTrue(rc.available && rc.isDefault)
  }

  @Test
  fun `bodies built through the builders write the server's bytes`() {
    // A second host (or a test double) adopting the types writes what the server writes, whatever
    // its encoder's settings: every server default is @EncodeDefault.
    val plain = Json
    val catalog =
      CatalogReadCatalogV1.Builder("tiles")
        .apply {
          listed = false
          loadState = CatalogReadCatalogV1.PENDING
        }
        .build()
    val encoded = plain.encodeToJsonElement(CatalogReadCatalogV1.serializer(), catalog)
    val server =
      Json.parseToJsonElement(CatalogReadGoldens.STATUS)
        .jsonObject
        .getValue("catalogList")
        .jsonArray[2]
    assertProjection(server, encoded, "catalogList[2]")
    assertEquals(
      (server as JsonObject).keys - setOf("themeOptimization", "renderCache"),
      (encoded as JsonObject).keys,
    )
  }

  @Test
  fun `a body with no catalog list is still a status`() {
    val status =
      readerJson.decodeFromString(
        CatalogReadStatusV1.serializer(),
        """{"schema":"compose-preview-serve/status/v1","version":"3.192.0","public":false,"status":"ok"}""",
      )
    assertEquals(emptyList(), status.catalogList)
  }

  @Test
  fun `a preview without an id is refused`() {
    assertFailsWith<SerializationException> {
      readerJson.decodeFromString(CatalogReadPreviewV1.serializer(), """{"label":"x"}""")
    }
  }

  @Test
  fun `the api is catalog-read level 1`() {
    assertEquals("catalog-read", CatalogReadRoutesV1.API_NAME)
    assertEquals(1, CatalogReadRoutesV1.API_LEVEL)
    assertEquals("compose-preview-serve/v3", CatalogReadRoutesV1.SERVE_SCHEMA)
  }

  private fun <T> decodeProjected(serializer: KSerializer<T>, body: String): T {
    val value = readerJson.decodeFromString(serializer, body)
    assertProjection(
      Json.parseToJsonElement(body),
      serverJson.encodeToJsonElement(serializer, value),
    )
    return value
  }

  /**
   * Every key [typed] has is in [server] with an equal value, recursively: the contract type is a
   * faithful projection of the server's body.
   */
  private fun assertProjection(server: JsonElement, typed: JsonElement, path: String = "$") {
    when (typed) {
      is JsonObject -> {
        val obj = server as? JsonObject ?: fail("$path: server sent ${server}, contract an object")
        typed.forEach { (key, value) ->
          val sent = obj[key] ?: fail("$path.$key: the server does not send this key")
          assertProjection(sent, value, "$path.$key")
        }
      }
      is JsonArray -> {
        val array = server as? JsonArray ?: fail("$path: server sent ${server}, contract an array")
        assertEquals(array.size, typed.size, "$path: array size")
        typed.forEachIndexed { i, value -> assertProjection(array[i], value, "$path[$i]") }
      }
      else -> assertEquals(server, typed, path)
    }
  }
}

internal object CatalogReadGoldens {
  private const val APK_DISABLED =
    "catalog-native-render-disabled: wfRenderer 'dwf-apk' renders a catalog's watch face on the " +
      "Android watch-face runtime, which this server has switched off for catalog renders"

  fun serverStatus(): ServerStatusResponse =
    ServerStatusResponse(
      version = "3.192.0",
      public = true,
      status = "degraded",
      uptimeSeconds = 5400,
      catalogs =
        ServerCatalogSummaryDto(
          total = 3,
          listed = 2,
          unlisted = 1,
          trusted = 1,
          degraded = 1,
          loaded = 2,
          failed = 0,
          pending = 1,
        ),
      daemons =
        ServerDaemonSummaryDto(
          known = 2,
          running = 0,
          activeStreams = 0,
          liveSeatsTotal = 4,
          liveSeatsAvailable = 4,
          liveSeatsUnbounded = false,
        ),
      config =
        ServerConfigDto(
          host = "0.0.0.0",
          port = 8080,
          allowRenderTrusted = true,
          trustStore = true,
          acceptBundles = false,
          catalogRefreshSeconds = 300,
          maxConcurrentRenders = 2,
          liveSeats = 4,
        ),
      catalogList =
        listOf(
          ServerCatalogDto(
            id = "wear-m3",
            listed = true,
            title = "Wear Material 3",
            trust = "branch:yschimke/compose-ai-tools@design-artifacts/wear-m3",
            previews = 412,
            live = true,
            running = false,
            repo = "yschimke/compose-ai-tools",
            branch = "design-artifacts/wear-m3",
            generatedAt = "2026-11-20T09:12:44Z",
            composeAiToolsVersion = "2.103.0",
            path = "/wear-m3/",
            lastLoadAttemptEpochMillis = 1763629964000,
          ),
          ServerCatalogDto(
            id = "remote-m3",
            listed = true,
            title = "Remote Material 3",
            trust = "unverified",
            live = false,
            running = false,
            degradation = "no liveBundle: this catalog serves published renders only",
            repo = "yschimke/remote-material3",
            branch = "design-artifacts/remote-m3",
            path = "/remote-m3/",
            metaStale = true,
            loadState = "stale",
            loadError = "HTTP 502 reading catalog.json",
            lastLoadAttemptEpochMillis = 1763630264000,
          ),
          ServerCatalogDto(
            id = "tiles",
            listed = false,
            live = false,
            running = false,
            path = "/tiles/",
            loadState = "pending",
          ),
        ),
      runningServers = emptyList(),
      recentDaemonFailures = emptyList(),
    )

  const val STATUS: String =
    """{"schema":"compose-preview-serve/status/v1","version":"3.192.0","public":true,""" +
      """"status":"degraded","uptimeSeconds":5400,"catalogs":{"total":3,"listed":2,"unlisted":1,""" +
      """"trusted":1,"degraded":1,"loaded":2,"failed":0,"pending":1},"daemons":{"known":2,""" +
      """"running":0,"activeStreams":0,"liveSeatsTotal":4,"liveSeatsAvailable":4,""" +
      """"liveSeatsUnbounded":false,"perPreviewSeatsTotal":0,"perPreviewSeatsAvailable":0,""" +
      """"liveSeatRefusals":0,"liveSeatRefusalsUnverified":0,"leasedSessions":[],""" +
      """"busyLeasedSessions":[],"spareSandboxes":null,"rcPlayerDaemon":null,""" +
      """"rcDesktopPlayerDaemon":null},"config":{"host":"0.0.0.0","port":8080,""" +
      """"allowRenderTrusted":true,"trustStore":true,"acceptBundles":false,"acceptDocs":false,""" +
      """"docTtlSeconds":0,"acceptImages":false,"imageTtlSeconds":0,"imageUploadRepository":null,""" +
      """"imagesHeld":0,"imageBytesHeld":0,"catalogRefreshSeconds":300,"catalogRegistries":[],""" +
      """"uiBuilderCatalogProblems":{},"maxConcurrentRenders":2,"liveSeats":4},"catalogList":[""" +
      """{"id":"wear-m3","listed":true,"title":"Wear Material 3",""" +
      """"trust":"branch:yschimke/compose-ai-tools@design-artifacts/wear-m3","previews":412,""" +
      """"failedRenders":0,"deferredPreviews":0,"live":true,"running":false,"degradation":null,""" +
      """"repo":"yschimke/compose-ai-tools","branch":"design-artifacts/wear-m3",""" +
      """"generatedAt":"2026-11-20T09:12:44Z","composeAiToolsVersion":"2.103.0",""" +
      """"designParityVersion":null,"path":"/wear-m3/","metaStale":false,"loadState":"loaded",""" +
      """"loadError":null,"lastLoadAttemptEpochMillis":1763629964000,"themeOptimization":null,""" +
      """"renderCache":null},""" +
      """{"id":"remote-m3","listed":true,"title":"Remote Material 3","trust":"unverified",""" +
      """"previews":null,"failedRenders":0,"deferredPreviews":0,"live":false,"running":false,""" +
      """"degradation":"no liveBundle: this catalog serves published renders only",""" +
      """"repo":"yschimke/remote-material3","branch":"design-artifacts/remote-m3",""" +
      """"generatedAt":null,"composeAiToolsVersion":null,"designParityVersion":null,""" +
      """"path":"/remote-m3/","metaStale":true,"loadState":"stale",""" +
      """"loadError":"HTTP 502 reading catalog.json","lastLoadAttemptEpochMillis":1763630264000,""" +
      """"themeOptimization":null,"renderCache":null},""" +
      """{"id":"tiles","listed":false,"title":null,"trust":null,"previews":null,"failedRenders":0,""" +
      """"deferredPreviews":0,"live":false,"running":false,"degradation":null,"repo":null,""" +
      """"branch":null,"generatedAt":null,"composeAiToolsVersion":null,"designParityVersion":null,""" +
      """"path":"/tiles/","metaStale":false,"loadState":"pending","loadError":null,""" +
      """"lastLoadAttemptEpochMillis":null,"themeOptimization":null,"renderCache":null}],""" +
      """"runningServers":[],"recentDaemonFailures":[],"branchFetch":null,"themeOptimizer":null,""" +
      """"themeCache":null,"catalogCache":null,"renderStats":null,"liveFrames":null,""" +
      """"playground":null,"agentAccess":null,"uiBuilder":null,"featureFlags":null,""" +
      """"processes":null}"""

  /** compose-ai-tools' `WatchFaceComplicationsPayload`, one user configuration, slots trimmed. */
  private val watchFacePayload: JsonObject = buildJsonObject {
    put("previewId", "ee.schimke.wear.faces.AnalogFace")
    put("dwfAssetPath", "watchface/analog/watchface.xml")
    put("widthPx", 450)
    put("heightPx", 450)
    put("clipShape", "CIRCLE")
    put("previewTime", "10:09:30")
    put("isAmbient", false)
    putJsonArray("userConfigurations") {
      add(
        buildJsonObject {
          put("id", "theme")
          put("kind", "list")
          put("displayName", "Theme")
          put("defaultValue", "blue")
          put("activeValue", "blue")
          putJsonArray("options") {
            add(JsonPrimitive("blue"))
            add(JsonPrimitive("amber"))
          }
          put("optionColors", JsonObject(emptyMap()))
        },
      )
    }
    putJsonArray("slots") {}
  }

  fun serverPreviews(): ServerPreviewsResponse =
    ServerPreviewsResponse(
      module = "Wear Material 3",
      catalogVersion = "2.103.0",
      trust = "branch:yschimke/compose-ai-tools@design-artifacts/wear-m3",
      degradations =
        listOf(
          ServerDegradationDto(
            "no-live-bundle",
            "this catalog publishes no liveBundle, so it serves published renders only",
          ),
        ),
      views = 17,
      previews =
        listOf(
          ServerPreviewDto(
            id = "ee.schimke.wear.ButtonPreviews.EdgeButton",
            label = "EdgeButton",
            modes = listOf("snapshot"),
            overrides =
              listOf(
                Json.parseToJsonElement(PREVIEW_OVERRIDE_LABEL),
                Json.parseToJsonElement(PREVIEW_OVERRIDE_ITEMS),
              ),
            views = 4,
            remoteCompose = true,
          ),
          ServerPreviewDto(
            id = "ee.schimke.wear.faces.AnalogFace",
            label = "AnalogFace",
            modes = listOf("snapshot", "live"),
            watchFace = watchFacePayload,
            watchFaceRenderers =
              listOf(
                ServerWatchFaceRendererInfo(
                  id = "dwf-apk",
                  label = "DWF Renderer APK (Launch)",
                  badgeLabel = "dwf → apk",
                  available = false,
                  unavailableReason = APK_DISABLED,
                ),
                ServerWatchFaceRendererInfo(
                  id = "dwf-rc",
                  label = "DWF RemoteCompose",
                  badgeLabel = "dwf → rc-jvm",
                  available = true,
                  supportsLiveOverrides = true,
                  isDefault = true,
                ),
              ),
          ),
        ),
    )

  val PREVIEWS: String =
    """{"schema":"compose-preview-serve/v3","module":"Wear Material 3","catalogVersion":"2.103.0",""" +
      """"trust":"branch:yschimke/compose-ai-tools@design-artifacts/wear-m3","degradations":[""" +
      """{"code":"no-live-bundle","detail":"this catalog publishes no liveBundle, so it serves """ +
      """published renders only"}],"views":17,"previews":[""" +
      """{"id":"ee.schimke.wear.ButtonPreviews.EdgeButton","label":"EdgeButton",""" +
      """"modes":["snapshot"],"overrides":[""" +
      PREVIEW_OVERRIDE_LABEL +
      "," +
      PREVIEW_OVERRIDE_ITEMS +
      """],"remoteComposeKnobs":[],"spatial":false,"liveOnly":false,"views":4,""" +
      """"remoteCompose":true,"watchFace":null,"watchFaceRenderers":[]},""" +
      """{"id":"ee.schimke.wear.faces.AnalogFace","label":"AnalogFace",""" +
      """"modes":["snapshot","live"],"overrides":[],"remoteComposeKnobs":[],"spatial":false,""" +
      """"liveOnly":false,"views":0,"remoteCompose":false,"watchFace":""" +
      """{"previewId":"ee.schimke.wear.faces.AnalogFace",""" +
      """"dwfAssetPath":"watchface/analog/watchface.xml","widthPx":450,"heightPx":450,""" +
      """"clipShape":"CIRCLE","previewTime":"10:09:30","isAmbient":false,"userConfigurations":[""" +
      """{"id":"theme","kind":"list","displayName":"Theme","defaultValue":"blue",""" +
      """"activeValue":"blue","options":["blue","amber"],"optionColors":{}}],"slots":[]},""" +
      """"watchFaceRenderers":[{"id":"dwf-apk","label":"DWF Renderer APK (Launch)",""" +
      """"badgeLabel":"dwf → apk","available":false,"unavailableReason":"$APK_DISABLED",""" +
      """"supportsLiveOverrides":false,"isDefault":false},{"id":"dwf-rc",""" +
      """"label":"DWF RemoteCompose","badgeLabel":"dwf → rc-jvm","available":true,""" +
      """"unavailableReason":null,"supportsLiveOverrides":true,"isDefault":true}]}]}"""

  /**
   * data-preview-overrides-core's `PreviewOverrideDeclaration` as the server writes it, every
   * default spelled out: `label` a string knob, `items[0]` one instance of a repeated int knob.
   */
  private const val PREVIEW_OVERRIDE_LABEL: String =
    """{"key":"label","type":"string","label":"Label","default":{"kind":"string","value":"Tap me"},""" +
      """"current":null,"index":null,"suggestions":[],"googleFonts":false,"options":[],""" +
      """"optionsExhaustive":false}"""

  private const val PREVIEW_OVERRIDE_ITEMS: String =
    """{"key":"items","type":"int","label":"items","default":{"kind":"int","value":3},""" +
      """"current":null,"index":0,"suggestions":[],"googleFonts":false,"options":[],""" +
      """"optionsExhaustive":false}"""
}
