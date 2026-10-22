package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire shape of `build/compose-previews/daemon-launch.json` — how a client starts a preview
 * daemon JVM. Both sides of the file: the Gradle plugin's `daemon-launch-builder` (and its
 * non-Gradle equivalents for Bazel and Amper) constructs and encodes it, and `:daemon-client`'s
 * supervisor, `:render-session-subprocess` and `compose-preview serve` [parse] it.
 *
 * Through 3.27.x this was only the reader: the writer declared its own `DaemonClasspathDescriptor`
 * in compose-ai-tools, and the two drifted (the writer's [btaCompile] was invisible here, and the
 * reader's [jailCommand] / [hardTtlSeconds] there). [btaCompile] closes the last gap, so the writer
 * can construct this type and the shape is declared once (#3824, preparation item 2). What stays
 * upstream is behaviour — resolving a classpath, choosing JVM args, and the canonical
 * pretty-printed encoder the plugin writes through.
 *
 * Lives in `:daemon-protocol` rather than an MCP or plugin module because everything that launches
 * a daemon needs it, and a consumer's runtime classpath must stay free of the plugin's AGP/Gradle
 * dependencies.
 *
 * **Schema versioning.** [schemaVersion] is [SCHEMA_VERSION] for every descriptor a current writer
 * produces. Bump it whenever the field shape changes in a way an older reader could misread; the VS
 * Code extension refuses a descriptor whose version it does not know.
 *
 * **Stable field ordering.** Collection fields are `List` (never `Set`): classpath order is
 * load-bearing for the Robolectric sandbox, and JVM arg order matters for some `--add-opens` / `-D`
 * precedence cases. kotlinx-serialization encodes [systemProperties] in the map's iteration order,
 * so a writer that wants a stable file hands the [Builder] a `LinkedHashMap`.
 */
@Serializable
@ConsistentCopyVisibility
public data class DaemonLaunchDescriptor
internal constructor(
  val schemaVersion: Int,
  val modulePath: String,
  val variant: String,
  val enabled: Boolean,
  val mainClass: String,
  val javaLauncher: String? = null,
  val classpath: List<String>,
  val jvmArgs: List<String>,
  val systemProperties: Map<String, String>,
  val workingDirectory: String,
  val manifestPath: String,
  /**
   * Optional argv **prefix** the daemon JVM launches behind — an OS jail (`bwrap`, `unshare`,
   * `systemd-run --scope`, …). Empty (the default, and what the gradle plugin writes) launches the
   * JVM directly, exactly as before.
   *
   * This is how the playground's per-session sandbox reaches the live lane: serve writes the jail
   * into the snippet's own `daemon-launch.json`, so the descriptor→spawn path applies it without
   * every intermediate layer having to thread a sandbox object through (`docs/design/PLAYGROUND.md`
   * §6). Distinct from [withSandboxCount], which sizes Robolectric's *in-JVM* sandbox pool and has
   * nothing to do with containment.
   */
  val jailCommand: List<String> = emptyList(),
  /**
   * Optional hard wall-clock lifetime, in seconds, for the spawned JVM. When set, the spawner arms
   * a watchdog that force-kills the process at the deadline — the "killed after a hard wall-clock
   * TTL" requirement for a playground session, enforced by the parent rather than by the child's
   * cooperation. Null (the default) means no watchdog: an ordinary project daemon lives as long as
   * its owner keeps it.
   */
  val hardTtlSeconds: Long? = null,
  /**
   * Stage-2 in-process compile config, written by the Gradle plugin when the variant wiring
   * resolved the Build Tools API inputs. `null` means the consumer has not opted in, and the
   * daemon's `compileSources` answers `result=fallback` for every call. The daemon itself consumes
   * this through flattened `composeai.daemon.bta*` system properties; the block is here for clients
   * (the VS Code extension reads it to decide whether `compileSources` is worth a round-trip) and
   * so the writer and the readers declare one shape.
   *
   * Schema version 2 is the version that introduced it. Declared last so the existing `componentN`
   * accessors keep their signatures.
   */
  val btaCompile: BtaCompileConfig? = null,
  /**
   * Where the module's sources, resources and build files are, as its build declares them; see
   * [SourceWatch]. `null` when the writer doesn't know, and a reader falls back to its own
   * conventions. Optional and ignored by older readers, so it needs no [SCHEMA_VERSION] bump.
   */
  val sourceWatch: SourceWatch? = null,
) {
  /**
   * Builds a [DaemonLaunchDescriptor].
   *
   * The construction API: [DaemonLaunchDescriptor]'s constructor is `internal`, and
   * `@ConsistentCopyVisibility` makes the generated `copy` internal with it, so neither is public
   * ABI. That matters because this type crosses a repository boundary as a compiled artifact:
   * adding a property to a data class REMOVES the old `<init>` and `copy$default` signatures, and a
   * consumer compiled against the previous release dies at its own call site with
   * `NoSuchMethodError`. Neither signature is reachable now, so neither can break.
   *
   * The rule that keeps that true: **a new property is always optional**, so it only ever adds a
   * setter here and never a parameter to this constructor.
   */
  public class Builder(
    schemaVersion: Int,
    modulePath: String,
    variant: String,
    enabled: Boolean,
    mainClass: String,
    classpath: List<String>,
    jvmArgs: List<String>,
    systemProperties: Map<String, String>,
    workingDirectory: String,
    manifestPath: String,
  ) {
    public var schemaVersion: Int = schemaVersion
    public var modulePath: String = modulePath
    public var variant: String = variant
    public var enabled: Boolean = enabled
    public var mainClass: String = mainClass
    public var classpath: List<String> = classpath
    public var jvmArgs: List<String> = jvmArgs
    public var systemProperties: Map<String, String> = systemProperties
    public var workingDirectory: String = workingDirectory
    public var manifestPath: String = manifestPath
    public var javaLauncher: String? = null
    public var jailCommand: List<String> = emptyList()
    public var hardTtlSeconds: Long? = null
    public var btaCompile: BtaCompileConfig? = null
    public var sourceWatch: SourceWatch? = null

    public fun build(): DaemonLaunchDescriptor =
      DaemonLaunchDescriptor(
        schemaVersion,
        modulePath,
        variant,
        enabled,
        mainClass,
        javaLauncher,
        classpath,
        jvmArgs,
        systemProperties,
        workingDirectory,
        manifestPath,
        jailCommand,
        hardTtlSeconds,
        btaCompile,
        sourceWatch,
      )
  }

  /** This [DaemonLaunchDescriptor] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(
        schemaVersion,
        modulePath,
        variant,
        enabled,
        mainClass,
        classpath,
        jvmArgs,
        systemProperties,
        workingDirectory,
        manifestPath,
      )
      .also {
        it.javaLauncher = javaLauncher
        it.jailCommand = jailCommand
        it.hardTtlSeconds = hardTtlSeconds
        it.btaCompile = btaCompile
        it.sourceWatch = sourceWatch
      }

  /** Returns a copy launched behind [command] and force-killed after [hardTtlSeconds]. */
  public fun jailed(command: List<String>, hardTtlSeconds: Long?): DaemonLaunchDescriptor =
    copy(jailCommand = command, hardTtlSeconds = hardTtlSeconds)

  /**
   * SANDBOX-POOL.md — returns a copy with `composeai.daemon.sandboxCount` merged into
   * [systemProperties]. The supervisor calls this on the descriptor read from disk before passing
   * it to [DaemonClientFactory.spawn] so the daemon JVM picks up the right pool size at boot.
   *
   * Idempotent at [count] = 1 (the daemon's default; the sysprop is omitted to keep the disk
   * descriptor trivially diffable across replicas-per-daemon settings of 0).
   */
  public fun withSandboxCount(count: Int): DaemonLaunchDescriptor {
    require(count >= 1) { "sandboxCount must be >= 1, got $count" }
    if (count == 1) return this
    val merged = systemProperties.toMutableMap()
    merged[SANDBOX_COUNT_PROP] = count.toString()
    return copy(systemProperties = merged)
  }

  public companion object {
    /**
     * Sysprop key the daemon reads to configure
     * [`RobolectricHost.sandboxCount`][ee.schimke.composeai.daemon.RobolectricHost.sandboxCount].
     * Mirrored on the daemon side as private consts in `DaemonMain.kt` and `SandboxProcessPool.kt`;
     * all three MUST agree, or every daemon silently gets a pool of one. Enforced by
     * `checkDaemonLaunchSchema`.
     */
    public const val SANDBOX_COUNT_PROP: String = "composeai.daemon.sandboxCount"

    /**
     * The [schemaVersion] a current writer stamps. Declared with the shape it versions, so a writer
     * reads it rather than re-declaring the number.
     *
     * Version history:
     * - **1** — initial schema.
     * - **2** — added the optional [btaCompile] block. The v1 VS Code reader failed on any unknown
     *   field, so even a null-defaulted addition was breaking for it.
     */
    public const val SCHEMA_VERSION: Int = 2

    private val json = Json { ignoreUnknownKeys = true }

    public fun parse(jsonText: String): DaemonLaunchDescriptor =
      json.decodeFromString(serializer(), jsonText)
  }
}
