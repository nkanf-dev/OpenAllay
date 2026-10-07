#!/usr/bin/env bash
set -euo pipefail

repository=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repository"

fail() {
  printf 'Modrinth publication failed: %s\n' "$1" >&2
  exit 1
}

if (( $# < 1 || $# > 2 )); then
  fail 'usage: publish-modrinth.sh <v-prefixed-version> [staged-release-directory]'
fi

tag=$1
version=${tag#v}
[[ "$tag" == v* && -n "$version" ]] || fail 'version must have a v prefix'
[[ -n "${MODRINTH_TOKEN:-}" ]] || fail 'MODRINTH_TOKEN is required'

configured_version=$(sed -n 's/^version=//p' gradle.properties)
minecraft_target=${OPENALLAY_MINECRAFT_TARGET-26.2}
minecraft_version=$(python3 "$repository/scripts/minecraft-target.py" \
  --target "$minecraft_target" --property minecraft_version)
[[ "$configured_version" == "$version" ]] \
  || fail "tag version $version does not match Gradle version $configured_version"

# Verify every selected artifact before any API activity. A receipt checks final
# staged-byte consistency; reviewed source catalog entries remain the admission.
# Compile/package receipts describe new release builds, never a new game run.
distribution=${2:-release}
publication_arguments=(publication-records "$distribution")
if [[ -n "${OPENALLAY_MINECRAFT_RECEIPT_DIRECTORY:-}" ]]; then
  publication_arguments+=(--receipt-directory "$OPENALLAY_MINECRAFT_RECEIPT_DIRECTORY")
fi
if [[ -n "${OPENALLAY_MINECRAFT_BUILD_RECEIPT_DIRECTORY:-}" ]]; then
  publication_arguments+=(--build-receipt-directory "$OPENALLAY_MINECRAFT_BUILD_RECEIPT_DIRECTORY")
fi
publication_records=$(python3 "$repository/scripts/build-minecraft-artifacts.py" "${publication_arguments[@]}")
# Channel policy belongs to reviewed source. Verify the entire stage, then select
# only real single-mod JARs. Forge 1.12.2 installs through its GitHub ZIP profile.
publication_records=$(python3 - "$repository" "$distribution" "$tag" "$publication_records" <<'PY'
import json
from pathlib import Path
import sys
root, directory, tag, records = sys.argv[1:]
sys.path.insert(0, str(Path(root) / "scripts"))
from release_publication import select_records
selected = select_records(Path(root), Path(directory), json.loads(records), tag, "modrinth")
print(json.dumps(selected, separators=(",", ":")))
PY
)


api=https://api.modrinth.com/v2
slug=openallay
user_agent="nkanf-dev/OpenAllay/${version} (https://github.com/nkanf-dev/OpenAllay)"
work=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/openallay-modrinth.XXXXXX")
trap 'rm -rf "$work"' EXIT

api_status() {
  local method=$1
  local url=$2
  local output=$3
  shift 3
  curl --silent --show-error --output "$output" --write-out '%{http_code}' \
    --request "$method" \
    --header "User-Agent: $user_agent" \
    --header "Authorization: $MODRINTH_TOKEN" \
    "$@" \
    "$url"
}

project_response="$work/project-response.json"
project_status=$(api_status GET "$api/project/$slug" "$project_response")
case "$project_status" in
  200) ;;
  404)
    python3 - "$repository" "$work/project.json" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
output = pathlib.Path(sys.argv[2])
sys.path.insert(0, str(root / "scripts"))
from modrinth_readme import render_readme

body = render_readme((root / "README.md").read_text(encoding="utf-8"))
payload = {
    "project_type": "mod",
    "slug": "openallay",
    "title": "OpenAllay",
    "description": "A modern Minecraft Agent with Skills, Extensions, and data-driven game analysis.",
    "body": body,
    "categories": ["utility"],
    "additional_categories": ["fabric", "forge", "neoforge"],
    "client_side": "required",
    "server_side": "optional",
    "license_id": "MIT",
    "source_url": "https://github.com/nkanf-dev/OpenAllay",
    "issues_url": "https://github.com/nkanf-dev/OpenAllay/issues",
    "is_draft": True,
}
output.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
PY
    project_status=$(api_status POST "$api/project" "$project_response" \
      --form "data=<$work/project.json;type=application/json" \
      --form "icon=@common/src/main/resources/assets/openallay/icon.png;type=image/png")
    [[ "$project_status" == 200 ]] \
      || fail "project creation returned HTTP $project_status"
    ;;
  *) fail "project lookup returned HTTP $project_status" ;;
esac

project_id=$(python3 - "$project_response" <<'PY'
import json
import sys
value = json.load(open(sys.argv[1], encoding="utf-8")).get("id")
if not value:
    raise SystemExit(1)
print(value)
PY
) || fail 'project response did not contain an ID'

project_review_status=$(python3 - "$project_response" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", "unknown"))
PY
) || fail 'project response did not contain a readable status'

# Keep the Modrinth page synchronized with the player-facing README on every
# release. The project may be invisible to anonymous callers while it is still
# a draft or in moderation, so this update uses the authenticated project ID.
python3 - "$repository" "$work/project-update.json" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
output = pathlib.Path(sys.argv[2])
sys.path.insert(0, str(root / "scripts"))
from modrinth_readme import render_readme

body = render_readme((root / "README.md").read_text(encoding="utf-8"))
payload = {
    "description": "A modern Minecraft Agent with Skills, Extensions, and data-driven game analysis.",
    "body": body,
    "categories": ["utility"],
    "additional_categories": ["fabric", "forge", "neoforge"],
    "client_side": "required",
    "server_side": "optional",
    "source_url": "https://github.com/nkanf-dev/OpenAllay",
    "issues_url": "https://github.com/nkanf-dev/OpenAllay/issues",
}
output.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
PY
project_update_status=$(api_status PATCH "$api/project/$project_id" \
  "$work/project-update-response.json" \
  --header 'Content-Type: application/json' \
  --data-binary "@$work/project-update.json")
[[ "$project_update_status" == 204 ]] \
  || fail "project metadata update returned HTTP $project_update_status"

release_type=release
if [[ "$version" == *-* ]]; then
  release_type=alpha
fi

publish_loader() {
  local loader=$1
  local artifact=$2
  local family_id=$3
  local game_versions=$4
  local expected_sha256=$5
  local dependency_project=${6:-}
  local versions_response="$work/${family_id}-versions.json"
  local encoded_loaders encoded_versions
  encoded_loaders=$(python3 -c 'import json,sys,urllib.parse; print(urllib.parse.quote(json.dumps([sys.argv[1]])))' "$loader")
  encoded_versions=$(python3 -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$game_versions")
  local status
  status=$(api_status GET \
    "$api/project/$project_id/version?loaders=$encoded_loaders&game_versions=$encoded_versions&include_changelog=false" \
    "$versions_response")
  [[ "$status" == 200 ]] || fail "$loader version lookup returned HTTP $status"

  # Never treat the same product version for another family/bytes as a match.
  # Refuse mismatched existing releases; do not overwrite delivered artifacts.
  if python3 - "$versions_response" "$version" "$game_versions" "$loader" "$artifact" <<'PY'
import hashlib
import json
from pathlib import Path
import sys
versions = json.load(open(sys.argv[1], encoding="utf-8"))
version, targets, loader, artifact = sys.argv[2:]
matching = [item for item in versions if item.get("version_number") == version]
if not matching:
    raise SystemExit(1)
sha512 = hashlib.sha512(Path(artifact).read_bytes()).hexdigest()
for item in matching:
    if (sorted(item.get("game_versions", [])) != sorted(json.loads(targets))
            or item.get("loaders") != [loader]
            or len(item.get("files", [])) != 1
            or item["files"][0].get("primary") is not True
            or item["files"][0].get("filename") != Path(artifact).name
            or item["files"][0].get("hashes", {}).get("sha512") != sha512):
        raise SystemExit(2)
raise SystemExit(0)
PY
  then
    printf 'modrinth_family=%s status=already_published version=%s\n' "$family_id" "$version"
    return
  else
    local existing_status=$?
    [[ "$existing_status" == 1 ]] || fail "existing $family_id version has different targets/bytes; release is immutable"
  fi

  [[ -f "$artifact" ]] || fail "missing $loader artifact: $artifact"
  python3 - "$work/$family_id-version.json" "$project_id" "$version" \
    "$game_versions" "$loader" "$release_type" "$dependency_project" "$family_id" <<'PY'
import json
import pathlib
import sys

output, project_id, version, game_versions, loader, release_type, dependency, family_id = sys.argv[1:]
dependencies = []
if dependency:
    dependencies.append({"project_id": dependency, "dependency_type": "required"})
payload = {
    "project_id": project_id,
    "name": f"OpenAllay {version} ({family_id})",
    "version_number": version,
    "changelog": f"See https://github.com/nkanf-dev/OpenAllay/releases/tag/v{version}",
    "dependencies": dependencies,
    "game_versions": json.loads(game_versions),
    "version_type": release_type,
    "loaders": [loader],
    "featured": False,
    "status": "listed",
    "file_parts": ["file"],
    "primary_file": "file",
}
pathlib.Path(output).write_text(json.dumps(payload), encoding="utf-8")
PY

  python3 - "$repository" "$artifact" "$expected_sha256" <<'PY'
from importlib.util import module_from_spec, spec_from_file_location
from pathlib import Path
import sys
root, artifact, expected = sys.argv[1:]
sys.path.insert(0, str(Path(root) / "scripts"))
spec = spec_from_file_location("publication_artifact_hash", Path(root) / "scripts/minecraft-artifacts.py")
catalog = module_from_spec(spec)
spec.loader.exec_module(catalog)
if catalog.file_hash(Path(artifact), catalog.MAX_ARTIFACT_BYTES) != expected:
    raise SystemExit("Final staged artifact changed after verification; refusing upload")
PY
  status=$(api_status POST "$api/version" "$work/$family_id-response.json" \
    --form "data=<$work/$family_id-version.json;type=application/json" \
    --form "file=@$artifact;type=application/java-archive")
  [[ "$status" == 200 ]] || fail "$loader version creation returned HTTP $status"
  printf 'modrinth_family=%s status=published version=%s\n' "$family_id" "$version"
}

while IFS=$'\t' read -r loader artifact family_id game_versions artifact_sha256; do
  dependency_project=
  if [[ "$loader" == fabric ]]; then dependency_project=P7dR8mSH; fi
  publish_loader "$loader" "$artifact" "$family_id" "$game_versions" "$artifact_sha256" "$dependency_project"
done < <(python3 -c '
import json, sys
for item in json.loads(sys.argv[1]):
    print("\t".join((item["loader"], item["artifactPath"], item["id"], json.dumps(item["supportedTargets"], separators=(",", ":")), item["artifactSha256"])))
' "$publication_records")

if [[ "$project_review_status" == draft ]]; then
  printf '{"requested_status":"approved"}' > "$work/submit.json"
  submit_status=$(api_status PATCH "$api/project/$project_id" "$work/submit-response.json" \
    --header 'Content-Type: application/json' \
    --data-binary "@$work/submit.json")
  [[ "$submit_status" == 204 ]] \
    || fail "project moderation submission returned HTTP $submit_status"
fi

printf 'modrinth_project_id=%s\n' "$project_id"
printf 'modrinth_project_status_before_release=%s\n' "$project_review_status"
printf 'modrinth_version=%s\n' "$version"
printf 'modrinth_publication=passed\n'
