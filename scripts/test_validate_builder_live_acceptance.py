import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("validate-builder-live-acceptance.py")
SPEC = importlib.util.spec_from_file_location("validate_builder_live_acceptance", SCRIPT)
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value), encoding="utf-8")


def state(block, **properties):
    return {"id": "minecraft:" + block, "properties": properties}


class LiveEvidenceValidatorTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repo = Path(self.temporary.name)
        self.copy_dir = self.repo / "build/e2e/copy"
        self.undo_dir = self.copy_dir / "phases/undo"
        self.game = self.copy_dir / "game"
        self.world = "openallay-builder-fabric-test"
        self.anchor = {"x": 0, "y": -61, "z": 0}
        self.copy_id = "native-copy-id"
        self.undo_id = "native-undo-id"
        self.template = {"format": "openallay:structure", "version": 1, "size": [5, 3, 5],
                         "includesAir": False, "palette": [], "blocks": []}
        self.source = {}
        for x in range(5):
            for z in range(5):
                self.source[(x, 0, z)] = state("stone_bricks")
        self.source[(2, 1, 0)] = state("oak_door", facing="north", half="lower")
        self.source[(2, 2, 0)] = state("oak_door", facing="north", half="upper")
        self.source[(2, 1, 2)] = state("chest", facing="north", type="single")
        self.source[(0, 1, 3)] = state("oak_stairs", facing="east", half="bottom")
        self.copy_entries = []
        copy_checks, undo_checks = [], []
        for relative, image in self.source.items():
            x, y, z = relative
            index = len(self.template["palette"])
            self.template["palette"].append(image)
            self.template["blocks"].append({"pos": [x, y, z], "state": index})
            name = "live-platform-" + str(x) + "-" + str(z) if y == 0 else "live-source-" + str(index)
            check = self.check(name, (x, -61 + y, z), image)
            copy_checks.append(check)
            undo_checks.append(copy.deepcopy(check))
            target = (8 + 4 - z, -61 + y, x)
            rotated = validator.rotate_state(image)
            before = state("grass_block") if y == 0 else state("air")
            entry = {"position": list(target), "before": before,
                     "intended": rotated, "verified": rotated, "sequence": len(self.copy_entries)}
            self.copy_entries.append(entry)
            copy_checks.append(self.check("live-copy-" + str(index), target, rotated))
            undo_checks.append(self.check("live-undo-" + str(index), target, before))
        write_json(self.game / "config/openallay-builder/templates/actual.json", self.template)
        self.copy_journal = self.journal(self.copy_id, "Native model copy", self.copy_entries)
        self.copy_path = self.game / "config/openallay-builder/journals" / (self.copy_id + ".json")
        write_json(self.copy_path, self.copy_journal)
        self.phase(self.copy_dir, "builder-live-copy", copy_checks,
                   'var template=b.load_template("actual"); b.paste_structure(template,8,-61,0,{rotation:90}); return {status:b.finish()};',
                   {"status": {"operationId": self.copy_id, "state": "completed"}},
                   ("template-save", "template-load", "write-readback"))
        undo_entries = [{"position": entry["position"], "before": entry["verified"],
                         "intended": entry["before"], "verified": entry["before"], "sequence": index}
                        for index, entry in enumerate(self.copy_entries)]
        self.undo_journal = self.journal(self.undo_id, "Undo " + self.copy_id, undo_entries)
        self.undo_path = self.game / "config/openallay-builder/journals" / (self.undo_id + ".json")
        write_json(self.undo_path, self.undo_journal)
        self.phase(self.undo_dir, "builder-live-undo", undo_checks,
                   'return b.undo("' + self.copy_id + '");',
                   {"undo": {"operationId": self.undo_id, "restored": len(self.copy_entries), "conflicts": [], "uncertain": []}},
                   ("undo-readback",), resume=True)

    def check(self, name, pos, image):
        return {"name": name, **dict(zip(("x", "y", "z"), pos)), "passed": True, "observed": True,
                "actualId": image["id"], "expectedProperties": image["properties"]}

    def journal(self, identifier, label, entries):
        return {"format": "openallay:operation-journal", "version": 1, "id": identifier,
                "worldId": "native-world-saved-data-uuid", "dimension": "minecraft:overworld",
                "label": label, "status": "COMPLETED", "createdAt": 1, "updatedAt": 2,
                "detail": "All journaled writes have server readback", "entries": entries}

    def phase(self, directory, scenario, checks, source, result, evidence, resume=False):
        report = {"scenario": scenario, "outcome": "COMPLETED", "requestId": scenario, "sessionId": scenario,
                  "nativeAcceptance": {"outcome": "PASSED", "worldName": self.world,
                     "survival": True, "cheatsOff": True, "independentAnchor": self.anchor,
                     "oracle": "independent-integrated-server-owner-thread-readback", "checks": checks}}
        trace = {"schemaVersion": 1, "requestId": scenario, "sessionId": scenario, "finalState": "COMPLETED",
                 "events": [{"type": "tool_call", "payload": {"toolId": validator.JS_TOOL, "arguments": {"source": source}}},
                            {"type": "tool_result", "payload": {"toolId": validator.JS_TOOL, "failure": False,
                              "result": {"status": "success", "value": {"preview": result, "evidence": [
                                {"sourceId": "openallay_builder:" + item} for item in evidence]}}}}]}
        manifest = {"scenario": scenario, "noGameLaunched": False, "gameDirectory": str(self.game),
                    "world": self.world, "report": str(directory / "report.json"), "trace": str(directory / "trace.json")}
        if resume:
            manifest["resumeFrom"] = str(self.copy_dir)
        write_json(directory / "launch.json", manifest)
        write_json(directory / "report.json", report)
        write_json(directory / "trace.json", trace)

    def mutate(self, path, function):
        value = validator.read_json(path)
        function(value)
        write_json(path, value)

    def test_copy_selects_actual_target_journal_and_clean_inverse_undo(self):
        proof = validator.validate_copy(self.copy_dir, self.repo)[0]
        self.assertEqual(self.copy_id, proof["exactCopyOpId"])
        self.assertEqual(29, proof["changedEntryCount"])
        self.assertEqual("structured-production-tool-result", proof["operationIdLink"])
        undo = validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)
        self.assertEqual(self.undo_id, undo["exactUndoOpId"])
        self.assertEqual(29, undo["restored"])

    def test_returned_claim_without_native_target_journal_never_passes(self):
        self.copy_path.unlink()
        with self.assertRaisesRegex(ValueError, "unambiguous"):
            validator.validate_copy(self.copy_dir, self.repo)

    def test_native_orientation_wrong_rejects_even_passed_report_and_comment(self):
        self.mutate(self.copy_path, lambda journal: journal["entries"][-1]["verified"]["properties"].update(facing="east"))
        with self.assertRaisesRegex(ValueError, "unambiguous"):
            validator.validate_copy(self.copy_dir, self.repo)

    def test_any_failed_javascript_or_wrong_request_trace_rejects(self):
        trace = self.copy_dir / "trace.json"
        self.mutate(trace, lambda value: value["events"][-1]["payload"].update(failure=True))
        with self.assertRaisesRegex(ValueError, "Tool failed"):
            validator.validate_copy(self.copy_dir, self.repo)
        self.mutate(trace, lambda value: value.update(requestId="unrelated"))
        with self.assertRaisesRegex(ValueError, "report request"):
            validator.validate_copy(self.copy_dir, self.repo)

    def test_combined_source_copy_or_duplicate_target_journal_rejects(self):
        duplicate = copy.deepcopy(self.copy_journal)
        duplicate["id"] = "duplicate-copy"
        write_json(self.copy_path.with_name("duplicate-copy.json"), duplicate)
        with self.assertRaisesRegex(ValueError, "found 2"):
            validator.validate_copy(self.copy_dir, self.repo)
        self.copy_path.with_name("duplicate-copy.json").unlink()
        self.mutate(self.copy_path, lambda journal: journal["entries"].append({
            "position": [0, -61, 0], "before": state("grass_block"),
            "intended": state("stone_bricks"), "verified": state("stone_bricks")}))
        with self.assertRaisesRegex(ValueError, "unambiguous"):
            validator.validate_copy(self.copy_dir, self.repo)

    def test_unique_native_journal_is_fallback_when_model_omits_operation_id(self):
        self.mutate(self.copy_dir / "trace.json", lambda trace: trace["events"][-1]["payload"]["result"]["value"].update(preview="built"))
        self.assertEqual("unique-native-target-journal", validator.validate_copy(self.copy_dir, self.repo)[0]["operationIdLink"])

    def test_zero_restore_conflicts_wrong_journal_and_manual_clear_do_not_pass(self):
        trace = self.undo_dir / "trace.json"
        self.mutate(trace, lambda value: value["events"][-1]["payload"]["result"]["value"]["preview"]["undo"].update(restored=0))
        with self.assertRaisesRegex(ValueError, "inverse journal"):
            validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)
        self.mutate(trace, lambda value: value["events"][-1]["payload"]["result"]["value"]["preview"]["undo"].update(restored=29, conflicts=[{"x": 1}]))
        with self.assertRaisesRegex(ValueError, "inverse journal"):
            validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)
        self.mutate(trace, lambda value: value["events"][-1]["payload"]["result"]["value"]["preview"]["undo"].update(conflicts=[]))
        self.mutate(self.undo_path, lambda journal: journal.update(label="Manual target clearing"))
        with self.assertRaisesRegex(ValueError, "inverse journal"):
            validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)

    def test_wrong_world_or_unverified_copy_entry_rejects(self):
        self.mutate(self.undo_path, lambda journal: journal.update(worldId="other-world"))
        with self.assertRaisesRegex(ValueError, "inverse journal"):
            validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)
        self.mutate(self.copy_path, lambda journal: journal["entries"][0].pop("verified"))
        with self.assertRaisesRegex(ValueError, "unambiguous"):
            validator.validate_copy(self.copy_dir, self.repo)

    def test_integral_float_receipt_passes_and_invalid_counts_reject(self):
        self.assertEqual(75, validator.integral_count(75.0))
        self.assertEqual(0, validator.integral_count(0.0))
        trace = self.undo_dir / "trace.json"
        self.mutate(trace, lambda value: value["events"][-1]["payload"]["result"]["value"]["preview"]["undo"].update(restored=29.0))
        self.assertEqual(29, validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)["restored"])
        for invalid in (29.5, True, False, float("nan"), float("inf"), -float("inf"), -1, -1.0, "29"):
            with self.subTest(restored=invalid):
                self.assertIsNone(validator.integral_count(invalid))
                self.mutate(trace, lambda value: value["events"][-1]["payload"]["result"]["value"]["preview"]["undo"].update(restored=invalid))
                with self.assertRaisesRegex(ValueError, "undo receipt"):
                    validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)

    def test_evidence_paths_cannot_read_outside_acceptance_tree(self):
        self.mutate(self.copy_dir / "launch.json", lambda manifest: manifest.update(trace=str(self.repo / "secret.json")))
        with self.assertRaisesRegex(ValueError, "escaped"):
            validator.validate_copy(self.copy_dir, self.repo)


if __name__ == "__main__":
    unittest.main()
