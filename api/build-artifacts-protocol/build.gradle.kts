// The build-neutral artifacts file a build-system integration (a Bazel aspect, a wrapper script)
// writes and the compose-preview CLI reads to discover and render one configured target. Shape
// only: running builds, resolving paths and launching renderers stay in compose-ai-tools.
plugins {
  id("composeai.base-conventions")
  id("composeai.maven-publishing")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  testImplementation(kotlin("test"))
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "build-artifacts-protocol",
    displayName = "Compose Preview — Build Artifacts Protocol",
    description =
      "The versioned artifacts file a non-Gradle build integration writes for one configured " +
        "target — application and runtime jars, sources, runfiles and Android resources.",
  )
  inceptionYear.set("2026")
}

kotlin {
  explicitApi()

  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()
}

tasks.named("check") { dependsOn("checkKotlinAbi") }
