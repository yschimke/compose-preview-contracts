package ee.schimke.composeai.buildartifacts.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class BuildArtifactsTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `schema 1 files written by released aspects still decode`() {
    val artifacts =
      json.decodeFromString<BuildArtifacts>(
        """
        {
          "schemaVersion": 1,
          "target": "//app:ui",
          "configuration": "bazel-out/k8-fastbuild/bin",
          "projectJars": ["bazel-out/k8-fastbuild/bin/app/ui.jar"],
          "runtimeJars": ["bazel-out/k8-fastbuild/bin/app/ui.jar", "external/dep/dep.jar"],
          "sourceFiles": ["app/Ui.kt"],
          "dataFiles": []
        }
        """
      )
    assertEquals(BuildArtifacts.SCHEMA_VERSION_JVM, artifacts.schemaVersion)
    assertNull(artifacts.android)
    assertEquals(listOf("app/Ui.kt"), artifacts.sourceFiles)
  }

  @Test
  fun `a JVM target omits the android block and stays on schema 1`() {
    val artifacts =
      BuildArtifacts.Builder("//app:ui", "cfg", listOf("a.jar"), listOf("a.jar")).build()
    assertEquals(BuildArtifacts.SCHEMA_VERSION_JVM, artifacts.schemaVersion)
    assertFalse("android" in Json.encodeToString(BuildArtifacts.serializer(), artifacts))
  }

  @Test
  fun `an android block selects schema 2 and round trips`() {
    val android =
      AndroidBuildArtifacts.Builder("com.example.ui", "res.ap_", "AndroidManifest.xml")
        .apply {
          rJar = "r.jar"
          compileSdk = 35
        }
        .build()
    val artifacts =
      BuildArtifacts.Builder("//app:ui", "cfg", listOf("a.jar"), listOf("a.jar"))
        .apply { this.android = android }
        .build()
    assertEquals(BuildArtifacts.SCHEMA_VERSION_ANDROID, artifacts.schemaVersion)
    val decoded =
      json.decodeFromString<BuildArtifacts>(
        Json.encodeToString(BuildArtifacts.serializer(), artifacts)
      )
    assertEquals(artifacts, decoded)
    assertEquals(artifacts, artifacts.newBuilder().build())
    assertEquals(android, android.newBuilder().build())
  }

  @Test
  fun `the link inputs the Android aspect writes decode and round trip`() {
    val artifacts =
      json.decodeFromString<BuildArtifacts>(
        """
        {
          "schemaVersion": 2,
          "target": "//ui:ui",
          "configuration": "bazel-out/k8-fastbuild/bin",
          "projectJars": ["ui.jar"],
          "runtimeJars": ["r-final.jar", "ui.jar"],
          "android": {
            "packageName": "com.example.ui",
            "resourceApk": "resources.ap_",
            "manifest": "AndroidManifest.xml",
            "rJar": "r-final.jar",
            "link": {
              "script": "external/rules_compose_preview+/link.sh",
              "aapt2": "external/androidsdk/build-tools/36.0.0/aapt2",
              "javaHome": "external/remotejdk17_linux",
              "androidJar": "external/androidsdk/platforms/android-36/android.jar",
              "packages": ["com.example.ui", "com.example.theme"],
              "compiledResources": ["theme/theme.zip", "ui/ui_impl.zip"],
              "assets": [{"path": "nested/ui-only.txt", "file": "ui/assets/nested/ui-only.txt"}]
            }
          }
        }
        """
      )
    val link = assertNotNull(artifacts.android?.link)
    assertEquals(listOf("com.example.ui", "com.example.theme"), link.packages)
    assertEquals(listOf("theme/theme.zip", "ui/ui_impl.zip"), link.compiledResources)
    assertEquals(
      listOf(
        AndroidLinkAsset.Builder("nested/ui-only.txt", "ui/assets/nested/ui-only.txt").build()
      ),
      link.assets,
    )
    assertEquals(
      artifacts,
      json.decodeFromString<BuildArtifacts>(
        Json.encodeToString(BuildArtifacts.serializer(), artifacts)
      ),
    )
    assertEquals(link, link.newBuilder().build())
    assertEquals(artifacts.android, artifacts.android?.newBuilder()?.build())
  }

  @Test
  fun `an android block without link inputs omits them`() {
    val android = AndroidBuildArtifacts.Builder("com.example.ui", "res.ap_", "m.xml").build()
    assertNull(android.link)
    assertFalse("link" in Json.encodeToString(AndroidBuildArtifacts.serializer(), android))
  }
}
