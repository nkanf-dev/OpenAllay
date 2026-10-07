"""Select source-admitted player assets from verified final-path publication records."""
import hashlib
import json
from pathlib import Path
import re

MAX_ARTIFACT_BYTES = 512 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def file_hash(path):
    require(path.is_file() and not path.is_symlink(), "Missing or symlinked publication asset")
    require(0 < path.stat().st_size <= MAX_ARTIFACT_BYTES, "Publication asset size exceeds limit")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def product_version(root, tag):
    values = re.findall(r"(?m)^version=([^\r\n]+)$", (root / "gradle.properties").read_text())
    require(len(values) == 1 and re.fullmatch(r"[0-9][0-9A-Za-z]*(?:[.+-][0-9A-Za-z]+)*", values[0]),
            "Expected one safe source-owned product version")
    require(tag == "v" + values[0], "Release tag differs from source-owned product version")
    return values[0]


def select_records(root, directory, records, tag, channel):
    """Check the complete stage before selecting a channel. Never admit by suffix alone."""
    require(channel in ("github", "modrinth"), "Unknown publication channel")
    version = product_version(root, tag)
    directory = directory.resolve(strict=True)
    families = json.loads((root / "gradle/minecraft-artifacts.json").read_text())["acceptedFamilies"]
    require(type(records) is list and len(records) == len(families), "Records must cover every accepted family")
    require(len({row["id"] for row in records}) == len(records), "Duplicate publication family")
    indexed = {row["id"]: row for row in records}
    require(set(indexed) == {family["id"] for family in families}, "Unknown or missing publication family")
    expected = {}
    selected = []
    for family in families:
        record = indexed[family["id"]]
        kind, channels = family["artifactKind"], family["publicationChannels"]
        require(kind in ("jar", "zip") and channels in (["github"], ["github", "modrinth"]),
                "Invalid source-owned publication policy")
        require("modrinth" not in channels or kind == "jar", "Modrinth requires an actual single-mod JAR")
        for key in ("loader", "supportedTargets", "artifactKind", "publicationChannels"):
            require(record[key] == family[key], "Publication record differs from source family: " + key)
        filename = family["filenameTemplate"].replace("{version}", version)
        require(re.fullmatch(r"openallay-[A-Za-z0-9.+-]+\." + kind, filename), "Unsafe player asset name/kind")
        require(record.get("filename") == filename, "Publication filename differs from current source version")
        path = Path(record["artifactPath"])
        require(path.is_absolute() and path == directory / filename and path.resolve(strict=True) == path,
                "Publication asset must be the exact final staged path")
        digest = record["artifactSha256"]
        require(type(digest) is str and re.fullmatch(r"[0-9a-f]{64}", digest), "Invalid publication SHA256")
        require(file_hash(path) == digest, "Final staged artifact changed after verification; refusing upload")
        expected[filename] = digest
        if channel in channels:
            selected.append(record)
    observed = {path.name for path in directory.iterdir() if path.suffix in (".jar", ".zip")}
    require(observed == set(expected), "Stage must contain exactly the admitted JAR and ZIP assets")
    checksum_path = directory / "SHA256SUMS"
    require(checksum_path.is_file() and not checksum_path.is_symlink(), "Missing verified SHA256SUMS")
    checksums = {}
    for line in checksum_path.read_text().splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (openallay-[A-Za-z0-9.+-]+\.(?:jar|zip))", line)
        require(match is not None, "Invalid SHA256SUMS player asset entry")
        digest, filename = match.groups()
        require(filename not in checksums, "Duplicate SHA256SUMS entry")
        checksums[filename] = digest
    require(checksums == expected, "SHA256SUMS must bind exactly the complete player asset set")
    require(selected, "No source-admitted assets for publication channel")
    return selected
