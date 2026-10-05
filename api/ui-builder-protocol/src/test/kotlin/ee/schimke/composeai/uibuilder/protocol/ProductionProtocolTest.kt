package ee.schimke.composeai.uibuilder.protocol

import ee.schimke.composeai.uibuilder.protocol.production.*
import java.io.File
import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.Test

class ProductionProtocolTest {
  private val json = Json {
    classDiscriminator = "type"
    encodeDefaults = true
  }

  private fun fixtures(): File {
    var dir: File? = File(System.getProperty("user.dir"))
    while (dir != null) {
      val candidate = File(dir, "docs/ui-builder/protocol-fixtures/production-v1")
      if (candidate.isDirectory) return candidate
      dir = dir.parentFile
    }
    error("production fixtures missing")
  }

  @Test
  fun productionFilesRetainTheirDeclaredApi() {
    for (name in listOf("Library.uid", "Queue.uid", "EpisodeCard.uid", "LibraryModels.uid")) {
      val file =
        json.decodeFromString(ProductionUidFileV1.serializer(), File(fixtures(), name).readText())
      assertEquals(PRODUCTION_UID_SCHEMA_V1, file.schema)
      assertEquals(
        file,
        json.decodeFromString(
          ProductionUidFileV1.serializer(),
          json.encodeToString(ProductionUidFileV1.serializer(), file),
        ),
      )
      assertEquals(file, file.newBuilder().build())
      file.models.forEach { model ->
        assertEquals(model, model.newBuilder().build())
        model.fields.forEach { assertEquals(it, it.newBuilder().build()) }
      }
      file.entryPoint?.let { entry ->
        assertEquals(entry, entry.newBuilder().build())
        entry.events.forEach { assertEquals(it, it.newBuilder().build()) }
        entry.eventBindings.forEach { assertEquals(it, it.newBuilder().build()) }
        entry.bindings.forEach { assertEquals(it, it.newBuilder().build()) }
        entry.components.forEach { assertEquals(it, it.newBuilder().build()) }
      }
      assertFailsWith<SerializationException> {
        json.decodeFromString(DesignDocumentV1.serializer(), File(fixtures(), name).readText())
      }
      assertFailsWith<SerializationException> {
        Json { ignoreUnknownKeys = true }
          .decodeFromString(DesignDocumentV1.serializer(), File(fixtures(), name).readText())
      }
    }
  }

  @Test
  fun futureContractFieldsAndTypesAreNotIgnored() {
    val source = File(fixtures(), "EpisodeCard.uid").readText()
    val tree = json.parseToJsonElement(source).jsonObject
    assertFailsWith<SerializationException> {
      json.decodeFromJsonElement(
        ProductionUidFileV1.serializer(),
        JsonObject(tree + ("futureApi" to JsonPrimitive(true))),
      )
    }
    assertFailsWith<SerializationException> {
      json.decodeFromString(
        ProductionUidFileV1.serializer(),
        source.replace("\"kind\": \"scalar\"", "\"kind\": \"futureType\""),
      )
    }
  }

  @Test
  fun productionFixturesMatchThePublishedSchema() {
    val schema =
      File(
        fixtures().parentFile.parentFile.parentFile.parentFile,
        "api/ui-builder-protocol/src/jvmMain/resources/schemas/production-uid-v1.schema.json",
      )
    val validator = SubsetSchemaValidator(json.parseToJsonElement(schema.readText()).jsonObject)
    fixtures().listFiles()!!.forEach {
      assertEquals(emptyList(), validator.validate(json.parseToJsonElement(it.readText())), it.name)
    }
  }
}
