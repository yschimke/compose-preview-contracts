package ee.schimke.composeai.discovery

import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenDocumentJvmFloorTest {
  @Test
  fun `screen document bytecode can load on Java 17 regardless of the Gradle JVM`() {
    val type = ScreenDocument::class.java
    DataInputStream(requireNotNull(type.getResourceAsStream("ScreenDocument.class"))).use { input ->
      assertEquals(0xCAFEBABE.toInt(), input.readInt())
      input.readUnsignedShort() // minor version
      val major = input.readUnsignedShort()
      assertTrue(major <= 61, "screen-document requires Java ${major - 44}; consumers run Java 17")
    }
  }
}
