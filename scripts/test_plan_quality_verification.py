"""Offline behavior tests for mainline failed/affected-client verification."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("quality_planner", ROOT / "scripts/plan-quality-verification.py")
planner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(planner)


class QualityPlanTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="quality-plan-fixture-", dir=ROOT)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.write("common/src/main/java/Feature.java", "production before\n")
        self.write("distribution/extensions.lock.json", "pinned Builder before\n")
        self.git("init", "--quiet")
        self.git("config", "user.name", "Offline fixture")
        self.git("config", "user.email", "offline@example.invalid")
        self.commit("source")
        self.source_sha = self.head()

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "--quiet", "-m", message)

    def head(self):
        return self.git("rev-parse", "HEAD").strip()

    def source_run(self, head=None):
        run = {"id": 123, "head_sha": head or self.source_sha, "run_attempt": 2,
               "path": ".github/workflows/quality.yml", "repository": {"full_name": "owner/OpenAllay"},
               "conclusion": "failure"}
        jobs = [{"id": 456, "name": "Minecraft 26.2", "status": "completed", "conclusion": "success",
                 "head_sha": head or self.source_sha, "run_id": 123, "run_attempt": 2,
                 "html_url": "https://github.com/owner/OpenAllay/actions/runs/123/job/456"},
                {"name": "Packaged client fabric", "conclusion": "failure"}]
        return run, jobs

    def plan(self, mode="clients", loaders="none", **kwargs):
        return planner.make_plan(self.root, mode, loaders, candidate_sha=self.head(), current_run_id="999",
                                 repository="owner/OpenAllay", **kwargs)

    def reuse(self, loaders="fabric", **kwargs):
        run, jobs = self.source_run()
        return self.plan(loaders=loaders, source_run_id="123", source_sha=self.source_sha,
                         source_run=run, source_jobs=jobs, **kwargs)

    def runner_repair(self):
        for relative in ("scripts/run-packaged-builder-acceptance.py", "scripts/test_packaged_builder_acceptance.py",
                         "scripts/plan-quality-verification.py", "scripts/run-ci-software-graphics.py",
                         ".github/workflows/quality.yml",
                         "docs/quality-dispatch.md", "common/src/test/java/Test.java"):
            self.write(relative, "runner/tests/docs repair\n")
        self.commit("non-production repair")

    def receipt_args(self, plan):
        artifact = self.root / "artifacts"
        artifact.mkdir()
        records = {}
        for loader in planner.LOADERS:
            name = "openallay-" + loader + "-26.2-0.4.1.jar"
            content = ("original " + loader + " production JAR bytes").encode()
            (artifact / name).write_bytes(content)
            records[name] = hashlib.sha256(content).hexdigest()
        self.write("artifacts/SHA256SUMS", "".join(sha + "  " + name + "\n" for name, sha in records.items()))
        run, jobs = self.source_run()
        args = argparse.Namespace(report=self.write("plan.json", json.dumps(plan)), receipt_loader="fabric",
                                  source_run_json=self.write("run.json", json.dumps(run)),
                                  source_jobs_json=self.write("jobs.json", json.dumps(jobs)),
                                  repository="owner/OpenAllay", artifact_directory=artifact,
                                  receipt=self.root / "receipt.json")
        return args, records

    def test_manual_default_selects_no_expensive_jobs_and_valid_inert_matrix(self):
        plan = self.plan()
        self.assertFalse(plan["run_verify"])
        self.assertFalse(plan["run_client"])
        self.assertEqual([], plan["clients"])
        self.assertEqual(["fabric"], [row["loader"] for row in plan["client_matrix"]["include"]])

    def test_full_mode_runs_one_verifier_and_both_complete_clients_fresh(self):
        plan = self.plan("full")
        self.assertTrue(plan["run_verify"])
        self.assertTrue(plan["run_client"])
        self.assertEqual(["fabric", "neoforge"], plan["client_loaders"])
        self.assertTrue(all(row["source_run_id"] == "999" and row["source_sha"] == self.source_sha
                            and not row["reused"] for row in plan["clients"]))

    def test_selects_exact_failed_or_affected_loader_not_both_by_default(self):
        for value, expected in (("fabric", ["fabric"]), ("neoforge", ["neoforge"]),
                                ("fabric,neoforge", ["fabric", "neoforge"]),
                                ("neoforge,fabric", ["neoforge", "fabric"])):
            with self.subTest(value=value):
                plan = self.reuse(value)
                self.assertFalse(plan["run_verify"])
                self.assertEqual(expected, plan["client_loaders"])
                self.assertEqual(expected, [row["loader"] for row in plan["client_matrix"]["include"]])
                self.assertTrue(all(row["source_job"]["id"] == 456 for row in plan["clients"]))

    def test_rejects_invalid_modes_loader_tokens_and_unused_source_inputs(self):
        for value in ("", "all", "forge", "fabric,fabric", "fabric,", "fabric, neoforge", " fabric", "none,fabric"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.plan(loaders=value)
        with self.assertRaisesRegex(ValueError, "mode"):
            self.plan("bogus")
        for mode, loaders in (("full", "fabric"), ("full", "none"), ("clients", "none")):
            with self.subTest(mode=mode, loaders=loaders), self.assertRaises(ValueError):
                self.plan(mode, loaders, source_run_id="123", source_sha=self.source_sha)

    def test_selected_clients_require_available_exact_source_and_prior_run(self):
        run, jobs = self.source_run()
        for run_id, sha in (("", ""), ("123", "short"), ("123", "a" * 40),
                            ("999", self.source_sha), ("0123", self.source_sha)):
            with self.subTest(run_id=run_id, sha=sha), self.assertRaises(ValueError):
                self.plan(loaders="fabric", source_run_id=run_id, source_sha=sha, source_run=run, source_jobs=jobs)

    def test_runner_tests_docs_workflow_repair_preserves_old_jar_source(self):
        self.runner_repair()
        plan = self.reuse()
        self.assertNotEqual(self.source_sha, plan["candidate_sha"])
        self.assertEqual(self.source_sha, plan["clients"][0]["source_sha"])
        self.assertEqual(2, plan["clients"][0]["source_job"]["run_attempt"])
        self.assertIn("scripts/run-packaged-builder-acceptance.py", plan["changed_paths"])

    def test_production_gradle_sdk_engine_builder_classpath_unknown_changes_fail_closed(self):
        paths = ("common/src/main/java/Feature.java", "fabric/build.gradle", "neoforge/src/main/java/Native.java",
                 "engine-core/src/main/java/Engine.java", "extension-api/src/main/java/API.java",
                 "runtime-rhino/src/main/java/Runtime.java", "build-logic/build.gradle", "gradle/libs.versions.toml",
                 "gradle/minecraft-targets/26.2.properties", "adapters/minecraft/src/main/java/Game.java",
                 "distribution/extensions.lock.json", "scripts/prepare-distribution.py",
                 "scripts/stage-ci-client-production.py", "scripts/new-classpath-helper.py", "unknown-input.txt")
        for relative in paths:
            with self.subTest(path=relative):
                self.git("reset", "--hard", self.source_sha)
                self.write(relative, "changed package input\n")
                self.commit("package input repair")
                with self.assertRaisesRegex(ValueError, re.escape(relative)):
                    self.reuse()

    def test_other_target_pin_is_not_a_26_2_package_input(self):
        self.write("gradle/minecraft-targets/1.20.2.properties", "minecraft_version=1.20.2\n")
        self.commit("other target repair")
        self.assertTrue(self.reuse()["clients"][0]["reused"])

    def test_move_of_production_source_to_docs_cannot_hide_package_change(self):
        (self.root / "docs").mkdir()
        self.git("mv", "common/src/main/java/Feature.java", "docs/Feature.java")
        self.commit("rename production")
        with self.assertRaisesRegex(ValueError, "Feature"):
            self.reuse()

    def test_git_candidate_must_be_exact_checked_out_commit(self):
        self.runner_repair()
        with self.assertRaisesRegex(ValueError, "checked-out"):
            planner.make_plan(self.root, "clients", "none", candidate_sha=self.source_sha,
                              current_run_id="999", repository="owner/OpenAllay")

    def test_source_run_repository_workflow_and_head_must_match(self):
        for field, value in (("id", 321), ("head_sha", "f" * 40),
                             ("path", ".github/workflows/minecraft-native.yml"),
                             ("repository", {"full_name": "other/repository"})):
            run, jobs = self.source_run()
            run[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                planner.validate_quality_source(run, jobs, "123", self.source_sha, "owner/OpenAllay")

    def test_source_job_name_success_head_attempt_and_url_must_match(self):
        for field, value in (("name", "Native 26.2"), ("conclusion", "failure"), ("status", "in_progress"),
                             ("head_sha", "f" * 40), ("run_id", 321), ("run_attempt", 1),
                             ("id", 0), ("html_url", "https://github.com/owner/OpenAllay/actions/runs/123/job/987")):
            run, jobs = self.source_run()
            jobs[0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                planner.validate_quality_source(run, jobs, "123", self.source_sha, "owner/OpenAllay")
        for attempt in (None, 0, True, "2"):
            run, jobs = self.source_run()
            run["run_attempt"] = jobs[0]["run_attempt"] = attempt
            with self.subTest(attempt=attempt), self.assertRaises(ValueError):
                planner.validate_quality_source(run, jobs, "123", self.source_sha, "owner/OpenAllay")
        run, jobs = self.source_run()
        jobs.append(dict(jobs[0]))
        with self.assertRaisesRegex(ValueError, "one exact"):
            planner.validate_quality_source(run, jobs, "123", self.source_sha, "owner/OpenAllay")

    def test_prior_aggregate_failure_does_not_repeat_successful_verifier(self):
        plan = self.reuse("fabric,neoforge")
        self.assertFalse(plan["run_verify"])
        self.assertEqual("success", plan["clients"][0]["source_job"]["conclusion"])

    def test_receipt_keeps_candidate_source_job_attempt_and_both_original_jar_sha256s(self):
        self.runner_repair()
        plan = self.reuse()
        args, records = self.receipt_args(plan)
        receipt = planner.write_client_receipt(args)
        self.assertEqual(records, receipt["jar_sha256"])
        self.assertEqual(self.source_sha, receipt["source_sha"])
        self.assertEqual(plan["candidate_sha"], receipt["candidate_sha"])
        self.assertNotEqual(receipt["source_sha"], receipt["candidate_sha"])
        self.assertEqual({"id": 456, "url": "https://github.com/owner/OpenAllay/actions/runs/123/job/456",
                          "run_attempt": 2, "conclusion": "success"}, receipt["source_job"])
        self.assertEqual("client-production-" + self.source_sha, receipt["artifact_name"])
        self.assertEqual(receipt, json.loads(args.receipt.read_text()))

    def test_receipt_rejects_original_jar_byte_change_even_on_unselected_loader(self):
        args, records = self.receipt_args(self.reuse())
        for name in records:
            jar = args.artifact_directory / name
            original = jar.read_bytes()
            jar.write_bytes(b"wrong production bytes")
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "bytes"):
                planner.write_client_receipt(args)
            jar.write_bytes(original)

    def test_receipt_rejects_wrong_target_and_missing_original_loader(self):
        args, records = self.receipt_args(self.reuse())
        manifest = args.artifact_directory / "SHA256SUMS"
        first = next(iter(records))
        manifest.write_text(records[first] + "  " + first + "\n")
        with self.assertRaisesRegex(ValueError, "each loader"):
            planner.write_client_receipt(args)
        renamed = first.replace("26.2", "26.3")
        (args.artifact_directory / first).rename(args.artifact_directory / renamed)
        manifest.write_text(records[first] + "  " + renamed + "\n")
        with self.assertRaisesRegex(ValueError, "each loader"):
            planner.write_client_receipt(args)

    def test_receipt_fails_if_source_job_changes_attempt_after_plan(self):
        args, _ = self.receipt_args(self.reuse())
        run, jobs = self.source_run()
        run["run_attempt"] = jobs[0]["run_attempt"] = 3
        args.source_run_json.write_text(json.dumps(run))
        args.source_jobs_json.write_text(json.dumps(jobs))
        with self.assertRaisesRegex(ValueError, "changed after selection"):
            planner.write_client_receipt(args)

    def test_receipt_fresh_pr_records_tested_merge_sha_separate_from_run_head(self):
        self.runner_repair()
        run_head = self.source_sha
        plan = self.plan("full", current_run_head_sha=run_head)
        args, _ = self.receipt_args(plan)
        run, jobs = self.source_run(head=run_head)
        run["id"] = jobs[0]["run_id"] = 999
        jobs[0]["html_url"] = "https://github.com/owner/OpenAllay/actions/runs/999/job/456"
        args.source_run_json.write_text(json.dumps(run))
        args.source_jobs_json.write_text(json.dumps(jobs))
        receipt = planner.write_client_receipt(args)
        self.assertEqual(plan["candidate_sha"], receipt["source_sha"])
        self.assertEqual(run_head, receipt["source_run_head_sha"])
        self.assertNotEqual(receipt["source_sha"], receipt["source_run_head_sha"])

    def test_planning_queries_actual_quality_run_and_all_job_pages(self):
        run, jobs = self.source_run()
        with patch.object(planner.shared, "command", wraps=planner.shared.command) as command:
            command.side_effect = lambda *args: (json.dumps([{"jobs": jobs}]) if "--paginate" in args
                                                 else json.dumps(run) if args[0] == "gh"
                                                 else command._mock_wraps(*args))
            plan = self.plan(loaders="fabric", source_run_id="123", source_sha=self.source_sha)
        api_calls = [call.args for call in command.call_args_list if call.args[0] == "gh"]
        self.assertEqual([("gh", "api", "repos/owner/OpenAllay/actions/runs/123"),
                          ("gh", "api", "--paginate", "--slurp",
                           "repos/owner/OpenAllay/actions/runs/123/jobs?filter=latest&per_page=100")], api_calls)
        self.assertEqual(456, plan["clients"][0]["source_job"]["id"])

    def test_cli_emits_real_full_and_empty_selection_outputs(self):
        for mode, run_verify, loaders in (("clients", "false", []), ("full", "true", ["fabric", "neoforge"])):
            output, report = self.root / (mode + "-outputs"), self.root / (mode + "-plan.json")
            result = subprocess.run([sys.executable, "-B", str(ROOT / "scripts/plan-quality-verification.py"),
                                     "--root", str(self.root), "--mode", mode, "--candidate-sha", self.source_sha,
                                     "--current-run-id", "999", "--repository", "owner/OpenAllay",
                                     "--github-output", str(output), "--report", str(report)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            outputs = dict(line.split("=", 1) for line in output.read_text().splitlines())
            self.assertEqual(run_verify, outputs["run_verify"])
            self.assertEqual("true" if loaders else "false", outputs["run_client"])
            self.assertEqual(loaders, json.loads(report.read_text())["client_loaders"])
            self.assertTrue(json.loads(outputs["client_matrix"])["include"])

    def test_cli_receipt_reads_plan_and_original_bytes(self):
        args, records = self.receipt_args(self.reuse())
        result = subprocess.run([sys.executable, "-B", str(ROOT / "scripts/plan-quality-verification.py"),
                                 "--repository", args.repository, "--report", str(args.report),
                                 "--receipt-loader", args.receipt_loader, "--artifact-directory", str(args.artifact_directory),
                                 "--source-run-json", str(args.source_run_json), "--source-jobs-json", str(args.source_jobs_json),
                                 "--receipt", str(args.receipt)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(records, json.loads(args.receipt.read_text())["jar_sha256"])


if __name__ == "__main__":
    unittest.main()
