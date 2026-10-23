package ee.schimke.composeai.daemon.protocol

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trips each protocol message through kotlinx.serialization and asserts structural equality
 * with the on-disk fixture under
 * [docs/daemon/protocol-fixtures/](../../../../../../../docs/daemon/protocol-fixtures/).
 *
 * The same fixtures are consumed by Stream C's TypeScript test suite — they are the cross-language
 * source of truth for the wire format. A round-trip here protects against three regressions:
 *
 * 1. Field rename without updating the fixture.
 * 2. Missing `@SerialName` on an enum value the JSON spells differently.
 * 3. Required field becoming optional or vice-versa silently.
 *
 * Comparison is done at the [JsonElement] level (not raw text) so whitespace, key ordering, and
 * trailing-newline differences in the fixtures don't make the test brittle.
 */
class MessagesTest {

  private val json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = false
  }

  @Test fun roundTripInitializeParams() = roundTrip<InitializeParams>("client-initialize.json")

  @Test
  fun roundTripInitializeResult() = roundTrip<InitializeResult>("daemon-initializeResult.json")

  // compose-ai-tools#6114 — an `initialize` answered before the host is ready.
  @Test
  fun roundTripInitializeParamsWithBootProgress() =
    roundTrip<InitializeParams>("client-initialize-bootProgress.json")

  @Test
  fun roundTripInitializeResultBooting() =
    roundTrip<InitializeResult>("daemon-initializeResult-booting.json")

  @Test fun roundTripBootProgress() = roundTrip<BootProgressParams>("daemon-bootProgress.json")

  @Test
  fun roundTripBootProgressReady() = roundTrip<BootProgressParams>("daemon-bootProgress-ready.json")

  @Test
  fun roundTripBootProgressFailed() =
    roundTrip<BootProgressParams>("daemon-bootProgress-failed.json")

  @Test
  fun roundTripCompileWarmUpResult() =
    roundTrip<CompileWarmUpResult>("daemon-compileWarmUpResult.json")

  @Test
  fun roundTripCompileWarmUpResultUnavailable() =
    roundTrip<CompileWarmUpResult>("daemon-compileWarmUpResult-unavailable.json")

  @Test
  fun roundTripCompileWarmUpParams() = roundTrip<CompileWarmUpParams>("client-compileWarmUp.json")

  @Test
  fun compileWarmUpParamsBuildsAndDecodesFromEmpty() {
    val built = CompileWarmUpParams.Builder().also { it.sources = listOf("/m/A.kt") }.build()
    assertEquals(built, built.newBuilder().build())
    assertEquals(
      emptyList<String>(),
      json.decodeFromString(CompileWarmUpParams.serializer(), "{}").sources,
    )
  }

  @Test
  fun compileWarmUpResultBuildsAndDecodesFromReadyAlone() {
    val built =
      CompileWarmUpResult.Builder(ready = false).also { it.message = "not configured" }.build()
    assertEquals(built, built.newBuilder().build())
    val bare = json.decodeFromString(CompileWarmUpResult.serializer(), """{"ready":true}""")
    assertEquals(true, bare.ready)
    assertEquals(0L, bare.durationMs)
    assertEquals(null, bare.message)
    assertEquals("compileWarmUp", CompileWarmUpResult.METHOD)
  }

  @Test
  fun bootProgressOptInIsAbsentFromTheWireUnlessSet() {
    // A client that does not opt in must send exactly the pre-#6114 shape, and a daemon that is
    // already up must answer with exactly the pre-#6114 shape, even under an encoder that writes
    // defaults.
    val loud = Json { encodeDefaults = true }
    val caps = loud.encodeToString(ClientCapabilities.serializer(), ClientCapabilities(true, false))
    assertEquals("""{"visibility":true,"metrics":false}""", caps)
    val result =
      json.decodeFromString(InitializeResult.serializer(), fixture("daemon-initializeResult.json"))
    assertEquals(false, result.booting)
    assertEquals(
      false,
      loud.encodeToString(InitializeResult.serializer(), result).contains("booting"),
    )
  }

  @Test
  fun bootProgressDecodesAPhaseNoReleaseNames() {
    // An unknown phase from a newer daemon decodes rather than failing the notification.
    val future =
      json.decodeFromString(
        BootProgressParams.serializer(),
        """{"phase":"downloadingAndroidAll"}""",
      )
    assertEquals("downloadingAndroidAll", future.phase)
    assertEquals(null, future.slot)
  }

  @Test
  fun preBootProgressConstructorsAndCopyStillLink() {
    val caps = ClientCapabilities(visibility = true, metrics = true)
    assertEquals(false, caps.bootProgress)
    val opted = caps.newBuilder().also { it.bootProgress = true }.build()
    assertEquals(true, opted.copy(metrics = false).bootProgress)
    val result =
      json.decodeFromString(InitializeResult.serializer(), fixture("daemon-initializeResult.json"))
    val booting = result.newBuilder().also { it.booting = true }.build()
    assertEquals(true, booting.copy(pid = 1).booting)
  }

  @Test fun roundTripSetVisibleParams() = roundTrip<SetVisibleParams>("client-setVisible.json")

  @Test fun roundTripSetFocusParams() = roundTrip<SetFocusParams>("client-setFocus.json")

  @Test fun roundTripFileChangedParams() = roundTrip<FileChangedParams>("client-fileChanged.json")

  @Test fun roundTripRenderNowParams() = roundTrip<RenderNowParams>("client-renderNow.json")

  @Test
  fun roundTripRenderNowParamsWithOverrides() =
    roundTrip<RenderNowParams>("client-renderNow-overrides.json")

  @Test fun roundTripRenderNowResult() = roundTrip<RenderNowResult>("daemon-renderNowResult.json")

  // Issue #3749 — `preview/rows`, the `@PreviewParameter` row enumeration surface.
  @Test fun roundTripPreviewRowsParams() = roundTrip<PreviewRowsParams>("client-previewRows.json")

  @Test
  fun roundTripPreviewRowsResult() = roundTrip<PreviewRowsResult>("daemon-previewRowsResult.json")

  @Test
  fun roundTripDiscoveryUpdatedParams() =
    roundTrip<DiscoveryUpdatedParams>("daemon-discoveryUpdated.json")

  @Test
  fun roundTripRenderStartedParams() = roundTrip<RenderStartedParams>("daemon-renderStarted.json")

  @Test
  fun roundTripRenderFinishedParams() =
    roundTrip<RenderFinishedParams>("daemon-renderFinished.json")

  @Test
  fun roundTripRenderFailedParams() = roundTrip<RenderFailedParams>("daemon-renderFailed.json")

  @Test
  fun roundTripClasspathDirtyParams() =
    roundTrip<ClasspathDirtyParams>("daemon-classpathDirty.json")

  @Test
  fun roundTripSandboxRecycleParams() =
    roundTrip<SandboxRecycleParams>("daemon-sandboxRecycle.json")

  @Test
  fun roundTripDaemonWarmingParams() = roundTrip<DaemonWarmingParams>("daemon-daemonWarming.json")

  @Test
  fun roundTripDaemonReadyParams() {
    // Empty-object payload — read raw, decode, re-encode, compare.
    val text = fixture("daemon-daemonReady.json")
    val parsed = json.decodeFromString(DaemonReadyParams.serializer(), text)
    val reEncoded = json.encodeToString(DaemonReadyParams.serializer(), parsed)
    assertEquals(json.parseToJsonElement(text), json.parseToJsonElement(reEncoded))
  }

  @Test fun roundTripLogParams() = roundTrip<LogParams>("daemon-log.json")

  // D1 — data product fixtures. See docs/daemon/DATA-PRODUCTS.md § "Wire surface".

  @Test fun roundTripDataFetchParams() = roundTrip<DataFetchParams>("client-dataFetch.json")

  @Test fun roundTripDataFetchResult() = roundTrip<DataFetchResult>("daemon-dataFetchResult.json")

  @Test
  fun roundTripDataSubscribeParams() = roundTrip<DataSubscribeParams>("client-dataSubscribe.json")

  @Test
  fun roundTripDataSubscribeParamsWithParams() =
    roundTrip<DataSubscribeParams>("client-dataSubscribe-withParams.json")

  @Test
  fun roundTripDataSubscribeResult() =
    roundTrip<DataSubscribeResult>("daemon-dataSubscribeResult.json")

  @Test
  fun roundTripRenderFinishedParamsWithDataProducts() =
    roundTrip<RenderFinishedParams>("daemon-renderFinished-withDataProducts.json")

  @Test
  fun roundTripRenderFinishedParamsWithRecomposition() =
    roundTrip<RenderFinishedParams>("daemon-renderFinished-withRecomposition.json")

  // compose-preview-server#1181 — the per-render work trace.
  @Test
  fun roundTripRenderFinishedParamsWithWorkTrace() =
    roundTrip<RenderFinishedParams>("daemon-renderFinished-withWorkTrace.json")

  @Test fun roundTripJsonRpcRequest() = roundTrip<JsonRpcRequest>("envelope-request.json")

  @Test fun roundTripJsonRpcResponse() = roundTrip<JsonRpcResponse>("envelope-response.json")

  @Test
  fun roundTripJsonRpcNotification() = roundTrip<JsonRpcNotification>("envelope-notification.json")

  @Test
  fun roundTripJsonRpcErrorResponse() {
    val text = fixture("envelope-errorResponse.json")
    val parsed = json.decodeFromString(JsonRpcResponse.serializer(), text)
    val reEncoded = json.encodeToString(JsonRpcResponse.serializer(), parsed)
    assertEquals(json.parseToJsonElement(text), json.parseToJsonElement(reEncoded))
  }

  /**
   * Smoke-test that fixture coverage is at least as broad as the inventory in
   * [docs/daemon/protocol-fixtures/README.md]. This catches a "we added a new message but forgot
   * the fixture" regression in PR review.
   */
  @Test
  fun fixtureInventoryMatchesExpected() {
    val present = fixturesDir().list()?.filter { it.endsWith(".json") }?.toSet().orEmpty()
    val expected =
      setOf(
        "client-initialize.json",
        "daemon-initializeResult.json",
        "client-setVisible.json",
        "client-setFocus.json",
        "client-fileChanged.json",
        "client-renderNow.json",
        "client-renderNow-overrides.json",
        "daemon-renderNowResult.json",
        "client-previewRows.json",
        "daemon-previewRowsResult.json",
        "daemon-discoveryUpdated.json",
        "daemon-renderStarted.json",
        "daemon-renderFinished.json",
        "daemon-renderFailed.json",
        "daemon-classpathDirty.json",
        "daemon-sandboxRecycle.json",
        "daemon-daemonWarming.json",
        "daemon-daemonReady.json",
        "daemon-log.json",
        // compose-ai-tools#6114 — early `initialize` and boot progress.
        "client-initialize-bootProgress.json",
        "daemon-initializeResult-booting.json",
        "daemon-bootProgress.json",
        "daemon-bootProgress-ready.json",
        "daemon-bootProgress-failed.json",
        "client-compileWarmUp.json",
        "daemon-compileWarmUpResult.json",
        "daemon-compileWarmUpResult-unavailable.json",
        "envelope-request.json",
        "envelope-response.json",
        "envelope-notification.json",
        "envelope-errorResponse.json",
        // D1 — data products.
        "client-dataFetch.json",
        "daemon-dataFetchResult.json",
        "client-dataSubscribe.json",
        "client-dataSubscribe-withParams.json",
        "daemon-dataSubscribeResult.json",
        "daemon-renderFinished-withDataProducts.json",
        "daemon-renderFinished-withRecomposition.json",
        "daemon-renderFinished-withWorkTrace.json",
      )
    val missing = expected - present
    assertEquals("missing protocol fixtures: $missing", emptySet<String>(), missing)
  }

  // -- RenderErrorKind tolerant decode (issue #1789 / VERSIONING.md § 4.1) ----

  @Test
  fun renderErrorKindDecodesFineGrainedDiscriminants() {
    val params =
      json.decodeFromString(
        RenderFailedParams.serializer(),
        """{"id":"com.example.HomeKt#HomePreview","error":""" +
          """{"kind":"classpathSkew","message":"Implemented only in JetBrains fork",""" +
          """"suggestion":"use the org.jetbrains.compose UI artifacts"}}""",
      )
    assertEquals(RenderErrorKind.CLASSPATH_SKEW, params.error.kind)
    // Re-encodes back to the documented wire spelling.
    val reEncoded = json.encodeToString(RenderFailedParams.serializer(), params)
    assertEquals(
      "classpathSkew",
      json
        .parseToJsonElement(reEncoded)
        .let { it as kotlinx.serialization.json.JsonObject }["error"]
        .let { it as kotlinx.serialization.json.JsonObject }["kind"]
        .let { (it as kotlinx.serialization.json.JsonPrimitive).content },
    )
  }

  @Test
  fun renderErrorKindToleratesAnUnknownFutureValue() {
    // A newer daemon emits a kind this client predates — it must decode to UNKNOWN, not throw.
    val params =
      json.decodeFromString(
        RenderFailedParams.serializer(),
        """{"id":"p","error":{"kind":"someFutureKind","message":"boom"}}""",
      )
    assertEquals(RenderErrorKind.UNKNOWN, params.error.kind)
  }

  // -- helpers ----------------------------------------------------------------

  private inline fun <reified T : Any> roundTrip(fixtureName: String) {
    val text = fixture(fixtureName)
    val serializer = kotlinx.serialization.serializer<T>()
    val parsed = json.decodeFromString(serializer, text)
    val reEncoded = json.encodeToString(serializer, parsed)
    val originalTree = json.parseToJsonElement(text)
    val reEncodedTree = json.parseToJsonElement(reEncoded)
    assertEquals("round-trip mismatch for $fixtureName", originalTree, reEncodedTree)
  }

  private fun fixture(name: String): String = File(fixturesDir(), name).readText()

  private fun fixturesDir(): File {
    // Walk up from the working directory until we find docs/daemon/
    // protocol-fixtures. Robolectric tests get a per-test cwd that is the
    // module's project dir, so a relative path of "../docs/..." works in
    // both Gradle and IDE runs.
    var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
    while (dir != null) {
      val candidate = File(dir, "docs/daemon/protocol-fixtures")
      if (candidate.isDirectory) return candidate
      dir = dir.parentFile
    }
    error("could not locate docs/daemon/protocol-fixtures from ${System.getProperty("user.dir")}")
  }
}
