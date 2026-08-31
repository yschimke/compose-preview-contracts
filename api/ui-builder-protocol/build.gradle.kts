// Shape-only v1 protocol shared by the Compose UI builder's browser, server and MCP surfaces.
// There is intentionally no Compose, server, filesystem, network or reducer dependency here.
plugins {
  id("composeai.base-conventions")
  id("composeai.maven-publishing")
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "ui-builder-protocol",
    displayName = "Compose Preview — UI Builder Protocol",
    description =
      "Versioned serializable catalog, design, command, collaboration and transport-envelope " +
        "shapes shared by Compose UI builder browser, server and MCP clients.",
  )
  inceptionYear.set("2026")
}

kotlin {
  explicitApi()

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
      dependencies {
        implementation(libs.junit)
        implementation(kotlin("test"))
      }
    }
  }
}

tasks.named("check") { dependsOn("checkKotlinAbi") }
