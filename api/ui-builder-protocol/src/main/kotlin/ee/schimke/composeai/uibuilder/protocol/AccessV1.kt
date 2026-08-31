package ee.schimke.composeai.uibuilder.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Persistent authorization metadata, revisioned independently from the design document. */
@Serializable
public data class DesignAccessControlV1(
  public val accessRevision: Long,
  public val ownerActorId: String,
  public val actorGrants: List<DesignActorGrantV1> = emptyList(),
  public val shareLinks: List<DesignShareLinkV1> = emptyList(),
)

/** Access granted to one authenticated actor. The owner is not duplicated in this list. */
@Serializable
public data class DesignActorGrantV1(
  public val actorId: String,
  public val role: DesignAccessRoleV1,
  /** Authoritative permissions; [role] is a stable presentation/audit label only. */
  public val allowedActions: List<DesignAccessActionV1>,
  public val grantedByActorId: String,
  public val grantedAtEpochMillis: Long,
)

/**
 * Persistent bearer-link grant. [shareId] is an opaque, unguessable identifier and must be treated
 * as a secret by transports and clients.
 */
@Serializable
public data class DesignShareLinkV1(
  public val shareId: String,
  public val role: DesignAccessRoleV1,
  /** Authoritative permissions; [role] does not implicitly add any action. */
  public val allowedActions: List<DesignAccessActionV1>,
  public val createdByActorId: String,
  public val createdAtEpochMillis: Long,
  public val expiresAtEpochMillis: Long? = null,
  public val revokedAtEpochMillis: Long? = null,
)

@Serializable
public enum class DesignAccessRoleV1 {
  @SerialName("owner") OWNER,
  @SerialName("editor") EDITOR,
  @SerialName("viewer") VIEWER,
}

@Serializable
public enum class DesignAccessActionV1 {
  @SerialName("read") READ,
  @SerialName("write") WRITE,
  @SerialName("export") EXPORT,
  @SerialName("manageAccess") MANAGE_ACCESS,
  @SerialName("delete") DELETE,
}

/** Requester-specific effective access returned by list operations. */
@Serializable
public data class DesignActorAccessV1(
  public val actorId: String,
  public val role: DesignAccessRoleV1,
  public val allowedActions: List<DesignAccessActionV1>,
)

/** Compact, lossless listing metadata without embedding the potentially large semantic tree. */
@Serializable
public data class DesignListItemV1(
  public val designId: String,
  public val title: String,
  public val revision: Long,
  public val accessRevision: Long,
  public val catalogPin: CatalogReferenceV1,
  public val createdAtEpochMillis: Long? = null,
  public val updatedAtEpochMillis: Long? = null,
  public val ownerActorId: String,
  public val requesterAccess: DesignActorAccessV1,
)

/** One atomic access-control mutation. Audit actor/timestamp fields are assigned by the service. */
@Serializable public sealed interface DesignAccessMutationV1

@Serializable
@SerialName("grantActor")
public data class GrantActorAccessMutationV1(
  public val actorId: String,
  public val role: DesignAccessRoleV1,
  public val allowedActions: List<DesignAccessActionV1>,
) : DesignAccessMutationV1

@Serializable
@SerialName("revokeActor")
public data class RevokeActorAccessMutationV1(public val actorId: String) : DesignAccessMutationV1

@Serializable
@SerialName("createShareLink")
public data class CreateDesignShareLinkMutationV1(
  public val role: DesignAccessRoleV1,
  public val allowedActions: List<DesignAccessActionV1>,
  public val expiresAtEpochMillis: Long? = null,
) : DesignAccessMutationV1

@Serializable
@SerialName("revokeShareLink")
public data class RevokeDesignShareLinkMutationV1(public val shareId: String) :
  DesignAccessMutationV1

@Serializable
@SerialName("transferOwnership")
public data class TransferDesignOwnershipMutationV1(public val newOwnerActorId: String) :
  DesignAccessMutationV1
