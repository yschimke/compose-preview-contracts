package ee.schimke.composeai.buildartifacts.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * One configured target's build outputs, as a non-Gradle build integration reports them.
 *
 * A Bazel aspect, a Blaze wrapper or any other producer writes this file; the compose-preview CLI
 * reads it to discover previews and launch a renderer without running Gradle. Every path is
 * relative to the build's execution root unless it is absolute.
 *
 * [schemaVersion] is [SCHEMA_VERSION_JVM] for a JVM/Desktop target and [SCHEMA_VERSION_ANDROID]
 * when [android] is present, so a reader that predates the Android block refuses the file instead
 * of rendering an Android target as a desktop one.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class BuildArtifacts
internal constructor(
  public val schemaVersion: Int,
  /** The target's label in the producing build system, e.g. `//app:ui`. */
  public val target: String,
  /** Identity of the build configuration the outputs came from; never mixes configurations. */
  public val configuration: String,
  /** The target's own compiled jars; previews are discovered only in these. */
  public val projectJars: List<String>,
  /** The complete runtime classpath, in order, including every [projectJars] entry. */
  public val runtimeJars: List<String>,
  /** Kotlin/Java sources of the target, for source locations in the preview manifest. */
  public val sourceFiles: List<String> = emptyList(),
  /** Runtime data files (runfiles) that are not jars. */
  public val dataFiles: List<String> = emptyList(),
  /** Android resources; present only for an Android target. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val android: AndroidBuildArtifacts? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` for why the constructor is
   * internal.
   */
  public class Builder(
    target: String,
    configuration: String,
    projectJars: List<String>,
    runtimeJars: List<String>,
  ) {
    public var target: String = target
    public var configuration: String = configuration
    public var projectJars: List<String> = projectJars
    public var runtimeJars: List<String> = runtimeJars
    public var sourceFiles: List<String> = emptyList()
    public var dataFiles: List<String> = emptyList()
    public var android: AndroidBuildArtifacts? = null

    /** [schemaVersion] follows from whether [android] is set. */
    public fun build(): BuildArtifacts =
      BuildArtifacts(
        if (android == null) SCHEMA_VERSION_JVM else SCHEMA_VERSION_ANDROID,
        target,
        configuration,
        projectJars,
        runtimeJars,
        sourceFiles,
        dataFiles,
        android,
      )
  }

  /** These artifacts as a [Builder], for deriving modified ones. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(target, configuration, projectJars, runtimeJars).also {
      it.sourceFiles = sourceFiles
      it.dataFiles = dataFiles
      it.android = android
    }

  public companion object {
    /** A JVM/Desktop target: no [android] block. */
    public const val SCHEMA_VERSION_JVM: Int = 1

    /** An Android target: [android] is present. */
    public const val SCHEMA_VERSION_ANDROID: Int = 2
  }
}

/**
 * The linked Android resources a Robolectric render needs, equivalent to what an
 * `android_local_test` or AGP unit test hands Robolectric through `test_config.properties`.
 *
 * Resource IDs in [rJar] must be the final IDs assigned when [resourceApk] was linked; a library's
 * own non-final R classes do not match a linked APK.
 */
@Serializable
@ConsistentCopyVisibility
public data class AndroidBuildArtifacts
internal constructor(
  /** The package of the generated R class (`android_custom_package`). */
  public val packageName: String,
  /** A standalone linked resource APK (`resources.ap_`), assets included. */
  public val resourceApk: String,
  /** The merged `AndroidManifest.xml` the APK was linked against. */
  public val manifest: String,
  /** R classes with final IDs for [resourceApk]; null when they are already in `runtimeJars`. */
  public val rJar: String? = null,
  /**
   * The compile SDK level, used to choose Robolectric's runtime; null selects the renderer default.
   */
  public val compileSdk: Int? = null,
) {
  /** Additive construction API, as [BuildArtifacts.Builder]. */
  public class Builder(packageName: String, resourceApk: String, manifest: String) {
    public var packageName: String = packageName
    public var resourceApk: String = resourceApk
    public var manifest: String = manifest
    public var rJar: String? = null
    public var compileSdk: Int? = null

    public fun build(): AndroidBuildArtifacts =
      AndroidBuildArtifacts(packageName, resourceApk, manifest, rJar, compileSdk)
  }

  /** This block as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(packageName, resourceApk, manifest).also {
      it.rJar = rJar
      it.compileSdk = compileSdk
    }
}
