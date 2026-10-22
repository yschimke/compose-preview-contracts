package ee.schimke.composeai.daemon.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * [DaemonLaunchDescriptor] is both sides of `daemon-launch.json`: the Gradle plugin's
 * `daemon-launch-builder` constructs and encodes it, and the daemon supervisors
 * [parse][DaemonLaunchDescriptor.parse] it. These pin the file the plugin writes today, so the
 * writer moving onto this type cannot change what is on disk.
 */
class DaemonLaunchDescriptorTest {

  /**
   * A schema-v2 descriptor exactly as `DaemonLaunchBuilder.json` (pretty-printed, `encodeDefaults`,
   * `explicitNulls`) wrote it while the writer still declared its own `DaemonClasspathDescriptor`.
   */
  private val pluginWrittenV2 =
    """
    {
        "schemaVersion": 2,
        "modulePath": ":samples:android",
        "variant": "debug",
        "enabled": true,
        "mainClass": "ee.schimke.composeai.daemon.DaemonMain",
        "javaLauncher": null,
        "classpath": [
            "/r/daemon.jar",
            "/r/app.jar"
        ],
        "jvmArgs": [
            "-Xmx2g",
            "--add-opens=java.base/java.lang=ALL-UNNAMED"
        ],
        "systemProperties": {
            "composeai.daemon.protocolVersion": "1",
            "composeai.daemon.modulePath": ":samples:android"
        },
        "workingDirectory": "/w/samples/android",
        "manifestPath": "/w/samples/android/build/compose-previews/previews.json",
        "btaCompile": {
            "implClasspath": [
                "/k/kotlin-build-tools-impl.jar"
            ],
            "compileClasspath": [
                "/c/compose-runtime.jar"
            ],
            "compilerPlugins": [
                "/k/kotlin-compose-compiler-plugin-embeddable.jar"
            ],
            "outputDir": "/w/samples/android/build/classes",
            "moduleName": "android_debug",
            "icWorkingDir": "/w/samples/android/build/compose-previews/daemon-state/bta-ic",
            "ineligibilityReason": null
        }
    }
    """
      .trimIndent()

  /** The writer's canonical encoder settings, as `daemon-launch-builder` declares them. */
  private val writerJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
  }

  private fun expected(): DaemonLaunchDescriptor =
    DaemonLaunchDescriptor.Builder(
        schemaVersion = DaemonLaunchDescriptor.SCHEMA_VERSION,
        modulePath = ":samples:android",
        variant = "debug",
        enabled = true,
        mainClass = "ee.schimke.composeai.daemon.DaemonMain",
        classpath = listOf("/r/daemon.jar", "/r/app.jar"),
        jvmArgs = listOf("-Xmx2g", "--add-opens=java.base/java.lang=ALL-UNNAMED"),
        systemProperties =
          linkedMapOf(
            "composeai.daemon.protocolVersion" to "1",
            "composeai.daemon.modulePath" to ":samples:android",
          ),
        workingDirectory = "/w/samples/android",
        manifestPath = "/w/samples/android/build/compose-previews/previews.json",
      )
      .also {
        it.btaCompile =
          BtaCompileConfig.Builder(
              implClasspath = listOf("/k/kotlin-build-tools-impl.jar"),
              compileClasspath = listOf("/c/compose-runtime.jar"),
              compilerPlugins = listOf("/k/kotlin-compose-compiler-plugin-embeddable.jar"),
              outputDir = "/w/samples/android/build/classes",
              moduleName = "android_debug",
              icWorkingDir = "/w/samples/android/build/compose-previews/daemon-state/bta-ic",
            )
            .build()
      }
      .build()

  @Test
  fun `parses a plugin-written v2 descriptor including its btaCompile block`() {
    val parsed = DaemonLaunchDescriptor.parse(pluginWrittenV2)
    assertEquals(expected(), parsed)
    assertEquals("android_debug", parsed.btaCompile?.moduleName)
    assertNull(parsed.btaCompile?.ineligibilityReason)
    assertEquals(emptyList(), parsed.jailCommand)
    assertNull(parsed.hardTtlSeconds)
  }

  @Test
  fun `the writer's encoding keeps every key the plugin wrote, with the same values`() {
    val written = writerJson.parseToJsonElement(pluginWrittenV2).jsonObject
    val reencoded =
      writerJson
        .parseToJsonElement(
          writerJson.encodeToString(DaemonLaunchDescriptor.serializer(), expected())
        )
        .jsonObject

    // Every key a released reader knows is still there, unchanged. The only additions are the two
    // sandbox fields serve already writes and the optional `sourceWatch`, at their defaults — which
    // every reader tolerates (the VS Code reader is a plain `JSON.parse` that checks only
    // `schemaVersion`).
    for ((key, value) in written) assertEquals(value, reencoded[key], key)
    assertEquals(
      written.keys + setOf("jailCommand", "hardTtlSeconds", "sourceWatch"),
      reencoded.keys,
    )
    assertEquals(
      expected(),
      DaemonLaunchDescriptor.parse(
        writerJson.encodeToString(DaemonLaunchDescriptor.serializer(), expected())
      ),
    )
  }

  @Test
  fun `a descriptor without btaCompile parses to null`() {
    val withoutBta = pluginWrittenV2.substringBefore(",\n    \"btaCompile\"") + "\n}"
    assertNull(DaemonLaunchDescriptor.parse(withoutBta).btaCompile)
  }

  @Test
  fun `newBuilder carries btaCompile and its ineligibility reason`() {
    val bta =
      BtaCompileConfig.Builder(
          implClasspath = listOf("a.jar"),
          compileClasspath = emptyList(),
          compilerPlugins = emptyList(),
          outputDir = "/o",
          moduleName = "m",
          icWorkingDir = "/ic",
        )
        .also { it.ineligibilityReason = "KSP is applied" }
        .build()
    assertEquals(bta, bta.newBuilder().build())

    val descriptor = expected().newBuilder().also { it.btaCompile = bta }.build()
    assertEquals(descriptor, descriptor.newBuilder().build())
    assertEquals("KSP is applied", descriptor.btaCompile?.ineligibilityReason)
    assertEquals(bta, descriptor.withSandboxCount(4).btaCompile)
  }

  @Test
  fun `parses a sourceWatch block, and a descriptor without one parses to null`() {
    assertNull(DaemonLaunchDescriptor.parse(pluginWrittenV2).sourceWatch)

    val withWatch =
      pluginWrittenV2.removeSuffix("}") +
        """
        ,"sourceWatch": {
          "sourceDirs": ["/w/samples/android/src/main/kotlin"],
          "buildFiles": ["/w/samples/android/build.gradle.kts"]
        }}
        """
          .trimIndent()
    val watch = DaemonLaunchDescriptor.parse(withWatch).sourceWatch
    assertEquals(listOf("/w/samples/android/src/main/kotlin"), watch?.sourceDirs)
    // A field the writer left out is empty, not an error.
    assertEquals(emptyList(), watch?.resourceDirs)
    assertEquals(listOf("/w/samples/android/build.gradle.kts"), watch?.buildFiles)
  }

  @Test
  fun `newBuilder carries sourceWatch`() {
    val watch =
      SourceWatch.Builder()
        .also {
          it.sourceDirs = listOf("/m/src/main/kotlin")
          it.resourceDirs = listOf("/m/src/main/res")
        }
        .build()
    assertEquals(watch, watch.newBuilder().build())
    val descriptor = expected().newBuilder().also { it.sourceWatch = watch }.build()
    assertEquals(descriptor, descriptor.newBuilder().build())
    assertEquals(watch, descriptor.withSandboxCount(4).sourceWatch)
  }
}
