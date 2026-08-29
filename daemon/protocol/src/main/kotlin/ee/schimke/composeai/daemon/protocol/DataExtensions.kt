package ee.schimke.composeai.daemon.protocol

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Data-extension descriptors carried on the wire, moved here from `data-render-core`. The planner,
 * the store and the extension implementations are behaviour and stay in compose-ai-tools.
 */
/**
 * Renderer-agnostic identity for a data extension.
 *
 * Extension ids are stable protocol/configuration names, not Kotlin class names. They are used in
 * request input maps, ordering constraints, and diagnostic messages.
 */
@Serializable
@JvmInline
public value class DataExtensionId(public val value: String) : Comparable<DataExtensionId> {
  init {
    require(value.isNotBlank()) { "Data extension id must not be blank." }
  }

  override fun compareTo(other: DataExtensionId): Int = value.compareTo(other.value)

  override fun toString(): String = value
}

@Serializable
public data class DataExtensionDescriptor(
  val id: DataExtensionId,
  val displayName: String = id.value,
  val recordingScriptEvents: List<RecordingScriptEventDescriptor> = emptyList(),
  /**
   * Issue #1203 — `true` when every dispatch path under this extension only makes sense while a
   * held interactive composition is up (the canonical case is keyboard / rotary input). Clients use
   * this to auto-enter live mode when the user toggles the extension on for a preview instead of
   * asking them to flip Live separately. Defaults to `false` so existing extensions (recording
   * probe, state save/restore, etc.) keep their pre-flag behaviour.
   */
  val requiresInteractive: Boolean = false,
)

@Serializable
public data class RecordingScriptEventDescriptor(
  val id: String,
  val displayName: String = id,
  val summary: String = "",
  val supported: Boolean = false,
) {
  init {
    require(id.contains('.')) {
      "Recording script event id '$id' must be namespaced, e.g. '${id}.event'."
    }
    require(id.isNotBlank()) { "Recording script event id must not be blank." }
  }
}
