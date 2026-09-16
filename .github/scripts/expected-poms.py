#!/usr/bin/env python3
"""The coordinates a release must have produced locally, and the version each carries.

The dry run before the Central upload asserts a POM exists for every coordinate. Once a release
publishes only the modules it changed, the tag is no longer the answer for all of them: a skipped
module keeps the version it last published at, so asserting the tag would fail a correct release,
while asserting nothing at all would let a module drop out of the upload unnoticed.

`PublishedVersions.resolve` makes the same decision inside Gradle. This reads the same two inputs —
the publish set and `publishing-manifest.json` — to predict it, deliberately rather than sharing
code with it: a predictor that cannot disagree with the thing it checks is not a check.

    expected-poms.py --version 3.1.0 [--publish-set a,b] [--manifest publishing-manifest.json]

Prints `<artifactId> <version>` per line. Omitting `--publish-set` means no plan ran and every
module publishes at the tag; passing it empty means the plan ran and found nothing.
"""

from __future__ import annotations

import argparse
import json
import sys

# A KMP root POM can exist while one target publication is missing, so both concrete variants a
# consumer resolves are named, not only the metadata coordinate they share.
KMP_VARIANTS = {
    "ui-builder-protocol": ["ui-builder-protocol-jvm", "ui-builder-protocol-wasm-js"],
    "screen-document": ["screen-document-jvm", "screen-document-wasm-js"],
}

# The index of the release. Never skipped, always at the tag: a consumer resolving the BOM there
# has to find it whether or not any module changed.
BOM = "compose-preview-contracts-bom"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--manifest", default="publishing-manifest.json")
    ap.add_argument("--publish-set", default=None)
    args = ap.parse_args()

    recorded = json.load(open(args.manifest, encoding="utf-8"))["modules"]
    publish_set = (
        None
        if args.publish_set is None
        else {m.strip() for m in args.publish_set.split(",") if m.strip()}
    )

    for module in sorted(recorded):
        published = publish_set is None or module in publish_set
        effective = args.version if published else recorded[module]
        for coordinate in [module, *KMP_VARIANTS.get(module, [])]:
            print(coordinate, effective)
    print(BOM, args.version)
    return 0


if __name__ == "__main__":
    sys.exit(main())
