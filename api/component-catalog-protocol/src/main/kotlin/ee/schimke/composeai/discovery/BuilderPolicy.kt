package ee.schimke.composeai.discovery

import kotlinx.serialization.Serializable

/**
 * One `key=value` pair from a `@BuilderComponent` array parameter, split on the **first** `=` so a
 * value may contain one.
 *
 * The same shape and the same reason as a catalog variant's props: annotations cannot hold a `Map`,
 * and splitting once at discovery beats every consumer growing its own splitter.
 */
@Serializable
@ConsistentCopyVisibility
public data class BuilderPair
internal constructor(
  public val key: String,
  public val value: String,
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder(
    public var key: String,
    public var value: String,
  ) {

    public fun build(): BuilderPair = BuilderPair(key, value)
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(key, value)
}

/**
 * UI-builder policy for one component, discovered from `@BuilderComponent` in the
 * `preview-annotations` artifact — what a catalog says about how its component behaves in a drawing
 * tool, as opposed to what its signature already says.
 *
 * The component record is derived: parameters, slots, call site and opt-in markers all come from
 * `@kotlin.Metadata` or from discovery's inference, so nothing in it is typed twice. This is the
 * residue that no signature holds — that `onCheckedChange` updates a `checked` state rather than
 * merely firing, that a new instance should arrive with a label in it, that the canvas may draw
 * this one through a real adapter and must draw that one as a placeholder. It used to be written in
 * Kotlin in the preview server, per catalog, by hand; the contract that moves it here is
 * [UI_BUILDER_CATALOG_CONTRACT.md](https://github.com/yschimke/compose-preview-server/blob/main/docs/design/UI_BUILDER_CATALOG_CONTRACT.md).
 *
 * ### One type, two files
 *
 * Declared in the shared source `preview-discovery` and `:screen-model` both compile, so
 * `previews.json` ([PreviewInfo.builder], where the annotation lands) and `components.json`
 * ([ComponentRecord.builder], where a consumer reads it) carry the *same* type rather than a
 * manifest shape and a record shape that mean the same thing and drift apart on their third field.
 *
 * ### Nothing is defaulted on the catalog's behalf
 *
 * A blank annotation argument records `null`, not the value a generator would pick: "the catalog
 * did not say" and "the catalog said `placeholder`" are different facts, and a report of which
 * components nobody has looked at is only true if the record can tell them apart. Every consumer
 * applies its own defaults on read, and says so.
 */
@Serializable
@ConsistentCopyVisibility
public data class BuilderPolicy
internal constructor(
  /**
   * `@BuilderComponent.id` — the id this component is known by in the builder, and the string a
   * saved design stores in a node. Null derives one from the catalog identity.
   */
  public val id: String? = null,
  /**
   * `@BuilderComponent.component` — which component this policy is about, when its sticker renders
   * several. A callable FQN or a simple name; null when the sticker renders one.
   */
  public val component: String? = null,
  /**
   * The `@CatalogComponent.id` of the sticker that declared this policy.
   *
   * A derived builder id is a slug of the catalog identity, and one callable is routinely published
   * under several — `Button/Filled` and `Button/Tonal` are two stickers over one `Button`, and the
   * record's `componentIds` is the sorted union across every preview. Deriving from the first of
   * that list would hand a policy declared on `Button/Tonal` the identity `…/filled`, which is the
   * string every saved design then stores. So the declaring binding is recorded and used.
   */
  public val declaredForCatalogId: String? = null,
  /** Insert-panel group override; null keeps the catalog's own `@CatalogGroup`. */
  public val group: String? = null,
  /** Insert-panel label; null derives one from the id's last segment. */
  public val displayName: String? = null,
  /**
   * Canvas adapter id this catalog claims for the component, or `"placeholder"`.
   *
   * Null means the catalog did not say, which a builder treats as placeholder and a generator
   * reports as unclaimed — a shelf drawn entirely in placeholders is a fact worth being able to
   * see. A builder that ships no adapter by this name draws the placeholder and logs it once; it
   * never refuses the catalog, because the file is published once and read by builders of several
   * vintages that the publisher cannot upgrade.
   */
  public val canvas: String? = null,
  /**
   * `<callback>=<state>:<type>` — the lambdas that update state rather than merely firing, so an
   * export hoists a `remember` above the call instead of emitting a picture of a checkbox.
   */
  public val stateCallbacks: List<BuilderPair> = emptyList(),
  /** `<parameter>=<value>` — what a freshly inserted instance arrives holding. */
  public val starter: List<BuilderPair> = emptyList(),
  /**
   * `<slot>=<traits>`, `|`-separated — what each of the component's slots accepts. The record
   * already says which parameters *are* slots; this says what each will take, which is a design
   * decision rather than a type.
   */
  public val slots: List<BuilderPair> = emptyList(),
  /**
   * Traits this component offers to other components' slots. Deliberately free-form: slot
   * acceptance is a within-catalog relation and no reader compares traits across catalogs.
   */
  public val traits: List<String> = emptyList(),
  /** The parameter whose value drives the builder's variant control, when it has one. */
  public val variantProperty: String? = null,
  /** `<label>=<value>` variant-control entries; empty offers the property's own allowed values. */
  public val variants: List<BuilderPair> = emptyList(),
  /**
   * Renders only in the native lane. The builder still offers, exports and natively renders it; the
   * canvas shows a placeholder that says so rather than one that looks like a failure. Distinct
   * from a null [canvas], which means nobody has claimed an adapter for it yet.
   */
  public val nativeOnly: Boolean = false,
  /** Why this component is kept off the builder's shelf; null leaves the pack rules to decide. */
  public val exclude: String? = null,
  /**
   * The preview ids that declared this policy, in order.
   *
   * A component is routinely rendered by several previews, and any of them may carry the
   * annotation. Recording who declared it makes two things answerable that a merged policy
   * otherwise hides: which sticker to edit, and — when [conflicting] is non-empty — that more than
   * one sticker declared a *different* policy for the same component.
   */
  public val declaredBy: List<String> = emptyList(),
  /**
   * Preview ids whose declared policy disagreed with this one and was dropped.
   *
   * Empty in the ordinary case, including the case where several previews declare the *same*
   * policy. Non-empty means one component's stickers contradict each other and the lowest preview
   * id won — recorded rather than resolved silently, because the resolution is arbitrary and the
   * disagreement is the thing somebody needs to fix.
   */
  public val conflicting: List<String> = emptyList(),
  /**
   * Other components the sticker renders that this policy could equally have been about.
   *
   * Empty in the ordinary case. Non-empty means the sticker renders several components, the
   * annotation named none of them with `component`, and the policy was bound to the first — a guess
   * discovery is honest about rather than a rule. The generator reports it and names the fix.
   */
  public val ambiguousWith: List<String> = emptyList(),
  /**
   * `key=value` entries the annotation carried that could not be read, verbatim.
   *
   * A malformed entry costs that entry rather than the build, and the reason that is an acceptable
   * trade is that it is *reported*. Dropping the raw string at the point of the split would leave
   * nothing to report with, and the component would keep a default nobody meant it to have with no
   * symptom at all — which is the failure the leniency was supposed to be cheaper than.
   */
  public val malformed: List<String> = emptyList(),
) {
  /** Additive construction API; future optional fields do not replace a public constructor. */
  public class Builder {
    public var id: String? = null
    public var component: String? = null
    public var declaredForCatalogId: String? = null
    public var group: String? = null
    public var displayName: String? = null
    public var canvas: String? = null
    public var stateCallbacks: List<BuilderPair> = emptyList()
    public var starter: List<BuilderPair> = emptyList()
    public var slots: List<BuilderPair> = emptyList()
    public var traits: List<String> = emptyList()
    public var variantProperty: String? = null
    public var variants: List<BuilderPair> = emptyList()
    public var nativeOnly: Boolean = false
    public var exclude: String? = null
    public var declaredBy: List<String> = emptyList()
    public var conflicting: List<String> = emptyList()
    public var ambiguousWith: List<String> = emptyList()
    public var malformed: List<String> = emptyList()

    public fun build(): BuilderPolicy =
      BuilderPolicy(
        id,
        component,
        declaredForCatalogId,
        group,
        displayName,
        canvas,
        stateCallbacks,
        starter,
        slots,
        traits,
        variantProperty,
        variants,
        nativeOnly,
        exclude,
        declaredBy,
        conflicting,
        ambiguousWith,
        malformed,
      )
  }

  /** This value as a [Builder], for deriving a modified one. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder().also {
      it.id = id
      it.component = component
      it.declaredForCatalogId = declaredForCatalogId
      it.group = group
      it.displayName = displayName
      it.canvas = canvas
      it.stateCallbacks = stateCallbacks
      it.starter = starter
      it.slots = slots
      it.traits = traits
      it.variantProperty = variantProperty
      it.variants = variants
      it.nativeOnly = nativeOnly
      it.exclude = exclude
      it.declaredBy = declaredBy
      it.conflicting = conflicting
      it.ambiguousWith = ambiguousWith
      it.malformed = malformed
    }
}
