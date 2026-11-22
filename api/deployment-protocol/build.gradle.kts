// What every container deployed beside the preview server must agree on with the tools that deploy
// it and with its siblings: the liveness / readiness / version endpoints, the release manifest a
// component publishes, and the session-introspection reply a sibling reads to authenticate a
// browser. The preview server, the UI builder and the hosted MCP ship on their own release trains,
// so these shapes live below all three. Shape only: probing, rolling out and verifying sessions
// stay in each implementation. See docs/design/DEPLOYMENT_PROTOCOL.md.
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
    artifactId = "deployment-protocol",
    displayName = "Compose Preview — Deployment Protocol",
    description =
      "The health, readiness and version endpoints, release manifest and session-introspection " +
        "reply shared by the containers deployed beside the compose-preview server.",
  )
  inceptionYear.set("2026")
}

kotlin {
  explicitApi()

  @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class) abiValidation()
}

tasks.named("check") { dependsOn("checkKotlinAbi") }
