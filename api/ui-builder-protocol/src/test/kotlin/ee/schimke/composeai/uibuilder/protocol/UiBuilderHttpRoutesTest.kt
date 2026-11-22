package ee.schimke.composeai.uibuilder.protocol

import java.io.File
import kotlin.test.assertFailsWith
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bodies on the routes [UiBuilderHttpRoutesV1] names, exactly as each side writes them today.
 *
 * Server bodies are compose-preview-server 3.192.0's, encoded with the server's own `Json`
 * instances: [serverJson] is `UI_BUILDER_JSON` in `ServeUiBuilderRoutes.kt` (every
 * `/api/ui-builder/v1` body), and [adminJson] is `ServeHttpServer`'s companion `JSON` (the admin
 * listing). Each server golden must decode strictly and re-encode to the same bytes, so the server
 * already conforms to the types here.
 *
 * Client bodies are what ha-designs (yschimke/homeassistant-remotecompose `design-server`,
 * `UiBuilder.kt`) sends, built the way its `buildJsonObject` calls build them. Each must decode the
 * way the server decodes it: strictly, unknown keys refused.
 */
internal object HttpGoldens {
  /** `UiBuilderSeed.document` for a one-light card: what ha-designs `PUT`s. */
  const val HA_DESIGNS_SEED_DOCUMENT: String =
    """{"schema":"compose-ui-builder-document/v1-candidate","id":"card-light-m1abc2","title":"light",""" +
      """"revision":0,"catalogPin":{"systemId":"remote-m3","catalogRevision":"wear-widget-scaffolds-v1",""" +
      """"capabilityDigest":"candidate","nativeRuntimeId":"candidate"},"environment":{"widthDp":360,""" +
      """"heightDp":224,"density":2,"theme":"light","locale":"en-US","fontScale":1,"layoutDirection":"ltr"},""" +
      """"stateVariables":{"card.title":{"type":"text","valueType":"string","initialValue":"Kitchen",""" +
      """"persistence":"design"},"light0.name":{"type":"text","valueType":"string","initialValue":"Ceiling",""" +
      """"persistence":"design"},"light0.state":{"type":"text","valueType":"string","initialValue":"on",""" +
      """"persistence":"design"},"light0.is_on":{"type":"value","valueType":"bool","initialValue":true,""" +
      """"persistence":"design"}},"roots":["root"],"nodes":{""" +
      """"title":{"id":"title","componentId":"remote-m3/remote-text","properties":{"text":{"type":"state",""" +
      """"variable":"card.title"},"style":{"type":"enum","value":"titleLarge"},"maxLines":{"type":"int",""" +
      """"value":1}},"modifiers":[],"slots":{}},""" +
      """"name0":{"id":"name0","componentId":"remote-m3/remote-text","properties":{"text":{"type":"state",""" +
      """"variable":"light0.name"},"style":{"type":"enum","value":"titleMedium"},"maxLines":{"type":"int",""" +
      """"value":1}},"modifiers":[],"slots":{}},""" +
      """"state0":{"id":"state0","componentId":"remote-m3/remote-text","properties":{"text":{"type":"state",""" +
      """"variable":"light0.state"},"style":{"type":"enum","value":"bodyMedium"},"maxLines":{"type":"int",""" +
      """"value":1}},"modifiers":[],"slots":{}},""" +
      """"text0":{"id":"text0","componentId":"layout/column","properties":{"weight":{"type":"float",""" +
      """"value":1.0}},"modifiers":[],"slots":{"children":["name0","state0"]}},""" +
      """"switch-label0":{"id":"switch-label0","componentId":"remote-m3/remote-text","properties":{"text":""" +
      """{"type":"state","variable":"light0.state"},"style":{"type":"enum","value":"labelMedium"},""" +
      """"maxLines":{"type":"int","value":1}},"modifiers":[],"slots":{}},""" +
      """"switch0":{"id":"switch0","componentId":"remote-m3/remote-switch-button","properties":{"checked":""" +
      """{"type":"state","variable":"light0.is_on"}},"modifiers":[],"slots":{"label":["switch-label0"],""" +
      """"icon":[],"secondaryLabel":[]}},""" +
      """"row0":{"id":"row0","componentId":"layout/collapsible-row","properties":{"horizontalSpacingDp":""" +
      """{"type":"float","value":12.0},"verticalAlignment":{"type":"enum","value":"center"}},"modifiers":""" +
      """[{"type":"fillMaxWidth"}],"slots":{"children":["text0","switch0"]}},""" +
      """"column":{"id":"column","componentId":"layout/collapsible-column","properties":{"verticalSpacingDp":""" +
      """{"type":"float","value":8.0}},"modifiers":[{"type":"fillMaxWidth"}],"slots":{"children":""" +
      """["title","row0"]}},""" +
      """"root":{"id":"root","componentId":"remote-m3/remote-card","properties":{},"modifiers":""" +
      """[{"type":"fillMaxWidth"}],"slots":{"content":["column"]}}}}"""

  /** ha-designs `editors()`: the access lookup it makes for a shared design. */
  const val HA_DESIGNS_GET_DESIGN_ACCESS: String =
    """{"schemaVersion":1,"requestId":"q8Zp1Xy2","actorId":"operator",""" +
      """"request":{"type":"getDesignAccess","designId":"card-light-m1abc2"}}"""

  /** ha-designs `servedPin()` and `dashboardCatalog()`. */
  const val HA_DESIGNS_LIST_CATALOGS: String =
    """{"schemaVersion":1,"requestId":"q8Zp1Xy3","actorId":"operator","request":{"type":"listCatalogs"}}"""

  /** The server's answer to [HA_DESIGNS_GET_DESIGN_ACCESS] for a design shared with one editor. */
  const val SERVER_DESIGN_ACCESS_RESPONSE: String =
    """{"schemaVersion":1,"requestId":"q8Zp1Xy2","response":{"type":"designAccess",""" +
      """"designId":"card-light-m1abc2","access":{"accessRevision":1,"ownerActorId":"operator",""" +
      """"actorGrants":[{"actorId":"github:octocat","role":"editor","allowedActions":["read","write",""" +
      """"export"],"grantedByActorId":"operator","grantedAtEpochMillis":1750000020000}],""" +
      """"shareLinks":[]}}}"""

  /** An envelope that did not decode: `requestId` is the literal `invalid`. */
  const val SERVER_INVALID_ENVELOPE: String =
    """{"schemaVersion":1,"requestId":"invalid","response":{"type":"error","error":""" +
      """{"code":"badRequest","message":"invalid UI-builder request envelope","retryable":false}}}"""

  /** No credential: `401` with `WWW-Authenticate: Bearer`. */
  const val SERVER_UNAUTHORIZED_ENVELOPE: String =
    """{"schemaVersion":1,"requestId":"q8Zp1Xy3","response":{"type":"error","error":""" +
      """{"code":"unauthorized","message":"authentication is required","retryable":false}}}"""

  /**
   * `actorId` other than the authenticated actor: code `unauthorized`, but HTTP `403`. The only
   * place the code and the status disagree.
   */
  const val SERVER_ACTOR_MISMATCH_ENVELOPE: String =
    """{"schemaVersion":1,"requestId":"q8Zp1Xy3","response":{"type":"error","error":""" +
      """{"code":"unauthorized","message":"request actor does not match the authenticated actor",""" +
      """"retryable":false}}}"""

  /** The operator token on a host without GitHub sign-in. */
  const val SERVER_IDENTITY_OPERATOR: String =
    """{"schemaVersion":1,"designVisibilitySupported":true,"actorId":"operator","signedIn":true,""" +
      """"canWrite":true}"""

  /** The operator token on a host with GitHub sign-in and no session: still offered a sign-in. */
  const val SERVER_IDENTITY_OPERATOR_WITH_SIGN_IN: String =
    """{"schemaVersion":1,"designVisibilitySupported":true,"actorId":"operator","signedIn":true,""" +
      """"canWrite":true,"signInUrl":"/auth/github/start?return=%2Fui-builder"}"""

  /** A signed-in account that may read but not write. */
  const val SERVER_IDENTITY_READER: String =
    """{"schemaVersion":1,"designVisibilitySupported":true,"actorId":"github:visitor","signedIn":true,""" +
      """"canWrite":false,"writeDeniedReason":"Creating a design needs write access to """ +
      """yschimke/homeassistant-remotecompose, which the account you are signed in with (visitor) """ +
      """does not have. Ask an operator to add you, or sign in with an account that has it."}"""

  /** A signed-out visitor on a host serving public designs. */
  const val SERVER_IDENTITY_ANONYMOUS: String =
    """{"schemaVersion":1,"designVisibilitySupported":true,"actorId":"anonymous:visitor",""" +
      """"signedIn":false,"canWrite":false,"writeDeniedReason":"Sign in with GitHub to create and """ +
      """edit designs.","signInUrl":"/auth/github/start?return=%2Fui-builder"}"""

  /** `401` from the identity route on a host with GitHub sign-in. */
  const val SERVER_IDENTITY_REFUSAL: String =
    """{"schemaVersion":1,"message":"authentication is required",""" +
      """"signInUrl":"/auth/github/start?return=%2Fui-builder"}"""

  /** `401` from the identity route on a host without GitHub sign-in. */
  const val SERVER_IDENTITY_REFUSAL_NO_SIGN_IN: String =
    """{"schemaVersion":1,"message":"authentication is required"}"""

  /** `GET /admin/ui-builder/designs`: one served design, one quarantined, one in the trash. */
  const val SERVER_ADMIN_DESIGNS: String =
    """{"schema":"compose-preview-serve/admin-ui-builder-designs/v1","designs":[""" +
      """{"designId":"card-light-m1abc2","title":"light","revision":4,"catalogSystemId":"ha-cards",""" +
      """"ownerActorId":"operator","collaborators":1,"createdAtEpochMillis":1750000000000,""" +
      """"updatedAtEpochMillis":1750000100000,"activeSubscribers":0,"unusableReason":null,""" +
      """"degradedReason":null,"documentAvailable":true},""" +
      """{"designId":"broken","title":"broken","revision":0,"catalogSystemId":"","ownerActorId":"",""" +
      """"collaborators":0,"createdAtEpochMillis":0,"updatedAtEpochMillis":0,"activeSubscribers":0,""" +
      """"unusableReason":"the stored document does not decode","degradedReason":null,""" +
      """"documentAvailable":false}],""" +
      """"trash":[{"designId":"old-card","title":"Old card","ownerActorId":"github:octocat",""" +
      """"trashedAtEpochMillis":1750000200000,"trashedByActorId":"github:octocat",""" +
      """"restorableUntilEpochMillis":1752592200000}]}"""

  /**
   * `POST /api/ui-builder/v1/documents/export.rc?artifact=true`: the artifact, base64 bytes and the
   * revision-pinning diagnostic the `X-UI-Builder-Revision` header is read from.
   */
  const val SERVER_RC_ARTIFACT: String =
    """{"format":"rc","mediaType":"application/octet-stream","encoding":"base64","content":"AH+A/w==",""" +
      """"contentDigest":"0d6ba02a2b1ef6c3a1e5e8e4e3f8bd2c5d8fa4c6f6d0c8b2e5a1f3c7d9b0e4a6",""" +
      """"diagnostics":[{"severity":"info","code":"REVISION_PINNED_REMOTE_EXPORT",""" +
      """"message":"Exported design document revision 0 (sha256:abc)."}]}"""
}

class UiBuilderHttpRoutesTest {
  /** `UI_BUILDER_JSON`: every `/api/ui-builder/v1` body the server writes and reads. */
  private val serverJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = false
  }

  /** `ServeHttpServer`'s companion `JSON`: the admin listing. */
  private val adminJson = Json { encodeDefaults = true }

  /** What a client reading with defaults sees. */
  private val defaultJson = Json

  @Test
  fun `route constants are the server's paths`() {
    assertEquals("/api/ui-builder/v1/requests", UiBuilderHttpRoutesV1.REQUESTS_PATH)
    assertEquals("/api/ui-builder/v1/designs/{designId}", UiBuilderHttpRoutesV1.DESIGN_PATH)
    assertEquals(
      "/api/ui-builder/v1/designs/{designId}/export.rc",
      UiBuilderHttpRoutesV1.DESIGN_EXPORT_RC_PATH,
    )
    // The server registers `export.${format.name.lowercase()}`: the path segment is the enum
    // constant's name, which for RC is also its serial name.
    assertTrue(
      UiBuilderHttpRoutesV1.DESIGN_EXPORT_RC_PATH.endsWith(
        "export.${ExportFormatV1.RC.name.lowercase()}",
      ),
    )
    assertEquals(
      "/api/ui-builder/v1/documents/export.rc",
      UiBuilderHttpRoutesV1.DOCUMENT_EXPORT_RC_PATH,
    )
    assertEquals("/api/ui-builder/v1/identity", UiBuilderHttpRoutesV1.IDENTITY_PATH)
    assertEquals("/admin/ui-builder/designs", UiBuilderHttpRoutesV1.ADMIN_DESIGNS_PATH)
    assertEquals("/ui-builder/{designId}", UiBuilderHttpRoutesV1.EDITOR_DESIGN_PATH)
    assertTrue(
      UiBuilderHttpRoutesV1.DESIGN_PATH.contains("{${UiBuilderHttpRoutesV1.DESIGN_ID_PARAMETER}}"),
    )
    assertEquals(8 * 1024 * 1024, UiBuilderHttpRoutesV1.MAX_REQUEST_BYTES)
    assertEquals("ui-builder-editor", UiBuilderHttpRoutesV1.API_NAME)
    assertEquals(2, UiBuilderHttpRoutesV1.API_LEVEL)
  }

  @Test
  fun `the editor link is the shape ha-designs builds`() {
    // `UiBuilderConfig.designUrl`: `<browserUrl>/ui-builder/<encoded id>`.
    val link =
      UiBuilderHttpRoutesV1.EDITOR_DESIGN_PATH.replace(
        "{${UiBuilderHttpRoutesV1.DESIGN_ID_PARAMETER}}",
        "card-light-m1abc2",
      )
    assertEquals("/ui-builder/card-light-m1abc2", link)
  }

  @Test
  fun `credential names are the ones the server reads`() {
    assertEquals("X-Compose-Preview-Token", UiBuilderHttpCredentialsV1.TOKEN_HEADER)
    assertEquals("token", UiBuilderHttpCredentialsV1.TOKEN_QUERY)
    assertEquals("X-Compose-Preview-Admin-Token", UiBuilderHttpCredentialsV1.ADMIN_TOKEN_HEADER)
    assertEquals("operator", UiBuilderHttpCredentialsV1.OPERATOR_ACTOR_ID)
    assertEquals("github:", UiBuilderHttpCredentialsV1.GITHUB_ACTOR_PREFIX)
    assertEquals("anonymous:visitor", UiBuilderHttpCredentialsV1.ANONYMOUS_ACTOR_ID)
    assertEquals("X-UI-Builder-Revision", UiBuilderHttpRoutesV1.REVISION_HEADER)
  }

  @Test
  fun `the document ha-designs puts decodes as the server decodes it`() {
    val document =
      serverJson.decodeFromString(
        DesignDocumentV1.serializer(),
        HttpGoldens.HA_DESIGNS_SEED_DOCUMENT,
      )
    // The `PUT` refuses a body whose id is not the URL's.
    assertEquals("card-light-m1abc2", document.id)
    assertEquals("remote-m3", document.catalogPin.systemId)
    assertEquals(listOf("root"), document.roots)
    assertEquals(9, document.nodes.size)
    assertEquals(
      StatePersistenceV1.DESIGN,
      document.stateVariables.getValue("light0.is_on").persistence,
    )
  }

  @Test
  fun `a document carrying a top-level template marker is refused`() {
    // ha-designs' studio strips `template` before it creates (CardStudio), because the server
    // decodes strictly and a `template` key is a 400, not an ignored field.
    val withTemplate =
      JsonObject(
        serverJson.parseToJsonElement(HttpGoldens.HA_DESIGNS_SEED_DOCUMENT).jsonObject +
          ("template" to JsonPrimitive("entities")),
      )
    assertFailsWith<SerializationException> {
      serverJson.decodeFromJsonElement(DesignDocumentV1.serializer(), withTemplate)
    }
  }

  @Test
  fun `the stored document reads back with every field ha-designs sent`() {
    // `GET /api/ui-builder/v1/designs/{id}` answers the document as the server encodes it,
    // defaults included. ha-designs reads it as a plain JsonObject: everything it put is there,
    // with the same value.
    val sent = serverJson.parseToJsonElement(HttpGoldens.HA_DESIGNS_SEED_DOCUMENT).jsonObject
    val stored =
      serverJson.decodeFromString(
        DesignDocumentV1.serializer(),
        HttpGoldens.HA_DESIGNS_SEED_DOCUMENT,
      )
    val served = serverJson.encodeToJsonElement(DesignDocumentV1.serializer(), stored).jsonObject
    assertSubset(sent, served, path = "$")
    // And the served body is itself a strict DesignDocumentV1.
    assertEquals(stored, serverJson.decodeFromJsonElement(DesignDocumentV1.serializer(), served))
  }

  @Test
  fun `ha-designs' envelope requests decode strictly`() {
    val access =
      serverJson.decodeFromString(
        HttpRequestEnvelopeV1.serializer(),
        HttpGoldens.HA_DESIGNS_GET_DESIGN_ACCESS,
      )
    assertEquals(UiBuilderHttpCredentialsV1.OPERATOR_ACTOR_ID, access.actorId)
    assertEquals(GetDesignAccessRequestV1("card-light-m1abc2"), access.request)
    assertEquals(
      HttpGoldens.HA_DESIGNS_GET_DESIGN_ACCESS,
      serverJson.encodeToString(HttpRequestEnvelopeV1.serializer(), access),
    )

    val catalogs =
      serverJson.decodeFromString(
        HttpRequestEnvelopeV1.serializer(),
        HttpGoldens.HA_DESIGNS_LIST_CATALOGS,
      )
    assertEquals(ListCatalogsRequestV1, catalogs.request)
    assertEquals(
      HttpGoldens.HA_DESIGNS_LIST_CATALOGS,
      serverJson.encodeToString(HttpRequestEnvelopeV1.serializer(), catalogs),
    )
  }

  @Test
  fun `server response envelopes round trip byte for byte`() {
    listOf(
        HttpGoldens.SERVER_DESIGN_ACCESS_RESPONSE,
        HttpGoldens.SERVER_INVALID_ENVELOPE,
        HttpGoldens.SERVER_UNAUTHORIZED_ENVELOPE,
        HttpGoldens.SERVER_ACTOR_MISMATCH_ENVELOPE,
      )
      .forEach { golden ->
        val decoded = serverJson.decodeFromString(HttpResponseEnvelopeV1.serializer(), golden)
        assertEquals(
          golden,
          serverJson.encodeToString(HttpResponseEnvelopeV1.serializer(), decoded),
        )
      }
  }

  @Test
  fun `ha-designs reads the access response where the server puts it`() {
    // `editors()`: `response.type == designAccess`, then the owner and every editor grant.
    val payload =
      defaultJson
        .parseToJsonElement(HttpGoldens.SERVER_DESIGN_ACCESS_RESPONSE)
        .jsonObject
        .getValue("response")
        .jsonObject
    assertEquals(JsonPrimitive("designAccess"), payload["type"])
    val access = payload.getValue("access").jsonObject
    assertEquals("operator", access.getValue("ownerActorId").jsonPrimitive.content)
    val grant = access.getValue("actorGrants").jsonArray.single().jsonObject
    assertEquals("editor", grant.getValue("role").jsonPrimitive.content)
    assertEquals("github:octocat", grant.getValue("actorId").jsonPrimitive.content)
  }

  @Test
  fun `ha-designs reads the catalog pin where the server puts it`() {
    // `servedPin()`:
    // `response.catalogs[].benchmark.{catalogSystemId,catalogRevision,nativeRuntimeId}`.
    val catalog =
      serverJson.decodeFromString(
        CatalogCapabilityV1.serializer(),
        File(fixturesDir(), "catalog.json").readText(),
      )
    val served =
      serverJson.encodeToString(
        HttpResponseEnvelopeV1.serializer(),
        HttpResponseEnvelopeV1(
          requestId = "q8Zp1Xy3",
          response = CatalogsResponseV1(listOf(catalog)),
        ),
      )
    val payload = defaultJson.parseToJsonElement(served).jsonObject.getValue("response").jsonObject
    assertEquals(JsonPrimitive("catalogs"), payload["type"])
    val benchmark =
      payload.getValue("catalogs").jsonArray.single().jsonObject.getValue("benchmark").jsonObject
    assertEquals(
      catalog.benchmark.catalogSystemId,
      benchmark.getValue("catalogSystemId").jsonPrimitive.content,
    )
    assertEquals(
      catalog.benchmark.catalogRevision,
      benchmark.getValue("catalogRevision").jsonPrimitive.content,
    )
    assertEquals(
      catalog.benchmark.nativeRuntimeId,
      benchmark.getValue("nativeRuntimeId").jsonPrimitive.content,
    )
  }

  @Test
  fun `identity bodies round trip byte for byte under any encoder`() {
    listOf(
        HttpGoldens.SERVER_IDENTITY_OPERATOR,
        HttpGoldens.SERVER_IDENTITY_OPERATOR_WITH_SIGN_IN,
        HttpGoldens.SERVER_IDENTITY_READER,
        HttpGoldens.SERVER_IDENTITY_ANONYMOUS,
      )
      .forEach { golden ->
        val decoded = serverJson.decodeFromString(UiBuilderIdentityV1.serializer(), golden)
        assertEquals(golden, serverJson.encodeToString(UiBuilderIdentityV1.serializer(), decoded))
        // `@EncodeDefault` on both ends: the same bytes whether or not the encoder writes defaults.
        assertEquals(golden, defaultJson.encodeToString(UiBuilderIdentityV1.serializer(), decoded))
      }
    listOf(HttpGoldens.SERVER_IDENTITY_REFUSAL, HttpGoldens.SERVER_IDENTITY_REFUSAL_NO_SIGN_IN)
      .forEach { golden ->
        val decoded = serverJson.decodeFromString(UiBuilderIdentityRefusalV1.serializer(), golden)
        assertEquals(
          golden,
          serverJson.encodeToString(UiBuilderIdentityRefusalV1.serializer(), decoded),
        )
        assertEquals(
          golden,
          defaultJson.encodeToString(UiBuilderIdentityRefusalV1.serializer(), decoded),
        )
      }
  }

  @Test
  fun `the identity builder writes the server's bytes`() {
    val operator =
      UiBuilderIdentityV1.Builder(UiBuilderHttpCredentialsV1.OPERATOR_ACTOR_ID)
        .apply {
          signedIn = true
          canWrite = true
        }
        .build()
    assertEquals(
      HttpGoldens.SERVER_IDENTITY_OPERATOR,
      serverJson.encodeToString(UiBuilderIdentityV1.serializer(), operator),
    )
    val withSignIn =
      operator.newBuilder().apply { signInUrl = "/auth/github/start?return=%2Fui-builder" }.build()
    assertEquals(
      HttpGoldens.SERVER_IDENTITY_OPERATOR_WITH_SIGN_IN,
      serverJson.encodeToString(UiBuilderIdentityV1.serializer(), withSignIn),
    )
    // An older host's reply: actor only. Everything optional reads as absent.
    val older =
      serverJson.decodeFromString(
        UiBuilderIdentityV1.serializer(),
        """{"schemaVersion":1,"actorId":"operator"}""",
      )
    assertEquals(null, older.signedIn)
    assertEquals(null, older.canWrite)
    assertEquals(true, older.designVisibilitySupported)
  }

  @Test
  fun `ha-designs reads the identity the way the server writes it`() {
    // `dashboardPreviewAccess()`: authorized when 200 and `signedIn == true`; `signInUrl` offered.
    val signedIn = defaultJson.parseToJsonElement(HttpGoldens.SERVER_IDENTITY_READER).jsonObject
    assertEquals(JsonPrimitive(true), signedIn["signedIn"])
    val refused = defaultJson.parseToJsonElement(HttpGoldens.SERVER_IDENTITY_REFUSAL).jsonObject
    assertEquals(
      "/auth/github/start?return=%2Fui-builder",
      refused.getValue("signInUrl").jsonPrimitive.content,
    )
  }

  @Test
  fun `the admin listing round trips byte for byte`() {
    val decoded =
      adminJson.decodeFromString(
        UiBuilderAdminDesignsV1.serializer(),
        HttpGoldens.SERVER_ADMIN_DESIGNS,
      )
    assertEquals(
      HttpGoldens.SERVER_ADMIN_DESIGNS,
      adminJson.encodeToString(UiBuilderAdminDesignsV1.serializer(), decoded),
    )
    // Strict too: nothing in the server's listing is outside the type.
    assertEquals(
      decoded,
      serverJson.decodeFromString(
        UiBuilderAdminDesignsV1.serializer(),
        HttpGoldens.SERVER_ADMIN_DESIGNS,
      ),
    )
    val quarantined = decoded.designs[1]
    assertEquals(false, quarantined.documentAvailable)
    assertEquals(quarantined.designId, quarantined.title)
  }

  @Test
  fun `the admin builder writes the server's bytes`() {
    val listing =
      UiBuilderAdminDesignsV1.Builder()
        .apply {
          designs =
            listOf(
              UiBuilderAdminDesignV1.Builder("card-light-m1abc2", "light")
                .apply {
                  revision = 4
                  catalogSystemId = "ha-cards"
                  ownerActorId = "operator"
                  collaborators = 1
                  createdAtEpochMillis = 1750000000000
                  updatedAtEpochMillis = 1750000100000
                }
                .build(),
              UiBuilderAdminDesignV1.Builder("broken", "broken")
                .apply {
                  unusableReason = "the stored document does not decode"
                  documentAvailable = false
                }
                .build(),
            )
          trash =
            listOf(
              UiBuilderTrashedDesignV1.Builder("old-card", "Old card", "github:octocat")
                .apply {
                  trashedAtEpochMillis = 1750000200000
                  trashedByActorId = "github:octocat"
                  restorableUntilEpochMillis = 1752592200000
                }
                .build(),
            )
        }
        .build()
    assertEquals(
      HttpGoldens.SERVER_ADMIN_DESIGNS,
      adminJson.encodeToString(UiBuilderAdminDesignsV1.serializer(), listing),
    )
    assertEquals(listing, listing.newBuilder().build())
  }

  @Test
  fun `ha-designs filters the admin listing on fields the server writes`() {
    // `designsFor()`: catalogSystemId, a JsonNull unusableReason, ownerActorId, collaborators,
    // designId and createdAtEpochMillis.
    val rows =
      defaultJson
        .parseToJsonElement(HttpGoldens.SERVER_ADMIN_DESIGNS)
        .jsonObject
        .getValue("designs")
        .jsonArray
        .map { it.jsonObject }
    val usable = rows.filter {
      it["unusableReason"].let { reason -> reason == null || reason is JsonNull }
    }
    assertEquals(
      listOf("card-light-m1abc2"),
      usable.map { it.getValue("designId").jsonPrimitive.content },
    )
    val row = usable.single()
    assertEquals("ha-cards", row.getValue("catalogSystemId").jsonPrimitive.content)
    assertEquals("operator", row.getValue("ownerActorId").jsonPrimitive.content)
    assertEquals(1, row.getValue("collaborators").jsonPrimitive.int)
    assertEquals(1750000000000, row.getValue("createdAtEpochMillis").jsonPrimitive.long)
  }

  @Test
  fun `the rc artifact round trips and names its revision`() {
    val artifact =
      serverJson.decodeFromString(ExportArtifactV1.serializer(), HttpGoldens.SERVER_RC_ARTIFACT)
    assertEquals(ExportFormatV1.RC, artifact.format)
    assertEquals(ExportEncodingV1.BASE64, artifact.encoding)
    assertEquals(
      HttpGoldens.SERVER_RC_ARTIFACT,
      serverJson.encodeToString(ExportArtifactV1.serializer(), artifact),
    )
  }

  private fun assertSubset(expected: JsonElement, actual: JsonElement?, path: String) {
    if (expected is JsonObject) {
      val actualObject = actual as? JsonObject ?: error("$path: expected an object, got $actual")
      expected.forEach { (key, value) -> assertSubset(value, actualObject[key], "$path.$key") }
    } else if (expected is JsonArray) {
      val actualArray = actual as? JsonArray ?: error("$path: expected an array, got $actual")
      assertEquals("$path size", expected.size, actualArray.size)
      expected.forEachIndexed { i, value -> assertSubset(value, actualArray[i], "$path[$i]") }
    } else if (
      expected is JsonPrimitive &&
        actual is JsonPrimitive &&
        expected.doubleOrNull != null &&
        !expected.isString
    ) {
      // `1` and `1.0` are the same number: a Double field re-encodes an integer literal as `1.0`.
      assertEquals(path, expected.content.toDouble(), actual.content.toDouble(), 0.0)
    } else {
      assertEquals(path, expected, actual)
    }
  }

  private fun fixturesDir(): File {
    var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (directory != null) {
      val candidate = File(directory, "docs/ui-builder/protocol-fixtures/v1")
      if (candidate.isDirectory) return candidate
      directory = directory.parentFile
    }
    error("could not locate UI builder v1 fixtures from ${System.getProperty("user.dir")}")
  }
}
