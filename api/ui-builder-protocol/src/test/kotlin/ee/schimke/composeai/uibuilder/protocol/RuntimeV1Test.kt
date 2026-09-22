package ee.schimke.composeai.uibuilder.protocol

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class RuntimeV1Test {
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
    explicitNulls = false
  }

  @Test
  fun `manifest artifact descriptor and renderer messages retain their wire fields`() {
    val digest = "a".repeat(64)
    val manifest =
      UiBuilderRuntimeManifestV1(
        runtimeId = "wear-m3-p1-abcd",
        protocolVersion = 1,
        entrypoint = "index.html",
        integritySha256 = digest,
        remoteComposeWriter = "4307936-ps17-cmp01",
        rcPlayer = "1.69.0",
      )
    val artifact =
      UiBuilderRuntimeArtifactV1(
        runtimeId = manifest.runtimeId,
        protocolVersion = manifest.protocolVersion,
        integritySha256 = digest,
      )
    val descriptor =
      UiBuilderRuntimeDescriptorV1(
        catalogSystemId = "wear-m3",
        catalogRevision = "revision",
        capabilityDigest = "capability",
        runtimeId = manifest.runtimeId,
        protocolVersion = manifest.protocolVersion,
        assetRoot = "/ui-builder/runtime/${manifest.runtimeId}/",
        integritySha256 = digest,
      )
    val action = UiBuilderRendererActionV1("design", 7, "list", "scrollBy", 0.0, 24.0)
    val message =
      UiBuilderRendererMessageV1(
        runtimeId = manifest.runtimeId,
        requestId = "request-1",
        type = "dispatchAction",
        payload =
          json.encodeToJsonElement(UiBuilderRendererActionV1.serializer(), action).jsonObject,
      )

    assertEquals(manifest, json.decodeFromString(json.encodeToString(manifest)))
    assertEquals(artifact, json.decodeFromString(json.encodeToString(artifact)))
    assertEquals(descriptor, json.decodeFromString(json.encodeToString(descriptor)))
    assertEquals(message, json.decodeFromString(json.encodeToString(message)))
    assertEquals(UI_BUILDER_RUNTIME_ARTIFACT_PATH_V1, artifact.path)
    assertEquals(UI_BUILDER_RENDERER_PROTOCOL_SCHEMA_V1, message.schema)
  }

  @Test
  fun `shared validation rejects unsafe identity paths and digest mismatches`() {
    val manifest =
      UiBuilderRuntimeManifestV1(
        runtimeId = "latest",
        protocolVersion = 0,
        entrypoint = "../index.html",
        integritySha256 = "A".repeat(64),
        remoteComposeWriter = "",
        rcPlayer = "",
      )

    assertEquals(
      listOf(
        UiBuilderRuntimeValidationIssueV1("runtimeId", "unsafeOrReserved"),
        UiBuilderRuntimeValidationIssueV1("protocolVersion", "notPositive"),
        UiBuilderRuntimeValidationIssueV1("entrypoint", "unsafe"),
        UiBuilderRuntimeValidationIssueV1("integritySha256", "invalid"),
        UiBuilderRuntimeValidationIssueV1("remoteComposeWriter", "blank"),
        UiBuilderRuntimeValidationIssueV1("rcPlayer", "blank"),
      ),
      manifest.validateContract(emptySet(), actualIntegritySha256 = "b".repeat(64)),
    )
    assertTrue(
      UiBuilderRuntimeArtifactV1("other.zip", "valid", 1, "b".repeat(64))
        .validateContract()
        .contains(UiBuilderRuntimeValidationIssueV1("path", "unsupported"))
    )
    assertEquals(null, normalizeUiBuilderRuntimeAssetPathV1("a/../b"))
  }

  @Test
  fun `tree integrity framing matches the cross-language vector`() {
    val fixture = json.parseToJsonElement(fixtureFile().readText()).jsonObject
    assertEquals(
      UI_BUILDER_RUNTIME_INTEGRITY_ALGORITHM_V1,
      fixture.getValue("algorithm").jsonPrimitive.content,
    )
    val assets =
      fixture.getValue("assets").jsonArray.associate { assetElement ->
        val asset = assetElement.jsonObject
        asset.getValue("path").jsonPrimitive.content to
          Base64.getDecoder().decode(asset.getValue("base64").jsonPrimitive.content)
      }
    val digest = MessageDigest.getInstance(UI_BUILDER_RUNTIME_INTEGRITY_ALGORITHM_V1)

    frameUiBuilderRuntimeTreeIntegrityV1(assets, digest::update)

    assertEquals(fixture.getValue("integritySha256").jsonPrimitive.content, digest.digest().toHex())
    assertFailsWith<IllegalArgumentException> {
      frameUiBuilderRuntimeTreeIntegrityV1(
        mapOf(UI_BUILDER_RUNTIME_MANIFEST_NAME_V1 to byteArrayOf()),
        digest::update,
      )
    }
  }

  private fun fixtureFile(): File {
    var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (directory != null) {
      val candidate = File(directory, "docs/ui-builder/runtime-fixtures/v1/tree-integrity.json")
      if (candidate.isFile) return candidate
      directory = directory.parentFile
    }
    error("could not locate UI-builder runtime fixtures from ${System.getProperty("user.dir")}")
  }

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
