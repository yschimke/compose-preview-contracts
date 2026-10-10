package ee.schimke.composeai.discovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `ui-builder.json` — the builder catalog a repository publishes, generated and never edited.
 *
 * Produced by [UiBuilderCatalogs.generate] from three inputs the catalog repository owns: the
 * discovered component record, the `catalog.spec.json` cover sheet, and the authored
 * [UiBuilderPolicyFile]. Written by the discovery task into `build/compose-previews/` beside
 * `components.json`, and copied by the design-artifacts pipeline to the delivery branch root where
 * `catalog.json` names it as `uiBuilderFile`.
 *
 * ### It is policy, and the record beside it is the inventory
 *
 * This file deliberately does **not** restate the components. Every parameter, slot, call site and
 * opt-in marker is already in `components.json`, which travels with it, is published by the same
 * run and is pinned by the same revision; a consumer reads the two together, joining on
 * [UiBuilderComponentPolicy.record]. Deriving a second, fuller component list here would put a
 * second implementation of the derivation rules into the pipeline while the first is still running
 * in the preview server — and two implementations of a rule this exacting is how the two sides of a
 * contract come to disagree.
 *
 * That the derivation eventually moves upstream is the plan
 * ([UI_BUILDER_CATALOG_CONTRACT.md](https://github.com/yschimke/compose-preview-server/blob/main/docs/design/UI_BUILDER_CATALOG_CONTRACT.md));
 * moving it *before* the server reads a published file at all would be a rewrite with nothing to
 * check it against. Publishing policy first is the step that can be proved equivalent, because the
 * server composes it with the same record it already derives from today.
 *
 * ### Every reader may ignore what it does not know
 *
 * The file is published once and read by builders of several vintages that the publisher cannot
 * upgrade — a deployment, a `serve` on a laptop, a local `compose-preview-server ui`, an editor
 * extension reading through one of those. [schema] refuses a future *major*; an unknown field never
 * fails a load, and an adapter or template role a build does not ship costs a placeholder and a log
 * line rather than the catalog.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderCatalogFile
internal constructor(
  public val schema: String = UI_BUILDER_CATALOG_SCHEMA,
  public val catalog: UiBuilderCatalogIdentity,
  /** Which record this file was generated against, so a consumer can tell they are a pair. */
  public val record: UiBuilderRecordRef,
  /**
   * Everything a builder reads, in the one place every existing reader already looks.
   *
   * Until compose-preview-contracts can carry typed fields on `CatalogCapabilityV1`, these ride in
   * `statusSemantics` exactly as `platform`, `previewSurfaces` and `componentMenu` already do.
   * Making them typed is the right change and is sequenced separately; it is not a prerequisite,
   * because every reader reads `statusSemantics` today.
   */
  public val statusSemantics: UiBuilderStatusSemantics,
  /**
   * What the generator noticed, in a stable, machine-readable vocabulary.
   *
   * Published in the file rather than only logged. A catalog's shelf is drawn from data now, and
   * the two questions somebody asks of it — "why is this component not on the shelf" and "why is
   * everything a placeholder" — have to be answerable from the artifact, by a person who was not
   * watching the build that produced it.
   */
  public val diagnostics: List<UiBuilderDiagnostic> = emptyList(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var catalog: UiBuilderCatalogIdentity,
    public var record: UiBuilderRecordRef,
    public var statusSemantics: UiBuilderStatusSemantics,
  ) {
    public var schema: String = UI_BUILDER_CATALOG_SCHEMA
    public var diagnostics: List<UiBuilderDiagnostic> = emptyList()

    public fun build(): UiBuilderCatalogFile =
      UiBuilderCatalogFile(schema, catalog, record, statusSemantics, diagnostics)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(catalog, record, statusSemantics).also {
      it.schema = schema
      it.diagnostics = diagnostics
    }
}

/** Who this catalog is, in the vocabulary the chooser and the pack merge read. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderCatalogIdentity
internal constructor(
  public val id: String,
  public val title: String,
  public val platform: String,
  public val platformLabel: String,
  /**
   * The Gradle module the record was discovered from, and its variant. Diagnostic, not identity.
   */
  public val module: String? = null,
  public val variant: String? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var id: String,
    public var title: String,
    public var platform: String,
    public var platformLabel: String,
  ) {
    public var module: String? = null
    public var variant: String? = null

    public fun build(): UiBuilderCatalogIdentity =
      UiBuilderCatalogIdentity(id, title, platform, platformLabel, module, variant)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(id, title, platform, platformLabel).also {
      it.module = module
      it.variant = variant
    }
}

/** The component record this file is the policy half of. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderRecordRef
internal constructor(
  public val file: String = UI_BUILDER_RECORD_FILE,
  public val schemaVersion: Int,
  public val components: Int,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var schemaVersion: Int,
    public var components: Int,
  ) {
    public var file: String = UI_BUILDER_RECORD_FILE

    public fun build(): UiBuilderRecordRef = UiBuilderRecordRef(file, schemaVersion, components)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(schemaVersion, components).also { it.file = file }
}

/** Everything a builder reads about the catalog, carried where every reader already looks. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderStatusSemantics
internal constructor(
  public val platform: String,
  public val platformLabel: String,
  /**
   * The resolved prefix every derived builder id carries — `componentIdPrefix`, or `<catalogId>/`.
   *
   * Published because it is the only way a consumer can name a component this file says nothing
   * about. An unannotated record component is deliberately absent from [components] and still
   * belongs on the shelf, so its id has to be DERIVABLE: without this a consumer holding
   * m3-catalog's record has to guess between `m3/card` and `m3-catalog/card`, and guessing wrong
   * changes the identity every saved design stores for most of the default shelf.
   */
  public val componentIdPrefix: String,
  public val previewSurfaces: JsonElement? = null,
  public val browserPreview: JsonElement? = null,
  public val componentMenu: UiBuilderComponentMenu,
  public val frame: JsonElement? = null,
  public val code: UiBuilderCode? = null,
  /**
   * Catalog-declared, versioned Compose source adapter; lifted to the wire capability by a host.
   */
  public val composeSourceExport: UiBuilderComposeSourceExport? = null,
  /** Branch-relative paths of the template designs, which every existing reader takes as such. */
  public val templates: List<String> = emptyList(),
  /**
   * What the New design chooser says about this catalog and its templates, beside [templates]
   * rather than in it so a reader of the paths is unaffected. Null when the policy authored none.
   */
  public val newDesign: UiBuilderNewDesignSemantics? = null,
  public val colorTokens: JsonElement? = null,
  public val assetRegistry: JsonElement? = null,
  /** Successor rules interpreted by a catalog-upgrade-aware builder. */
  public val supersedes: JsonElement? = null,
  public val builtins: Map<String, UiBuilderBuiltin> = emptyMap(),
  /** Per-component policy, keyed by builder id. The record beside this file is the inventory. */
  public val components: Map<String, UiBuilderComponentPolicy> = emptyMap(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var platform: String,
    public var platformLabel: String,
    public var componentIdPrefix: String,
    public var componentMenu: UiBuilderComponentMenu,
  ) {
    public var previewSurfaces: JsonElement? = null
    public var browserPreview: JsonElement? = null
    public var frame: JsonElement? = null
    public var code: UiBuilderCode? = null
    public var composeSourceExport: UiBuilderComposeSourceExport? = null
    public var templates: List<String> = emptyList()
    public var newDesign: UiBuilderNewDesignSemantics? = null
    public var colorTokens: JsonElement? = null
    public var assetRegistry: JsonElement? = null
    public var supersedes: JsonElement? = null
    public var builtins: Map<String, UiBuilderBuiltin> = emptyMap()
    public var components: Map<String, UiBuilderComponentPolicy> = emptyMap()

    public fun build(): UiBuilderStatusSemantics =
      UiBuilderStatusSemantics(
        platform,
        platformLabel,
        componentIdPrefix,
        previewSurfaces,
        browserPreview,
        componentMenu,
        frame,
        code,
        composeSourceExport,
        templates,
        newDesign,
        colorTokens,
        assetRegistry,
        supersedes,
        builtins,
        components,
      )
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(platform, platformLabel, componentIdPrefix, componentMenu).also {
      it.previewSurfaces = previewSurfaces
      it.browserPreview = browserPreview
      it.frame = frame
      it.code = code
      it.composeSourceExport = composeSourceExport
      it.templates = templates
      it.newDesign = newDesign
      it.colorTokens = colorTokens
      it.assetRegistry = assetRegistry
      it.supersedes = supersedes
      it.builtins = builtins
      it.components = components
    }
}

/** Group order, and the group each policy-carrying component belongs to. */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderComponentMenu
internal constructor(
  public val groupOrder: List<String> = emptyList(),
  public val components: Map<String, UiBuilderMenuEntry> = emptyMap(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var groupOrder: List<String> = emptyList()
    public var components: Map<String, UiBuilderMenuEntry> = emptyMap()

    public fun build(): UiBuilderComponentMenu = UiBuilderComponentMenu(groupOrder, components)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.groupOrder = groupOrder
      it.components = components
    }
}

@Serializable
@ConsistentCopyVisibility
public data class UiBuilderMenuEntry internal constructor(public val group: String) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var group: String) {

    public fun build(): UiBuilderMenuEntry = UiBuilderMenuEntry(group)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(group)
}

/**
 * One component's builder policy as published: [BuilderPolicy], resolved, plus the join back to the
 * record.
 *
 * [record] is the load-bearing field. It is the record's `canonicalId`, so a consumer holding
 * `components.json` and this file can pair a builder id with the signature, the slots and the call
 * site it stands for — without either file restating the other.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderComponentPolicy
internal constructor(
  /** The record's `canonicalId` — `<module>/<jvmOwner>.<name>`. */
  public val record: String,
  /** The catalog identity this component publishes under, when it has one. */
  public val catalogId: String? = null,
  public val displayName: String? = null,
  public val canvas: String? = null,
  public val canvasMapping: JsonElement? = null,
  /**
   * The layout the editing canvas draws while an author is inside this component — see
   * [UiBuilderUnrolledMock].
   *
   * Absent, which is every component today, keeps the component's own layout while editing. Carried
   * onto the wire under the component's `wasm` block, which is where the builder reads it.
   */
  public val unrolled: UiBuilderUnrolledMock? = null,
  public val nativeOnly: Boolean = false,
  public val traits: List<String> = emptyList(),
  public val slots: Map<String, List<String>> = emptyMap(),
  public val stateCallbacks: Map<String, String> = emptyMap(),
  public val starter: Map<String, String> = emptyMap(),
  public val variantProperty: String? = null,
  public val variants: Map<String, String> = emptyMap(),
  /** Present only when the component is kept off the shelf; the value is the stated reason. */
  public val excluded: String? = null,
  /**
   * The vocabulary the catalog states for this component — see `UiBuilderAuthoredComponent`.
   *
   * Raw JSON, carried rather than modelled: the shape is the UI builder's and the preview server
   * validates it. Absent when the catalog states nothing, which is not the same as an empty list.
   */
  public val propertyCapabilities: List<JsonElement>? = null,
  public val slotCapabilities: List<JsonElement>? = null,
  public val modifierCapabilities: List<String>? = null,
  /** See `UiBuilderAuthoredComponent.insertContent`; only ever authored, never derived. */
  public val insertContent: JsonElement? = null,
  /** See `UiBuilderAuthoredComponent.shelfRole`; only ever authored, never derived. */
  public val shelfRole: String? = null,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(public var record: String) {
    public var catalogId: String? = null
    public var displayName: String? = null
    public var canvas: String? = null
    public var canvasMapping: JsonElement? = null
    public var unrolled: UiBuilderUnrolledMock? = null
    public var nativeOnly: Boolean = false
    public var traits: List<String> = emptyList()
    public var slots: Map<String, List<String>> = emptyMap()
    public var stateCallbacks: Map<String, String> = emptyMap()
    public var starter: Map<String, String> = emptyMap()
    public var variantProperty: String? = null
    public var variants: Map<String, String> = emptyMap()
    public var excluded: String? = null
    public var propertyCapabilities: List<JsonElement>? = null
    public var slotCapabilities: List<JsonElement>? = null
    public var modifierCapabilities: List<String>? = null
    public var insertContent: JsonElement? = null
    public var shelfRole: String? = null

    public fun build(): UiBuilderComponentPolicy =
      UiBuilderComponentPolicy(
        record,
        catalogId,
        displayName,
        canvas,
        canvasMapping,
        unrolled,
        nativeOnly,
        traits,
        slots,
        stateCallbacks,
        starter,
        variantProperty,
        variants,
        excluded,
        propertyCapabilities,
        slotCapabilities,
        modifierCapabilities,
        insertContent,
        shelfRole,
      )
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(record).also {
      it.catalogId = catalogId
      it.displayName = displayName
      it.canvas = canvas
      it.canvasMapping = canvasMapping
      it.unrolled = unrolled
      it.nativeOnly = nativeOnly
      it.traits = traits
      it.slots = slots
      it.stateCallbacks = stateCallbacks
      it.starter = starter
      it.variantProperty = variantProperty
      it.variants = variants
      it.excluded = excluded
      it.propertyCapabilities = propertyCapabilities
      it.slotCapabilities = slotCapabilities
      it.modifierCapabilities = modifierCapabilities
      it.insertContent = insertContent
      it.shelfRole = shelfRole
    }
}

/**
 * Something the generator noticed, addressed to a person reading the published file.
 *
 * [code] is a stable slug so a gate can assert on it; [subject] is the component, builtin or field
 * it is about; [message] is for the person. Never a build failure on its own — a catalog that is
 * half-annotated is a catalog in progress, and refusing to publish it would leave the author with
 * nothing to look at.
 */
@Serializable
@ConsistentCopyVisibility
public data class UiBuilderDiagnostic
internal constructor(
  public val code: String,
  public val subject: String,
  public val message: String,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var code: String,
    public var subject: String,
    public var message: String,
  ) {

    public fun build(): UiBuilderDiagnostic = UiBuilderDiagnostic(code, subject, message)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(code, subject, message)
}
