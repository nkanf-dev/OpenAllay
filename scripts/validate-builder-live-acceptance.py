#!/usr/bin/env python3
"""Read-only post-check of retained live Builder copy and undo evidence.

Reads only packaged acceptance reports, production traces, templates and journals.
It does not launch Minecraft, call a provider, inspect save chunks or modify files.
Printed JSON is derived evidence, not a model-authored acceptance claim.
"""

import argparse
from collections import defaultdict, deque
import hashlib
import json
import math
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


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def under(path, root):
    path, root = Path(path).resolve(), Path(root).resolve()
    require(path.is_relative_to(root), "Evidence path escaped its acceptance directory")
    return path


def load_phase(directory, scenario, repo=REPO):
    directory = under(directory, Path(repo) / "build/e2e")
    manifest = read_json(directory / "launch.json")
    require(manifest.get("scenario") == scenario and manifest.get("noGameLaunched") is False,
            "Manifest does not identify a launched " + scenario)
    game = under(manifest["gameDirectory"], Path(repo) / "build/e2e")
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
    pairs = tool_pairs(trace)
    require(pairs, "No production JavaScript calls were retained")
    return {"directory": directory, "manifest": manifest, "game": game, "report": report,
            "native": native, "trace": trace, "pairs": pairs,
            "reportPath": report_path, "tracePath": trace_path}


def tool_pairs(trace):
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
                require(payload.get("failure") is False and normalized.get("status") == "success",
                        "A live JavaScript Tool failed")
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


def journals(game):
    directory = under(game / "config/openallay-builder/journals", game / "config")
    result = []
    for path in sorted(directory.glob("*.json")):
        path = under(path, directory)
        journal = read_json(path)
        require(journal.get("format") == JOURNAL_FORMAT and journal.get("version") == 1,
                "Unsupported native journal format")
        require(path.stem == journal.get("id"), "Native journal filename and ID differ")
        entries = journal.get("entries")
        require(isinstance(entries, list), "Native journal has no entry list")
        indexed = {position(entry["position"]): entry for entry in entries}
        require(len(indexed) == len(entries), "Native journal has duplicate positions")
        result.append((path, journal, indexed))
    return result


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
        if template.get("format") != "openallay:structure" or template.get("version") != 1 or template.get("size") != [5, 3, 5]:
            continue
        palette, cells = template.get("palette", []), template.get("blocks", [])
        indexed = {}
        for cell in cells:
            pos, index = cell.get("pos"), cell.get("state")
            require(isinstance(pos, list) and len(pos) == 3 and all(type(n) is int for n in pos)
                    and type(index) is int and 0 <= index < len(palette), "Malformed persisted native template")
            require(tuple(pos) not in indexed, "Duplicate persisted template position")
            indexed[tuple(pos)] = palette[index]
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


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase", choices=("copy", "undo"))
    parser.add_argument("--copy-dir", type=Path, required=True)
    parser.add_argument("--undo-dir", type=Path)
    args = parser.parse_args(argv)
    try:
        require(args.phase == "copy" or args.undo_dir is not None, "undo requires --undo-dir")
        proof = validate_copy(args.copy_dir)[0] if args.phase == "copy" else validate_undo(args.copy_dir, args.undo_dir)
        print(json.dumps(proof, indent=2, sort_keys=True))
        return 0
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError) as failure:
        print("Live Builder evidence refused: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
