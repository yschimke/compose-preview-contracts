package ee.schimke.composeai.agentgrants

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AgentGrantCapabilityTest {
  @Test
  fun `ui builder is an independent capability and has a stable wire name`() {
    assertEquals(AgentGrantCapability.UI_BUILDER, AgentGrantCapability.parse("UI-BUILDER"))
    assertEquals(
      setOf(AgentGrantCapability.IMAGES, AgentGrantCapability.UI_BUILDER),
      AgentGrantCapability.parseAll("images, ui-builder"),
    )
    assertEquals(
      listOf("images", "ui-builder"),
      AgentGrantCapability.wireNames(AgentGrantCapability.entries.toSet()),
    )
  }

  @Test
  fun `scope ladder never implies persistent ui builder permission`() {
    AgentGrantScope.entries.forEach { scope ->
      assertFalse(scope.name == AgentGrantCapability.UI_BUILDER.name)
      assertFalse(scope.wire == AgentGrantCapability.UI_BUILDER.wire)
      assertFalse(
        AgentGrantScope.upTo(scope).any { it.wire == AgentGrantCapability.UI_BUILDER.wire }
      )
    }
    assertNull(AgentGrantScope.parse(AgentGrantCapability.UI_BUILDER.wire))
  }
}
