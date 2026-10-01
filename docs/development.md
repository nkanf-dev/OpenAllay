# Development

OpenAllay's 0.2.4 release candidate targets Minecraft 26.2 and requires Java 25.
It uses Extension API 0.2.1, guide-history schema 5, and server protocol 5.
Use the checked-in Gradle wrapper; no system Gradle installation is required.

## Build and test

```bash
./gradlew :common:test
./gradlew :fabric:build :neoforge:build
```

For unreliable Maven connections, replace `./gradlew` with `./gradlew-curl`.
The helper extracts failed dependency URLs from Gradle output, downloads them
with curl retries into the ignored `.gradle/curl-mirror` directory, and resumes
the same command. On FLClash, its proxy can be selected explicitly:

```bash
OPENALLAY_CURL_PROXY=socks5h://127.0.0.1:7890 ./gradlew-curl build
```

## Agent benchmark

The versioned benchmark corpus is
`common/src/main/resources/data/openallay/benchmarks/core.json`. Its fixture
identity, required capabilities, prompts, turn budgets, and external canonical
predicates are decoded strictly. Deterministic corpus/verifier coverage is
offline. It also verifies that the local live-benchmark fixture actually
contains and selects its content-profile and inventory-rich cases instead of
silently filtering them:

```bash
scripts/run-agent-benchmark.sh deterministic
```

The live mode is an explicit, billable provider operation. It runs every case
applicable to the `javascript-agent-v2` fixture through the production
`GameGuideAgent`, model protocol adapter, Tool registry, Rhino runtime, Skill
loader, result normalizer, and trace recorder:

```bash
OPENALLAY_MODEL_BASE_URL='https://provider.example/v1/' \
OPENALLAY_MODEL='provider/model-id' \
OPENALLAY_API_KEY='...' \
OPENALLAY_BENCHMARK_REPEATS=3 \
scripts/run-agent-benchmark.sh live
```

The preferred reproducible form reuses the credential-free schema-2
`models.json` format and selects one named profile. Its `credentialRef` must
resolve an environment variable in the benchmark process:

```bash
OPENROUTER_API_KEY='...' \
scripts/run-agent-benchmark.sh live \
  --profile config/openallay/models.json \
  --profile-id openrouter-main \
  --repeats 3
```

`--cases`, `--include-commands`, and `--output` make case selection, the
experimental write-capable fixture, and retained-report location explicit.
The schema-4 live report records the selected profile ID, canonical model
identity, typed per-attempt result, and raw provider-neutral trace without
retaining the credential. A sibling `*-audit.json` file classifies only
unambiguous stable terminal/Tool error codes; unresolved or multi-domain
failures remain `UNRESOLVED` for engineering review.

`OPENALLAY_BENCHMARK_CASES` may contain a comma-separated subset of exact case
IDs. Experimental command cases are excluded unless
`OPENALLAY_BENCHMARK_INCLUDE_COMMANDS=true`. Server routing is exercised by
the deterministic `server-model-routing-v1` GuideService fixture and remains
explicitly outside the local JavaScript/provider fixture. The default local
fixture includes the Farmer's Delight-style food
ranking and recipe/inventory craftability cases as detached, generalized test
data; it does not copy expected answers into model context. Default runs print
an `OPENALLAY_BENCHMARK_SKIPPED` line for every unavailable case and retain a
schema-2 selection plan with its required fixture, skip reason, and missing
capabilities, so fixture coverage cannot shrink silently. Explicitly selected
unavailable cases still fail fast.
Reports under
`build/reports/openallay/benchmarks/` retain corpus
version, commit, redacted provider authority, canonical model ID, complete
provider-neutral Agent traces, success probability, per-attempt counters, and
median model/Tool calls. The audit sidecar retains evidence source, code, event
index, and Tool ID so it can be traced back to the raw report. Wall-clock
values remain trace diagnostics and are not benchmark score inputs. API keys
and authorization data are never written to either file.

## Continuous integration and releases

The `Quality` GitHub Actions workflow runs for pull requests, pushes to `main`,
`mc/**` and `feat/**`, and manual dispatches. It validates automation sources,
tests distribution scripts, prepares the pinned Extension source, runs the clean
common test plus both loader build gate, inspects the Phase 4 and SQLite packaging
contracts, and verifies exactly one production JAR per loader under the OpenAllay
identity. Graphical gameplay and billable model acceptance are separate opt-in
checks.

Releases are created only by pushing an annotated strict-SemVer tag. The tag
must exactly match `version` in `gradle.properties`. A SemVer prerelease suffix,
including `-SNAPSHOT`, is published as a GitHub prerelease and a Modrinth alpha;
a version without a suffix is published as a stable release.

```bash
# After the candidate audit and required checks pass:
git tag -a v0.2.4 -m 'OpenAllay 0.2.4'
git push origin main v0.2.4
```

Pushing `v*` starts the `Release` workflow. It rejects lightweight or non-SemVer
tags, a version mismatch with `gradle.properties`, tags outside `origin/main`,
existing GitHub releases, failed tests, malformed distributions, and a missing
`MODRINTH_TOKEN` Actions secret. It prepares the exact pinned Extension source and
runs the clean common/both-loader and packaging gates before staging.

A successful run publishes exactly `openallay-fabric-26.2-<version>.jar` and
`openallay-neoforge-26.2-<version>.jar` to Modrinth, then attaches build provenance
and publishes those same two JARs plus `SHA256SUMS` to GitHub. The nested Builder
stays a separate Extension inside each loader artifact. Publication is ordered,
not atomic: verify both services and artifact hashes after the job finishes.
The Modrinth publisher updates its project page from README and is idempotent by
loader/version presence, not byte equality.

When the tagged commit contains `docs/releases/<version>.md`, release notes use
that curated player overview plus a Full changelog link when a prior tag exists.
Versions without a curated file retain generated notes, commit range and
contributors. Enable protected `v*` tags, required `Quality` checks, read-only
default Actions permissions, and immutable releases in repository settings;
workflow files cannot enforce those repository-level controls themselves.

## Run environments

```bash
./gradlew :fabric:runClient
./gradlew :fabric:runServer
./gradlew :neoforge:runClient
./gradlew :neoforge:runServer
```

Dedicated validation is headless. Fabric accepts `--args nogui`; NeoForge's
development launcher is already headless and must be run without Gradle
`--args`, because that option replaces its launch main class.

The accepted Fabric 26.2 full-mod development profile uses Architectury Fabric
21.0.4. Architectury Fabric 21.0.2 and earlier is known to break character
input, so Fabric metadata marks only versions through 21.0.2 incompatible when
the optional mod is present. Version 21.0.3 has not been verified: it is not
blocked, but its compatibility is unknown. This boundary does not make
Architectury a requirement. NeoForge is unaffected by this Fabric-only issue.

## Client model configuration

The main mode is pure client-side. The only supported player-managed format is
`config/openallay/models.json` schema 2. It contains no secret: each profile
retains only a qualified `credentialRef`. Schema 1 and the old single-profile
`model.json` format are not imported. If `models.json` is missing, OpenAllay
starts unconfigured even when `model.json` exists. Invalid or old files remain
untouched and produce a redacted settings notice; explicitly save a valid schema
2 profile to configure the client. This client-only rule does not change the
separate server-owned `server-model.json` format.

Ordinary players enter an API key through the native masked password field.
OpenAllay stores it under an immutable `local:<uuid>` reference in
`config/openallay/credentials.sqlite3`; a saved key is never filled back into
the widget or exposed through copy/cut, settings snapshots, diagnostics, logs,
packets, prompts, or history. On POSIX systems the credential database receives
owner-only permissions on a best-effort basis. This is local restrictive
storage, not an OS-native vault.

Externally authored development or headless configurations may instead name an
environment reference. This form is not requested by the normal player UI:

```json
{
  "schemaVersion": 2,
  "defaultProfileId": "openrouter-main",
  "profiles": [
    {
      "id": "openrouter-main",
      "displayName": "OpenRouter Main",
      "enabled": true,
      "protocol": "openai_chat",
      "baseUrl": "https://openrouter.ai/api/v1/",
      "model": "provider/model-id",
      "credentialRef": "env:OPENROUTER_API_KEY",
      "contextWindowTokens": 256000,
      "maxOutputTokens": 8192,
      "connectTimeoutSeconds": 30,
      "requestTimeoutSeconds": 300
    }
  ]
}
```

`anthropic_messages` is the other protocol. Remote endpoints require HTTPS;
HTTP is accepted only for loopback development. Inline `apiKey` and `apiKeyEnv`
are not valid in schema 2. Context precedence is explicit `contextWindowTokens`,
then exact trusted provider metadata/cache, then an eligible BEST match in the
bundled table. Unresolved models require manual context. Explicit values always
win, including a 1,000,000-token user budget. There is no 32K fallback or arbitrary
context cap. The `256000` value above is an example, not a fallback.

Output ownership follows the same explicit/automatic distinction. An explicit
`maxOutputTokens` remains a manual budget. Omitted or JSON-null output resolves the
exact trusted provider maximum, then the matched bundled maximum. An unknown
maximum requires manual configuration; there is no 8192/4096 output fallback.
Clearing the native Models output field restores automatic maximum selection.
The resolved output and context must satisfy `ContextBudget`; neither is silently
clamped. For the matched `gpt-6-luna`, the bundled maximum is 128,000 output tokens;
a manual 1,000,000 context remains unchanged. This is published capability data,
not a promise that every gateway accepts the same maximum.

The builtin model table works offline at arbitrary OpenAI-compatible endpoints,
even when authenticated `/models` returns IDs without limits.
Disabled profiles can retain an unresolved context window. Their diagnostic and
settings projections preserve that unknown value without blocking usable profiles
from starting; enabled unresolved profiles still need a resolved or explicit budget. Missing context
uses an eligible deterministic BEST match: canonical/upstream ID and published
alias first, provider-wrapper normalization next, then family/version-aware
similarity. Unknown unrelated names still require manual context. Models settings
shows the chosen model, published context/output, source/capture time, and every
published USD-per-million-token price tier. These are reference estimates, not
claims about the configured gateway's actual bill. Model ID, endpoint, protocol,
profile selection and explicit output setting are never changed.

An untouched automatic context display remains omitted in `models.json` on save;
only an actual context edit makes it explicit. Later exact trusted metadata can
therefore replace a builtin estimate, and edits affect only future requests.
Published output ceilings are advisory, not new request output defaults.

The separate strict resource
`data/openallay/models/builtin-model-catalog.json` contains reviewed capability
and pricing provenance. The provider metadata cache remains schema 1 and contains
no prices or builtin matches. Developers can update the table explicitly:

```bash
python3 -m unittest discover -s scripts -p test_update_builtin_model_catalog.py -v
python3 scripts/update-builtin-model-catalog.py --fetch-public --output /tmp/builtin-model-catalog.proposal.json
```

The updater pulls only the fixed unauthenticated public sources into a proposal.
Review identities, aliases, limits, decimal price units/tiers and source changes
before replacing the committed resource. Runtime does not fetch these URLs.
Keep the shipped `data/openallay/models/LICENSE.models.dev` attribution. See
[the source evidence and reproducible offline refresh procedure](verification/builtin-model-catalog/README.md)
and SKMB-2026-10-01-036 for the full precedence and matching contract.

`connectTimeoutSeconds` covers establishment of the provider connection.
`requestTimeoutSeconds` is the total budget for one dispatched model attempt,
including response headers, streaming body consumption, and decoding. A
scheduled cancellable watchdog closes a stalled body and reports the stable
`model_timeout` failure; cancellation, timeout, or completion wins exactly once
and late bytes cannot mutate the request.

OpenRouter metadata uses the official `GET /api/v1/models` catalog fields
`id`, `canonical_slug`, `context_length`, and the optional
`top_provider.max_completion_tokens`. Startup reads
`config/openallay/model-metadata.json` asynchronously. A cache miss refreshes in
the background without blocking startup; successful credential-free metadata
is cached across launches, and a failed refresh leaves explicit configuration
and prior cache intact. The cache is configuration-layer state, not an Agent
tool. Model providers and future online knowledge tools reuse the JDK HTTP
transport mechanics but retain separate credentials, permissions, codecs, and
evidence policy.

The guide screen's compact model control opens an explicit scrollable selector
for the selected session's named profiles and the server model when offered;
it never changes the model merely by cycling a button. Returning from settings
refreshes capabilities automatically. Switching during an active request
changes only the next request; the status line continues to show the model
captured by the running request. Commands provide the same semantics:

```text
/guide model list
/guide model profile <profile-id>
/guide model client
/guide model server
```

The last two forms remain compatibility shortcuts. `client` restores that
session's last named client profile and never silently chooses another one.

The Guide screen's gear button opens the common native settings screen on both
Fabric and NeoForge. Its Models page can create, edit, enable/disable, delete,
select the default profile, reload external edits, manually refresh trusted
metadata, and run one explicit connection test. Saving validates the whole
candidate and stages a new immutable local credential where needed, atomically
replaces `models.json`, and only then publishes the already-prepared runtime for
future requests. Active requests retain the runtime they captured at
submission. Replacing a key never overwrites the credential used by an active
profile; unreachable rows are collected only after successful publication.

The Models page mixes client-owned profiles with the connected server model.
The server entry is synchronized automatically, carries a server badge, and is
strictly read-only: client settings cannot edit, test, delete, or persist it.
It is configured exclusively by the server's `server-model.json`. A valid
server runtime is constructed at server startup before the first capability
packet can advertise it. Missing, disabled, or invalid configuration advertises
no server model and records only a server-local diagnostic. Disconnect removes
the entry and restores each affected session to its local default for future
requests; an already active request keeps the model/runtime it captured. The
model ID field
remains editable and can fetch an authenticated `/models` catalog using the
currently typed password first or the already-saved credential otherwise. A
saved credential is represented by an explicit saved-key hint; its value is
never filled back into the field. Anthropic profiles send their native API-key
and version headers plus Bearer compatibility for gateways whose catalog route
is OpenAI-style; every header remains confined to the validated provider
origin.

When a player explicitly selects the server model, the request advertises only
that player's currently enabled registered read-only client Tools. The server
intersects the IDs with its trusted registry and may call those Tools back on
the requesting client, including client options, installed mods, packs,
shaders, F3-style diagnostics, and player-visible state. Results return in
bounded chunks and remain correlated to the actor, request, and invocation.
Tool failures are returned to the model as structured results so it can explain
or recover; explicit cancellation, disconnect, and shutdown remain terminal.
The same rule applies to client-hosted models using server Tools: bridge
unavailability, malformed results, and the five-minute remote Tool deadline are
complete Tool failures, not Agent termination. Incomplete request, result, and
event chunk assemblies are sparse, accepted only for active requests, and
expire after the same deadline (or immediately at request termination).
Only the live Minecraft snapshot capture runs on the client thread. Tool
execution and potentially large normalization/chunk encoding run on a virtual
worker, and packet sends are marshalled back to the client thread. Invalid
settings fail closed to an empty advertised client Tool set.

Focused spatial observation uses the optional `world` binding of
`openallay:run_javascript`. Client-local execution captures client-visible
loaded blocks and entities on the Minecraft client thread. A server-hosted
model keeps scripts requesting `roots: ["world"]` on the server and captures
server-authoritative data for the authenticated requesting player. A
client-hosted model may call the server's advertised read-only JavaScript
projection for the same authoritative route. That projection rejects the
experimental `commands` root; command mutation remains a separate client-owned,
default-off capability.

Both routes detach block state, coordinates, entity summaries, and entity
details before the Rhino worker observes them. Entity observation IDs are
opaque and valid only for that request. Cancellation or disconnect stops later
capture slices and closes the request workspace; unloaded regions remain
explicit partial coverage rather than empty proof.

The top-level settings sections are General, Models, Extensions, Skills,
History, Diagnostics, and About. Extensions is a master-detail projection of
the JavaScript host, reusable modules, registered detached adapters, pending
community packages, and opt-in experimental capabilities. It is not a legacy
per-Tool enablement page. Skills remain a separate installed/community
document surface; their filesystem packages, validation, provenance, and
override rules are described below.

The public Skill community catalog is a strict schema-2 JSON document. Each
entry includes the stable package ID plus a player-facing display name,
description, and publisher, so the Community tab can explain a Skill before
installation. Archive, SHA-256, compatibility, version, and source remain
machine-validated package fields. Development-era schema-1 caches are rejected;
successful refreshes and all codec output use schema 2. The installed package's
validated root `SKILL.md`, not catalog display metadata, remains the
Agent-instruction source of truth.

The connection test displays a cost warning and requires a second confirmation.
It sends one non-streaming, non-retrying request capped at 64 output tokens with
no Guide history, Tools, Skills, game state, evidence, or trace. Assistant text
and provider bodies are discarded. The result contains only a stable category,
redacted endpoint authority, protocol, completion time, and latency. Model
metadata refresh/listing is a separate configuration operation and never counts
as a successful inference test. Closing settings cancels only an active probe;
an already-confirmed atomic save continues to its terminal result.

If `models.json` does not exist, OpenAllay presents one disabled in-memory draft
and does not create a file until the player explicitly saves. Old `model.json`
files are not imported or modified. Invalid startup files remain untouched and
produce a redacted settings notice. The screen receives only credential presence
and transient password-input state; it cannot read or render a stored value.

The 0.2 runtime has no Tool-family settings, per-Tool enablement files, or
user-editable Tool source envelopes. JavaScript is the primary model-facing
game-data capability. The Extensions page describes `openallay:run_javascript`,
its request-scoped `mc` roots, bundled helper modules, registered data adapters,
and the default-off game-command bridge. Skills remain a separate document
surface.

Recipe capture and knowledge integrations continue to publish their current
runtime data into the JavaScript host graph. Their availability is determined
by the installed game/mod environment rather than a second Tool configuration
layer. Existing recipe behavior options remain in
`config/openallay/tools/recipes-options.json`; they are runtime capture
configuration, not a model-facing Tool catalog.

Player-facing tool details are controlled separately by
`config/openallay/display.json` on both loaders. A missing file uses the safe
default below:

```json
{
  "schemaVersion": 3,
  "debugMode": false,
  "animationsEnabled": true,
  "assistantName": "OpenAllay"
}
```

`assistantName` is the player-chosen local name shown for the companion in the
native interface. It is trimmed, must not be blank, and cannot contain control
characters. Renaming it changes presentation only; it does not rewrite saved
conversation content, session IDs, evidence, or model/tool configuration.

Normal mode renders scrollable recipe, inventory, usage, and craftability cards
with native item icons, counts, tooltips, and typed recipe-viewer actions. Tool and
source details show friendly recorded source, authority meaning, coverage and
localized capture time under decision 038. Raw tool/invocation IDs, enum spellings,
provenance, metadata values, internal failure codes and normalized JSON remain
Debug-only. Setting `debugMode` to `true` appends a separate local diagnostic
section containing the already-redacted technical projection. An invalid file
keeps Debug Mode off and displays a localized notice; it never rewrites the
malformed file. The General page edits Debug Mode through the shared
`GuideDisplayRuntime`; an atomic save immediately reprojects both the settings
screen and newly rendered Guide content without requiring a restart. Reload
retains the last valid projection on malformed external edits.

`animationsEnabled` controls only subtle progress presentation. It does not
change semantic content, action availability, evidence, layout identity, or
narration. Schemas 1 and 2 are pre-release development state and are rejected
rather than migrated. Normal diagnostics say that history is loaded on demand and
show current-page loading/failure in friendly terms. Debug diagnostics add
only count-based window cursors, loaded/total counts, semantic cache hits and
misses, fallback counts, and context-token estimates; they never include raw
cursor/request payloads, transcripts, paths, provider bodies, actors, or scope
identifiers.

The History page projects only friendly connection kind, persistence health,
active-request state, and pending-write/deletion status. It can delete the
current player/world-or-server partition or all partitions belonging to the
current local player identity. Both actions require a fresh one-use
confirmation and are rejected as `history_delete_busy` while matching requests
or ordered writes are active. Whole-database reset is visible only in Debug
Mode and requires a distinct second confirmation. Settings delegates every
operation to the current `GuideService`/ordered repository; it never receives a
raw scope identifier, database path, or SQL string.

Diagnostics always presents localized, player-friendly cards for model,
knowledge/capability, recipe, history, and context health. Debug Mode adds a
separate whitelisted technical section containing only redacted endpoint
authority, metadata/checkpoint/source generations, counts, and stable status
codes. Neither projection can contain provider bodies, reasoning, transcript
content, credential values, raw history scopes, or filesystem paths. Closing
the screen discards drafts and confirmation tokens but does not cancel an
already confirmed history/configuration transaction; shutdown disconnects the
Guide service, closes ordered history, cancels any live probe, and closes the
metadata cache asynchronously on both loaders.

For an optional server-hosted model, use
`config/openallay/server-model.json` on the server. The capability is advertised
only when that configuration is valid. Client packets never contain the key.

## Player commands

```text
/guide <question>
/guide ask <question>
/guide cancel
/guide clear
/guide status
/guide skills
/guide sources
/guide session list
/guide session new <id>
/guide session switch <id>
/guide session close <id>
/guide model client
/guide model server
```

The same player may run requests concurrently in different sessions. A second
request in the same session returns `agent_busy`. OpenAllay applies no fixed
global concurrency or queue-count limit. Provider HTTP 429 closes only the
matching endpoint gate, honors `Retry-After` when present, and otherwise uses
cancellable exponential backoff with fair session rotation.

Client-local tools use detached client player, registry, and synchronized
recipe-display snapshots. If the server advertises enhancements, the same
client Agent also sees separately named server read tools. Model location and
tool location are independent.

## Shared GuideService and opt-in client E2E

Commands, the Phase 3C screen, and development probes consume one
connection-scoped `GuideService`. It owns immutable request/session snapshots,
model mode, topology, cancellation, retry, sources, and disconnect cleanup.
Each active snapshot carries only redacted lifecycle progress: phase, request
and phase start, most recent progress, attempt, and optional retry/deadline.
The GUI derives elapsed/countdown text locally in a fixed-height strip; clocks
do not append transcript rows or create history writes.
Across the client/server bridge, an attempt carries its relative timeout budget
rather than a server wall-clock epoch. The receiving client derives a local
display-only deadline, so clock skew cannot shorten, extend, or invalidate the
server-owned watchdog.
The server Agent protocol is version 5 and is decoded once in common
code. Unknown or malformed events fail only their correlated request; there is
no silent client/server fallback. Server-model requests carry only the
partition's visible user/completed-assistant history; they never carry restored
capabilities, live evidence, reasoning, credentials, or full normalized tool
results. Protocol v5 also carries the selected server model's actual context
budget and canonical model identity, and splits the encoded request into independently strict,
SHA-256-checked 24 KiB transport chunks so long histories do not depend on one
Minecraft custom-payload string.

Normal-mode guide history is stored at `config/openallay/history.sqlite3` in
SQLite schema 5. The 0.2.4 candidate retains the format released in 0.2.2;
JavaScript display intent adds no database or wire version. Recognized older
OpenAllay schemas 1, 2, 3, and 4 return
`history_schema_unsupported`; startup does not alter their tables, rows, or
files. Future, corrupt, foreign, missing/inconsistent-metadata, and otherwise
unrecognized databases also fail closed without mutation. To retain old history,
back up the database and open it with a compatible OpenAllay version. To discard
it, use the separately confirmed Debug Mode reset only after making any desired
backup. There is no automatic migration or reset. Each partition key is a
SHA-256 digest of the player
UUID, connection kind, and normalized integrated-world path or multiplayer
address; the raw path/address is not stored. Database work runs on one ordered
background worker and never blocks the client or render thread.

The durable projection contains sessions, their selected model profiles, user
messages, chronological visible assistant/tool/status entries, each request's
captured model selection, evidence summaries, and terminal request state. It
excludes model reasoning, credentials, authorization data,
raw provider bodies, full inventory snapshots, and full normalized tool
results. Loading temporarily disables submission. A load or write failure keeps
the in-memory Agent usable and shows that new messages are not durable. Work
left active by process loss restores as `INTERRUPTED`; continuing it always
requires an explicit retry with a new request ID. Versioned compaction
checkpoints are retained separately as derived, non-evidence memory and reused
only when their source hash and prompt/schema versions match. The generating
model ID is provenance, not a reuse lock: changing provider or model keeps the
session transcript and a valid checkpoint, then re-estimates the projection
against the newly selected model's own budget. Player history administration
and redacted normal/debug diagnostics are available in native settings.
Startup restores partition/session metadata without materializing request
bodies. The screen requests viewport-sized history pages independently from
provider-neutral context reads, which use the selected model's actual budget.
Safe Markdown, validated Minecraft references, registered controlled
components, semantic fallback text, variable-height virtualization, stable
anchors, and presentation-only animation are implemented in common code.

The real-client probe is disabled unless `openallay.e2e.enabled=true`. When
enabled, it waits for a real client player, submits through the same
`GuideService`, records status transitions, tool IDs, evidence, timings and
payload hashes, writes a redacted JSON report, then optionally requests clean
client shutdown. Report writing belongs to this development harness and is not
an Agent tool.

The helper below starts a deterministic loopback OpenAI/SSE fixture and launches
the selected graphical development client:

```bash
./scripts/run-real-client-e2e.sh fabric
./scripts/run-real-client-e2e.sh neoforge
```

Connect the launched client to a disposable world or test server, or set
`OPENALLAY_E2E_QUICK_PLAY_WORLD` to an existing disposable single-player world.
The fixture waits for durable hydration and every enabled installed recipe
viewer to publish a non-empty current catalog. It requests one
`openallay:run_javascript` call over the detached recipe, player, and knowledge
roots, then uses the exact current-capture recipe reference and computed result
in deterministic pre-authored component responses. The loopback fixture is not
a model and does not test model planning, answer quality, or live provider
behavior. It does exercise the production Tool, current game capture, evidence
binding, UI chronology, and native presentation path. The report records
redacted semantic/component/fallback counts and history-window/cache metrics,
and the script rejects any outcome other than `COMPLETED`.

The default retained recipe is `minecraft:iron_block`. A compatible mod recipe
can exercise native viewer embedding without changing the fixture, for example:

```bash
OPENALLAY_E2E_RECIPE_OUTPUT=farmersdelight:apple_cider \
OPENALLAY_E2E_RECIPE_ID=farmersdelight:cooking/apple_cider \
OPENALLAY_E2E_RECIPE_LABEL=苹果酒 \
./scripts/run-real-client-e2e.sh fabric
```

These variables affect only the deterministic loopback scenario. The exact
reference is read from the current JavaScript capture result; the fixture never
fabricates a viewer handle or craftability result.

Set `OPENALLAY_E2E_HISTORY_SEED_REQUESTS` to create sequential durable seed
requests before the reported scenario. `OPENALLAY_E2E_MIN_HISTORY_REQUESTS`
asserts the durable total; `OPENALLAY_E2E_REQUIRE_PAGED_HISTORY=true` additionally
requires a restart to hydrate fewer requests than the durable total and expose
an earlier-page cursor. The harness temporarily replaces `models.json` with an
isolated loopback profile and restores the exact prior file on exit.

The harness is intentionally opt-in because it opens a graphical client. CI
validates the controller, both loader hooks, shell syntax, and fixture syntax,
but does not claim a real-client run. Earlier Phase 4C Fabric reports, redacted
logs, exact JEI navigation screenshots, artifact URLs, and hashes are retained
under `docs/verification/phase-4c-all-known-recipes/`. Earlier consolidated
Fabric/NeoForge semantic-history reports and compatibility boundaries are under
`docs/verification/phase-4-final-acceptance/`; those artifacts predate the
manual-acceptance correction set and do not close it.
The closing correction reports, 12 reviewed screenshots, exact game-state Tool
probes, runtime artifact provenance, and production hashes are retained under
`docs/verification/phase-4-final-corrections/`.

## Player GUI

The configurable `key.openallay.open_guide` mapping defaults to `K`; bare
`/guide` uses the same opener on Fabric and NeoForge. The native full-screen
Screen does not pause the world. Escape and opening another Screen only detach
the UI subscription, so active work continues and reopening reconstructs from
the latest immutable GuideSnapshot.

The screen provides a responsive session rail/overlay, virtualized wrapped
transcript, multiline composer (Enter sends, Shift+Enter inserts a line break,
and Ctrl+Enter remains a compatibility shortcut), stop/retry controls, and an
explicit local/server model selector. Enter outside the focused composer keeps
the selected widget/card action. Only model text deltas are visible;
reasoning is absent from the UI view type. Assistant segments and tool cards
render in actual Agent event order, and a running card updates in place by its
tool invocation ID before later assistant text appears. Grounded recipe,
inventory and craftability tools receive first-class summaries, while other
tools use a deterministic friendly fallback. Tool and evidence details show the
recorded source, actual authority, coverage and capture time in normal mode;
raw technical metadata and normalized JSON require local default-off Debug Mode.
Details have an explicit close button, Escape closes them before the Guide, and
navigation keys scroll them without dismissing or clicking through the panel.
Session switches and disconnect cleanup remove stale detail state.

Decision 038 reserves responsive header/composer bounds and scrollable General/
About content. A raw pending Tool in a terminal request displays **Stopped before
a result was recorded**, a UI-only projection that preserves raw status and
fabricates neither success nor failure. Retry resends the question; it does not
resume a step. Stop prevents future work, not completed effects.

The final 0.2.4 packaged UI scenes passed on 2026-10-01: Fabric
`20261001-ui-disabled-03` (24 PNGs), NeoForge `20261001-ui-provider-failure-02`
(26 PNGs), and Fabric `20261001-ui-stop-01` (24 PNGs). The first retains native
permission-off/no-write success; the second retains a successful player read
followed by actual HTTP 503 and request failure; the third retains actual accepted
cancellation with no normalized Tool result. Each restores its original display
configuration. Independent Minecraft UI/UX review inspected all 74 final PNGs
and approved the reviewed scope with no release-blocking P0/P1. The integrated
clean gate passed 907 common tests with zero failures/errors and 6 skips, both
loader builds, and distribution/Phase 4/SQLite packaging. The retained local
review is `/Users/nkanf/docs/openallay-ui-ux-review-2026-10-01.md`; the artifact and
scene records are in `build/e2e/ui-release-0.2.4/`. Source/tests support the other
contract paths; these three captures are the graphical acceptance scope.

Deleting a session from the Guide screen uses two native confirmations bound to
the originally selected session; the second warning states that durable history
is removed and any active request is stopped. User and assistant rows expose an
explicit local clipboard action. The Export action captures the complete
current session in request/timeline order and atomically writes a UTF-8 text
file below `openallay/exports` in the Minecraft game directory. The writer has no
path input, rejects symlink escape, and omits normalized Tool payloads,
checkpoints, model settings, raw diagnostics, and credential-shaped text.

Validated inline `recipe_grid` components bind only to the complete normalized
recipe result from their same-request Tool invocation. Visible bindings enter a
client-thread-only `NativeDomainView` lifecycle and are released when their row
leaves the viewport or the Screen closes. JEI 26.2 exact references re-resolve
against the current provider generation and embed its public
`IRecipeLayoutDrawable` (`drawRecipe`, overlays/tooltips, and `tick`). Oversized,
stale, missing, or failed layouts fall through to OpenAllay's neutral labelled
slot canvas. REI 26.2 currently exposes category widget construction but no
verified exact durable-reference re-resolution contract used by OpenAllay, so
its native provider records `rei_exact_embedding_unsupported` and uses the same
neutral fallback instead of claiming or approximating a mod-owned screen.

If the selected model is unavailable, the screen still opens and shows the
configuration/capability state. Client profiles remain at
`config/openallay/models.json`; the Models page accepts a transient masked API
key but never displays a stored secret. Model-mode changes affect future
requests only and never trigger silent fallback.

## Rhino Agent runtime

Every factual success carries immutable evidence: authority, completeness,
capture time, source, provenance, game version, loader, and optional scoped
details. The general model-facing analysis operation is:

```text
openallay:run_javascript
```

OpenAllay embeds the KubeJS-Mods Rhino fork directly; KubeJS itself is not a
runtime dependency. Every invocation gets a fresh Rhino context and immutable
detached `mc` graph. Safe mode is the default; only a request with explicitly
captured client-local unrestricted authorization gets standard Rhino objects and
`Java.type` interop under decision 033. `MinecraftAgentHostGraph` retains the original
detached Java records and lists; `RhinoHostAdapter` exposes record components,
collection elements, String-keyed map entries, Optional values, stable scalars,
and existing Gson leaves through lazy identity-cached read-only `Scriptable`
views. Request and workspace input is never converted to a second Gson tree,
embedded in source, stringified, or parsed with `JSON.parse`.

Normal JavaScript `filter`, `map`, `reduce`, grouping, joins, optional chaining,
and pure helper functions replace repeated per-row domain Tool calls. Host
arrays use the standard Array prototype but are not writable. Non-mutating
transforms create ordinary JavaScript arrays; call `filter`, `map`, `flatMap`,
or `slice` before `sort`, `reverse`, `splice`, or other mutation.

The runtime embeds the KubeJS Rhino fork. Tested top-level collection pipelines
behave normally. For nested lookups inside a repeated callback, use an indexed
`for (var index = 0; ...)` loop instead of an inner `find`/`map` callback that
declares block-scoped locals; Rhino 2101 otherwise reports a redeclaration
failure. Bundled Skills and examples use the tested form.

Model guidance asks each new `run_javascript` call to include a short `title` and
`description` in the player's language. The optional input fields accept strings;
absent, null or blank values use localized defaults, and non-string live values
fail `invalid_arguments`. Cards label this literal model text **Planned action**,
separately from code-owned execution status and factual results. Completion
updates the same invocation and preserves its start intent.

Valid intent labels do not change source, roots, handles, duplicate execution
identity, permissions or evidence. History and server ToolStarted events retain
only the closed display intent through existing JavaScript presentation-message
arguments: zero for legacy, two for title/description. Raw arguments and source
remain excluded. Stored/wire arguments still have strict string/control-character
validation; history schema 5 and protocol 5 are unchanged. See decision 037.

`run_javascript` accepts `roots`; normal analysis should select only the
required host views, for example `["items"]` or `["items", "recipes"]`.
Registry and recipe rows are exposed once in those JavaScript-native views.
`mc.registryEntries` is the unified cross-kind registry array, while
`mc.registries` and `mc.recipeCatalog` carry catalog metadata.

The graph exposes captured capabilities such as:

```text
mc.capabilities
mc.items / blocks / fluids / effects / enchantments / entities
mc.registryEntries / registries
mc.recipes / recipeCatalog
mc.player
mc.game
mc.knowledge / knowledgeCatalog
mc.extensions / extensionCatalog / extensionDiagnostics
```

Every root is declared by the same KubeJS-Rhino `TypeInfo`-derived catalog used
by the closed host adapter. `schema.list()` reports roots and availability
without resolving values; `schema.describe("game.mods.installed")` walks one
stable declared path. Registry `properties`, recipe `extensions`, and extension
objects remain open-ended. Use one focused `helpers.schema` sample only for
those genuinely dynamic values, rather than rediscovering stable core records.
All capture still occurs on the Minecraft-owned thread and detaches immediately
before Rhino runs on a virtual worker.

Canonical results remain internal Gson JSON for evidence validation, UI cards,
traces, and subsequent programs. The provider receives a compact CLI-like text
projection without JSON wrappers or evidence payload duplication. Large results
stay in a request workspace under an opaque handle; a later program can pass
that handle explicitly and call `workspace.open(handle)`. Client-executed Tools
for a server-hosted model share the same request correlation and close their
workspace at the terminal request event.

In safe mode, the runtime rejects source and result graphs that exceed the
accepted depth/node/array/object/string budgets before workspace publication.
A safe-mode workspace admits at most 16 canonical results and 32 MiB of estimated
content, with an 8 MiB per-result budget. One execution may reopen at most four
handles totaling 8 MiB. Safe-mode model/UI previews are bounded; Debug renders
bounded metadata rather than raw normalized JSON. Provider input is re-estimated
against the selected model budget before every continuation turn in both modes.

The safe Rhino scope denies arbitrary Java wrappers, reflection, class loading,
network, process, real filesystem and live game access. Captured `mc` views remain
read-only; the separately authorized command binding retains its own permissions.
Instruction observation enforces cancellation and a monotonic deadline. Invalid,
cyclic, non-finite or unsupported results fail with stable `javascript_*` codes.
Decision 033 bypasses OpenAllay source, interpreter-time, result-normalization,
workspace, handle-selection and preview budgets only for authorized client-local
unrestricted execution. Request correlation, cancellation, evidence validation
and terminal workspace cleanup remain active.

Trusted optional integrations first capture and detach public mod API state on
the owning Minecraft thread. Java-side `JavascriptDataModule` implementations
run later on the Agent worker and may only project immutable
`ToolInvocationContext` records or immutable collections into evidence-bearing
values under `mc.extensions`; they cannot call live APIs or use reflection.
Capture failures are isolated as module diagnostics. Unsupported nested values
remain isolated to the property that attempts to read them. The detached `mc`
projection exposes component values, not Java methods, `Class`, generic wrappers
or reflection. This projection stays read-only in unrestricted mode; separate
`Java.type` interop does not turn adapter data into live objects.

JEI and REI are loader-discovered first-party `OpenAllayExtension`
implementations. Their existing public-API recipe captures remain the factual
providers; the Extension projection exposes detached provider availability,
completeness, generation-bearing recipe references, categories, diagnostics,
and focus/navigation capabilities at `mc.extensions["openallay:jei"]` or
`mc.extensions["openallay:rei"]`. JEI advertises exact durable-reference
navigation and native layouts. REI advertises item-focused recipe/usage
navigation but explicitly reports that exact durable-reference navigation is
unsupported. Losing either runtime degrades only that provider projection.

External Extensions use the same loader-owned startup path. A Fabric
`ModInitializer` calls `OpenAllayFabric.registerExtension(...)`; a NeoForge mod
constructor calls `OpenAllayNeoForge.registerExtension(...)`. Registration may
happen before or after OpenAllay bootstrap: early candidates are queued and
late candidates are validated against the live registry. OpenAllay deliberately
does not scan classes or hot-load packages, so installing or importing a JAR
always requires a normal loader restart.

EMI is not registered as a 26.2 Extension. Its official project and artifact
catalog currently publish through Minecraft 1.21.x rather than a compatible
26.2 public API artifact, so OpenAllay does not compile against, advertise, or
pretend to support it. A future adapter must first verify a real 26.2 public API
on both target loaders.

Reviewed JavaScript modules are the reusable domain layer—the equivalent of
prebuilt, composable operations rather than additional one-purpose Tools.
`require("openallay:crafting")` exposes recipe-cost and deterministic global
capacity-allocation operations inside the same `run_javascript` batch. The
module reports observed allocation, missing requirements, maximum crafts, and
whether evidence is conclusive; it does not recursively craft intermediate
items. Its behavior is compared with the Java allocation oracle in contract
tests. Exact module IDs resolve only from bundled resources and are cached within
one execution. Loading a module grants no additional authority: safe-mode host
restrictions or the request's captured unrestricted permission still apply.
The 0.2.0 line removes the legacy domain retrieval and craftability Tool
implementations. Runtime model Tools are `openallay:run_javascript` plus the
Skill-loading surface; recipes, guides, inventory, game context, and extension
data are mounted below the JavaScript host graph instead of represented by
parallel Tool families.

The player-facing Settings section named **Extensions** has separate installed
and community master-detail pages. Installed cards list compatibility,
diagnostics, and every declared root, data adapter, JavaScript module, Skill,
and native result view without capturing game state or invoking an adapter.
The community page refreshes the strict schema-2 catalog at
`https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Extensions/main/catalog.json`
and retains its last valid generation in
`config/openallay/catalogs/extensions.json`. Every package embeds a strict
schema-1 identity at `META-INF/openallay-extension.json`. One schema-2 catalog
entry represents one logical Extension ID/version and contains a separate URL,
SHA-256, and mod-ID set for each supported loader. OpenAllay selects the current
loader before download. A compatible downloaded package must match its
loader-specific artifact, embedded identity, descriptor, and checksum. A local
JAR is identified directly by the embedded manifest and does not need a catalog
entry. Both paths validate actual SHA-256, compatibility ranges, and loader mod
metadata before atomically writing the JAR under a stable managed name in the
instance `mods` directory. The settings
projection remains
`restart_required` until a later loader startup actually registers the
Extension; it never claims hot activation.

The first visit to either community tab starts one non-blocking catalog
refresh. Any cached Skill or Extension generation remains immediately usable
while the refresh runs. A failure keeps that cache and shows one diagnostic
instead of repeatedly retrying; the refresh button is the explicit retry.

The public authoring repository includes an independent dual-loader example at
`examples/hello-extension`. Its CI builds one shared typed contribution into
separate Fabric and NeoForge packages, verifies each embedded manifest and
loader metadata, and records both SHA-256 values. OpenAllay `0.2.0` predates the
public Extension SPI; `0.2.1` is the first release that publishes it. External
projects must compile against `0.2.1` or a later compatible 0.2.x artifact and
must not advertise `0.2.0` as an OpenAllay product dependency. Product and
Extension API versions are independent: OpenAllay `0.2.4` implements Extension
API `0.2.1`. A manifest using its invocation participants can declare
`[0.2.1,0.3)` while loader metadata separately requires a product version that
provides those APIs. Existing API `0.2.0` contributions and four-list constructors
remain compatible. The model catalog and display intent do not change the SPI.

An experimental game-command capability also lives on that page and is
disabled by default. Its strict state is stored in
`config/openallay/experimental-commands.json`. Enabling it affects future
requests only and adds:

```javascript
commands.list()
commands.describe(path)
commands.run(command)
```

Use `roots: ["commands"]` for command-only JavaScript. `commands` is a
top-level binding rather than an `mc` dataset root, and selecting it while the
experimental capability is disabled fails explicitly.

The detached catalog recursively mirrors the complete Brigadier dispatcher
visible to the requesting player, including vanilla, server, loader, and
mod-registered commands. Execution removes at most one leading slash and then
uses the normal player command route on the Minecraft client thread. OpenAllay
adds no command allowlist, argument restriction, or call-count cap; Minecraft's
parser, connection, player identity, and permissions remain authoritative.
Calls are ordered and serialized per player. `commands.run` waits on the Rhino
worker for the associated non-overlay Minecraft feedback window and returns
`state`, ordered `messages`, and `durationMillis`; it does not block the render
thread. `state` is `feedback` or `no_feedback`, not a fabricated universal
success bit. A command already submitted is not rolled back if a later
statement fails or the request is cancelled. When the setting is off, the
`commands` object and matching Skill are absent from the request.

## Unrestricted JavaScript and automatic JVM guidance

The Extensions page has a separate default-off unrestricted JavaScript setting.
Only a client-local model request can capture this permission. Server-model
requests and every server-originated client Tool callback explicitly capture
safe mode, regardless of the local toggle; they never inherit local JVM authority.
A setting change affects future requests only. Missing or invalid configuration
resolves to disabled.

Authorized requests automatically receive the bundled `unrestricted-javascript`
Skill instructions in their system prompt and can load its declared Java interop
reference through `load_skill`. The Skill explains the actual Rhino `Java.type`
bridge, static and instance method calls, Java collections, and safe owning-thread
scheduling for Minecraft access. It explains capability use; it does not grant
permission. Tool and Skill deny policies still apply. The prompt and `load_skill`
use the same frozen request catalog, including after a repository or policy change.
The context budget reserves room for the larger of the isolated and authorized
prompts. Ordinary requests and server command prompts do not advertise this Skill.

In this mode the Agent may use JVM file, network, process, and live-object APIs
for the player's task, rather than pretending execution is confined to detached
`mc` roots. The `mc` roots themselves remain immutable. Cancellation is not
rollback and cannot guarantee interruption of blocking native/Java operations.
Java values alone are not grounding evidence. Never include JVM credentials in
model context, tool results, traces, logs, or player answers. See
SKMB-2026-09-29-033 for the authority and lifetime contract.

## Knowledge and Skills

Patchouli is read directly from active client resource packs, without a binary
dependency. Locale precedence is active locale, `zh_cn`, then `en_us`.
Config/advancement-gated entries whose visibility cannot be proven are excluded.
Text, item/recipe links, and embedded dense or sparse multiblocks are indexed.

Each successful knowledge reload builds one immutable, in-memory index and
publishes it atomically with the matching snapshot evidence. Search first
protects exact document and linked item/recipe identities, then weights stable
path aliases, source metadata, titles and Markdown headings before applying a
Unicode-aware BM25-style score to section text. Each indexed result retains an
exact `sourceId`/`documentId` pair for later document projection and adds a
stable heading-derived `sectionId` plus an evidenced excerpt. Documents without
headings use the stable `document` section.

This index deliberately remains pure Java. Knowledge generations already live
as detached resource snapshots, so copying them into SQLite FTS would create a
second mutation and failure lifecycle without adding durability. The active
retriever is behind a narrow common interface so a later optional embedding or
reranking adapter can compose with the local candidates, but exact identities,
provenance, evidence and the deterministic offline path cannot be replaced by
an external score.

FTB Quests is optional. Its public API is resolved through allowlisted public
method handles; private reflection is never used. Only chapters and quests the
API reports visible for the current team enter the snapshot. On 26.2, where no
compatible FTB Quests release is currently available, the source reports an
explicit integration diagnostic and the rest of the Agent continues normally.

Skills follow a constrained Agent Skills filesystem format:

```text
skills/<skill-name>/
├── SKILL.md
├── references/     # optional read-only Markdown/text
└── assets/         # optional non-executable resources
```

`SKILL.md` contains YAML frontmatter and Markdown instructions. `name` and
`description` are required and the directory name matches `name`; optional
Agent Skills fields and OpenAllay namespaced string metadata remain strictly
validated. `allowed-tools` expresses a dependency only and never grants a
permission. Scripts, URL references, root escape, unsafe symlinks, arbitrary
paths, and unsupported files are rejected.

Community packages store their package version as the string metadata key
`openallay/version`. Settings compares that durable value with the catalog
version. A legacy package without the key has an unknown installed version and
is offered one update; a matching installed version is current. Skill API
compatibility is a separate `0.2` contract and is not inferred from the
OpenAllay product patch version.

The Skill community catalog is cached at
`config/openallay/catalogs/skills.json`; refresh, install, and local import are
configuration-layer operations and never become model Tools.

Bundled packages under the mod resources are immutable and use uppercase
`SKILL.md`. Local packages live under `config/openallay/skills/`; a valid local
package with the same name overrides its bundled package. The settings UI and
`openallay:manage_skill` share the confined package contract. Agent create,
update, and delete accept only an exact Skill name, complete `SKILL.md`, and
optional Markdown references below `references/`; they never accept a path.
Candidates are staged, parsed, dependency-checked, and published atomically.
Changes affect future request snapshots only. Deleting a bundled override
reveals the immutable bundled package.

`openallay:load_skill` returns at most 8192 characters at a time. When
`complete` is false, the model continues the same exact document with the
returned opaque cursor. The cursor is bound to the Skill name, reference path,
and content fingerprint; malformed, cross-document, and stale cursors fail
closed instead of falling back to a full read.

Skills are instructions and references, not executable Rhino modules. They
cannot fetch URLs, register Tools, grant permissions, or expand the JavaScript
sandbox. A Skill teaches the model how to use the registered runtime; it does
not itself execute.

## Live provider acceptance

The normal test suite skips real network calls. To verify streaming, a real
tool call, tool-result continuation, grounded Chinese output, and secret
redaction, export credentials only in the shell environment and run:

```bash
OPENALLAY_MODEL_BASE_URL=https://provider.example/v1/ \
OPENALLAY_MODEL=model-id \
OPENALLAY_API_KEY=... \
OPENALLAY_MODEL_PROTOCOL=ANTHROPIC_MESSAGES \
./scripts/live-model-smoke.sh
```

Never commit a model JSON containing `apiKey`.

For local transport investigation, explicitly add
`-Dopenallay.model.diagnostics=true` to the Minecraft JVM. The packaged launcher
supports `--model-diagnostics`. This default-off switch logs exception class
names, one allowlisted model frame, HTTP status, field types and decoder counts.
It excludes exception messages, provider values/bodies, reasoning and credentials;
it is separate from player Debug Mode and does not make a provider request.

To exercise the production Rhino prompt, bundled analytical Skill, JavaScript
Tool, compact model projection, and the two requested batch scenarios, use:

```bash
OPENALLAY_MODEL_BASE_URL=https://provider.example/v1/ \
OPENALLAY_MODEL=model-id \
OPENALLAY_API_KEY=... \
OPENALLAY_MODEL_PROTOCOL=OPENAI_CHAT \
./scripts/live-model-smoke.sh javascript-agent
```

The live test accepts `OPENALLAY_LIVE_STREAM=false` when isolating provider
stream transport from Agent/tool behavior. It records only redacted task,
Tool-ID, script, result-summary, and invocation-count diagnostics.
When an endpoint is intermittently unavailable, set
`OPENALLAY_LIVE_JAVASCRIPT_SCENARIO=sword` or `container` to rerun only that
scenario; the default `all` still exercises both.

To exercise exactly the native settings connection-probe contract from a
headless script, place a strict schema-2 `models.json`-format file in an ignored
path such as `run/openallay/settings-probe.json`. The externally authored file
uses `"credentialRef": "env:PROVIDER_KEY_NAMED_BY_THE_FILE"`; export that
environment variable in the shell, then run:

```bash
export OPENALLAY_SETTINGS_PROBE_CONFIG="$PWD/run/openallay/settings-probe.json"
export PROVIDER_KEY_NAMED_BY_THE_FILE='...'
./scripts/live-model-smoke.sh settings-probe
```

The script never accepts a credential on argv. It rejects inline `apiKey`,
`apiKeyEnv`, URL credentials/query/fragment, schema-1 profiles, and non-HTTPS
remote endpoints through the strict production loader. This environment-reference
path is for external/headless operation and is not a player settings workflow.
Retained output contains only the terminal code and, on success, profile ID,
protocol, redacted authority, and latency; it never prints assistant output or
raw provider bodies.

## Development commands

The following commands require game-master permission:

```text
/openallay dev tools
```

The model-facing catalog contains `openallay:run_javascript` plus the Skill
loading and management Tools available to the current runtime. Recipe, guide,
inventory, game-state, resource-search, and craftability domain Tools were
removed for 0.2.0; their detached data is available through the JavaScript host
graph and bundled Skills instead.

## Deterministic Agent trace replay

Phase 1 deliberately replays recorded Agent traces instead of pretending that a
rule-based response is a live model. A tool-call step invokes the real
`ToolRegistry`; its normalized result is checked with an `exact`, `contains`, or
`schema` expectation. Assistant-message steps are explicitly pre-authored trace
content.

Trace JSON files live under:

```text
data/<namespace>/agent_traces/<trace-id>.json
```

Schema version 1 is strict. Unknown fields, duplicate JSON keys, unsupported
versions, malformed tool IDs, and a mismatch between filename and declared
trace ID are rejected. Resources are discovered from the active server resource
manager each time, so a normal data-pack reload updates the available traces.

The trace declares which context capabilities it needs: `registries`,
`recipes`, and/or `player`. Capture occurs on the Minecraft server thread and
immediately detaches game objects into immutable records before tools run.
Console replay of a player-required trace returns `player_required`.

The Phase 1 replay contract has no project-defined item-count, recipe-count,
inventory-count, string-length, trace-step or report-length cap. Its reports
preserve the requested fixture data and observe counts, estimated serialized
bytes, capture time, tool-result bytes and replay time. This is a historical
replay contract, not a general statement about current Rhino, workspace or
provider-context budgets.

The Phase 2 transport runs in the Minecraft JVM with JDK HTTP, without a
Node/Python sidecar or MCP bridge. The later Rhino runtime's safe and unrestricted
execution modes are described above.

The trace parser and replay engine remain available for extension-owned
deterministic fixtures. OpenAllay no longer bundles replay documents that call
the removed domain Tools.

## Phase 3A verification baseline

On 2026-07-17 the complete common suite reported 100 tests, 0 failures, 0
errors, and 1 opt-in live-provider test skipped. Both production artifacts built
successfully:

```text
fabric/build/libs/openallay-fabric-26.2-0.1.0-SNAPSHOT.jar
neoforge/build/libs/openallay-neoforge-26.2-0.1.0-SNAPSHOT.jar
```

Both JARs contain the five new grounded workflow tools,
`EvidenceMetadata`, and `CraftabilityCalculator`. Repository and artifact
credential-pattern scans returned no matches. Phase 3B command E2E,
GuideService, and Phase 3C GUI are not included in this baseline.

## Phase 3B verification baseline

On 2026-07-17 the complete common suite reported 119 tests, 0 failures, 0
errors, and 1 opt-in live-provider test skipped. Fabric and NeoForge production
builds both passed and contain `GuideService`, `GuideStateReducer`, the strict
server event codec, and the gated real-client controller. Tracked-file and JAR
credential-pattern scans returned no matches. The deterministic HTTP/SSE model
fixture answered a direct contract request successfully. No graphical client
was launched during this unattended run, so no real-client report or visual
gameplay acceptance is claimed by this baseline.

## Phase 3 final verification baseline

The final clean build on 2026-07-17 reported 125 common tests, 0 failures, 0
errors, and 1 opt-in live-provider test skipped, followed by successful Fabric
and NeoForge production builds. Required GUI/service classes and language assets
are present in both artifacts. Tracked files, all Git objects (including the
unreachable-object set reported by `git fsck`), and every built JAR returned no
credential-pattern matches. Script syntax and the deterministic fixture parser
also passed.

```text
Fabric  SHA-256 157dcf0fd50bc4b85fa41ee24d363f42b8c7e8d8f24bb8b79a7fddb13cf55f56
NeoForge SHA-256 2d6d31bef6a8f023ee4f95399ba8e6ab6bd89333c7cb4276c77b99c2d2b7f347
```

This baseline does not include a graphical client launch, screenshot, or
manual interaction claim.

## Loader boundary

Production source under `common/` must not import Fabric or NeoForge APIs. Loader
entrypoints, lifecycle hooks, and command registration remain in their respective
loader modules. A unit test enforces this boundary.

## Default Extension distribution build

OpenAllay's default loader artifacts include a separately built online Builder
Extension. Its source and commits live in `OpenAllay-Extensions`, not in core.
The 0.2.4 candidate retains Builder version `0.1.0` at source revision
`53548537bb7db2b4c3cee09af60f0c7b098bbd79` from
`distribution/extensions.lock.json`; core, Extension and API versions are separate.
Installation does not enable unrestricted JavaScript. Player automation and
Baritone integration remain research-only.

Prepare the exact locked Extension source explicitly before a distribution build:

```bash
python3 scripts/prepare-distribution.py
./gradlew clean :common:test :fabric:build :neoforge:build
```

Preparation checks out `distribution/extensions.lock.json` into ignored local
build state. Gradle does not perform an implicit source download. A missing,
wrong-revision or modified checkout fails the default distribution build.
`./gradlew :common:jar` and `:common:test` can bootstrap without that checkout.
`-PbundleExtensions=false` is an explicit core-only developer build, not a
complete distribution; distribution verification rejects it.

For coordinated changes across both repositories, use
`-PopenallayExtensionsDir=../OpenAllay-Extensions -PallowUnpinnedExtensions=true`.
This still builds the Extension, but marks its provenance as unpinned and is
rejected by release distribution verification. Commit the Extension, update the
source lock, prepare its exact revision and rerun the ordinary full gate before
shipping. No Python process or source checkout runs inside Minecraft.

## Trusted Extension invocation scopes and requirement metadata

Extension API `0.2.1` adds an optional fifth contribution list of
`JavascriptInvocationParticipant` values. Existing four-list constructors remain
available. A participant opens on the JavaScript worker and returns an
`AutoCloseable`; cleanup runs in reverse order on that same worker. The supplied
`JavascriptInvocationContext` exposes the immutable invocation, cancellation,
`requireActive()`, operation evidence recording, and `completedSuccessfully()`.
No live game binding or domain Tool is added by this interface.

`completedSuccessfully()` means the JavaScript body returned normally while its
scope was active. It is not a claim that a domain action succeeded or that later
Tool normalization/evidence validation passed. Closing any scope revokes its
cancellation lifetime, even after a normal return. Extensions must not confuse
that revocation with the domain operation's outcome. Queued owner-thread actions
must recheck active scope and their own exact connection/world identity.

Skill extra metadata can declare whitespace-separated advisory IDs under
`openallay/requires-capabilities`, `openallay/requires-extensions`, and
`openallay/requires-skills`. Extension descriptors, package manifests and catalog
entries support an optional `requirements` object with the equivalent
`capabilities`, `extensions`, and `skills` arrays. These fields grant nothing and
never add an installation or runtime gate. Existing actual Tool policy and
required-mods compatibility semantics remain separate.

Settings displays declared requirements and their current status. Package
installation first validates/stages a candidate for review. The player can
explicitly enable an available requirement, cancel, or Continue anyway. Continuing
publishes only the selected package; it does not change authorizations or install
dependencies. An explicitly confirmed setting write may finish even if the
review is then closed; closing is not rollback. Changes affect future requests,
not captured authority in an active request.

All construction source, JS modules, Skill, native scheduling, templates and
journals are in the separate `OpenAllay-Extensions` repository. The core runtime
has no construction function or construction Skill-name branch. The default
Builder backend uses the active integrated server under client-local unrestricted
JavaScript. It does not implement a remote server write protocol, edit a client
world mirror, open offline saves, or silently switch to commands. Unsupported
execution contexts fail explicitly. See decisions 034 and 035 for lifecycle,
artifact, partial-failure and advisory-review semantics.

### Packaged Builder acceptance harness

`scripts/run-packaged-builder-acceptance.py` prepares an isolated instance and
requires an explicit reviewed launch. It uses the default production-named JAR
and nested Builder, not Gradle source classes. See
[the packaged acceptance guide](verification/packaged-builder-acceptance.md).
This is opt-in graphical testing. Its native oracle reads actual integrated-server
blocks on the owning server thread. Java exit success or a final model answer is
not acceptance. Every selected native check must pass.

The guide records the 2026-09-30 packaged runtime receipts: Fabric and NeoForge
each passed disabled authorization (1 native check), full construction (85), and
same-world restart (85). The primary `gpt-kanglives` / `gpt-6-luna` profile, with
explicit 1,000,000-token context, completed separate copy (58) and linked undo (80)
requests. Strict journal validators passed exact copy linkage and all 75 inverse
undo positions with clean conflict/uncertain lists. These receipts identify their
0.2.3 acceptance-instrumented artifacts; they are not hashes or final-gate results
for the later 0.2.4 candidate.

The controller can create a new survival, commands-off superflat world or reopen
only a prior manifest-owned acceptance world. Normal sessions never use these
hooks. The launcher validates the previous native report before reload and live
undo. The two live-model phases retain actual copied state before undo, then
verify source preservation and target restoration. Retained Tool results and
journals must also prove the copy operation and linked undo; model narration is
not proof.

The disposable synthetic username must fit Minecraft's 16-character login field.
Simulation distance is 5, within the Minecraft26.2 range. `--low-impact` reduces
only the generated client's window, FPS, and heap; render distance remains 4 for
fixture coverage. It does not change ordinary game options.

OpenAI-compatible optional `tool_calls`, usage, and usage-detail fields may be
missing or JSON null. Both response codecs treat those forms as absent. Invalid
non-null shapes and malformed tool arguments remain failures. Live connection
probes and graphical acceptance remain separate from deterministic tests.

## Manual runtime corrections after 0.2.4

Decision 040 records the real-client failures and their corrections. Durable Tool
identities remain request-qualified internally. Provider codecs preserve valid
IDs and deterministically map invalid outbound IDs while preserving Tool-call
and Tool-result pairing; OpenAI-compatible call IDs obey the endpoint's 64-character
schema field and alphabet. No history or server protocol format changes.

`run_javascript.roots` is a list of bare top-level names: `roots:["player"]`
selects `mc.player`, and `roots:["game"]` selects `mc.game`. Invalid access-path
selectors provide exact corrective feedback without selecting extra roots.
A returned module/function remains `javascript_result_invalid`; return JSON data
from an operation instead. An argument/result failure does not prove terrain or
an integration is missing.

Settings diagnostics read counts-only published source state from the existing
knowledge reload. Missing capture/evidence is shown as unknown rather than zero.
Source failures can retain the last valid counts with an explicit partial state.
Disconnect, shutdown and player/world scope replacement clear published source
facts and old primary-provider handles through the common context-provider hook.
The next connection stays unknown until fresh capture; saved source configuration
and enablement policy remain unchanged.
The context metric is the latest actual local request estimate, not the sum of
compaction checkpoints. Its lookup is bound to the selected model/session/request;
server models, pre-dispatch requests and replaced/disconnected endpoints report
unknown. Pending writes/active requests are current work counts; checkpoint counts
are retained compaction records. No provider request or live capture occurs from
the settings render path.

These corrections are committed/pushed without a new tag or release. The original
0.2.4 manual run and its failed traces remain retained; verification for this
source change is recorded in the implementation plan.
