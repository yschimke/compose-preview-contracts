package ee.schimke.composeai.buildlogic

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import java.io.File
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.configure

abstract class ComposeAiMavenPublishingExtension
@Inject
constructor(objects: ObjectFactory) {
  val artifactId: Property<String> = objects.property(String::class.java)
  val displayName: Property<String> = objects.property(String::class.java)
  val description: Property<String> = objects.property(String::class.java)
  val inceptionYear: Property<String> = objects.property(String::class.java).convention("2026")

  fun coordinates(artifactId: String, displayName: String, description: String) {
    this.artifactId.set(artifactId)
    this.displayName.set(displayName)
    this.description.set(description)
  }
}

class ComposeAiMavenPublishingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("composeai.android-conventions")
    project.pluginManager.apply("composeai.jvm-conventions")
    project.pluginManager.apply("composeai.kotlin-conventions")
    project.pluginManager.apply("maven-publish")
    project.pluginManager.apply("com.vanniktech.maven.publish")

    val extension =
      project.extensions.create(
        "composeAiMavenPublishing",
        ComposeAiMavenPublishingExtension::class.java,
      )

    project.group = "ee.schimke.composeai"
    project.version =
      project.providers.environmentVariable("PLUGIN_VERSION").orNull
        ?: project.nextPatchSnapshotVersion()

    project.configureAndroidLibraryPublication()

    project.afterEvaluate {
      val artifactId =
        extension.artifactId.orNull ?: error("composeAiMavenPublishing.artifactId is required")
      val displayName =
        extension.displayName.orNull ?: error("composeAiMavenPublishing.displayName is required")
      val artifactDescription =
        extension.description.orNull ?: error("composeAiMavenPublishing.description is required")

      project.guardAgainstPublishingUpstreamCoordinates()

      project.extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        if (!project.version.toString().endsWith("SNAPSHOT")) {
          signAllPublications()
        }
        coordinates("ee.schimke.composeai", artifactId, project.version.toString())
        pom {
          name.set(displayName)
          description.set(artifactDescription)
          url.set("https://github.com/yschimke/compose-ai-tools")
          inceptionYear.set(extension.inceptionYear)
          licenses {
            license {
              name.set("The Apache License, Version 2.0")
              url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
              distribution.set("repo")
            }
          }
          developers {
            developer {
              id.set("yschimke")
              name.set("Yuri Schimke")
              url.set("https://github.com/yschimke")
            }
          }
          scm {
            url.set("https://github.com/yschimke/compose-ai-tools")
            connection.set("scm:git:https://github.com/yschimke/compose-ai-tools.git")
            developerConnection.set(
              "scm:git:ssh://git@github.com/yschimke/compose-ai-tools.git"
            )
          }
        }
      }
    }
  }
}

/**
 * Publish an Android library as its single `release` variant, with real sources and an empty
 * javadoc jar — Maven Central requires *a* javadoc artifact but not a useful one for a Kotlin
 * library whose docs live in the repo.
 *
 * This used to be copy-pasted into all 25 Android modules that publish, each carrying the same
 * three imports, the same `@file:Suppress("DEPRECATION")` header, and the same nine-line
 * `mavenPublishing { configure(...) }` block. Twenty-five copies of one decision is twenty-five
 * places to miss when the plugin's API moves — which the suppression comment itself predicted
 * ("the replacement types vary between plugin versions"). Now it moves here, once.
 *
 * `withPlugin` rather than an `afterEvaluate` check so the JVM modules that share this convention
 * plugin (65 of the 90) are untouched — vanniktech's own default handles them correctly.
 */
@Suppress("DEPRECATION") // AndroidSingleVariantLibrary(Boolean, Boolean); replacement types
// (SourcesJar / JavadocJar) vary between plugin versions. Re-visit when bumping.
private fun Project.configureAndroidLibraryPublication() {
  pluginManager.withPlugin("com.android.library") {
    extensions.configure<MavenPublishBaseExtension> {
      configure(
        AndroidSingleVariantLibrary(
          javadocJar = JavadocJar.Empty(),
          sourcesJar = SourcesJar.Sources(),
          variant = "release",
        )
      )
    }
  }
}

private fun Project.nextPatchSnapshotVersion(): String {
  val manifest =
    generateSequence(rootDir) { it.parentFile }
      .map { it.resolve(".release-please-manifest.json") }
      .firstOrNull(File::isFile)
      ?: error("Could not find .release-please-manifest.json from $rootDir")
  val current = Regex(""""\.":\s*"([^"]+)"""").find(manifest.readText())!!.groupValues[1]
  val (major, minor, patch) = current.split(".").map { it.toInt() }
  return "$major.$minor.${patch + 1}-SNAPSHOT"
}

/**
 * Refuse to publish to Maven Central while yschimke/compose-ai-tools still owns these coordinates.
 *
 * This repository and that one both publish `ee.schimke.composeai:daemon-protocol` and its
 * siblings, because this one was seeded as a COPY of those modules rather than as their new home
 * (compose-ai-tools#4732 took the narrow cut; the cutover, where that repository stops building
 * them and consumes these instead, has not happened). Two repositories cannot own one coordinate:
 * whichever publishes second either collides with an existing version or silently replaces what
 * the other shipped.
 *
 * The seeded version makes it sharper. `.release-please-manifest.json` starts at the upstream
 * release these modules were extracted from, so a release from here would target versions that
 * already exist on Central, published from there — and `publishToMavenCentral(automaticRelease =
 * true)` promotes without a human looking.
 *
 * `publishToMavenLocal` is untouched: it is how CI proves the POMs resolve, and it cannot reach
 * anyone else.
 *
 * At cutover, delete this guard rather than setting the flag — see docs/VERSIONING.md.
 */
private fun Project.guardAgainstPublishingUpstreamCoordinates() {
  val cutoverDone =
    providers.gradleProperty("composeai.contracts.cutover").orNull?.toBoolean() ?: false
  if (cutoverDone) return

  // Match by task name rather than by type: the publishing plugin registers a family of
  // `publish…ToMavenCentral…` tasks and a repository-scoped `publishAllPublicationsTo…`, and a
  // guard that knew only one of their names would leave the others open.
  tasks.configureEach {
    val central = name.contains("MavenCentral") || name.contains("SonatypeRepository")
    if (central && !name.contains("MavenLocal")) {
      doFirst {
        throw GradleException(
          buildString {
            appendLine("Refusing to run `$name`: yschimke/compose-ai-tools still publishes these coordinates.")
            appendLine()
            appendLine(
              "This repository holds a copy of those modules, not their new home — the cutover " +
                "in compose-ai-tools#4732 has not happened. Publishing from here would collide " +
                "with versions already on Maven Central, and automaticRelease promotes them " +
                "without review."
            )
            appendLine()
            appendLine("`publishToMavenLocal` still works and is what CI uses.")
            appendLine()
            append(
              "When the cutover lands, DELETE this guard (and the versioning decision it " +
                "enforces) rather than passing -Pcomposeai.contracts.cutover=true; the flag " +
                "exists so an intentional dry run is possible, not as a permanent setting."
            )
          }
        )
      }
    }
  }
}
