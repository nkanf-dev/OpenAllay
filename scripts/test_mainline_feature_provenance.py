"""Offline negative/identity tests for bounded reusable mainline feature evidence."""
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = spec_from_file_location("mainline_provenance", ROOT / "scripts/plan-ci-verification.py")
shared = module_from_spec(spec)
spec.loader.exec_module(shared)

class MainlineClosureTest(unittest.TestCase):
    def setUp(self):
        self.before, self.after = "a" * 40, "b" * 40
        self.trees = {self.before: {}, self.after: {}}
        self.owners = {}
    def fake_git(self, root, *args):
        if args[0] == "rev-parse": return args[-1][:40] + "\n"
        if args[0] == "ls-tree":
            return "".join("100644 blob " + blob + "\t" + path + "\0" for path, blob in self.trees[args[-1]].items())
        if args[0] == "show":
            sha, path = args[1].split(":", 1)
            if path == "gradle/minecraft-targets.gradle":
                return "def nativeFamilies = ['26.2': '26.2']\ndef nativeFamilyParents = [:]\n"
            return self.owners.get((sha, path), "stable selection owner\n")
        raise AssertionError(args)
    def closure(self, **kwargs):
        with patch.object(shared, "git", self.fake_git):
            return shared.validate_mainline_closure(ROOT, self.before, self.after, **kwargs)
    def test_old_target_not_selected_but_behavior_sdk_builder_deps_unknown_fail_closed(self):
        for path in ["native-builds/early-neoforge/build.gradle", "neoforge/src/targets/1.20.2/java/Metadata.java"]:
            self.trees[self.after] = {path: "c" * 40}
            self.closure()
        for path in ["engine-core/src/main/java/Engine.java", "extension-api/src/main/java/API.java",
                     "distribution/extensions.lock.json", "common/src/main/resources/config.json",
                     "gradle/minecraft-targets/26.2.properties", "build-logic/build.gradle",
                     "scripts/new-packaging-helper.py", "common/src/main/java/dev/openallay/guide/e2e/OtherProbe.java"]:
            self.trees[self.after] = {path: "c" * 40}
            with self.subTest(path=path), self.assertRaises(ValueError): self.closure(fixture_bridge=True)
    def test_mechanical_rename_requires_identical_blobs_no_competing_module(self):
        old, new = "adapters/minecraft-26.2/src/main/java/Binding.java", "adapters/minecraft/src/main/java/Binding.java"
        self.trees[self.before] = {old: "c" * 40}
        self.trees[self.after] = {new: "c" * 40}
        self.closure()
        self.trees[self.after] = {new: "d" * 40}
        with self.assertRaises(ValueError): self.closure()
        self.trees[self.after] = {old: "c" * 40, new: "c" * 40}
        with self.assertRaises(ValueError): self.closure()
    def test_build_owner_only_exact_project_token_rename_allowed(self):
        self.owners[(self.before, "settings.gradle")] = "include('adapters:minecraft-26.2')\n"
        self.owners[(self.after, "settings.gradle")] = "include('adapters:minecraft')\n"
        self.closure()
        self.owners[(self.after, "settings.gradle")] += "includeBuild('native-builds/early-neoforge')\n"
        with self.assertRaisesRegex(ValueError, "selection owner"): self.closure()
    def test_arbitrary_fixture_change_not_reviewed_blob_bridge(self):
        path, blobs = next(iter(shared.FIXTURE_BRIDGE.items()))
        self.trees[self.before] = {path: blobs[0]}
        self.trees[self.after] = {path: blobs[1]}
        with self.assertRaises(ValueError): self.closure()
        self.assertEqual([path], self.closure(fixture_bridge=True)["reviewedFixtureOnlyBridge"])
        self.trees[self.after] = {path: "f" * 40}
        with self.assertRaises(ValueError): self.closure(fixture_bridge=True)

if __name__ == "__main__": unittest.main()
