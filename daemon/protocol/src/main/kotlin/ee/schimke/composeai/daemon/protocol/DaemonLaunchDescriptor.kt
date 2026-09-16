package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Trimmed parse of `build/compose-previews/daemon-launch.json` — how a client starts a preview
 * daemon JVM. Mirrors the field set written by
 * [`DaemonClasspathDescriptor`][ee.schimke.composeai.plugin.daemon.DaemonClasspathDescriptor] in
 * the gradle plugin; the schema is re-declared rather than depended on so a consumer's runtime
 * classpath stays free of the plugin's AGP/Gradle deps.
 *
 * Lives in `:daemon:core` — the published `daemon-core` contract — rather than in `:mcp`, where it
 * used to sit. Everything that launches a daemon needs it (`:mcp`'s supervisor,
 * `:render-session-subprocess`, `compose-preview serve`'s bundle daemon), and reading it out of the
 * MCP server module made every one of those a consumer of the MCP server. That is the coupling
 * issue #3824 has to remove before `serve` can be extracted: the preview server is a protocol
 * client, and its dependency floor is `daemon-core` + the payload schemas, not an MCP server.
 *
 * Still two representations of one schema — this and the gradle plugin's published
 * `daemon-launch-builder` writer, which have drifted. Consolidating them into a single published
 * contract is the next step (#3824, preparation item 2); this move puts the reader on the right
 * side of the boundary first.
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

    private val json = Json { ignoreUnknownKeys = true }

    public fun parse(jsonText: String): DaemonLaunchDescriptor =
      json.decodeFromString(serializer(), jsonText)
  }
}
