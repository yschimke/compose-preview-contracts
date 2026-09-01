package ee.schimke.composeai.uibuilder.protocol

import java.io.File
import java.security.MessageDigest
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class UiBuilderProtocolCompatibilityTest {
  private val strictJson = Json {
    classDiscriminator = "type"
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = false
  }

  private val fixtures: Map<String, KSerializer<Any>> =
    mapOf(
      fixtureSerializer("catalog.json", CatalogCapabilityV1.serializer()),
      fixtureSerializer("catalog-upgrade-delta.json", ServiceDeltaV1.serializer()),
      fixtureSerializer("lossless-document-command.json", LosslessProtocolFixtureV1.serializer()),
      fixtureSerializer("materialized-confetti.json", DesignDocumentV1.serializer()),
      fixtureSerializer("materialized-jetcaster.json", DesignDocumentV1.serializer()),
      fixtureSerializer("design-state.json", DesignStateV1.serializer()),
      fixtureSerializer("http-access-conflict-response.json", HttpResponseEnvelopeV1.serializer()),
      fixtureSerializer("http-commands-request.json", HttpRequestEnvelopeV1.serializer()),
      fixtureSerializer(
        "http-catalog-upgrade-blocked-response.json",
        HttpResponseEnvelopeV1.serializer(),
      ),
      fixtureSerializer(
        "http-catalog-upgrade-preview-request.json",
        HttpRequestEnvelopeV1.serializer(),
      ),
      fixtureSerializer(
        "http-catalog-upgrade-preview-response.json",
        HttpResponseEnvelopeV1.serializer(),
      ),
      fixtureSerializer("http-conflict-response.json", HttpResponseEnvelopeV1.serializer()),
      fixtureSerializer("http-list-designs-request.json", HttpRequestEnvelopeV1.serializer()),
      fixtureSerializer("http-list-designs-response.json", HttpResponseEnvelopeV1.serializer()),
      fixtureSerializer("service-delta.json", ServiceDeltaV1.serializer()),
      fixtureSerializer("mcp-design-access-response.json", McpResponseEnvelopeV1.serializer()),
      fixtureSerializer("mcp-export-request.json", McpRequestEnvelopeV1.serializer()),
      fixtureSerializer("mcp-export-response.json", McpResponseEnvelopeV1.serializer()),
      fixtureSerializer("mcp-get-design-access-request.json", McpRequestEnvelopeV1.serializer()),
      fixtureSerializer(
        "mcp-update-design-access-request.json",
        McpRequestEnvelopeV1.serializer(),
      ),
      fixtureSerializer("session-presence.json", DesignUpdateEnvelopeV1.serializer()),
    )

  @Test
  fun everyV1FixtureStrictlyRoundTrips() {
    fixtures.forEach { (name, serializer) ->
      val original = strictJson.parseToJsonElement(fixture(name))
      val decoded = strictJson.decodeFromJsonElement(serializer, original)
      val encoded = strictJson.encodeToJsonElement(serializer, decoded)
      assertEquals("round-trip mismatch for $name", original, encoded)
    }
  }

  @Test
  fun fixtureInventoryIsExplicit() {
    val present =
      fixturesDir().listFiles().orEmpty().filter { it.extension == "json" }.map { it.name }.toSet()
    assertEquals(fixtures.keys, present)
  }

  @Test
  fun unknownFieldsAreRejectedByStrictReaders() {
    val original = strictJson.parseToJsonElement(fixture("mcp-export-request.json")) as JsonObject
    val changed = JsonObject(original + ("futureField" to JsonPrimitive(true)))
    assertFailsWith<SerializationException> {
      strictJson.decodeFromJsonElement(McpRequestEnvelopeV1.serializer(), changed)
    }
  }

  @Test
  fun requiredEnvelopeFieldsCannotDisappear() {
    assertFailsWith<SerializationException> {
      strictJson.decodeFromString(
        HttpRequestEnvelopeV1.serializer(),
        """{"schemaVersion":1,"requestId":"r","request":{"type":"listCatalogs"}}""",
      )
    }
  }

  @Test
  fun actorIdentityIsExplicitAtTransportAndNestedCommandBoundaries() {
    val request =
      strictJson.decodeFromString(
        HttpRequestEnvelopeV1.serializer(),
        fixture("http-commands-request.json"),
      )
    val command = (request.request as ApplyOperationRequestV1).submission as DesignCommandV1
    assertEquals(request.actorId, command.actorId)

    val listRequest =
      strictJson.decodeFromString(
        HttpRequestEnvelopeV1.serializer(),
        fixture("http-list-designs-request.json"),
      )
    val listResponse =
      strictJson.decodeFromString(
        HttpResponseEnvelopeV1.serializer(),
        fixture("http-list-designs-response.json"),
      )
    val requester = (listResponse.response as DesignsResponseV1).designs.single().requesterAccess
    assertEquals(listRequest.actorId, requester.actorId)
  }

  @Test
  fun clientsCannotSupplyReducerPositionKeys() {
    assertFailsWith<SerializationException> {
      strictJson.decodeFromString(
        NodeLocationV1.serializer(),
        """{"afterNodeId":"anchor","positionKey":{"path":[10],"tieBreaker":"client"}}""",
      )
    }
  }

  @Test
  fun schemaVersionIsEncodedEvenWhenDefaultsAreDisabled() {
    val encoded =
      strictJson.encodeToJsonElement(
        McpRequestEnvelopeV1.serializer(),
        McpRequestEnvelopeV1(
          callId = "call",
          actorId = "actor",
          request = ListCatalogsRequestV1,
        ),
      ) as JsonObject
    assertEquals(JsonPrimitive(UI_BUILDER_SCHEMA_VERSION_V1), encoded["schemaVersion"])
  }

  @Test
  fun documentHashTracksCanonicalContentAndSurvivesSerialization() {
    val firstHash = sha256("{\"revision\":42}")
    val secondHash = sha256("{\"revision\":43}")
    assertNotEquals(firstHash, secondHash)

    val outcome =
      AcceptedOutcomeV1(
        "operation",
        43,
        101,
        secondHash,
        false,
        documentUpdatedAtEpochMillis = 1_750_000_010_123,
      )
    val encoded = strictJson.encodeToString(AcceptedOutcomeV1.serializer(), outcome)
    assertEquals(
      JsonPrimitive(1_750_000_010_123),
      (strictJson.parseToJsonElement(encoded) as JsonObject)["documentUpdatedAtEpochMillis"],
    )
    assertEquals(outcome, strictJson.decodeFromString(AcceptedOutcomeV1.serializer(), encoded))
  }

  @Test
  fun legacyDeltaWithoutDocumentTimestampStillRoundTrips() {
    val original = strictJson.parseToJsonElement(fixture("service-delta.json"))
    val decoded = strictJson.decodeFromJsonElement(ServiceDeltaV1.serializer(), original)

    assertEquals(null, decoded.operations.single().outcome.documentUpdatedAtEpochMillis)
    assertEquals(original, strictJson.encodeToJsonElement(ServiceDeltaV1.serializer(), decoded))
  }

  @Test
  fun catalogUpgradePreviewBindsAnOrderedDiffToTheExactCandidate() {
    val envelope =
      strictJson.decodeFromString(
        HttpResponseEnvelopeV1.serializer(),
        fixture("http-catalog-upgrade-preview-response.json"),
      )
    val preview = (envelope.response as CatalogUpgradePreviewResponseV1).preview

    assertEquals(CatalogUpgradePreviewStatusV1.READY, preview.status)
    assertEquals(preview.targetCatalogPin, preview.candidateDocument?.catalogPin)
    assertEquals(
      "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      preview.candidateDocumentHash,
    )
    assertEquals(preview.changes.map { it.path }.sorted(), preview.changes.map { it.path })
    assertEquals(
      listOf(
        ReplaceCatalogUpgradeChangeV1::class,
        ReplaceCatalogUpgradeChangeV1::class,
        RemoveCatalogUpgradeChangeV1::class,
        AddCatalogUpgradeChangeV1::class,
      ),
      preview.changes.map { it::class },
    )
  }

  @Test
  fun blockedCatalogUpgradeHasDiagnosticsWithoutACandidate() {
    val envelope =
      strictJson.decodeFromString(
        HttpResponseEnvelopeV1.serializer(),
        fixture("http-catalog-upgrade-blocked-response.json"),
      )
    val preview = (envelope.response as CatalogUpgradePreviewResponseV1).preview

    assertEquals(CatalogUpgradePreviewStatusV1.BLOCKED, preview.status)
    assertNull(preview.candidateDocument)
    assertNull(preview.candidateDocumentHash)
    assertEquals(0, preview.changes.size)
    assertEquals(CatalogUpgradeIssueSeverityV1.ERROR, preview.issues.single().severity)
  }

  @Test
  fun catalogRollbackIsAnAcceptedCompensatingHistoryEvent() {
    val delta =
      strictJson.decodeFromString(
        ServiceDeltaV1.serializer(),
        fixture("catalog-upgrade-delta.json"),
      )
    val upgrade =
      ((delta.operations[0].submission as DesignCommandV1).operations.single()
        as CatalogUpgradeMutationV1)
    val rollback =
      ((delta.operations[1].submission as DesignCommandV1).operations.single()
        as CatalogUpgradeMutationV1)

    assertEquals(upgrade.sourceCatalogPin, rollback.targetCatalogPin)
    assertEquals(upgrade.targetCatalogPin, rollback.sourceCatalogPin)
    assertEquals("catalog-upgrade-1", rollback.compensatesCatalogUpgradeOperationId)
    assertEquals(upgrade.targetDocumentHash, delta.operations[0].outcome.documentHash)
    assertEquals(rollback.targetDocumentHash, delta.operations[1].outcome.documentHash)
    assertEquals(listOf(14L, 15L), delta.operations.map { it.outcome.sequence })
  }

  @Test
  fun materializedBenchmarkDocumentsRetainCanonicalHashesAndExplicitZeroEdges() {
    val expectedHashes =
      mapOf(
        "materialized-confetti.json" to
          "7a19916f450c735be6ebecfad46498f994d33abcaebf196cac24b5d539eac517",
        "materialized-jetcaster.json" to
          "09b7af04ab546421f72b81b1c49564f044790b8f2db4d2304dc66ff73c148643",
      )
    expectedHashes.forEach { (name, expectedHash) ->
      val original = strictJson.parseToJsonElement(fixture(name))
      val decoded = strictJson.decodeFromJsonElement(DesignDocumentV1.serializer(), original)
      val encoded = strictJson.encodeToJsonElement(DesignDocumentV1.serializer(), decoded)

      assertEquals("materialized round-trip mismatch for $name", original, encoded)
      assertEquals(true, encoded.toString().contains("\"startDp\":0"))
      assertEquals("source hash mismatch for $name", expectedHash, sha256(canonicalJson(original)))
      assertEquals("encoded hash mismatch for $name", expectedHash, sha256(canonicalJson(encoded)))
    }
  }

  private fun fixture(name: String): String = File(fixturesDir(), name).readText()

  private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { byte
      ->
      "%02x".format(byte)
    }

  private fun canonicalJson(element: JsonElement): String =
    when (element) {
      is JsonObject ->
        element.entries
          .sortedBy { it.key }
          .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "${JsonPrimitive(key)}:${canonicalJson(value)}"
          }
      is JsonArray ->
        element.joinToString(
          separator = ",",
          prefix = "[",
          postfix = "]",
          transform = ::canonicalJson,
        )
      is JsonPrimitive -> {
        val number = element.takeUnless { it.isString }?.doubleOrNull
        if (number != null && number % 1.0 == 0.0) number.toLong().toString()
        else element.toString()
      }
    }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Any> fixtureSerializer(
    name: String,
    serializer: KSerializer<T>,
  ): Pair<String, KSerializer<Any>> = name to serializer as KSerializer<Any>

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

@Serializable
private data class LosslessProtocolFixtureV1(
  val document: DesignDocumentV1,
  val values: List<UiValueV1>,
  val assetSources: List<AssetSourceV1>,
  val submissions: List<DesignSubmissionV1>,
  val outcomes: List<CommandOutcomeV1>,
  val componentKinds: List<ComponentKindV1>,
  val propertyValueKinds: List<PropertyValueKindV1>,
  val dimensionUnits: List<DimensionUnitV1>,
  val jsonValueTypes: List<JsonValueTypeV1>,
  val wasmStatuses: List<WasmAdapterStatusV1>,
  val svgStatuses: List<SvgCapabilityStatusV1>,
  val svgFallbacks: List<SvgFallbackV1>,
  val themes: List<ThemeV1>,
  val layoutDirections: List<LayoutDirectionV1>,
  val windowPostures: List<WindowPostureV1>,
  val animationStates: List<AnimationStateV1>,
  val stateVariableTypes: List<StateVariableTypeV1>,
  val stateValueTypes: List<StateValueTypeV1>,
  val statePersistences: List<StatePersistenceV1>,
  val conflictCodes: List<ConflictCodeV1>,
  val rejectionCodes: List<RejectionCodeV1>,
)
