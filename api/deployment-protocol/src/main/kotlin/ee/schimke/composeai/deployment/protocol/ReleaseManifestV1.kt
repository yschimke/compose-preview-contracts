package ee.schimke.composeai.deployment.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * What one component release says about itself: the API levels it provides, the levels it needs
 * from the components beside it, the files it attaches and the container image it ships.
 *
 * Published as [FILE] beside a release's assets and, for a component that ships an archive, at the
 * archive root. It generalises the UI-builder editor archive's `ui-builder-web.json`
 * (`compose-ui-builder-web/v1`), which stays exactly as it is: that file's `serverApi` is
 * `provides[`[DeploymentApisV1.UI_BUILDER_EDITOR]`]` here and its `rootOffline` is
 * `provides[`[DeploymentApisV1.UI_BUILDER_ROOT_OFFLINE]`]`. See DEPLOYMENT_PROTOCOL.md.
 *
 * Decode with `ignoreUnknownKeys = true`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class ReleaseManifestV1
internal constructor(
  /** Always [SCHEMA]. */
  public val schema: String,
  /** Which component this is a release of ([DeploymentComponentsV1]). */
  public val component: String,
  /** The released version, as the release tag names it, without a `v`. */
  public val version: String,
  /** The API levels this release speaks, by [DeploymentApisV1] name. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val provides: Map<String, Int> = emptyMap(),
  /**
   * The API levels this release needs from its siblings, by [DeploymentApisV1] name: met when a
   * deployed component's `provides` (or `/version` `apis`) names that API at a level inside the
   * range.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  public val requires: Map<String, ApiRangeV1> = emptyMap(),
  /** The files attached to the release, each with its digest. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val assets: List<ReleaseAssetV1> = emptyList(),
  /** The container image this release ships; null for a component that ships none. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val image: ReleaseImageV1? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` for why the constructor is
   * internal.
   */
  public class Builder(component: String, version: String) {
    public var component: String = component
    public var version: String = version
    public var provides: Map<String, Int> = emptyMap()
    public var requires: Map<String, ApiRangeV1> = emptyMap()
    public var assets: List<ReleaseAssetV1> = emptyList()
    public var image: ReleaseImageV1? = null

    public fun build(): ReleaseManifestV1 =
      ReleaseManifestV1(SCHEMA, component, version, provides, requires, assets, image)
  }

  /** This manifest as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(component, version).also {
      it.provides = provides
      it.requires = requires
      it.assets = assets
      it.image = image
    }

  public companion object {
    public const val SCHEMA: String = "compose-preview/release-manifest/v1"

    /** The manifest's file name, as a release asset and at an archive root. */
    public const val FILE: String = "compose-preview-release.json"
  }
}

/** An inclusive range of API levels, [min] to [max]. */
@Serializable
@ConsistentCopyVisibility
public data class ApiRangeV1
internal constructor(
  /** The lowest level accepted, inclusive. */
  public val min: Int,
  /** The highest level accepted, inclusive. */
  public val max: Int,
) {
  /** Additive construction API, as [ReleaseManifestV1.Builder]. */
  public class Builder(min: Int, max: Int) {
    public var min: Int = min
    public var max: Int = max

    public fun build(): ApiRangeV1 = ApiRangeV1(min, max)
  }

  /** This range as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(min, max)
}

/** One file attached to a release. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class ReleaseAssetV1
internal constructor(
  /** The asset's file name on the release, e.g. `compose-preview-ui-builder-web-3.48.0.zip`. */
  public val name: String,
  /** Lower-case hex SHA-256 of the asset's bytes, 64 characters. */
  public val sha256: String,
  /** The asset's media type, e.g. `application/zip`; null when unstated. */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val mediaType: String? = null,
) {
  /** Additive construction API, as [ReleaseManifestV1.Builder]. */
  public class Builder(name: String, sha256: String) {
    public var name: String = name
    public var sha256: String = sha256
    public var mediaType: String? = null

    public fun build(): ReleaseAssetV1 = ReleaseAssetV1(name, sha256, mediaType)
  }

  /** This asset as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(name, sha256).also { it.mediaType = mediaType }
}

/** The container image a release ships. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class ReleaseImageV1
internal constructor(
  /** The image repository without a tag, e.g. `ghcr.io/yschimke/compose-preview-host`. */
  public val repository: String,
  /** The tag the release pushed, normally the release version. */
  public val tag: String,
  /**
   * The manifest digest the tag pointed at when the release was cut (`sha256:<hex>`). A deploy tool
   * pins this rather than the tag when it is present; null when unknown.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val digest: String? = null,
) {
  /** Additive construction API, as [ReleaseManifestV1.Builder]. */
  public class Builder(repository: String, tag: String) {
    public var repository: String = repository
    public var tag: String = tag
    public var digest: String? = null

    public fun build(): ReleaseImageV1 = ReleaseImageV1(repository, tag, digest)
  }

  /** This image as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder = Builder(repository, tag).also { it.digest = digest }
}
