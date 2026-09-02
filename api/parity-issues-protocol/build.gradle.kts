// The versioned JSON artifact a catalog workflow publishes so a preview server can join GitHub
// issues back to the previews and design references they describe. Shape only: fetching GitHub,
// validating trust boundaries and rendering issue badges stay in their implementation repository.
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
    artifactId = "parity-issues-protocol",
    displayName = "Compose Preview — Parity Issues Protocol",
    description =
      "The versioned catalog artifact that joins GitHub issues to Compose previews and design " +
        "references, without producer or server implementation.",
  )
  inceptionYear.set("2026")
}

kotlin {
  explicitApi()

  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()
}

tasks.named("check") { dependsOn("checkKotlinAbi") }
