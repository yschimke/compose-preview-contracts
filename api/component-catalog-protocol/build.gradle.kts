// Shape-only catalog wire types: `components.json` (the component record discovery derives),
// `ui-builder.policy.json` (what a catalog authors for a UI builder) and `ui-builder.json` (the
// builder catalog generated from the two). compose-ai-tools writes them, the UI builder and the
// preview server read them, so none of them depends on another to share the shapes. Deriving a
// record, validating a policy and generating a catalog stay in compose-ai-tools.
plugins {
  id("composeai.base-conventions")
  id("composeai.maven-publishing")
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

composeAiMavenPublishing {
  coordinates(
    artifactId = "component-catalog-protocol",
    displayName = "Compose Preview — Component Catalog Protocol",
    description =
      "Versioned serializable shapes for a catalog's component record (components.json), its " +
        "authored UI-builder policy and the generated UI-builder catalog file.",
  )
  inceptionYear.set("2026")
}

kotlin {
  explicitApi()
  // Read by the Java 17 Gradle plugin that writes these files as well as the browser editor.
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
