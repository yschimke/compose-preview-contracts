package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.Serializable

/**
 * The files a module's preview classes are built from, as its build declares them: where a tool
 * that notices edits without being told (the MCP server's source scan, an editor's file watcher)
 * should look. It is the build's full list and a starting point; a client that knows which files an
 * agent will edit may watch a subset first, and should keep the rest on a slower check rather than
 * drop it, since an edit there still changes what renders.
 *
 * Paths are absolute. Directories are watched recursively. Every field is optional, so a writer
 * that knows only some of them still publishes those.
 */
@Serializable
@ConsistentCopyVisibility
public data class SourceWatch
internal constructor(
  /** Directories of the module's Kotlin and Java sources, generated ones included. */
  val sourceDirs: List<String> = emptyList(),
  /** Directories of resources compiled into the previews: Android `res/`, Compose resources. */
  val resourceDirs: List<String> = emptyList(),
  /**
   * Files whose edits change the module's classpath or compile settings, such as build scripts and
   * version catalogs. An edit to one needs a new launch descriptor, not just a recompile.
   */
  val buildFiles: List<String> = emptyList(),
) {
  /**
   * Builds a [SourceWatch]; see [DaemonLaunchDescriptor.Builder] for why the constructor is
   * internal.
   */
  public class Builder {
    public var sourceDirs: List<String> = emptyList()
    public var resourceDirs: List<String> = emptyList()
    public var buildFiles: List<String> = emptyList()

    public fun build(): SourceWatch = SourceWatch(sourceDirs, resourceDirs, buildFiles)
  }

  /** This [SourceWatch] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.sourceDirs = sourceDirs
      it.resourceDirs = resourceDirs
      it.buildFiles = buildFiles
    }
}
