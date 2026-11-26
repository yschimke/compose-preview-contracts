package ee.schimke.composeai.io

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmClasspathArgTest {

  @Test
  fun `emits an @argfile token backed by a -classpath file`() {
    val cp = listOf("/a/b.jar", "/c/d.jar")
    val token = classpathArgFile(cp)

    assertTrue(token.startsWith("@"), "expected an @argfile token, got: $token")
    val file = File(token.removePrefix("@"))
    assertTrue(file.isFile, "argfile should exist on disk")
    assertTrue(
      file.name.startsWith("composeai-cp-p${ProcessHandle.current().pid()}-"),
      "argfile should be tagged with the current PID, got: ${file.name}",
    )
    assertEquals("-classpath \"${cp.joinToString(File.pathSeparator)}\"\n", file.readText())
  }

  @Test
  fun `command length stays bounded no matter how large the classpath is`() {
    // The whole point: a full app's render classpath (hundreds of long jar paths) must NOT land on
    // the command line, where it overflows ARG_MAX (execve E2BIG). The @argfile token is a short,
    // fixed-shape reference regardless of classpath size.
    val huge = (1..5_000).map { "/nix/store/${"x".repeat(40)}/lib/artifact-$it.jar" }
    val joinedBytes = huge.joinToString(File.pathSeparator).length
    val token = classpathArgFile(huge)

    assertTrue(joinedBytes > 200_000, "sanity: the raw classpath really is huge ($joinedBytes B)")
    assertTrue(
      token.length < 512,
      "the @argfile token must stay short (was ${token.length} B) so argv can't overflow",
    )
    // …and the file still round-trips the full classpath.
    val body = File(token.removePrefix("@")).readText()
    assertTrue(body.contains("artifact-5000.jar"), "argfile should carry every entry")
  }

  @Test
  fun `quotes and escapes spaces, backslashes and quotes for the argfile grammar`() {
    // Wrapped in quotes; backslashes doubled (argfile un-escapes \\ -> \); embedded quote escaped.
    assertEquals("\"/plain/path.jar\"", quoteForArgFile("/plain/path.jar"))
    assertEquals("\"/has a space/x.jar\"", quoteForArgFile("/has a space/x.jar"))
    assertEquals("\"C:\\\\Program Files\\\\a.jar\"", quoteForArgFile("C:\\Program Files\\a.jar"))
    assertEquals("\"weird\\\"name.jar\"", quoteForArgFile("weird\"name.jar"))
  }

  @Test
  fun `writes into a caller-supplied directory, for a spawn that cannot see the parent's tmp`() {
    // A sandboxed playground daemon runs behind a jail with its own /tmp (bwrap --tmpfs /tmp), so
    // an argfile in the parent's temp dir would be invisible and the launcher would fail to read
    // its own classpath. The caller passes the one directory both sides can see.
    val dir = java.nio.file.Files.createTempDirectory("argfile-dir").toFile()
    try {
      val token = classpathArgFile(listOf("/a/b.jar"), dir)

      val file = File(token.removePrefix("@"))
      assertEquals(dir.canonicalFile, file.parentFile.canonicalFile)
      assertTrue(file.readText().contains("/a/b.jar"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun `sweeps stale argfiles for dead PIDs while preserving live, self, legacy and unrelated files`() {
    val dir = java.nio.file.Files.createTempDirectory("argfile-sweep").toFile()
    try {
      val selfPid = ProcessHandle.current().pid()
      val deadPid = selfPid + 100_000L
      val liveOtherPid = selfPid + 100_001L

      val deadFile =
        File(dir, "composeai-cp-p$deadPid-111.args").apply { writeText("-classpath a") }
      val liveOtherFile =
        File(dir, "composeai-cp-p$liveOtherPid-222.args").apply { writeText("-classpath b") }
      val selfFile =
        File(dir, "composeai-cp-p$selfPid-333.args").apply { writeText("-classpath c") }
      val legacyFile = File(dir, "composeai-cp123456.args").apply { writeText("-classpath d") }
      val unrelatedFile = File(dir, "other.args").apply { writeText("keep") }

      val removed =
        sweepStaleClasspathArgFiles(directory = dir, isAlive = { pid -> pid == liveOtherPid })

      assertEquals(1, removed)
      assertFalse(deadFile.exists(), "dead-PID argfile should be deleted")
      assertTrue(liveOtherFile.exists(), "live-PID argfile must be preserved")
      assertTrue(selfFile.exists(), "current-PID argfile must be preserved")
      assertTrue(legacyFile.exists(), "untagged legacy argfile must be preserved")
      assertTrue(unrelatedFile.exists(), "unrelated file must be preserved")
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun `classpathArgFile automatically reclaims stale dead-PID argfiles in its directory`() {
    val dir = java.nio.file.Files.createTempDirectory("argfile-auto-sweep").toFile()
    try {
      // Spawn a short-lived process and wait for it to exit so its PID is genuinely dead.
      val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
      val exited = ProcessBuilder(javaBin, "-version").redirectErrorStream(true).start()
      val deadPid = exited.pid()
      exited.inputStream.use { it.readBytes() }
      exited.waitFor()

      val orphan =
        File(dir, "composeai-cp-p$deadPid-999.args").apply { writeText("-classpath old") }
      assertTrue(orphan.isFile)

      val token = classpathArgFile(listOf("/fresh.jar"), dir)
      val fresh = File(token.removePrefix("@"))

      assertFalse(orphan.exists(), "orphaned argfile from exited process should be swept")
      assertTrue(fresh.isFile, "newly written argfile should exist")
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun `rejects an empty classpath`() {
    assertFailsWith<IllegalArgumentException> { classpathArgFile(emptyList()) }
  }
}
