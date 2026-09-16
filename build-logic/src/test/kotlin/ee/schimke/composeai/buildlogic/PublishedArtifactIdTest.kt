package ee.schimke.composeai.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The assumption `:bom` derives its constraints from, pinned against the real build files.
 *
 * The BOM names every coordinate this repository publishes, taking the project paths from
 * `settings.gradle.kts` and flattening each into an artifact id. That is the kind of assumption
 * that stops being true silently: a module whose artifact id no longer matches its project path
 * would simply go missing from the BOM, and a BOM that omits a coordinate is worse than no BOM —
 * a consumer trusting it gets no version for that module and a resolution failure with an empty
 * version.
 */
class PublishedArtifactIdTest {

  @Test
  fun `every published module's artifact id is its project path flattened`() {
    val repoRoot =
      generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { it.resolve("settings.gradle.kts").isFile && it.resolve("bom").isDirectory }

    val settings = repoRoot.resolve("settings.gradle.kts").readText()
    val includes = Regex("""include\("(:[^"]+)"\)""").findAll(settings).map { it.groupValues[1] }
    val overrides =
      Regex("""project\("(:[^"]+)"\)\.projectDir\s*=\s*file\("([^"]+)"\)""")
        .findAll(settings)
        .associate { it.groupValues[1] to it.groupValues[2] }

    val mismatches = mutableListOf<String>()
    var published = 0
    for (path in includes) {
      val dir = overrides[path] ?: path.removePrefix(":").replace(':', '/')
      val buildFile = repoRoot.resolve(dir).resolve("build.gradle.kts")
      if (!buildFile.isFile) continue
      val text = buildFile.readText()
      // Matched with its closing quote, exactly as `settings.gradle.kts` does: the platform plugin
      // id shares this one's first 26 characters.
      if (!text.contains("""composeai.maven-publishing")""")) continue
      published++
      val declared =
        Regex("""artifactId\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1) ?: continue
      val derived = path.removePrefix(":").replace(':', '-')
      if (declared != derived) mismatches += "$path declares $declared, BOM would name $derived"
    }

    assertEquals(emptyList(), mismatches, "artifact id no longer follows the project path")
    // A guard on the guard: if the walk stops finding modules, the check above passes vacuously.
    assertEquals(12, published, "published module count changed; check the BOM still covers them")
  }
}
