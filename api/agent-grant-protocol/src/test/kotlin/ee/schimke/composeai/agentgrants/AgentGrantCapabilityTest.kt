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
      listOf("images", "ui-builder-read", "ui-builder-write", "ui-builder-export"),
      AgentGrantCapability.wireNames(AgentGrantCapability.entries.toSet()),
    )
    assertNull(AgentGrantCapability.parse("ui-builder"))
  }

  @Test
  fun `scope ladder never implies persistent ui builder permission`() {
    val uiBuilderCapabilities =
      setOf(
        AgentGrantCapability.UI_BUILDER_READ,
        AgentGrantCapability.UI_BUILDER_WRITE,
        AgentGrantCapability.UI_BUILDER_EXPORT,
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
