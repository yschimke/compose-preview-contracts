#!/usr/bin/env bash
# Decide which modules a release actually has to publish.
#
# Until now every release uploaded all 12 coordinates at the tag, whether or not a byte of them
# changed. Each coordinate-publish is 20 files against an org-wide Maven Central file-count limit
# that all of yschimke's publishing repositories share (yschimke/compose-ai-tools#4772), and these
# contracts release on their own cadence precisely because they change rarely — so most of that
# upload is re-uploading bytes nobody touched.
#
# Ported from compose-preview-daemon, where the same script runs; the rules below are its rules.
#
# A module is published when:
#
#   1. a file under it changed since the tag IT last published at (not since the last release —
#      a module skipped for three releases is compared against its own baseline, so nothing is
#      ever missed by a gap), or
#   2. a module it depends on is being published, or
#   3. a shared build input changed, which can move every artifact at once — narrowed, for the
#      version catalog, to the modules whose build scripts name a changed entry (see SHARED below).
#
# Only shipped sources count for rule 1: a change confined to a module's test source sets
# (`src/test`, `src/jvmTest`, …) cannot reach its artifact and does not mark it dirty.
#
# Rule 2 is what keeps the POMs honest, and it is deliberately coarser than it needs to be. A
# published POM names its project dependencies at *their* `project.version`, so a module may only
# be skipped while everything it depends on is also skipped; otherwise it would name a sibling
# version that was never uploaded — exactly the break compose-ai-tools shipped in v2.2.1 and paid
# for in yschimke/wear-m3-catalog#350. Propagating on any change rather than only on an ABI change
# costs about 6 percentage points (73% -> 67%) and needs no assumption about binary compatibility;
# tighten it only when the modules carry ABI dumps to prove the surface held.
#
# Every uncertainty resolves to "publish". Central refuses a second upload of a version, so an
# unnecessary publish costs quota while a wrongly-skipped one is unrepairable.
#
# Usage: maven-publish-plan.sh --head <ref> [--manifest <path>]
# Output: one artifact id per line, on stdout. Diagnostics go to stderr.
set -euo pipefail

HEAD_REF=""
MANIFEST=""
WRITE_MANIFEST=""
while [ $# -gt 0 ]; do
  case "$1" in
    --head) HEAD_REF="$2"; shift 2 ;;
    --manifest) MANIFEST="$2"; shift 2 ;;
    --write-manifest) WRITE_MANIFEST="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
[ -n "$HEAD_REF" ] || { echo "--head is required" >&2; exit 2; }
[ -z "$MANIFEST" ] || [ -f "$MANIFEST" ] || { echo "no manifest at $MANIFEST" >&2; exit 2; }

python3 - "$HEAD_REF" "$MANIFEST" "$WRITE_MANIFEST" <<'PY'
import json, os, re, subprocess, sys, collections, urllib.error, urllib.request
try:
    import tomllib  # 3.11+
except ImportError:  # pragma: no cover - resolves to "publish everything"
    tomllib = None
from concurrent.futures import ThreadPoolExecutor

GROUP_PATH = "ee/schimke/composeai"
CENTRAL = "https://repo1.maven.org/maven2"

head, manifest_path, write_manifest_path = sys.argv[1], sys.argv[2], sys.argv[3]

def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True).stdout

settings = open("settings.gradle.kts", encoding="utf-8").read()
dirs = dict(re.findall(r'project\("(:[^"]+)"\)\.projectDir = file\("([^"]+)"\)', settings))
paths = re.findall(r'^include\("(:[^"]+)"\)', settings, re.M)

modules = {}   # artifactId -> directory
deps = {}      # artifactId -> [artifactId]
path_to_id = {}
for p in paths:
    d = dirs.get(p, p.lstrip(":").replace(":", "/"))
    try:
        text = open(d + "/build.gradle.kts", encoding="utf-8").read()
    except OSError:
        continue
    if 'composeai.maven-publishing")' not in text:
        continue
    aid = p.lstrip(":").replace(":", "-")
    modules[aid] = d
    path_to_id[p] = aid
    deps[aid] = [m.lstrip(":").replace(":", "-")
                 for m in re.findall(r'project\("(:[^"]+)"\)', text)]
deps = {a: [d for d in ds if d in modules] for a, ds in deps.items()}

def central_release(aid):
    """The newest version of `aid` on Central, or None if it has never published there.

    `<release>` rather than `<latest>`: `latest` can name a snapshot on repositories that carry
    them, and a baseline that is not a real release would diff against a tag that does not exist.
    """
    url = f"{CENTRAL}/{GROUP_PATH}/{aid}/maven-metadata.xml"
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            body = response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None  # never published
        print(f"  {aid}: Central said {e.code}; publishing", file=sys.stderr)
        return None
    except Exception as e:  # noqa: BLE001 - any failure resolves to "publish"
        print(f"  {aid}: could not reach Central ({e}); publishing", file=sys.stderr)
        return None
    m = re.search(r"<release>([^<]+)</release>", body)
    return m.group(1) if m else None


if manifest_path:
    recorded = json.load(open(manifest_path, encoding="utf-8"))["modules"]
    print(f"  baseline: {manifest_path} ({len(recorded)} entries)", file=sys.stderr)
else:
    # Eight at a time: 69 sequential round-trips is most of this script's wall clock, and Central
    # serves these as static files.
    with ThreadPoolExecutor(max_workers=8) as pool:
        found = dict(zip(sorted(modules), pool.map(central_release, sorted(modules))))
    recorded = {aid: v for aid, v in found.items() if v}
    print(f"  baseline: Maven Central ({len(recorded)} of {len(modules)} coordinates)",
          file=sys.stderr)

if write_manifest_path:
    with open(write_manifest_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "_comment": "The version each coordinate is published at on Maven Central. "
                            "Resolved at release time by .github/scripts/maven-publish-plan.sh "
                            "and NOT committed - Central is the source of truth.",
                "modules": dict(sorted(recorded.items())),
            },
            f,
            indent=2,
        )
        f.write("\n")


# ---- rule 3: shared build inputs ------------------------------------------------------------
#
# A shared build input can change any artifact, so by default it opens the gate for everything.
# Two narrowings, both measured (yschimke/compose-preview-contracts#111): v3.5.0 published every
# coordinate for an AGP bump no module compiles against, and the version catalog is by far the
# most frequently changed shared input, almost always for one alias.
#
#   * `build-logic/src/test/**` is build-logic's own test suite. It is not on the plugin classpath
#     and cannot reach an artifact.
#   * `gradle/libs.versions.toml` is diffed entry by entry. A changed alias dirties only the
#     published modules whose build scripts name it. If a changed alias is named by anything that
#     reaches every module — build-logic, the root build, settings, a build script outside a
#     published module — or the diff cannot be read with confidence, everything publishes.
#
# Everything else under SHARED still publishes everything.
SHARED = re.compile(r"^(build-logic/|gradle/|gradlew|settings\.gradle\.kts$|build\.gradle\.kts$)")
CATALOG = "gradle/libs.versions.toml"
SHARED_IGNORED = re.compile(r"^build-logic/src/test/")

# Test source sets, relative to a module directory: `src/test`, and the KMP / Android `…Test` ones
# (`src/commonTest`, `src/jvmTest`, `src/androidUnitTest`). None of them is packaged into a published
# artifact. `src/testFixtures` IS published by `java-test-fixtures`, and is deliberately not
# matched: the name has to END in `Test`.
TEST_SOURCES = re.compile(r"^src/(test|[A-Za-z0-9]+Test)/")

# The one build-logic dependency that is known to reach only some modules. AGP is on the convention
# plugins' classpath, but the conventions touch it only inside `withPlugin("com.android.…")`, so it
# can change an artifact only for a module that applies an Android plugin — and no module here does.
# `android_only_is_safe` re-proves that on every run rather than trusting this comment.
ANDROID_ONLY_VERSIONS = {"agp"}
ANDROID_CONVENTIONS = "build-logic/src/main/kotlin/ee/schimke/composeai/buildlogic/ComposeAiAndroidConventionsPlugin.kt"
GOOGLE_MAVEN = os.environ.get("COMPOSEAI_GOOGLE_MAVEN", "https://dl.google.com/dl/android/maven2")


class Unsure(Exception):
    """Anything the plan cannot decide with confidence. Resolves to "publish everything"."""


def tag_exists(tag):
    return subprocess.run(["git", "rev-parse", "--verify", "-q", tag + "^{commit}"],
                          capture_output=True).returncode == 0


def norm(alias):
    # Gradle treats `-`, `_` and `.` in an alias as the same separator: `kotlinx-serialization-json`
    # is `libs.kotlinx.serialization.json`.
    return re.sub(r"[-_.]", ".", alias)


CATALOG_SECTIONS = ("versions", "libraries", "plugins", "bundles")


def load_catalog(rev):
    if tomllib is None:
        raise Unsure("this Python has no tomllib")
    proc = subprocess.run(["git", "show", f"{rev}:{CATALOG}"], capture_output=True, text=True)
    if proc.returncode != 0:
        raise Unsure(f"cannot read {CATALOG} at {rev}")
    try:
        return tomllib.loads(proc.stdout)
    except Exception as e:  # noqa: BLE001
        raise Unsure(f"cannot parse {CATALOG} at {rev}: {e}")


def resolve_catalog(cat):
    """{accessor: resolved entry}, with every `version.ref` replaced by the version it names.

    Resolving the refs is what makes "a changed version ref dirties every alias using it" fall out of
    a plain comparison: the entry's resolved value moves even though its own text did not.
    """
    versions = cat.get("versions", {})
    if not isinstance(versions, dict):
        raise Unsure("[versions] is not a table")
    out = {}
    for name, v in versions.items():
        out[f"libs.versions.{norm(name)}"] = v

    def resolve_entry(section, name, entry):
        if isinstance(entry, str):
            return entry
        if not isinstance(entry, dict):
            raise Unsure(f"[{section}] {name} has an unexpected shape")
        entry = dict(entry)
        v = entry.get("version")
        if isinstance(v, dict) and "ref" in v:
            if set(v) != {"ref"} or v["ref"] not in versions:
                raise Unsure(f"[{section}] {name} names an unknown version ref")
            entry["version"] = {"resolved": versions[v["ref"]]}
        return entry

    libraries = {}
    for name, entry in cat.get("libraries", {}).items():
        libraries[name] = resolve_entry("libraries", name, entry)
        out[f"libs.{norm(name)}"] = libraries[name]
    for name, entry in cat.get("plugins", {}).items():
        out[f"libs.plugins.{norm(name)}"] = resolve_entry("plugins", name, entry)
    for name, members in cat.get("bundles", {}).items():
        if not isinstance(members, list):
            raise Unsure(f"[bundles] {name} is not a list")
        by_norm = {norm(k): v for k, v in libraries.items()}
        if any(norm(m) not in by_norm for m in members):
            raise Unsure(f"[bundles] {name} names an unknown library")
        out[f"libs.bundles.{norm(name)}"] = [by_norm[norm(m)] for m in members]
    return out


def changed_aliases(tag):
    before, after = load_catalog(tag), load_catalog(head)
    for key in set(before) | set(after):
        if key not in CATALOG_SECTIONS and before.get(key) != after.get(key):
            raise Unsure(f"catalog section [{key}] changed and is not understood")
    a, b = resolve_catalog(before), resolve_catalog(after)
    return {k for k in set(a) | set(b) if a.get(k) != b.get(k)}, after


# Every way a script can name a catalog entry that is not a `libs.x.y` token. A literal lookup is
# mapped back to an accessor; anything that enumerates the catalog or computes the name cannot be.
LITERAL_LOOKUP = re.compile(r'\bfind(Version|Library|Plugin|Bundle)\(\s*"([^"]+)"\s*\)')
ANY_LOOKUP = re.compile(r"\bfind(Version|Library|Plugin|Bundle)\(")
ENUMERATION = re.compile(r"\b(versionAliases|libraryAliases|pluginAliases|bundleAliases)\b")
LOOKUP_PREFIX = {"Version": "libs.versions.", "Library": "libs.", "Plugin": "libs.plugins.",
                 "Bundle": "libs.bundles."}


def catalog_references(path, text):
    """The catalog accessors `text` names, as `libs.…` tokens."""
    refs = set(re.findall(r"\blibs\.[A-Za-z0-9_.]*[A-Za-z0-9_]", text))
    literal = LITERAL_LOOKUP.findall(text)
    if len(literal) != len(ANY_LOOKUP.findall(text)) or ENUMERATION.search(text):
        raise Unsure(f"{path} looks the catalog up by a name the plan cannot read")
    refs |= {LOOKUP_PREFIX[kind] + norm(name) for kind, name in literal}
    return refs


def uses(refs, accessor):
    # `libs.versions.kotlin.get()` names `libs.versions.kotlin`. The prefix match also counts
    # `libs.okio.fakefilesystem` as a use of `libs.okio` — an over-match, which only ever publishes.
    return any(r == accessor or r.startswith(accessor + ".") for r in refs)


def version_tuple(v):
    m = re.fullmatch(r"(\d+(?:\.\d+)*)(-[A-Za-z0-9.-]+)?", v or "")
    if not m:
        raise Unsure(f"cannot compare version {v!r}")
    return tuple(int(x) for x in m.group(1).split(".")), m.group(2)


def android_only_is_safe(cat_head, build_logic_texts):
    """Can an AGP bump be confined to the modules that apply an Android plugin? Proven, not assumed.

    1. AGP's API is referenced only by the Android conventions plugin; every other mention of
       `com.android` in build-logic is a `withPlugin("com.android.…")` string.
    2. The new AGP does not drag a newer Kotlin Gradle plugin onto the convention classpath than the
       catalog pins. Gradle resolves the highest requested version, so an AGP needing a newer KGP
       would silently change the compiler every module is built with.
    """
    for path, text in build_logic_texts.items():
        if not path.startswith("build-logic/src/main/") or path == ANDROID_CONVENTIONS:
            continue
        for line in text.splitlines():
            if "com.android" in line and 'withPlugin("com.android.' not in line:
                print(f"  {path} references AGP outside a withPlugin guard", file=sys.stderr)
                return False
    versions = cat_head.get("versions", {})
    agp, kotlin = versions.get("agp"), versions.get("kotlin")
    if not isinstance(agp, str) or not isinstance(kotlin, str):
        return False
    url = f"{GOOGLE_MAVEN}/com/android/tools/build/gradle/{agp}/gradle-{agp}.pom"
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            pom = response.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001 - any failure resolves to "publish"
        print(f"  could not read the AGP {agp} POM ({e})", file=sys.stderr)
        return False
    kgp = re.findall(
        r"<artifactId>kotlin-gradle-plugin</artifactId>\s*<version>([^<]*)</version>", pom)
    if len(kgp) != pom.count("<artifactId>kotlin-gradle-plugin</artifactId>"):
        print("  cannot read AGP's kotlin-gradle-plugin dependency", file=sys.stderr)
        return False
    ceiling, ceiling_q = version_tuple(kotlin)
    for v in kgp:
        t, q = version_tuple(v)
        # Same numbers, different qualifier: no ordering worth trusting (2.5.0 vs 2.5.0-RC,
        # -RC vs -RC2), so it is treated as newer.
        if t > ceiling or (t == ceiling and q != ceiling_q):
            print(f"  AGP {agp} requires kotlin-gradle-plugin {v}, above the catalog's {kotlin}",
                  file=sys.stderr)
            return False
    return True


def build_scripts():
    """{path: text} for every build script at head, and build-logic's main sources."""
    names = git("ls-tree", "-r", "--name-only", head).split("\n")
    out = {}
    for n in names:
        if SHARED_IGNORED.match(n):
            continue
        if n.endswith((".gradle.kts", ".gradle")) or (
                n.startswith("build-logic/src/main/") and n.endswith((".kt", ".kts", ".java"))):
            out[n] = git("show", f"{head}:{n}")
    return out


def owning_module(path):
    for aid, directory in modules.items():
        if path.startswith(directory.rstrip("/") + "/"):
            return aid
    return None


def catalog_impact(tag):
    """The published modules the catalog change `tag..head` can move.

    Raises Unsure for "everything": a changed alias reached by a shared script, or a diff the plan
    cannot read.
    """
    changed, cat_head = changed_aliases(tag)
    if not changed:
        print(f"  {CATALOG} changed since {tag}, but no entry did", file=sys.stderr)
        return set()
    print(f"  {CATALOG} since {tag}: {', '.join(sorted(changed))}", file=sys.stderr)
    scripts = build_scripts()
    shared_refs = {}
    module_refs = collections.defaultdict(set)
    for path, text in scripts.items():
        refs = catalog_references(path, text)
        aid = owning_module(path)
        if aid is not None:
            module_refs[aid] |= refs
        else:
            shared_refs[path] = refs

    android_modules = None
    impacted = set()
    for accessor in sorted(changed):
        shared_users = sorted(p for p, refs in shared_refs.items() if uses(refs, accessor))
        name = accessor.removeprefix("libs.versions.")
        if shared_users and (
                name not in ANDROID_ONLY_VERSIONS or accessor == name
                or shared_users != ["build-logic/build.gradle.kts"]):
            raise Unsure(f"{accessor} is used by {', '.join(shared_users)}")
        if shared_users:
            if not android_only_is_safe(cat_head, scripts):
                raise Unsure(f"{accessor} cannot be confined to Android modules")
            if android_modules is None:
                android_plugins = {
                    f"libs.plugins.{norm(n)}" for n, e in cat_head.get("plugins", {}).items()
                    if str(e.get("id", "") if isinstance(e, dict) else e).startswith("com.android.")
                }
                android_modules = {
                    aid for aid in modules
                    if any("com.android" in scripts[p] or any(uses(catalog_references(p, scripts[p]), ap)
                                                               for ap in android_plugins)
                           for p in scripts if owning_module(p) == aid)
                }
            print(f"  {accessor}: build-logic only, reaches Android modules "
                  f"({', '.join(sorted(android_modules)) or 'none'})", file=sys.stderr)
            impacted |= android_modules
        users = {aid for aid, refs in module_refs.items() if uses(refs, accessor)}
        if users:
            print(f"  {accessor}: {', '.join(sorted(users))}", file=sys.stderr)
        impacted |= users
    return impacted


# Every changed-path listing below passes `--no-renames`. With rename detection on (git's default),
# `git diff --name-only` names only a moved file's DESTINATION, so a source moved from `src/main` into
# a test source set — or out of build-logic's main tree into `build-logic/src/test` — would read as
# a test-only change and be exempted. Both sides of the move have to be seen.


def changed_since(version, directory):
    """Did anything that ships from `directory` move between the tag for `version` and head?"""
    tag = f"v{version}"
    if not tag_exists(tag):
        print(f"  {directory}: no tag {tag}; publishing", file=sys.stderr)
        return True
    prefix = directory.rstrip("/") + "/"
    out = git("diff", "--no-renames", "--name-only", f"{tag}..{head}", "--", directory)
    out = [f for f in out.split("\n") if f]
    shipped = [f for f in out if not TEST_SOURCES.match(f.removeprefix(prefix))]
    if out and not shipped:
        print(f"  {directory}: only test sources changed since {tag}", file=sys.stderr)
    return bool(shipped)


shared_changed = False
catalog_dirty = {}  # version -> the modules whose baseline is that version and whose aliases moved
for version in sorted(set(recorded.values())):
    tag = f"v{version}"
    if not tag_exists(tag):
        print(f"  no tag {tag}", file=sys.stderr)
        shared_changed = True
        break
    files = [f for f in git("diff", "--no-renames", "--name-only", f"{tag}..{head}").split("\n") if f]
    shared = [f for f in files if SHARED.match(f) and not SHARED_IGNORED.match(f)]
    other = [f for f in shared if f != CATALOG]
    if other:
        print(f"  shared build input(s) changed since {tag}: {', '.join(other[:5])}",
              file=sys.stderr)
        shared_changed = True
        break
    if CATALOG in shared:
        try:
            catalog_dirty[version] = catalog_impact(tag)
        except Unsure as e:
            print(f"  {e}", file=sys.stderr)
            shared_changed = True
            break

if shared_changed:
    print("  a shared build input changed; publishing every module", file=sys.stderr)
    for aid in sorted(modules):
        print(aid)
    sys.exit(0)

dirty = set()
for aid, directory in modules.items():
    if aid not in recorded:
        print(f"  {aid}: never published; publishing", file=sys.stderr)
        dirty.add(aid)
    elif aid in catalog_dirty.get(recorded[aid], ()):
        dirty.add(aid)
    elif changed_since(recorded[aid], directory):
        dirty.add(aid)

# Rule 2: anything depending on a dirty module is dirty too, transitively.
rev = collections.defaultdict(set)
for aid, ds in deps.items():
    for d in ds:
        rev[d].add(aid)
stack = list(dirty)
while stack:
    m = stack.pop()
    for r in rev.get(m, ()):
        if r not in dirty:
            dirty.add(r)
            stack.append(r)

print(f"  {len(dirty)} of {len(modules)} modules publish", file=sys.stderr)
for aid in sorted(dirty):
    print(aid)
PY
