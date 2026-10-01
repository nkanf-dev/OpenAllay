#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
manifest="${OPENALLAY_CONTENT_PROFILE:-$repository_root/scripts/dev-content-profile.json}"
target="${OPENALLAY_CONTENT_MODS_DIR:-$repository_root/fabric/run/mods}"
operation="${1:-install}"

case "$operation" in
  install|verify) ;;
  *)
    echo "Usage: $0 [install|verify]" >&2
    exit 2
    ;;
esac

mkdir -p "$target"

while IFS=$'\t' read -r project file url expected_sha256; do
  destination="$target/$file"
  if [[ "$operation" == "install" && ! -f "$destination" ]]; then
    temporary="$(mktemp "$target/.openallay-profile.XXXXXX")"
    trap 'rm -f "$temporary"' EXIT
    curl --fail --location --silent --show-error \
      --user-agent 'OpenAllay/0.2.x development (github.com/nkanf-dev/OpenAllay)' \
      "$url" \
      --output "$temporary"
    actual_sha256="$(shasum -a 256 "$temporary" | awk '{print $1}')"
    if [[ "$actual_sha256" != "$expected_sha256" ]]; then
      echo "$project: checksum mismatch" >&2
      exit 1
    fi
    mv "$temporary" "$destination"
    trap - EXIT
  fi

  if [[ ! -f "$destination" ]]; then
    echo "$project: missing $destination" >&2
    exit 1
  fi
  actual_sha256="$(shasum -a 256 "$destination" | awk '{print $1}')"
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    echo "$project: checksum mismatch in $destination" >&2
    exit 1
  fi
  echo "$project: verified $file"
done < <(
  python3 - "$manifest" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    profile = json.load(source)

if set(profile) != {
    "name",
    "loader",
    "minecraftVersion",
    "generatedAt",
    "artifacts",
}:
    raise SystemExit("profile fields do not match schema")
if profile["loader"] != "fabric":
    raise SystemExit("unsupported content profile")

for artifact in profile["artifacts"]:
    print(
        artifact["project"],
        artifact["file"],
        artifact["url"],
        artifact["sha256"],
        sep="\t",
    )
PY
)
