#!/usr/bin/env bash
set -euo pipefail

repository=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
version=$(sed -n 's/^version=//p' "$repository/gradle.properties")
minecraft_target=${OPENALLAY_MINECRAFT_TARGET-26.2}
minecraft_version=$(python3 "$repository/scripts/minecraft-target.py" \
  --target "$minecraft_target" --property minecraft_version)

fail() {
  printf 'distribution verification failed: %s\n' "$1" >&2
  exit 1
}

test -n "$version" || fail 'version is missing from gradle.properties'
test "$minecraft_version" = "$minecraft_target" || fail 'target/profile mismatch'
if (( $# > 1 )); then
  fail 'usage: verify-distribution.sh [staged-release-directory]'
fi

# An unstaged ordinary build is singleton-only. Explicit interval builds must
# pass the same accepted IDs as -PminecraftArtifact. A staged release checks all
# source-accepted families; candidates are never selected here.
arguments=(verify --target "$minecraft_target")
if [[ -n "${OPENALLAY_MINECRAFT_ARTIFACTS:-}" ]]; then
  arguments+=(--families "$OPENALLAY_MINECRAFT_ARTIFACTS")
fi
if (( $# == 1 )); then
  arguments+=("$1")
fi
python3 "$repository/scripts/build-minecraft-artifacts.py" "${arguments[@]}"
printf 'distribution_verification=passed\n'
