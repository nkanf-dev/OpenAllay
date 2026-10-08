#!/usr/bin/env python3
"""Exact SQLite game-runtime role projection. Original upstream remains a tooling artifact."""
import hashlib
import json
from pathlib import Path
import zipfile

POLICY_PATH = Path(__file__).resolve().with_name("sqlite-runtime-role-policy.json")
COORDINATE = "org.xerial:sqlite-jdbc:3.50.3.0"
UPSTREAM_SHA256 = "a3f53a2aa15ae9425a9e793bbe9c8e5288febeb4b65ef5c1a4e80d4c2045cf08"

EXCLUDED_ROLES = {
    "META-INF/versions/9/org/sqlite/nativeimage/SqliteJdbcFeature.class": "graalvm-hosted-build",
    "META-INF/versions/9/org/sqlite/nativeimage/SqliteJdbcFeature$1.class": "graalvm-hosted-build",
    "META-INF/versions/9/org/sqlite/nativeimage/SqliteJdbcFeature$SqliteJdbcFeatureException.class": "graalvm-hosted-build",
    "META-INF/versions/9/module-info.class": "jpms-metadata",
    "META-INF/native-image/org.xerial/sqlite-jdbc/native-image.properties": "graalvm-hosted-registration",
}

def digest(blob): return hashlib.sha256(blob).hexdigest()

def project_sqlite_runtime(archive, coordinate, policy_path=POLICY_PATH):
    """Return exact game entry bytes and a role/preservation receipt; never modify the upstream JAR."""
    archive = Path(archive)
    if coordinate != COORDINATE: raise ValueError("SQLite projection coordinate differs")
    policy = json.loads(Path(policy_path).read_text())
    if policy["coordinate"] != COORDINATE or policy["upstream"]["sha256"] != UPSTREAM_SHA256:
        raise ValueError("SQLite policy publication identity differs")
    excluded = {entry["path"]: entry for entry in policy["excluded_game_entries"]}
    expected = {entry["path"]: entry for entry in policy["preserved_entries"]}
    if {name: entry["role"] for name, entry in excluded.items()} != EXCLUDED_ROLES:
        raise ValueError("SQLite excluded runtime/tooling roles differ from the fixed policy")
    if len(excluded) != 5 or len(expected) != len(policy["preserved_entries"]) or set(excluded).intersection(expected):
        raise ValueError("SQLite role policy shape differs")
    raw = archive.read_bytes()
    with zipfile.ZipFile(archive) as sources:
        names = [entry.filename for entry in sources.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)): raise ValueError("Duplicate SQLite upstream entry")
        actual_mr = {name for name in names if name.startswith("META-INF/versions/")}
        expected_mr = {name for name in excluded if name.startswith("META-INF/versions/")}
        if actual_mr != expected_mr: raise ValueError("Unknown or missing SQLite multi-release entry: " + str(sorted(actual_mr ^ expected_mr)))
        if set(names) != set(expected).union(excluded): raise ValueError("Unknown or missing pinned SQLite publication entry")
        if len(raw) != policy["upstream"]["bytes"] or digest(raw) != UPSTREAM_SHA256:
            raise ValueError("SQLite upstream publication bytes differ")
        kept = {}; omitted = []; preserved = []; base_classes = []; natives = []
        for name in names:
            blob = sources.read(name); record = excluded.get(name, expected.get(name))
            if len(blob) != record["bytes"] or digest(blob) != record["sha256"]:
                raise ValueError("SQLite entry preimage differs: " + name)
            proof = {"path": name, "bytes": len(blob), "sha256": digest(blob)}
            if name in excluded:
                proof["excludedRole"] = record["role"]
                proof["retainedOriginalToolingCoordinate"] = COORDINATE
                proof["retainedOriginalToolingSha256"] = UPSTREAM_SHA256
                omitted.append(proof); continue
            kept[name] = blob; preserved.append(proof)
            if name.endswith(".class"):
                if blob[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(blob[6:8], "big") != 52:
                    raise ValueError("Pinned SQLite game runtime class is not genuine Java8: " + name)
                base_classes.append(proof)
            if name.startswith("org/sqlite/native/"): natives.append(proof)
    if len(base_classes) != policy["preserved_base_class_count"] or len(natives) != policy["preserved_native_payload_count"]:
        raise ValueError("SQLite runtime class/native payload closure differs")
    if "META-INF/native-image/org.xerial/sqlite-jdbc/native-image.properties" in kept:
        raise ValueError("Dangling hosted native-image registration in game runtime")
    receipt = {"scope": "exact pinned SQLite ordinary JVM8 game-runtime role projection",
        "coordinate": COORDINATE, "originalToolingArtifact": {"sha256": UPSTREAM_SHA256,
            "bytes": len(raw), "retained": True, "reference": COORDINATE},
        "excludedEntries": omitted, "preservedEntries": preserved,
        "preservedBaseClasses": len(base_classes), "preservedNativePayloads": len(natives),
        "bytecodeEdits": False, "nativeRebuild": False, "upstreamDowngrade": False}
    return kept, receipt

def audit_projected_entries(entries, receipt):
    """Verify packer hook entry bytes immediately before relocation/normal packaging metadata handling."""
    expected = {entry["path"]: entry for entry in receipt["preservedEntries"]}
    if set(entries) != set(expected): raise ValueError("Projected SQLite game entry set differs")
    for name, blob in entries.items():
        if len(blob) != expected[name]["bytes"] or digest(blob) != expected[name]["sha256"]:
            raise ValueError("Projected SQLite game entry changed: " + name)
    return True
