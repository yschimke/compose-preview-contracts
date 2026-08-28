package ee.schimke.composeai.daemon.devices

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class DeviceDimensionsCatalogDriftTest {

  @Test
  fun daemonCatalogMatchesGradlePluginCatalog() {
    val pluginRoot = findPluginRepoRoot()
    assumeTrue(
      "no compose-ai-tools checkout found (set COMPOSE_AI_TOOLS_ROOT) — skipping cross-repo drift check",
      pluginRoot != null,
    )
    val plugin =
      readCatalog(
        pluginRoot!!.resolve(
          "gradle-plugin/preview-discovery/src/main/kotlin/ee/schimke/composeai/discovery/DeviceDimensions.kt"
        )
      )
    val daemon = readCatalog(daemonCopy())

    assertFalse("plugin DeviceDimensions catalog should not be empty", plugin.isEmpty())
    assertFalse("daemon DeviceDimensions catalog should not be empty", daemon.isEmpty())
    assertEquals(
      "DeviceDimensions ids drifted between plugin and daemon copies",
      plugin.keys,
      daemon.keys,
    )
    assertEquals(
      "DeviceDimensions geometry drifted between plugin and daemon copies",
      plugin,
      daemon,
    )
  }

  @Test
  fun daemonSpecParserReadsTheSameTermsAsGradlePlugin() {
    // The catalog check above only covers the device *map*. The `spec:` parser is duplicated too,
    // and it drifted: the daemon learned `parent=` / a symmetric `orientation=` while the plugin
    // copy kept resolving a rotated tablet as a 400×800 default, or vice versa. Comparing the set
    // of terms each parser actually reads catches exactly that, without pinning formatting.
    val pluginRoot = findPluginRepoRoot()
    assumeTrue(
      "no compose-ai-tools checkout found (set COMPOSE_AI_TOOLS_ROOT) — skipping cross-repo drift check",
      pluginRoot != null,
    )
    val plugin =
      specTerms(
        pluginRoot!!.resolve(
          "gradle-plugin/preview-discovery/src/main/kotlin/ee/schimke/composeai/discovery/DeviceDimensions.kt"
        )
      )
    val daemon = specTerms(daemonCopy())

    assertFalse("plugin spec: parser should read some terms", plugin.isEmpty())
    assertEquals("spec: grammar drifted between plugin and daemon copies", plugin, daemon)
  }

  @Test
  fun readCatalogIgnoresCommentedEntries() {
    val temp = Files.createTempFile("device-dimensions-catalog", ".kt")
    try {
      Files.writeString(
        temp,
        """
        val KNOWN_DEVICES = mapOf(
          // "commented" to DeviceSpec(1, 2, 3.0f),
          "active" to DeviceSpec(4, 5, 6.0f),
        )
        """
          .trimIndent(),
      )

      assertEquals(mapOf("active" to DeviceEntry(4, 5, 6.0f)), readCatalog(temp))
    } finally {
      Files.deleteIfExists(temp)
    }
  }

  private fun readCatalog(path: Path): Map<String, DeviceEntry> {
    val text = Files.readString(path)
    return entryRegex.findAll(text).associate { match ->
      val (id, width, height, density) = match.destructured
      id to DeviceEntry(width.toInt(), height.toInt(), density.toFloat())
    }
  }

  /** Every `params["…"]` term the file's `spec:` parser consults, comments excluded. */
  private fun specTerms(path: Path): Set<String> {
    val text = Files.readString(path).lineSequence().filterNot { it.trim().startsWith("//") }
    return termRegex.findAll(text.joinToString("\n")).map { it.groupValues[1] }.toSet()
  }

  /**
   * Locate a `yschimke/compose-ai-tools` checkout, which is where the *other* copy of
   * `DeviceDimensions` lives.
   *
   * These two tests compare a duplicated catalog and a duplicated `spec:` parser. Both copies used
   * to be in one repository, so finding the second was a walk up the tree. Since this module was
   * extracted the plugin's copy is in another repository, and the check is only as good as its
   * ability to see it.
   *
   * Rather than delete the check (the drift it catches is real — the daemon once learned `parent=`
   * and a symmetric `orientation=` while the plugin copy kept resolving a rotated tablet as a
   * 400x800 default), it looks for that checkout in order:
   *
   * 1. `COMPOSE_AI_TOOLS_ROOT`, set explicitly — this is what CI uses.
   * 2. A sibling `../compose-ai-tools` directory, the usual local layout.
   * 3. Walking up from here, which still works if this module is ever vendored back.
   *
   * When none is present the test SKIPS rather than fails, because "you do not have the other
   * repository checked out" is not a drift signal. CI does have it, so the check still gates.
   */
  private fun findPluginRepoRoot(): Path? {
    val marker =
      "gradle-plugin/preview-discovery/src/main/kotlin/ee/schimke/composeai/discovery/DeviceDimensions.kt"

    System.getenv("COMPOSE_AI_TOOLS_ROOT")
      ?.takeIf { it.isNotBlank() }
      ?.let {
        val explicit = Path.of(it).toAbsolutePath()
        check(Files.exists(explicit.resolve(marker))) {
          "COMPOSE_AI_TOOLS_ROOT=$explicit does not contain $marker"
        }
        return explicit
      }

    val sibling = Path.of("").toAbsolutePath().parent?.resolve("compose-ai-tools")
    if (sibling != null && Files.exists(sibling.resolve(marker))) return sibling

    var current: Path? = Path.of("").toAbsolutePath()
    while (current != null) {
      if (Files.exists(current.resolve(marker))) return current
      current = current.parent
    }
    return null
  }

  private fun daemonCopy(): Path =
    Path.of("").toAbsolutePath().let { cwd ->
      // Gradle runs tests with the project dir as the working directory.
      val local =
        cwd.resolve("src/main/kotlin/ee/schimke/composeai/daemon/devices/DeviceDimensions.kt")
      check(Files.exists(local)) { "could not find this module's DeviceDimensions.kt at $local" }
      local
    }

  private data class DeviceEntry(val widthDp: Int, val heightDp: Int, val density: Float)

  companion object {
    private val entryRegex =
      Regex("(?m)^\\s*\"([^\"]+)\"\\s+to\\s+DeviceSpec\\((\\d+),\\s*(\\d+),\\s*([0-9.]+)f\\)")

    private val termRegex = Regex("""params\[\s*"([^"]+)"\s*\]""")
  }
}
