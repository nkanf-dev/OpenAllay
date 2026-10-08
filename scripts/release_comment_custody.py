"""Admit only four reviewed raw source pairs from the same-line class-property Javadoc repair."""
import hashlib
import json
from pathlib import Path
import re

POLICY_PATH = "distribution/release-comment-deltas.json"
PATHS = frozenset((
    "engine-core/src/main/java/dev/openallay/benchmark/BenchmarkTraceAudit.java",
    "engine-core/src/main/java/dev/openallay/recipe/RecipeCatalogStatus.java",
    "engine-core/src/main/java/dev/openallay/recipe/RecipeProviderReadiness.java",
    "engine-core/src/main/java/dev/openallay/recipe/config/RecipeClientConfig.java",
))


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def policy(root):
    value = json.loads((root / POLICY_PATH).read_text())
    require(set(value) == {"reviewedPatchSha256", "files"} and re.fullmatch(r"[0-9a-f]{64}", value["reviewedPatchSha256"]),
            "Exact reviewed comment-delta policy required")
    rows = value["files"]
    require(type(rows) is list and len(rows) == 4 and {row["path"] for row in rows} == PATHS,
            "Only four exact reviewed comment owners are admitted")
    for row in rows:
        require(set(row) == {"path", "beforeSha256", "afterSha256", "noncommentCharLineColumnSha256"} and
                all(re.fullmatch(r"[0-9a-f]{64}", row[key]) for key in ("beforeSha256", "afterSha256", "noncommentCharLineColumnSha256")),
                "Exact raw-before/raw-after/reviewed-character-coordinate custody required")
        require(row["beforeSha256"] != row["afterSha256"], "Comment repair must have distinct reviewed source bytes")
    return {row["path"]: row for row in rows}


def verify_pair(root, path, original, current):
    """Hash equality certifies this reviewed pair; never parse or generalize Java comments."""
    rows = policy(root)
    require(path in rows, "Unknown comment/source owner cannot be reused")
    row = rows[path]
    require(sha(original) == row["beforeSha256"] and sha(current) == row["afterSha256"],
            "Source differs from the exact reviewed comment-only before/after pair: " + path)
    return {"path": path, "beforeSha256": row["beforeSha256"], "afterSha256": row["afterSha256"],
            "reviewedNoncommentCharLineColumnSha256": row["noncommentCharLineColumnSha256"]}
