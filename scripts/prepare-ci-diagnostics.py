#!/usr/bin/env python3
"""Stage lossless diagnostics from one explicit disposable CI fixture run.

Source files are never modified. See --help; this is not a runtime archiver or a
secret scrubber. Reports/traces must already use the fixture's redaction path.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import struct
import subprocess
import sys
import tempfile
import warnings

REPO = Path(__file__).resolve().parents[1]
JSON_NAMES = {"report.json", "trace.json", "report.trace.json", "source-manifest.json", "summary.json",
              "launch.json", "native-snapshot-audit.json", "persistence-audit.json"}
ROOT_LOGS = {"client.log", "fixture.log", "stdout.log", "stderr.log", "latest.log", "debug.log"}
DENIED = {"config", "configs", "credentials", "accounts", "models", "saves", "worlds", "assets",
          "libraries", "runtime", "runtimes", "natives", "mods", "resourcepacks", "history"}
ENCODER_FLAGS = ["-quiet", "-lossless", "-exact", "-m", "6", "-metadata", "all"]
MAX_FILE_BYTES = 128 * 1024 * 1024
MAX_TOTAL_BYTES = 512 * 1024 * 1024
MAX_FILES = 2048
MAX_PIXELS = 32_000_000
NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,120}\Z")


class DiagnosticsError(ValueError):
    pass


def no_links(path):
    for part in (path, *path.parents):
        if part.is_symlink():
            raise DiagnosticsError("Symlinks are not allowed: " + str(part))


def safe_root(path, repo):
    path = Path(path)
    if ".." in path.parts:
        raise DiagnosticsError("Parent traversal is not allowed")
    path = path if path.is_absolute() else repo / path
    root = repo / "build/e2e"
    if path == root or not path.is_relative_to(root):
        raise DiagnosticsError("Select a run/stage below ignored build/e2e, not the shared root")
    no_links(path)
    return path


def diagnostic_kind(relative):
    text = str(relative)
    path = PurePosixPath(text)
    if (not text or "\\" in text or path.is_absolute() or text != path.as_posix()
            or any(not NAME.fullmatch(part) or part.lower() in DENIED for part in path.parts)):
        raise DiagnosticsError("Unsafe diagnostic relative path: " + text)
    parts = path.parts
    if any(token in path.stem.lower() for token in ("credential", "secret", "password", "account", "token")):
        raise DiagnosticsError("Credential-like diagnostic filenames are not allowed")
    if len(parts) == 1 and path.name in JSON_NAMES:
        return "report"
    if path.suffix == ".log" and (len(parts) == 1 or parts[0] == "logs"):
        return "log"
    if text in {"game/logs/latest.log", "game/logs/debug.log"}:
        return "log"
    if path.suffix == ".png" and len(parts) >= 2 and parts[0] == "screenshots":
        return "screenshot"
    raise DiagnosticsError("Not an allowed CI diagnostic: " + text)


def select_files(source, includes):
    selected = set()
    for name in sorted(JSON_NAMES | ROOT_LOGS):
        if os.path.lexists(source / name):
            selected.add(name)
    # Never traverse a game profile. Only these diagnostic subtrees are scanned.
    for directory, suffix in (("screenshots", ".png"), ("logs", ".log")):
        base = source / directory
        if not os.path.lexists(base):
            continue
        no_links(base)
        if not base.is_dir():
            raise DiagnosticsError("Diagnostic subtree is not a directory: " + directory)
        def walk_error(error):
            raise DiagnosticsError("Cannot inspect diagnostic subtree") from error
        for parent, directories, files in os.walk(base, followlinks=False, onerror=walk_error):
            directories.sort()
            files.sort()
            for name in directories + files:
                candidate = Path(parent) / name
                no_links(candidate)
                relative_parts = candidate.relative_to(source).parts
                if any(not NAME.fullmatch(part) or part.lower() in DENIED for part in relative_parts):
                    raise DiagnosticsError("Unsafe path in diagnostic subtree")
            for name in files:
                if Path(name).suffix == suffix:
                    selected.add((Path(parent) / name).relative_to(source).as_posix())
            if len(selected) > MAX_FILES:
                raise DiagnosticsError("Too many diagnostic files")
    selected.update(includes)
    if not selected:
        raise DiagnosticsError("No allowed diagnostics found in the explicit run root")
    if len(selected) > MAX_FILES:
        raise DiagnosticsError("Too many diagnostic files")
    for relative in sorted(selected):
        diagnostic_kind(relative)
        candidate = source / relative
        no_links(candidate)
        try:
            info = candidate.stat()
        except OSError as error:
            raise DiagnosticsError("Missing selected diagnostic: " + relative) from error
        if not stat.S_ISREG(info.st_mode):
            raise DiagnosticsError("Selected diagnostic is not a regular file: " + relative)
    return sorted(selected)


def file_identity(info):
    return (info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns)


def copy_snapshot(source, target, limit):
    no_links(source)
    descriptor = os.open(source, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    sha = hashlib.sha256()
    count = 0
    target.parent.mkdir(parents=True, exist_ok=True)
    with os.fdopen(descriptor, "rb") as stream, target.open("xb") as output:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode) or before.st_size > limit:
            raise DiagnosticsError("Selected diagnostic exceeds the file limit or is not regular")
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            count += len(chunk)
            if count > limit:
                raise DiagnosticsError("Diagnostic grew beyond the file limit")
            sha.update(chunk)
            output.write(chunk)
        if file_identity(before) != file_identity(os.fstat(stream.fileno())) or count != before.st_size:
            raise DiagnosticsError("Source diagnostic changed while being read")
    return sha.hexdigest(), count, file_identity(before)


def digest(path, expected_identity=None):
    no_links(path)
    sha = hashlib.sha256()
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(descriptor, "rb") as stream:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode) or (expected_identity is not None
                and file_identity(before) != expected_identity):
            raise DiagnosticsError("Source diagnostic changed before publication")
        count = 0
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            count += len(chunk)
            if count > before.st_size:
                raise DiagnosticsError("Diagnostic grew while hashing")
            sha.update(chunk)
        if count != before.st_size or file_identity(before) != file_identity(os.fstat(stream.fileno())):
            raise DiagnosticsError("Diagnostic changed while hashing")
    return sha.hexdigest()


def decode_image(path, expected_format, max_pixels):
    try:
        from PIL import Image
    except ImportError as error:
        raise DiagnosticsError("Pillow is required in the CI tool environment for pixel verification") from error
    try:
        if expected_format == "PNG":
            with path.open("rb") as stream:
                header = stream.read(29)
            if (len(header) != 29 or header[:8] != b"\x89PNG\r\n\x1a\n"
                    or header[8:16] != b"\x00\x00\x00\x0dIHDR" or header[24] > 8):
                raise DiagnosticsError("Unsupported PNG header or high-depth screenshot")
            width, height = struct.unpack(">II", header[16:24])
            if width <= 0 or height <= 0 or max(width, height) > 16383 or width * height > max_pixels:
                raise DiagnosticsError("Oversized diagnostic image")
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(path) as image:
                width, height = image.size
                if (image.format != expected_format or getattr(image, "n_frames", 1) != 1
                        or width <= 0 or height <= 0 or max(width, height) > 16383
                        or width * height > max_pixels):
                    raise DiagnosticsError("Invalid, animated, or oversized diagnostic image")
                image.load()
                rgba = image.convert("RGBA").tobytes()
                metadata = {key: image.info.get(key, b"") for key in ("icc_profile", "exif")}
                return (width, height, rgba, metadata)
    except DiagnosticsError:
        raise
    except Exception as error:
        raise DiagnosticsError("Cannot decode/verify diagnostic image") from error


def encode_webp(cwebp, source, output):
    try:
        result = subprocess.run([cwebp, *ENCODER_FLAGS, str(source), "-o", str(output)],
                                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=120, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise DiagnosticsError("Lossless cwebp encoding failed or timed out") from error
    if result.returncode != 0:
        raise DiagnosticsError("Lossless cwebp encoding failed")


def prepare(source, output, cwebp="cwebp", includes=(), *, repo=REPO,
            max_file_bytes=MAX_FILE_BYTES, max_total_bytes=MAX_TOTAL_BYTES, max_pixels=MAX_PIXELS):
    repo = Path(repo).resolve()
    for value, bound in ((max_file_bytes, MAX_FILE_BYTES), (max_total_bytes, MAX_TOTAL_BYTES),
                         (max_pixels, MAX_PIXELS)):
        if type(value) is not int or not 0 < value <= bound:
            raise DiagnosticsError("Limits must be positive integers no larger than built-in bounds")
    source = safe_root(source, repo)
    output = safe_root(output, repo)
    if not source.is_dir():
        raise DiagnosticsError("Explicit CI source run root must exist")
    if source.is_relative_to(output) or output.is_relative_to(source):
        raise DiagnosticsError("Source and output trees must not overlap")
    if os.path.lexists(output):
        raise DiagnosticsError("Output must be a new, nonexistent compact staging root")
    selected = select_files(source, includes)
    source_total = sum((source / name).stat().st_size for name in selected)
    if source_total > max_total_bytes or any((source / name).stat().st_size > max_file_bytes for name in selected):
        raise DiagnosticsError("Selected diagnostics exceed the byte limits")
    encoder = shutil.which(cwebp)
    has_images = any(diagnostic_kind(name) == "screenshot" for name in selected)
    if has_images and encoder is None:
        raise DiagnosticsError("Standard cwebp is required to stage PNG screenshots")
    output.parent.mkdir(parents=True, exist_ok=True)
    no_links(output.parent)
    workspace = Path(tempfile.mkdtemp(prefix=".ci-diagnostics-", dir=output.parent))
    entries = []
    originals = []
    published = False
    reserved_output = False
    try:
        staging = workspace / "publish"
        staging.mkdir()
        total_read = 0
        for relative in selected:
            snapshot = workspace / "inputs" / relative
            source_sha, source_bytes, identity = copy_snapshot(source / relative, snapshot, max_file_bytes)
            total_read += source_bytes
            if total_read > max_total_bytes:
                raise DiagnosticsError("Selected diagnostics exceed the total byte limit")
            originals.append((relative, source_sha, identity))
            kind = diagnostic_kind(relative)
            output_relative = relative
            chosen = snapshot
            image_info = None
            no_savings = False
            if kind == "screenshot":
                original = decode_image(snapshot, "PNG", max_pixels)
                candidate = workspace / "encoded" / Path(relative).with_suffix(".webp")
                candidate.parent.mkdir(parents=True, exist_ok=True)
                encode_webp(encoder, snapshot, candidate)
                no_links(candidate)
                if not candidate.is_file() or not 0 < candidate.stat().st_size <= max_file_bytes:
                    raise DiagnosticsError("Encoded WebP exceeds the file limit or is missing")
                decoded = decode_image(candidate, "WEBP", max_pixels)
                if original[:3] != decoded[:3]:
                    raise DiagnosticsError("Lossless WebP decoded RGBA pixels/dimensions do not match PNG")
                if original[3] != decoded[3]:
                    raise DiagnosticsError("Lossless WebP ICC/EXIF metadata does not match PNG")
                webp_bytes = candidate.stat().st_size
                image_info = {"width": original[0], "height": original[1],
                              "rgbaSha256": hashlib.sha256(original[2]).hexdigest(),
                              "webpSha256": digest(candidate), "webpBytes": webp_bytes,
                              "pixelsVerified": True, "metadataVerified": True}
                no_savings = webp_bytes >= source_bytes
                if not no_savings:
                    chosen = candidate
                    output_relative = Path(relative).with_suffix(".webp").as_posix()
            destination = staging / output_relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            with chosen.open("rb") as stream, destination.open("xb") as target:
                shutil.copyfileobj(stream, target, 1024 * 1024)
            entries.append({"sourcePath": relative, "sourceSha256": source_sha, "sourceBytes": source_bytes,
                            "outputPath": output_relative, "outputSha256": digest(destination),
                            "outputBytes": destination.stat().st_size, "kind": kind,
                            "noSavings": no_savings, "image": image_info})
        # Do not publish mixed evidence from a still-running or changed run.
        for relative, expected_sha, identity in originals:
            path = source / relative
            no_links(path)
            if file_identity(path.stat()) != identity or digest(path, identity) != expected_sha:
                raise DiagnosticsError("Source diagnostic changed before publication")
        manifest = {"sourceRoot": str(source), "outputRoot": str(output),
                    "encoder": {"program": encoder if has_images else None,
                                "arguments": ENCODER_FLAGS if has_images else []},
                    "sourceBytes": total_read, "outputBytes": sum(entry["outputBytes"] for entry in entries),
                    "files": entries}
        (staging / "diagnostics-manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        # Exclusive reservation prevents replacement of even an empty frozen stage.
        no_links(output)
        output.mkdir()
        reserved_output = True
        for child in sorted(staging.iterdir(), key=lambda item: item.name == "diagnostics-manifest.json"):
            child.rename(output / child.name)
        published = True
        return manifest
    finally:
        shutil.rmtree(workspace)
        if reserved_output and not published:
            shutil.rmtree(output)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="Explicit CI fixture run root below build/e2e")
    parser.add_argument("--output", required=True, type=Path, help="New compact staging root below build/e2e, outside source")
    parser.add_argument("--cwebp", default="cwebp", help="Standard cwebp executable (Ubuntu package: webp)")
    parser.add_argument("--include", action="append", default=[], help="Additional exact, allowlisted relative diagnostic file; repeatable")
    parser.add_argument("--max-file-bytes", type=int, default=MAX_FILE_BYTES)
    parser.add_argument("--max-total-bytes", type=int, default=MAX_TOTAL_BYTES)
    parser.add_argument("--max-pixels", type=int, default=MAX_PIXELS)
    args = parser.parse_args(argv)
    try:
        manifest = prepare(args.source, args.output, args.cwebp, args.include,
                           max_file_bytes=args.max_file_bytes, max_total_bytes=args.max_total_bytes,
                           max_pixels=args.max_pixels)
    except (DiagnosticsError, OSError) as error:
        print("CI diagnostics refused: " + str(error), file=sys.stderr)
        return 1
    print(json.dumps({"output": manifest["outputRoot"], "files": len(manifest["files"]),
                      "sourceBytes": manifest["sourceBytes"], "outputBytes": manifest["outputBytes"]}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
