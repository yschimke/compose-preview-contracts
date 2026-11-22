package ee.schimke.composeai.deployment.protocol

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The published JSON Schemas under `src/main/resources/schemas/` are the serializers, written down
 * by [DeploymentJsonSchema]. Run with `UPDATE_SCHEMAS=1` to rewrite them deliberately after a
 * change. The goldens in [Goldens] are then validated against them, so a schema that is right about
 * the types but would reject what servers send today fails too.
 */
class DeploymentJsonSchemaTest {
  private val schemas: Map<String, Pair<String, KSerializer<*>>> =
    mapOf(
      "deployable-version-v1.schema.json" to
        ("Deployable version (v1)" to DeployableVersionV1.serializer()),
      "release-manifest-v1.schema.json" to
        ("Release manifest (v1)" to ReleaseManifestV1.serializer()),
      "session-whoami-v1.schema.json" to ("Session whoami (v1)" to SessionWhoamiV1.serializer()),
    )

  @Test
  fun `committed schemas match the serializers`() {
    val update = System.getenv("UPDATE_SCHEMAS") == "1"
    schemas.forEach { (file, spec) ->
      val generated =
        schemaJson.encodeToString(
          JsonObject.serializer(),
          DeploymentJsonSchema.generate(spec.first, spec.second),
        ) + "\n"
      val target = File(schemaDir(), file)
      if (update) target.writeText(generated)
      assertTrue(target.isFile, "missing $file - run with UPDATE_SCHEMAS=1")
      assertEquals(
        generated,
        target.readText(),
        "$file is stale; regenerate with UPDATE_SCHEMAS=1 ./gradlew :deployment-protocol:test",
      )
    }
  }

  @Test
  fun `today's server bodies validate against the schemas`() {
    listOf(
        "deployable-version-v1.schema.json" to Goldens.SERVER_VERSION,
        "deployable-version-v1.schema.json" to Goldens.COMPONENT_VERSION,
        "release-manifest-v1.schema.json" to Goldens.RELEASE_MANIFEST,
        "session-whoami-v1.schema.json" to Goldens.SERVER_WHOAMI,
        "session-whoami-v1.schema.json" to Goldens.SERVER_GUEST_WHOAMI,
      )
      .forEach { (file, body) ->
        val violations = validator(file).validate(Json.parseToJsonElement(body))
        assertTrue(violations.isEmpty(), "$file rejects $body:\n${violations.joinToString("\n")}")
      }
  }

  @Test
  fun `a body missing a required field is rejected`() {
    val version = Json.parseToJsonElement(Goldens.SERVER_VERSION).jsonObject
    assertTrue(
      validator("deployable-version-v1.schema.json").validate(JsonObject(version - "version")).any {
        "missing version" in it
      },
    )
    val whoami = Json.parseToJsonElement(Goldens.SERVER_WHOAMI).jsonObject
    assertTrue(
      validator("session-whoami-v1.schema.json").validate(JsonObject(whoami - "login")).any {
        "missing login" in it
      },
    )
  }

  private fun validator(file: String) =
    SubsetSchemaValidator(Json.parseToJsonElement(File(schemaDir(), file).readText()).jsonObject)

  private fun schemaDir(): File {
    var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (directory != null) {
      listOf("src/main/resources/schemas", "api/deployment-protocol/src/main/resources/schemas")
        .map { File(directory, it) }
        .firstOrNull {
          it.isDirectory && File(it, "../../kotlin/ee/schimke/composeai/deployment").exists()
        }
        ?.let {
          return it
        }
      directory = directory.parentFile
    }
    error("cannot locate deployment-protocol's schema directory")
  }
}
