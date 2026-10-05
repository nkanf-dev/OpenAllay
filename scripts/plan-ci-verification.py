#!/usr/bin/env python3
"""Select native CI jobs and validate exact production artifacts for key smokes.

This planner does not choose repair impact for the operator. A dispatch lists the
failed/affected jobs; source reuse is allowed only for unchanged package inputs.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
NATIVE_TARGETS = ("26.3", "26.1", "1.21.11", "1.21.10", "1.21.8", "1.21.6", "1.21.5",
                  "1.21.4", "1.21.3", "1.21.1", "1.20.6", "1.20.4", "1.20.3", "1.20.2", "1.20.1")
DEFAULT_SMOKES = tuple((target, loader) for target in ("26.3", "1.21.1", "1.20.1")
                       for loader in ("fabric", "neoforge"))
MODULES = "common|fabric|neoforge|engine-core|extension-api|runtime-json|runtime-maven|runtime-rhino|adapters/[^/]+"
# Fail closed for other paths, including build helpers, all production sources,
# Gradle, native overrides, the Builder lock, and newly introduced build inputs.
NON_PACKAGE_FILES = {
    "README.md", "README.zh-CN.md", "AGENTS.md", ".gitignore",
    "extension-api/README.md", "adapters/minecraft/README.md",
    "scripts/fixtures/minecraft-launch/1.20.1.json",
    "scripts/fixtures/minecraft-launch/1.21.1.json",
    "scripts/fixtures/minecraft-launch/README.md",
}
NON_PACKAGE_RUNNERS = {
    "scripts/fixtures/mainline-feature-acceptance.json", "scripts/verify-minecraft-binary-intervals.py",
    "scripts/plan-ci-verification.py", "scripts/plan-quality-verification.py", "scripts/prepare-ci-diagnostics.py",
    "scripts/prepare-ci-minecraft-runtime.py", "scripts/run-ci-client-acceptance.py",
    "scripts/run-ci-game-workflow.py", "scripts/run-packaged-builder-acceptance.py",
    "scripts/run-ci-software-graphics.py",
    "scripts/compact-ci-game-evidence.py", "scripts/e2e-builder-fixture.js", "scripts/e2e-model-fixture.py",
    "scripts/run-real-client-e2e.sh", "scripts/validate-builder-live-acceptance.py",
}


def command(*args):
    result = subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return result.stdout


def git(root, *args):
    return command("git", "-C", str(root), *args)


def commit_sha(root, value):
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise ValueError("source/candidate SHA must be a full lowercase Git commit SHA")
    try:
        resolved = git(root, "rev-parse", "--verify", value + "^{commit}").strip()
    except subprocess.CalledProcessError as exc:
        raise ValueError("source/candidate commit is not available in this checkout: " + value) from exc
    if resolved != value:
        raise ValueError("source/candidate SHA does not identify its exact commit")
    return resolved


def selected_tokens(value, label):
    tokens = [item.strip() for item in value.split(",")]
    if not tokens or any(not item for item in tokens):
        raise ValueError(label + " must not contain empty entries; use none")
    if len(tokens) != len(set(tokens)):
        raise ValueError(label + " must not contain duplicates")
    return tokens


def check_profile(root, target):
    if target not in NATIVE_TARGETS:
        raise ValueError("unknown native representative/profile: " + target)
    profile = root / "gradle/minecraft-targets" / (target + ".properties")
    if not profile.is_file() or not re.search(r"^minecraft_version=" + re.escape(target) + r"$",
                                             profile.read_text(encoding="utf-8"), re.MULTILINE):
        raise ValueError("missing or mismatched target profile: " + target)


def select_native(root, value):
    targets = list(NATIVE_TARGETS) if value == "all" else [] if value == "none" else selected_tokens(value, "native_targets")
    for target in targets:
        check_profile(root, target)
    return targets


def select_smoke(root, value):
    pairs = list(DEFAULT_SMOKES) if value == "default" else []
    if value not in ("default", "none"):
        for token in selected_tokens(value, "smoke_targets"):
            parts = token.split("/")
            if len(parts) != 2 or parts[1] not in ("fabric", "neoforge"):
                raise ValueError("smoke_targets require exact target/fabric or target/neoforge pairs")
            pairs.append(tuple(parts))
    for target, _ in pairs:
        check_profile(root, target)
    return pairs


def package_input_changed(path, target):
    if path in NON_PACKAGE_FILES or path in NON_PACKAGE_RUNNERS or path.startswith(("docs/", ".github/")):
        return False
    if re.fullmatch(r"scripts/test[_-][^/]+\.py", path):
        return False
    if re.match(r"^(?:" + MODULES + r")/src/test/", path):
        return False
    profile = re.fullmatch(r"gradle/minecraft-targets/([^/]+)\.properties", path)
    if profile and profile.group(1) != target:
        return False  # Gradle reads only the selected target tuple.
    return True


# Bounded to the current mainline recipe. These owners select only the 26.2
# roots and never invoke the isolated early NeoForge build. Any owner edit
# other than the exact module-path rename makes this proof unavailable.
MAINLINE_SELECTION_OWNERS = (
    "settings.gradle", "gradle/minecraft-targets.gradle", "gradle/minecraft-source-family.gradle",
    "build-logic/src/main/groovy/multiloader-loader.gradle", "common/build.gradle",
    "fabric/build.gradle", "neoforge/build.gradle", "scripts/compile-native-target.py",
)
FIXTURE_BRIDGE = {
    "common/src/main/java/dev/openallay/guide/e2e/GuideBuilderE2EProbe.java":
        ("b83bada8eabadc9b02c7e7fd0042bb361fd0d7eb", "967658ccc323379eaf7c13b7382e1ba476cc04a4"),
    "common/src/main/java/dev/openallay/guide/e2e/GuideClientE2EController.java":
        ("547ca254c43a042c46bd1587dd0a3c07ce8b102d", "4491dd00306488751b0ff84ad585a1cb6c44dbeb"),
    "common/src/main/java/dev/openallay/guide/e2e/GuideProbeWorldSettings.java":
        ("61dc3b454ff811d09a1baa5c93522b7220a840b1", "60fae2153f90856c27cd69922416258be77f6f85"),
}


def mainline_path(path):
    return path.replace("adapters/minecraft-26.2/", "adapters/minecraft/")


def mainline_text(text):
    return text.replace("adapters:minecraft-26.2", "adapters:minecraft")


def git_tree(root, sha):
    entries = {}
    for record in git(root, "ls-tree", "-r", "-z", sha).split("\0"):
        if not record:
            continue
        metadata, path = record.split("\t", 1)
        mode, kind, blob = metadata.split()
        canonical = mainline_path(path)
        if canonical in entries:
            raise ValueError("competing renamed module paths: " + canonical)
        entries[canonical] = (mode, kind, blob, path)
    return entries


def validate_mainline_closure(root, source_sha, candidate_sha, *, fixture_bridge=False):
    """Exact current 26.2 closure only; never claim JAR byte equivalence."""
    source_sha, candidate_sha = commit_sha(root, source_sha), commit_sha(root, candidate_sha)
    before, after = git_tree(root, source_sha), git_tree(root, candidate_sha)
    rename_owners = {"settings.gradle", "fabric/build.gradle", "neoforge/build.gradle",
                     "gradle/early-neoforge-inputs.gradle"}
    for owner in MAINLINE_SELECTION_OWNERS:
        left = git(root, "show", source_sha + ":" + owner)
        right = git(root, "show", candidate_sha + ":" + owner)
        if (mainline_text(left) if owner in rename_owners else left) != (mainline_text(right) if owner in rename_owners else right):
            raise ValueError("unproved 26.2 selection owner change: " + owner)
    # The exact selection map is read from both immutable commits, not HEAD files.
    selector = git(root, "show", source_sha + ":gradle/minecraft-targets.gradle")
    families = re.search(r"def nativeFamilies = \[(.*?)\]", selector)
    parents = re.search(r"def nativeFamilyParents = \[(.*?)\]", selector)
    if not families or not parents:
        raise ValueError("unproved current native family map")
    pairs = lambda match: dict(re.findall(r"'([^']+)': '([^']+)'", match.group(1)))
    if pairs(families).get("26.2") != "26.2" or "26.2" in pairs(parents):
        raise ValueError("26.2 closure has inherited native families")
    changed, fixture_changes = [], []
    for path in sorted(before.keys() | after.keys()):
        left, right = before.get(path), after.get(path)
        if left and right and left[:3] == right[:3]:
            continue
        changed.append(path)
        if not package_input_changed(path, "26.2") or path == "adapters/minecraft/README.md":
            continue
        if left and right and left[:2] == right[:2] and path in rename_owners:
            if mainline_text(git(root, "show", source_sha + ":" + left[3])) == mainline_text(git(root, "show", candidate_sha + ":" + right[3])):
                continue
        target = re.fullmatch(r"(?:common|fabric|neoforge)/src/targets/([^/]+)/.+", path)
        if target and target.group(1) != "26.2":
            continue  # Only 26.2, with no parent families, is selected by the verified owners.
        if path.startswith("native-builds/early-neoforge/") or path in {
                "gradle/capture-early-neoforge-pipeline.init.gradle", "scripts/capture-early-neoforge-pipeline.py"}:
            continue  # Isolated old-target recipe; verified owners do not include/invoke it on 26.2.
        if (fixture_bridge and path in FIXTURE_BRIDGE and left and right
                and left[:2] == right[:2] == ("100644", "blob")
                and (left[2], right[2]) == FIXTURE_BRIDGE[path]):
            fixture_changes.append(path)
            continue
        raise ValueError("unproved 26.2 behavior/package input change: " + (right or left)[3])
    return {"sourceSha": source_sha, "candidateSha": candidate_sha, "target": "26.2",
            "changedPaths": changed, "reviewedFixtureOnlyBridge": fixture_changes,
            "productionBehaviorInputs": "identical", "binaryByteEquivalence": "not-claimed"}


def validate_source_job(run, jobs, run_id, source_sha, repository, target, run_head_sha=None, *,
                        workflow_path=".github/workflows/minecraft-native.yml", job_name=None):
    # PR jobs package the tested merge SHA; the Actions run API identifies the
    # contributing branch head. Keep both identities, never substitute one.
    run_head_sha = run_head_sha or source_sha
    job_name = job_name or "Native " + target
    if (str(run.get("id")) != run_id or run.get("head_sha") != run_head_sha
            or run.get("path") != workflow_path
            or run.get("repository", {}).get("full_name") != repository):
        raise ValueError("artifact source run does not match repository, workflow, run ID and source SHA")
    matches = [job for job in jobs if job.get("name") == job_name]
    if len(matches) != 1:
        raise ValueError("artifact source must have one exact " + job_name + " job")
    job = matches[0]
    if (job.get("status") != "completed" or job.get("conclusion") != "success"
            or job.get("head_sha") != run_head_sha or str(job.get("run_id")) != run_id
            or job.get("run_attempt") != run.get("run_attempt")):
        raise ValueError("artifact source " + job_name + " job is not successful at its exact run head/attempt")
    if (type(run.get("run_attempt")) is not int or run["run_attempt"] < 1
            or type(job.get("run_attempt")) is not int or job["run_attempt"] < 1
            or type(job.get("id")) is not int or job["id"] < 1
            or job.get("html_url") != "https://github.com/" + repository + "/actions/runs/" + run_id
            + "/job/" + str(job["id"])):
        raise ValueError("artifact source job has no verified positive attempt/job identity/URL")
    return {"id": job["id"], "url": job["html_url"], "run_attempt": job["run_attempt"], "conclusion": "success"}


def make_plan(root, native_targets, smoke_targets, *, candidate_sha, current_run_id, repository,
              source_run_id="", source_sha="", source_run=None, source_jobs=None, current_run_head_sha=None):
    candidate_sha = commit_sha(root, candidate_sha)
    current_run_head_sha = commit_sha(root, current_run_head_sha or candidate_sha)
    if git(root, "rev-parse", "HEAD").strip() != candidate_sha:
        raise ValueError("candidate SHA differs from the checked-out runner source")
    if not re.fullmatch(r"[1-9][0-9]*", current_run_id):
        raise ValueError("current run ID must be a positive integer")
    native = select_native(root, native_targets)
    smokes = select_smoke(root, smoke_targets)
    reused_targets = {target for target, _ in smokes if target not in native}
    changed = []
    verified_jobs = {}
    if reused_targets:
        if not re.fullmatch(r"[1-9][0-9]*", source_run_id) or not source_sha:
            raise ValueError(", ".join(sorted(reused_targets)) + " smoke needs a selected native build or source run ID + SHA")
        source_sha = commit_sha(root, source_sha)
        if source_run_id == current_run_id:
            raise ValueError("reuse must identify a prior artifact run, not this run")
        # --no-renames reports both deleted and added paths. A move cannot hide a
        # production change behind an allowed documentation/test destination.
        changed = [path for path in git(root, "diff", "--name-only", "--no-renames", "-z",
                                        source_sha, candidate_sha, "--").split("\0") if path]
        for target in sorted(reused_targets):
            blocked = [path for path in changed if package_input_changed(path, target)]
            if blocked:
                raise ValueError("cannot reuse " + target + ": changed package inputs: " + ", ".join(blocked))
        if source_run is None or source_jobs is None:
            source_run = json.loads(command("gh", "api", "repos/" + repository + "/actions/runs/" + source_run_id))
            pages = json.loads(command("gh", "api", "--paginate", "--slurp", "repos/" + repository
                                       + "/actions/runs/" + source_run_id + "/jobs?filter=latest&per_page=100"))
            source_jobs = [job for page in pages for job in page["jobs"]]
        for target in sorted(reused_targets):
            verified_jobs[target] = validate_source_job(source_run, source_jobs, source_run_id, source_sha, repository, target)
    elif source_run_id or source_sha:
        raise ValueError("artifact source inputs are unused; remove them when all selected smokes build fresh")
    rows = []
    for target, loader in smokes:
        reused = target not in native
        rows.append({"target": target, "loader": loader, "source_sha": source_sha if reused else candidate_sha,
                     "source_run_id": source_run_id if reused else current_run_id,
                     "source_run_head_sha": source_sha if reused else current_run_head_sha, "reused": reused,
                     "source_job": verified_jobs.get(target)})
    # GitHub rejects empty matrices before job-level conditions on some routes.
    # Guards are false for these inert fallback rows; they can never run a job.
    inert = {"target": NATIVE_TARGETS[0], "loader": "fabric", "source_sha": candidate_sha,
             "source_run_id": current_run_id, "source_run_head_sha": current_run_head_sha,
             "reused": False, "source_job": None}
    return {"candidate_sha": candidate_sha, "candidate_run_id": current_run_id,
            "native_targets": native, "smoke_targets": rows, "changed_paths": changed,
            "run_native": bool(native), "run_smoke": bool(rows),
            "native_matrix": {"target": native or [NATIVE_TARGETS[0]]},
            "smoke_matrix": {"include": [{key: value for key, value in row.items() if key != "source_job"}
                                          for row in rows or [inert]]}}


def verify_artifact_checksums(directory):
    records = {}
    for line in (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([^/\\]+\.jar)", line)
        if not match or match.group(2) in records:
            raise ValueError("invalid production SHA256SUMS")
        sha, name = match.groups()
        path = directory / name
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != sha:
            raise ValueError("production artifact bytes do not match original SHA256SUMS: " + name)
        records[name] = sha
    if not records:
        raise ValueError("production artifact checksum manifest is empty")
    return records


def write_smoke_receipt(args):
    plan = json.loads(args.report.read_text(encoding="utf-8"))
    rows = [row for row in plan["smoke_targets"] if row["target"] == args.receipt_target and row["loader"] == args.receipt_loader]
    if len(rows) != 1:
        raise ValueError("artifact receipt must match one selected smoke")
    row = rows[0]
    run = json.loads(args.source_run_json.read_text(encoding="utf-8"))
    jobs = json.loads(args.source_jobs_json.read_text(encoding="utf-8"))
    job = validate_source_job(run, jobs, row["source_run_id"], row["source_sha"], args.repository,
                              row["target"], row["source_run_head_sha"])
    checksums = verify_artifact_checksums(args.artifact_directory)
    if not any(name.startswith("openallay-" + row["loader"] + "-" + row["target"] + "-") for name in checksums):
        raise ValueError("production artifact has no matching target/loader JAR")
    evidence = {"candidate_sha": plan["candidate_sha"], "candidate_run_id": plan["candidate_run_id"],
                **row, "source_job": job, "artifact_name": "compat-production-" + row["target"] + "-" + row["source_sha"],
                "jar_sha256": checksums, "changed_paths": plan["changed_paths"]}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--native-targets", default="all")
    parser.add_argument("--smoke-targets", default="default")
    parser.add_argument("--candidate-sha", default="")
    parser.add_argument("--current-run-id", default="")
    parser.add_argument("--current-run-head-sha", default="")
    parser.add_argument("--repository", required=True)
    parser.add_argument("--source-run-id", default="")
    parser.add_argument("--source-sha", default="")
    parser.add_argument("--report", type=Path, default=Path("ci-verification-plan.json"))
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--receipt-target")
    parser.add_argument("--receipt-loader", choices=("fabric", "neoforge"))
    parser.add_argument("--artifact-directory", type=Path)
    parser.add_argument("--source-run-json", type=Path)
    parser.add_argument("--source-jobs-json", type=Path)
    args = parser.parse_args()
    try:
        if args.receipt:
            if not all((args.receipt_target, args.receipt_loader, args.artifact_directory,
                        args.source_run_json, args.source_jobs_json)):
                raise ValueError("receipt requires target, loader, artifacts and source run/jobs JSON")
            result = write_smoke_receipt(args)
        else:
            result = make_plan(args.root, args.native_targets, args.smoke_targets, candidate_sha=args.candidate_sha,
                               current_run_id=args.current_run_id, repository=args.repository,
                               source_run_id=args.source_run_id, source_sha=args.source_sha,
                               current_run_head_sha=args.current_run_head_sha)
            args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            if args.github_output:
                with args.github_output.open("a", encoding="utf-8") as output:
                    for name in ("run_native", "run_smoke", "native_matrix", "smoke_matrix"):
                        output.write(name + "=" + json.dumps(result[name], separators=(",", ":")) + "\n")
        print(json.dumps(result, indent=2))
        return 0
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as exc:
        print("CI verification selection refused: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
