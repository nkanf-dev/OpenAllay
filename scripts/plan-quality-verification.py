#!/usr/bin/env python3
"""Select mainline Quality clients and keep their verified JAR source exact.

Full mode builds/tests once and runs both complete clients. Clients mode runs
only the failed/affected loaders selected by the operator, with unchanged prior
production artifacts. No game or Gradle work is done by this planner.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("ci_verification", Path(__file__).with_name("plan-ci-verification.py"))
shared = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(shared)
LOADERS = ("fabric", "neoforge")
TARGET = "26.2"


def select_clients(value):
    if value == "none":
        return []
    loaders = shared.selected_tokens(value, "client_loaders")
    if any(loader not in LOADERS for loader in loaders) or ",".join(loaders) != value:
        raise ValueError("client_loaders requires exact fabric/neoforge CSV or none")
    return loaders


def validate_quality_source(run, jobs, run_id, source_sha, repository, run_head_sha=None):
    return shared.validate_source_job(run, jobs, run_id, source_sha, repository, TARGET, run_head_sha,
                                      workflow_path=".github/workflows/quality.yml", job_name="Minecraft 26.2")


def make_plan(root, mode, client_loaders, *, candidate_sha, current_run_id, repository,
              source_run_id="", source_sha="", source_run=None, source_jobs=None, current_run_head_sha=None):
    candidate_sha = shared.commit_sha(root, candidate_sha)
    current_run_head_sha = shared.commit_sha(root, current_run_head_sha or candidate_sha)
    if shared.git(root, "rev-parse", "HEAD").strip() != candidate_sha:
        raise ValueError("candidate SHA differs from the checked-out runner source")
    if not re.fullmatch(r"[1-9][0-9]*", current_run_id):
        raise ValueError("current run ID must be a positive integer")
    if mode not in ("full", "clients"):
        raise ValueError("mode must be full or clients")
    if mode == "full":
        if client_loaders != "none" or source_run_id or source_sha:
            raise ValueError("full mode always verifies and runs both clients; remove selective/source inputs")
        loaders = list(LOADERS)
    else:
        loaders = select_clients(client_loaders)
    changed, source_job = [], None
    if mode == "clients" and loaders:
        if not re.fullmatch(r"[1-9][0-9]*", source_run_id) or not source_sha:
            raise ValueError("selected clients need the prior artifact run ID and exact source SHA")
        source_sha = shared.commit_sha(root, source_sha)
        if source_run_id == current_run_id:
            raise ValueError("reuse must identify a prior artifact run, not this run")
        changed = [path for path in shared.git(root, "diff", "--name-only", "--no-renames", "-z",
                                               source_sha, candidate_sha, "--").split("\0") if path]
        shared.validate_mainline_closure(root, source_sha, candidate_sha)
        if source_run is None or source_jobs is None:
            source_run = json.loads(shared.command("gh", "api", "repos/" + repository + "/actions/runs/" + source_run_id))
            pages = json.loads(shared.command("gh", "api", "--paginate", "--slurp", "repos/" + repository
                                               + "/actions/runs/" + source_run_id + "/jobs?filter=latest&per_page=100"))
            source_jobs = [job for page in pages for job in page["jobs"]]
        source_job = validate_quality_source(source_run, source_jobs, source_run_id, source_sha, repository)
    elif source_run_id or source_sha:
        raise ValueError("artifact source inputs are unused; remove them when no clients are selected")
    rows = [{"loader": loader, "source_sha": source_sha if mode == "clients" else candidate_sha,
             "source_run_id": source_run_id if mode == "clients" else current_run_id,
             "source_run_head_sha": source_sha if mode == "clients" else current_run_head_sha,
             "reused": mode == "clients", "source_job": source_job} for loader in loaders]
    inert = {"loader": "fabric", "source_sha": candidate_sha, "source_run_id": current_run_id,
             "source_run_head_sha": current_run_head_sha, "reused": False, "source_job": None}
    return {"mode": mode, "candidate_sha": candidate_sha, "candidate_run_id": current_run_id,
            "client_loaders": loaders, "clients": rows, "changed_paths": changed,
            "run_verify": mode == "full", "run_client": bool(rows),
            "client_matrix": {"include": [{key: value for key, value in row.items() if key != "source_job"}
                                            for row in rows or [inert]]}}


def write_client_receipt(args):
    plan = json.loads(args.report.read_text(encoding="utf-8"))
    rows = [row for row in plan["clients"] if row["loader"] == args.receipt_loader]
    if len(rows) != 1:
        raise ValueError("artifact receipt must match one selected Quality client")
    row = rows[0]
    run = json.loads(args.source_run_json.read_text(encoding="utf-8"))
    jobs = json.loads(args.source_jobs_json.read_text(encoding="utf-8"))
    job = validate_quality_source(run, jobs, row["source_run_id"], row["source_sha"], args.repository,
                                  row["source_run_head_sha"])
    if row["reused"] and job != row["source_job"]:
        raise ValueError("Quality source job/attempt changed after selection; select the source again")
    checksums = shared.verify_artifact_checksums(args.artifact_directory)
    for loader in LOADERS:
        if len([name for name in checksums if name.startswith("openallay-" + loader + "-" + TARGET + "-")]) != 1:
            raise ValueError("Quality production artifact requires exactly one 26.2 JAR for each loader")
    if len(checksums) != len(LOADERS):
        raise ValueError("Quality production artifact must contain only the original two loader JARs")
    evidence = {"candidate_sha": plan["candidate_sha"], "candidate_run_id": plan["candidate_run_id"],
                **row, "source_job": job, "artifact_name": "client-production-" + row["source_sha"],
                "jar_sha256": checksums, "changed_paths": plan["changed_paths"]}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--mode", default="clients", choices=("full", "clients"))
    parser.add_argument("--client-loaders", default="none")
    parser.add_argument("--candidate-sha", default="")
    parser.add_argument("--current-run-id", default="")
    parser.add_argument("--current-run-head-sha", default="")
    parser.add_argument("--repository", required=True)
    parser.add_argument("--source-run-id", default="")
    parser.add_argument("--source-sha", default="")
    parser.add_argument("--report", type=Path, default=Path("quality-verification-plan.json"))
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--receipt-loader", choices=LOADERS)
    parser.add_argument("--artifact-directory", type=Path)
    parser.add_argument("--source-run-json", type=Path)
    parser.add_argument("--source-jobs-json", type=Path)
    args = parser.parse_args()
    try:
        if args.receipt:
            if not all((args.receipt_loader, args.artifact_directory, args.source_run_json, args.source_jobs_json)):
                raise ValueError("receipt requires loader, artifacts and source run/jobs JSON")
            result = write_client_receipt(args)
        else:
            result = make_plan(args.root, args.mode, args.client_loaders, candidate_sha=args.candidate_sha,
                               current_run_id=args.current_run_id, repository=args.repository,
                               source_run_id=args.source_run_id, source_sha=args.source_sha,
                               current_run_head_sha=args.current_run_head_sha)
            args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            if args.github_output:
                with args.github_output.open("a", encoding="utf-8") as output:
                    for name in ("run_verify", "run_client", "client_matrix"):
                        output.write(name + "=" + json.dumps(result[name], separators=(",", ":")) + "\n")
        print(json.dumps(result, indent=2))
        return 0
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError) as exc:
        print("Quality verification selection refused: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
