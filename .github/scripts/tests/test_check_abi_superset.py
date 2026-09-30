"""Tests for check-abi-superset.py, run against throwaway git repositories.

The script decides whether a pull request may remove a signature a released consumer can call, and
the only time it matters is the day somebody does. Each case makes a release tag with a dump, changes
the dump, and pins the exit code.
"""
import os
import subprocess
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check-abi-superset.py")
OLD = "public final class a/Foo {\n\tpublic fun <init> (I)V\n\tpublic fun copy (I)La/Foo;\n}\n"


def run(cmd, cwd, env=None):
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, env=env)


class AbiSupersetTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.path = self.dir.name
        for cmd in (
            ["git", "init", "-q", "-b", "main"],
            ["git", "config", "user.email", "t@example.com"],
            ["git", "config", "user.name", "t"],
        ):
            run(cmd, self.path)
        os.makedirs(os.path.join(self.path, "api"))
        self.write("api/x.api", OLD)
        with open(os.path.join(self.path, ".release-please-manifest.json"), "w") as f:
            f.write('{".": "1.0.0"}\n')
        run(["git", "add", "-A"], self.path)
        run(["git", "commit", "-qm", "release"], self.path)
        run(["git", "tag", "v1.0.0"], self.path)

    def write(self, name, text):
        with open(os.path.join(self.path, name), "w") as f:
            f.write(text)
        run(["git", "add", "-A"], self.path)

    def check(self, **env):
        merged = {**os.environ, **env}
        merged.pop("ABI_BREAK_OK", None) if "ABI_BREAK_OK" not in env else None
        return run(["python3", SCRIPT], self.path, merged)

    def test_unchanged_passes(self):
        self.assertEqual(self.check().returncode, 0)

    def test_additions_pass(self):
        self.write("api/x.api", OLD.replace("}\n", "\tpublic fun extra ()V\n}\n"))
        self.assertEqual(self.check().returncode, 0)

    def test_a_removed_synthetic_line_fails(self):
        # The 3.13.0 shape: a synthetic default-argument constructor disappears.
        self.write("api/x.api", OLD.replace("\tpublic fun <init> (I)V\n", ""))
        result = self.check()
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("<init>", result.stderr)

    def test_a_replaced_copy_fails(self):
        self.write("api/x.api", OLD.replace("copy (I)", "copy (IJ)"))
        self.assertEqual(self.check().returncode, 1)

    def test_a_deliberate_break_can_be_allowed(self):
        self.write("api/x.api", OLD.replace("\tpublic fun <init> (I)V\n", ""))
        self.assertEqual(self.check(ABI_BREAK_OK="1").returncode, 0)

    def test_a_new_dump_has_nothing_to_break(self):
        self.write("api/new.api", "public final class b/Bar {\n}\n")
        self.assertEqual(self.check().returncode, 0)

    def test_a_missing_baseline_tag_is_an_error_not_a_pass(self):
        run(["git", "tag", "-d", "v1.0.0"], self.path)
        self.assertEqual(self.check().returncode, 2)


if __name__ == "__main__":
    unittest.main()
