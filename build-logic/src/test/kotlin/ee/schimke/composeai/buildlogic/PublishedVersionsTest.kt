package ee.schimke.composeai.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The version rule a reduced release turns on, and the manifest shape it reads.
 *
 * Every module's `project.version` and every one of the BOM's constraints goes through
 * [PublishedVersions.resolve]. A POM names its project dependencies at their `project.version`, so
 * getting this wrong does not fail the release — it publishes a POM naming a sibling version that
 * was never uploaded, which a consumer discovers at resolution time and which cannot be withdrawn.
 */
class PublishedVersionsTest {

  private val manifest =
    """
    {
      "modules": {
        "daemon-protocol": "3.0.0",
        "common-io": "2.9.1"
      }
    }
    """

  @Test
  fun `a module in the publish set takes the tag`() {
    assertEquals(
      "3.1.0",
      PublishedVersions.resolve("daemon-protocol", "3.1.0", setOf("daemon-protocol"), manifest),
    )
  }

  @Test
  fun `a module outside the publish set keeps the version it last published at`() {
    assertEquals("2.9.1", PublishedVersions.resolve("common-io", "3.1.0", setOf("x"), manifest))
  }

  @Test
  fun `no publish set at all means every module takes the tag`() {
    // The `workflow_dispatch` recovery path, where no plan ran.
    assertEquals("3.1.0", PublishedVersions.resolve("common-io", "3.1.0", null, manifest))
  }

  @Test
  fun `an empty publish set is a real answer, not a missing one`() {
    // A releasable change confined to `.github/` publishes no module. Collapsing this into the
    // case above would upload every coordinate on exactly the releases that need none of them.
    assertEquals("2.9.1", PublishedVersions.resolve("common-io", "3.1.0", emptySet(), manifest))
  }

  @Test
  fun `a skipped module with no recorded version fails rather than taking the tag`() {
    // Stamping the tag onto a module that is not being uploaded is what publishes a POM naming a
    // coordinate that does not exist, so this is an error and not a fallback.
    val failure =
      assertFailsWith<IllegalStateException> {
        PublishedVersions.resolve("newcomer", "3.1.0", emptySet(), manifest)
      }
    assertEquals(true, failure.message!!.contains("newcomer"))
  }

  @Test
  fun `the publish set property parses, and absent is not empty`() {
    assertNull(PublishedVersions.parsePublishSet(null))
    assertEquals(emptySet(), PublishedVersions.parsePublishSet(""))
    assertEquals(
      setOf("a", "b"),
      PublishedVersions.parsePublishSet(" a , b ,, "),
    )
  }

  @Test
  fun `the committed manifest is the shape the regex reads`() {
    // The reader is a regex because build-logic carries no JSON dependency. What keeps that honest
    // is this: the real file, read the real way, for every module the real build publishes.
    val repoRoot =
      generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { it.resolve("settings.gradle.kts").isFile && it.resolve("bom").isDirectory }
    val text = repoRoot.resolve("publishing-manifest.json").readText()
    val ids =
      Regex("\"([a-z0-9-]+)\"\\s*:\\s*\"\\d+\\.\\d+\\.\\d+\"").findAll(text).map {
        it.groupValues[1]
      }
    val found = ids.associateWith { PublishedVersions.recordedVersion(it, text) }
    assertEquals(12, found.size, "every published module needs a recorded version")
    assertEquals(emptyList(), found.filterValues { it == null }.keys.toList())
  }
}
