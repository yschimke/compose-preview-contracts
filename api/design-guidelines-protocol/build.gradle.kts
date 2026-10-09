// Shape-only design-guideline vocabulary: the rules a catalog publishes, the pictures it asks for,
// the request a guidelines model is sent and the result recorded from it. Every host that checks
// guidelines speaks it — the UI builder, the compose-preview CLI, VS Code previews, the preview
// server and the preview-diff workflow — so none of them depends on another to share it. Building
// a prompt, drawing a picture and calling a model stay in their implementation repositories.
plugins {
  id("composeai.base-conventions")
  id("composeai.maven-publishing")
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "design-guidelines-protocol",
    displayName = "Compose Preview — Design Guidelines Protocol",
    description =
      "Versioned serializable shapes for design-guideline checks: a catalog's rules and picture " +
        "frames, the request a guidelines model is sent, and the recorded result.",
  )
  inceptionYear.set("2026")
}

kotlin {
  explicitApi()
  // Read by the Java 17 build tooling as well as the browser editor, as ui-builder-protocol is.
  jvmToolchain(17)

  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()

  jvm()
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  sourceSets {
    commonMain {
      kotlin.srcDir("src/main/kotlin")
      dependencies { api(libs.kotlinx.serialization.json) }
    }
    jvmTest {
      kotlin.srcDir("src/test/kotlin")
      resources.srcDir("src/test/resources")
      dependencies {
        implementation(libs.junit)
        implementation(kotlin("test"))
      }
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }
