"""Offline behavior checks for selective native builds and real artifact reuse."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("plan_ci_verification", ROOT / "scripts/plan-ci-verification.py")
planner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(planner)


class PlanTest(unittest.TestCase):
    def setUp(self):
        # Fixtures stay in the source packet/checkout and never use a full repo copy.
        self.temporary = tempfile.TemporaryDirectory(prefix="ci-plan-fixture-", dir=ROOT)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for target in planner.NATIVE_TARGETS:
            self.write("gradle/minecraft-targets/" + target + ".properties", "minecraft_version=" + target + "\n")
        self.write("common/src/main/java/Feature.java", "production before\n")
        self.write("distribution/extensions.lock.json", "pinned Builder before\n")
        self.write("scripts/prepare-distribution.py", "build helper before\n")
        self.git("init", "--quiet")
        self.git("config", "user.name", "Offline fixture")
        self.git("config", "user.email", "offline@example.invalid")
        self.commit("source")
        self.source_sha = self.git("rev-parse", "HEAD").strip()

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", message)

    def source_run(self, target="26.3", conclusion="success", head=None):
        run = {"id": 123, "head_sha": head or self.source_sha,
               "path": ".github/workflows/minecraft-native.yml", "run_attempt": 2,
               "repository": {"full_name": "owner/OpenAllay"}}
        jobs = [{"id": 456, "name": "Native " + target, "conclusion": conclusion,
                 "status": "completed", "run_attempt": 2,
                 "head_sha": head or self.source_sha, "run_id": 123,
                 "html_url": "https://github.com/owner/OpenAllay/actions/runs/123/job/456"}]
        return run, jobs

    def plan(self, native="all", smoke="default", **kwargs):
        return planner.make_plan(self.root, native, smoke, candidate_sha=self.git("rev-parse", "HEAD").strip(),
                                 current_run_id="999", repository="owner/OpenAllay", **kwargs)

    def reuse(self, native="none", smoke="26.3/fabric", target="26.3", **kwargs):
        run, jobs = self.source_run(target)
        return self.plan(native, smoke, source_run_id="123", source_sha=self.source_sha,
                         source_run=run, source_jobs=jobs, **kwargs)

    def test_default_full_native_and_only_six_key_smokes(self):
        plan = self.plan()
        self.assertEqual(list(planner.NATIVE_TARGETS), plan["native_targets"])
        self.assertEqual(15, len(plan["native_targets"]))
        self.assertEqual(6, len(plan["smoke_matrix"]["include"]))
        self.assertNotIn("26.2", plan["native_targets"])
        self.assertTrue(all(row["source_sha"] == self.source_sha for row in plan["smoke_targets"]))

    def test_dispatch_selects_only_failed_native_jobs_and_one_exact_loader(self):
        plan = self.plan("1.20.2,1.20.3", "1.20.2/neoforge")
        self.assertEqual(["1.20.2", "1.20.3"], plan["native_targets"])
        self.assertEqual(["1.20.2", "1.20.3"], plan["native_matrix"]["target"])
        self.assertEqual([("1.20.2", "neoforge")],
                         [(row["target"], row["loader"]) for row in plan["smoke_targets"]])
        self.assertEqual("999", plan["smoke_targets"][0]["source_run_id"])

    def test_rejects_unknown_profile_and_profile_outside_native_representatives(self):
        for value in ("bogus", "../26.3", "1.20.5", "26.2"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.plan(value, "none")
        (self.root / "gradle/minecraft-targets/26.3.properties").unlink()
        with self.assertRaisesRegex(ValueError, "profile"):
            self.plan("26.3", "none")

    def test_rejects_duplicates_empty_tokens_unknown_loader_and_smoke_target(self):
        for native, smoke in (("26.3,26.3", "none"), ("26.3,", "none"),
                              ("all,26.3", "none"), ("none", "26.3/fabric,26.3/fabric"),
                              ("all", "26.3/forge"), ("all", "bogus/fabric")):
            with self.subTest(native=native, smoke=smoke), self.assertRaises(ValueError):
                self.plan(native, smoke)

    def test_empty_selection_has_false_guards_and_valid_nonempty_matrix(self):
        plan = self.plan("none", "none")
        self.assertEqual([], plan["native_targets"])
        self.assertEqual([], plan["smoke_targets"])
        self.assertFalse(plan["run_native"])
        self.assertFalse(plan["run_smoke"])
        self.assertTrue(plan["native_matrix"]["target"])
        self.assertTrue(plan["smoke_matrix"]["include"])

    def test_smoke_requires_its_selected_native_build_or_verified_reuse_not_global_native_success(self):
        with self.assertRaisesRegex(ValueError, "26.3.*source"):
            self.plan("1.20.2,1.20.3", "26.3/fabric")
        plan = self.reuse(native="1.20.2,1.20.3")
        self.assertTrue(plan["run_native"])
        self.assertTrue(plan["run_smoke"])
        row = plan["smoke_targets"][0]
        self.assertEqual("123", row["source_run_id"])
        self.assertEqual(self.source_sha, row["source_sha"])
        self.assertTrue(row["reused"])
        self.assertEqual(456, row["source_job"]["id"])

    def test_docs_tests_workflow_and_runner_changes_preserve_actual_old_jar_source(self):
        self.write("docs/dispatch.md", "dispatch repair\n")
        self.write("extension-api/README.md", "SDK documentation repair\n")
        self.write("adapters/minecraft-26.2/README.md", "historical native receipt wording\n")
        self.write("common/src/test/java/Test.java", "new test\n")
        self.write(".github/workflows/minecraft-native.yml", "fixed workflow\n")
        self.write("scripts/run-ci-game-workflow.py", "fixed runner\n")
        self.commit("non-production repair")
        plan = self.reuse()
        self.assertNotEqual(self.source_sha, plan["candidate_sha"])
        self.assertEqual(self.source_sha, plan["smoke_targets"][0]["source_sha"])
        self.assertIn("scripts/run-ci-game-workflow.py", plan["changed_paths"])
        self.assertEqual(456, plan["smoke_targets"][0]["source_job"]["id"])

    def test_production_native_dependency_builder_and_unknown_changes_block_reuse(self):
        paths = ("common/src/main/java/Feature.java", "fabric/build.gradle",
                 "adapters/minecraft-26.2/src/targets/26.1/java/Native.java",
                 "gradle/libs.versions.toml", "distribution/extensions.lock.json",
                 "scripts/prepare-distribution.py", "LICENSE", ".gitattributes", "unknown-input.txt")
        for relative in paths:
            with self.subTest(relative=relative):
                self.git("reset", "--hard", self.source_sha)
                self.write(relative, "changed production input\n")
                self.commit("changed input")
                with self.assertRaisesRegex(ValueError, "changed.*" + relative.replace(".", r"\.")):
                    self.reuse()

    def test_other_profile_change_does_not_invalidate_unaffected_target(self):
        self.write("gradle/minecraft-targets/1.20.2.properties", "minecraft_version=1.20.2\nfixed_dependency=true\n")
        self.commit("1.20.2 pin repair")
        self.assertTrue(self.reuse()["smoke_targets"][0]["reused"])
        with self.assertRaisesRegex(ValueError, "changed.*1.20.2.properties"):
            self.reuse(smoke="1.20.2/fabric", target="1.20.2")

    def test_rename_from_production_to_docs_is_not_allowed_to_hide_changed_input(self):
        self.git("mv", "common/src/main/java/Feature.java", "Feature.java")
        self.commit("move production input")
        with self.assertRaisesRegex(ValueError, "changed"):
            self.reuse()

    def test_reuse_rejects_invalid_sha_missing_commit_and_unused_inputs(self):
        run, jobs = self.source_run()
        for sha in ("short", "a" * 40):
            with self.subTest(sha=sha), self.assertRaises(ValueError):
                self.plan("none", "26.3/fabric", source_run_id="123", source_sha=sha,
                          source_run=run, source_jobs=jobs)
        with self.assertRaisesRegex(ValueError, "unused"):
            self.plan("all", "default", source_run_id="123", source_sha=self.source_sha,
                      source_run=run, source_jobs=jobs)

    def test_prior_run_and_exact_source_job_must_be_real_and_successful(self):
        for field, value in (("head_sha", "f" * 40), ("id", 321),
                             ("path", ".github/workflows/quality.yml"),
                             ("repository", {"full_name": "other/repository"})):
            run, jobs = self.source_run()
            run[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                planner.validate_source_job(run, jobs, "123", self.source_sha,
                                            "owner/OpenAllay", "26.3")
        for field, value in (("conclusion", "failure"), ("status", "in_progress"),
                             ("name", "Native 1.20.2"), ("head_sha", "f" * 40),
                             ("run_attempt", 1), ("run_id", 321)):
            run, jobs = self.source_run()
            jobs[0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                planner.validate_source_job(run, jobs, "123", self.source_sha,
                                            "owner/OpenAllay", "26.3")

    def test_aggregate_failure_does_not_invalidate_successful_target_source_job(self):
        run, jobs = self.source_run()
        run["conclusion"] = "failure"
        jobs.append({"name": "Native 1.20.2", "conclusion": "failure"})
        source_job = planner.validate_source_job(run, jobs, "123", self.source_sha,
                                                "owner/OpenAllay", "26.3")
        self.assertEqual(456, source_job["id"])

    def test_checksum_receipt_preserves_exact_jar_sha_and_fails_changed_bytes(self):
        import hashlib
        artifact = self.root / "artifacts"
        artifact.mkdir()
        jar = artifact / "openallay-fabric-26.3-test.jar"
        jar.write_bytes(b"actual production JAR bytes")
        sha = hashlib.sha256(jar.read_bytes()).hexdigest()
        (artifact / "SHA256SUMS").write_text(sha + "  " + jar.name + "\n")
        self.assertEqual({jar.name: sha}, planner.verify_artifact_checksums(artifact))
        jar.write_bytes(b"changed bytes")
        with self.assertRaisesRegex(ValueError, "bytes"):
            planner.verify_artifact_checksums(artifact)

    def test_pr_fresh_jar_records_tested_merge_sha_and_action_run_head_separately(self):
        self.write("docs/pr.md", "branch contribution\n")
        self.commit("branch head")
        branch_head = self.git("rev-parse", "HEAD").strip()
        self.write("docs/merge.md", "tested merge\n")
        self.commit("tested merge")
        plan = self.plan("26.3", "26.3/fabric", current_run_head_sha=branch_head)
        row = plan["smoke_targets"][0]
        self.assertEqual(plan["candidate_sha"], row["source_sha"])
        self.assertEqual(branch_head, row["source_run_head_sha"])
        self.assertNotEqual(row["source_sha"], row["source_run_head_sha"])
        run, jobs = self.source_run(head=branch_head)
        source_job = planner.validate_source_job(run, jobs, "123", row["source_sha"],
                                                "owner/OpenAllay", "26.3", branch_head)
        self.assertEqual(456, source_job["id"])

    def test_receipt_records_candidate_source_job_and_actual_original_jar_checksum(self):
        import argparse
        import hashlib
        self.write("docs/fix.md", "runner-only change\n")
        self.commit("non-production change")
        plan = self.reuse()
        artifact = self.root / "artifacts"
        artifact.mkdir()
        jar = artifact / "openallay-fabric-26.3-test.jar"
        jar.write_bytes(b"production JAR bytes")
        sha = hashlib.sha256(jar.read_bytes()).hexdigest()
        (artifact / "SHA256SUMS").write_text(sha + "  " + jar.name + "\n")
        run, jobs = self.source_run()
        report, run_json, jobs_json = [self.root / name for name in ("plan.json", "run.json", "jobs.json")]
        for path, content in ((report, plan), (run_json, run), (jobs_json, jobs)):
            path.write_text(json.dumps(content))
        args = argparse.Namespace(report=report, receipt_target="26.3", receipt_loader="fabric",
                                  source_run_json=run_json, source_jobs_json=jobs_json,
                                  repository="owner/OpenAllay", artifact_directory=artifact,
                                  receipt=self.root / "receipt.json")
        evidence = planner.write_smoke_receipt(args)
        self.assertEqual(sha, evidence["jar_sha256"][jar.name])
        self.assertEqual(self.source_sha, evidence["source_sha"])
        self.assertEqual(plan["candidate_sha"], evidence["candidate_sha"])
        self.assertNotEqual(evidence["candidate_sha"], evidence["source_sha"])
        self.assertEqual(456, evidence["source_job"]["id"])
        self.assertEqual("success", evidence["source_job"]["conclusion"])
        jobs[0]["conclusion"] = "failure"
        jobs_json.write_text(json.dumps(jobs))
        with self.assertRaisesRegex(ValueError, "not successful"):
            planner.write_smoke_receipt(args)

    def test_sha_manifest_rejects_traversal_duplicate_record_and_symlink(self):
        import hashlib
        artifact = self.root / "artifacts"
        artifact.mkdir()
        jar = artifact / "openallay-fabric-26.3-test.jar"
        jar.write_bytes(b"production JAR bytes")
        record = hashlib.sha256(jar.read_bytes()).hexdigest() + "  " + jar.name + "\n"
        manifest = artifact / "SHA256SUMS"
        for text in (record + record, record.replace(jar.name, "../" + jar.name), ""):
            manifest.write_text(text)
            with self.subTest(text=text), self.assertRaises(ValueError):
                planner.verify_artifact_checksums(artifact)
        jar.unlink()
        jar.symlink_to(self.root / "common/src/main/java/Feature.java")
        manifest.write_text(record)
        with self.assertRaises(ValueError):
            planner.verify_artifact_checksums(artifact)

    def test_command_emits_real_selection_and_guarded_matrix_outputs(self):
        output = self.root / "github-output"
        report = self.root / "plan.json"
        result = subprocess.run(["python3", "-B", str(ROOT / "scripts/plan-ci-verification.py"),
                                 "--root", str(self.root), "--native-targets", "1.20.2,1.20.3",
                                 "--smoke-targets", "1.20.3/fabric", "--candidate-sha", self.source_sha,
                                 "--current-run-id", "999", "--repository", "owner/OpenAllay",
                                 "--github-output", str(output), "--report", str(report)],
                                capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        parsed = json.loads(report.read_text())
        self.assertEqual(["1.20.2", "1.20.3"], parsed["native_targets"])
        outputs = dict(line.split("=", 1) for line in output.read_text().splitlines())
        self.assertEqual("true", outputs["run_native"])
        self.assertEqual("true", outputs["run_smoke"])
        self.assertEqual(["1.20.2", "1.20.3"], json.loads(outputs["native_matrix"])["target"])


if __name__ == "__main__":
    unittest.main()
