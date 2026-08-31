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
- revisioned snapshots, ordered event deltas and presence updates;
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
- Sealed variants use the `type` discriminator and stable lower-camel `@SerialName` values.
- New optional fields may be added with defaults. Renaming fields, changing requiredness or reusing
  an enum/variant spelling requires a new protocol version.
- Readers that need forward-compatible minor evolution should use `ignoreUnknownKeys = true`.
  Strict fixture tests intentionally use `false` to catch accidental schema drift here.
