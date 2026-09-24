# Builder-written design documents

The five Google-app sample designs (Gmail, Calendar, Keep, Photos and Play, tablet variants),
materialized from compose-ui-builder's operations fixtures and encoded by that repository's
`UiBuilderDocument` serializer. They are deliberately **not** written by this module:
`BuilderDocumentConformanceTest` uses them to prove the typed protocol reads and re-writes the
builder's node values exactly. Regenerate them from the builder rather than editing by hand.
