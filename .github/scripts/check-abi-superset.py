#!/usr/bin/env python3
"""Fail a pull request that removes a line from a committed ABI dump.

`checkKotlinAbi` compares the code with the dump that is committed beside it, so a change and its
regenerated dump always agree: it answers "did you update the dump?", never "did you break anyone?".
Contracts 3.13.0 shipped a binary break that passed it (#122 added a field to three data classes,
which drops the synthetic default-argument constructor and the old `copy` that jars compiled against
3.12.0 call, so the server took a `NoSuchMethodError` and returned HTTP 500s). The dump diff showed
every one of those removals; it was read as noise.

This compares each committed dump with the one at the LAST RELEASE and fails on any removed line,
synthetic ones included. Additions are fine. A deliberate break belongs in a major release: mark the
pull request `feat!:` (CI then passes `ABI_BREAK_OK=1`), or set the variable yourself.

Usage: check-abi-superset.py [--baseline-tag vX.Y.Z]   (default: v + .release-please-manifest.json)
Exit:  0 nothing removed (or allowed), 1 lines removed, 2 could not compare.
"""
import json
import os
import subprocess
import sys

SHOWN = 12


def git(*args, check=True):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=check)


def baseline_tag(argv):
    if "--baseline-tag" in argv:
        return argv[argv.index("--baseline-tag") + 1]
    with open(".release-please-manifest.json", encoding="utf-8") as f:
        return "v" + json.load(f)["."]


def lines(text):
    return {line.rstrip() for line in text.splitlines() if line.strip()}


def main(argv):
    tag = baseline_tag(argv)
    if git("rev-parse", "--verify", "-q", f"{tag}^{{commit}}", check=False).returncode != 0:
        print(f"::error::no tag {tag} to compare the ABI against (fetch it first)", file=sys.stderr)
        return 2
    dumps = [p for p in git("ls-files", "*.api").stdout.split("\n") if p]
    if not dumps:
        print("::error::no committed .api dumps found", file=sys.stderr)
        return 2

    failures = {}
    for path in dumps:
        old = git("show", f"{tag}:{path}", check=False)
        if old.returncode != 0:
            continue  # a dump that did not exist at the release: nothing to have broken
        with open(path, encoding="utf-8") as f:
            removed = sorted(lines(old.stdout) - lines(f.read()))
        if removed:
            failures[path] = removed

    if not failures:
        print(f"ABI is a superset of {tag}'s across {len(dumps)} dump(s)")
        return 0

    for path, removed in failures.items():
        print(f"{path}: {len(removed)} line(s) present in {tag} are gone", file=sys.stderr)
        for line in removed[:SHOWN]:
            print(f"  - {line.strip()[:180]}", file=sys.stderr)
        if len(removed) > SHOWN:
            print(f"  ... and {len(removed) - SHOWN} more", file=sys.stderr)
    note = (
        "Every removed line is a signature a jar compiled against "
        f"{tag} can call. That includes the synthetic default-argument constructors and `copy` "
        "overloads a data class loses when a parameter is added to its primary constructor. "
        "Add a field through the type's Builder, or keep the old constructor and copy; see "
        "AGENTS.md and docs/VERSIONING.md."
    )
    if os.environ.get("ABI_BREAK_OK") == "1":
        print(f"ABI break allowed (ABI_BREAK_OK=1): {note}", file=sys.stderr)
        return 0
    print(f"::error::{note} A deliberate break is a major release: title the PR `feat!:`.",
          file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
