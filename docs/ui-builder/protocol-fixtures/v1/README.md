# UI builder protocol v1 fixtures

These JSON documents lock the v1 field names, required fields, enum spellings and sealed-type
discriminators. `UiBuilderProtocolCompatibilityTest` parses and exactly round-trips every file with
unknown-key tolerance disabled. Add a fixture and its concrete serializer when adding a top-level
wire shape; adding a sealed subtype should extend an existing aggregate fixture or add a new one.
