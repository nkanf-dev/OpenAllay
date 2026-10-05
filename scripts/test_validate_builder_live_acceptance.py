import copy
import importlib.util
import json
import gzip
import hashlib
import struct
import uuid
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
        self.temporary = tempfile.TemporaryDirectory(dir=SCRIPT.parent)
        self.addCleanup(self.temporary.cleanup)
        self.repo = Path(self.temporary.name).resolve()
        self.copy_dir = self.repo / "build/e2e/copy"
        self.undo_dir = self.copy_dir / "phases/undo"
        self.game = self.copy_dir / "game"
        self.world = "openallay-builder-fabric-test"
        self.anchor = {"x": 0, "y": -61, "z": 0}
        self.copy_id = "00000000-0000-0000-0000-000000000001"
        self.undo_id = "00000000-0000-0000-0000-000000000002"
        self.template = {"format": "openallay:structure", "size": [5, 3, 5],
                         "includesAir": False, "palette": [], "blocks": [], "metadata": {},
                         "gameVersion": "26.2", "dataVersion": 4900}
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
        return {"format": "openallay:operation-journal", "id": identifier,
                "worldId": "10000000-0000-0000-0000-000000000001", "dimension": "minecraft:overworld",
                "label": label, "status": "COMPLETED", "createdAt": 1, "updatedAt": 2,
                "detail": "All journaled writes have server readback", "checkpoint": 2, "entries": entries}

    def phase(self, directory, scenario, checks, source, result, evidence, resume=False):
        report = {"scenario": scenario, "outcome": "COMPLETED", "requestId": scenario, "sessionId": scenario,
                  "nativeAcceptance": {"outcome": "PASSED", "worldName": self.world,
                     "survival": True, "cheatsOff": True, "independentAnchor": self.anchor,
                     "oracle": "independent-integrated-server-owner-thread-readback", "checks": checks}}
        trace = {"requestId": scenario, "sessionId": scenario, "finalState": "COMPLETED",
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
        duplicate["id"] = "00000000-0000-0000-0000-000000000003"
        write_json(self.copy_path.with_name("00000000-0000-0000-0000-000000000003.json"), duplicate)
        with self.assertRaisesRegex(ValueError, "found 2"):
            validator.validate_copy(self.copy_dir, self.repo)
        self.copy_path.with_name("00000000-0000-0000-0000-000000000003.json").unlink()
        self.mutate(self.copy_path, lambda journal: journal["entries"].append({
            "position": [0, -61, 0], "before": state("grass_block"),
            "intended": state("stone_bricks"), "verified": state("stone_bricks"), "sequence": 29}))
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
        self.mutate(self.undo_path, lambda journal: journal.update(worldId="20000000-0000-0000-0000-000000000001"))
        with self.assertRaisesRegex(ValueError, "inverse journal"):
            validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)
        self.mutate(self.copy_path, lambda journal: journal["entries"][0].pop("verified"))
        with self.assertRaisesRegex(ValueError, "exact shape"):
            validator.validate_copy(self.copy_dir, self.repo)

    def test_integral_float_receipt_passes_and_invalid_counts_reject(self):
        self.assertEqual(75, validator.integral_count(75.0))
        self.assertEqual(0, validator.integral_count(0.0))
        trace = self.undo_dir / "trace.json"
        self.mutate(trace, lambda value: value["events"][-1]["payload"]["result"]["value"]["preview"]["undo"].update(restored=29.0))
        self.assertEqual(29, validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)["restored"])
        valid_trace = validator.read_json(trace)
        for invalid in (29.5, True, False, float("nan"), float("inf"), -float("inf"), -1, -1.0, "29"):
            with self.subTest(restored=invalid):
                self.assertIsNone(validator.integral_count(invalid))
                changed = copy.deepcopy(valid_trace)
                changed["events"][-1]["payload"]["result"]["value"]["preview"]["undo"].update(restored=invalid)
                write_json(trace, changed)
                with self.assertRaisesRegex(ValueError, "undo receipt|Nonfinite"):
                    validator.validate_undo(self.copy_dir, self.undo_dir, self.repo)

    def test_evidence_paths_cannot_read_outside_acceptance_tree(self):
        self.mutate(self.copy_dir / "launch.json", lambda manifest: manifest.update(trace=str(self.repo / "secret.json")))
        with self.assertRaisesRegex(ValueError, "escaped"):
            validator.validate_copy(self.copy_dir, self.repo)




def identity_nbt(world_id, data_version=4900):
    # Independent real SavedData byte layout, not a validator-generated image.
    def text(value):
        data = value.encode("utf-8")
        return struct.pack(">H", len(data)) + data
    return (b"\x0a\x00\x00\x0a" + text("data") + b"\x08" + text("uuid") + text(world_id)
            + b"\x00\x03" + text("DataVersion") + struct.pack(">i", data_version) + b"\x00")


class DurableAcceptanceAuditTests(unittest.TestCase):
    def test_json_float_overflow_and_bounded_directory_preflight_refuse(self):
        with self.assertRaisesRegex(ValueError, "Nonfinite"):
            validator.strict_json('{"value":1e999}')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for index in range(3):
                (root / str(index)).write_text("fixture")
            with self.assertRaisesRegex(ValueError, "Unbounded|unbounded"):
                validator.bounded_entries(root, 2)


    """Synthetic offline native records. Never real-client acceptance evidence."""
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(dir=SCRIPT.parent)
        self.addCleanup(self.temporary.cleanup)
        self.repo = Path(self.temporary.name).resolve()
        self.directory = self.repo / "build/e2e/acceptance"
        self.game = self.directory / "game"
        self.world = "openallay-builder-fabric-independent-offline"
        self.world_id = "10000000-0000-0000-0000-000000000001"
        self.actor = "20000000-0000-0000-0000-000000000001"
        self.anchor = {"x": 8, "y": -61, "z": 8}
        self.ids = [str(uuid.UUID(int=index)) for index in range(1, 15)]
        self.token = "actual-build-call"
        self.journal_dir = self.game / "config/openallay-builder/journals"
        self.template_path = self.game / "config/openallay-builder/templates/openallay_e2e_builder_native.json"
        self.identity_path = self.game / "saves" / self.world / "dimensions/minecraft/overworld/data/openallay_builder/world_identity.dat"
        self.retained_path = self.game / "config/openallay/e2e" / (self.world + ".acceptance.json")
        self.identity_path.parent.mkdir(parents=True)
        self.identity_path.write_bytes(gzip.compress(identity_nbt(self.world_id), mtime=0))
        self.air, self.gold, self.diamond = (state(name) for name in ("air", "gold_block", "diamond_block"))
        # One independent disk image per native landmark plus exact lifecycle writes.
        # Assign disjoint build sites to nine actual-shaped persisted operation records.
        self.entries = [[] for _ in self.ids]
        self.checks = []
        origin = tuple(self.anchor[k] for k in ("x", "y", "z"))
        for name, x, y, z, identifier, properties in validator.ACCEPTANCE_LANDMARKS:
            if name == "geometry-checkerboard-floor":
                identifier = "minecraft:quartz_block"
            image = {"id": identifier, "properties": properties}
            pos = (origin[0] + x, origin[1] + y, origin[2] + z)
            self.checks.append({"name": name, **dict(zip(("x", "y", "z"), pos)), "expectedId": identifier,
                                "expectedProperties": properties, "actualId": identifier, "actualProperties": properties,
                                "passed": True, "observed": True})
            if name.startswith(("partial-", "cancel-", "undo-")):
                continue
            group = next((i for i, prefix in enumerate(("house-", "skyscraper-", "cottage-", "windmill-", "farm-", "dock-"))
                          if name.startswith(prefix)), 6 if name.startswith(("geometry-", "decoration-")) else 7 if name.startswith("terrain-") else 8)
            self.entry(group, pos, state("grass_block") if y == 0 else self.air, image)
        # Complete current TemplateStore shape including explicit air and typed chest.
        cells = []
        self.template = {"format": "openallay:structure", "size": [3, 2, 3], "includesAir": True,
                         "metadata": {"scenario": "builder_acceptance", "seed": 17}, "gameVersion": "26.2", "dataVersion": 4900,
                         "palette": [], "blocks": []}
        for x in range(3):
            for y in range(2):
                for z in range(3):
                    image = state("stone_bricks") if y == 0 else self.air
                    if (x, y, z) == (0, 1, 0):
                        image = state("oak_stairs", facing="north")
                    if (x, y, z) == (2, 1, 1):
                        image = {**state("chest", facing="east"), "blockEntity": '{id:"minecraft:chest",Items:[]}'}
                    if (x, y, z) == (1, 1, 2):
                        image = state("red_concrete")
                    palette = {"id": image["id"], "properties": image["properties"]}
                    if palette not in self.template["palette"]:
                        self.template["palette"].append(palette)
                    cell = {"pos": [x, y, z], "state": self.template["palette"].index(palette)}
                    if "blockEntity" in image:
                        cell["blockEntity"] = image["blockEntity"]
                    self.template["blocks"].append(cell)
                    pos = (origin[0] + 14 + x, origin[1] + y, origin[2] + 32 + z)
                    existing = next((entry for entry in self.entries[8] if entry["position"] == list(pos)), None)
                    if existing:
                        existing.update(intended=image, verified=image)
                    else:
                        self.entry(8, pos, self.air if y else state("grass_block"), image)
        write_json(self.template_path, self.template)
        partial = self.positions(32)
        cancel = self.positions(34)
        undo = self.positions(36)
        self.entry(9, partial[0], self.air, self.gold)
        self.entry(10, cancel[0], self.air, self.diamond)
        for pos in undo:
            self.entry(11, pos, self.air, self.gold)
        self.entry(12, undo[1], self.gold, self.diamond)
        self.entry(13, undo[0], self.gold, self.air)
        labels = ["OpenAllay E2E Builder acceptance"] * 9 + [
            "OpenAllay E2E lifecycle " + self.token + " partial", "OpenAllay E2E lifecycle " + self.token + " cancel",
            "OpenAllay E2E lifecycle " + self.token + " undo original", "OpenAllay E2E lifecycle " + self.token + " undo intervention",
            "Undo " + self.ids[11]]
        self.rows = []
        for index, identifier in enumerate(self.ids):
            terminal = "FAILED" if index == 9 else "CANCELLED" if index == 10 else "COMPLETED"
            journal = {"format": "openallay:operation-journal", "id": identifier, "worldId": self.world_id,
                       "dimension": "minecraft:overworld", "label": labels[index], "status": terminal,
                       "createdAt": 1, "updatedAt": 2, "detail": "Actual-shaped offline test checkpoint",
                       "checkpoint": 2, "entries": self.entries[index]}
            write_json(self.journal_path(index), journal)
            self.rows.append({"id": identifier, "label": labels[index], "status": terminal.lower(), "entries": len(self.entries[index])})
        status = lambda index: {"operationId": self.ids[index], "state": "completed", "reads": 1, "writes": len(self.entries[index]), "detail": ""}
        prerequisite = lambda positions: {"positions": [dict(zip(("x", "y", "z"), p)) for p in positions], "beforeImages": [self.air, self.air]}
        self.build = {"scenario": "builder_acceptance", "stage": "build", "probeToken": self.token, "anchor": self.anchor,
                      "context": {"dimension": "minecraft:overworld", "playerUuid": self.actor}, "seed": 17,
                      "provider": "deterministic_loopback_fixture_not_live_model", "status": status(8),
                      "operations": [{"name": name, **status(index)} for index, name in enumerate(validator.BUILD_NAMES)],
                      "actions": [], "sites": {}, "terrain": {}, "baselineOperations": self.rows[:9],
                      "lifecycle": {"partial": prerequisite(partial), "cancel": prerequisite(cancel), "undo": prerequisite(undo)},
                      "templates": {"saved": ["openallay_e2e_builder_native"], "listed": True, "size": [3, 2, 3],
                                    "blockCount": 18, "paletteSize": len(self.template["palette"])}}
        failures = [{"status": "failure", "code": code, "message": message} for code, message in validator.FAILURES]
        self.partial = self.stage("partial_observation", 10, {"partial": {**prerequisite(partial), "failure": failures[0],
                                         "journal": self.rows[9], "afterImages": [self.gold, self.air]}})
        self.cancel = self.stage("cancel_observation", 11, {"cancel": {**prerequisite(cancel), "failure": failures[1],
                                        "journal": self.rows[10], "afterImages": [self.diamond, self.air]}})
        self.undo = self.stage("undo", 14, {"undo": {**prerequisite(undo), "result": {"operationId": self.ids[13], "restored": 1,
                       "conflicts": [dict(zip(("x", "y", "z"), undo[1]))], "uncertain": []}, "status": status(13),
                       "originalStatus": status(11), "interventionStatus": status(12), "afterImages": [self.air, self.diamond]}})
        self.final = {**copy.deepcopy(self.build), "stage": "final", "durableOperations": self.rows,
                      "observationStatus": {"state": "completed", "writes": 0},
                      "lifecycle": {**self.partial["lifecycle"], **self.cancel["lifecycle"], **self.undo["lifecycle"]}}
        enriched = copy.deepcopy(self.final)
        for operation in enriched["operations"]:
            operation["journal"] = self.rows[self.ids.index(operation["operationId"])]
        for key in ("originalStatus", "interventionStatus", "status"):
            item = enriched["lifecycle"]["undo"][key]
            item["journal"] = self.rows[self.ids.index(item["operationId"])]
        retained = {"outcome": "PASSED", "requestId": "actual-request", "worldName": self.world, "nativeAnchor": self.anchor,
                    **{key: enriched[key] for key in ("operations", "lifecycle", "templates")}}
        write_json(self.retained_path, retained)
        native = {"outcome": "PASSED", "worldName": self.world, "survival": True, "cheatsOff": True,
                  "oracle": "independent-integrated-server-owner-thread-readback", "independentAnchor": self.anchor,
                  "checks": self.checks, "toolContractPassed": True, "receiptNativeBindingPassed": True}
        report = {"scenario": "builder-acceptance", "outcome": "COMPLETED", "requestId": "actual-request", "sessionId": "actual-session",
                  "nativeAcceptance": native}
        values = [self.success(self.build), failures[0], self.success(self.partial), failures[1], self.success(self.cancel),
                  self.success(self.undo), self.success(self.final)]
        events = [{"type": "tool_call", "payload": {"toolId": "openallay:load_skill", "arguments": {"name": "minecraft-builder"}}},
                  {"type": "tool_result", "payload": {"toolId": "openallay:load_skill", "failure": False, "result": {"status": "success"}}}]
        for value in values:
            events.extend([{"type": "tool_call", "payload": {"toolId": validator.JS_TOOL, "arguments": {"source": "actual recorded source"}}},
                           {"type": "tool_result", "payload": {"toolId": validator.JS_TOOL, "failure": value["status"] == "failure", "result": value}}])
        trace = {"requestId": "actual-request", "actorId": self.actor, "sessionId": "actual-session", "finalState": "COMPLETED", "events": events}
        manifest = {"scenario": "builder-acceptance", "minecraft": "26.2", "noGameLaunched": False, "gameDirectory": str(self.game),
                    "world": self.world, "report": str(self.directory / "report.json"), "trace": str(self.directory / "trace.json"),
                    "command": ["java", "--uuid", self.actor.replace("-", "")]}
        for name, value in (("launch", manifest), ("report", report), ("trace", trace)):
            write_json(self.directory / (name + ".json"), value)

    def positions(self, z):
        return [(self.anchor["x"] + x, self.anchor["y"] + 1, self.anchor["z"] + z) for x in (44, 45)]

    def entry(self, index, pos, before, image):
        self.entries[index].append({"position": list(pos), "before": before, "intended": image, "verified": image,
                                    "sequence": len(self.entries[index])})

    def journal_path(self, index):
        return self.journal_dir / (self.ids[index] + ".json")

    def stage(self, name, count, lifecycle):
        return {"scenario": "builder_acceptance", "stage": name, "probeToken": self.token, "anchor": self.anchor,
                "context": {"dimension": "minecraft:overworld", "playerUuid": self.actor}, "durableOperations": self.rows[:count],
                "lifecycle": lifecycle, "observationStatus": {"state": "completed", "writes": 0}}

    def success(self, receipt):
        return {"status": "success", "value": {"resultType": "string", "complete": True, "preview": json.dumps(receipt)}}

    def mutate(self, path, function):
        value = validator.read_json(path)
        function(value)
        write_json(path, value)

    def audit(self):
        return validator.validate_acceptance(self.directory, self.repo)

    def test_exact_public_rows_actual_checkpoints_native_identity_and_template_pass(self):
        before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in self.directory.rglob("*") if p.is_file()}
        proof = self.audit()
        self.assertEqual(self.world_id, proof["worldId"])
        self.assertEqual(14, len(proof["journalRows"]))
        self.assertEqual(85, proof["landmarkCount"])
        self.assertIn("journals-have-no-actor-field", proof["actorBinding"])
        self.assertEqual(before, {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in self.directory.rglob("*") if p.is_file()})

    def test_actual_checkpoint_without_saved_journals_refuses(self):
        self.journal_path(0).unlink()
        with self.assertRaisesRegex(ValueError, "exact 14"):
            self.audit()

    def test_extra_foreign_journal_refuses(self):
        extra = validator.read_json(self.journal_path(0))
        extra["id"] = str(uuid.UUID(int=100))
        write_json(self.journal_dir / (extra["id"] + ".json"), extra)
        with self.assertRaisesRegex(ValueError, "exact 14"):
            self.audit()

    def test_wrong_native_world_and_dimension_refuse(self):
        original = validator.read_json(self.journal_path(0))
        for key, value in (("worldId", str(uuid.UUID(int=200))), ("dimension", "minecraft:the_nether")):
            changed = {**original, key: value}
            write_json(self.journal_path(0), changed)
            with self.assertRaisesRegex(ValueError, "world/dimension"):
                self.audit()

    def test_actual_actor_not_model_uuid_refuses(self):
        self.mutate(self.directory / "launch.json", lambda value: value.update(command=["java", "--uuid", str(uuid.UUID(int=500))]))
        with self.assertRaisesRegex(ValueError, "actor/launch/trace"):
            self.audit()

    def test_partial_wrong_intended_cancel_wrong_actual_undo_conflict_entry_refuse(self):
        cases = ((9, lambda value: value["entries"][0].update(intended=self.diamond)),
                 (10, lambda value: value["entries"][0].update(verified=self.gold)),
                 (13, lambda value: value["entries"].append({"position": list(self.positions(36)[1]), "before": self.diamond,
                       "intended": self.air, "verified": self.air, "sequence": 1})))
        originals = {i: validator.read_json(self.journal_path(i)) for i, _ in cases}
        for index, mutation in cases:
            for i, original in originals.items():
                write_json(self.journal_path(i), original)
            self.mutate(self.journal_path(index), mutation)
            with self.assertRaises(ValueError):
                self.audit()

    def test_incomplete_checkpoint_pending_retouch_and_old_version_refuse(self):
        original = validator.read_json(self.journal_path(0))
        for mutation in (lambda value: value.pop("checkpoint"), lambda value: value.update(checkpoint=0),
                         lambda value: value["entries"][0].pop("verified"), lambda value: value.update(version=1),
                         lambda value: value["entries"][0].update(previousVerified=self.air)):
            write_json(self.journal_path(0), original)
            self.mutate(self.journal_path(0), mutation)
            with self.assertRaises(ValueError):
                self.audit()

    def test_covered_sparse_cleanup_leftover_is_valid_uncovered_or_corrupt_is_not(self):
        path = self.journal_dir / (self.ids[9] + "--00000000000000000001.json")
        delta = {"format": "openallay:operation-delta", "id": self.ids[9], "sequence": 1, "updatedAt": 1, "kind": "intents",
                 "intents": [{"position": list(self.positions(32)[0]), "before": self.air, "intended": self.gold}]}
        write_json(path, delta)
        self.assertEqual(1, len(self.audit()["coveredDeltaSha256"]))
        self.mutate(path, lambda value: value.update(id=self.ids[10]))
        with self.assertRaisesRegex(ValueError, "Delta identity"):
            self.audit()
        path.unlink()
        uncovered = path.with_name(self.ids[9] + "--00000000000000000003.json")
        write_json(uncovered, {**delta, "sequence": 3})
        with self.assertRaisesRegex(ValueError, "uncovered"):
            self.audit()

    def test_temp_wal_and_orphan_delta_refuse_without_cleanup(self):
        pending = self.journal_dir / ".pending-native.tmp"
        pending.write_text("partial")
        with self.assertRaisesRegex(ValueError, "Incomplete WAL"):
            self.audit()
        self.assertEqual("partial", pending.read_text())
        pending.unlink()
        write_json(self.journal_dir / (str(uuid.UUID(int=99)) + "--00000000000000000001.json"), {})
        with self.assertRaisesRegex(ValueError, "Orphan"):
            self.audit()

    def test_identity_absent_wrong_uuid_alias_and_oversize_refuse(self):
        original = self.identity_path.read_bytes()
        self.identity_path.unlink()
        self.identity_path.with_name("world_identity-alias.dat").write_bytes(original)
        with self.assertRaisesRegex(ValueError, "Missing"):
            self.audit()
        for data in (gzip.compress(identity_nbt(str(uuid.UUID(int=700)))), gzip.compress(b"x" * 65537), original[:-1], original + original):
            self.identity_path.write_bytes(data)
            with self.assertRaises(ValueError):
                self.audit()

    def test_identity_exact_nbt_duplicate_unknown_tag_depth_and_trailing_refuse(self):
        for data in (identity_nbt(self.world_id) + b"x", b"\x0a\x00\x00\x09", b"\x0a\x00\x00\x0a\x00\x01a\x0a\x00\x01b\x0a\x00\x01c"):
            self.identity_path.write_bytes(gzip.compress(data))
            with self.assertRaises(ValueError):
                self.audit()

    def test_symlink_identity_journal_and_in_root_trace_refuse(self):
        for path in (self.identity_path, self.journal_path(0), self.directory / "trace.json"):
            data = path.read_bytes()
            target = path.with_name(path.name + ".original")
            target.write_bytes(data)
            path.unlink()
            path.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "Symlink"):
                self.audit()
            path.unlink()
            path.write_bytes(data)
            target.unlink()

    def test_duplicate_member_corrupt_image_sequence_status_and_native_landmark_refuse(self):
        original = self.journal_path(0).read_text()
        self.journal_path(0).write_text(original.replace('"checkpoint": 2', '"checkpoint": 2, "checkpoint": 2'))
        with self.assertRaisesRegex(ValueError, "Duplicate JSON"):
            self.audit()
        self.journal_path(0).write_text(original)
        for mutation in (lambda value: value.update(status="RUNNING"), lambda value: value["entries"][0]["verified"].update(properties=[]),
                         lambda value: value["entries"][1].update(sequence=0)):
            self.journal_path(0).write_text(original)
            self.mutate(self.journal_path(0), mutation)
            with self.assertRaises(ValueError):
                self.audit()
        self.journal_path(0).write_text(original)
        self.mutate(self.directory / "report.json", lambda value: value["nativeAcceptance"]["checks"].pop())
        with self.assertRaisesRegex(ValueError, "all 85"):
            self.audit()

    def test_template_modified_public_row_changed_and_synthetic_dead_status_refuse(self):
        self.mutate(self.template_path, lambda value: value["palette"][0].update(id="minecraft:gold_block"))
        with self.assertRaisesRegex(ValueError, "template image"):
            self.audit()
        write_json(self.template_path, self.template)
        self.mutate(self.retained_path, lambda value: value["operations"][0]["journal"].update(entries=999))
        with self.assertRaisesRegex(ValueError, "persist actual 14"):
            self.audit()

    def test_failed_tool_never_projected_success_and_complete_scalar_required(self):
        trace_path = self.directory / "trace.json"
        original = validator.read_json(trace_path)
        results = [e for e in original["events"] if e["type"] == "tool_result" and e["payload"]["toolId"] == validator.JS_TOOL]
        results[1]["payload"].update(failure=False, result=self.success({"status": {"state": "failed-partial"}}))
        write_json(trace_path, original)
        with self.assertRaises(ValueError):
            self.audit()

    def reload_phase(self):
        directory = self.directory / "phases/reload"
        manifest = {"scenario": "builder-reload", "minecraft": "26.2", "noGameLaunched": False,
                    "resumeFrom": str(self.directory), "gameDirectory": str(self.game), "world": self.world,
                    "report": str(directory / "report.json"), "trace": str(directory / "trace.json")}
        native = validator.read_json(self.directory / "report.json")["nativeAcceptance"]
        native["exactPersistencePassed"] = True
        report = {"scenario": "builder-reload", "outcome": "COMPLETED", "requestId": "reload-request", "sessionId": "reload-session",
                  "nativeAcceptance": native}
        receipt = {"scenario": "builder_reload", "operations": self.rows, "operationCount": 14,
                   "template": {"name": "openallay_e2e_builder_native", "size": [3, 2, 3]},
                   "listed": ["openallay_e2e_builder_native"], "status": {"state": "completed", "writes": 0}}
        trace = {"requestId": "reload-request", "sessionId": "reload-session", "actorId": self.actor,
                 "finalState": "COMPLETED", "events": [
                     {"type": "tool_call", "payload": {"toolId": validator.JS_TOOL, "arguments": {"source": "native reload"}}},
                     {"type": "tool_result", "payload": {"toolId": validator.JS_TOOL, "failure": False, "result": self.success(receipt)}}]}
        for name, value in (("launch", manifest), ("report", report), ("trace", trace)):
            write_json(directory / (name + ".json"), value)
        return directory

    def test_reload_same_world_exact_rows_and_retained_template_pass(self):
        directory = self.reload_phase()
        proof = validator.validate_acceptance(self.directory, self.repo, reload_directory=directory)
        self.assertIn("reloadReportSha256", proof)
        self.mutate(directory / "report.json", lambda value: value["nativeAcceptance"].update(exactPersistencePassed=False))
        with self.assertRaisesRegex(ValueError, "resume exact"):
            validator.validate_acceptance(self.directory, self.repo, reload_directory=directory)

    def test_public_missing_extra_label_status_count_and_incomplete_scalar_refuse(self):
        trace_path = self.directory / "trace.json"
        original = validator.read_json(trace_path)
        final_index = len(original["events"]) - 1
        for mutate in (lambda value: value["durableOperations"].pop(),
                       lambda value: value["durableOperations"].append({**value["durableOperations"][0], "id": str(uuid.UUID(int=500))}),
                       lambda value: value["durableOperations"][9].update(label="invented partial"),
                       lambda value: value["durableOperations"][9].update(status="completed"),
                       lambda value: value["durableOperations"][9].update(entries=2)):
            changed = copy.deepcopy(original)
            receipt = json.loads(changed["events"][final_index]["payload"]["result"]["value"]["preview"])
            mutate(receipt)
            changed["events"][final_index]["payload"]["result"] = self.success(receipt)
            write_json(trace_path, changed)
            with self.assertRaises(ValueError):
                self.audit()
        write_json(trace_path, original)
        self.mutate(trace_path, lambda value: value["events"][final_index]["payload"]["result"]["value"].update(complete=False))
        with self.assertRaisesRegex(ValueError, "complete actual scalar"):
            self.audit()

    def test_native_before_image_changed_undo_conflict_or_denied_cell_edited_refuse(self):
        self.mutate(self.journal_path(13), lambda value: value["entries"][0].update(before=self.diamond))
        with self.assertRaisesRegex(ValueError, "before/actual"):
            self.audit()

    def test_component_symlink_and_path_escape_refuse_before_reading(self):
        config = self.game / "config"
        target = self.game / "config-original"
        config.rename(target)
        config.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "Symlink"):
            self.audit()
        config.unlink()
        target.rename(config)
        self.mutate(self.directory / "launch.json", lambda value: value.update(trace=str(self.directory / "../foreign.json")))
        with self.assertRaisesRegex(ValueError, "escaped"):
            self.audit()

    def test_template_foreign_file_and_wrong_external_data_version_refuse(self):
        extra = self.template_path.with_name("foreign.json")
        write_json(extra, self.template)
        with self.assertRaisesRegex(ValueError, "extra persisted"):
            self.audit()
        extra.unlink()
        self.mutate(self.template_path, lambda value: value.update(dataVersion=100))
        with self.assertRaisesRegex(ValueError, "version differs"):
            self.audit()

    def test_completed_native_intended_wrong_and_covered_outcome_shape_refuse(self):
        original = validator.read_json(self.journal_path(0))
        self.mutate(self.journal_path(0), lambda value: value["entries"][0].update(intended=self.gold))
        with self.assertRaisesRegex(ValueError, "intended image"):
            self.audit()
        write_json(self.journal_path(0), original)
        path = self.journal_dir / (self.ids[9] + "--00000000000000000002.json")
        delta = {"format": "openallay:operation-delta", "id": self.ids[9], "sequence": 2, "updatedAt": 2, "kind": "outcome",
                 "actual": [{"position": list(self.positions(32)[0]), "actual": self.gold}], "unstarted": []}
        write_json(path, delta)
        self.assertEqual(1, len(self.audit()["coveredDeltaSha256"]))
        self.mutate(path, lambda value: value.update(unstarted=[list(self.positions(32)[0])]))
        with self.assertRaisesRegex(ValueError, "overlapping"):
            self.audit()

    def test_native_identity_duplicate_members_refuse(self):
        encoded = identity_nbt(self.world_id)
        # Duplicate DataVersion tag at the root (before its final end marker).
        encoded = encoded[:-1] + b"\x03\x00\x0bDataVersion" + struct.pack(">i", 4900) + b"\x00"
        self.identity_path.write_bytes(gzip.compress(encoded))
        with self.assertRaisesRegex(ValueError, "Duplicate native identity NBT"):
            self.audit()

    def forge_identity(self):
        # Synthetic 1.19.2 SavedData at the producer's single exact path.
        self.identity_path.unlink()
        self.identity_path = self.game / "saves" / self.world / "data/openallay_builder_world_identity.dat"
        self.identity_path.parent.mkdir(parents=True, exist_ok=True)
        self.identity_path.write_bytes(gzip.compress(identity_nbt(self.world_id, 3120), mtime=0))
        self.mutate(self.directory / "launch.json", lambda value: value.update(minecraft="1.19.2", loader="forge"))
        self.mutate(self.template_path, lambda value: value.update(gameVersion="1.19.2", dataVersion=3120))

        _, warmup = NativeCommandWarmupTests().valid()
        warmup["actorId"] = self.actor
        for result in warmup["commands"].values():
            result["actorId"] = self.actor
        self.mutate(self.directory / "report.json", lambda value: (
            value.update(nativeCommandWarmup=warmup), value["nativeAcceptance"].update(nativeCommandWarmup=warmup)))

    def test_forge_1192_full_acceptance_and_exact_original_reload(self):
        self.forge_identity()
        directory = self.reload_phase()
        self.mutate(directory / "launch.json", lambda value: value.update(minecraft="1.19.2", loader="forge"))
        proof = validator.validate_acceptance(self.directory, self.repo, reload_directory=directory)
        self.assertEqual(str(self.identity_path), proof["identity"])
        self.assertEqual(3120, proof["nativeDataVersion"])
        self.assertEqual(self.actor, proof["actorUuid"])
        self.assertEqual(self.world_id, proof["worldId"])
        self.assertEqual(14, len(proof["journalRows"]))
        self.assertEqual(85, proof["landmarkCount"])
        self.assertEqual(hashlib.sha256(self.template_path.read_bytes()).hexdigest(), proof["templateSha256"])
        self.assertIn("reloadReportSha256", proof)

    def test_forge_1192_identity_alias_mainline_path_and_wrong_external_version_refuse(self):
        self.forge_identity()
        data = self.identity_path.read_bytes()
        self.identity_path.unlink()
        mainline = self.game / "saves" / self.world / "dimensions/minecraft/overworld/data/openallay_builder/world_identity.dat"
        mainline.write_bytes(data)
        self.identity_path.with_name("world_identity.dat").write_bytes(data)
        with self.assertRaisesRegex(ValueError, "Missing"):
            self.audit()
        # Matching synthetic template version cannot disguise the wrong native game version.
        self.identity_path.write_bytes(gzip.compress(identity_nbt(self.world_id, 3121)))
        self.mutate(self.template_path, lambda value: value.update(dataVersion=3121))
        with self.assertRaisesRegex(ValueError, "DataVersion differs"):
            self.audit()

    def test_identity_unverified_targets_and_1192_wrong_loader_remain_closed(self):
        self.forge_identity()
        for target, loader in (("1.20.1", "neoforge"), ("1.20.4", "fabric"), ("26.3", "fabric"),
                               ("1.19.2", "fabric"), ("1.19.2", "neoforge"), ("1.19.2", None)):
            with self.subTest(target=target, loader=loader):
                self.mutate(self.directory / "launch.json", lambda value: value.update(minecraft=target, loader=loader))
                with self.assertRaisesRegex(ValueError, "supports verified"):
                    self.audit()

    def test_forge_reload_target_loader_game_path_and_native_anchor_remain_exact(self):
        self.forge_identity()
        directory = self.reload_phase()
        self.mutate(directory / "launch.json", lambda value: value.update(minecraft="1.19.2", loader="forge"))
        manifest = validator.read_json(directory / "launch.json")
        for mutation in (lambda value: value.update(minecraft="26.2"), lambda value: value.update(loader="neoforge"),
                         lambda value: value.update(gameDirectory=str(directory / "game"))):
            write_json(directory / "launch.json", manifest)
            self.mutate(directory / "launch.json", mutation)
            with self.assertRaises(ValueError):
                validator.validate_acceptance(self.directory, self.repo, reload_directory=directory)
        write_json(directory / "launch.json", manifest)
        self.mutate(directory / "report.json", lambda value: value["nativeAcceptance"].update(independentAnchor={"x": 999, "y": -61, "z": 8}))
        with self.assertRaises(ValueError):
            validator.validate_acceptance(self.directory, self.repo, reload_directory=directory)


if __name__ == "__main__":
    unittest.main()

class NativeCommandWarmupTests(unittest.TestCase):
    def valid(self):
        actor = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        token = "openallay_native_command_" + "a" * 32
        warmup = {"outcome": "PASSED", "actorId": actor, "token": token,
                  "worldAuthorityChanged": False, "initialUnrestrictedSetting": False,
                  "restoredUnrestrictedSetting": False}
        for name in ("nativeFeedbackEvidence", "offOwnerCaptureRejected", "closedCapabilityRemoved",
                     "closedBridgeRemoved", "cancelledReuseRejected", "serverReadbackOwnerThread",
                     "cheatsOffAfter", "survivalAfter", "javascriptSettingRestored"):
            warmup[name] = True
        warmup["commands"] = {}
        for index, (name, command, message) in enumerate((
                ("help", "help me", "/me <action>"),
                ("signed", "me " + token, "Player " + token),
                ("error", "help " + token + "_missing", "Native error")), 1):
            warmup["commands"][name] = {"actorId": actor, "command": command, "state": "feedback",
                                       "feedbackObserved": True, "messages": [message], "sequence": float(index)}
        return actor, warmup

    def test_exact_forge_warmup_requires_feedback_and_restored_builder_baseline(self):
        actor, warmup = self.valid()
        manifest = {"minecraft": "1.19.2", "loader": "forge", "unrestrictedOptIn": False}
        validator.validate_native_command_warmup({"nativeCommandWarmup": warmup}, {"nativeCommandWarmup": warmup}, manifest, actor)
        for key, value in (("javascriptSettingRestored", False), ("restoredUnrestrictedSetting", True),
                           ("nativeFeedbackEvidence", False)):
            invalid = copy.deepcopy(warmup); invalid[key] = value
            with self.assertRaises(ValueError):
                validator.validate_native_command_warmup({"nativeCommandWarmup": invalid}, {"nativeCommandWarmup": invalid}, manifest, actor)
        with self.assertRaises(ValueError):
            validator.validate_native_command_warmup({}, {}, manifest, actor)

    def test_actor_token_sequence_cannot_be_replaced(self):
        actor, warmup = self.valid()
        manifest = {"minecraft": "1.19.2", "loader": "forge"}
        for key, value in (("actorId", "different"), ("sequence", 1.5), ("messages", ["unrelated"])):
            invalid = copy.deepcopy(warmup); invalid["commands"]["signed"][key] = value
            with self.assertRaises(ValueError):
                validator.validate_native_command_warmup({"nativeCommandWarmup": invalid}, {"nativeCommandWarmup": invalid}, manifest, actor)
