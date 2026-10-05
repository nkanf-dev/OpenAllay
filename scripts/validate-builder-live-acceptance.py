#!/usr/bin/env python3
"""Read-only post-check of retained Builder acceptance, copy and undo evidence.

Reads only packaged acceptance reports, production traces, templates and journals.
It does not launch Minecraft, call a provider, inspect save chunks or modify files.
For acceptance, reads ONLY the exact mainline 26.2 native Builder identity SavedData
under the manifest original disposable world. It never reads chunks or other saves.
Printed JSON is derived evidence, not a model-authored acceptance claim.
"""

import argparse
from collections import defaultdict, deque
import hashlib
import json
import math
import os
import stat
import struct
import uuid
import zlib
from pathlib import Path
import re
import sys

REPO = Path(__file__).resolve().parents[1]
JS_TOOL = "openallay:run_javascript"
JOURNAL_FORMAT = "openallay:operation-journal"
DIRECTIONS = ("north", "east", "south", "west")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strict_json(text):
    def members(items):
        value = {}
        for key, child in items:
            require(key not in value, "Duplicate JSON member: " + key)
            value[key] = child
        return value
    def nonfinite(value):
        raise ValueError("Nonfinite JSON number: " + value)
    def finite_float(value):
        number = float(value)
        require(math.isfinite(number), "Nonfinite JSON number: " + value)
        return number
    return json.loads(text, object_pairs_hook=members, parse_constant=nonfinite, parse_float=finite_float)


def read_bytes(path, limit=16 * 1024 * 1024):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), "Missing or nonregular evidence: " + str(path))
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "rb") as source:
        require(stat.S_ISREG(os.fstat(source.fileno()).st_mode), "Nonregular evidence")
        data = source.read(limit + 1)
    require(len(data) <= limit, "Oversized evidence: " + str(path))
    return data


def read_json(path):
    return strict_json(read_bytes(path).decode("utf-8"))


def sha256(path):
    return hashlib.sha256(read_bytes(path)).hexdigest()


def under(path, root):
    # Keep the lexical path until every component has passed NOFOLLOW checks.
    # Resolving first would conceal an in-root symlink or '..' escape.
    path, root = Path(path).absolute(), Path(root).absolute()
    require(".." not in path.parts and ".." not in root.parts, "Evidence path escaped its acceptance directory")
    require(path.is_relative_to(root), "Evidence path escaped its acceptance directory")
    for component in (root, *[root.joinpath(*path.relative_to(root).parts[:i])
                            for i in range(1, len(path.relative_to(root).parts) + 1)]):
        require(not component.is_symlink(), "Symlink evidence refused: " + str(component))
    require(path.resolve().is_relative_to(root.resolve()), "Evidence path escaped its acceptance directory")
    return path.resolve()


def fields(value, required, optional=()):
    require(isinstance(value, dict) and set(required) <= set(value)
            and set(value) <= set(required) | set(optional), "Native artifact exact shape differs")


def integer(value, minimum=0):
    require(type(value) is int and minimum <= value <= 9223372036854775807, "Invalid native integer")
    return value


def canonical_uuid(value):
    require(isinstance(value, str) and str(uuid.UUID(value)) == value, "Noncanonical native UUID")
    return value


def block_image(value):
    fields(value, ("id", "properties"), ("blockEntity",))
    require(isinstance(value["id"], str) and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9/._-]+", value["id"])
            and isinstance(value["properties"], dict), "Invalid native block image")
    require(all(isinstance(k, str) and k.strip() and isinstance(v, str) and v.strip()
                for k, v in value["properties"].items()), "Invalid native block properties")
    require("blockEntity" not in value or isinstance(value["blockEntity"], str) and value["blockEntity"].strip(),
            "Invalid native block entity image")
    return value


def load_phase(directory, scenario, repo=REPO, allow_fixture_failures=False):
    acceptance_root = under(Path(repo).resolve() / "build/e2e", Path(repo).resolve())
    directory = under(directory, acceptance_root)
    manifest = read_json(directory / "launch.json")
    require(manifest.get("scenario") == scenario and manifest.get("noGameLaunched") is False,
            "Manifest does not identify a launched " + scenario)
    game = under(manifest["gameDirectory"], acceptance_root)
    expected_game = (Path(manifest["resumeFrom"]) if manifest.get("resumeFrom") else directory) / "game"
    require(game == expected_game.resolve(), "Phase does not use its original disposable game directory")
    require(str(manifest.get("world", "")).startswith("openallay-builder-"), "Not a disposable Builder world")
    report_path, trace_path = under(manifest["report"], directory), under(manifest["trace"], directory)
    report, trace = read_json(report_path), read_json(trace_path)
    native = report.get("nativeAcceptance", {})
    require(report.get("scenario") == scenario and report.get("outcome") == "COMPLETED"
            and native.get("outcome") == "PASSED", "Independent native acceptance did not pass")
    require(native.get("worldName") == manifest["world"] and native.get("survival") is True
            and native.get("cheatsOff") is True, "Native world identity or authorization differs")
    require(native.get("oracle") == "independent-integrated-server-owner-thread-readback",
            "Missing independent owner-thread readback")
    require(trace.get("requestId") == report.get("requestId")
            and trace.get("sessionId") == report.get("sessionId") and trace.get("finalState") == "COMPLETED"
            and not trace.get("errorCode"), "Production trace is not the completed report request")
    checks = native.get("checks", [])
    require(checks and all(check.get("passed") is True and check.get("observed") is True for check in checks),
            "Native landmarks are missing, failed or unobserved")
    pairs = tool_pairs(trace, allow_fixture_failures)
    require(pairs, "No production JavaScript calls were retained")
    return {"directory": directory, "manifest": manifest, "game": game, "report": report,
            "native": native, "trace": trace, "pairs": pairs,
            "reportPath": report_path, "tracePath": trace_path}


def tool_pairs(trace, allow_fixture_failures=False):
    # Recorder emits calls in order, then results in the same per-tool order.
    pending = defaultdict(deque)
    pairs = []
    for event in trace.get("events", []):
        payload = event.get("payload") or {}
        tool = payload.get("toolId")
        if event.get("type") == "tool_call":
            pending[tool].append(payload.get("arguments") or {})
        elif event.get("type") == "tool_result":
            require(pending[tool], "Tool result has no corresponding production call")
            arguments = pending[tool].popleft()
            if tool == JS_TOOL:
                normalized = payload.get("result") or {}
                require(allow_fixture_failures or payload.get("failure") is False and normalized.get("status") == "success",
                        "A live JavaScript Tool failed")
                require(payload.get("failure") is (normalized.get("status") == "failure"), "Tool failure flag differs")
                require(isinstance(arguments.get("source"), str), "JavaScript source is missing")
                pairs.append((arguments, normalized))
    require(not any(pending.values()), "Production trace has unfinished Tool calls")
    return pairs


def preview(normalized):
    value = normalized.get("value") or {}
    return value.get("preview") if isinstance(value, dict) else None


def objects(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from objects(child)
    elif isinstance(value, list):
        for child in value:
            yield from objects(child)


def operation_ids(pairs):
    return {item["operationId"] for _, result in pairs for item in objects(preview(result))
            if isinstance(item.get("operationId"), str)}


def position(value):
    # Native BlockPosition.toJson emits [x,y,z]; E2E readback emits an object.
    if isinstance(value, list):
        require(len(value) == 3 and all(type(n) is int for n in value), "Invalid native journal position")
        return tuple(value)
    require(isinstance(value, dict) and all(type(value.get(key)) is int for key in ("x", "y", "z")),
            "Invalid native readback position")
    return value["x"], value["y"], value["z"]


def integral_count(value):
    """Rhino JSON may encode native long counts as 75.0; reject other values."""
    if type(value) is int:
        return value if value >= 0 else None
    if type(value) is float and math.isfinite(value) and value >= 0 and value.is_integer():
        return int(value)
    return None


def state_matches(image, expected):
    return (isinstance(image, dict) and image.get("id") == expected.get("id")
            and isinstance(image.get("properties"), dict)
            and all(image["properties"].get(key) == value for key, value in expected.get("properties", {}).items()))


def decode_snapshot(journal):
    fields(journal, ("format", "id", "worldId", "dimension", "label", "status", "createdAt", "updatedAt",
                     "detail", "checkpoint", "entries"))
    require(journal["format"] == JOURNAL_FORMAT, "Unsupported native journal format")
    canonical_uuid(journal["id"])
    canonical_uuid(journal["worldId"])
    require(isinstance(journal["dimension"], str)
            and re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9/._-]+", journal["dimension"]), "Invalid native dimension")
    require(all(isinstance(journal[k], str) for k in ("label", "detail")), "Invalid journal text")
    require(journal["status"] in ("COMPLETED", "FAILED", "CANCELLED"), "Journal is not a terminal fixture checkpoint")
    integer(journal["createdAt"])
    integer(journal["updatedAt"], journal["createdAt"])
    integer(journal["checkpoint"], 2)
    require(isinstance(journal["entries"], list) and 0 < len(journal["entries"]) <= 100000,
            "Native journal entry count is missing or unbounded")
    indexed, previous_sequence = {}, -1
    for entry in journal["entries"]:
        fields(entry, ("position", "before", "intended", "verified", "sequence"))
        require(isinstance(entry["position"], list), "Native journal position must be an array")
        pos = position(entry["position"])
        require(all(-2147483648 <= n <= 2147483647 for n in pos), "Position exceeds native integer range")
        sequence = integer(entry["sequence"])
        require(sequence > previous_sequence and pos not in indexed, "Invalid first-touch order or duplicate journal position")
        previous_sequence = sequence
        for key in ("before", "intended", "verified"):
            block_image(entry[key])
        indexed[pos] = entry
    return indexed


def validate_covered_delta(delta, identifier, sequence, journal):
    # Native load deliberately skips already-covered records. Accept legitimate
    # sparse cleanup leftovers, but still refuse malformed bytes and uncovered WAL.
    common = ("format", "id", "sequence", "updatedAt", "kind")
    kind = delta.get("kind")
    fields(delta, (*common, *(('intents',) if kind == "intents" else ('actual', 'unstarted'))))
    require(delta["format"] == "openallay:operation-delta" and delta["id"] == identifier
            and integer(delta["sequence"], 1) == sequence, "Delta identity/sequence mismatch")
    require(journal["createdAt"] <= integer(delta["updatedAt"]) <= journal["updatedAt"], "Delta timestamp differs")
    touched = set()
    if kind == "intents":
        require(isinstance(delta["intents"], list) and delta["intents"], "Empty intent delta")
        for intent in delta["intents"]:
            fields(intent, ("position", "before", "intended"))
            require(isinstance(intent["position"], list), "Invalid delta position")
            pos = position(intent["position"])
            require(pos not in touched, "Duplicate delta position")
            touched.add(pos)
            block_image(intent["before"])
            block_image(intent["intended"])
    else:
        require(kind == "outcome" and isinstance(delta["actual"], list) and isinstance(delta["unstarted"], list)
                and (delta["actual"] or delta["unstarted"]), "Invalid/empty outcome delta")
        for image in delta["actual"]:
            fields(image, ("position", "actual"))
            require(isinstance(image["position"], list), "Invalid delta position")
            pos = position(image["position"])
            require(pos not in touched, "Duplicate delta position")
            touched.add(pos)
            block_image(image["actual"])
        for pos in delta["unstarted"]:
            require(isinstance(pos, list) and position(pos) not in touched, "Duplicate/overlapping delta position")
            touched.add(position(pos))


def bounded_entries(directory, maximum):
    paths = []
    for path in directory.iterdir():
        require(len(paths) < maximum, "Unexpected or unbounded evidence directory")
        paths.append(path)
    return sorted(paths)


def journals(game, expected_ids=None):
    directory = under(game / "config/openallay-builder/journals", game)
    require(directory.is_dir(), "Missing native journal directory")
    paths = bounded_entries(directory, 4096)
    for path in paths:
        under(path, directory)
        require(path.is_file() and path.suffix == ".json", "Incomplete WAL or unexpected journal artifact")
    if expected_ids is not None:
        require({p.stem for p in paths if "--" not in p.stem} == set(expected_ids),
                "Missing/extra/foreign native journals; expected exact 14")
    bases, deltas = {}, []
    for path in paths:
        under(path, directory)
        require(path.is_file() and path.suffix == ".json", "Incomplete WAL or unexpected journal artifact")
        name = path.stem
        if "--" in name:
            identifier, digits = name.split("--", 1)
            canonical_uuid(identifier)
            require(re.fullmatch(r"[0-9]{20}", digits), "Invalid delta sequence filename")
            deltas.append((path, identifier, integer(int(digits), 1)))
        else:
            canonical_uuid(name)
            journal = read_json(path)
            require(name == journal.get("id"), "Native journal filename and ID differ")
            bases[name] = (path, journal, decode_snapshot(journal))
    for path, identifier, sequence in deltas:
        require(identifier in bases, "Orphan journal delta without base")
        journal = bases[identifier][1]
        require(sequence <= journal["checkpoint"], "Terminal checkpoint has uncovered/missing journal deltas")
        validate_covered_delta(read_json(path), identifier, sequence, journal)
    return list(bases.values())


def decode_template(template):
    fields(template, ("format", "size", "palette", "blocks", "metadata", "includesAir", "gameVersion", "dataVersion"))
    require(template["format"] == "openallay:structure" and type(template["includesAir"]) is bool
            and isinstance(template["metadata"], dict) and isinstance(template["gameVersion"], str)
            and template["gameVersion"].strip(), "Invalid native template identity")
    integer(template["dataVersion"])
    size = position(template["size"])
    require(all(0 < n <= 2147483647 for n in size) and isinstance(template["palette"], list)
            and isinstance(template["blocks"], list), "Invalid native template size/content")
    for image in template["palette"]:
        fields(image, ("id", "properties"))
        block_image(image)
    indexed = {}
    for cell in template["blocks"]:
        fields(cell, ("pos", "state"), ("blockEntity",))
        pos = position(cell["pos"])
        require(isinstance(cell["pos"], list) and all(0 <= pos[i] < size[i] for i in range(3))
                and pos not in indexed, "Duplicate/outside native template position")
        index = integer(cell["state"])
        require(index < len(template["palette"]), "Palette index outside palette")
        image = dict(template["palette"][index])
        if "blockEntity" in cell:
            image["blockEntity"] = cell["blockEntity"]
        block_image(image)
        require(template["includesAir"] or image["id"] not in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"),
                "Air block conflicts with includesAir=false")
        indexed[pos] = image
    return indexed


def rotate_state(image):
    output = {"id": image["id"], "properties": dict(image.get("properties", {}))}
    facing = output["properties"].get("facing")
    if facing in DIRECTIONS:
        output["properties"]["facing"] = DIRECTIONS[(DIRECTIONS.index(facing) + 1) % 4]
    axis = output["properties"].get("axis")
    if axis in ("x", "z"):
        output["properties"]["axis"] = "z" if axis == "x" else "x"
    return output


def matching_templates(phase):
    anchor = phase["native"]["independentAnchor"]
    origin = position(anchor)
    source_checks = [check for check in phase["native"]["checks"]
                     if check["name"].startswith("live-") and not check["name"].startswith("live-copy-")]
    directory = under(phase["game"] / "config/openallay-builder/templates", phase["game"] / "config")
    matches = []
    for path in sorted(directory.glob("*.json")):
        path = under(path, directory)
        template = read_json(path)
        indexed = decode_template(template)
        if template["size"] != [5, 3, 5]:
            continue
        if all(state_matches(indexed.get(tuple(check[k] - origin[i] for i, k in enumerate(("x", "y", "z")))),
                             {"id": check["actualId"], "properties": check.get("expectedProperties", {})})
               for check in source_checks):
            matches.append((path, template, indexed))
    require(matches, "No persisted native 5x3x5 template matches the independently read source")
    return matches


def copy_candidate(phase, journal, entries, template):
    anchor = position(phase["native"]["independentAnchor"])
    ax, ay, az = anchor
    if journal.get("status") != "COMPLETED" or not entries:
        return False
    if any(not (ax + 8 <= x <= ax + 12 and ay <= y <= ay + 2 and az <= z <= az + 4)
           for x, y, z in entries):
        return False  # A source+copy combined journal cannot be safely undone alone.
    for (x, y, z), entry in entries.items():
        before = entry.get("before") or {}
        expected_before = "minecraft:grass_block" if y == ay else "minecraft:air"
        if before.get("id") != expected_before or entry.get("verified") is None:
            return False
    for (x, y, z), image in template.items():
        target = (ax + 8 + 4 - z, ay + y, az + x)
        if target not in entries or not state_matches(entries[target].get("verified"), rotate_state(image)):
            return False
    for check in phase["native"]["checks"]:
        if check["name"].startswith("live-copy-"):
            entry = entries.get(position(check))
            if entry is None or not state_matches(entry.get("verified"),
                                                 {"id": check["actualId"], "properties": check.get("expectedProperties", {})}):
                return False
    return True


def validate_copy(directory, repo=REPO):
    phase = load_phase(directory, "builder-live-copy", repo)
    # Source syntax is supporting context only. Native template+journal+readbacks
    # below carry the proof, so a comment/dead-code function name is not enough.
    require(any(re.search(r"\.paste_structure\s*\(", args["source"]) for args, _ in phase["pairs"]),
            "No retained production JavaScript paste_structure call")
    evidence = [item for _, result in phase["pairs"] for item in (result.get("value") or {}).get("evidence", [])]
    source_ids = {item.get("sourceId") for item in evidence}
    require({"openallay_builder:template-save", "openallay_builder:template-load", "openallay_builder:write-readback"} <= source_ids,
            "Missing production native template and write evidence")
    candidates = {}
    for template_path, _, template in matching_templates(phase):
        for path, journal, entries in journals(phase["game"]):
            if copy_candidate(phase, journal, entries, template):
                candidates[journal["id"]] = (path, journal, entries, template_path)
    require(len(candidates) == 1, "Expected one unambiguous completed native target-only copy journal; found " + str(len(candidates)))
    copy_id, (path, journal, entries, template_path) = next(iter(candidates.items()))
    require(isinstance(journal.get("worldId"), str) and journal.get("dimension") == "minecraft:overworld",
            "Copy journal has no native world/dimension identity")
    returned_ids = operation_ids(phase["pairs"])
    linkage = "structured-production-tool-result" if copy_id in returned_ids else "unique-native-target-journal"
    proof = {"outcome": "PASSED", "phase": "copy", "exactCopyOpId": copy_id,
             "worldId": journal["worldId"], "dimension": journal["dimension"],
             "entryCount": len(entries), "changedEntryCount": sum(entry["before"] != entry["verified"] for entry in entries.values()),
             "operationIdLink": linkage, "journal": str(path), "journalSha256": sha256(path),
             "template": str(template_path), "templateSha256": sha256(template_path),
             "reportSha256": sha256(phase["reportPath"]), "traceSha256": sha256(phase["tracePath"]),
             "world": phase["manifest"]["world"], "copyDirectory": str(phase["directory"])}
    require(proof["changedEntryCount"] > 0, "Copy journal contains no actual native changes")
    return proof, phase, journal, entries


def validate_undo(copy_directory, undo_directory, repo=REPO):
    proof, copy_phase, copy_journal, copy_entries = validate_copy(copy_directory, repo)
    undo = load_phase(undo_directory, "builder-live-undo", repo)
    require(undo["game"] == copy_phase["game"]
            and Path(undo["manifest"].get("resumeFrom", "")).resolve() == copy_phase["directory"]
            and undo["manifest"]["world"] == proof["world"]
            and undo["native"]["independentAnchor"] == copy_phase["native"]["independentAnchor"],
            "Undo does not resume the exact independently checked copy world")
    copy_id = proof["exactCopyOpId"]
    require(any(re.search(r"\.undo\s*\(", args["source"]) and copy_id in args["source"] for args, _ in undo["pairs"]),
            "No retained production undo call cites the exact copy operation ID")
    receipts = [{**item, "restored": integral_count(item.get("restored"))}
                for _, result in undo["pairs"] for item in objects(preview(result))
                if integral_count(item.get("restored")) is not None
                and isinstance(item.get("conflicts"), list) and isinstance(item.get("uncertain"), list)]
    require(receipts, "No structured production undo receipt was retained")
    candidates = []
    for path, journal, entries in journals(undo["game"]):
        if journal.get("label") != "Undo " + copy_id or journal.get("status") != "COMPLETED":
            continue
        if journal.get("worldId") != proof["worldId"] or journal.get("dimension") != proof["dimension"] or set(entries) != set(copy_entries):
            continue
        if not all(entry.get("before") == copy_entries[pos].get("verified")
                   and entry.get("verified") == copy_entries[pos].get("before") for pos, entry in entries.items()):
            continue
        if any(receipt.get("operationId") == journal["id"] and receipt["restored"] == len(copy_entries)
               and not receipt["conflicts"] and not receipt["uncertain"] for receipt in receipts):
            candidates.append((path, journal))
    require(len(candidates) == 1, "No unique completed inverse journal matches the exact copy ID and clean undo receipt")
    path, journal = candidates[0]
    return {**proof, "phase": "undo", "exactUndoOpId": journal["id"], "restored": len(copy_entries),
            "conflicts": [], "uncertain": [], "undoJournal": str(path), "undoJournalSha256": sha256(path),
            "undoReportSha256": sha256(undo["reportPath"]), "undoTraceSha256": sha256(undo["tracePath"])}


# Frozen independent native oracle landmarks. These are not model-authored checks.
ACCEPTANCE_LANDMARKS = (
    ('house-floor', 0, 0, 0, 'minecraft:oak_planks', {}),
    ('house-door-lower', 3, 1, 0, 'minecraft:oak_door', {'half': 'lower', 'facing': 'north'}),
    ('house-door-upper', 3, 2, 0, 'minecraft:oak_door', {'half': 'upper', 'facing': 'north'}),
    ('house-bed-foot', 1, 1, 5, 'minecraft:red_bed', {'part': 'foot', 'facing': 'north'}),
    ('house-bed-head', 1, 1, 4, 'minecraft:red_bed', {'part': 'head', 'facing': 'north'}),
    ('house-chest', 4, 1, 5, 'minecraft:chest', {'facing': 'north'}),
    ('house-lantern', 3, 4, 3, 'minecraft:lantern', {'hanging': 'true'}),
    ('skyscraper-lower-light', 14, 3, 2, 'minecraft:sea_lantern', {}),
    ('skyscraper-upper-light', 14, 6, 2, 'minecraft:sea_lantern', {}),
    ('skyscraper-ladder', 13, 1, 3, 'minecraft:ladder', {'facing': 'north'}),
    ('skyscraper-ladder-backing', 13, 1, 4, 'minecraft:iron_block', {}),
    ('skyscraper-rod', 14, 11, 2, 'minecraft:lightning_rod', {'facing': 'up'}),
    ('cottage-beam', 25, 3, 0, 'minecraft:oak_log', {'axis': 'x'}),
    ('cottage-ridge', 26, 7, -1, 'minecraft:dark_oak_slab', {'type': 'bottom'}),
    ('cottage-campfire', 27, 10, 3, 'minecraft:campfire', {'lit': 'true'}),
    ('cottage-chimney', 27, 9, 3, 'minecraft:bricks', {}),
    ('windmill-axle', 38, 5, 0, 'minecraft:oak_log', {'axis': 'z'}),
    ('windmill-sail-spar', 39, 5, 0, 'minecraft:oak_fence', {}),
    ('windmill-sail', 39, 6, 0, 'minecraft:white_wool', {}),
    ('farm-soil', 0, 0, 18, 'minecraft:farmland', {'moisture': '7'}),
    ('farm-crop', 0, 1, 18, 'minecraft:beetroots', {'age': '3'}),
    ('farm-canal', -1, 0, 18, 'minecraft:water', {}),
    ('farm-gate', 1, 1, 17, 'minecraft:oak_fence_gate', {'facing': 'north'}),
    ('dock-start', 14, 0, 18, 'minecraft:spruce_planks', {}),
    ('dock-end', 15, 0, 21, 'minecraft:spruce_planks', {}),
    ('dock-piling', 14, -2, 18, 'minecraft:spruce_log', {'axis': 'y'}),
    ('dock-lantern', 13, 3, 21, 'minecraft:lantern', {'hanging': 'false'}),
    ('geometry-box', 24, 2, 18, 'minecraft:stone_bricks', {}),
    ('geometry-hollow', 25, 1, 19, 'minecraft:air', {}),
    ('geometry-wall', 29, 1, 18, 'minecraft:polished_andesite', {}),
    ('geometry-wall-corner', 28, 2, 18, 'minecraft:gold_block', {}),
    ('geometry-circle', 36, 0, 19, 'minecraft:yellow_concrete', {}),
    ('geometry-cylinder', 39, 2, 19, 'minecraft:blue_concrete', {}),
    ('geometry-cylinder-interior', 40, 1, 19, 'minecraft:air', {}),
    ('geometry-cone', 43, 2, 19, 'minecraft:red_concrete', {}),
    ('geometry-arch-end', 24, 1, 22, 'minecraft:bricks', {}),
    ('geometry-arch-top', 24, 3, 24, 'minecraft:bricks', {}),
    ('geometry-roof-stair', 28, 2, 24, 'minecraft:spruce_stairs', {'facing': 'east', 'half': 'bottom', 'shape': 'straight'}),
    ('geometry-roof-ridge', 29, 3, 24, 'minecraft:spruce_slab', {'type': 'bottom'}),
    ('decoration-door-lower', 33, 1, 24, 'minecraft:birch_door', {'half': 'lower', 'facing': 'east', 'hinge': 'right'}),
    ('decoration-door-upper', 33, 2, 24, 'minecraft:birch_door', {'half': 'upper', 'facing': 'east', 'hinge': 'right'}),
    ('decoration-door-support', 33, 0, 24, 'minecraft:smooth_stone', {}),
    ('decoration-bed-foot', 34, 1, 25, 'minecraft:blue_bed', {'facing': 'east', 'part': 'foot'}),
    ('decoration-bed-head', 35, 1, 25, 'minecraft:blue_bed', {'facing': 'east', 'part': 'head'}),
    ('decoration-bed-support', 35, 0, 25, 'minecraft:smooth_stone', {}),
    ('decoration-flower', 32, 1, 23, 'minecraft:poppy', {}),
    ('decoration-potted-flower', 32, 1, 24, 'minecraft:potted_dandelion', {}),
    ('decoration-window', 38, 1, 23, 'minecraft:glass_pane', {'east': 'true', 'west': 'true'}),
    ('decoration-lantern', 40, 3, 25, 'minecraft:lantern', {'hanging': 'false'}),
    ('decoration-lantern-support', 40, 2, 25, 'minecraft:oak_fence', {}),
    ('decoration-tree-trunk', 43, 1, 25, 'minecraft:oak_log', {'axis': 'y'}),
    ('decoration-tree-crown', 43, 4, 25, 'minecraft:oak_leaves', {'persistent': 'true'}),
    ('terrain-foundation', 0, -1, 32, 'minecraft:dirt', {}),
    ('terrain-clear-flower', 0, 1, 32, 'minecraft:air', {}),
    ('terrain-clear-log', 0, 1, 33, 'minecraft:air', {}),
    ('terrain-clear-all', 2, 1, 33, 'minecraft:air', {}),
    ('terrain-straight-start', 0, 0, 32, 'minecraft:stone_bricks', {}),
    ('terrain-straight-end', 6, 0, 32, 'minecraft:stone_bricks', {}),
    ('terrain-smart-start', 0, 0, 34, 'minecraft:polished_andesite', {}),
    ('terrain-smart-end', 6, 0, 34, 'minecraft:polished_andesite', {}),
    ('terrain-smart-detour', 3, 0, 35, 'minecraft:polished_andesite', {}),
    ('terrain-obstacle', 3, 1, 34, 'minecraft:stone', {}),
    ('terrain-obstacle-ground', 3, 0, 34, 'minecraft:dirt', {}),
    ('template-source-stair', 14, 1, 32, 'minecraft:oak_stairs', {'facing': 'north'}),
    ('template-source-chest', 16, 1, 33, 'minecraft:chest', {'facing': 'east'}),
    ('template-rotation-stair', 21, 1, 32, 'minecraft:oak_stairs', {'facing': 'east'}),
    ('template-rotation-chest', 20, 1, 34, 'minecraft:chest', {'facing': 'south'}),
    ('template-rotation-marker', 19, 1, 33, 'minecraft:red_concrete', {}),
    ('template-rotation-air', 21, 1, 33, 'minecraft:air', {}),
    ('template-front-back-stair', 26, 1, 32, 'minecraft:oak_stairs', {'facing': 'north'}),
    ('template-front-back-chest', 24, 1, 33, 'minecraft:chest', {'facing': 'west'}),
    ('template-front-back-air', 25, 1, 32, 'minecraft:air', {}),
    ('template-left-right-stair', 29, 1, 34, 'minecraft:oak_stairs', {'facing': 'south'}),
    ('template-left-right-chest', 31, 1, 33, 'minecraft:chest', {'facing': 'east'}),
    ('template-left-right-air', 30, 1, 34, 'minecraft:air', {}),
    ('template-combined-stair', 36, 1, 34, 'minecraft:oak_stairs', {'facing': 'east'}),
    ('template-combined-chest', 35, 1, 32, 'minecraft:chest', {'facing': 'north'}),
    ('template-combined-air', 36, 1, 33, 'minecraft:air', {}),
    ('partial-earlier-write', 44, 1, 32, 'minecraft:gold_block', {}),
    ('partial-invalid-next', 45, 1, 32, 'minecraft:air', {}),
    ('cancel-earlier-write', 44, 1, 34, 'minecraft:diamond_block', {}),
    ('cancel-next-denied', 45, 1, 34, 'minecraft:air', {}),
    ('undo-restored', 44, 1, 36, 'minecraft:air', {}),
    ('undo-conflict-preserved', 45, 1, 36, 'minecraft:diamond_block', {}),
    ('geometry-floor-parity', 32, 0, 18, 'minecraft:quartz_block', {}),
)
BUILD_NAMES = ("house", "skyscraper", "cottage", "windmill", "farm", "dock", "geometry_decoration", "terrain", "templates")
FAILURES = (("invalid_native_input", "Builder native operation failed; inspect the session status"),
            ("session_closed", "Builder session is closed or cancelled"))


def native_world_identity(game, manifest):
    """Only the exact 26.2 overworld SavedData identity; no save walk/chunks."""
    require(manifest.get("minecraft") == "26.2", "Bounded native identity parser supports verified mainline 26.2 only")
    require(re.fullmatch(r"openallay-builder-[a-zA-Z0-9_.-]+", manifest["world"]), "Invalid disposable world name")
    # 26.2 stores every dimension, including overworld, under its Identifier path.
    path = under(game / "saves" / manifest["world"]
                 / "dimensions/minecraft/overworld/data/openallay_builder/world_identity.dat", game)
    compressed = read_bytes(path, 65536)
    require(compressed.startswith(b"\x1f\x8b"), "Native identity is not gzip NBT")
    decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
    try:
        data = decoder.decompress(compressed, 65537)
    except zlib.error as failure:
        raise ValueError("Malformed native identity gzip") from failure
    require(len(data) <= 65536 and decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail,
            "Oversized/truncated/trailing native identity gzip")
    # This owner writes exactly root Compound{data:Compound{uuid:String},DataVersion:Int}.
    # A full NBT/save/chunk framework is unnecessary. Reject every other tag/shape.
    offset = 0
    def take(size):
        nonlocal offset
        require(offset + size <= len(data), "Truncated native identity NBT")
        value = data[offset:offset + size]
        offset += size
        return value
    def string():
        size = struct.unpack(">H", take(2))[0]
        require(size <= 1024, "Oversized native identity NBT string")
        return take(size).decode("utf-8")
    def compound(depth):
        require(depth <= 2, "Native identity NBT depth exceeds exact shape")
        value = {}
        while True:
            tag = take(1)[0]
            if tag == 0:
                return value
            name = string()
            require(name not in value, "Duplicate native identity NBT member")
            if tag == 10:
                value[name] = compound(depth + 1)
            elif tag == 8:
                value[name] = string()
            elif tag == 3:
                value[name] = struct.unpack(">i", take(4))[0]
            else:
                raise ValueError("Unexpected native identity NBT tag")
    require(take(1) == b"\x0a" and string() == "", "Invalid native identity NBT root")
    identity = compound(0)
    require(offset == len(data), "Trailing native identity NBT")
    fields(identity, ("data", "DataVersion"))
    fields(identity["data"], ("uuid",))
    integer(identity["DataVersion"])
    return canonical_uuid(identity["data"]["uuid"]), path, identity["DataVersion"]


def scalar_receipt(result):
    value = result.get("value")
    require(result.get("status") == "success" and isinstance(value, dict)
            and value.get("resultType") == "string" and value.get("complete") is True
            and isinstance(value.get("preview"), str), "Missing complete actual scalar Tool receipt")
    receipt = strict_json(value["preview"])
    require(isinstance(receipt, dict), "Tool receipt is not an object")
    return receipt


def public_rows(values):
    require(isinstance(values, list), "Missing public journal rows")
    rows = {}
    for row in values:
        fields(row, ("id", "label", "status", "entries"))
        canonical_uuid(row["id"])
        require(row["id"] not in rows and isinstance(row["label"], str) and row["label"].strip()
                and row["status"] in ("completed", "failed", "cancelled"), "Invalid/duplicate public journal row")
        count = integral_count(row["entries"])
        require(count is not None and count > 0, "Invalid public journal entry count")
        rows[row["id"]] = {**row, "entries": count}
    return rows


def completed_id(item):
    require(isinstance(item, dict) and item.get("state") == "completed" and integral_count(item.get("writes")) not in (None, 0),
            "Missing real completed write operation")
    return canonical_uuid(item["operationId"])


def read_only_status(item):
    require(isinstance(item, dict) and item.get("state") == "completed" and integral_count(item.get("writes")) == 0
            and "operationId" not in item, "Observation is not a fresh completed read-only invocation")


def receipt_rows(receipt, token):
    operations = receipt["operations"]
    require(isinstance(operations, list) and len(operations) == 9, "Expected nine completed build operations")
    expected, identifiers = [], set()
    for name, operation in zip(BUILD_NAMES, operations):
        identifier = completed_id(operation)
        require(operation.get("name") == name and identifier not in identifiers, "Wrong/duplicate build operation")
        identifiers.add(identifier)
        expected.append((identifier, "OpenAllay E2E Builder acceptance", "completed", None))
    lifecycle = receipt["lifecycle"]
    fields(lifecycle, ("partial", "cancel", "undo"))
    for kind, terminal in (("partial", "failed"), ("cancel", "cancelled")):
        item = lifecycle[kind]
        fields(item, ("failure", "journal", "positions", "beforeImages", "afterImages"))
        row = next(iter(public_rows([item["journal"]]).values()))
        require(row["id"] not in identifiers, "Lifecycle reused operation ID")
        identifiers.add(row["id"])
        expected.append((row["id"], "OpenAllay E2E lifecycle " + token + " " + kind, terminal, 1))
    undo = lifecycle["undo"]
    fields(undo, ("result", "status", "originalStatus", "interventionStatus", "positions", "beforeImages", "afterImages"))
    original = completed_id(undo["originalStatus"])
    for key, count, label in (("originalStatus", 2, "OpenAllay E2E lifecycle " + token + " undo original"),
                              ("interventionStatus", 1, "OpenAllay E2E lifecycle " + token + " undo intervention"),
                              ("status", 1, "Undo " + original)):
        identifier = completed_id(undo[key])
        require(identifier not in identifiers, "Undo reused operation ID")
        identifiers.add(identifier)
        expected.append((identifier, label, "completed", count))
    return expected


def expected_landmarks(anchor):
    origin = position(anchor)
    result = {}
    for name, x, y, z, block, properties in ACCEPTANCE_LANDMARKS:
        if name == "geometry-floor-parity":
            block = "minecraft:quartz_block" if (origin[0] + origin[2] + 50) % 2 == 0 else "minecraft:black_concrete"
        result[name] = (tuple(origin[i] + n for i, n in enumerate((x, y, z))), {"id": block, "properties": properties})
    return result


def validate_landmarks(native, anchor):
    expected = expected_landmarks(anchor)
    checks = native.get("checks")
    require(isinstance(checks, list) and len(checks) == 85, "Missing/extra native landmarks; expected all 85")
    observed = {}
    for check in checks:
        name = check.get("name")
        require(name in expected and name not in observed, "Foreign/duplicate native landmark")
        pos, image = expected[name]
        actual = {"id": check.get("actualId"), "properties": check.get("actualProperties")}
        block_image(actual)
        require(position(check) == pos and check.get("expectedId") == image["id"]
                and check.get("expectedProperties") == image["properties"] and state_matches(actual, image)
                and check.get("passed") is True and check.get("observed") is True, "Native landmark does not match independent oracle")
        observed[pos] = actual
    return observed


def validate_fixture_receipts(phase):
    pairs = phase["pairs"]
    require(len(pairs) == 7, "Expected exactly seven actual JavaScript Tools")
    # Check Skill+JS production calls; trace has no invocation IDs. The approved
    # consumer already binds probeToken to GuideToolActivity.invocationId.
    calls = [e["payload"] for e in phase["trace"]["events"] if e.get("type") == "tool_call"]
    require(len(calls) == 8 and calls[0].get("toolId") == "openallay:load_skill"
            and calls[0].get("arguments", {}).get("name") == "minecraft-builder"
            and all(call.get("toolId") == JS_TOOL for call in calls[1:]), "Wrong actual Skill/Tool chronology")
    skill_results = [e["payload"] for e in phase["trace"]["events"] if e.get("type") == "tool_result"
                     and e["payload"].get("toolId") == "openallay:load_skill"]
    require(len(skill_results) == 1 and skill_results[0].get("failure") is False
            and skill_results[0].get("result", {}).get("status") == "success", "Actual Skill did not succeed")
    receipts = {}
    stages = {0: "build", 2: "partial_observation", 4: "cancel_observation", 5: "undo", 6: "final"}
    for index, stage in stages.items():
        receipt = scalar_receipt(pairs[index][1])
        require(receipt.get("scenario") == "builder_acceptance" and receipt.get("stage") == stage, "Wrong actual receipt stage")
        receipts[stage] = receipt
    build, final = receipts["build"], receipts["final"]
    token = build.get("probeToken")
    require(isinstance(token, str) and token.strip(), "Missing actual probe token")
    fields(build["context"], ("dimension", "playerUuid"))
    canonical_uuid(build["context"]["playerUuid"])
    require(build["context"]["dimension"] == "minecraft:overworld", "Wrong current acceptance dimension")
    require(build["anchor"] == phase["native"]["independentAnchor"], "Tool/native anchor differs")
    require(phase["native"].get("receiptNativeBindingPassed") is True and phase["native"].get("toolContractPassed") is True,
            "Actual consumer Tool/native receipt binding did not pass")
    command = phase["manifest"].get("command", [])
    require(isinstance(command, list) and command.count("--uuid") == 1, "Launch lacks exact actor UUID")
    require(command.index("--uuid") + 1 < len(command), "Launch UUID value is missing")
    actor = str(uuid.UUID(command[command.index("--uuid") + 1]))
    require(actor == build["context"]["playerUuid"] == phase["trace"].get("actorId"), "Actual actor/launch/trace relation differs")
    for receipt in receipts.values():
        require(all(receipt.get(key) == build.get(key) for key in ("anchor", "context", "probeToken")), "Fresh receipt context changed")
    for index, (code, message) in zip((1, 3), FAILURES):
        failure = pairs[index][1]
        fields(failure, ("status", "code", "message"))
        require(failure == {"status": "failure", "code": code, "message": message}, "Expected actual FAILED Tool differs")
    for stage in ("partial_observation", "cancel_observation", "final"):
        read_only_status(receipts[stage]["observationStatus"])
    expected = receipt_rows(final, token)
    final_rows = public_rows(final["durableOperations"])
    require(len(final_rows) == 14 and set(final_rows) == {row[0] for row in expected}, "Expected exact 14 public journal IDs")
    for identifier, label, terminal, count in expected:
        row = final_rows[identifier]
        require(row["label"] == label and row["status"] == terminal and (count is None or row["entries"] == count),
                "Public journal label/state/count differs")
    build_ids = {operation["operationId"] for operation in build["operations"]}
    require(len(build_ids) == 9 and build_ids == {row[0] for row in expected[:9]}
            and public_rows(build["baselineOperations"]) == {key: final_rows[key] for key in build_ids},
            "Build baseline is not exactly nine completed journals")
    previous = public_rows(build["baselineOperations"])
    for stage, added in (("partial_observation", expected[9:10]), ("cancel_observation", expected[10:11]), ("undo", expected[11:])):
        current = public_rows(receipts[stage]["durableOperations"])
        wanted = {**previous, **{row[0]: final_rows[row[0]] for row in added}}
        require(current == wanted, "Fresh public journal delta changed old/missing/extra rows")
        previous = current
    require(previous == final_rows, "Final public list changed after undo")
    for key in ("operations", "status", "actions", "templates", "sites", "terrain", "baselineOperations", "seed", "provider"):
        require(final.get(key) == build.get(key), "Final receipt did not relay validated build fields")
    require(final["status"].get("state") == "completed" and final["status"].get("operationId") == expected[8][0],
            "Final status is not the real completed ninth build operation")
    for kind, stage in (("partial", "partial_observation"), ("cancel", "cancel_observation"), ("undo", "undo")):
        require(receipts[stage]["lifecycle"][kind] == final["lifecycle"][kind], "Final lifecycle was invented or changed")
    origin = position(final["anchor"])
    air = {"id": "minecraft:air", "properties": {}}
    for kind, z, first in (("partial", 32, "gold_block"), ("cancel", 34, "diamond_block"), ("undo", 36, "air")):
        item = final["lifecycle"][kind]
        positions = [tuple(origin[i] + n for i, n in enumerate((x, 1, z))) for x in (44, 45)]
        require([position(p) for p in item["positions"]] == positions and item["beforeImages"] == [air, air],
                "Lifecycle exact positions/before images differ")
        after = [{"id": "minecraft:" + first, "properties": {}}, air if kind != "undo" else {"id": "minecraft:diamond_block", "properties": {}}]
        require(item["afterImages"] == after, "Fresh lifecycle readback differs")
        require(build["lifecycle"][kind] == {"positions": item["positions"], "beforeImages": [air, air]}, "Lifecycle prerequisites changed")
        if kind != "undo":
            failure = pairs[1 if kind == "partial" else 3][1]
            require(item["failure"] == failure and public_rows([item["journal"]])[item["journal"]["id"]] == final_rows[item["journal"]["id"]],
                    "Lifecycle failure/journal not actual Tool/public row")
    undo = final["lifecycle"]["undo"]
    fields(undo["result"], ("operationId", "restored", "conflicts", "uncertain"))
    require(undo["result"]["operationId"] == expected[13][0] and integral_count(undo["result"]["restored"]) == 1
            and [position(p) for p in undo["result"]["conflicts"]] == [position(undo["positions"][1])]
            and undo["result"]["uncertain"] == [], "Actual undo restore/conflict receipt differs")
    return final, final_rows, expected, actor


def validate_acceptance(directory, repo=REPO, reload_directory=None):
    phase = load_phase(directory, "builder-acceptance", repo, allow_fixture_failures=True)
    require(not phase["manifest"].get("resumeFrom"), "Acceptance must use the exact original disposable world")
    receipt, rows, expected, actor = validate_fixture_receipts(phase)
    world_id, identity_path, data_version = native_world_identity(phase["game"], phase["manifest"])
    loaded = journals(phase["game"], rows)
    actual = {journal["id"]: (path, journal, entries) for path, journal, entries in loaded}
    require(len(actual) == 14 and set(actual) == set(rows), "Missing/extra/foreign native journals; expected exact 14")
    origin = position(receipt["anchor"])
    latest = {}
    for identifier, label, terminal, count in expected:
        path, journal, entries = actual[identifier]
        require(journal["worldId"] == world_id and journal["dimension"] == receipt["context"]["dimension"],
                "Native journal belongs to another exact world/dimension")
        require(journal["label"] == label and journal["status"].lower() == terminal and len(entries) == rows[identifier]["entries"],
                "Actual native journal differs from exact public row")
        require(all(-4 <= pos[0] - origin[0] <= 46 and -3 <= pos[1] - origin[1] <= 13 and -4 <= pos[2] - origin[2] <= 39
                    for pos in entries), "Native journal entry outside bounded fixture site")
        for pos, entry in entries.items():
            if pos in latest:
                require(entry["before"] == latest[pos], "Native cross-operation before/actual image chain differs")
            latest[pos] = entry["verified"]
    landmarks = validate_landmarks(phase["native"], receipt["anchor"])
    for name, (pos, expected_image) in expected_landmarks(receipt["anchor"]).items():
        if name.startswith(("partial-", "cancel-", "undo-")):
            continue  # Exact lifecycle intended/actual image checks follow below.
        final_entry = next((actual[row[0]][2][pos] for row in reversed(expected[:9]) if pos in actual[row[0]][2]), None)
        require(final_entry is None or state_matches(final_entry["intended"], expected_image),
                "Native build intended image differs from independent landmark")
    for pos, image in landmarks.items():
        require(pos not in latest or latest[pos]["id"] == image["id"]
                and latest[pos]["properties"] == image["properties"], "Native journal verified image differs from native landmark")
        require(pos in latest or image["id"] in ("minecraft:air", "minecraft:grass_block"),
                "Written native landmark missing from journals")
    air, gold, diamond = ({"id": "minecraft:" + name, "properties": {}} for name in ("air", "gold_block", "diamond_block"))
    def exact_entries(identifier, images):
        entries = actual[identifier][2]
        require(set(entries) == set(images), "Lifecycle journal has missing/extra/denied/conflict entries")
        for pos, (before, intended, verified) in images.items():
            entry = entries[pos]
            require((entry["before"], entry["intended"], entry["verified"]) == (before, intended, verified),
                    "Lifecycle native before/intended/actual images differ")
    def point(x, z):
        return origin[0] + x, origin[1] + 1, origin[2] + z
    exact_entries(expected[9][0], {point(44, 32): (air, gold, gold)})
    exact_entries(expected[10][0], {point(44, 34): (air, diamond, diamond)})
    exact_entries(expected[11][0], {point(44, 36): (air, gold, gold), point(45, 36): (air, gold, gold)})
    exact_entries(expected[12][0], {point(45, 36): (gold, diamond, diamond)})
    exact_entries(expected[13][0], {point(44, 36): (gold, air, air)})
    retained_path = under(phase["game"] / "config/openallay/e2e" / (phase["manifest"]["world"] + ".acceptance.json"), phase["game"])
    retained = read_json(retained_path)
    fields(retained, ("outcome", "requestId", "worldName", "nativeAnchor", "operations", "lifecycle", "templates"))
    require(retained["outcome"] == "PASSED" and retained["requestId"] == phase["report"]["requestId"]
            and retained["worldName"] == phase["manifest"]["world"] and retained["nativeAnchor"] == receipt["anchor"],
            "Retained actual public receipt world/request/anchor differs")
    enriched = strict_json(json.dumps(receipt))
    for operation in enriched["operations"]:
        operation["journal"] = rows[operation["operationId"]]
    for key in ("originalStatus", "interventionStatus", "status"):
        item = enriched["lifecycle"]["undo"][key]
        item["journal"] = rows[item["operationId"]]
    require(all(retained[key] == enriched[key] for key in ("operations", "lifecycle", "templates")),
            "Retained receipt did not persist actual 14 public journal rows")
    template_dir = under(phase["game"] / "config/openallay-builder/templates", phase["game"])
    require(receipt["templates"]["saved"] == ["openallay_e2e_builder_native"] and receipt["templates"]["listed"] is True,
            "Actual template save/list receipt differs")
    template_paths = bounded_entries(template_dir, 2)
    template_path = template_dir / "openallay_e2e_builder_native.json"
    require(template_paths == [template_path], "Missing/extra persisted native templates")
    under(template_path, template_dir)
    template = read_json(template_path)
    cells = decode_template(template)
    require(template["size"] == [3, 2, 3] and template["includesAir"] is True and len(cells) == 18
            and template["metadata"] == {"scenario": "builder_acceptance", "seed": 17}
            and template["gameVersion"] == phase["manifest"]["minecraft"] and template["dataVersion"] == data_version,
            "Actual native template coverage/metadata/version differs")
    require(receipt["templates"]["size"] == template["size"]
            and integral_count(receipt["templates"]["blockCount"]) == len(cells)
            and integral_count(receipt["templates"]["paletteSize"]) == len(template["palette"]), "Template receipt/storage counts differ")
    template_entries = actual[expected[8][0]][2]
    for relative, image in cells.items():
        pos = tuple(origin[i] + n for i, n in enumerate((14 + relative[0], relative[1], 32 + relative[2])))
        entry = template_entries.get(pos)
        # Canonical air can be proven unchanged and omitted by the native sparse writer.
        require(entry is not None and entry["verified"] == image or entry is None and image == air,
                "Persisted template image differs from actual source journal")
    proof = {"outcome": "PASSED", "phase": "acceptance", "world": phase["manifest"]["world"], "worldId": world_id,
             "dimension": receipt["context"]["dimension"], "actorUuid": actor,
             "actorBinding": "actual-context-and-trace-actorId-and-exact-launch-uuid; journals-have-no-actor-field",
             "identity": str(identity_path), "identitySha256": sha256(identity_path), "nativeDataVersion": data_version,
             "journalRows": rows, "journalSha256": {str(path): sha256(path) for path, _, _ in loaded},
             "coveredDeltaSha256": {str(path): sha256(path) for path in sorted(actual[expected[0][0]][0].parent.iterdir()) if "--" in path.stem},
             "landmarkCount": 85, "template": str(template_path), "templateSha256": sha256(template_path),
             "retainedReceipt": str(retained_path), "retainedReceiptSha256": sha256(retained_path),
             "manifestSha256": sha256(phase["directory"] / "launch.json"), "reportSha256": sha256(phase["reportPath"]),
             "traceSha256": sha256(phase["tracePath"]),
             "limits": "No chunks/save scan; 85 actual native readbacks plus complete journal images and exact identity SavedData only."
             }
    if reload_directory is not None:
        reload = load_phase(reload_directory, "builder-reload", repo)
        require(reload["game"] == phase["game"] and Path(reload["manifest"].get("resumeFrom", "")).absolute() == phase["directory"]
                and reload["manifest"]["world"] == phase["manifest"]["world"] and reload["native"].get("exactPersistencePassed") is True,
                "Reload does not independently resume exact original acceptance world")
        reload_receipt = scalar_receipt(reload["pairs"][-1][1])
        require(reload_receipt.get("scenario") == "builder_reload" and public_rows(reload_receipt["operations"]) == rows
                and integral_count(reload_receipt["operationCount"]) == 14, "Reload public journal persistence differs")
        validate_landmarks(reload["native"], receipt["anchor"])
        require(reload["native"]["independentAnchor"] == receipt["anchor"] and reload["trace"].get("actorId") == actor,
                "Reload native anchor/actual actor differs")
        read_only_status(reload_receipt["status"])
        template_receipt = reload_receipt["template"]
        require(template_receipt == {"name": "openallay_e2e_builder_native", "size": template["size"]},
                "Reload actual public template receipt differs")
        require(reload_receipt["listed"] == ["openallay_e2e_builder_native"], "Reload list lost/added persisted native template")
        proof.update(reloadReportSha256=sha256(reload["reportPath"]), reloadTraceSha256=sha256(reload["tracePath"]))
    return proof


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("copy", "undo", "acceptance"))
    parser.add_argument("--copy-dir", type=Path)
    parser.add_argument("--acceptance-dir", type=Path)
    parser.add_argument("--reload-dir", type=Path)
    parser.add_argument("--undo-dir", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.phase == "acceptance":
            require(args.acceptance_dir is not None, "acceptance requires --acceptance-dir")
            proof = validate_acceptance(args.acceptance_dir, reload_directory=args.reload_dir)
        else:
            require(args.copy_dir is not None, "copy/undo requires --copy-dir")
            require(args.phase == "copy" or args.undo_dir is not None, "undo requires --undo-dir")
            proof = validate_copy(args.copy_dir)[0] if args.phase == "copy" else validate_undo(args.copy_dir, args.undo_dir)
        print(json.dumps(proof, indent=2, sort_keys=True))
        return 0
    except (ValueError, OSError, KeyError, TypeError, IndexError, struct.error, RecursionError) as failure:
        print("Live Builder evidence refused: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
