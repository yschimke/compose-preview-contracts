@file:OptIn(ExperimentalSerializationApi::class)

package ee.schimke.composeai.guidelines.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One design rule as a model is asked to judge it: the [guidance] quoted from [source], and a
 * yes/no [check] where YES means the design follows it.
 *
 * [kind] is [KIND_STRUCTURE] when the design's tree or source is enough evidence and [KIND_VISUAL]
 * when the model needs a picture. [surfaces] narrows it to [SURFACE_SCREEN] or [SURFACE_WIDGET]
 * designs and [profiles] to the Remote Compose profiles it is about (`launcher-widgets-v7`, with an
 * optional `+experimental`); empty means every one. [platforms] is empty in a catalog's own file,
 * whose [CatalogGuidelinesV1.platform] covers every rule in it.
 */
@Serializable
@ConsistentCopyVisibility
public data class GuidelineRuleV1
internal constructor(
  public val id: String,
  public val platforms: List<String> = emptyList(),
  public val kind: String,
  public val severity: String,
  public val guidance: String,
  public val check: String,
  public val source: String,
  public val surfaces: List<String> = emptyList(),
  public val profiles: List<String> = emptyList(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var id: String,
    public var kind: String,
    public var severity: String,
    public var guidance: String,
    public var check: String,
    public var source: String,
  ) {
    public var platforms: List<String> = emptyList()
    public var surfaces: List<String> = emptyList()
    public var profiles: List<String> = emptyList()

    public fun build(): GuidelineRuleV1 =
      GuidelineRuleV1(id, platforms, kind, severity, guidance, check, source, surfaces, profiles)
  }

  /** This rule as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id, kind, severity, guidance, check, source).also {
      it.platforms = platforms
      it.surfaces = surfaces
      it.profiles = profiles
    }

  public companion object {
    public const val KIND_STRUCTURE: String = "structure"
    public const val KIND_VISUAL: String = "visual"
    public const val SEVERITY_WARNING: String = "warning"
    public const val SEVERITY_INFO: String = "info"
    public const val SURFACE_SCREEN: String = "screen"
    public const val SURFACE_WIDGET: String = "widget"
  }
}

/** A set of rules under one [version], as a host bundles them. */
@Serializable
@ConsistentCopyVisibility
public data class GuidelineRuleSetV1
internal constructor(
  public val schema: String,
  public val version: Int,
  public val about: String = "",
  public val rules: List<GuidelineRuleV1>,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var schema: String,
    public var version: Int,
    public var rules: List<GuidelineRuleV1>,
  ) {
    public var about: String = ""

    public fun build(): GuidelineRuleSetV1 = GuidelineRuleSetV1(schema, version, about, rules)
  }

  /** This set as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(schema, version, rules).also { it.about = about }
}

/**
 * One picture a catalog asks a guidelines model to be shown. [kind] says how a host draws it:
 * - [KIND_DEVICE]: the design as authored, its first frame.
 * - [KIND_UNROLLED]: [heightFactor] times as tall, so a scrolling list reaches its end.
 * - [KIND_SIZED]: at [widthDp] × [heightDp], whatever size the design was authored at.
 * - [KIND_WIDGET_HOST]: a widget in the launcher container [hostShape] (`round`, `squircle`,
 *   `rectangular`), at the widget's own size.
 *
 * [surface] limits it to one kind of design; [whenScrolls] asks for it only when the design
 * scrolls; [label] names it ("tablet", "Samsung"). [description] is the catalog's own words for it,
 * which say what the picture is and never what is wrong with it.
 */
@Serializable
@ConsistentCopyVisibility
public data class GuidelineFrameV1
internal constructor(
  public val kind: String,
  public val surface: String? = null,
  public val label: String? = null,
  public val widthDp: Int? = null,
  public val heightDp: Int? = null,
  public val heightFactor: Int? = null,
  public val hostShape: String? = null,
  public val whenScrolls: Boolean = false,
  public val description: String? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var kind: String) {
    public var surface: String? = null
    public var label: String? = null
    public var widthDp: Int? = null
    public var heightDp: Int? = null
    public var heightFactor: Int? = null
    public var hostShape: String? = null
    public var whenScrolls: Boolean = false
    public var description: String? = null

    public fun build(): GuidelineFrameV1 =
      GuidelineFrameV1(
        kind,
        surface,
        label,
        widthDp,
        heightDp,
        heightFactor,
        hostShape,
        whenScrolls,
        description,
      )
  }

  /** This frame as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(kind).also {
      it.surface = surface
      it.label = label
      it.widthDp = widthDp
      it.heightDp = heightDp
      it.heightFactor = heightFactor
      it.hostShape = hostShape
      it.whenScrolls = whenScrolls
      it.description = description
    }

  public companion object {
    public const val KIND_DEVICE: String = "device"
    public const val KIND_UNROLLED: String = "unrolled"
    public const val KIND_SIZED: String = "sized"
    public const val KIND_WIDGET_HOST: String = "widget-host"
  }
}

/**
 * A catalog's own design guidance, published as [FILE_NAME] beside its `ui-builder.json`: the rules
 * a design or preview built from [catalog] is checked against, and the pictures the model is shown.
 * [version] is bumped by the catalog whenever a rule or frame changes and is recorded with every
 * result.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogGuidelinesV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  public val catalog: String,
  public val platform: String,
  public val version: Int,
  public val about: String = "",
  public val frames: List<GuidelineFrameV1> = emptyList(),
  public val rules: List<GuidelineRuleV1> = emptyList(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var catalog: String,
    public var platform: String,
    public var version: Int,
  ) {
    public var schema: String = SCHEMA
    public var about: String = ""
    public var frames: List<GuidelineFrameV1> = emptyList()
    public var rules: List<GuidelineRuleV1> = emptyList()

    public fun build(): CatalogGuidelinesV1 =
      CatalogGuidelinesV1(schema, catalog, platform, version, about, frames, rules)
  }

  /** These guidelines as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(catalog, platform, version).also {
      it.schema = schema
      it.about = about
      it.frames = frames
      it.rules = rules
    }

  public companion object {
    public const val SCHEMA: String = "compose-ui-builder/catalog-guidelines/v1"

    /** The file name a catalog publishes its guidelines under, beside `ui-builder.json`. */
    public const val FILE_NAME: String = "ui-builder.guidelines.json"
  }
}

/**
 * A picture attached to a [GuidelineRequestV1]: one frame drawn. [kind] is the frame it is (see the
 * constants, or a catalog's sized-frame label such as `tablet`), and [description] is how the
 * request's user text introduces it. [dataUrl] is absent where the bytes travel separately, as MCP
 * image blocks do.
 */
@Serializable
@ConsistentCopyVisibility
public data class GuidelinePictureV1
internal constructor(
  public val kind: String,
  public val description: String,
  public val widthDp: Int,
  public val heightDp: Int,
  public val dataUrl: String? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var kind: String,
    public var description: String,
    public var widthDp: Int,
    public var heightDp: Int,
  ) {
    public var dataUrl: String? = null

    public fun build(): GuidelinePictureV1 =
      GuidelinePictureV1(kind, description, widthDp, heightDp, dataUrl)
  }

  /** This picture as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(kind, description, widthDp, heightDp).also { it.dataUrl = dataUrl }

  public companion object {
    public const val KIND_DEVICE: String = "device"
    public const val KIND_UNROLLED: String = "unrolled"
    public const val KIND_WIDGET_SAMSUNG: String = "widget-samsung"
    public const val KIND_WIDGET_PIXEL_WATCH: String = "widget-pixel-watch"
  }
}

/**
 * The rules a [GuidelineRequestV1] asks about: [asked] of the [forPlatform] that apply to this kind
 * of design, from the set [version] published at [source]. [visualSkipped] counts the visual rules
 * left out because no picture could be attached.
 */
@Serializable
@ConsistentCopyVisibility
public data class GuidelineRequestRulesV1
internal constructor(
  public val version: Int,
  public val source: String,
  public val forPlatform: Int,
  public val asked: List<GuidelineRuleV1>,
  public val visualSkipped: Int = 0,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var version: Int,
    public var source: String,
    public var forPlatform: Int,
    public var asked: List<GuidelineRuleV1>,
  ) {
    public var visualSkipped: Int = 0

    public fun build(): GuidelineRequestRulesV1 =
      GuidelineRequestRulesV1(version, source, forPlatform, asked, visualSkipped)
  }

  /** These rules as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(version, source, forPlatform, asked).also { it.visualSkipped = visualSkipped }
}

/**
 * Everything a guidelines model is asked about one subject, before a model is chosen: the exact
 * [systemPrompt] and [userText] it reads, the [pictures] it sees, the [rules] it answers, the
 * [responseSchema] its reply is held to and, for a person reading it, where each part came from
 * ([provenance]).
 *
 * The subject is a UI-builder design ([designId] at [revision]) or a rendered `@Preview`
 * ([previewId]); a host fills the one it has. [platform] is null for a catalog with no guidelines.
 */
@Serializable
@ConsistentCopyVisibility
public data class GuidelineRequestV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  public val designId: String? = null,
  /** The `@Preview` this request is about, where the subject is a rendered preview. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val previewId: String? = null,
  public val revision: Int,
  public val platform: String? = null,
  public val rules: GuidelineRequestRulesV1,
  public val pictures: List<GuidelinePictureV1> = emptyList(),
  public val sourceAttached: Boolean = false,
  public val systemPrompt: String,
  public val userText: String,
  public val responseSchema: JsonObject,
  public val provenance: List<String> = emptyList(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var revision: Int,
    public var rules: GuidelineRequestRulesV1,
    public var systemPrompt: String,
    public var userText: String,
    public var responseSchema: JsonObject,
  ) {
    public var schema: String = SCHEMA
    public var designId: String? = null
    public var previewId: String? = null
    public var platform: String? = null
    public var pictures: List<GuidelinePictureV1> = emptyList()
    public var sourceAttached: Boolean = false
    public var provenance: List<String> = emptyList()

    public fun build(): GuidelineRequestV1 =
      GuidelineRequestV1(
        schema,
        designId,
        previewId,
        revision,
        platform,
        rules,
        pictures,
        sourceAttached,
        systemPrompt,
        userText,
        responseSchema,
        provenance,
      )
  }

  /** This request as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(revision, rules, systemPrompt, userText, responseSchema).also {
      it.schema = schema
      it.designId = designId
      it.previewId = previewId
      it.platform = platform
      it.pictures = pictures
      it.sourceAttached = sourceAttached
      it.provenance = provenance
    }

  public companion object {
    public const val SCHEMA: String = "compose-ui-builder/guidelines-prompt/v1"
  }
}

/**
 * A model's answer for one rule: [verdict] is `pass`, `fail` or `not_applicable`, [confidence] its
 * probability (0 to 1) that the verdict is right, and [nodeIds] the design nodes it is about.
 */
@Serializable
@ConsistentCopyVisibility
public data class GuidelineVerdictV1
internal constructor(
  public val ruleId: String,
  public val verdict: String,
  public val confidence: Double = 0.0,
  public val nodeIds: List<String> = emptyList(),
  public val reason: String = "",
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var ruleId: String, public var verdict: String) {
    public var confidence: Double = 0.0
    public var nodeIds: List<String> = emptyList()
    public var reason: String = ""

    public fun build(): GuidelineVerdictV1 =
      GuidelineVerdictV1(ruleId, verdict, confidence, nodeIds, reason)
  }

  /** This verdict as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(ruleId, verdict).also {
      it.confidence = confidence
      it.nodeIds = nodeIds
      it.reason = reason
    }

  public companion object {
    public const val PASS: String = "pass"
    public const val FAIL: String = "fail"
    public const val NOT_APPLICABLE: String = "not_applicable"
  }
}

/**
 * A subject's latest guidelines result as a host keeps it: the [verdicts] and the rule ids they
 * answered ([asked]), not the findings, so every reader derives findings from the rules the same
 * way. The host fills [ranBy] and [recordedAtEpochMillis] from the credential, never from the body.
 * The subject is a design ([designId]) or a `@Preview` ([previewId]), as for [GuidelineRequestV1].
 */
@Serializable
@ConsistentCopyVisibility
public data class GuidelineRecordV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  public val designId: String? = null,
  /** The `@Preview` this result is about, where the subject is a rendered preview. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val previewId: String? = null,
  public val revision: Int,
  public val model: String,
  public val rulesVersion: Int,
  public val asked: List<String>,
  public val verdicts: List<GuidelineVerdictV1>,
  public val ranBy: String? = null,
  public val recordedAtEpochMillis: Long? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var revision: Int,
    public var model: String,
    public var rulesVersion: Int,
    public var asked: List<String>,
    public var verdicts: List<GuidelineVerdictV1>,
  ) {
    public var schema: String = SCHEMA
    public var designId: String? = null
    public var previewId: String? = null
    public var ranBy: String? = null
    public var recordedAtEpochMillis: Long? = null

    public fun build(): GuidelineRecordV1 =
      GuidelineRecordV1(
        schema,
        designId,
        previewId,
        revision,
        model,
        rulesVersion,
        asked,
        verdicts,
        ranBy,
        recordedAtEpochMillis,
      )
  }

  /** This record as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(revision, model, rulesVersion, asked, verdicts).also {
      it.schema = schema
      it.designId = designId
      it.previewId = previewId
      it.ranBy = ranBy
      it.recordedAtEpochMillis = recordedAtEpochMillis
    }

  public companion object {
    public const val SCHEMA: String = "compose-ui-builder/guidelines-result/v1"
  }
}
