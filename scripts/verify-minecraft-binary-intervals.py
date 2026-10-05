#!/usr/bin/env python3
"""Build nonpublishing intervals once; run each exact official target with unchanged bytes.

Source/profile/metadata/package checks are rejection gates, never game proof.
Runtime summaries use the existing mechanical client gates and require visual review.
Catalog admission remains a separate reviewed acceptedFamilies edit.
"""
import argparse
from importlib.util import module_from_spec, spec_from_file_location
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ("builder-restricted", "builder-cancel", "ui-stop")


def module(filename, root=ROOT):
    spec = spec_from_file_location(filename.replace("-", "_"), root / "scripts" / filename)
    loaded = module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


artifacts = module("minecraft-artifacts.py")
require = artifacts.require


def catalog(root=ROOT):
    return artifacts.read_catalog(root / "gradle/minecraft-artifacts.json")


def candidates(data):
    return [artifacts.family_for(loader, row["buildTarget"], row["targets"])
            for row in data["candidateIntervals"] for loader in row["loaders"]]


def family_for_id(data, family_id, loader=None, target=None):
    family = artifacts.receipt_family(data, family_id, loader or family_id.split("-", 1)[0])
    require(loader is None or family["loader"] == loader, "Family loader differs")
    require(target is None or target in family["supportedTargets"], "Exact target is outside declared family")
    return family


def select(data, target, family_ids):
    ids = family_ids.split(",")
    require(ids and all(ids) and len(set(ids)) == len(ids), "Expected distinct candidate family IDs")
    families = [family for family in candidates(data) if family["id"] in ids]
    require(len(families) == len(ids), "Only reviewed nonpublishing candidates may use validation selection")
    require(len({family["loader"] for family in families}) == len(families), "One family per loader per build")
    require(all(family["buildTarget"] == target for family in families), "Candidate build target must stay exact")
    return families


def release_matrix(data):
    return {"runtime_matrix": {"include": [
        {"family": family["id"], "loader": family["loader"], "target": target}
        for family in data["acceptedFamilies"] for target in family["supportedTargets"]]}}


def final_stage_receipts(directory, results_directory, receipt_directory, root=ROOT):
    data = catalog(root)
    require(not receipt_directory.exists(), "Preserve prior final-stage receipts")
    version = module("verify-native-target-package.py", root).read_properties(root / "gradle.properties")["version"]
    stage = artifacts.read_json(directory / "build-record.json")
    require(stage["sourceSha"] == source_identity(root), "Final publication source differs from original staged bytes")
    results = []
    for family in data["acceptedFamilies"]:
        staged_artifact(directory, family, version, stage["sourceSha"])
        for target in family["supportedTargets"]:
            runtime = artifacts.read_json(results_directory / (family["id"] + "-" + target + ".json"))
            require(runtime["sourceSha"] == stage["sourceSha"]
                    and runtime["sourceRunId"] == stage["sourceRunId"]
                    and runtime["sourceRunAttempt"] == stage["sourceRunAttempt"],
                    "Final runtime evidence differs from original artifact source/run/attempt")
        # Final actual runtime runs also cover 26.2; every admitted interval is checked.
        results.append(aggregate_receipt(family["id"], directory / artifacts.describe(family, version)["filename"],
                                         results_directory, receipt_directory, root))
    return results


def matrices(data, selection="all"):
    available = candidates(data)
    ids = [family["id"] for family in available] if selection == "all" else selection.split(",")
    require(ids and all(ids) and len(set(ids)) == len(ids), "Expected distinct known candidate families")
    chosen = [family for family in available if family["id"] in ids]
    require(len(chosen) == len(ids), "Unknown candidate family; publication entries are not candidates")
    grouped = {}
    for family in chosen:
        grouped.setdefault(family["buildTarget"], []).append(family)
    builds = [{"target": target, "families": ",".join(family["id"] for family in families),
               "loaders": ",".join(family["loader"] for family in families)}
              for target, families in grouped.items()]
    runtimes = [{"build_target": family["buildTarget"], "family": family["id"], "loader": family["loader"],
                 "target": target} for family in chosen for target in family["supportedTargets"]]
    return {"build_matrix": {"include": builds}, "runtime_matrix": {"include": runtimes}}


def package_guard(path, family, version, root=ROOT):
    """Exact family predicates plus real loader pins; never rewrite packaged metadata."""
    build = module("build-minecraft-artifacts.py", root)
    build.metadata(path, family, version)
    target_reader = module("minecraft-target.py", root)
    profile = target_reader.read_profile(root, family["buildTarget"])
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if family["loader"] == "fabric":
            metadata = json.loads(archive.read("fabric.mod.json"))
            require(metadata["depends"]["fabricloader"] == ">=" + profile["fabric_loader_version"],
                    "Candidate Fabric loader dependency differs from the real build profile")
            require(metadata["depends"]["java"] == ">=" + profile["java_version"], "Candidate Java metadata differs")
        else:
            descriptor = "META-INF/mods.toml" if family["buildTarget"] in ("1.20.1", "1.20.2", "1.20.3", "1.20.4") else "META-INF/neoforge.mods.toml"
            metadata = archive.read(descriptor).decode()
            blocks = metadata.split("[[dependencies.openallay]]")[1:]
            loader = "forge" if family["buildTarget"] == "1.20.1" else "neoforge"
            rows = [dict(re.findall(r'(?m)^\s*(modId|versionRange)\s*=\s*"([^"\n]+)"', block.split("[[", 1)[0])) for block in blocks]
            loader_rows = [row for row in rows if row.get("modId") == loader]
            require(len(loader_rows) == 1 and loader_rows[0]["versionRange"] == ("[47.1.0,)" if family["buildTarget"] == "1.20.1" else "[" + profile["neoforge_version"] + ",)"),
                    "Candidate NeoForge dependency differs from the real build profile")
            loader_fields = re.findall(r'(?m)^\s*loaderVersion\s*=\s*"([^"\n]+)"', metadata)
            require(loader_fields == [profile["neoforge_loader_version_range"]], "Candidate FML loader range differs")
        # Parse every real Mixin binding and require its packaged class and referenced refmap.
        for name in names:
            if name.endswith(".mixins.json"):
                config = json.loads(archive.read(name))
                for scope in ("mixins", "client", "server"):
                    for binding in config.get(scope, []):
                        owner = (config["package"] + "." + binding).replace(".", "/") + ".class"
                        require(owner in names, "Configured native Mixin class missing: " + owner)
                if "refmap" in config:
                    require(config["refmap"] in names, "Configured native Mixin refmap missing")
                    require(type(json.loads(archive.read(config["refmap"]))) is dict, "Invalid native refmap")
    return {"packageRejectionGates": "passed", "runtimeAcceptance": "not-established"}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def source_identity(root=ROOT):
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    require(re.fullmatch(r"[0-9a-f]{40}", sha) is not None, "Exact Git source SHA required")
    require(os.environ.get("GITHUB_SHA", sha) == sha, "Build/run source differs from exact workflow source")
    return sha


def build_stage(target, family_ids, directory, root=ROOT):
    data = catalog(root)
    families = select(data, target, family_ids)
    require(not directory.exists(), "Refusing to overwrite candidate stage")
    native_compile = module("compile-native-target.py", root)
    native_compile.compile_target(root, target, loaders=tuple(family["loader"] for family in families), candidate_ids=family_ids)
    build = module("build-minecraft-artifacts.py", root)
    # Reuse bundled Builder/tokenizer/SQLite/shared-engine verification, but not publication admission.
    records = build.verify(families)
    profile = module("minecraft-target.py", root).read_profile(root, target)
    native = module("verify-native-target-package.py", root)
    engine = native.engine_files(root)
    for record, family in zip(records, families):
        native.verify(Path(record["artifactPath"]), family["loader"], target, int(profile["java_version"]),
                      engine, bundled_builder=True, family=family)
    directory.mkdir(parents=True)
    for record in records:
        original = Path(record["artifactPath"])
        destination = directory / record["filename"]
        shutil.copyfile(original, destination)
        require(artifacts.file_hash(destination, artifacts.MAX_ARTIFACT_BYTES) == record["artifactSha256"], "Candidate stage bytes changed")
    (directory / "SHA256SUMS").write_text("".join(record["artifactSha256"] + "  " + record["filename"] + "\n" for record in records))
    result = {"sourceSha": source_identity(root), "buildTarget": target, "publishing": False,
              "sourceRunId": os.environ.get("GITHUB_RUN_ID"), "sourceRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
              "sourceJob": os.environ.get("GITHUB_JOB"), "artifacts": records,
              "acceptanceBoundary": "Package gates only; same-byte runtime proof pending"}
    write_json(directory / "build-record.json", result)
    return result


def staged_artifact(directory, family, version, source_sha):
    record = artifacts.read_json(directory / "build-record.json")
    require(record["sourceSha"] == source_sha and record["publishing"] is False
            and record["buildTarget"] in (None, family["buildTarget"]), "Candidate staged source or build target differs")
    matches = [row for row in record["artifacts"] if row["id"] == family["id"]]
    require(len(matches) == 1, "Missing exact candidate production artifact")
    row = matches[0]
    described = artifacts.describe(family, version)
    require(all(row[key] == value for key, value in described.items()), "Staged family metadata differs")
    path = (directory / described["filename"]).resolve(strict=True)
    require(path.parent == directory.resolve() and not (directory / described["filename"]).is_symlink(), "Aliased candidate stage")
    expected = artifacts.hash_text(row["artifactSha256"])
    lines = (directory / "SHA256SUMS").read_text().splitlines()
    pairs = [line.split("  ", 1) for line in lines]
    require(all(len(pair) == 2 and Path(pair[1]).name == pair[1] for pair in pairs)
            and len({pair[1] for pair in pairs}) == len(pairs), "Invalid staged checksums")
    require(dict((name, sha) for sha, name in pairs).get(path.name) == expected, "Candidate stage checksum record differs")
    require(artifacts.file_hash(path, artifacts.MAX_ARTIFACT_BYTES) == expected, "Original candidate JAR changed")
    return path, expected, record


def verify_summary(summary, family, target, sha, jar, scenarios=SCENARIOS):
    require(summary["loader"] == family["loader"] and summary["minecraft"] == target
            and summary["artifactSha256"] == sha and summary["status"] == "PASSED"
            and summary["noPaidModel"] is True, "Actual exact-target key client batch did not pass")
    require(not summary["failures"], "Actual client batch retains failures")
    rows = summary["scenarios"]
    require([row["scenario"] for row in rows] == list(scenarios), "Every key scenario must run in order")
    for row in rows:
        require(row["status"] == "PASSED" and not row["failures"], "Key scenario failed or was skipped")
        require(row["artifactAfter"]["sha256"] == sha, "Key scenario changed production bytes")
    for key in ("originalArtifact", "finalArtifact"):
        require(summary[key]["sha256"] == sha and summary[key]["path"] == str(jar), "Client summary artifact source differs")


def run_runtime(directory, family_id, loader, target, java, batch_id, root=ROOT):
    family = family_for_id(catalog(root), family_id, loader, target)
    version = module("verify-native-target-package.py", root).read_properties(root / "gradle.properties")["version"]
    jar, sha, build_record = staged_artifact(directory, family, version, source_identity(root))
    package_guard(jar, family, version, root)
    if os.environ.get("GITHUB_RUN_ID"):
        require(build_record["sourceRunId"] == os.environ["GITHUB_RUN_ID"], "Final artifact belongs to a different build run")
        require(build_record["sourceRunAttempt"] == os.environ.get("GITHUB_RUN_ATTEMPT"), "Final artifact belongs to a different build attempt")
    runtime = root / "build/e2e/runtime" / target / "minecraft"
    # The existing launcher checks official runtime files/profile hashes, game identity, Java,
    # the exact target Fabric API, and pinned loader coordinates before native launch.
    receipt_path = runtime / ".provision" / (loader + "-runtime.json")
    receipt = artifacts.read_json(receipt_path)
    require(receipt["loader"] == loader and receipt["minecraft"] == target
            and receipt["minecraftRoot"] == str(runtime.resolve()), "Official runtime identity differs")
    command = [sys.executable, "-B", str(root / "scripts/run-ci-client-acceptance.py"), loader,
               "--artifact-family", family_id, "--minecraft-target", target, "--batch-id", batch_id,
               "--jar", str(jar), "--artifact-sha256", sha, "--java", str(java.resolve()),
               "--minecraft-root", str(runtime.resolve()),
               "--assets-root", str((root / "build/e2e/runtime" / target / "assets").resolve()),
               "--scenarios", *SCENARIOS]
    if loader == "fabric":
        command += ["--fabric-api", receipt["fabricApi"]]
    environment = dict(os.environ, OPENALLAY_E2E_SOURCE_REVISION=build_record["sourceSha"])
    result = subprocess.run(command, cwd=root, env=environment, check=False)
    # Always check immutable bytes, including failed native startup/report scenarios.
    require(artifacts.file_hash(jar, artifacts.MAX_ARTIFACT_BYTES) == sha, "Original interval JAR changed during runtime")
    require(result.returncode == 0, "Actual candidate client batch failed; retain native diagnostics")
    summary_path = root / "build/e2e/ci-client" / loader / batch_id / "summary.json"
    summary = artifacts.read_json(summary_path)
    verify_summary(summary, family, target, sha, jar)
    result = {"familyId": family_id, "loader": loader, "target": target, "artifactSha256": sha,
              "sourceSha": build_record["sourceSha"], "sourceRunId": build_record["sourceRunId"],
              "sourceRunAttempt": build_record["sourceRunAttempt"], "buildTarget": family["buildTarget"],
              "runtimeProfileSha256": artifacts.file_hash(receipt_path, artifacts.MAX_JSON_BYTES),
              "runtimeSummarySha256": artifacts.file_hash(summary_path, artifacts.MAX_JSON_BYTES),
              "runtimeSummary": summary,
              "kind": "runtime", "outcome": "passed", "publishing": False,
              "runtimeAcceptance": "mechanical-key-scenarios-passed; visual-review-required"}
    write_json(root / "build/ci-binary-interval" / (family_id + "-" + target + ".json"), result)
    return result


def bind_stage(directory, family_ids, root=ROOT):
    """Bind exact final release files before running them; never rebuild or overwrite records."""
    ids = [family["id"] for family in catalog(root)["acceptedFamilies"]] if family_ids == "accepted" else family_ids.split(",")
    require(ids and all(ids) and len(set(ids)) == len(ids), "Expected distinct exact family IDs")
    families = [family_for_id(catalog(root), identity) for identity in ids]
    require(not (directory / "build-record.json").exists(), "Preserve previous final artifact provenance")
    version = module("verify-native-target-package.py", root).read_properties(root / "gradle.properties")["version"]
    records = []
    for family in families:
        described = artifacts.describe(family, version)
        jar = (directory / described["filename"]).resolve(strict=True)
        require(jar.parent == directory.resolve(), "Final artifact must stay inside exact stage")
        package_guard(jar, family, version, root)
        records.append({**described, "artifactPath": str(jar),
                        "artifactSha256": artifacts.file_hash(jar, artifacts.MAX_ARTIFACT_BYTES)})
    # Existing SHA256SUMS must bind the exact final files; the script never repairs it.
    result = {"sourceSha": source_identity(root), "buildTarget": families[0]["buildTarget"] if len({f["buildTarget"] for f in families}) == 1 else None, "publishing": False,
              "sourceRunId": os.environ.get("GITHUB_RUN_ID"), "sourceRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
              "sourceJob": os.environ.get("GITHUB_JOB"), "artifacts": records,
              "acceptanceBoundary": "Final staged bytes, runtime proof pending"}
    write_json(directory / "build-record.json", result)
    for family in families:
        staged_artifact(directory, family, version, result["sourceSha"])
    return result


def aggregate_receipt(family_id, artifact, results_directory, receipt_directory, root=ROOT):
    """Check each actual mechanical result and emit the already-existing integrity receipt."""
    family = family_for_id(catalog(root), family_id)
    artifact = artifacts.absolute_file(str(artifact.resolve(strict=True)))
    sha = artifacts.file_hash(artifact, artifacts.MAX_ARTIFACT_BYTES)
    version = module("verify-native-target-package.py", root).read_properties(root / "gradle.properties")["version"]
    package_guard(artifact, family, version, root)
    require(not (receipt_directory / (family_id + ".json")).exists(), "Preserve previous acceptance receipt")
    observed_source = None
    runs = []
    for target in family["supportedTargets"]:
        result_path = results_directory / (family_id + "-" + target + ".json")
        result = artifacts.read_json(result_path)
        require(result["familyId"] == family_id and result["loader"] == family["loader"]
                and result["target"] == target and result["artifactSha256"] == sha
                and result["kind"] == "runtime" and result["outcome"] == "passed",
                "Missing, failed, or different-byte exact-target runtime evidence")
        require(re.fullmatch(r"[0-9a-f]{40}", result["sourceSha"]) is not None, "Missing exact binary source SHA")
        observed_source = observed_source or result["sourceSha"]
        require(result["sourceSha"] == observed_source, "Interval targets differ in binary source")
        summary = result["runtimeSummary"]
        verify_summary(summary, family, target, sha, Path(summary["originalArtifact"]["path"]))
        evidence_name = family_id + "-" + target + ".runtime.json"
        evidence_path = receipt_directory / evidence_name
        require(not evidence_path.exists(), "Preserve original target runtime evidence")
        write_json(evidence_path, result)
        runs.append({"target": target, "loader": family["loader"], "artifactPath": str(artifact),
                     "artifactSha256": sha, "kind": "runtime", "outcome": "passed",
                     "evidencePath": evidence_name,
                     "evidenceSha256": artifacts.file_hash(evidence_path, artifacts.MAX_EVIDENCE_BYTES)})
    receipt = {"familyId": family_id, "loader": family["loader"], "artifactPath": str(artifact),
               "artifactSha256": sha, "runs": runs}
    path = receipt_directory / (family_id + ".json")
    write_json(path, receipt)
    return artifacts.verify_receipt(family, path, artifact, sha)


def feature_acceptance_guard(index, evidence, source_sha, repository, root=ROOT):
    """Compose only pinned positive rows; retain the failed original batch as-is."""
    shared = module("plan-ci-verification.py", root)
    quality = module("plan-quality-verification.py", root)
    verifier = index["verifier"]
    run = artifacts.read_json(evidence / "verifier-run.json")
    jobs = artifacts.read_json(evidence / "verifier-jobs.json")
    job = quality.validate_quality_source(run, jobs, str(verifier["runId"]), verifier["sourceSha"], repository)
    require(type(verifier["artifactId"]) is int and verifier["artifactId"] > 0
            and verifier["artifactName"] == "client-production-" + verifier["sourceSha"]
            and re.fullmatch(r"sha256:[0-9a-f]{64}", verifier["artifactDigest"]) is not None,
            "Missing original verifier production artifact identity")
    require(job == verifier["job"], "Verifier job/attempt differs from reviewed source")
    closure = shared.validate_mainline_closure(root, verifier["sourceSha"], source_sha)
    required = ("builder-restricted", "builder-acceptance", "builder-partial", "builder-cancel", "builder-reload",
                "ui-stop", "ui-provider-failure", "ui-manual-regressions", "ui-live-ux-regressions")
    covered = {loader: {} for loader in artifacts.LOADERS}
    retained_batches = []
    for number, selected in enumerate(index["clients"]):
        loader, runner_sha = selected["loader"], selected["runnerSha"]
        require(loader in covered, "Unknown feature loader")
        require(type(selected["jobId"]) is int and selected["jobId"] > 0
                and type(selected["runId"]) is int and selected["runId"] > 0
                and type(selected["artifactId"]) is int and selected["artifactId"] > 0
                and re.fullmatch(r"sha256:[0-9a-f]{64}", selected["artifactDigest"]) is not None
                and selected["artifactName"] == "client-" + loader + "-" + runner_sha
                and not Path(selected["summaryPath"]).is_absolute()
                and ".." not in Path(selected["summaryPath"]).parts, "Invalid original feature artifact identity/path")
        directory = evidence / str(number)
        client_run = artifacts.read_json(directory / "run.json")
        client_jobs = artifacts.read_json(directory / "jobs.json")
        matches = [row for row in client_jobs if row.get("name") == "Packaged client " + loader]
        require(len(matches) == 1, "Expected one exact feature client job")
        client_job = matches[0]
        require(client_run.get("status") == "completed"
                and client_run.get("id") == selected["runId"] and client_run.get("head_sha") == runner_sha
                and client_run.get("path") == ".github/workflows/quality.yml"
                and client_run.get("repository", {}).get("full_name") == repository
                and client_run.get("run_attempt") == selected["attempt"]
                and type(selected["attempt"]) is int and selected["attempt"] > 0
                and client_job.get("id") == selected["jobId"]
                and client_job.get("run_id") == selected["runId"]
                and client_job.get("head_sha") == runner_sha
                and client_job.get("run_attempt") == selected["attempt"]
                and client_job.get("status") == "completed"
                and client_job.get("conclusion") == selected["jobConclusion"]
                and selected["jobConclusion"] in ("success", "failure")
                and client_job.get("html_url") == "https://github.com/" + repository + "/actions/runs/"
                    + str(selected["runId"]) + "/job/" + str(selected["jobId"]),
                "Client job run/head/attempt/outcome differs from immutable selection")
        summary_path = directory / "artifact" / selected["summaryPath"]
        receipt_path = directory / "artifact" / "ci-verification/artifact-source.json"
        require(artifacts.file_hash(summary_path, artifacts.MAX_JSON_BYTES) == selected["summarySha256"]
                and artifacts.file_hash(receipt_path, artifacts.MAX_JSON_BYTES) == selected["receiptSha256"],
                "Original feature summary or source receipt bytes changed")
        summary, receipt = artifacts.read_json(summary_path), artifacts.read_json(receipt_path)
        original_job = quality.validate_quality_source(
            artifacts.read_json(directory / "artifact/ci-verification/source-run.json"),
            artifacts.read_json(directory / "artifact/ci-verification/source-jobs.json"),
            str(selected["sourceRunId"]), selected["sourceSha"], repository, receipt["source_run_head_sha"])
        require(original_job == selected["sourceJob"], "Client original verifier identity changed")
        require(receipt["candidate_sha"] == runner_sha and receipt["candidate_run_id"] == str(selected["runId"])
                and receipt["loader"] == loader and receipt["source_sha"] == selected["sourceSha"]
                and receipt["source_run_id"] == str(selected["sourceRunId"])
                and receipt["source_job"] == selected["sourceJob"]
                and receipt["jar_sha256"] == selected["jarSha256"], "Original client artifact source differs")
        jar_name = "openallay-" + loader + "-26.2-" + index["version"] + ".jar"
        sha = receipt["jar_sha256"][jar_name]
        require(summary["loader"] == loader and summary["minecraft"] == "26.2"
                and summary["artifactSha256"] == sha and summary["noPaidModel"] is True,
                "Feature summary target/original artifact differs")
        for key in ("originalArtifact", "finalArtifact"):
            require(summary[key]["sha256"] == sha and Path(summary[key]["path"]).name == jar_name,
                    "Feature batch changed original production bytes")
        if selected["sourceSha"] == verifier["sourceSha"]:
            require(selected["sourceRunId"] == verifier["runId"] and selected["sourceJob"] == job
                    and selected["jarSha256"] == verifier["jarSha256"]
                    and selected["jobConclusion"] == "success" and summary["status"] == "PASSED"
                    and not summary["failures"], "Latest Builder evidence is not original verified-byte success")
        else:
            require(tuple(selected["scenarios"]) == ("builder-restricted", "builder-partial", "builder-cancel",
                    "ui-stop", "ui-provider-failure", "ui-manual-regressions", "ui-live-ux-regressions"),
                    "Prior-byte carry is limited to the seven reviewed unaffected scenarios")
            shared.validate_mainline_closure(root, selected["sourceSha"], verifier["sourceSha"], fixture_bridge=True)
        shared.validate_mainline_closure(root, selected["sourceSha"], runner_sha)
        for scenario in selected["scenarios"]:
            rows = [row for row in summary["scenarios"] if row["scenario"] == scenario]
            require(len(rows) == 1 and scenario in required and scenario not in covered[loader],
                    "Missing, duplicate or unknown feature scenario")
            row = rows[0]
            require(row["status"] == "PASSED" and not row["failures"]
                    and row["artifactBefore"]["sha256"] == sha and row["artifactAfter"]["sha256"] == sha,
                    "Selected feature scenario failed, skipped or changed bytes")
            covered[loader][scenario] = {"runId": selected["runId"], "attempt": selected["attempt"],
                    "jobId": selected["jobId"], "runnerSha": runner_sha, "artifactSourceSha": selected["sourceSha"],
                    "artifactSha256": sha, "artifactId": selected["artifactId"],
                    "artifactDigest": selected["artifactDigest"], "summarySha256": selected["summarySha256"],
                    "acceptance": "actual-original-byte-pass" if selected["sourceSha"] == verifier["sourceSha"]
                                  else "reviewed-fixture-only-behavior-regression-carry"}
        retained_batches.append({"runId": selected["runId"], "loader": loader, "status": summary["status"],
                                 "failures": summary["failures"], "jobConclusion": selected["jobConclusion"]})
    require(all(set(rows) == set(required) for rows in covered.values()), "Incomplete mainline feature acceptance")
    return {"candidateSha": source_sha, "verifier": verifier, "productionClosure": closure,
            "featureAcceptance": "complete-reviewed-mainline-evidence-composition", "scenarios": covered,
            "originalBatchOutcomes": retained_batches,
            "candidateBinaryAcceptance": "not-established; unchanged interval/final-stage runtime gates required"}


def download_feature_evidence(index, evidence, repository):
    """Download original artifact archives by ID and digest, never relabel bytes."""
    shared = module("plan-ci-verification.py")
    require(not evidence.exists(), "Preserve previous feature evidence")
    evidence.mkdir(parents=True)
    def api(path):
        return json.loads(shared.command("gh", "api", "repos/" + repository + "/actions/" + path))
    verifier = index["verifier"]
    metadata = api("artifacts/" + str(verifier["artifactId"]))
    require(metadata["id"] == verifier["artifactId"] and metadata["name"] == verifier["artifactName"]
            and metadata["digest"] == verifier["artifactDigest"]
            and metadata["workflow_run"]["id"] == verifier["runId"]
            and metadata["workflow_run"]["head_sha"] == verifier["sourceSha"], "Verifier production artifact API identity differs")
    write_json(evidence / "verifier-artifact-api.json", metadata)
    write_json(evidence / "verifier-run.json", api("runs/" + str(verifier["runId"])))
    write_json(evidence / "verifier-jobs.json", api("runs/" + str(verifier["runId"]) + "/attempts/"
               + str(verifier["job"]["run_attempt"]) + "/jobs?per_page=100")["jobs"])
    for number, selected in enumerate(index["clients"]):
        directory = evidence / str(number)
        directory.mkdir()
        write_json(directory / "run.json", api("runs/" + str(selected["runId"])))
        write_json(directory / "jobs.json", api("runs/" + str(selected["runId"]) + "/attempts/"
                   + str(selected["attempt"]) + "/jobs?per_page=100")["jobs"])
        metadata = api("artifacts/" + str(selected["artifactId"]))
        require(metadata["id"] == selected["artifactId"] and metadata["name"] == selected["artifactName"]
                and metadata["digest"] == selected["artifactDigest"] and not metadata["expired"]
                and metadata["workflow_run"]["id"] == selected["runId"]
                and metadata["workflow_run"]["head_sha"] == selected["runnerSha"], "Feature artifact API identity differs")
        archive = directory / "original.zip"
        with archive.open("wb") as stream:
            subprocess.run(["gh", "api", "repos/" + repository + "/actions/artifacts/"
                            + str(selected["artifactId"]) + "/zip"], stdout=stream, check=True)
        require(artifacts.file_hash(archive, artifacts.MAX_ARTIFACT_BYTES) == selected["artifactDigest"].removeprefix("sha256:"),
                "Feature archive bytes differ from original Actions digest")
        with zipfile.ZipFile(archive) as zipped:
            require(all(not Path(name).is_absolute() and ".." not in Path(name).parts for name in zipped.namelist()),
                    "Aliased feature archive entry")
            zipped.extractall(directory / "artifact")
        write_json(directory / "artifact-api.json", metadata)


def feature_run_guard(run, source_sha, repository):
    require(type(run) is dict and run.get("head_sha") == source_sha
            and run.get("status") == "completed" and run.get("conclusion") == "success"
            and run.get("path") == ".github/workflows/quality.yml"
            and run.get("repository", {}).get("full_name") == repository,
            "Successful exact-source mainline Quality is required before candidate native builds")
    return {"sourceSha": source_sha, "featureRunId": run["id"], "featureAcceptance": "mainline-quality-passed"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    plan = commands.add_parser("matrix")
    plan.add_argument("--families", default="all")
    plan.add_argument("--github-output", type=Path)
    plan.add_argument("--feature-run-json", type=Path)
    plan.add_argument("--feature-acceptance-json", type=Path)
    plan.add_argument("--feature-evidence-directory", type=Path)
    plan.add_argument("--repository")
    release_plan = commands.add_parser("release-matrix")
    release_plan.add_argument("--github-output", type=Path)
    final_receipts = commands.add_parser("final-stage-receipts")
    final_receipts.add_argument("--directory", type=Path, required=True)
    final_receipts.add_argument("--results-directory", type=Path, required=True)
    final_receipts.add_argument("--receipt-directory", type=Path, required=True)
    selection = commands.add_parser("select")
    selection.add_argument("--target", required=True)
    selection.add_argument("--families", required=True)
    selection.add_argument("--version", required=True)
    build = commands.add_parser("build-and-stage")
    build.add_argument("--target", required=True)
    build.add_argument("--families", required=True)
    build.add_argument("--directory", type=Path, required=True)
    bind = commands.add_parser("bind-stage", help="Bind already-built final-stage families without rebuilding")
    bind.add_argument("--families", required=True)
    bind.add_argument("--directory", type=Path, required=True)
    receipt = commands.add_parser("receipt", help="Existing receipt consistency only; external review still required")
    receipt.add_argument("--family", required=True)
    receipt.add_argument("--artifact", type=Path, required=True)
    receipt.add_argument("--results-directory", type=Path, required=True)
    receipt.add_argument("--receipt-directory", type=Path, required=True)
    runtime = commands.add_parser("run-runtime")
    runtime.add_argument("--family", required=True)
    runtime.add_argument("--loader", choices=artifacts.LOADERS, required=True)
    runtime.add_argument("--target", required=True)
    runtime.add_argument("--directory", type=Path, required=True)
    runtime.add_argument("--java", type=Path, required=True)
    runtime.add_argument("--batch-id", required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "matrix":
            result = matrices(catalog(), args.families)
            require(bool(args.feature_run_json) != bool(args.feature_acceptance_json), "Exactly one feature evidence route required")
            if args.feature_acceptance_json:
                require(args.repository and args.feature_evidence_directory, "Feature composition requires repository and evidence directory")
                index = artifacts.read_json(args.feature_acceptance_json)
                download_feature_evidence(index, args.feature_evidence_directory, args.repository)
                result["featureEvidence"] = feature_acceptance_guard(index, args.feature_evidence_directory, source_identity(), args.repository)
            else:
                result["featureEvidence"] = feature_run_guard(artifacts.read_json(args.feature_run_json), source_identity(), args.repository)
            if args.github_output:
                with args.github_output.open("a") as stream:
                    for key, value in result.items():
                        stream.write(key + "=" + json.dumps(value, separators=(",", ":")) + "\n")
        elif args.command == "release-matrix":
            result = release_matrix(catalog())
            if args.github_output:
                with args.github_output.open("a") as stream:
                    stream.write("runtime_matrix=" + json.dumps(result["runtime_matrix"], separators=(",", ":")) + "\n")
        elif args.command == "final-stage-receipts":
            result = final_stage_receipts(args.directory.resolve(), args.results_directory.resolve(), args.receipt_directory.resolve())
        elif args.command == "select":
            result = {family["loader"]: artifacts.describe(family, args.version)
                      for family in select(catalog(), args.target, args.families)}
        elif args.command == "build-and-stage":
            result = build_stage(args.target, args.families, args.directory.resolve())
        elif args.command == "bind-stage":
            result = bind_stage(args.directory.resolve(), args.families)
        elif args.command == "receipt":
            result = aggregate_receipt(args.family, args.artifact, args.results_directory.resolve(), args.receipt_directory.resolve())
        else:
            result = run_runtime(args.directory.resolve(), args.family, args.loader, args.target, args.java, args.batch_id)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, subprocess.CalledProcessError) as failure:
        print("Candidate binary interval verification refused: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
