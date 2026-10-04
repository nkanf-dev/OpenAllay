#!/usr/bin/env python3
"""Prepare or verify the exact Extension source used by distribution builds.

Network access happens only in the explicit prepare command. Gradle uses
--verify-only and never clones, fetches, or changes an existing checkout.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "distribution/extensions.lock.json"
DEFAULT_SOURCE = ROOT / ".gradle/distribution-sources/openallay-extensions"


def decode_json(content: str | bytes) -> dict:
    """Standard JSON parser with duplicate members and non-finite values rejected."""
    def object_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON member: {key}")
            result[key] = value
        return result

    def invalid_constant(value):
        raise ValueError(f"Non-finite JSON number: {value}")

    return json.loads(content, object_pairs_hook=object_pairs, parse_constant=invalid_constant)


def exact_fields(value: object, fields: set[str], name: str) -> None:
    if not isinstance(value, dict) or set(value) != fields:
        raise ValueError(f"{name} fields do not match the current shape")


def relative_path(value: object) -> str:
    if not isinstance(value, str) or not value or value.strip() != value:
        raise ValueError("Extension paths must be nonblank relative paths")
    path = PurePosixPath(value)
    if (path.is_absolute() or ".." in path.parts or "\\" in value or ":" in value
            or path.as_posix() != value or value == "."):
        raise ValueError("Extension paths must be relative and cannot traverse directories")
    return value


def validate_manifest(data: object) -> dict:
    exact_fields(data, {"source", "project", "version", "extensionId", "openAllayApiVersion", "artifact"},
        "Extension lock")
    source = data["source"]
    exact_fields(source, {"repository", "revision"}, "Extension source")
    if not isinstance(source["revision"], str) or not re.fullmatch(r"[0-9a-f]{40}", source["revision"]):
        raise ValueError("Extension source revision must be a full 40-character Git commit")
    if not isinstance(source["repository"], str):
        raise ValueError("Extension source repository must be an HTTPS URL without credentials")
    url = urlparse(source["repository"])
    if url.scheme != "https" or not url.hostname or url.username or url.password:
        raise ValueError("Extension source repository must be an HTTPS URL without credentials")
    for field in ("project", "artifact"):
        relative_path(data[field])
    if not data["artifact"].endswith(".jar"):
        raise ValueError("Extension artifact must be a JAR path")
    for field in ("version", "openAllayApiVersion"):
        if not isinstance(data[field], str) or not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+\-]*", data[field]):
            raise ValueError(f"Invalid Extension {field}")
    if not isinstance(data["extensionId"], str) or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", data["extensionId"]):
        raise ValueError("Invalid Extension extensionId")
    return data


def load_manifest(path: Path) -> dict:
    return validate_manifest(decode_json(path.read_text(encoding="utf-8")))


def git(source: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(source), *args], check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).stdout.strip()


def verify_source(source: Path, manifest: dict, allow_unpinned: bool = False) -> dict:
    if not (source / ".git").exists():
        raise ValueError("Extension source is not prepared. Run python3 scripts/prepare-distribution.py")
    if Path(git(source, "rev-parse", "--show-toplevel")).resolve() != source.resolve():
        raise ValueError("Extension source must be the root of its own Git checkout")
    if git(source, "remote", "get-url", "origin") != manifest["source"]["repository"]:
        raise ValueError("Extension source checkout origin does not match the lock")
    revision = git(source, "rev-parse", "HEAD")
    dirty = bool(git(source, "status", "--porcelain", "--untracked-files=all"))
    if not allow_unpinned:
        if revision != manifest["source"]["revision"]:
            raise ValueError(f"Extension revision mismatch: {revision}; expected {manifest['source']['revision']}")
        if dirty:
            raise ValueError("Extension source has uncommitted changes; a distribution requires clean pinned source")
    project = source / manifest["project"]
    if not project.resolve().is_relative_to(source.resolve()):
        raise ValueError("Extension project escapes the source checkout")
    if not (project / "settings.gradle").is_file():
        raise ValueError(f"Extension project is missing at {project}; check the source lock revision")
    return {"revision": revision, "dirty": dirty, "pinned": not allow_unpinned}


def prepare_source(source: Path, manifest: dict) -> None:
    source.parent.mkdir(parents=True, exist_ok=True)
    if source.exists():
        if not (source / ".git").exists():
            raise ValueError(f"Refusing to overwrite non-checkout directory: {source}")
        if git(source, "status", "--porcelain", "--untracked-files=all"):
            raise ValueError(f"Refusing to change dirty source checkout: {source}")
        if git(source, "remote", "get-url", "origin") != manifest["source"]["repository"]:
            raise ValueError("Existing source checkout origin does not match the lock")
    else:
        source.mkdir()
        git(source, "init")
        git(source, "remote", "add", "origin", manifest["source"]["repository"])
    revision = manifest["source"]["revision"]
    git(source, "fetch", "--depth=1", "origin", revision)
    git(source, "-c", "advice.detachedHead=false", "checkout", "--detach", revision)
    verify_source(source, manifest)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--source-directory", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--allow-unpinned", action="store_true", help="Explicit local development only")
    args = parser.parse_args()
    try:
        manifest = load_manifest(args.manifest)
        source = args.source_directory.resolve()
        if args.allow_unpinned and not args.verify_only:
            raise ValueError("--allow-unpinned is only valid with --verify-only; preparation always uses the lock")
        if not args.verify_only:
            prepare_source(source, manifest)
        evidence = verify_source(source, manifest, args.allow_unpinned)
        print(json.dumps(evidence, sort_keys=True))
        if args.allow_unpinned:
            print("WARNING: local unpinned Extension source; not a reproducible release build", file=sys.stderr)
        return 0
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as exc:
        print(f"Extension source verification failed: {exc}", file=sys.stderr)
        if isinstance(exc, subprocess.CalledProcessError) and exc.stderr:
            print(exc.stderr.strip(), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
