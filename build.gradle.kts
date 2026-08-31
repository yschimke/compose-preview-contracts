plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  // Loaded into the root scope so every publishing module shares the plugin's ClassLoader and
  // Gradle can share the MavenCentral build service across them.
  alias(libs.plugins.maven.publish) apply false
}
