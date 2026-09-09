# UI builder protocol v1 fixtures

These JSON documents lock the v1 field names, required fields, enum spellings and sealed-type
discriminators. `UiBuilderProtocolCompatibilityTest` parses and exactly round-trips every file with
unknown-key tolerance disabled. Add a fixture and its concrete serializer when adding a top-level
wire shape; adding a sealed subtype should extend an existing aggregate fixture or add a new one.

`lossless-document-command.json` is the exhaustive shape fixture. It includes the property,
modifier, action and environment forms observed in both the Confetti schedule and Jetcaster
Discover references, as well as all v1 mutation, outcome, conflict and rejection variants.
`materialized-confetti.json` and `materialized-jetcaster.json` are exact documents replayed from the
authoritative operations fixtures. Their tests lock both strict structure—including explicit zero
padding/inset edges—and the operations fixtures' published canonical SHA-256 values.

The list-design and access fixtures lock actor-specific effective actions, independently revisioned
owner/ACL state, every atomic access mutation, and opaque bearer-link sharing. Transport actor IDs
are authenticated requester identities; ACL mutation actor IDs identify grant targets.

The catalog-upgrade fixtures lock the non-committing deterministic preview, its exact candidate
document and structural diff, and the accepted upgrade plus compensating rollback in durable
history. Rollback reverses the catalog pins and appends history rather than rewriting it.

The `sidecar-*` fixtures lock the three records that sit *beside* a design rather than inside it:
the links record naming what it is for, the reference overlay it is drawn against, and the comment
board. None of them is part of the design document — no mutation carries them and none of them may
move a design's revision — but each is an HTTP response body, an MCP payload and a file on disk at
once, so the field names are as load-bearing as any envelope's. A shipped host already has these
bytes in its `links/`, `references/` and `comments/` directories: the shapes may gain a field, and
may never rename one. Each fixture carries only non-default values, since the strict reader encodes
no defaults; `schemaVersion` is `@EncodeDefault` and so is always on the wire.

`comment-webhook-event.json` locks the notification a host sends when somebody says something on a
design's board. It is not the board: it carries an excerpt rather than a body, a sentence rather
than an anchor's three fields, and a count rather than the comments, because its reader is a person
deciding whether to click. The chat adapters a host also offers are one-way renderings nobody
parses, but the `plain` format is this event verbatim and a `plain` receiver is a program written
against these names — which is what makes it a contract. `schema` is `@EncodeDefault` so a receiver
can always branch on the version.
