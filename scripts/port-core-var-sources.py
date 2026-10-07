#!/usr/bin/env python3
"""Remote-only public compiler attribution into one immutable canonical source packet."""
import argparse
import base64
import difflib
import hashlib
import json
from pathlib import Path
import os
import shutil
import subprocess


def digest(blob):
    return hashlib.sha256(blob).hexdigest()


def replace_sites(blob, sites):
    # javac SourcePositions count UTF16 units, including supplementary characters.
    text = blob.decode("utf-8").encode("utf-16-le")
    offsets = set()
    for offset, replacement in sorted(sites, reverse=True):
        if offset in offsets or offset < 0:
            raise ValueError("Duplicate/negative compiler offset")
        offsets.add(offset)
        start = offset * 2
        if text[start:start + 6] != "var".encode("utf-16-le"):
            raise ValueError("Exact parsed source token differs")
        text = text[:start] + replacement.encode("utf-16-le") + text[start + 6:]
    return text.decode("utf-16-le").encode("utf-8")


def read_sites(path):
    owners = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        owner, offset, encoded, variable = line.split("\t")
        owners.setdefault(owner, []).append((int(offset), base64.b64decode(encoded, validate=True).decode("utf-8")))
    return owners


def classpath_receipt(entries):
    result = []
    for name in entries:
        path = Path(name)
        if path.is_file():
            blob = path.read_bytes()
            result.append({"path": str(path), "kind": "file", "bytes": len(blob), "sha256": digest(blob)})
        elif path.is_dir():
            classes = sorted(path.rglob("*.class"))
            if not classes:
                raise ValueError("Empty/non-class compiler dependency directory: " + str(path))
            rows = [{"path": str(p.relative_to(path)), "sha256": digest(p.read_bytes()), "bytes": p.stat().st_size} for p in classes]
            result.append({"path": str(path), "kind": "classes-directory", "classes": rows})
        else:
            raise ValueError("Missing actual compiler dependency: " + str(path))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--classpath-metadata", type=Path, required=True)
    parser.add_argument("--javac", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--java8", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    project = args.project.resolve()
    output = args.output.resolve()
    if output == project or project in output.parents:
        raise ValueError("Packet output must be outside the source checkout")
    output.mkdir(parents=True, exist_ok=False)
    commands = []

    def run(command):
        commands.append([str(x) for x in command])
        (output / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
        index = len(commands)
        with (output / ("command-%02d.log" % index)).open("wb") as log:
            subprocess.run(commands[-1], cwd=project, stdout=log, stderr=subprocess.STDOUT, check=True)

    metadata = json.loads(args.classpath_metadata.read_text())
    if metadata["release"] != 17 or metadata["producer"] != ":engine-core:compileJava":
        raise ValueError("Require modern current production compiler metadata")
    source_root = (project / "engine-core/src/main/java").resolve()
    if Path(metadata["sourceRoot"]).resolve() != source_root:
        raise ValueError("Metadata source owner differs")
    source_paths = sorted(source_root.rglob("*.java"))
    if [str(p.resolve()) for p in source_paths] != sorted(metadata["sources"]):
        raise ValueError("Metadata omits/adds canonical production sources")
    classpath = metadata["classpath"]
    if classpath.split(os.pathsep) != metadata["compileClasspath"]:
        raise ValueError("Classpath metadata mismatch")
    before_classpath = classpath_receipt(metadata["compileClasspath"])
    (output / "classpath-receipt.json").write_text(json.dumps(before_classpath, indent=2) + "\n")
    shutil.copyfile(args.classpath_metadata, output / "gradle-classpath-metadata.json")
    selected_file = project / "scripts/core-var-port/selected-paths.txt"
    selected = selected_file.read_text().splitlines()
    request = json.loads((project / "scripts/core-var-port/request.json").read_text())
    if selected != request["selectedOwners"] or len(selected) != 15 or len(set(selected)) != 15:
        raise ValueError("Selection differs from approved request")
    before = {str(p.relative_to(source_root)): p.read_bytes() for p in source_paths}
    if {name: digest(before[name]) for name in selected} != request["selectedPreimageSha256"]:
        raise ValueError("Approved selected preimages changed; refresh request before attribution")
    snapshot = output / "attribution-source"; snapshot.mkdir()
    for name, blob in before.items():
        target = snapshot / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(blob)
    classes = output / "tool-classes"; classes.mkdir()
    tool_names = ["build-logic/src/main/java/dev/openallay/build/AttributedVarTypes.java",
                 "build-logic/src/main/java/dev/openallay/build/CanonicalVarTypePort.java",
                 "scripts/core-var-port/CanonicalVarTypePortFixture.java",
                 "scripts/rhino-language-port/RhinoVarPort.java",
                 "scripts/rhino-language-port/RhinoVarPortFixture.java"]
    tools = [project / name for name in tool_names]
    run([args.javac, "--release", "17", "-encoding", "UTF-8", "-d", classes, *tools])
    run([args.java, "-cp", classes, "dev.openallay.tools.rhino.RhinoVarPortFixture"])
    run([args.java, "-cp", classes, "dev.openallay.build.CanonicalVarTypePortFixture", output / "fixture", args.java, args.java8])
    sites_path = output / "attributed-var-sites.tsv"
    report_path = output / "attribution-report.json"
    run([args.java, "-cp", classes, "dev.openallay.build.CanonicalVarTypePort", snapshot, classpath,
         selected_file, sites_path, report_path])
    report = json.loads(report_path.read_text()); sites = read_sites(sites_path)
    if report["sources"] != len(before) or set(report["selectedParsedCounts"]) != set(selected):
        raise ValueError("Compiler parsed owner closure differs")
    if report["rejectedForms"] or report["convertedSites"] != sum(report["selectedParsedCounts"].values()):
        raise ValueError("Incomplete fail-closed compiler conversion")
    for name in selected:
        if report["selectedParsedCounts"][name] != len(sites.get(name, [])):
            raise ValueError("Exact parsed owner counts differ")
    if set(sites) - set(selected):
        raise ValueError("Unexpected converted owner")
    after = dict(before)
    for name, values in sites.items():
        after[name] = replace_sites(before[name], values)
        (snapshot / name).write_bytes(after[name])
    # Real all-source modern compiler re-attribution of converted canonical bytes.
    run([args.java, "-cp", classes, "dev.openallay.build.CanonicalVarTypePort", snapshot, classpath,
         selected_file, output / "post-var-sites.tsv", output / "post-attribution-report.json"])
    post_report = json.loads((output / "post-attribution-report.json").read_text())
    if post_report["convertedSites"] != 0 or post_report["rejectedForms"]:
        raise ValueError("Converted owner retains inferred variables")
    if before_classpath != classpath_receipt(metadata["compileClasspath"]):
        raise ValueError("Compiler classpath changed during attribution")
    if before != {str(p.relative_to(source_root)): p.read_bytes() for p in sorted(source_root.rglob("*.java"))}:
        raise ValueError("Canonical source checkout changed during attribution")
    packet = output / "source-packet"; packet.mkdir()
    patch = ""; records = []
    for name in sorted(sites):
        path = "engine-core/src/main/java/" + name
        old = before[name]; new = after[name]
        for side, blob in [("pre", old), ("post", new)]:
            target = packet / side / path; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(blob)
        patch += "".join(difflib.unified_diff(old.decode().splitlines(True), new.decode().splitlines(True),
                    fromfile="a/"+path, tofile="b/"+path))
        records.append({"path": path, "pre_bytes": len(old), "pre_sha256": digest(old),
                        "post_bytes": len(new), "post_sha256": digest(new), "parsedVarSites": len(sites[name])})
    (packet / "source.patch").write_bytes(patch.encode("utf-8"))
    manifest = {"scope": "parsed VAR explicit inferred type edits only; canonical class names and algorithms unchanged",
                "source_count": len(before), "selectedOwnerCount": len(selected),
                "selectedParsedCounts": report["selectedParsedCounts"], "site_count": report["convertedSites"],
                "patch_sha256": digest(patch.encode()), "patch_bytes": len(patch.encode()), "files": records,
                "allSourcePreimage": [{"path": name, "sha256": digest(blob), "bytes": len(blob)} for name, blob in sorted(before.items())],
                "toolSources": [{"path": name, "sha256": digest((project/name).read_bytes())} for name in tool_names],
                "classpathReceiptSha256": digest((output/"classpath-receipt.json").read_bytes()),
                "attributionReportSha256": digest(report_path.read_bytes()),
                "compilerOracle": "genuine public compiler original release17 vs converted release8 fixture, separate real Java8 process",
                "modernAllSourceAttribution": True, "wholeRuntimeJava8Acceptance": False,
                "requiredRootAcceptance": ["hash and patch review", "full modern engine-core tests", "selected affected control/bridge fixtures"]}
    (packet / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Review-only canonical packet: " + str(packet))


if __name__ == "__main__":
    main()
