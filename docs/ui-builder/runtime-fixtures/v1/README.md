# UI-builder runtime v1 fixtures

`tree-integrity.json` is the cross-language test vector for catalog-owned renderer bundles. Paths
are ordered by unsigned UTF-8 bytes, then each asset contributes:

```text
path UTF-8 | 0x00 | decimal byte length UTF-8 | 0x00 | content bytes
```

SHA-256 over that stream must equal `integritySha256`. The manifest is never an input because it
contains the resulting digest. Publisher, server and renderer-host implementations should consume
this vector rather than inventing a local example.
