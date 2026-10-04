#!/usr/bin/env bash
set -euo pipefail

repository=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repository"

fail() {
  printf 'Modrinth publication failed: %s\n' "$1" >&2
  exit 1
}

if (( $# != 1 )); then
  fail 'usage: publish-modrinth.sh <v-prefixed-version>'
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

# A profile is only a pin tuple. Require matching built metadata and the existing
# full package/Extension gate before any Modrinth API activity.
OPENALLAY_MINECRAFT_TARGET="$minecraft_target" "$repository/scripts/verify-distribution.sh"

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
    "additional_categories": ["fabric", "neoforge"],
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
    "additional_categories": ["fabric", "neoforge"],
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
  local dependency_project=${3:-}
  local versions_response="$work/${loader}-versions.json"
  local encoded_loaders encoded_versions
  encoded_loaders=$(python3 -c 'import json,sys,urllib.parse; print(urllib.parse.quote(json.dumps([sys.argv[1]])))' "$loader")
  encoded_versions=$(python3 -c 'import json,sys,urllib.parse; print(urllib.parse.quote(json.dumps([sys.argv[1]])))' "$minecraft_version")
  local status
  status=$(api_status GET \
    "$api/project/$project_id/version?loaders=$encoded_loaders&game_versions=$encoded_versions&include_changelog=false" \
    "$versions_response")
  [[ "$status" == 200 ]] || fail "$loader version lookup returned HTTP $status"

  if python3 - "$versions_response" "$version" <<'PY'
import json
import sys
versions = json.load(open(sys.argv[1], encoding="utf-8"))
raise SystemExit(0 if any(v.get("version_number") == sys.argv[2] for v in versions) else 1)
PY
  then
    printf 'modrinth_loader=%s status=already_published version=%s\n' "$loader" "$version"
    return
  fi

  [[ -f "$artifact" ]] || fail "missing $loader artifact: $artifact"
  python3 - "$work/$loader-version.json" "$project_id" "$version" \
    "$minecraft_version" "$loader" "$release_type" "$dependency_project" <<'PY'
import json
import pathlib
import sys

output, project_id, version, game_version, loader, release_type, dependency = sys.argv[1:]
dependencies = []
if dependency:
    dependencies.append({"project_id": dependency, "dependency_type": "required"})
payload = {
    "project_id": project_id,
    "name": f"OpenAllay {version} ({loader.title()})",
    "version_number": version,
    "changelog": f"See https://github.com/nkanf-dev/OpenAllay/releases/tag/v{version}",
    "dependencies": dependencies,
    "game_versions": [game_version],
    "version_type": release_type,
    "loaders": [loader],
    "featured": False,
    "status": "listed",
    "file_parts": ["file"],
    "primary_file": "file",
}
pathlib.Path(output).write_text(json.dumps(payload), encoding="utf-8")
PY

  status=$(api_status POST "$api/version" "$work/$loader-response.json" \
    --form "data=<$work/$loader-version.json;type=application/json" \
    --form "file=@$artifact;type=application/java-archive")
  [[ "$status" == 200 ]] || fail "$loader version creation returned HTTP $status"
  printf 'modrinth_loader=%s status=published version=%s\n' "$loader" "$version"
}

publish_loader fabric \
  "fabric/build/libs/openallay-fabric-${minecraft_version}-${version}.jar" \
  P7dR8mSH
publish_loader neoforge \
  "neoforge/build/libs/openallay-neoforge-${minecraft_version}-${version}.jar"

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
