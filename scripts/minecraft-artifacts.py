#!/usr/bin/env python3
"""Select native binary release families; inspect offline receipt consistency only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import sys
from minecraft_target_loaders import target_loaders

ROOT = Path(__file__).resolve().parents[1]
LOADERS = ("fabric", "forge", "neoforge")
DEFAULT_LOADERS = ("fabric", "neoforge")
MAX_JSON_BYTES = 1024 * 1024
MAX_ARTIFACT_BYTES = 512 * 1024 * 1024
MAX_EVIDENCE_BYTES = 16 * 1024 * 1024
FAMILY_FIELDS = {"id", "loader", "buildTarget", "supportedTargets", "filenameTemplate", "packagingRecipe", "artifactKind", "publicationChannels"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def shape(value, fields, label):
    require(type(value) is dict and set(value) == set(fields), label + ": exact fields required")


def text(value, label):
    require(type(value) is str and value and value == value.strip(), label + ": nonempty unpadded string required")
    return value


def hash_text(value):
    require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value), "Expected lowercase SHA256")
    return value


def regular(path, limit):
    info = path.stat()
    require(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= limit, "File is empty, not regular, or too large: " + str(path))
    return info


def read_json(path):
    regular(path, MAX_JSON_BYTES)
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate JSON key: " + key)
            result[key] = value
        return result
    def no_constant(value):
        raise ValueError("Nonstandard JSON constant: " + value)
    with path.open("rb") as stream:
        data = stream.read(MAX_JSON_BYTES + 1)
    require(len(data) <= MAX_JSON_BYTES, "JSON grew beyond byte limit")
    return json.loads(data.decode("utf-8"), object_pairs_hook=unique, parse_constant=no_constant)


def file_hash(path, limit):
    before = regular(path, limit)
    digest = hashlib.sha256()
    count = 0
    with path.open("rb") as stream:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            count += len(block)
            require(count <= limit, "File grew beyond bound: " + str(path))
            digest.update(block)
    after = path.stat()
    signature = lambda info: (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns)
    require(signature(before) == signature(after) and count == after.st_size, "File changed while reading: " + str(path))
    return digest.hexdigest()


def target_key(target):
    text(target, "Minecraft target")
    require(re.fullmatch(r"[1-9][0-9]*(?:\.(?:0|[1-9][0-9]*)){1,2}", target), "Expected exact stable Minecraft target")
    numbers = tuple(int(part) for part in target.split("."))
    return numbers + (0,) * (3 - len(numbers))


def interval(targets, order):
    require(type(targets) is list and targets and all(type(target) is str for target in targets), "Target interval must be a nonempty string list")
    require(len(set(targets)) == len(targets) and all(target in order for target in targets), "Unknown or duplicate interval target")
    start = order.index(targets[0])
    require(order[start:start + len(targets)] == targets, "Interval targets must be ordered and include every known intermediate target")


def label_for(targets):
    return targets[0] if len(targets) == 1 else targets[0] + "-through-" + targets[-1]


def family_for(loader, build_target, targets):
    label = label_for(targets)
    legacy = loader == "forge" and targets == [build_target] and build_target == "1.16.5"
    kind = "jar"
    recipe = "forge-stock8" if loader == "forge" and targets == ["1.12.2"] else ("forge-flat" if legacy else "nested-mod")
    return {"id": loader + "-" + label, "loader": loader, "buildTarget": build_target,
            "supportedTargets": list(targets), "filenameTemplate": "openallay-" + loader + "-" + label + "-{version}." + kind,
            "packagingRecipe": recipe, "artifactKind": kind,
            "publicationChannels": ["github", "modrinth"]}


def validate_family(family, order):
    shape(family, FAMILY_FIELDS, "Artifact family")
    require(family["loader"] in LOADERS, "Unsupported loader")
    interval(family["supportedTargets"], order)
    if any(target_key(target) < target_key("1.18.2") for target in family["supportedTargets"]):
        require(family["loader"] == "forge" and family["supportedTargets"] == [family["buildTarget"]]
                and family["buildTarget"] in ("1.12.2", "1.16.5"), "Stock legacy recipes require an exact accepted Forge tuple")
    require(family["buildTarget"] in family["supportedTargets"], "buildTarget must be inside its interval")
    require(family == family_for(family["loader"], family["buildTarget"], family["supportedTargets"]), "Family id/filename must match its loader and exact interval")


def read_catalog(path):
    catalog = read_json(path)
    shape(catalog, {"defaultTarget", "targetOrder", "acceptedFamilies", "candidateIntervals"}, "Catalog")
    require(catalog["defaultTarget"] == "26.2", "Existing development default must stay 26.2")
    order = catalog["targetOrder"]
    require(type(order) is list and order and len(order) <= 128, "Expected bounded external target order")
    keys = [target_key(target) for target in order]
    require(keys == sorted(set(keys)) and keys[0] >= target_key("1.12.2") and "26.2" in order
            and all(target_key(target) >= target_key("1.18.2") or target in ("1.12.2", "1.16.5") for target in order), "External target order must be unique, ascending, and within this phase")
    accepted = catalog["acceptedFamilies"]
    candidates = catalog["candidateIntervals"]
    require(type(accepted) is list and 0 < len(accepted) <= 128 and type(candidates) is list and len(candidates) <= 128, "Expected bounded family/candidate lists")
    ids, coverage = set(), set()
    for family in accepted:
        validate_family(family, order)
        require(all(family["loader"] in target_loaders(ROOT, target)["loaders"]
                    for target in family["supportedTargets"]), "Family must use actual native target loaders")
        require(family["id"] not in ids, "Duplicate family id")
        ids.add(family["id"])
        for target in family["supportedTargets"]:
            key = (family["loader"], target)
            require(key not in coverage, "Overlapping accepted families for loader/target")
            coverage.add(key)
    require(all((loader, "26.2") in coverage for loader in DEFAULT_LOADERS), "Keep the current default family for both loaders")
    candidate_ids = set()
    for candidate in candidates:
        shape(candidate, {"loaders", "buildTarget", "targets", "publishing"}, "Candidate interval")
        require(candidate["publishing"] is False, "Candidates must remain nonpublishing")
        loaders = candidate["loaders"]
        require(type(loaders) is list and loaders and all(type(loader) is str and loader in LOADERS for loader in loaders)
                and len(set(loaders)) == len(loaders), "Candidate loaders must be distinct known loaders")
        interval(candidate["targets"], order)
        require(all(loader in target_loaders(ROOT, target)["loaders"]
                    for loader in loaders for target in candidate["targets"]), "Candidate must use actual native target loaders")
        require(candidate["buildTarget"] in candidate["targets"], "Candidate buildTarget must be inside its interval")
        for loader in loaders:
            candidate_id = family_for(loader, candidate["buildTarget"], candidate["targets"])["id"]
            require(candidate_id not in ids | candidate_ids, "Duplicate accepted/candidate family id")
            candidate_ids.add(candidate_id)
    return catalog


def resolve(catalog, target, loader):
    require(loader in LOADERS and target in catalog["targetOrder"], "Unknown loader or target")
    found = [family for family in catalog["acceptedFamilies"] if family["loader"] == loader and target in family["supportedTargets"]]
    require(len(found) == 1, "Target/loader has no unique accepted artifact family; candidates do not publish")
    return found[0]


def receipt_family(catalog, family_id, loader):
    require(loader in LOADERS, "Unsupported loader")
    families = catalog["acceptedFamilies"] + [family_for(candidate_loader, candidate["buildTarget"], candidate["targets"])
        for candidate in catalog["candidateIntervals"] for candidate_loader in candidate["loaders"]]
    found = [family for family in families if family["id"] == family_id and family["loader"] == loader]
    require(len(found) == 1, "No unique family for this id/loader")
    return found[0]


def describe(family, version=None):
    targets = family["supportedTargets"]
    result = dict(family)
    result["minecraftMavenRange"] = "[" + targets[0] + ("]" if len(targets) == 1 else "," + targets[-1] + "]")
    result["fabricMinecraftPredicate"] = targets[0] if len(targets) == 1 else ">=" + targets[0] + " <=" + targets[-1]
    if version is not None:
        require(type(version) is str and re.fullmatch(r"[0-9][0-9A-Za-z]*(?:[.+-][0-9A-Za-z]+)*", version), "Unsafe release version/filename")
        result["filename"] = family["filenameTemplate"].replace("{version}", version)
    return result


def absolute_file(value):
    value = text(value, "Artifact path")
    path = Path(value)
    require(path.is_absolute() and str(path.resolve(strict=True)) == value, "Artifact path must be absolute, canonical, and not a symlink")
    return path


def evidence_file(value, directory):
    value = text(value, "Evidence path")
    parts = value.split("/")
    require(all(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", part) and ".." not in part for part in parts), "Evidence path must be safe and relative")
    path = directory.joinpath(*parts)
    require(path.resolve(strict=True).is_relative_to(directory) and path.resolve(strict=True) == path, "Evidence must stay inside receipt directory without symlinks")
    return path


def verify_receipt(family, receipt_path, artifact_path, expected_sha):
    expected_sha = hash_text(expected_sha)
    artifact = absolute_file(str(artifact_path))
    require(artifact.suffix == "." + family["artifactKind"], "Artifact suffix differs from exact family format")
    actual_sha = file_hash(artifact, MAX_ARTIFACT_BYTES)
    require(actual_sha == expected_sha, "Actual artifact SHA256 differs from expected SHA256")
    receipt_path = receipt_path.resolve(strict=True)
    receipt = read_json(receipt_path)
    shape(receipt, {"familyId", "loader", "artifactPath", "artifactSha256", "runs"}, "Receipt")
    require(receipt["familyId"] == family["id"] and receipt["loader"] == family["loader"], "Receipt family/loader mismatch")
    require(receipt["artifactPath"] == str(artifact) and hash_text(receipt["artifactSha256"]) == expected_sha, "Receipt artifact path/SHA mismatch")
    runs = receipt["runs"]
    require(type(runs) is list and len(runs) == len(family["supportedTargets"]), "Receipt must cover each exact target once")
    evidence_paths, observed = set(), set()
    for run in runs:
        shape(run, {"target", "loader", "artifactPath", "artifactSha256", "kind", "outcome", "evidencePath", "evidenceSha256"}, "Target runtime receipt")
        require(type(run["target"]) is str and run["target"] in family["supportedTargets"] and run["target"] not in observed, "Missing, extra, or duplicate receipt target")
        observed.add(run["target"])
        require(run["loader"] == family["loader"] and run["artifactPath"] == str(artifact)
                and hash_text(run["artifactSha256"]) == expected_sha, "All targets must use the same loader and immutable artifact path/SHA")
        require(run["kind"] == "runtime" and run["outcome"] == "passed", "Compilation/preparation is not a passed runtime receipt")
        evidence = evidence_file(run["evidencePath"], receipt_path.parent)
        require(evidence not in evidence_paths and evidence not in (artifact, receipt_path), "Each target needs separate retained runtime evidence")
        evidence_paths.add(evidence)
        require(file_hash(evidence, MAX_EVIDENCE_BYTES) == hash_text(run["evidenceSha256"]), "Retained evidence SHA256 mismatch")
    require(file_hash(artifact, MAX_ARTIFACT_BYTES) == expected_sha, "Artifact changed during receipt validation")
    return {"familyId": family["id"], "loader": family["loader"], "supportedTargets": family["supportedTargets"],
            "artifactSha256": expected_sha, "receiptConsistency": "passed", "runtimeAcceptance": "requires-external-review"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=ROOT / "gradle/minecraft-artifacts.json")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate", help="Validate source catalog shape, not binary support")
    selection = commands.add_parser("resolve", help="Resolve only accepted families")
    selection.add_argument("--loader", choices=LOADERS, required=True)
    selection.add_argument("--target")
    selection.add_argument("--version")
    verification = commands.add_parser("verify-receipt", help="Check integrity; never promote a candidate")
    verification.add_argument("--family", required=True)
    verification.add_argument("--loader", choices=LOADERS, required=True)
    for command, mandatory in ((selection, False), (verification, True)):
        command.add_argument("--receipt", type=Path, required=mandatory)
        command.add_argument("--artifact", type=Path, required=mandatory)
        command.add_argument("--sha256", required=mandatory)
    args = parser.parse_args(argv)
    try:
        catalog = read_catalog(args.catalog)
        if args.command == "validate":
            result = {"catalogShape": "valid", "acceptedFamilies": len(catalog["acceptedFamilies"]), "candidateIntervals": len(catalog["candidateIntervals"])}
        elif args.command == "resolve":
            family = resolve(catalog, args.target if args.target is not None else catalog["defaultTarget"], args.loader)
            supplied = (args.receipt is not None, args.artifact is not None, args.sha256 is not None)
            require(all(supplied) or not any(supplied), "receipt/artifact/sha256 must be provided together")
            existing_singleton = family["supportedTargets"] == ["26.2"]
            require(existing_singleton or all(supplied), "Newly listed families require a same-artifact runtime receipt; catalog edits alone cannot admit a range")
            result = describe(family, args.version)
            result["publication"] = "existing-26.2-selection" if existing_singleton else "accepted-catalog-entry-with-receipt"
            if all(supplied):
                result["verification"] = verify_receipt(family, args.receipt, args.artifact, args.sha256)
        else:
            family = receipt_family(catalog, args.family, args.loader)
            result = verify_receipt(family, args.receipt, args.artifact, args.sha256)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (OSError, UnicodeError, ValueError, TypeError, KeyError, RecursionError) as failure:
        print("Minecraft artifact selection refused: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
