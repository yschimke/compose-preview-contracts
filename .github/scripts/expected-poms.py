#!/usr/bin/env python3
"""The coordinates a release must have produced locally, and the version each carries.

The dry run before the Central upload asserts a POM exists for every coordinate. Once a release
publishes only the modules it changed, the tag is no longer the answer for all of them: a skipped
module keeps the version it last published at, so asserting the tag would fail a correct release,
while asserting nothing at all would let a module drop out of the upload unnoticed.

`PublishedVersions.resolve` makes the same decision inside Gradle. This reads the same inputs — the
published modules, the publish set and `publishing-manifest.json` — to predict it, deliberately
rather than sharing code with it: a predictor that cannot disagree with the thing it checks is not a
check.

The module list comes from the build (`settings.gradle.kts` and each module's
`composeai.maven-publishing` plugin), never from the manifest. The manifest only records what Central
already has, so it is absent on a `workflow_dispatch` recovery run, which computes no plan, and it
never names a module publishing for the first time. Reading the list from it crashed the recovery
run for v3.23.0 and left every new module unchecked. A KMP module's targets come from its build file
in the same way, so a new KMP module cannot drop out of this check either.

    expected-poms.py --version 3.1.0 [--publish-set a,b] [--manifest publishing-manifest.json]

Prints `<artifactId> <version>` per line. Omitting `--publish-set` means no plan ran and every
module publishes at the tag, and the manifest is not read. Passing it empty means the plan ran and
found nothing, and then every module needs a manifest entry.
"""

from __future__ import annotations

import argparse
import json
import re
import sys

# The Gradle target accessor -> the artifactId suffix of the publication it adds. A KMP root POM
# can exist while one target publication is missing, so every concrete variant a consumer resolves
# is named, not only the metadata coordinate they share.
KMP_TARGET_SUFFIXES = {"jvm": "jvm", "wasmJs": "wasm-js"}

# The index of the release. Never skipped, always at the tag: a consumer resolving the BOM there
# has to find it whether or not any module changed.
BOM = "compose-preview-contracts-bom"


def published_modules(settings_path: str = "settings.gradle.kts") -> dict[str, list[str]]:
    """artifactId -> its KMP variant artifactIds (empty for a single-target module)."""
    settings = open(settings_path, encoding="utf-8").read()
    dirs = dict(re.findall(r'project\("(:[^"]+)"\)\.projectDir = file\("([^"]+)"\)', settings))
    modules: dict[str, list[str]] = {}
    for path in re.findall(r'^include\("(:[^"]+)"\)', settings, re.M):
        directory = dirs.get(path, path.lstrip(":").replace(":", "/"))
        try:
            text = open(directory + "/build.gradle.kts", encoding="utf-8").read()
        except OSError:
            continue
        if 'composeai.maven-publishing")' not in text:
            continue
        aid = path.lstrip(":").replace(":", "-")
        variants = []
        if "kotlin.multiplatform" in text:
            for accessor, suffix in KMP_TARGET_SUFFIXES.items():
                if re.search(rf"^\s*(@\S+\s+)?{accessor}\s*[({{]", text, re.M):
                    variants.append(f"{aid}-{suffix}")
        modules[aid] = variants
    return modules


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--manifest", default="publishing-manifest.json")
    ap.add_argument("--publish-set", default=None)
    ap.add_argument("--settings", default="settings.gradle.kts")
    args = ap.parse_args()

    modules = published_modules(args.settings)
    if not modules:
        print(f"no published modules found from {args.settings}", file=sys.stderr)
        return 1

    if args.publish_set is None:
        publish_set, recorded = set(modules), {}
    else:
        publish_set = {m.strip() for m in args.publish_set.split(",") if m.strip()}
        recorded = json.load(open(args.manifest, encoding="utf-8"))["modules"]

    for module in sorted(modules):
        if module in publish_set:
            effective = args.version
        elif module in recorded:
            effective = recorded[module]
        else:
            print(f"{module} is skipped by the plan but has no entry in {args.manifest}",
                  file=sys.stderr)
            return 1
        for coordinate in [module, *modules[module]]:
            print(coordinate, effective)
    print(BOM, args.version)
    return 0


if __name__ == "__main__":
    sys.exit(main())
