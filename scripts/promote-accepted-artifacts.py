#!/usr/bin/env python3
"""Promote original accepted CI bytes; no compiler, native runtime, or proof rewriting."""
import argparse
import hashlib
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def module(filename):
    spec = spec_from_file_location(filename.replace("-", "_"), ROOT / "scripts" / filename)
    loaded = module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded

artifacts = module("minecraft-artifacts.py")
require = artifacts.require
shape = artifacts.shape

def positive(value):
    require(type(value) is int and value > 0, "Expected positive external ID/attempt")

def reference(value, archive_ids):
    shape(value, {"archiveId", "path"}, "Original archive file reference")
    require(value["archiveId"] in archive_ids, "Unknown original archive ID")
    parts = value["path"].split("/")
    require(all(re.fullmatch(r"[A-Za-z0-9_.$-]+", part) and part not in (".", "..") for part in parts),
            "Unsafe original archive path")

def selection(path):
    require(path.is_file(), "Pending accepted original selection: " + str(path) + "; Root must supply exact original artifact identities and complete accepted runtime proof before publication")
    data = artifacts.read_json(path)
    shape(data, {"repository", "releaseVersion", "archives", "families"}, "Accepted original selection")
    require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", data["repository"]), "Invalid repository")
    build = module("build-minecraft-artifacts.py")
    require(data["releaseVersion"] == build.version(), "Release version differs; accepted bytes must not be relabeled")
    require(type(data["archives"]) is list and data["archives"], "Missing immutable archive identities")
    ids = set()
    for archive in data["archives"]:
        shape(archive, {"id", "name", "sha256", "runId", "runAttempt", "headSha", "workflowPath", "jobId", "jobName"}, "Original CI archive")
        for key in ("id", "runId", "runAttempt", "jobId"):
            positive(archive[key])
        require(archive["id"] not in ids, "Duplicate archive ID")
        ids.add(archive["id"])
        artifacts.hash_text(archive["sha256"])
        require(re.fullmatch(r"[0-9a-f]{40}", archive["headSha"]), "Invalid original run head SHA")
        for key in ("name", "workflowPath", "jobName"):
            artifacts.text(archive[key], key)
    catalog = build.catalog()
    families = {family["id"]: family for family in catalog["acceptedFamilies"]}
    require(type(data["families"]) is list and len(data["families"]) == len(families), "Select every accepted family exactly once")
    observed = set()
    for row in data["families"]:
        shape(row, {"familyId", "filename", "artifactSha256", "sourceSha", "production", "stageRecord", "engineEntries", "runs"}, "Accepted original family")
        require(row["familyId"] in families and row["familyId"] not in observed, "Unknown, duplicate, or candidate family")
        observed.add(row["familyId"])
        family = families[row["familyId"]]
        require(row["filename"] == artifacts.describe(family, data["releaseVersion"])["filename"], "Original filename must equal exact admitted family")
        require(re.fullmatch(r"[0-9a-f]{40}", row["sourceSha"]), "Missing original packaged source SHA")
        artifacts.hash_text(row["artifactSha256"])
        reference(row["production"], ids)
        require(Path(row["production"]["path"]).name == row["filename"], "Never rename original JAR")
        if row["stageRecord"] is not None:
            reference(row["stageRecord"], ids)
            require(row["stageRecord"]["archiveId"] == row["production"]["archiveId"], "Stage record must belong to original production archive")
        engines = row["engineEntries"]
        require(type(engines) is dict and "dev/openallay/guide/GuideService.class" in engines
                and "dev/openallay/FeatureServices.class" in engines, "Original package engine entry inventory missing")
        for name, digest in engines.items():
            reference({"archiveId": row["production"]["archiveId"], "path": name}, ids)
            artifacts.hash_text(digest)
        require(type(row["runs"]) is list and [run["target"] for run in row["runs"]] == family["supportedTargets"], "Exactly one original proof per admitted target, in target order")
        for run in row["runs"]:
            shape(run, {"target", "summary", "profile", "result", "sourceReceipt"}, "Original runtime selection")
            for key in ("summary", "profile"):
                reference(run[key], ids)
            for key in ("result", "sourceReceipt"):
                if run[key] is not None:
                    reference(run[key], ids)
            require((run["result"] is None) != (run["sourceReceipt"] is None), "Use actual interval result or existing native/mainline source receipt")
            require(all(run[key] is None or run[key]["archiveId"] == run["summary"]["archiveId"] for key in ("profile", "result", "sourceReceipt")), "Runtime proof files must share one original runtime archive")
    # Release admission stays in the source catalog, not an arbitrary selection label.
    require({(f["loader"], t) for f in families.values() for t in f["supportedTargets"]}
            == {(loader, t) for loader in artifacts.LOADERS for t in catalog["targetOrder"]},
            "Release catalog must admit all exact loader/target pairs")
    return data

def api(repository, endpoint):
    result = subprocess.run(["gh", "api", "repos/" + repository + "/" + endpoint], check=True, capture_output=True)
    return json.loads(result.stdout)

def archive_directory(cache, identity):
    return cache / str(identity) / "files"

def original_file(cache, ref):
    directory = archive_directory(cache, ref["archiveId"]).resolve(strict=True)
    path = directory / ref["path"]
    require(path.resolve(strict=True) == path and path.is_relative_to(directory), "Aliased original archive entry")
    # The immutable archive, not its mutable extraction, owns every selected byte.
    with zipfile.ZipFile(cache / str(ref["archiveId"]) / "original.zip") as archive:
        digest = hashlib.sha256()
        with archive.open(ref["path"]) as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
    require(artifacts.file_hash(path, 2 * 1024**3) == digest.hexdigest(), "Extracted original entry differs from immutable archive")
    return path

def verify_archive_identity(data, archive, metadata, run, job):
    require(metadata["id"] == archive["id"] and metadata["name"] == archive["name"] and metadata["expired"] is False,
            "Original immutable artifact missing, expired, or renamed")
    require(metadata["digest"] == "sha256:" + archive["sha256"], "Original artifact archive digest differs")
    require(metadata["workflow_run"]["id"] == archive["runId"] and metadata["workflow_run"]["head_sha"] == archive["headSha"], "Artifact belongs to a different original run/head")
    require(run["run_attempt"] == archive["runAttempt"], "Wrong original run attempt")
    require(job["started_at"] <= metadata["created_at"] <= job["completed_at"],
            "Artifact was not uploaded during its exact successful original producer job/attempt")
    # A failed feature batch can carry only the fixture's reviewed positive rows.
    # Preserve the real failure. Ordinary production/native archives require success.
    fixture = artifacts.read_json(ROOT / "scripts/fixtures/mainline-feature-acceptance.json")
    carried = [row for row in fixture["clients"] if row["artifactId"] == archive["id"]
               and row["jobConclusion"] == "failure"]
    if carried:
        require(len(carried) == 1, "Ambiguous reviewed feature carry")
        selected = carried[0]
        require(archive["name"] == selected["artifactName"] and "sha256:" + archive["sha256"] == selected["artifactDigest"]
                and archive["runId"] == selected["runId"] and archive["runAttempt"] == selected["attempt"]
                and archive["headSha"] == selected["runnerSha"] and archive["jobId"] == selected["jobId"]
                and archive["workflowPath"] == ".github/workflows/quality.yml"
                and archive["jobName"] == "Packaged client " + selected["loader"], "Failed archive is not exact reviewed feature carry")
        require(run["id"] == archive["runId"] and run["head_sha"] == archive["headSha"]
                and run["path"] == archive["workflowPath"] and run["repository"]["full_name"] == data["repository"]
                and run["status"] == "completed" and job["id"] == archive["jobId"] and job["run_id"] == archive["runId"]
                and job["run_attempt"] == archive["runAttempt"] and job["head_sha"] == archive["headSha"]
                and job["name"] == archive["jobName"] and job["status"] == "completed" and job["conclusion"] == "failure",
                "Original reviewed failed feature batch identity differs")
        return
    shared = module("plan-ci-verification.py")
    checked = shared.validate_source_job(run, [job], str(archive["runId"]), archive["headSha"], data["repository"], "unused",
                                         archive["headSha"], workflow_path=archive["workflowPath"], job_name=archive["jobName"])
    require(checked["id"] == archive["jobId"], "Wrong original successful source job")

def download(data, cache):
    require(not cache.exists(), "Preserve previously downloaded original evidence")
    cache.mkdir(parents=True)
    for archive in data["archives"]:
        directory = cache / str(archive["id"])
        directory.mkdir()
        metadata = api(data["repository"], "actions/artifacts/" + str(archive["id"]))
        run = api(data["repository"], "actions/runs/" + str(archive["runId"]) + "/attempts/" + str(archive["runAttempt"]))
        job = api(data["repository"], "actions/jobs/" + str(archive["jobId"]))
        verify_archive_identity(data, archive, metadata, run, job)
        write_json(directory / "identity.json", {"artifact": metadata, "run": run, "job": job})
        zipped = directory / "original.zip"
        with zipped.open("xb") as stream:
            subprocess.run(["gh", "api", "repos/" + data["repository"] + "/actions/artifacts/" + str(archive["id"]) + "/zip"], stdout=stream, check=True)
        require(artifacts.file_hash(zipped, 2 * 1024**3) == archive["sha256"], "Downloaded original archive SHA256 differs")
        files = archive_directory(cache, archive["id"])
        files.mkdir()
        with zipfile.ZipFile(zipped) as source:
            entries = source.infolist()
            require(len(entries) <= 20000 and sum(entry.file_size for entry in entries) <= 2 * 1024**3, "Original evidence archive exceeds bound")
            names = set()
            for entry in entries:
                name = entry.filename.rstrip("/")
                reference({"archiveId": archive["id"], "path": name}, {archive["id"]})
                require(name not in names and ((entry.external_attr >> 16) & 0o170000) != 0o120000, "Duplicate/symlink original archive entry")
                names.add(name)
                destination = files / name
                if entry.is_dir():
                    destination.mkdir(parents=True, exist_ok=True)
                else:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    with source.open(entry) as src, destination.open("xb") as dst:
                        shutil.copyfileobj(src, dst)

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(value, indent=2, sort_keys=True) + "\n")

def original_proofs(data, cache, row, family):
    archives = {a["id"]: a for a in data["archives"]}
    producer = archives[row["production"]["archiveId"]]
    identity = artifacts.read_json(cache / str(producer["id"]) / "identity.json")
    require(identity["job"]["conclusion"] == "success", "Original production job must succeed")
    jar = original_file(cache, row["production"])
    require(artifacts.file_hash(jar, artifacts.MAX_ARTIFACT_BYTES) == row["artifactSha256"], "Original JAR SHA256 differs")
    checksums = (jar.parent / "SHA256SUMS").read_text().splitlines()
    require(checksums.count(row["artifactSha256"] + "  " + jar.name) == 1, "Original staged checksum missing or ambiguous")
    if row["stageRecord"] is not None:
        stage = artifacts.read_json(original_file(cache, row["stageRecord"]))
        require(stage["sourceSha"] == row["sourceSha"] and str(stage["sourceRunId"]) == str(producer["runId"])
                and str(stage["sourceRunAttempt"]) == str(producer["runAttempt"]), "Original staged source/run/attempt differs")
        matches = [a for a in stage["artifacts"] if a["id"] == family["id"]]
        require(len(matches) == 1 and matches[0]["filename"] == jar.name and matches[0]["artifactSha256"] == row["artifactSha256"], "Original staged family identity differs")
    interval = module("verify-minecraft-binary-intervals.py")
    summaries = []
    for run in row["runs"]:
        summary_path = original_file(cache, run["summary"])
        summary = artifacts.read_json(summary_path)
        profile_path = original_file(cache, run["profile"])
        profile = artifacts.read_json(profile_path)
        pins = module("prepare-ci-minecraft-runtime.py").read_pins(ROOT, run["target"])
        require(profile["loader"] == family["loader"] and profile["minecraft"] == run["target"]
                and profile["mechanism"] == "official-client-installer", "Original actual loader/target profile differs")
        require(profile["pins"] == {key: pins[key] for key in ("minecraft_version", "java_version", "fabric_loader_version", "fabric_version", "neoforge_version")},
                "Original actual game/loader/Fabric API pins differ")
        require(profile["sourceProfile"] == "gradle/minecraft-targets/" + run["target"] + ".properties", "Original target source profile path differs")
        require(profile["sourceProfileSha256"] == artifacts.file_hash(ROOT / profile["sourceProfile"], artifacts.MAX_JSON_BYTES), "Original source target profile changed")
        require((family["loader"] == "fabric" and profile["fabricApi"] is not None) or (family["loader"] == "neoforge" and profile["fabricApi"] is None), "Original loader API identity differs")
        old_path = Path(summary["originalArtifact"]["path"])
        scenarios = ("builder-acceptance", "builder-reload") if run["target"] == "26.2" else ("builder-restricted",)
        original_scenarios = tuple(item["scenario"] for item in summary["scenarios"])
        require(all(name in original_scenarios for name in scenarios), "Required native boundary scenario missing")
        require(len(original_scenarios) == len(set(original_scenarios)), "Duplicate original scenario rows")
        interval.verify_summary(summary, family, run["target"], row["artifactSha256"], old_path, scenarios=original_scenarios)
        require(old_path.name == row["filename"], "Runtime used a different original filename")
        if run["result"] is not None:
            result = artifacts.read_json(original_file(cache, run["result"]))
            require(result["familyId"] == family["id"] and result["loader"] == family["loader"] and result["target"] == run["target"]
                    and result["sourceSha"] == row["sourceSha"] and str(result["sourceRunId"]) == str(producer["runId"])
                    and str(result["sourceRunAttempt"]) == str(producer["runAttempt"]) and result["artifactSha256"] == row["artifactSha256"]
                    and result["kind"] == "runtime" and result["outcome"] == "passed", "Original actual interval proof/source differs")
            require(result["runtimeSummary"] == summary and result["runtimeSummarySha256"] == artifacts.file_hash(summary_path, artifacts.MAX_JSON_BYTES)
                    and result["runtimeProfileSha256"] == artifacts.file_hash(profile_path, artifacts.MAX_JSON_BYTES), "Original runtime summary/profile digest differs")
        else:
            source = artifacts.read_json(original_file(cache, run["sourceReceipt"]))
            require(source["source_sha"] == row["sourceSha"] and str(source["source_run_id"]) == str(producer["runId"])
                    and source["source_run_head_sha"] == producer["headSha"] and source["source_job"]["id"] == producer["jobId"]
                    and source["source_job"]["run_attempt"] == producer["runAttempt"]
                    and source["jar_sha256"][jar.name] == row["artifactSha256"], "Original mainline/native source receipt differs")
        summaries.append((run, summary_path))
    return jar, summaries

def feature_composition(data, cache):
    """Reuse the checked-in nine-scenario composition; do not relabel prior bytes."""
    index = artifacts.read_json(ROOT / "scripts/fixtures/mainline-feature-acceptance.json")
    archives = {archive["id"]: archive for archive in data["archives"]}
    verifier = index["verifier"]
    require(index["version"] == data["releaseVersion"] and verifier["artifactId"] in archives,
            "Original mainline composition verifier missing")
    producer = archives[verifier["artifactId"]]
    require(producer["runId"] == verifier["runId"] and producer["headSha"] == verifier["sourceSha"]
            and producer["name"] == verifier["artifactName"] and "sha256:" + producer["sha256"] == verifier["artifactDigest"],
            "Mainline original verifier archive differs from reviewed composition")
    for row in data["families"]:
        if row["familyId"] in ("fabric-26.2", "neoforge-26.2"):
            require(row["sourceSha"] == verifier["sourceSha"] and row["production"]["archiveId"] == verifier["artifactId"]
                    and row["artifactSha256"] == verifier["jarSha256"][row["filename"]],
                    "Mainline publication must use reviewed original verifier bytes")
    # Only small authentic proof files are linked, never game or artifact copies.
    with tempfile.TemporaryDirectory(prefix="feature-composition-", dir=cache) as temporary:
        evidence = Path(temporary)
        identity = artifacts.read_json(cache / str(verifier["artifactId"]) / "identity.json")
        write_json(evidence / "verifier-run.json", identity["run"])
        write_json(evidence / "verifier-jobs.json", [identity["job"]])
        for number, selected in enumerate(index["clients"]):
            require(selected["artifactId"] in archives, "Reviewed original feature archive missing")
            archive = archives[selected["artifactId"]]
            require(archive["name"] == selected["artifactName"] and "sha256:" + archive["sha256"] == selected["artifactDigest"],
                    "Reviewed original feature archive differs")
            identity = artifacts.read_json(cache / str(selected["artifactId"]) / "identity.json")
            directory = evidence / str(number)
            write_json(directory / "run.json", identity["run"])
            write_json(directory / "jobs.json", [identity["job"]])
            for name in (selected["summaryPath"], "ci-verification/artifact-source.json",
                         "ci-verification/source-run.json", "ci-verification/source-jobs.json"):
                original = original_file(cache, {"archiveId": selected["artifactId"], "path": name})
                destination = directory / "artifact" / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.hardlink_to(original)
        interval = module("verify-minecraft-binary-intervals.py")
        # Publication preserves the exact verifier JAR; tag source is the release
        # decision source, not a claim that its changed production code was tested.
        return interval.feature_acceptance_guard(index, evidence, verifier["sourceSha"], data["repository"])

def records(selection_path, cache, directory, receipt_directory, stage=False):
    data = selection(selection_path)
    build = module("build-minecraft-artifacts.py")
    families = {f["id"]: f for f in build.catalog()["acceptedFamilies"]}
    # Recheck downloaded archive identities and bytes on each publication boundary.
    for archive in data["archives"]:
        identity = artifacts.read_json(cache / str(archive["id"]) / "identity.json")
        verify_archive_identity(data, archive, identity["artifact"], identity["run"], identity["job"])
        require(artifacts.file_hash(cache / str(archive["id"]) / "original.zip", 2 * 1024**3) == archive["sha256"], "Original archive changed")
    composition = feature_composition(data, cache)
    if stage:
        require(not directory.exists() and not receipt_directory.exists(), "Preserve existing release and final-path receipts")
        directory.mkdir(parents=True)
        receipt_directory.mkdir(parents=True)
    lock = build.builder.prepare.load_manifest(ROOT / "distribution/extensions.lock.json")
    result = []
    builder_digest = None
    rejected = []
    for row in data["families"]:
        try:
            family = families[row["familyId"]]
            original, proofs = original_proofs(data, cache, row, family)
            path = (directory / row["filename"]).resolve()
            if stage:
                shutil.copyfile(original, path)
            require(artifacts.file_hash(path, artifacts.MAX_ARTIFACT_BYTES) == row["artifactSha256"], "Final-path JAR differs from original accepted bytes")
            package_report = module("verify-minecraft-binary-intervals.py").package_guard(
                path, family, data["releaseVersion"], accepted_original_runtime_sha=row["artifactSha256"])
            # Original artifact entry inventory is backed by the successful producer job.
            # It is not current-source compiled-engine output or new independent proof.
            with zipfile.ZipFile(path) as archive:
                for name, digest in row["engineEntries"].items():
                    require(hashlib.sha256(archive.read(name)).hexdigest() == digest, "Original shared-engine entry changed: " + name)
            digest = build.builder.verify_package(path, family["loader"], lock)
            require(builder_digest is None or builder_digest == digest, "Families must bundle the same accepted universal Builder")
            builder_digest = digest
            build.builder_support(path, family, lock)
            build.tokenizer.verify(path, family["loader"])
            receipt_path = receipt_directory / (family["id"] + ".json")
            if stage:
                runs = []
                for run, summary_path in proofs:
                    evidence_name = family["id"] + "-" + run["target"] + ".original-summary.json"
                    destination = receipt_directory / evidence_name
                    require(not destination.exists(), "Preserve original runtime evidence")
                    shutil.copyfile(summary_path, destination)
                    runs.append({"target": run["target"], "loader": family["loader"], "artifactPath": str(path), "artifactSha256": row["artifactSha256"],
                                 "kind": "runtime", "outcome": "passed", "evidencePath": evidence_name,
                                 "evidenceSha256": artifacts.file_hash(destination, artifacts.MAX_EVIDENCE_BYTES)})
                write_json(receipt_path, {"familyId": family["id"], "loader": family["loader"], "artifactPath": str(path),
                                         "artifactSha256": row["artifactSha256"], "runs": runs})
            artifacts.verify_receipt(family, receipt_path, path, row["artifactSha256"])
            receipt = artifacts.read_json(receipt_path)
            for entry, (_, summary_path) in zip(receipt["runs"], proofs):
                require(entry["evidenceSha256"] == artifacts.file_hash(summary_path, artifacts.MAX_EVIDENCE_BYTES), "Final receipt differs from original accepted summary")
            result.append({**artifacts.describe(family, data["releaseVersion"]), "artifactPath": str(path), "artifactSha256": row["artifactSha256"],
                           "originalSourceSha": row["sourceSha"], "originalProductionArchive": row["production"], "originalRuns": row["runs"],
                           "nonfatalPackageFindings": package_report["findings"],
                           **({"mainlineFeatureComposition": composition} if family["supportedTargets"] == ["26.2"] else {})})
        except (OSError, ValueError, TypeError, KeyError, IndexError, zipfile.BadZipFile) as failure:
            rejected.append({"familyId": row["familyId"], "reason": str(failure)})
    require(not rejected, "Original publication family checks failed: " + json.dumps(rejected, sort_keys=True))
    require(sorted(p.name for p in directory.glob("*.jar")) == sorted(row["filename"] for row in data["families"]), "Extra or missing release JAR")
    sums = "".join(row["artifactSha256"] + "  " + row["filename"] + "\n" for row in result)
    if stage:
        (directory / "SHA256SUMS").write_text(sums)
        shutil.copyfile(selection_path, directory / "accepted-originals.json")
    require((directory / "SHA256SUMS").read_text() == sums, "Final checksums changed")
    require((directory / "accepted-originals.json").read_bytes() == selection_path.read_bytes(), "Final original provenance selection changed")
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("download", "stage", "verify"))
    parser.add_argument("--selection", type=Path, default=ROOT / "distribution/accepted-release-artifacts.json")
    parser.add_argument("--cache", type=Path, default=ROOT / "build/accepted-originals")
    parser.add_argument("--directory", type=Path, default=ROOT / "release")
    parser.add_argument("--receipt-directory", type=Path, default=ROOT / "build/release-receipts")
    args = parser.parse_args()
    try:
        data = selection(args.selection)
        if args.command == "download":
            download(data, args.cache)
        else:
            print(json.dumps(records(args.selection, args.cache.resolve(), args.directory.resolve(), args.receipt_directory.resolve(), stage=args.command == "stage"), indent=2, sort_keys=True))
    except (OSError, ValueError, TypeError, KeyError, IndexError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(2, "Original accepted-byte publication refused: " + str(error) + "\n")

if __name__ == "__main__":
    main()
