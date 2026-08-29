package ee.schimke.composeai.daemon.protocol

import kotlinx.serialization.Serializable

/**
 * Preview-extension and pipeline descriptors carried on the wire.
 *
 * Moved here from `data-render-core`. These are the metadata a client needs to validate a planned
 * product before a scene is allocated; the pipeline that executes them is behaviour and stays in
 * compose-ai-tools.
 */
/**
 * Renderer-agnostic description of a preview extension step.
 *
 * This is intentionally metadata-first: renderers and clients can validate a planned product before
 * they allocate a Compose scene, and UI clients can explain why a requested combination is not
 * available.
 */
@Serializable
public data class PreviewPipelineStep(
  val id: String,
  val displayName: String = id,
  val productKinds: List<String> = emptyList(),
  val annotationFqns: List<String> = emptyList(),
  val usageModes: Set<PreviewExtensionUsageMode> = setOf(PreviewExtensionUsageMode.ExplicitEffect),
  val traits: Set<PipelineStepTrait> = emptySet(),
  val requires: Set<PipelineCapability> = emptySet(),
  val provides: Set<PipelineCapability> = emptySet(),
  val conflictsWith: Set<PipelineStepTrait> = emptySet(),
  val sampling: SamplingPolicy? = null,
  val extraction: ExtractionSpec? = null,
)

@Serializable
public enum class PreviewExtensionUsageMode {
  ExplicitEffect,
  SuggestedExtraPreview,
}

@Serializable
public data class PreviewExtensionDescriptor(
  val id: String,
  val displayName: String = id,
  val usageModes: Set<PreviewExtensionUsageMode> = setOf(PreviewExtensionUsageMode.ExplicitEffect),
  val componentExtensionIds: List<String> = emptyList(),
  val cliCommands: List<PreviewExtensionCliCommand> = emptyList(),
  val steps: List<PreviewPipelineStep> = emptyList(),
)

@Serializable
public data class PreviewExtensionCliCommand(
  val id: String,
  val displayName: String = id,
  val summary: String = "",
  val command: List<String>,
  val agentRecommended: Boolean = false,
  val requiresDaemon: Boolean = false,
  val usageModes: Set<PreviewExtensionUsageMode> = emptySet(),
  val productKinds: List<String> = emptyList(),
)

@Serializable
public enum class PipelineStepTrait {
  ScenarioDriver,
  InteractiveDriver,
  AnnotationInspector,
  ExtraPreviewSuggester,
  FrameProcessor,
  FinalArtifactProcessor,
  DataExtractor,
  Check,
  Encoder,
  Profiler,
}

@Serializable
public enum class PipelineCapability {
  Frames,
  SingleFrame,
  MultipleFrames,
  PreviewFunctionAnnotations,
  SuggestedPreviews,
  DeviceGeometry,
  DeviceClip,
  DeviceBackground,
  ScrollState,
  SemanticsSnapshot,
  AccessibilityNodes,
  AccessibilityFindings,
  OverlayAnnotations,
  ImageArtifact,
  AnnotatedImageArtifact,
  AnimatedArtifact,
  InteractiveSession,
  TraceEvents,
}

@Serializable
public enum class SamplingPolicy {
  Start,
  End,
  EachFrame,
  OnDemand,
  Aggregate,
  Failure,
}

@Serializable
public data class ExtractionSpec(
  val kind: String,
  val sampling: SamplingPolicy,
  val requiresImage: Boolean = false,
  val requiresSemantics: Boolean = false,
  val aggregate: Boolean = false,
)
