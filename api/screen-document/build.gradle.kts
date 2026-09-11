// Serializable screen inputs shared by offline generators and their editor/server consumers.
// There is intentionally no Compose, server, filesystem, network or reducer dependency here.
plugins {
  id("composeai.base-conventions")
  id("composeai.maven-publishing")
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "screen-document",
    displayName = "Compose Preview — Screen Document",
    description =
      "Serializable screen, value, action, selection, repetition and function shapes shared by " +
        "Compose code generators and their consumers. No generator or Compose runtime.",
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
