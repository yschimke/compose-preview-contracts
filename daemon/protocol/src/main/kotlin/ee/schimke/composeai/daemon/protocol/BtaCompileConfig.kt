package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.Serializable

/**
 * Stage-2 in-process compile config: the [DaemonLaunchDescriptor.btaCompile] block of
 * `daemon-launch.json`. The Gradle plugin writes it whenever the variant wiring resolved the inputs
 * (Build Tools API implementation classpath, module name, output directory, incremental compilation
 * directory). The daemon builds a `DefaultBtaCompileService` from the same values at startup but
 * only loads the Build Tools API classloader once a client calls `compileSources`, so a non-null
 * block costs nothing unless the editor opts in.
 *
 * A wire type that can gain a field, so it is built through [Builder]: the constructor is
 * `internal` and `@ConsistentCopyVisibility` keeps `copy` internal with it, so a new optional
 * property adds a setter rather than changing a public signature.
 */
@Serializable
@ConsistentCopyVisibility
public data class BtaCompileConfig
internal constructor(
  /**
   * Build Tools API implementation classpath: `kotlin-build-tools-impl` with the matching
   * `kotlin-compiler-embeddable`, `kotlin-daemon-embeddable`,
   * `kotlin-compose-compiler-plugin-embeddable` and their runtime jars. Loaded into an isolated
   * classloader the daemon's main classloader never sees. Must match the consumer's Kotlin version.
   */
  val implClasspath: List<String>,
  /** The module's compile classpath — the jar list `compileKotlin` would see. */
  val compileClasspath: List<String>,
  /** Compiler plugin jars, e.g. the Compose compiler plugin. Empty when Compose is not applied. */
  val compilerPlugins: List<String>,
  /**
   * Where compiled `.class` files are written: the directory the daemon's child classloader
   * watches.
   */
  val outputDir: String,
  /**
   * The Kotlin `MODULE_NAME` argument. Matches the build's own module name so emitted
   * `kotlin.Metadata` agrees with the build's output.
   */
  val moduleName: String,
  /** Persistent incremental-compilation cache directory for the module. */
  val icWorkingDir: String,
  /**
   * Non-null when the module is not a stage-2 candidate (typically because KSP, KAPT or an
   * annotation processor is applied). The daemon's `compileSources` answers `result=fallback` with
   * this reason verbatim. `null` means eligible.
   */
  val ineligibilityReason: String? = null,
) {
  /** Builds a [BtaCompileConfig]; see the class KDoc for why the constructor is internal. */
  public class Builder(
    implClasspath: List<String>,
    compileClasspath: List<String>,
    compilerPlugins: List<String>,
    outputDir: String,
    moduleName: String,
    icWorkingDir: String,
  ) {
    public var implClasspath: List<String> = implClasspath
    public var compileClasspath: List<String> = compileClasspath
    public var compilerPlugins: List<String> = compilerPlugins
    public var outputDir: String = outputDir
    public var moduleName: String = moduleName
    public var icWorkingDir: String = icWorkingDir
    public var ineligibilityReason: String? = null

    public fun build(): BtaCompileConfig =
      BtaCompileConfig(
        implClasspath,
        compileClasspath,
        compilerPlugins,
        outputDir,
        moduleName,
        icWorkingDir,
        ineligibilityReason,
      )
  }

  /** This [BtaCompileConfig] as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(implClasspath, compileClasspath, compilerPlugins, outputDir, moduleName, icWorkingDir)
      .also { it.ineligibilityReason = ineligibilityReason }
}
