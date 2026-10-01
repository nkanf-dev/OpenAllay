#!/usr/bin/env bash
set -euo pipefail

mode="${1:-deterministic}"
if (( $# > 0 )); then
  shift
fi

usage() {
  cat <<'EOF'
Usage:
  scripts/run-agent-benchmark.sh deterministic
  scripts/run-agent-benchmark.sh live [options]

Live options:
  --profile <models.json>   Load a credential-free model profile file.
  --profile-id <id>        Select a named profile; defaults to defaultProfileId.
  --repeats <count>        Attempts per selected case (default: 3).
  --cases <id,id,...>      Run an exact comma-separated case subset.
  --include-commands       Include the experimental command benchmark.
  --output <directory>     Retained report directory.

Without --profile, live mode uses OPENALLAY_MODEL_BASE_URL, OPENALLAY_MODEL,
and OPENALLAY_API_KEY. Profile credentials must use credentialRef=env:NAME.
EOF
}

case "$mode" in
  -h|--help)
    usage
    exit 0
    ;;
  deterministic)
    if (( $# != 0 )); then
      usage >&2
      exit 2
    fi
    exec ./gradlew :common:test \
      --tests 'dev.openallay.benchmark.*' \
      --tests 'dev.openallay.model.live.LiveAgentBenchmarkAcceptanceTest.fixtureSelectionDeclaresEveryDefaultCoverageGap' \
      --rerun-tasks \
      --max-workers=1
    ;;
  live)
    profile_file=""
    profile_id=""
    repeats="${OPENALLAY_BENCHMARK_REPEATS:-3}"
    cases="${OPENALLAY_BENCHMARK_CASES:-}"
    include_commands="${OPENALLAY_BENCHMARK_INCLUDE_COMMANDS:-false}"
    output="${OPENALLAY_BENCHMARK_OUTPUT:-}"
    while (( $# > 0 )); do
      case "$1" in
        --profile)
          (( $# >= 2 )) || { usage >&2; exit 2; }
          profile_file=$2
          shift 2
          ;;
        --profile-id)
          (( $# >= 2 )) || { usage >&2; exit 2; }
          profile_id=$2
          shift 2
          ;;
        --repeats)
          (( $# >= 2 )) || { usage >&2; exit 2; }
          repeats=$2
          shift 2
          ;;
        --cases)
          (( $# >= 2 )) || { usage >&2; exit 2; }
          cases=$2
          shift 2
          ;;
        --include-commands)
          include_commands=true
          shift
          ;;
        --output)
          (( $# >= 2 )) || { usage >&2; exit 2; }
          output=$2
          shift 2
          ;;
        -h|--help)
          usage
          exit 0
          ;;
        *)
          printf 'Unknown live benchmark option: %s\n' "$1" >&2
          usage >&2
          exit 2
          ;;
      esac
    done
    [[ "$repeats" =~ ^[1-9][0-9]*$ ]] \
      || { echo "--repeats must be a positive integer" >&2; exit 2; }
    if [[ -n "$profile_file" ]]; then
      [[ -f "$profile_file" ]] \
        || { echo "Benchmark profile does not exist: $profile_file" >&2; exit 2; }
      export OPENALLAY_BENCHMARK_PROFILE_FILE="$profile_file"
      if [[ -n "$profile_id" ]]; then
        export OPENALLAY_BENCHMARK_PROFILE_ID="$profile_id"
      fi
    else
      [[ -z "$profile_id" ]] \
        || { echo "--profile-id requires --profile" >&2; exit 2; }
      : "${OPENALLAY_MODEL_BASE_URL:?Set OPENALLAY_MODEL_BASE_URL, including the API version path}"
      : "${OPENALLAY_MODEL:?Set OPENALLAY_MODEL}"
      : "${OPENALLAY_API_KEY:?Set OPENALLAY_API_KEY in the environment}"
    fi
    export OPENALLAY_LIVE_AGENT_BENCHMARK=true
    export OPENALLAY_MODEL_PROTOCOL="${OPENALLAY_MODEL_PROTOCOL:-OPENAI_CHAT}"
    export OPENALLAY_BENCHMARK_REPEATS="$repeats"
    export OPENALLAY_BENCHMARK_CASES="$cases"
    export OPENALLAY_BENCHMARK_INCLUDE_COMMANDS="$include_commands"
    if [[ -n "$output" ]]; then
      export OPENALLAY_BENCHMARK_OUTPUT="$output"
    fi
    export OPENALLAY_PRODUCT_COMMIT="${OPENALLAY_PRODUCT_COMMIT:-$(git rev-parse HEAD)}"
    exec ./gradlew-curl :common:test \
      --tests 'dev.openallay.model.live.LiveAgentBenchmarkAcceptanceTest' \
      --rerun-tasks \
      --max-workers=1
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
