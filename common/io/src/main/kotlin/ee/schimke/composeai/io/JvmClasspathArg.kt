package ee.schimke.composeai.io

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * The charset the `java` launcher uses to decode an `@argfile` — the platform **native** encoding
 * (`sun.jnu.encoding`, the same charset filenames use), NOT necessarily UTF-8. Writing the argfile
 * in UTF-8 would corrupt a non-ASCII classpath entry (e.g. a Gradle cache under `C:\Users\José`) on
 * a non-UTF-8 locale, leaving the spawned daemon unable to resolve its classes. Fall back to the
 * JVM default only if the property is somehow unset/unknown.
 */
private val ARGFILE_CHARSET: Charset = runCatching {
  Charset.forName(System.getProperty("sun.jnu.encoding"))
}
  .getOrDefault(Charset.defaultCharset())

private val PID_TAGGED_ARGFILE: Regex = Regex("""^composeai-cp-p(\d+)-.*\.args$""")

/**
 * Build the `-classpath` portion of a `java …` command in a form that cannot overflow the OS
 * argument limit (`execve` `E2BIG` — "Argument list too long").
 *
 * A large module's render classpath — a full app's *hundreds* of jars — blows past `ARG_MAX` (and
 * the per-argument `MAX_ARG_STRLEN`) when passed as a literal `-cp <jar:jar:…>` argument. The spawn
 * then fails with `error=7`, which silently drops the daemon subprocess: e.g. the
 * `--with-semantics` layout-inspector capture never starts, so the catalog renders without
 * wireframe / figma SVGs. Small catalog/sample modules stay under the limit, so the failure stays
 * invisible until a real app module hits it.
 *
 * The fix is a Java **@argfile**: write `-classpath "<cp>"` to a file and pass the single token
 * `@<file>` on the command line. The launcher reads the classpath from the file, so `argv` stays
 * short no matter how big the classpath is. Argfiles are honoured by every `java` launcher since
 * JDK 9.
 *
 * Each argfile is tagged with the current process's PID (`composeai-cp-p<pid>-*.args`), registered
 * for deletion on normal JVM exit, and preceded by a best-effort [sweepStaleClasspathArgFiles] in
 * the target directory so files orphaned by `SIGKILL` or crashes are reclaimed on the next launch
 * instead of accumulating in `java.io.tmpdir`.
 *
 * @param directory where to write the argfile; null (the default) uses the JVM temp dir. A
 *   **sandboxed** spawn passes the jailed process's own writable directory instead: a jail with its
 *   own `/tmp` (e.g. `bwrap --tmpfs /tmp`) cannot see the parent's temp dir, so an argfile written
 *   there would leave the launcher unable to read its own classpath.
 * @return the `@<file>` token to splice into the command **in place of** `-cp`/`-classpath` + the
 *   joined classpath. The file is registered for deletion on exit.
 */
public fun classpathArgFile(classpath: List<String>, directory: File? = null): String {
  require(classpath.isNotEmpty()) { "classpath must not be empty" }
  runCatching { sweepStaleClasspathArgFiles(directory) }
  val joined = classpath.joinToString(File.pathSeparator)
  val pid = ProcessHandle.current().pid()
  val file = File.createTempFile("composeai-cp-p$pid-", ".args", directory)
  file.deleteOnExit()
  file.writeText("-classpath ${quoteForArgFile(joined)}\n", ARGFILE_CHARSET)
  return "@" + file.absolutePath
}

/**
 * Delete stale `composeai-cp-p<pid>-*.args` argfiles in [directory] (or the JVM temp directory when
 * `null`) whose creating process is no longer alive.
 *
 * Files belonging to live processes (including this JVM), symlinks, directories, and untagged
 * legacy `composeai-cp*.args` files are left untouched. Callers such as long-lived servers may
 * invoke this at startup to reclaim argfiles orphaned by killed sessions before any new subprocess
 * is spawned.
 *
 * @return the number of stale argfiles removed.
 */
public fun sweepStaleClasspathArgFiles(directory: File? = null): Int =
  sweepStaleClasspathArgFiles(directory, ::isProcessAlive)

internal fun sweepStaleClasspathArgFiles(
  directory: File? = null,
  isAlive: (Long) -> Boolean,
): Int {
  val dir = directory ?: File(System.getProperty("java.io.tmpdir"))
  if (!dir.isDirectory) return 0
  val selfPid = ProcessHandle.current().pid()
  var deleted = 0
  runCatching {
    Files.newDirectoryStream(dir.toPath(), "composeai-cp-p*.args").use { stream ->
      for (entry in stream) {
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue
        val name = entry.fileName?.toString() ?: continue
        val pid =
          PID_TAGGED_ARGFILE.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull() ?: continue
        if (pid == selfPid) continue
        if (!isAlive(pid) && runCatching { Files.deleteIfExists(entry) }.getOrDefault(false)) {
          deleted++
        }
      }
    }
  }
  return deleted
}

private fun isProcessAlive(pid: Long): Boolean = runCatching {
  ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}
  .getOrDefault(true)

/**
 * Quote a single token for a Java argfile. Wrap it in double quotes so embedded whitespace stays
 * one argument, and double every backslash so Windows paths survive the argfile's `\`-escape rule
 * (the launcher un-escapes `\\` → `\`, `\"` → `"`). On POSIX classpaths (no backslashes, rarely a
 * space) this is effectively just the surrounding quotes.
 */
internal fun quoteForArgFile(token: String): String =
  "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
