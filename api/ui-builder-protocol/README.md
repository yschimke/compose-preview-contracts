# UI builder protocol

Published as the Kotlin Multiplatform coordinate `ee.schimke.composeai:ui-builder-protocol`, with
JVM (`ui-builder-protocol-jvm`) and Wasm (`ui-builder-protocol-wasm-js`) variants.

This module is the shape-only v1 contract shared by the standalone Compose UI builder's Wasm
client, service and MCP adapter. It owns:

- pinned catalog identity plus roles, traits, slot cardinalities, JSON property schemas, modifier,
  Wasm, Compose-code and SVG capabilities;
- persisted multiple-root design documents with a typed render environment, state declarations,
  ordered modifiers and named ordered slots. The typed modifier vocabulary is closed and
  declarative — sizing and constraints, the three fills, alignment (one modifier per scope, because
  a row aligns vertically and a column horizontally), offset and z-index, background, border,
  alpha, shadow, rotate, scale, the two scrolls, and a test tag. What is deliberately absent, and
  why, is on `DesignModifierV1`. One variant is open by name: `remoteCall`
  (`RemoteCallModifierV1`) carries any other `RemoteModifier` call, its arguments as values, for
  documents a Remote Compose catalog renders. Handling of the sealed type is therefore not
  exhaustive by modifier name: a consumer that renders or exports `remoteCall` validates its name
  and arguments against the catalog's vocabulary (generated from the released Remote Compose API)
  and refuses a call that catalog does not declare;
- atomic, client-identified edit batches using stable neighbour anchors, plus undo and redo; stable
  position keys remain reducer/server internals and are never client supplied;
- explicit catalog-pin upgrade previews with deterministic validation, structural diffs and
  hash-bound apply mutations; rollback is a new compensating mutation and never rewrites history;
- revisioned snapshots, ordered event deltas and presence updates;
- independently revisioned ownership, actor ACL and opaque bearer-link sharing metadata, plus
  paginated actor-specific design listings;
- request/response envelopes used over HTTP and by MCP tools.
- catalog-owned renderer artifact, manifest, installed-descriptor, iframe-message and inspection
  shapes, plus the canonical tree-integrity framing and its cross-language test vector.

It deliberately contains no reducer, validation policy, storage, rendering, HTTP, WebSocket or MCP
implementation. A consumer selects a concrete serializer (for example
`UiBuilderRequestV1.serializer()`) and configures its own `Json` instance. The fixtures in
`docs/ui-builder/protocol-fixtures/v1` lock the JSON discriminators and required fields.
Runtime archive I/O, SHA-256 implementations, installation, leases and iframe hosting remain in
their owning publisher, server and renderer-SDK repositories.

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

## JSON Schemas

`schemas/design-document-v1.schema.json`, `schemas/design-mutation-v1.schema.json` and
`schemas/design-submission-v1.schema.json` (JSON Schema draft 2020-12) ship in the JVM jar, and are
committed under `src/jvmMain/resources/schemas/` for anything that reads them from source.

- **Generated, never hand-written.** `ProtocolJsonSchemaTest` derives them from the serializers'
  descriptors and fails on any difference from the committed files, so a field or a mutation added
  in Kotlin shows up as a schema diff. Regenerate deliberately with
  `UPDATE_SCHEMAS=1 ./gradlew :ui-builder-protocol:jvmTest`.
- **Checked against the fixtures.** The materialized documents and the lossless submission fixture
  are validated against the committed schemas, so a schema that is right about types but wrong about
  real documents fails too.
- **Strict on purpose.** Objects are closed (`additionalProperties: false`), matching the
  `ignoreUnknownKeys = false` configuration hosts and the fixture tests decode with. A document with a
  field this version does not know is invalid *against this version's schema*; readers that want
  forward-compatible evolution still use `ignoreUnknownKeys = true` as above.
- **Describes the protocol document.** These cover `DesignDocumentV1` and the mutation/submission
  vocabulary, not the builder's own `.uid` file format (`UiBuilderDocument`), which differs in a
  few container fields (see `BuilderDocumentConformanceTest`).

## Production project files

`production.ProductionUidFileV1` is the explicit build-generation wrapper, identified by
`compose-ui-builder-production/v1`. It is **not** a `DesignDocumentV1` with optional extra fields:
older design-only tools must refuse it rather than discard an application's declared API. Readers
must check the schema before adapting a file and use strict unknown-field handling for this
contract. Production types retain the `kind` discriminator of the experimental builder format;
embedded design values retain `type`.

The wrapper declares project-relative imports, a pinned catalog digest, models and an optional
entry point plus design. A model-only file has neither entry point nor fabricated visual root.
Ordered model fields fix constructor order; ownership distinguishes generated models from mapped
external project data classes. Nested model references, nullable types and lists are explicit.
Entry points declare stable Kotlin names, visibility, input models, property bindings, component
uses, required application events and event bindings. Event payload paths are field-name lists;
null means no payload and an empty list means the entire input model. Component events forward
through explicit child-to-parent event-name mappings. None of these declarations are inferred
from the current visual tree.

All new extensible shapes use builders to keep future optional additions binary compatible.
The generated `schemas/production-uid-v1.schema.json` and fixtures under
`docs/ui-builder/protocol-fixtures/production-v1` pin the wire vocabulary. Filesystem eligibility,
model/path validation, editor persistence, and source generation remain in the builder repository.
The protocol module retains the Java 17 consumer floor and publishes JVM and Wasm variants.

### Nullable production reads and keyed component placements

`ProductionBindingV1.Builder.fallback` is an optional scalar JSON literal for null-safe reads.
`ProductionComponentUseV1.Builder.onNull` explicitly selects `skip` for absent component/list
content; `keyPath` selects repetition and names a stable item key relative to the child input model.
The generator owns type/path validation, literal range checks and runtime key uniqueness checks.
Missing policies are never inferred. Item keys must be non-null String, Int or Long fields, and
items must be non-null models. The DynamicLibrary/DynamicModels fixtures exercise these fields.
Existing builders keep their signatures, and strict older production readers reject these new
fields rather than silently dropping control flow. No service-document schema is changed.
