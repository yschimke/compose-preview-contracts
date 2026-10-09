"""Tests for `.github/scripts/expected-poms.py`.

Each test writes a settings file and module build scripts shaped like this repository's into a
throwaway directory and asks which POMs a release must have produced.

Run: python3 -m unittest discover -s .github/scripts/tests -v
"""

from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "expected-poms.py"

SETTINGS = """\
include(":daemon-protocol")
include(":wire-protocol")
include(":unpublished")
project(":wire-protocol").projectDir = file("api/wire-protocol")
"""

JVM = """\
plugins {
  id("ee.schimke.composeai.maven-publishing")
  alias(libs.plugins.kotlin.jvm)
}
"""

KMP = """\
plugins {
  id("ee.schimke.composeai.maven-publishing")
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  jvm()
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }
}
"""


class ExpectedPomsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.dir = pathlib.Path(tempfile.mkdtemp())
        (self.dir / "settings.gradle.kts").write_text(SETTINGS)
        for path, text in [
            ("daemon-protocol", JVM),
            ("api/wire-protocol", KMP),
            ("unpublished", "plugins { alias(libs.plugins.kotlin.jvm) }\n"),
        ]:
            (self.dir / path).mkdir(parents=True)
            (self.dir / path / "build.gradle.kts").write_text(text)

    def run_script(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--version", "2.0.0", *args],
            cwd=self.dir, capture_output=True, text=True,
        )

    def lines(self, result: subprocess.CompletedProcess[str]) -> list[str]:
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.splitlines()

    def test_recovery_run_needs_no_manifest(self) -> None:
        # A `workflow_dispatch` run computes no plan, so no manifest is written.
        self.assertEqual(
            self.lines(self.run_script()),
            [
                "daemon-protocol 2.0.0",
                "wire-protocol 2.0.0",
                "wire-protocol-jvm 2.0.0",
                "wire-protocol-wasm-js 2.0.0",
                "compose-preview-contracts-bom 2.0.0",
            ],
        )

    def test_new_module_in_publish_set_is_checked_without_a_manifest_entry(self) -> None:
        (self.dir / "publishing-manifest.json").write_text(
            json.dumps({"modules": {"daemon-protocol": "1.0.0"}})
        )
        self.assertEqual(
            self.lines(self.run_script("--publish-set", "wire-protocol")),
            [
                "daemon-protocol 1.0.0",
                "wire-protocol 2.0.0",
                "wire-protocol-jvm 2.0.0",
                "wire-protocol-wasm-js 2.0.0",
                "compose-preview-contracts-bom 2.0.0",
            ],
        )

    def test_skipped_module_without_a_manifest_entry_fails(self) -> None:
        (self.dir / "publishing-manifest.json").write_text(json.dumps({"modules": {}}))
        result = self.run_script("--publish-set", "")
        self.assertEqual(result.returncode, 1)
        self.assertIn("daemon-protocol is skipped by the plan", result.stderr)


if __name__ == "__main__":
    unittest.main()
