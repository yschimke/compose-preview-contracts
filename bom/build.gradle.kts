import ee.schimke.composeai.buildlogic.PublishedVersions

plugins {
  id("composeai.maven-publishing-platform")
  // `base-conventions` is what gives this project its ktfmt task — `:bom:ktfmtFormat` /
  // `:bom:ktfmtCheck` — so the BOM is formatted and checked like every other project here.
  id("composeai.base-conventions")
}

// The BOM for everything this repository publishes.
//
// These are wire contracts: a consumer deserialising one message should not have to name a version
// for each type it touches, and getting two of them out of step is how a message that serialises
// on one side fails to parse on the other. Importing this platform names one coordinate instead:
//
//     implementation(platform("ee.schimke.composeai:compose-preview-contracts-bom:<version>"))
//     implementation("ee.schimke.composeai:daemon-protocol")
//
// The constraints are derived, not listed. `settings.gradle.kts` collects every project path whose
// build script applies `composeai.maven-publishing` and hands them over as a system property; the
// artifact id is the path with its separators flattened. That convention holds for all twelve
// modules here, verified rather than assumed, and `PublishedArtifactIdTest` in build-logic pins it
// so a module that breaks it fails the build rather than going missing from the BOM.
//
// The two Kotlin Multiplatform modules (`ui-builder-protocol`, `screen-document`) are constrained
// at their base coordinate, which is the one a consumer names; Gradle module metadata resolves the
// `-jvm` / `-wasm-js` variants from it.
//
// Each constraint takes that module's EFFECTIVE version, via the same `PublishedVersions.resolve`
// that sets `project.version`, so the versions the BOM promises and the versions the POMs name
// cannot disagree. Today, with no publish set, everything resolves to the tag.
val publishedProjectPaths =
  providers.systemProperty("composeai.publishedProjectPaths").get().split(",").filter {
    it.isNotBlank()
  }

val publishSet =
  PublishedVersions.parsePublishSet(providers.gradleProperty("composeai.publishSet").orNull)

val manifestText = providers.provider {
  rootProject.layout.projectDirectory
    .file("publishing-manifest.json")
    .asFile
    .takeIf { it.isFile }
    ?.readText() ?: "{}"
}

dependencies {
  constraints {
    publishedProjectPaths
      .map { it.removePrefix(":").replace(':', '-') }
      .sorted()
      .forEach { artifactId ->
        val version =
          PublishedVersions.resolve(
            artifactId = artifactId,
            tagVersion = project.version.toString(),
            publishSet = publishSet,
            manifestText = manifestText.get(),
          )
        api("ee.schimke.composeai:$artifactId:$version")
      }
  }
}

composeAiPlatformPublishing {
  coordinates(
    artifactId = "compose-preview-contracts-bom",
    displayName = "Compose Preview Contracts - Bill of Materials",
    description =
      "Version constraints for every published Compose Preview wire contract, so a consumer " +
        "aligns the protocol and data-shape coordinates with one coordinate.",
  )
  inceptionYear.set("2026")
}
