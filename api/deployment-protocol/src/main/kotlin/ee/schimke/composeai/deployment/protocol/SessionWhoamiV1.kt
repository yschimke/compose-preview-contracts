package ee.schimke.composeai.deployment.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The `200` reply of `GET` [PATH] on the auth host: who a browser's signed-in session speaks for.
 *
 * A sibling container (the UI builder, a hosted MCP) authenticates a browser by forwarding that
 * request's `Cookie` header, unchanged, to [PATH] on the auth host over the internal network,
 * instead of sharing the cookie secret and verifying the cookie itself:
 *
 * - `200` with this body: a live session.
 * - `401`: no live session (no cookie, a forged or expired one, or more than one). The body is not
 *   JSON and is not part of the contract.
 * - Any other status, or no answer: the auth host could not say. The sibling fails closed.
 *
 * The reply is always [CACHE_CONTROL]; the sibling must not cache it beyond the request it is
 * answering either. The preview server's reply today, `{"login":…,"guest":…}`, decodes as one.
 * Decode with `ignoreUnknownKeys = true`. See DEPLOYMENT_PROTOCOL.md.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
public data class SessionWhoamiV1
internal constructor(
  /** The GitHub login the session was signed in as. */
  public val login: String,
  /**
   * True when the visitor signed in outside the host's allowlist and is served as a guest. The
   * preview server always sends it; absent reads as a member.
   */
  public val guest: Boolean = false,
  /**
   * What the session may do, as an open vocabulary the auth host defines. Empty from a host that
   * does not report it, which says nothing about what the session may do — not that it may do
   * nothing.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val scopes: List<String> = emptyList(),
  /**
   * When the session stops being live, in seconds since the epoch; null when unreported. A sibling
   * may use it to bound its own short-lived state, never to extend a session.
   */
  @EncodeDefault(EncodeDefault.Mode.NEVER) public val expiresAtEpochSeconds: Long? = null,
) {
  /**
   * Additive construction API; see `CatalogCapabilityV1.Builder` for why the constructor is
   * internal.
   */
  public class Builder(login: String) {
    public var login: String = login
    public var guest: Boolean = false
    public var scopes: List<String> = emptyList()
    public var expiresAtEpochSeconds: Long? = null

    public fun build(): SessionWhoamiV1 =
      SessionWhoamiV1(login, guest, scopes, expiresAtEpochSeconds)
  }

  /** This reply as a [Builder]. Replaces `copy`. */
  public fun newBuilder(): Builder =
    Builder(login).also {
      it.guest = guest
      it.scopes = scopes
      it.expiresAtEpochSeconds = expiresAtEpochSeconds
    }

  public companion object {
    /** Where the auth host answers. The preview server's `ServeGithubAuth.WHOAMI_PATH`. */
    public const val PATH: String = "/auth/github/whoami"

    /** The `Cache-Control` value the reply carries, whatever its status. */
    public const val CACHE_CONTROL: String = "no-store"
  }
}
