package ee.schimke.composeai.agentgrants

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AgentGrantCapabilityTest {
  @Test
  fun `ui builder permissions are independent capabilities with stable wire names`() {
    assertEquals(
      AgentGrantCapability.UI_BUILDER_READ,
      AgentGrantCapability.parse("UI-BUILDER-READ"),
    )
    assertEquals(
      setOf(
        AgentGrantCapability.IMAGES,
        AgentGrantCapability.UI_BUILDER_WRITE,
        AgentGrantCapability.UI_BUILDER_EXPORT,
      ),
      AgentGrantCapability.parseAll("images, ui-builder-write ui-builder-export"),
    )
    assertEquals(
      listOf(
        "images",
        "ui-builder-read",
        "ui-builder-write",
        "ui-builder-export",
        "ui-builder-destructive",
        "ui-builder-publish",
      ),
      AgentGrantCapability.wireNames(AgentGrantCapability.entries.toSet()),
    )
    assertNull(AgentGrantCapability.parse("ui-builder"))
  }

  @Test
  fun `destructive is its own capability, never implied by write`() {
    assertEquals(
      AgentGrantCapability.UI_BUILDER_DESTRUCTIVE,
      AgentGrantCapability.parse("ui-builder-destructive"),
    )
    assertFalse(
      AgentGrantCapability.UI_BUILDER_DESTRUCTIVE in
        AgentGrantCapability.parseAll("ui-builder-read, ui-builder-write, ui-builder-export"),
    )
  }

  @Test
  fun `publish is its own capability, never implied by write or destructive`() {
    val publish = AgentGrantCapability.parse("ui-builder-publish")
    assertEquals(AgentGrantCapability.UI_BUILDER_PUBLISH, publish)
    assertEquals(
      AgentGrantCapability.UI_BUILDER_PUBLISH,
      AgentGrantCapability.parse(" UI-Builder-Publish "),
    )
    assertEquals(
      listOf("ui-builder-publish"),
      AgentGrantCapability.wireNames(AgentGrantCapability.parseAll("ui-builder-publish")),
    )
    assertEquals(
      "Publish this account's watch faces to watches, including to every watch for public designs",
      AgentGrantCapability.UI_BUILDER_PUBLISH.humanDescription,
    )
    assertFalse(
      AgentGrantCapability.UI_BUILDER_PUBLISH in
        AgentGrantCapability.parseAll(
          "images, ui-builder-read, ui-builder-write, ui-builder-export, ui-builder-destructive",
        ),
    )
  }

  @Test
  fun `every capability round-trips through its wire name`() {
    AgentGrantCapability.entries.forEach { capability ->
      assertEquals(capability, AgentGrantCapability.parse(capability.wire))
      assertEquals(
        setOf(capability),
        AgentGrantCapability.parseAll(AgentGrantCapability.wireNames(setOf(capability)).single()),
      )
    }
  }

  @Test
  fun `scope ladder never implies persistent ui builder permission`() {
    val uiBuilderCapabilities =
      setOf(
        AgentGrantCapability.UI_BUILDER_READ,
        AgentGrantCapability.UI_BUILDER_WRITE,
        AgentGrantCapability.UI_BUILDER_EXPORT,
        AgentGrantCapability.UI_BUILDER_DESTRUCTIVE,
        AgentGrantCapability.UI_BUILDER_PUBLISH,
      )
    AgentGrantScope.entries.forEach { scope ->
      uiBuilderCapabilities.forEach { capability ->
        assertFalse(scope.name == capability.name)
        assertFalse(scope.wire == capability.wire)
        assertFalse(AgentGrantScope.upTo(scope).any { it.wire == capability.wire })
        assertNull(AgentGrantScope.parse(capability.wire))
      }
    }
  }
}
