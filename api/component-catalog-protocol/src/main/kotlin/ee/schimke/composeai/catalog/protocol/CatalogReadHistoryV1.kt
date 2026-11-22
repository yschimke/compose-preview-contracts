@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ee.schimke.composeai.catalog.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * The body of `GET` [CatalogReadRoutesV1.HISTORY_PATH] (`catalog-read` level 2): where one
 * catalog's render history lives, and which delivery commit the server is serving.
 *
 * [mode] picks the source, in the precedence the server's in-process MCP uses: the catalog's
 * delivery branch ([MODE_PUBLISHED]), else the server's own checkout ([MODE_LOCAL]), else none
 * ([MODE_NONE], with a [reason]). Carries an `ETag`; see [CatalogReadRoutesV1.ETAG_HEADER].
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadHistoryV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  /** [MODE_PUBLISHED], [MODE_LOCAL] or [MODE_NONE]. */
  public val mode: String,
  /** The delivery branch's repository, `owner/name`; [MODE_PUBLISHED] only. */
  @EncodeDefault public val repo: String? = null,
  /** The delivery branch; [MODE_PUBLISHED] only. */
  @EncodeDefault public val branch: String? = null,
  /**
   * The served delivery commit (the catalog's `provenance.commit`), the same value as
   * [CatalogReadPreviewsV1.revision]. Null when the catalog names none.
   */
  @EncodeDefault public val commit: String? = null,
  /**
   * Where the branch's `history.json` would be, at the branch head; null when no URL can be built
   * from [repo] and [branch]. Its format is compose-ai-tools', not this contract.
   */
  @EncodeDefault public val manifestUrl: String? = null,
  /**
   * A URL template for one historical render, with [COMMIT_PLACEHOLDER] and [PATH_PLACEHOLDER] to
   * substitute: a [CatalogReadHistoryVersionV1.commit] and [CatalogReadPreviewHistoryV1.path].
   */
  @EncodeDefault public val renderUrlTemplate: String? = null,
  /**
   * Whether the served tree carried a `history.json`, so [CatalogReadRoutesV1.PREVIEW_HISTORY_PATH]
   * can answer with timelines.
   */
  @EncodeDefault public val publishesManifest: Boolean = false,
  /** Why there is no history ([MODE_NONE]); null otherwise. */
  @EncodeDefault public val reason: String? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(mode: String) {
    public var schema: String = SCHEMA
    public var mode: String = mode
    public var repo: String? = null
    public var branch: String? = null
    public var commit: String? = null
    public var manifestUrl: String? = null
    public var renderUrlTemplate: String? = null
    public var publishesManifest: Boolean = false
    public var reason: String? = null

    public fun build(): CatalogReadHistoryV1 =
      CatalogReadHistoryV1(
        schema,
        mode,
        repo,
        branch,
        commit,
        manifestUrl,
        renderUrlTemplate,
        publishesManifest,
        reason,
      )
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(mode).also {
      it.schema = schema
      it.repo = repo
      it.branch = branch
      it.commit = commit
      it.manifestUrl = manifestUrl
      it.renderUrlTemplate = renderUrlTemplate
      it.publishesManifest = publishesManifest
      it.reason = reason
    }

  public companion object {
    public const val SCHEMA: String = "compose-preview-serve/history/v1"

    /** The catalog's delivery branch: [repo], [branch] and [commit] are set. */
    public const val MODE_PUBLISHED: String = "published"

    /** The server's own checkout (project mode); renders are served by the server itself. */
    public const val MODE_LOCAL: String = "local"

    /** No history to read; [reason] says why. */
    public const val MODE_NONE: String = "none"

    /** The commit placeholder in [renderUrlTemplate]. */
    public const val COMMIT_PLACEHOLDER: String = "{commit}"

    /** The render-path placeholder in [renderUrlTemplate]. */
    public const val PATH_PLACEHOLDER: String = "{path}"
  }
}

/**
 * The body of `GET` [CatalogReadRoutesV1.PREVIEW_HISTORY_PATH] (`catalog-read` level 2): one
 * preview's indexed render timeline, in all three [CatalogReadHistoryV1] modes.
 *
 * The provenance fields ([mode] to [renderUrlTemplate]) are the catalog's, as on
 * [CatalogReadHistoryV1]. With a timeline, [path] and [versions] are set and [reason] is null.
 * Without one, [versions] is null (never "empty, so unchanged") and [reason] says why, except in
 * [CatalogReadHistoryV1.MODE_LOCAL] with fewer than two distinct renders, where [versions] is empty
 * and [reason] says so. An unknown preview is `404`, text `no such preview`.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadPreviewHistoryV1
internal constructor(
  @EncodeDefault public val schema: String = SCHEMA,
  public val previewId: String,
  /** As [CatalogReadHistoryV1.mode]. */
  public val mode: String,
  @EncodeDefault public val repo: String? = null,
  @EncodeDefault public val branch: String? = null,
  /** The served delivery commit: the commit a timeline is pinned to when [path] is set. */
  @EncodeDefault public val commit: String? = null,
  @EncodeDefault public val manifestUrl: String? = null,
  @EncodeDefault public val renderUrlTemplate: String? = null,
  /** Why there is no timeline to show; null when [versions] is one. */
  @EncodeDefault public val reason: String? = null,
  /** The render's path in the delivery tree, for [CatalogReadHistoryV1.PATH_PLACEHOLDER]. */
  @EncodeDefault public val path: String? = null,
  /** How many publishes the timeline observed. */
  @EncodeDefault public val observations: Int? = null,
  /**
   * Whether the render keeps returning to bytes it had moved away from, on publishes that did not
   * change it: a byte difference between two [versions] is then not evidence of a real change.
   */
  @EncodeDefault public val unstable: Boolean? = null,
  /** How many times it returned. */
  @EncodeDefault public val flapCount: Int? = null,
  /** The distinct renders, newest first; null when there is no timeline to list. */
  @EncodeDefault public val versions: List<CatalogReadHistoryVersionV1>? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(previewId: String, mode: String) {
    public var schema: String = SCHEMA
    public var previewId: String = previewId
    public var mode: String = mode
    public var repo: String? = null
    public var branch: String? = null
    public var commit: String? = null
    public var manifestUrl: String? = null
    public var renderUrlTemplate: String? = null
    public var reason: String? = null
    public var path: String? = null
    public var observations: Int? = null
    public var unstable: Boolean? = null
    public var flapCount: Int? = null
    public var versions: List<CatalogReadHistoryVersionV1>? = null

    public fun build(): CatalogReadPreviewHistoryV1 =
      CatalogReadPreviewHistoryV1(
        schema,
        previewId,
        mode,
        repo,
        branch,
        commit,
        manifestUrl,
        renderUrlTemplate,
        reason,
        path,
        observations,
        unstable,
        flapCount,
        versions,
      )
  }

  /** This body as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(previewId, mode).also {
      it.schema = schema
      it.repo = repo
      it.branch = branch
      it.commit = commit
      it.manifestUrl = manifestUrl
      it.renderUrlTemplate = renderUrlTemplate
      it.reason = reason
      it.path = path
      it.observations = observations
      it.unstable = unstable
      it.flapCount = flapCount
      it.versions = versions
    }

  public companion object {
    public const val SCHEMA: String = "compose-preview-serve/preview-history/v1"
  }
}

/**
 * One distinct render on [CatalogReadPreviewHistoryV1.versions]. Two versions with the same [blob]
 * have the same bytes: that equality is what "changed" means between them.
 */
@Serializable
@ConsistentCopyVisibility
public data class CatalogReadHistoryVersionV1
internal constructor(
  /**
   * The delivery commit this version is addressed by, for
   * [CatalogReadHistoryV1.COMMIT_PLACEHOLDER].
   */
  public val commit: String,
  /** That commit's date, ISO-8601. */
  public val date: String,
  /** The render's git blob sha. */
  public val blob: String,
  /** How many adjacent delivery commits with identical bytes collapsed into this version. */
  public val commits: Int,
  /** The source commit the producer rendered from, where the history manifest records one. */
  @EncodeDefault public val sourceSha: String? = null,
  /** The history manifest's `sinceCommit` for this version, where it records one. */
  @EncodeDefault public val sinceCommit: String? = null,
  /** The history manifest's `occurrences` for this version (repeats of these bytes), if any. */
  @EncodeDefault public val occurrences: Int? = null,
  /**
   * Where these exact bytes are served. In [CatalogReadHistoryV1.MODE_PUBLISHED] an absolute
   * `raw.githubusercontent.com` URL pinned to [commit]; in [CatalogReadHistoryV1.MODE_LOCAL] a path
   * on the answering server (`/history/render/<blob>.png`), to resolve against its origin.
   */
  public val renderUrl: String,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` in `ui-builder-protocol` for why
   * the constructor is internal.
   */
  public class Builder(commit: String, date: String, blob: String, renderUrl: String) {
    public var commit: String = commit
    public var date: String = date
    public var blob: String = blob
    public var commits: Int = 1
    public var sourceSha: String? = null
    public var sinceCommit: String? = null
    public var occurrences: Int? = null
    public var renderUrl: String = renderUrl

    public fun build(): CatalogReadHistoryVersionV1 =
      CatalogReadHistoryVersionV1(
        commit,
        date,
        blob,
        commits,
        sourceSha,
        sinceCommit,
        occurrences,
        renderUrl,
      )
  }

  /** This value as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(commit, date, blob, renderUrl).also {
      it.commits = commits
      it.sourceSha = sourceSha
      it.sinceCommit = sinceCommit
      it.occurrences = occurrences
    }
}
