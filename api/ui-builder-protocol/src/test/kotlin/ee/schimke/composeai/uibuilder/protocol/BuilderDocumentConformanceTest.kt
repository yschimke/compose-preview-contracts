package ee.schimke.composeai.uibuilder.protocol

import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real design documents, written by the builder's own serializer, read as the typed protocol.
 *
 * compose-ui-builder keeps a node's `properties`, `modifiers` and `eventBindings` as raw JSON and
 * parses the `{"type": …}` encoding again in every reader (#107). The typed forms that would
 * replace that — [UiValueV1], [DesignModifierV1], [DesignActionV1] — already live here, and the
 * migration is only safe if they read and write those values exactly as the builder does: a
 * document's revision hash is computed over them, so a value that re-encoded as a different
 * spelling would be a different design.
 *
 * `docs/ui-builder/builder-documents/` is that evidence: the five Google-app sample designs, which
 * between them carry most of the wrapper vocabulary (typed scalars, colour and colour tokens, shape
 * tokens, padding, adaptive grids, enums and fourteen modifiers), encoded by `UiBuilderDocument`'s
 * serializer with `encodeDefaults = true` as the builder writes them. Each is decoded strictly with
 * the configuration hosts use, and every node's values must come back byte-for-byte. None of them
 * binds an event, so actions are pinned by the lossless protocol fixture instead.
 *
 * What is **not** asserted is document-level identity, and the difference is deliberate: the
 * protocol's node carries `assetBindings` and `tokenBindings`, and its document `tokenBindings` and
 * `components`, which the builder's document does not have yet, and `environment` holds `density`
 * and `fontScale` as `Double` where the builder keeps the authored JSON number. Those are the
 * container fields the migration adopts rather than preserves — none of them is a property value.
 */
class BuilderDocumentConformanceTest {
  /** What `compose-preview-server` decodes design documents with. */
  private val hostJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = false
  }

  private val properties = MapSerializer(String.serializer(), UiValueV1.serializer())
  private val modifiers = ListSerializer(DesignModifierV1.serializer())
  private val eventBindings =
    MapSerializer(String.serializer(), ListSerializer(DesignActionV1.serializer()))
  private val slots = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

  @Test
  fun everyNodeValueRoundTripsExactly() {
    val documents = documentsDir().listFiles().orEmpty().filter { it.extension == "json" }
    assertTrue("no builder documents under ${documentsDir()}", documents.size >= 5)

    val mismatches =
      documents
        .sortedBy { it.name }
        .flatMap { file ->
          val original = hostJson.parseToJsonElement(file.readText()).jsonObject
          // Strict: an unknown field anywhere in a builder document fails here, by name.
          val decoded = hostJson.decodeFromJsonElement(DesignDocumentV1.serializer(), original)
          val authored = original.getValue("nodes").jsonObject
          assertEquals("${file.name}: node ids", authored.keys, decoded.nodes.keys)
          decoded.nodes.flatMap { (id, node) ->
            val written = authored.getValue(id).jsonObject
            listOfNotNull(
              mismatch(file, id, "properties", written, encode(properties, node.properties)),
              mismatch(file, id, "modifiers", written, encode(modifiers, node.modifiers)),
              mismatch(
                file,
                id,
                "eventBindings",
                written,
                encode(eventBindings, node.eventBindings),
              ),
              mismatch(file, id, "slots", written, encode(slots, node.slots)),
            )
          }
        }

    assertEquals(mismatches.joinToString("\n"), emptyList<String>(), mismatches)
  }

  /**
   * Every wrapper type the corpus carries, so a document that stopped exercising one is noticed.
   */
  @Test
  fun theCorpusCoversTheBuildersWrapperVocabulary() {
    val seen = mutableSetOf<String>()
    fun walk(element: JsonElement) {
      when (element) {
        is JsonObject -> {
          (element["type"] as? kotlinx.serialization.json.JsonPrimitive)?.let { seen += it.content }
          element.values.forEach(::walk)
        }
        is kotlinx.serialization.json.JsonArray -> element.forEach(::walk)
        else -> Unit
      }
    }
    documentsDir()
      .listFiles()
      .orEmpty()
      .filter { it.extension == "json" }
      .forEach { file ->
        hostJson
          .parseToJsonElement(file.readText())
          .jsonObject
          .getValue("nodes")
          .jsonObject
          .values
          .forEach { node ->
            listOf("properties", "modifiers").forEach { walk(node.jsonObject[it]!!) }
          }
      }
    val expected =
      setOf(
        "string",
        "bool",
        "int",
        "float",
        "color",
        "colorToken",
        "enum",
        "shapeToken",
        "padding",
        "adaptiveGrid",
      )
    assertTrue("corpus no longer covers ${expected - seen}", seen.containsAll(expected))
  }

  private fun <T> encode(serializer: kotlinx.serialization.KSerializer<T>, value: T): JsonElement =
    hostJson.encodeToJsonElement(serializer, value)

  private fun mismatch(
    file: File,
    nodeId: String,
    field: String,
    written: JsonObject,
    reencoded: JsonElement,
  ): String? {
    val original = written[field] ?: return null
    return if (original == reencoded) null
    else "${file.name} node `$nodeId`.$field:\n  builder:  $original\n  protocol: $reencoded"
  }

  private fun documentsDir(): File {
    var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (directory != null) {
      val candidate = File(directory, "docs/ui-builder/builder-documents")
      if (candidate.isDirectory) return candidate
      directory = directory.parentFile
    }
    error("docs/ui-builder/builder-documents not found above ${System.getProperty("user.dir")}")
  }
}
