# UI builder protocol

Published as the Kotlin Multiplatform coordinate `ee.schimke.composeai:ui-builder-protocol`, with
JVM (`ui-builder-protocol-jvm`) and Wasm (`ui-builder-protocol-wasm-js`) variants.

This module is the shape-only v1 contract shared by the standalone Compose UI builder's Wasm
client, service and MCP adapter. It owns:

- pinned catalog identity plus roles, traits, slot cardinalities, JSON property schemas, modifier,
  Wasm, Compose-code and SVG capabilities;
- persisted multiple-root design documents with a typed render environment, state declarations,
  ordered modifiers and named ordered slots;
- atomic, client-identified edit batches using stable neighbour anchors, plus undo and redo; stable
  position keys remain reducer/server internals and are never client supplied;
- explicit catalog-pin upgrade previews with deterministic validation, structural diffs and
  hash-bound apply mutations; rollback is a new compensating mutation and never rewrites history;
- revisioned snapshots, ordered event deltas and presence updates;
- independently revisioned ownership, actor ACL and opaque bearer-link sharing metadata, plus
  paginated actor-specific design listings;
- request/response envelopes used over HTTP and by MCP tools.

It deliberately contains no reducer, validation policy, storage, rendering, HTTP, WebSocket or MCP
implementation. A consumer selects a concrete serializer (for example
`UiBuilderRequestV1.serializer()`) and configures its own `Json` instance. The fixtures in
`docs/ui-builder/protocol-fixtures/v1` lock the JSON discriminators and required fields.

Compatibility rules for v1:

- `schemaVersion` is always `1` on transport envelopes and service state. Authored documents and
  catalog capability files retain their existing string `schema` identifiers.
- IDs, revisions and sequence cursors are opaque to clients; ordering uses `sequence` only.
- Accepted outcomes carry a canonical-document SHA-256, never the canonical document body; full
  state travels in snapshots and retained deltas.
- A committed delta event takes its revision and sequence only from its accepted outcome; the event
  wrapper does not duplicate those cursors.
- Document revision and durable event sequence retain their existing meanings. Access policy uses
  a separate `accessRevision`, so sharing changes neither the document revision nor its hash.
- The envelope `actorId` is authenticated by the transport. Nested requester IDs on commands and
  presence must match it; actor IDs in ACL mutations name targets, not the requester. Services must
  reject a mismatch rather than trusting client-authored identity.
- `allowedActions` is authoritative. `role` is a stable presentation and audit label and never
  implies actions. Owners are represented once by `ownerActorId`, not duplicated in `actorGrants`.
- Share-link IDs are opaque unguessable bearer secrets. Only actors with `manageAccess` should
  receive them; transports must not log or expose them through ordinary design listings.
- Sealed variants use the `type` discriminator and stable lower-camel `@SerialName` values.
- `updateEnvironment` batches ordered typed field changes. Reducers must reject empty changes and
  duplicate environment fields, validate the complete candidate atomically, version fields
  independently for stale-write conflicts, and retain exact before/after values for history and
  undo/redo. Reset variants exist only for nullable environment fields; omission never means reset.
- A conflict names exactly one target: `nodeId` for node/property/move conflicts or
  `environmentField` for environment conflicts. Reducers reject conflicts with both or neither.
- New optional fields may be added with defaults. Renaming fields, changing requiredness or reusing
  an enum/variant spelling requires a new protocol version.
- Catalog upgrades are two-step: `previewCatalogUpgrade` does not commit, while an
  `upgradeCatalog` mutation binds the source and target pins, source and target document hashes,
  and preview digest. A rollback reverses the pins and names the accepted operation it compensates;
  both upgrade and rollback appear as ordinary accepted operations in the durable delta.
- Readers that need forward-compatible minor evolution should use `ignoreUnknownKeys = true`.
  Strict fixture tests intentionally use `false` to catch accidental schema drift here.
