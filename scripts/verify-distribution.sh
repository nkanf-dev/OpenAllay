#!/usr/bin/env bash
set -euo pipefail

repository=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
version=$(sed -n 's/^version=//p' "$repository/gradle.properties")
# Match the target used for the build; callers that build with -PminecraftTarget
# must pass the same value here. Keep the existing optional directory argument.
minecraft_target=${OPENALLAY_MINECRAFT_TARGET-26.2}
minecraft_version=$(python3 "$repository/scripts/minecraft-target.py" \
  --target "$minecraft_target" --property minecraft_version)
minecraft_version_range=$(python3 "$repository/scripts/minecraft-target.py" \
  --target "$minecraft_target" --property minecraft_version_range)

fail() {
  printf 'distribution verification failed: %s\n' "$1" >&2
  exit 1
}

test -n "$version" || fail 'version is missing from gradle.properties'
test -n "$minecraft_version" || fail 'minecraft_version is missing from the selected target profile'

fabric_name="openallay-fabric-${minecraft_version}-${version}.jar"
neoforge_name="openallay-neoforge-${minecraft_version}-${version}.jar"

if (( $# > 1 )); then
  fail 'usage: verify-distribution.sh [staged-release-directory]'
fi

if (( $# == 1 )); then
  distribution=$(cd "$1" && pwd)
  shopt -s nullglob
  staged_jars=("$distribution"/*.jar)
  shopt -u nullglob
  (( ${#staged_jars[@]} == 2 )) \
    || fail "staged release must contain exactly two JARs"
  fabric_jar="$distribution/$fabric_name"
  neoforge_jar="$distribution/$neoforge_name"
  test -f "$fabric_jar" && test -f "$neoforge_jar" \
    || fail "staged release must contain only $fabric_name and $neoforge_name"
else
  fabric_jar="$repository/fabric/build/libs/$fabric_name"
  neoforge_jar="$repository/neoforge/build/libs/$neoforge_name"
fi

verify_zip() {
  local jar=$1
  local listing
  local legacy_namespace='tome''wisp'
  test -s "$jar" || fail "missing production artifact: $jar"
  unzip -tq "$jar" >/dev/null || fail "invalid JAR archive: $jar"
  listing=$(jar tf "$jar")
  if grep -Eiq "(^|/)${legacy_namespace}(/|\\.|$)|^dev/${legacy_namespace}/" <<< "$listing"; then
    fail "legacy package branding is present in $jar"
  fi
  for entry in \
    'dev/openallay/OpenAllayBootstrap.class' \
    'dev/openallay/guide/history/SqliteGuideHistoryStore.class' \
    'dev/openallay/guide/semantic/SemanticMessageParser.class'; do
    grep -Fqx "$entry" <<< "$listing" \
      || fail "required product class $entry is missing from $jar"
  done
  for dependency in \
    'commonmark-0.28.0.jar' \
    'commonmark-ext-gfm-tables-0.28.0.jar' \
    'sqlite-jdbc-3.50.3.0.jar'; do
    grep -Fq "META-INF/jars/$dependency" <<< "$listing" \
      || grep -Fq "META-INF/jarjar/$dependency" <<< "$listing" \
      || fail "required product dependency $dependency is missing from $jar"
  done
}

verify_zip "$fabric_jar"
verify_zip "$neoforge_jar"

python3 - "$fabric_jar" "$version" "$minecraft_version" <<'PY'
import json
import sys
import zipfile

path, version, minecraft_version = sys.argv[1:]
with zipfile.ZipFile(path) as archive:
    metadata = json.loads(archive.read("fabric.mod.json"))
assert metadata["id"] == "openallay", metadata
assert metadata["name"] == "OpenAllay", metadata
assert metadata["version"] == version, metadata
assert metadata["environment"] == "*", metadata
assert metadata["depends"]["minecraft"] == "~" + minecraft_version, metadata
PY

python3 - "$neoforge_jar" "$version" "$minecraft_version_range" <<'PY'
import re
import sys
import zipfile

path, version, minecraft_version_range = sys.argv[1:]
with zipfile.ZipFile(path) as archive:
    metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
mods = metadata.split("[[mods]]", 1)[1].split("[[dependencies.", 1)[0]
values = dict(
    re.findall(
        r'(?m)^\s*(modId|displayName|version)\s*=\s*"([^"]+)"', mods
    )
)
assert values.get("modId") == "openallay", values
assert values.get("displayName") == "OpenAllay", values
assert values.get("version") == version, values
minecraft_dependencies = []
for block in metadata.split("[[dependencies.openallay]]")[1:]:
    block = block.split("[[", 1)[0]
    dependency = dict(re.findall(r'(?m)^\s*(modId|versionRange)\s*=\s*"([^"]+)"', block))
    if dependency.get("modId") == "minecraft":
        minecraft_dependencies.append(dependency)
assert len(minecraft_dependencies) == 1, minecraft_dependencies
assert minecraft_dependencies[0].get("versionRange") == minecraft_version_range, minecraft_dependencies
PY

python3 "$repository/scripts/verify-bundled-extensions.py" "$fabric_jar" "$neoforge_jar"

printf 'fabric_artifact=%s\n' "$fabric_name"
printf 'neoforge_artifact=%s\n' "$neoforge_name"
printf 'distribution_verification=passed\n'
