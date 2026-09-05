package ee.schimke.composeai.uibuilder.protocol

import java.io.File
import java.security.MessageDigest
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.elementNames
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

  /**
   * Every subclass discriminator the sealed hierarchy declares, read from the serializer.
   *
   * From the descriptor rather than `sealedSubclasses`, which needs `kotlin-reflect` on the test
   * classpath — and this asks the better question anyway: what a client sees is the serial name,
   * not the Kotlin class.
   */
  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  private fun modifierSerialNames(): Set<String> =
    DesignModifierV1.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()

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

  @Test
  fun stateAuthoringMutationsRoundTripInsideACommandBatch() {
    // They have to survive as `DesignMutationV1`, not just on their own: a client sends them inside
    // `DesignCommandV1.operations`, so the discriminator is what carries them.
    val wire =
      """
      {"type":"batch","designId":"d","operationId":"o","actorId":"a","clientId":"c",
       "baseRevision":7,
       "operations":[
         {"type":"setStateVariable","name":"expanded",
          "declaration":{"type":"value","valueType":"bool","initialValue":false,
                         "persistence":"preview"}},
         {"type":"setEventBinding","nodeId":"card","event":"click",
          "actions":[{"type":"toggle","variable":"expanded"}]},
         {"type":"setEventBinding","nodeId":"card","event":"longPress","actions":[]},
         {"type":"removeStateVariable","name":"stale"}
       ]}
      """
        .trimIndent()
        .replace("\n", "")
    val decoded = strictJson.decodeFromString(DesignSubmissionV1.serializer(), wire)
    val command = decoded as DesignCommandV1

    val declared = command.operations[0] as SetStateVariableMutationV1
    assertEquals("expanded", declared.name)
    assertEquals(StateVariableTypeV1.VALUE, declared.declaration.type)

    val bound = command.operations[1] as SetEventBindingMutationV1
    assertEquals(listOf(ToggleActionV1("expanded")), bound.actions)

    // An empty list is how an event is unbound, and it survives the round trip as empty rather
    // than vanishing into the default.
    val unbound = command.operations[2] as SetEventBindingMutationV1
    assertEquals(emptyList<DesignActionV1>(), unbound.actions)

    assertEquals("stale", (command.operations[3] as RemoveStateVariableMutationV1).name)
    assertEquals(
      strictJson.parseToJsonElement(wire),
      strictJson.encodeToJsonElement(DesignSubmissionV1.serializer(), decoded),
    )
  }

  @Test
  fun removingAStateVariableIsNotSpelledAsAnAbsentDeclaration() {
    // Strict readers run with explicitNulls = false, so an absent field and a null one are the same
    // bytes. If removal were `declaration: null`, it would be indistinguishable from a malformed
    // declare — so a declare without one is rejected outright and removal has its own type.
    assertFailsWith<SerializationException> {
      strictJson.decodeFromString(
        DesignMutationV1.serializer(),
        """{"type":"setStateVariable","name":"expanded"}""",
      )
    }
  }

  @Test
  fun unbindingAnEventSurvivesEncodingRatherThanBecomingAnAbsentField() {
    // Strict readers run with encodeDefaults = false. When `actions` had a default of `emptyList()`
    // an unbind encoded to nothing at all, so the reducer could not tell "remove this handler" from
    // "I said nothing about handlers". Required, it is always on the wire.
    val unbind: DesignMutationV1 = SetEventBindingMutationV1("card", "click", emptyList())
    val encoded =
      strictJson.encodeToJsonElement(DesignMutationV1.serializer(), unbind) as JsonObject

    assertEquals(JsonArray(emptyList()), encoded["actions"])
    assertEquals(unbind, strictJson.decodeFromJsonElement(DesignMutationV1.serializer(), encoded))
  }

  @Test
  fun aModifierChainRoundTripsInOrderInsideACommandBatch() {
    // Order is the contract for a modifier chain: padding-then-size is a different layout from
    // size-then-padding. A round trip that preserved the set and not the sequence would be wrong,
    // so this asserts the whole list positionally. That every *type* round trips is
    // `everyModifierTypeIsCarriedByTheLosslessFixture`, which reads the fixture rather than a
    // list here that a new modifier could quietly not join.
    val wire =
      """
      {"type":"batch","designId":"d","operationId":"o","actorId":"a","clientId":"c",
       "baseRevision":9,
       "operations":[
         {"type":"setModifiers","nodeId":"card","modifiers":[
           {"type":"padding","startDp":16,"topDp":8,"endDp":16,"bottomDp":8},
           {"type":"size","widthDp":240,"heightDp":96},
           {"type":"clip","shape":"medium"},
           {"type":"fillMaxWidth"},
           {"type":"fillMaxSize"},
           {"type":"matchParentSize"}]},
         {"type":"setModifiers","nodeId":"row","modifiers":[]}
       ]}
      """
        .trimIndent()
        .replace("\n", "")
    val decoded = strictJson.decodeFromString(DesignSubmissionV1.serializer(), wire)
    val command = decoded as DesignCommandV1

    assertEquals(
      listOf(
        PaddingModifierV1(JsonPrimitive(16), JsonPrimitive(8), JsonPrimitive(16), JsonPrimitive(8)),
        SizeModifierV1(JsonPrimitive(240), JsonPrimitive(96)),
        ClipModifierV1("medium"),
        FillMaxWidthModifierV1,
        FillMaxSizeModifierV1,
        MatchParentSizeModifierV1,
      ),
      (command.operations[0] as SetModifiersMutationV1).modifiers,
    )

    // An empty list clears the chain, and it survives as empty rather than as an absence.
    assertEquals(
      emptyList<DesignModifierV1>(),
      (command.operations[1] as SetModifiersMutationV1).modifiers,
    )
    assertEquals(
      strictJson.parseToJsonElement(wire),
      strictJson.encodeToJsonElement(DesignSubmissionV1.serializer(), decoded),
    )
  }

  @Test
  fun clearingModifiersSurvivesEncodingRatherThanBecomingAnAbsentField() {
    // Same trap as SetEventBindingMutationV1.actions, and the reason `modifiers` has no default:
    // with encodeDefaults = false a defaulted empty list is dropped, so "this node has no
    // modifiers any more" would reach the reducer as "I said nothing about modifiers".
    val cleared: DesignMutationV1 = SetModifiersMutationV1("card", emptyList())
    val encoded =
      strictJson.encodeToJsonElement(DesignMutationV1.serializer(), cleared) as JsonObject

    assertEquals(JsonArray(emptyList()), encoded["modifiers"])
    assertEquals(cleared, strictJson.decodeFromJsonElement(DesignMutationV1.serializer(), encoded))
  }

  @Test
  fun anEnvironmentWithoutATypefaceStillDecodes() {
    // The field is additive, so every document written before it existed must still read. With
    // explicitNulls = false the absent field and an explicit null are the same wire, and both mean
    // "the renderer's platform default" rather than "a face called null".
    val withoutTypeface =
      """
      {"widthDp":412,"heightDp":892,"density":2.625,"theme":"light","locale":"en-US",
       "fontScale":1.0,"layoutDirection":"ltr"}
      """
        .trimIndent()
    val decoded = strictJson.decodeFromString(DesignEnvironmentV1.serializer(), withoutTypeface)

    assertNull(decoded.typeface)
    // …and it does not reappear on the way out, or every stored document would gain a field.
    assertNull(
      (strictJson.encodeToJsonElement(DesignEnvironmentV1.serializer(), decoded) as JsonObject)[
        "typeface"]
    )
  }

  @Test
  fun aTypefaceIsCarriedAsABareFamilyName() {
    // The value is a family name, never a file or a URL: the document says WHICH face, and the
    // renderer decides whether it vendors that family or downloads it. A fixture that let a URL
    // through here would make the document host-specific.
    val environment =
      strictJson.decodeFromString(
        DesignEnvironmentV1.serializer(),
        """
        {"widthDp":412,"heightDp":892,"density":2.625,"theme":"light","locale":"en-US",
         "fontScale":1.0,"layoutDirection":"ltr","typeface":"Space Grotesk"}
        """
          .trimIndent(),
      )

    assertEquals("Space Grotesk", environment.typeface)
    assertEquals(
      strictJson.parseToJsonElement(
        """{"widthDp":412,"heightDp":892,"density":2.625,"theme":"light","locale":"en-US","fontScale":1.0,"layoutDirection":"ltr","typeface":"Space Grotesk"}"""
      ),
      strictJson.encodeToJsonElement(DesignEnvironmentV1.serializer(), environment),
    )
  }

  @Test
  fun settingAndResettingATypefaceRoundTripUnderTheirDiscriminators() {
    val set: EnvironmentChangeV1 = SetTypefaceEnvironmentChangeV1("Space Grotesk")
    val reset: EnvironmentChangeV1 = ResetTypefaceEnvironmentChangeV1

    val setWire =
      strictJson.encodeToJsonElement(EnvironmentChangeV1.serializer(), set) as JsonObject
    assertEquals(JsonPrimitive("setTypeface"), setWire["type"])
    assertEquals(JsonPrimitive("Space Grotesk"), setWire["value"])
    assertEquals(set, strictJson.decodeFromJsonElement(EnvironmentChangeV1.serializer(), setWire))

    val resetWire =
      strictJson.encodeToJsonElement(EnvironmentChangeV1.serializer(), reset) as JsonObject
    assertEquals(JsonPrimitive("resetTypeface"), resetWire["type"])
    assertEquals(
      reset,
      strictJson.decodeFromJsonElement(EnvironmentChangeV1.serializer(), resetWire),
    )

    assertEquals(EnvironmentFieldV1.TYPEFACE, set.field)
    assertEquals(EnvironmentFieldV1.TYPEFACE, reset.field)
  }

  @Test
  fun everyModifierTypeIsCarriedByTheLosslessFixture() {
    // The vocabulary and the fixture are two halves of one promise: the fixture is what proves a
    // type survives a strict round trip, and a modifier that is not in it is a type nothing has
    // ever encoded. Reflected over the sealed hierarchy rather than listed, so the next modifier
    // added to `DesignModifierV1` fails here until the fixture carries it.
    val document =
      strictJson
        .decodeFromString(
          LosslessProtocolFixtureV1.serializer(),
          fixture("lossless-document-command.json"),
        )
        .document
    val carried =
      document.nodes.values
        .flatMap(DesignNodeV1::modifiers)
        .map { modifier ->
          (strictJson.encodeToJsonElement(DesignModifierV1.serializer(), modifier) as JsonObject)
            .getValue("type")
            .let { (it as JsonPrimitive).content }
        }
        .toSet()

    assertEquals(modifierSerialNames(), carried)
  }

  @Test
  fun aScopedModifierNamesTheAxisItsScopeAligns() {
    // Three alignment modifiers rather than one with every value: a Row aligns vertically and a
    // Column horizontally, so a single `align` would carry values half its uses must ignore, and
    // a document could say "align this row child to the start" and mean nothing.
    val box: DesignModifierV1 = AlignModifierV1(AlignmentV1.BOTTOM_END)
    val column: DesignModifierV1 = AlignHorizontalModifierV1(HorizontalAlignmentV1.END)
    val row: DesignModifierV1 = AlignVerticalModifierV1(VerticalAlignmentV1.CENTER_VERTICALLY)

    listOf(box, column, row).forEach { modifier ->
      val encoded =
        strictJson.encodeToJsonElement(DesignModifierV1.serializer(), modifier) as JsonObject
      assertEquals(
        modifier,
        strictJson.decodeFromJsonElement(DesignModifierV1.serializer(), encoded),
      )
    }
    assertEquals(
      "bottomEnd",
      (strictJson.encodeToJsonElement(DesignModifierV1.serializer(), box) as JsonObject)[
          "alignment"]
        ?.let { (it as JsonPrimitive).content },
    )
  }

  @Test
  fun theFillsStayFieldlessSoStoredDocumentsKeepTheirBytes() {
    // `fillMaxWidth(0.5f)` is deliberately absent: these three are data objects whose encoded form
    // is the discriminator and nothing else, and giving them a fraction would change the bytes of
    // every document already stored — and every canonical hash taken over one.
    listOf(FillMaxSizeModifierV1, FillMaxWidthModifierV1, FillMaxHeightModifierV1).forEach {
      modifier ->
      val encoded =
        strictJson.encodeToJsonElement(DesignModifierV1.serializer(), modifier) as JsonObject
      assertEquals(setOf("type"), encoded.keys)
    }
  }

  @Test
  fun everyEnvironmentFieldHasAChangeThatNamesIt() {
    // The enum and the change set are two halves of one vocabulary. A field with no change is a
    // value a client can read and never write — which is exactly how `typeface` would have shipped
    // if only the document class had been touched.
    val named =
      setOf(
        SetWidthDpEnvironmentChangeV1(0).field,
        SetHeightDpEnvironmentChangeV1(0).field,
        SetDensityEnvironmentChangeV1(1.0).field,
        SetThemeEnvironmentChangeV1(ThemeV1.LIGHT).field,
        SetDynamicColorEnvironmentChangeV1(true).field,
        SetLocaleEnvironmentChangeV1("en").field,
        SetFontScaleEnvironmentChangeV1(1.0).field,
        SetLayoutDirectionEnvironmentChangeV1(LayoutDirectionV1.LTR).field,
        SetWindowPostureEnvironmentChangeV1(WindowPostureV1.FLAT).field,
        SetBrowserZoomPercentEnvironmentChangeV1(100).field,
        SetFixedTimeEnvironmentChangeV1("10:10").field,
        SetAnimationsEnvironmentChangeV1(AnimationStateV1.SETTLED).field,
        SetNetworkAccessEnvironmentChangeV1(false).field,
        SetBackgroundEnvironmentChangeV1(StringValueV1("x")).field,
        SetTypefaceEnvironmentChangeV1("Inter").field,
      )

    assertEquals(EnvironmentFieldV1.entries.toSet(), named)
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
