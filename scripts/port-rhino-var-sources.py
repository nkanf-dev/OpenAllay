#!/usr/bin/env python3
"""Remote build-only attributed var lowering into an immutable canonical recipe packet."""
import argparse
import base64
import difflib
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("archive", type=Path)
parser.add_argument("--javac", type=Path, required=True)
parser.add_argument("--java", type=Path, required=True, help="Modern JDK for build-only compiler attribution")
parser.add_argument("--classpath", required=True, help="Actual canonical Rhino compile classpath including genuine Gson/Guava/annotations")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
args.output.mkdir(parents=True, exist_ok=False)
prepared = args.output / "prepared"
manifest_path = root / "runtime-rhino/patches/rhino-source-manifest.json"
hunks_path = root / "runtime-rhino/patches/rhino-java17-hunks.json"
subprocess.run([sys.executable, str(root / "scripts/prepare-rhino-sources.py"), str(args.archive),
    str(manifest_path), str(hunks_path), str(prepared)], check=True)
classes = args.output / "tool-classes"; classes.mkdir()
source = root / "scripts/rhino-language-port/RhinoVarPort.java"
subprocess.run([str(args.javac), "--release", "17", "-d", str(classes), str(source), str(root / "scripts/rhino-language-port/RhinoVarPortFixture.java")], check=True)
subprocess.run([str(args.java), "-cp", str(classes), "dev.openallay.tools.rhino.RhinoVarPortFixture"], check=True)
selected = root / "scripts/rhino-language-port/selected-var-paths.txt"
sites_file = args.output / "attributed-var-sites.tsv"
subprocess.run([str(args.java), "-cp", str(classes), "dev.openallay.tools.rhino.RhinoVarPort",
    str(prepared / "java"), args.classpath, str(selected), str(sites_file)], check=True)
sites = {}
for line in sites_file.read_text().splitlines():
    path, offset, encoded = line.split("\t")
    sites.setdefault(path, []).append((int(offset), base64.b64decode(encoded).decode()))
expected = json.loads((root / "scripts/rhino-language-port/selected-var-counts.json").read_text())
if {path: len(values) for path, values in sites.items()} != expected:
    raise ValueError("Attributed sites differ from approved exact owner counts")

def digest(blob): return hashlib.sha256(blob).hexdigest()
def delta_hunks(before, after):
    if before == after:
        return []
    if not before:
        raise ValueError("Empty owner preimage cannot be patched uniquely")
    a = before.splitlines(True); b = after.splitlines(True); result = []; working = before
    for group in difflib.SequenceMatcher(None, a, b).get_grouped_opcodes(3):
        old = "".join(a[group[0][1]:group[-1][2]])
        new = "".join(b[group[0][3]:group[-1][4]])
        if working.count(old) != 1:
            # A whole nonempty owner matches itself exactly once. The caller records
            # its exact pre/post SHA256; never replace several ambiguous fragments.
            return [{"before": before, "after": after}]
        working = working.replace(old, new, 1); result.append({"before": old, "after": new})
    if working != after: raise ValueError("Source delta replay differs")
    return result

before = {str(p.relative_to(prepared / "java")): p.read_text() for p in (prepared / "java").rglob("*.java")}
after = dict(before)
for path, values in sites.items():
    # javac offsets count UTF16 code units, not Python Unicode code points.
    text = before[path].encode("utf-16-le")
    for offset, replacement in sorted(values, reverse=True):
        start = offset * 2
        if text[start:start + 6] != "var".encode("utf-16-le"): raise ValueError("Source token differs: " + path)
        text = text[:start] + replacement.encode("utf-16-le") + text[start + 6:]
    after[path] = text.decode("utf-16-le")
manifest = json.loads(manifest_path.read_text()); hunks = json.loads(hunks_path.read_text())
for path in sites:
    entry = next((item for item in hunks["files"] if item["path"] == path), None)
    if entry is None:
        entry = {"path": path, "old_path": path, "hunks": []}; hunks["files"].append(entry)
    entry["hunks"].extend(delta_hunks(before[path], after[path]))
published_path = root / "runtime-rhino/patches" / manifest["patch"]["filename"]
with zipfile.ZipFile(args.archive) as archive:
    upstream = {item["zip_path"]: archive.read(item["zip_path"]).decode() for item in manifest["source_closure"]}
published = ""
for path in after:
    original = upstream.get(path, "")
    if original == after[path]: continue
    published += "".join(difflib.unified_diff(original.splitlines(True), after[path].splitlines(True),
        fromfile="a/" + path if path in upstream else "/dev/null", tofile="b/" + path, n=0))
hunks["patch_sha256"] = digest(published.encode())
manifest["patch"]["sha256"] = hunks["patch_sha256"]; manifest["patch"]["bytes"] = len(published.encode())
manifest["patch"]["modified_upstream_files"] = sum(item["old_path"] is not None for item in hunks["files"])
manifest["patch"]["hunks"] = sum(len(item["hunks"]) for item in hunks["files"])
for item in manifest["source_closure"]:
    item["patched_bytes"] = len(after[item["zip_path"]].encode())
    item["patched_sha256"] = digest(after[item["zip_path"]].encode())
    if item["zip_path"] in sites: item["modified"] = True
manifest["source_only_status"] = "Attributed var language-only owner batch; remaining syntax and genuine post8 capability APIs stay pending. Full runtime build remains release17."
packet = args.output / "source-packet"; packet.mkdir()
changes = {
    "runtime-rhino/patches/rhino-java17-hunks.json": (hunks_path.read_text(), json.dumps(hunks, indent=2) + "\n"),
    "runtime-rhino/patches/rhino-source-manifest.json": (manifest_path.read_text(), json.dumps(manifest, indent=2) + "\n"),
    str(published_path.relative_to(root)): (published_path.read_text(), published),
}
patch = ""
for path, (old, new) in changes.items():
    for folder, text in [("pre", old), ("post", new)]:
        destination = packet / folder / path; destination.parent.mkdir(parents=True, exist_ok=True); destination.write_text(text)
    patch += "".join(difflib.unified_diff(old.splitlines(True), new.splitlines(True), fromfile="a/" + path, tofile="b/" + path))
(packet / "source.patch").write_text(patch)
(packet / "manifest.json").write_text(json.dumps({"patch_sha256": digest(patch.encode()),
    "scope": "attributed var declarations only", "source_count": 277, "site_count": sum(expected.values()),
    "files": [{"path": path, "pre_bytes": len(old.encode()), "pre_sha256": digest(old.encode()),
        "post_bytes": len(new.encode()), "post_sha256": digest(new.encode())} for path, (old, new) in changes.items()],
    "owners": [{"path": path, "pre_bytes": len(before[path].encode()), "pre_sha256": digest(before[path].encode()),
        "post_bytes": len(after[path].encode()), "post_sha256": digest(after[path].encode()),
        "sites": expected[path]} for path in sorted(sites)], "fullRuntimeJava8Acceptance": False}, indent=2) + "\n")
# Replay the entire exact277-source recipe after materialization, before delivery.
subprocess.run([sys.executable, str(root / "scripts/prepare-rhino-sources.py"), str(args.archive),
    str(packet / "post/runtime-rhino/patches/rhino-source-manifest.json"),
    str(packet / "post/runtime-rhino/patches/rhino-java17-hunks.json"), str(args.output / "verified-postimage")], check=True)
for path in before:
    actual = (args.output / "verified-postimage/java" / path).read_text()
    if actual != after[path]: raise ValueError("Final canonical postimage mismatch: " + path)
# All output belongs to this external immutable packet; never overwrite project source.
print("Canonical source packet: " + str(packet))
