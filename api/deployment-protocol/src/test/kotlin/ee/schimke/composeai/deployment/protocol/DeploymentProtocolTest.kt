package ee.schimke.composeai.deployment.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Bodies the preview server sends today, byte for byte as its encoder writes them. Each was derived
 * from compose-preview-server 3.192.0: `VersionResponse` in `ServeHttpServer.kt` (encoded with
 * `encodeDefaults = true`), `ServeGithubAuth.Whoami`, and `ServeUiBuilderEditor.EditorManifest`.
 */
internal object Goldens {
  const val SERVER_VERSION: String =
    """{"schema":"compose-preview-serve/version/v1","version":"3.192.0",""" +
      """"serveSchema":"compose-preview-serve/v3","public":true}"""

  const val COMPONENT_VERSION: String =
    """{"schema":"compose-preview/deployable-version/v1","version":"3.48.0",""" +
      """"component":"ui-builder","apis":{"ui-builder-editor":2,"ui-builder-root-offline":1}}"""

  const val SERVER_WHOAMI: String = """{"login":"octocat","guest":false}"""

  const val SERVER_GUEST_WHOAMI: String = """{"login":"visitor","guest":true}"""

  /** `ui-builder-web.json` as compose-ui-builder writes it today. */
  const val EDITOR_MANIFEST: String =
    """{"schema":"compose-ui-builder-web/v1","version":"3.48.0","serverApi":2,"rootOffline":1}"""

  const val RELEASE_MANIFEST: String =
    """
    {
      "schema": "compose-preview/release-manifest/v1",
      "component": "ui-builder",
      "version": "3.48.0",
      "provides": { "ui-builder-editor": 2, "ui-builder-root-offline": 1 },
      "requires": { "session-whoami": { "min": 1, "max": 1 } },
      "assets": [
        {
          "name": "compose-preview-ui-builder-web-3.48.0.zip",
          "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
          "mediaType": "application/zip"
        }
      ],
      "image": {
        "repository": "ghcr.io/yschimke/compose-ui-builder",
        "tag": "3.48.0",
        "digest": "sha256:1111111111111111111111111111111111111111111111111111111111111111"
      },
      "futureField": true
    }
    """
}

class DeploymentProtocolTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `the preview server's current version body decodes`() {
    val version = json.decodeFromString(DeployableVersionV1.serializer(), Goldens.SERVER_VERSION)
    assertEquals(DeployableVersionV1.PREVIEW_SERVER_SCHEMA, version.schema)
    assertTrue(version.schema in DeployableVersionV1.SCHEMAS)
    assertEquals("3.192.0", version.version)
    assertNull(version.component)
    assertEquals(emptyMap(), version.apis)
  }

  @Test
  fun `a component version body round trips`() {
    val version =
      DeployableVersionV1.Builder("3.48.0")
        .apply {
          component = DeploymentComponentsV1.UI_BUILDER
          apis =
            mapOf(
              DeploymentApisV1.UI_BUILDER_EDITOR to 2,
              DeploymentApisV1.UI_BUILDER_ROOT_OFFLINE to 1,
            )
        }
        .build()
    assertEquals(DeployableVersionV1.SCHEMA, version.schema)
    assertEquals(
      Json.parseToJsonElement(Goldens.COMPONENT_VERSION),
      Json.parseToJsonElement(Json.encodeToString(DeployableVersionV1.serializer(), version)),
    )
    assertEquals(
      version,
      json.decodeFromString(DeployableVersionV1.serializer(), Goldens.COMPONENT_VERSION),
    )
    assertEquals(version, version.newBuilder().build())
  }

  @Test
  fun `an encoder with encodeDefaults keeps the schema and omits the unset optional fields`() {
    val encoded = Json {
      encodeDefaults = true
    }
      .encodeToString(
        DeployableVersionV1.serializer(),
        DeployableVersionV1.Builder("1.0.0").build(),
      )
    val keys = Json.parseToJsonElement(encoded).jsonObject.keys
    assertEquals(setOf("schema", "version"), keys)
  }

  @Test
  fun `the preview server's current whoami replies decode`() {
    val member = json.decodeFromString(SessionWhoamiV1.serializer(), Goldens.SERVER_WHOAMI)
    assertEquals("octocat", member.login)
    assertFalse(member.guest)
    assertEquals(emptyList(), member.scopes)
    assertNull(member.expiresAtEpochSeconds)
    assertTrue(
      json.decodeFromString(SessionWhoamiV1.serializer(), Goldens.SERVER_GUEST_WHOAMI).guest,
    )
  }

  @Test
  fun `a whoami without the future fields encodes to today's bytes`() {
    val reply = SessionWhoamiV1.Builder("octocat").build()
    assertEquals(
      Goldens.SERVER_WHOAMI,
      Json { encodeDefaults = true }.encodeToString(SessionWhoamiV1.serializer(), reply),
    )
  }

  @Test
  fun `whoami future fields round trip through the builder`() {
    val reply =
      SessionWhoamiV1.Builder("octocat")
        .apply {
          scopes = listOf("ui-builder:write")
          expiresAtEpochSeconds = 1_800_000_000L
        }
        .build()
    val decoded =
      json.decodeFromString(
        SessionWhoamiV1.serializer(),
        Json.encodeToString(SessionWhoamiV1.serializer(), reply),
      )
    assertEquals(reply, decoded)
    assertEquals(reply, reply.newBuilder().build())
  }

  @Test
  fun `a release manifest decodes, ignoring unknown fields, and round trips`() {
    val manifest = json.decodeFromString(ReleaseManifestV1.serializer(), Goldens.RELEASE_MANIFEST)
    assertEquals(ReleaseManifestV1.SCHEMA, manifest.schema)
    assertEquals(DeploymentComponentsV1.UI_BUILDER, manifest.component)
    assertEquals(2, manifest.provides[DeploymentApisV1.UI_BUILDER_EDITOR])
    assertEquals(
      ApiRangeV1.Builder(1, 1).build(),
      manifest.requires[DeploymentApisV1.SESSION_WHOAMI],
    )
    assertEquals("application/zip", manifest.assets.single().mediaType)
    assertEquals("ghcr.io/yschimke/compose-ui-builder", manifest.image?.repository)
    val rebuilt =
      ReleaseManifestV1.Builder(DeploymentComponentsV1.UI_BUILDER, "3.48.0")
        .apply {
          provides = manifest.provides
          requires = mapOf(DeploymentApisV1.SESSION_WHOAMI to ApiRangeV1.Builder(1, 1).build())
          assets =
            listOf(
              ReleaseAssetV1.Builder(
                  "compose-preview-ui-builder-web-3.48.0.zip",
                  "0".repeat(64),
                )
                .apply { mediaType = "application/zip" }
                .build(),
            )
          image =
            ReleaseImageV1.Builder("ghcr.io/yschimke/compose-ui-builder", "3.48.0")
              .apply { digest = "sha256:" + "1".repeat(64) }
              .build()
        }
        .build()
    assertEquals(manifest, rebuilt)
    assertEquals(manifest, manifest.newBuilder().build())
    assertEquals(
      manifest,
      json.decodeFromString(
        ReleaseManifestV1.serializer(),
        Json.encodeToString(ReleaseManifestV1.serializer(), manifest),
      ),
    )
  }

  @Test
  fun `a minimal release manifest carries only schema, component and version`() {
    val encoded = Json {
      encodeDefaults = true
    }
      .encodeToString(
        ReleaseManifestV1.serializer(),
        ReleaseManifestV1.Builder(DeploymentComponentsV1.CATALOG_MCP, "4.2.0").build(),
      )
    assertEquals(
      setOf("schema", "component", "version"),
      Json.parseToJsonElement(encoded).jsonObject.keys,
    )
  }

  @Test
  fun `the editor manifest's fields map onto provides`() {
    // The documented mapping, pinned: `serverApi` is provides["ui-builder-editor"], `rootOffline`
    // is provides["ui-builder-root-offline"]. The editor manifest itself is untouched.
    val editor = Json.parseToJsonElement(Goldens.EDITOR_MANIFEST).jsonObject
    val release = json.decodeFromString(ReleaseManifestV1.serializer(), Goldens.RELEASE_MANIFEST)
    assertEquals(
      editor.getValue("serverApi").toString().toInt(),
      release.provides[DeploymentApisV1.UI_BUILDER_EDITOR],
    )
    assertEquals(
      editor.getValue("rootOffline").toString().toInt(),
      release.provides[DeploymentApisV1.UI_BUILDER_ROOT_OFFLINE],
    )
    assertEquals(editor.getValue("version").toString().trim('"'), release.version)
  }

  @Test
  fun `endpoint paths match the preview server's routes`() {
    assertEquals("/healthz", DeployableEndpointsV1.HEALTHZ_PATH)
    assertEquals("/readyz", DeployableEndpointsV1.READYZ_PATH)
    assertEquals("/version", DeployableEndpointsV1.VERSION_PATH)
    assertEquals("/auth/github/whoami", SessionWhoamiV1.PATH)
  }

  @Test
  fun `api names match the modules that type them`() {
    // `component-catalog-protocol`'s `CatalogReadRoutesV1.API_NAME` and `ui-builder-protocol`'s
    // `UiBuilderHttpRoutesV1.API_NAME` spell the same strings; neither module depends on this one.
    assertEquals("catalog-read", DeploymentApisV1.CATALOG_READ)
    assertEquals("ui-builder-editor", DeploymentApisV1.UI_BUILDER_EDITOR)
  }
}
