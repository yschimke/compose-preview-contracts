package ee.schimke.composeai.daemon.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The builders are the construction API for the protocol types that cross a repository boundary as
 * compiled artifacts.
 *
 * They exist for binary compatibility, not ergonomics. Adding a property to a Kotlin data class
 * REMOVES the old `<init>` and `copy$default` signatures rather than adding to them, so a consumer
 * compiled against the previous release dies at its own call site. That is not hypothetical: the
 * design-pages types did it to compose-ai-tools' render-host, as `NoSuchMethodError:
 * DesignPage.copy$default(...)`, and these types are consumed the same way.
 *
 * With the constructors `internal` and `@ConsistentCopyVisibility` making the generated `copy`
 * internal with them, neither is reachable from a consumer, so neither can be the thing that
 * breaks.
 */
class ProtocolBuilderTest {

  @Test
  fun `a builder fills the same defaults the constructor did`() {
    val focus = FocusOverride.Builder().build()
    assertEquals(null, focus.tabIndex)
    assertEquals(false, focus.overlay)
    assertEquals(false, focus.pressed)

    val gesture = GestureOverride.Builder().build()
    assertEquals(null, gesture.enabled)
    assertEquals(null, gesture.invokeLabel)

    val result = ExtensionsEnableResult.Builder().build()
    assertEquals(emptyList(), result.newlyEnabled)
    assertEquals(emptyList(), result.previewExtensions)
  }

  @Test
  fun `newBuilder round-trips every property`() {
    // The guarantee that makes `newBuilder()` a safe replacement for `copy()`: a property added to
    // the class and forgotten in `newBuilder` fails here rather than in a consumer, silently
    // dropped on the way across the wire.
    val focus =
      FocusOverride.Builder()
        .also {
          it.tabIndex = 3
          it.step = 2
          it.overlay = true
          it.enterPlacesFocus = true
          it.pressed = true
        }
        .build()
    assertEquals(focus, focus.newBuilder().build())

    val remote =
      RemoteComposeOverride.Builder()
        .also {
          it.namedValues = mapOf("k" to RemoteNamedValue.StringValue("v"))
          it.acceptedHostActions = listOf("tap")
        }
        .build()
    assertEquals(remote, remote.newBuilder().build())
  }

  @Test
  fun `newBuilder derives a modified value without touching the original`() {
    val original = GestureOverride.Builder().also { it.enabled = true }.build()
    val derived = original.newBuilder().also { it.invokeLabel = "Swipe" }.build()

    assertEquals(null, original.invokeLabel)
    assertEquals("Swipe", derived.invokeLabel)
    assertEquals(true, derived.enabled)
    assertNotEquals(original, derived)
  }

  @Test
  fun `a launch descriptor round-trips its required and optional properties`() {
    val descriptor =
      DaemonLaunchDescriptor.Builder(
          schemaVersion = 1,
          modulePath = ":app",
          variant = "debug",
          enabled = true,
          mainClass = "ee.schimke.Main",
          classpath = listOf("a.jar"),
          jvmArgs = listOf("-Xmx1g"),
          systemProperties = mapOf("k" to "v"),
          workingDirectory = "/tmp",
          manifestPath = "/tmp/manifest.json",
        )
        .also {
          it.javaLauncher = "/usr/bin/java"
          it.jailCommand = listOf("nsjail")
          it.hardTtlSeconds = 30L
        }
        .build()

    assertEquals(descriptor, descriptor.newBuilder().build())
    assertEquals("/usr/bin/java", descriptor.javaLauncher)
    assertEquals(30L, descriptor.hardTtlSeconds)
  }
}
