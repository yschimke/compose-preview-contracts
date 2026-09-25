"""Tests for `.github/scripts/maven-publish-plan.sh`.

Each test builds a throwaway git repository shaped like this one — a settings file, published
modules, build-logic, a version catalog — tags a release, makes one change, and asks the plan which
modules have to publish. `--manifest` supplies the baseline, so nothing here reaches Maven Central;
the AGP POM is served from a `file://` directory through `COMPOSEAI_GOOGLE_MAVEN`.

Run: python3 -m unittest discover -s .github/scripts/tests -v
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import tempfile
import textwrap
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "maven-publish-plan.sh"

GIT_ENV = {
    "GIT_AUTHOR_NAME": "Plan Test",
    "GIT_AUTHOR_EMAIL": "plan-test@example.invalid",
    "GIT_COMMITTER_NAME": "Plan Test",
    "GIT_COMMITTER_EMAIL": "plan-test@example.invalid",
    "GIT_CONFIG_GLOBAL": os.devnull,
    "GIT_CONFIG_NOSYSTEM": "1",
}

CATALOG = """\
# The catalog header comment.
[versions]
kotlin = "2.4.20"
kotlinCoreLibraries = "2.0.21"
agp = "9.4.1"
okio = "3.18.2"
junit = "4.13.2"
serialization = "1.11.0"

[libraries]
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
junit = { module = "junit:junit", version.ref = "junit" }
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

[bundles]
ser = ["serialization-json"]

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
"""

FILES = {
    "settings.gradle.kts": """\
        pluginManagement { includeBuild("build-logic") }
        include(":a")
        project(":a").projectDir = file("mods/a")
        include(":b")
        include(":c")
        include(":droid")
        include(":bom")
        """,
    "build.gradle.kts": """\
        plugins { alias(libs.plugins.kotlin.jvm) apply false }
        """,
    "gradle/libs.versions.toml": CATALOG,
    "gradle/wrapper/gradle-wrapper.properties": "distributionUrl=gradle-9.zip\n",
    "build-logic/build.gradle.kts": """\
        dependencies {
          implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
          implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        }
        """,
    "build-logic/settings.gradle.kts": """\
        dependencyResolutionManagement {
          versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
        }
        """,
    "build-logic/src/main/kotlin/ee/schimke/composeai/buildlogic/ComposeAiAndroidConventionsPlugin.kt": """\
        import com.android.build.api.dsl.LibraryExtension
        """,
    "build-logic/src/main/kotlin/ee/schimke/composeai/buildlogic/ComposeAiKotlinConventionsPlugin.kt": """\
        fun x() { catalogs.named("libs").findVersion("kotlinCoreLibraries") }
        """,
    "build-logic/src/main/kotlin/ee/schimke/composeai/buildlogic/ComposeAiMavenPublishingPlugin.kt": """\
        fun y() { pluginManager.withPlugin("com.android.library") { } }
        """,
    "build-logic/src/test/kotlin/SomeTest.kt": "class SomeTest\n",
    # a: okio; b: depends on a, uses junit; c: the bundle and the kotlin version; droid: Android.
    "mods/a/build.gradle.kts": """\
        plugins { id("composeai.maven-publishing") }
        dependencies { api(libs.okio) }
        """,
    "mods/a/src/main/kotlin/A.kt": "class A\n",
    "mods/a/src/test/kotlin/ATest.kt": "class ATest\n",
    "mods/a/src/testFixtures/kotlin/AFixture.kt": "class AFixture\n",
    "b/build.gradle.kts": """\
        plugins { id("composeai.maven-publishing") }
        dependencies {
          api(project(":a"))
          testImplementation(libs.junit)
        }
        """,
    "b/src/jvmTest/kotlin/BTest.kt": "class BTest\n",
    "c/build.gradle.kts": """\
        plugins { id("composeai.maven-publishing") }
        dependencies {
          api(libs.bundles.ser)
          api("org.jetbrains.kotlin:kotlin-build-tools-api:${libs.versions.kotlin.get()}")
        }
        """,
    "droid/build.gradle.kts": """\
        plugins {
          id("composeai.maven-publishing")
          alias(libs.plugins.android.library)
        }
        """,
    "bom/build.gradle.kts": """\
        plugins { id("composeai.maven-publishing-platform") }
        """,
    "docs/README.md": "docs\n",
}

MODULES = ["a", "b", "c", "droid"]


def agp_pom(kgp: str) -> str:
    return textwrap.dedent(
        f"""\
        <project>
          <dependencies>
            <dependency>
              <groupId>org.jetbrains.kotlin</groupId>
              <artifactId>kotlin-gradle-plugin</artifactId>
              <version>{kgp}</version>
              <scope>runtime</scope>
            </dependency>
          </dependencies>
        </project>
        """
    )


class PlanTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name) / "repo"
        self.maven = pathlib.Path(self.tmp.name) / "maven"
        self.root.mkdir()
        self.git("init", "-q", "-b", "main")
        for path, text in FILES.items():
            self.write(path, textwrap.dedent(text))
        self.commit("initial")
        self.git("tag", "v1.0.0")
        self.manifest = pathlib.Path(self.tmp.name) / "manifest.json"
        self.baseline({m: "1.0.0" for m in MODULES})

    def tearDown(self) -> None:
        self.tmp.cleanup()

    # ---- helpers ------------------------------------------------------------------------------

    def git(self, *args: str) -> str:
        return subprocess.run(
            ["git", "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", *args],
            cwd=self.root, env={**os.environ, **GIT_ENV}, check=True, capture_output=True,
            text=True,
        ).stdout

    def write(self, path: str, text: str) -> None:
        p = self.root / path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding="utf-8")

    def edit(self, path: str, old: str, new: str) -> None:
        p = self.root / path
        text = p.read_text(encoding="utf-8")
        self.assertIn(old, text, f"{old!r} not in {path}")
        p.write_text(text.replace(old, new), encoding="utf-8")

    def commit(self, message: str) -> None:
        self.git("add", "-A")
        self.git("commit", "-q", "--allow-empty", "-m", message)

    def baseline(self, modules: dict[str, str]) -> None:
        self.manifest.write_text(json.dumps({"modules": modules}), encoding="utf-8")

    def serve_agp(self, version: str, kgp: str) -> None:
        d = self.maven / "com/android/tools/build/gradle" / version
        d.mkdir(parents=True, exist_ok=True)
        (d / f"gradle-{version}.pom").write_text(agp_pom(kgp), encoding="utf-8")

    def plan(self) -> tuple[list[str], str]:
        proc = subprocess.run(
            [str(SCRIPT), "--head", "HEAD", "--manifest", str(self.manifest)],
            cwd=self.root, capture_output=True, text=True,
            env={**os.environ, "COMPOSEAI_GOOGLE_MAVEN": self.maven.as_uri()},
        )
        self.assertEqual(proc.returncode, 0, proc.stderr)
        return proc.stdout.split(), proc.stderr

    def assertPlan(self, expected: list[str]) -> str:
        got, log = self.plan()
        self.assertEqual(got, sorted(expected), log)
        return log

    def bump(self, entry: str, new: str) -> None:
        self.edit("gradle/libs.versions.toml", entry, new)
        self.commit("bump")

    # ---- the baseline behaviour ---------------------------------------------------------------

    def test_nothing_changed_publishes_nothing(self) -> None:
        self.assertPlan([])

    def test_docs_change_publishes_nothing(self) -> None:
        self.write("docs/README.md", "more docs\n")
        self.commit("docs")
        self.assertPlan([])

    def test_module_source_change_propagates_to_dependents(self) -> None:
        self.write("mods/a/src/main/kotlin/A.kt", "class A2\n")
        self.commit("a")
        self.assertPlan(["a", "b"])

    def test_never_published_module_publishes(self) -> None:
        self.baseline({"a": "1.0.0", "b": "1.0.0", "c": "1.0.0"})
        self.assertPlan(["droid"])

    # ---- test sources -------------------------------------------------------------------------

    def test_test_only_change_does_not_publish(self) -> None:
        self.write("mods/a/src/test/kotlin/ATest.kt", "class ATest2\n")
        self.write("b/src/jvmTest/kotlin/BTest.kt", "class BTest2\n")
        self.commit("tests")
        self.assertIn("only test sources changed", self.assertPlan([]))

    def test_test_fixtures_are_shipped(self) -> None:
        self.write("mods/a/src/testFixtures/kotlin/AFixture.kt", "class AFixture2\n")
        self.commit("fixtures")
        self.assertPlan(["a", "b"])

    def test_build_logic_tests_are_not_a_shared_input(self) -> None:
        self.write("build-logic/src/test/kotlin/SomeTest.kt", "class SomeTest2\n")
        self.commit("build-logic test")
        self.assertPlan([])

    def test_build_logic_source_moved_into_tests_publishes_everything(self) -> None:
        # With rename detection, `git diff --name-only` reports only the test-side destination, and
        # the move would read as a test-only change. The source leaving the plugin classpath is not.
        self.git(
            "mv",
            "build-logic/src/main/kotlin/ee/schimke/composeai/buildlogic/ComposeAiMavenPublishingPlugin.kt",
            "build-logic/src/test/kotlin/ComposeAiMavenPublishingPlugin.kt",
        )
        self.commit("move into tests")
        self.assertPlan(MODULES)

    def test_module_source_moved_into_tests_publishes_the_module(self) -> None:
        self.git("mv", "mods/a/src/main/kotlin/A.kt", "mods/a/src/test/kotlin/A.kt")
        self.commit("move into tests")
        self.assertPlan(["a", "b"])

    # ---- shared inputs other than the catalog still publish everything -------------------------

    def test_build_logic_source_publishes_everything(self) -> None:
        self.write("build-logic/src/main/kotlin/New.kt", "class New\n")
        self.commit("build-logic")
        self.assertPlan(MODULES)

    def test_root_build_publishes_everything(self) -> None:
        self.write("build.gradle.kts", "// release tooling\n")
        self.commit("root")
        self.assertPlan(MODULES)

    def test_wrapper_publishes_everything(self) -> None:
        self.write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=gradle-10.zip\n")
        self.commit("wrapper")
        self.assertPlan(MODULES)

    def test_missing_baseline_tag_publishes_everything(self) -> None:
        self.baseline({m: "0.9.0" for m in MODULES})
        self.assertPlan(MODULES)

    # ---- the version catalog ------------------------------------------------------------------

    def test_catalog_comment_only_publishes_nothing(self) -> None:
        self.bump("# The catalog header comment.", "# A different comment.")
        self.assertPlan([])

    def test_unused_new_entry_publishes_nothing(self) -> None:
        self.bump("[libraries]\n", '[libraries]\nguava = "com.google.guava:guava:33.0"\n')
        self.assertPlan([])

    def test_version_ref_dirties_its_library_users_and_their_dependents(self) -> None:
        self.bump('okio = "3.18.2"', 'okio = "3.19.0"')
        self.assertPlan(["a", "b"])

    def test_library_change_dirties_only_its_users(self) -> None:
        self.bump('junit = "4.13.2"', 'junit = "4.13.3"')
        self.assertPlan(["b"])

    def test_bundle_member_change_dirties_bundle_users(self) -> None:
        self.bump('serialization = "1.11.0"', 'serialization = "1.12.0"')
        self.assertPlan(["c"])

    def test_bundle_membership_change_dirties_bundle_users(self) -> None:
        self.bump('ser = ["serialization-json"]', 'ser = ["serialization-json", "okio"]')
        self.assertPlan(["c"])

    def test_alias_used_by_root_build_publishes_everything(self) -> None:
        # kotlin: the root build's plugin alias and build-logic's classpath.
        self.bump('kotlin = "2.4.20"', 'kotlin = "2.4.30"')
        self.assertPlan(MODULES)

    def test_literal_lookup_in_build_logic_publishes_everything(self) -> None:
        self.bump('kotlinCoreLibraries = "2.0.21"', 'kotlinCoreLibraries = "2.1.0"')
        self.assertIn("kotlinCoreLibraries", self.assertPlan(MODULES))

    def test_computed_lookup_publishes_everything(self) -> None:
        self.write(
            "build-logic/src/main/kotlin/ee/schimke/composeai/buildlogic/ComposeAiKotlinConventionsPlugin.kt",
            'fun x(n: String) { catalogs.named("libs").findVersion(n) }\n',
        )
        self.commit("computed lookup")
        self.git("tag", "v1.1.0")
        self.baseline({m: "1.1.0" for m in MODULES})
        self.bump('junit = "4.13.2"', 'junit = "4.13.3"')
        self.assertIn("cannot read", self.assertPlan(MODULES))

    def test_unparseable_catalog_publishes_everything(self) -> None:
        self.bump("[plugins]", "[plugins")
        self.assertIn("cannot parse", self.assertPlan(MODULES))

    def test_unknown_catalog_section_publishes_everything(self) -> None:
        self.bump("[bundles]", '[metadata]\nformat.version = "1.1"\n\n[bundles]')
        self.assertPlan(MODULES)

    def test_catalog_and_module_change_together(self) -> None:
        self.edit("gradle/libs.versions.toml", 'junit = "4.13.2"', 'junit = "4.13.3"')
        self.write("c/src/main/kotlin/C.kt", "class C\n")
        self.commit("both")
        self.assertPlan(["b", "c"])

    def test_catalog_compared_against_each_modules_own_baseline(self) -> None:
        # junit moves in 1.1.0, which only `a` and `c` did not publish at.
        self.bump('junit = "4.13.2"', 'junit = "4.13.3"')
        self.git("tag", "v1.1.0")
        self.baseline({"a": "1.0.0", "b": "1.1.0", "c": "1.0.0", "droid": "1.1.0"})
        self.assertPlan([])
        self.bump('okio = "3.18.2"', 'okio = "3.19.0"')
        self.assertPlan(["a", "b"])

    # ---- AGP: confined to Android modules, when that can be proven -----------------------------

    def test_agp_bump_reaches_only_android_modules(self) -> None:
        self.serve_agp("9.5.0", kgp="2.2.10")
        self.bump('agp = "9.4.1"', 'agp = "9.5.0"')
        self.assertPlan(["droid"])

    def test_agp_bump_with_no_android_module_publishes_nothing(self) -> None:
        self.write("droid/build.gradle.kts", 'plugins { id("composeai.maven-publishing") }\n')
        self.commit("droid is jvm")
        self.git("tag", "v1.1.0")
        self.baseline({m: "1.1.0" for m in MODULES})
        self.serve_agp("9.5.0", kgp="2.2.10")
        self.bump('agp = "9.4.1"', 'agp = "9.5.0"')
        self.assertPlan([])

    def test_agp_needing_a_newer_kotlin_plugin_publishes_everything(self) -> None:
        self.serve_agp("9.5.0", kgp="2.5.0")
        self.bump('agp = "9.4.1"', 'agp = "9.5.0"')
        self.assertIn("above the catalog", self.assertPlan(MODULES))

    def test_agp_pom_unreachable_publishes_everything(self) -> None:
        self.bump('agp = "9.4.1"', 'agp = "9.5.0"')
        self.assertPlan(MODULES)

    def test_agp_used_outside_the_android_conventions_publishes_everything(self) -> None:
        self.write(
            "build-logic/src/main/kotlin/ee/schimke/composeai/buildlogic/Other.kt",
            "import com.android.build.api.dsl.CommonExtension\n",
        )
        self.commit("agp elsewhere")
        self.git("tag", "v1.1.0")
        self.baseline({m: "1.1.0" for m in MODULES})
        self.serve_agp("9.5.0", kgp="2.2.10")
        self.bump('agp = "9.4.1"', 'agp = "9.5.0"')
        self.assertPlan(MODULES)


if __name__ == "__main__":
    unittest.main()
