plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  // Loaded into the root scope so every publishing module shares the plugin's ClassLoader and
  // Gradle can share the MavenCentral build service across them.
  alias(libs.plugins.maven.publish) apply false
}

// The publish task path for each module this build publishes, as `<task>\t<module dir>` rows.
//
// The release job runs this instead of keeping a hand-written task list in the workflow: a list in
// YAML goes stale the moment a module is added, and the failure mode is a coordinate that silently
// stops being published.
val printPublishTasks by
  tasks.registering {
    group = "publishing"
    description = "Print the publish task path for each module this build publishes."
    notCompatibleWithConfigurationCache("Inspects the project tree at execution time")
    val rootDirPath = rootDir
    // `-Pcomposeai.publishSet` names the modules this release actually has to upload, computed by
    // `.github/scripts/maven-publish-plan.sh`. Absent, every module publishes — the old behaviour,
    // and the right default for a `workflow_dispatch` recovery run whose baseline may not be
    // trustworthy.
    //
    // Absent and empty mean different things and must not be collapsed: absent is "no plan ran,
    // publish everything", empty is "the plan ran and found nothing". Deliberately mirrors
    // `PublishedVersions.parsePublishSet`, which the modules and `:bom` use — the root build script
    // cannot see build-logic's classes, so this is the one place the rule is restated.
    val publishSet =
      providers
        .gradleProperty("composeai.publishSet")
        .orNull
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()
    val rows =
      subprojects
        .filter {
          it.plugins.hasPlugin("composeai.maven-publishing") ||
            it.plugins.hasPlugin("composeai.maven-publishing-platform")
        }
        .filter { p ->
          // The BOM indexes a changed coordinate map, not every GitHub release. An empty set leaves
          // every constraint at its already-published version, so a new identical BOM wastes quota.
          publishSet == null ||
            (p.path == ":bom" && publishSet.isNotEmpty()) ||
            (p.path != ":bom" && p.path.removePrefix(":").replace(':', '-') in publishSet)
        }
        .map { p ->
          val dir = p.projectDir.relativeTo(rootDirPath).invariantSeparatorsPath
          "${p.path}:publishAndReleaseToMavenCentral" to dir
        }
    doLast { rows.sortedBy { (task, _) -> task }.forEach { (task, dir) -> println("$task\t$dir") } }
  }

// `check` at the root, so `./gradlew check` — what CI runs, and what every contributor runs —
// reaches build-logic's own tests.
//
// `build-logic` is an `includeBuild`, and Gradle's task-name matching does not descend into an
// included build: `PublishedArtifactIdTest` and `PublishedVersionsTest` were running for nobody.
// They pin the two rules a reduced release turns on — the artifact id `:bom` and the publish set
// address modules by, and the version a skipped module carries — and a published POM naming a
// coordinate that was never uploaded cannot be withdrawn. Tests nothing runs are not a safety net.
// Attached to the root's own `check` rather than registered as one: something further up applies
// `LifecycleBasePlugin` here, and registering a second `check` fails that plugin's own apply.
val buildLogicTests = gradle.includedBuild("build-logic").task(":test")

tasks.matching { it.name == "check" }.configureEach { dependsOn(buildLogicTests) }
