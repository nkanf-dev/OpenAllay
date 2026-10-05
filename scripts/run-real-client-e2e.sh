#!/usr/bin/env bash
set -euo pipefail

loader="${1:-}"
if [[ "$loader" != "fabric" && "$loader" != "neoforge" ]]; then
  echo "usage: $0 fabric|neoforge" >&2
  exit 2
fi

fixture_port="${OPENALLAY_E2E_FIXTURE_PORT:-18765}"
model_mode="${OPENALLAY_E2E_MODEL_MODE:-client}"
use_existing_profile="${OPENALLAY_E2E_USE_EXISTING_PROFILE:-false}"
profile_source="${OPENALLAY_E2E_PROFILE_SOURCE:-}"
if [[ "$model_mode" != "client" && "$model_mode" != "server" ]]; then
  echo "OPENALLAY_E2E_MODEL_MODE must be client or server" >&2
  exit 2
fi
run_dir="$loader/runs/client"
report="${OPENALLAY_E2E_REPORT:-$PWD/build/e2e/$loader-real-client.json}"
trace="${OPENALLAY_E2E_TRACE:-$report.trace.json}"
mkdir -p "$run_dir/config/openallay" "$(dirname "$report")"
model_config="$run_dir/config/openallay/models.json"
server_model_config="$run_dir/config/openallay/server-model.json"
config_backup_dir="$(mktemp -d)"
had_model_config=false
had_server_model_config=false
if [[ -f "$model_config" ]]; then
  cp "$model_config" "$config_backup_dir/models.json"
  had_model_config=true
fi
if [[ -f "$server_model_config" ]]; then
  cp "$server_model_config" "$config_backup_dir/server-model.json"
  had_server_model_config=true
fi
for credential_file in credentials.sqlite3 credentials.sqlite3-wal credentials.sqlite3-shm; do
  if [[ -f "$run_dir/config/openallay/$credential_file" ]]; then
    cp "$run_dir/config/openallay/$credential_file" "$config_backup_dir/$credential_file"
  fi
done
fixture_pid=""
cleanup() {
  if [[ -n "$fixture_pid" ]]; then
    kill "$fixture_pid" 2>/dev/null || true
  fi
  if [[ "$had_model_config" == true ]]; then
    cp "$config_backup_dir/models.json" "$model_config"
  else
    rm -f "$model_config"
  fi
  if [[ "$had_server_model_config" == true ]]; then
    cp "$config_backup_dir/server-model.json" "$server_model_config"
  else
    rm -f "$server_model_config"
  fi
  # The temporary E2E profile intentionally references no local secrets. Startup cleanup may
  # therefore collect the ordinary development profile's rows, so restore the whole SQLite
  # database triplet after the graphical client has closed.
  for credential_file in credentials.sqlite3 credentials.sqlite3-wal credentials.sqlite3-shm; do
    rm -f "$run_dir/config/openallay/$credential_file"
    if [[ -f "$config_backup_dir/$credential_file" ]]; then
      cp "$config_backup_dir/$credential_file" "$run_dir/config/openallay/$credential_file"
    fi
  done
  rm -rf "$config_backup_dir"
}
trap cleanup EXIT INT TERM

if [[ "$use_existing_profile" == "true" ]]; then
  if [[ "$model_mode" != "client" ]]; then
    echo "existing-profile E2E currently supports client model mode only" >&2
    exit 2
  fi
  if [[ -n "$profile_source" ]]; then
    if [[ ! -f "$profile_source/models.json" ]]; then
      echo "OPENALLAY_E2E_PROFILE_SOURCE must contain models.json" >&2
      exit 2
    fi
    cp "$profile_source/models.json" "$model_config"
    for credential_file in credentials.sqlite3 credentials.sqlite3-wal credentials.sqlite3-shm; do
      if [[ -f "$profile_source/$credential_file" ]]; then
        cp "$profile_source/$credential_file" "$run_dir/config/openallay/$credential_file"
      fi
    done
  elif [[ ! -f "$model_config" ]]; then
    echo "existing-profile E2E requires a configured models.json" >&2
    exit 2
  fi
else
  python3 - "$model_config" "$fixture_port" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
path.write_text(json.dumps({
    "defaultProfileId": "e2e-fixture",
    "profiles": [{
        "id": "e2e-fixture",
        "displayName": "OpenAllay E2E Fixture",
        "enabled": True,
        "protocol": "openai_chat",
        "baseUrl": f"http://127.0.0.1:{sys.argv[2]}/v1/",
        "model": "openallay-e2e-fixture",
        "credentialRef": "env:OPENALLAY_E2E_FIXTURE_KEY",
        "contextWindowTokens": 256000,
        "maxOutputTokens": 8192,
        "connectTimeoutSeconds": 10,
        "requestTimeoutSeconds": 120,
    }],
}), encoding="utf-8")
PY
  if [[ "$model_mode" == "server" ]]; then
    python3 - "$server_model_config" "$fixture_port" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
path.write_text(json.dumps({
    "enabled": True,
    "protocol": "openai_chat",
    "baseUrl": f"http://127.0.0.1:{sys.argv[2]}/v1/",
    "model": "openallay-e2e-server-fixture",
    "apiKeyEnv": "OPENALLAY_E2E_FIXTURE_KEY",
    "contextWindowTokens": 256000,
    "maxOutputTokens": 8192,
    "connectTimeoutSeconds": 10,
    "requestTimeoutSeconds": 120,
}), encoding="utf-8")
PY
  fi
  export OPENALLAY_E2E_FIXTURE_KEY="$(python3 -c 'import secrets; print(secrets.token_hex(16))')"

  python3 scripts/e2e-model-fixture.py --port "$fixture_port" &
  fixture_pid=$!
fi

echo "The harness is opt-in and will open a graphical Minecraft client."
echo "Connect it to a test world/server; the probe starts after a player exists."
client_args=()
if [[ -n "${OPENALLAY_E2E_QUICK_PLAY_WORLD:-}" ]]; then
  if [[ "$loader" == "fabric" ]]; then
    client_args=("--args=--quickPlaySingleplayer \"$OPENALLAY_E2E_QUICK_PLAY_WORLD\"")
  else
    client_args=("-Dopenallay.e2e.quickPlayWorld=$OPENALLAY_E2E_QUICK_PLAY_WORLD")
  fi
fi
gradle_command=(./gradlew-curl ":$loader:runClient" --max-workers=1)
if [[ ${#client_args[@]} -gt 0 ]]; then
  gradle_command+=("${client_args[@]}")
fi
gradle_command+=(
  -Dopenallay.e2e.enabled=true \
  -Dopenallay.e2e.question="${OPENALLAY_E2E_QUESTION:-请用 JavaScript 查询铁块配方，读取实际库存并判断材料是否足够，保留真实配方引用。}" \
  -Dopenallay.e2e.report="$report" \
  -Dopenallay.e2e.trace="$trace" \
  -Dopenallay.e2e.scenario="${OPENALLAY_E2E_SCENARIO:-live-rhino-crafting}" \
  -Dopenallay.e2e.session="${OPENALLAY_E2E_SESSION:-e2e}" \
  -Dopenallay.e2e.modelMode="$model_mode" \
  -Dopenallay.e2e.historySeedRequests="${OPENALLAY_E2E_HISTORY_SEED_REQUESTS:-0}" \
  -Dopenallay.e2e.screenshotRoot="${OPENALLAY_E2E_SCREENSHOT_ROOT:-}" \
  -Dopenallay.e2e.shutdownAfterScreenshots="${OPENALLAY_E2E_SHUTDOWN_AFTER_SCREENSHOTS:-false}" \
  -Dopenallay.e2e.shutdown="${OPENALLAY_E2E_SHUTDOWN:-true}"
)
"${gradle_command[@]}"

test -s "$report"
if [[ "$use_existing_profile" == "true" ]]; then
  test -s "$trace"
fi
python3 - "$report" "$trace" "$use_existing_profile" <<'PY'
import json, pathlib, sys
report = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if report.get("outcome") != "COMPLETED":
    raise SystemExit("E2E did not complete: " + json.dumps({
        "outcome": report.get("outcome"),
        "failureCode": report.get("failureCode"),
        "failureMessage": report.get("failureMessage"),
    }, ensure_ascii=False))
metrics = report.get("semanticMetrics", {})
scenario = report.get("scenario")
if sys.argv[3] == "true":
    trace = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
    if trace.get("requestId") != report.get("requestId"):
        raise SystemExit("E2E trace request does not match the report")
    if trace.get("finalState") != report.get("outcome"):
        raise SystemExit("E2E trace terminal state does not match the report")
    event_types = [event.get("type") for event in trace.get("events", [])]
    required = {"request", "state", "model_request", "model_turn", "tool_call", "tool_result"}
    if not required.issubset(event_types):
        raise SystemExit("E2E trace is incomplete: " + repr(sorted(required - set(event_types))))
    if "[REDACTED]" not in pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"):
        # Stored credentials normally never enter a trace, so redaction markers are optional.
        pass
if scenario.startswith("live-rhino-"):
    tools = report.get("toolIds", [])
    if "openallay:run_javascript" not in tools:
        raise SystemExit("Live Rhino E2E did not execute run_javascript")
    if "openallay:calculate_craftability" in tools:
        raise SystemExit("Live Rhino E2E exposed the retired craftability Tool")
    maximum = int(__import__("os").environ.get(
        "OPENALLAY_E2E_MAX_JAVASCRIPT_CALLS", "0"))
    javascript_calls = sum(
        1 for tool_id in tools if tool_id == "openallay:run_javascript")
    if maximum and javascript_calls > maximum:
        raise SystemExit(
            f"Live Rhino E2E used {javascript_calls} JavaScript calls; maximum is {maximum}")
    required_module = __import__("os").environ.get(
        "OPENALLAY_E2E_REQUIRED_MODULE", "")
    if required_module:
        modules = {
            module
            for event in trace.get("events", [])
            if event.get("type") == "tool_result"
            for module in (
                event.get("payload", {})
                .get("result", {})
                .get("value", {})
                .get("modules", [])
            )
        }
        if required_module not in modules:
            raise SystemExit(
                "Live Rhino E2E did not load required module: " + required_module)
    raise SystemExit(0)
raise SystemExit("Unsupported current E2E scenario: " + str(scenario))
PY
echo "E2E report: $report"
if [[ "$use_existing_profile" == "true" ]]; then
  echo "E2E trace: $trace"
fi
