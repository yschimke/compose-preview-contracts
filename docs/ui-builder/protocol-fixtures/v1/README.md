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
