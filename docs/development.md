# Development

OpenAllay 0.4.2 uses Minecraft 26.2 and Java 25 as the feature mainline and
implements public Extension API 0.4.0. Accepted release intervals are recorded in
`gradle/minecraft-artifacts.json`. Product and public API versions are independent.
Use the checked-in Gradle wrapper; a system Gradle installation is not needed.

See the [0.4.2 release notes](releases/0.4.2.md) for the current product changes.
The published [0.4.1 notes](releases/0.4.1.md), tags, and downloads remain unchanged.
Earlier Builder performance, request-control and Skill context evidence remains in
its [verification record](verification/2026-10-01-builder-performance-runtime-controls.md).
Historical execution/context receipts remain in the [041 verification record](verification/execution-context-simplification.md).
Those records describe their own source builds. They neither replace published
0.2.4 artifacts nor establish current in-game latency.

Before formal 1.0, internal formats are **Latest Only**: keep the current shape
and exact validation, without internal version numbers, versioned filenames,
migrations, or old aliases. This covers configuration, history, model context,
checkpoints, semantic components, common packets, traces, and benchmarks.
Independently released Extension packages/public APIs and community catalogs keep
their compatibility versions. Minecraft, loader, model, dependency, and native
`dataVersion` values remain external facts. This policy never authorizes deletion
of world saves, existing databases, exports, or operation journals.

## Setup, build, and run

### Common and loader gates

```bash
./gradlew :common:test
./gradlew :fabric:build :neoforge:build
```

Production code in `common/` must not import Fabric or NeoForge APIs.
Loader entrypoints, lifecycle hooks, command registration, and packet sends
belong in their loader modules. Both loaders share the common runtime/settings.

For unreliable Maven connections, replace `./gradlew` with `./gradlew-curl`.
It downloads failed dependency URLs with curl retries into ignored
`.gradle/curl-mirror` state, then resumes the command. To select an FLClash proxy:

```bash
OPENALLAY_CURL_PROXY=socks5h://127.0.0.1:7890 ./gradlew-curl build
```

### Minecraft world adapter

The core-owned [Minecraft world adapter](../adapters/minecraft/README.md) lives in
`adapters/minecraft/` and is included as `:adapters:minecraft`. It implements the
one public SDK's `MinecraftWorldAccess` and `WorldSession` ports; it is not a
version-specific Extension fork. Minecraft 26.2 remains the accepted feature mainline.

The module reuses `src/main/` as its base. The selected profile supplies exact
Minecraft/native dependency pins and the Java toolchain. Ordered native-family
roots under `src/targets/<family>/`, followed by an exact-target root when needed,
override only matching relative paths through `gradle/minecraft-source-family.gradle`.
Loader integration remains in `fabric/` and `neoforge/`, outside the shared SDK.

The module retains the existing `v26_2` Java package, `Minecraft26WorldAccess`
factory, and target-specific artifact names. `gradle/minecraft-artifacts.json`
defines the verified binary release families for Fabric and NeoForge, including
each family's build target and supported Minecraft versions. Candidate intervals
are verified with the same JAR on each target and reviewed before admission.

### Default distribution

The development distribution bundles **one universal Builder 0.4.0 JAR** from
`OpenAllay-Extensions`. The current lock at `distribution/extensions.lock.json`
pins source `367556f2e5f9baf377532b4bde016dee5dfd7d50` and one artifact path.
Both loaders contain the same raw resource at
`META-INF/openallay/bundled-extensions/openallay-builder-universal-0.4.0.jar`.
Builder is not registered as a Fabric or NeoForge mod. The host supplies public
Extension API 0.4.0 and the native game adapter. Enabling Builder includes building
and world writes; there is no separate Extension-private approval.
Builder's domain code, Skills, JavaScript, templates and journals remain one
Java-8 Extension payload with privately shaded Gson.

At normal startup, community packages in `config/openallay/extensions/` are
admitted first. An ACTIVE package with the same ID takes precedence over the
bundled package. Otherwise, the host verifies the bundled SHA-256 and manifest,
stages it under `config/openallay/.bundled-extensions/<sha256>/`, and uses the
same universal discovery path. It never overwrites a community JAR. Accepted
classloaders remain open until actual invocation workers finish at shutdown.

The published **v0.4.1** downloads remain unchanged: Builder 0.2.1 and legacy API
0.2.2. This development switch does not amend that release. Product, Extension
and API versions are independent. A Java-8 Extension is not proof that the core
runs on stock Forge 1.12.2. Player automation and Baritone remain research-only;
installation does not enable Agent JVM authority.

Prepare the exact source before a full distribution build:

```bash
python3 scripts/prepare-distribution.py
./gradlew clean :common:test :fabric:build :neoforge:build
```

Preparation uses ignored local build state. Gradle does not download Extension
source implicitly; missing, modified, or wrong-revision source fails the build.
`./gradlew :common:jar` and `./gradlew :common:test` can bootstrap without it.

- `-PbundleExtensions=false` makes an explicit core-only developer build.
- `-PopenallayExtensionsDir=../OpenAllay-Extensions -PallowUnpinnedExtensions=true`
  supports coordinated development against a local checkout.

Distribution verification rejects both core-only and unpinned builds. Before
shipping coordinated changes, commit the Extension, update the lock, prepare its
exact revision, and rerun the ordinary full gate. No source checkout or Python
process runs inside Minecraft.

### Development instances

```bash
./gradlew :fabric:runClient
./gradlew :fabric:runServer
./gradlew :neoforge:runClient
./gradlew :neoforge:runServer
```

Dedicated validation is headless. Fabric accepts `--args nogui`. NeoForge's
launcher is already headless; do not pass Gradle `--args`, which replaces its
launch main class.

The accepted Fabric 26.2 full-mod profile uses optional Architectury Fabric
21.0.4. Versions through 21.0.2 break text input and are marked incompatible.
Version 21.0.3 has not been verified: it is not blocked, but compatibility is
unknown. Architectury is not required. NeoForge is unaffected. See the
[Fabric content profile](verification/2026-07-25-fabric-26.2-content-profile.md).

## Runtime configuration

### Client models and credentials

Client profiles use the strict current shape in `config/openallay/models.json`,
without a format-version field or legacy import path. Missing configuration starts
unconfigured; malformed files stay untouched and produce a redacted notice.
Saving valid configuration is explicit.

Players enter keys through the native masked field. The credential store is
`config/openallay/credentials.sqlite3`; profiles retain only immutable
`local:<uuid>` references. Stored keys are never filled back into the widget or
exposed in copy/cut, settings snapshots, diagnostics, logs, packets, prompts,
traces, or history. POSIX owner-only permissions are best effort; this is local
restrictive storage, not an OS-native vault.

For externally authored development or headless configuration, use an
environment reference instead. The normal player UI does not request this form:

```json
{
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

`reasoningEffort` is optional. The Models page shows **Provider default (AUTO)**
or an explicit protocol effort. AUTO omits the wire field; the actual gateway
default is unknown. OpenAI Chat sends `reasoning_effort` and offers
`none`, `minimal`, `low`, `medium`, `high`, `xhigh`, and `max`. Anthropic sends
`output_config.effort` and offers `low`, `medium`, `high`, `xhigh`, and `max`.
This does not implicitly enable Anthropic thinking or set a thinking-token budget.
A specific model/gateway can reject an explicit choice; no name-based capability
ban or silent downgrade is applied. Active requests keep their captured effort.

`anthropic_messages` is the other protocol. Remote endpoints require HTTPS;
HTTP is allowed only for loopback development. Inline `apiKey` and `apiKeyEnv`
are invalid. The numbers above are manual examples, not defaults.

Context resolution is explicit `contextWindowTokens`, then exact trusted
provider metadata/cache, then an eligible deterministic BEST match in the
bundled catalog. Canonical/upstream IDs and published aliases precede provider
wrapper normalization and family/version-aware similarity. Unrelated unknown
models require manual context. Explicit values win, including a 1,000,000-token
budget; there is no 32K fallback or arbitrary context cap. An untouched automatic
context stays omitted on save; only an actual edit makes it manual.

Output is also manual or automatic:

- Explicit `maxOutputTokens` remains the manual budget.
- Omitted or JSON-null output resolves the exact trusted provider maximum, then
  the eligible bundled maximum. Clearing the native output field restores auto.
- An unknown maximum requires manual configuration. There is no 8192/4096
  fallback or guessed context-derived maximum.

Resolved runtime limits must satisfy `ContextBudget`: context exceeds two output
reserves. Neither value is silently clamped. The matched `gpt-6-luna` publishes a
128,000-token output maximum; an explicit 1,000,000-token context stays unchanged.
Published capability data does not promise every gateway accepts that maximum.
Disabled profiles may retain unresolved context/output without breaking settings
or diagnostics; enabled profiles need resolved or explicit limits.

The bundled catalog works offline at arbitrary OpenAI-compatible endpoints,
including gateways whose authenticated `/models` returns IDs without limits.
Settings shows the match, limits, source/capture time, and published
USD-per-million-token price tiers as reference estimates, not gateway billing
claims. Resolution never changes model ID, endpoint, protocol, payer, credential,
profile selection, or a manual budget. See
[decision 036](isme/decisions/2026-10-01-036-builtin-model-catalog.md) and
[decision 040](isme/decisions/2026-10-01-040-manual-runtime-root-causes.md).

### Metadata and catalog maintenance

`config/openallay/model-metadata.json` is the strict current trusted capability
cache; it contains no prices or builtin matches. Startup reads it asynchronously.
A cache miss refreshes in the background; failure retains prior cache and explicit
configuration. OpenRouter uses `GET /api/v1/models` fields `id`, `canonical_slug`,
`context_length`, and optional `top_provider.max_completion_tokens`.
Metadata listing/refresh is configuration I/O, not an Agent Tool or inference
acceptance. HTTP transport mechanics can be shared, but provider and future
knowledge clients keep separate credentials, permissions, codecs, and evidence.

The strict bundled resource is `data/openallay/models/builtin-model-catalog.json`.
Its refresh is an explicit developer operation:

```bash
python3 -m unittest discover -s scripts -p test_update_builtin_model_catalog.py -v
python3 scripts/update-builtin-model-catalog.py --fetch-public --output /tmp/builtin-model-catalog.proposal.json
```

The updater reads fixed unauthenticated public sources into a proposal. Review
identities, aliases, limits, decimal price units/tiers, and source changes before
replacing the resource. Runtime does not fetch those URLs. Keep
`data/openallay/models/LICENSE.models.dev`. See the
[source evidence and offline refresh procedure](verification/builtin-model-catalog/README.md).

### Settings publication and model ownership

The common native settings screen contains General, Models, Extensions, Skills,
History, Diagnostics, and About. Models supports profile editing, defaults,
external reload, metadata refresh, authenticated `/models` listing, and an
explicit connection test. Listing uses a currently typed key first, otherwise
the saved credential. Headers stay within the validated provider origin;
Anthropic catalog requests use native headers plus Bearer gateway compatibility.
The model ID remains editable.

A save validates the full candidate, stages any new immutable credential,
atomically replaces `models.json`, then publishes the prepared runtime. Changes
affect future requests. Active requests retain their captured runtime and key;
unreachable credential rows are collected only after successful publication.
Missing files create only a disabled in-memory draft until explicit save.

The server owns `config/openallay/server-model.json`, a separate format.
A valid runtime is constructed at startup before it is advertised. Missing,
disabled, or invalid server configuration advertises no model and records only a
server-local diagnostic. Clients see a synchronized, read-only server choice;
client settings cannot edit, test, delete, or persist it, and client packets never
contain its key. Server selection is connection-scoped. Disconnect restores the
local default for future requests; active requests retain captured runtime until
terminal cleanup. See
[model ownership and bridge decision 021](isme/decisions/2026-07-19-021-model-ownership-and-player-tool-bridge.md).

The connection test warns about cost and requires a second confirmation. It
sends one non-streaming, non-retrying request with at most 64 output tokens and
no history, Tools, Skills, game state, evidence, or trace. Assistant text and
provider bodies are discarded. Only category, redacted authority, protocol,
completion time, and latency remain. Closing settings cancels a probe or discards
drafts, but does not cancel an already confirmed configuration/history write.

`connectTimeoutSeconds` covers connection establishment. `requestTimeoutSeconds`
is the full budget for one dispatched attempt, including headers, streaming, and
decoding. A cancellable watchdog closes stalled bodies with `model_timeout`.
Cancellation, timeout, or completion wins once; late bytes cannot mutate state.

### Display, history, and diagnostics

`config/openallay/display.json` has the strict current shape. Missing files use:

```json
{
  "debugMode": false,
  "animationsEnabled": true,
  "assistantName": "OpenAllay"
}
```

`assistantName` is trimmed, nonblank, and control-free. It changes local
presentation, not saved content, IDs, evidence, or model/Tool configuration.
Animations affect progress presentation only. Invalid startup files keep Debug
off and stay untouched; reload retains the last valid projection. Atomic General-page saves reproject through
`GuideDisplayRuntime` without a restart.

Normal Tool details put useful results and actual safe error codes/messages first.
Origins are auxiliary grouped details, initially collapsed, with distinct-source
counts and actual capture ranges. Authority and coverage remain distinguishable;
client-visible or imported data is not called server-confirmed. Debug places the
submitted program and input/output before origin details. Programs, raw JSON,
technical identities, and metadata remain Debug-only. Diagnostics exclude
secrets, provider bodies, reasoning, transcripts, raw history scopes, paths,
actors, and request/cursor payloads.

Source diagnostics consume counts-only immutable state published by the existing
knowledge reload, not live captures from settings rendering. Known empty is
separate from unknown/not captured; failures may retain valid counts as partial.
Disconnect, shutdown, and player/world scope replacement clear published source
facts and old primary-provider handles. Saved source configuration and enablement
policy remain; a new connection is unknown until fresh capture.

Context diagnostics use the latest actual local request estimate, bound to the
selected model/session/request, not the sum of compaction checkpoints. Server
models, pre-dispatch requests, and replaced or disconnected endpoints report
unknown. Pending-write/active-request counts describe current work; checkpoint
counts describe retained compaction records. Debug adds count-based history
window/cache/fallback metrics and context estimates only. See
[decision 040](isme/decisions/2026-10-01-040-manual-runtime-root-causes.md).

History settings delegates to `GuideService` and its ordered repository without
raw database paths, scopes, or SQL. Deleting the current partition or all current
player partitions needs a fresh one-use confirmation and fails
`history_delete_busy` during matching requests/writes. Whole-database reset is
Debug-only and needs a distinct second confirmation. Shutdown disconnects the
service, closes ordered history, cancels probes, and closes metadata cache
asynchronously on both loaders.

## Guide requests, history, and UI

### Native commands

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
/guide model list
/guide model profile <profile-id>
/guide model client
/guide model server
```

`client` restores that session's last named profile without silently choosing
another. The screen uses
an explicit profile/server selector. Switching during work changes the next
request; status still names the model captured by the running request.

Development Tool inspection requires game-master permission:

```text
/openallay dev tools
```

Commands, screens, and probes use one connection-scoped `GuideService` for
sessions, routing, immutable snapshots, cancellation, retry, and cleanup. A
player can run requests in different sessions concurrently; the same session
returns `agent_busy`. There is no fixed global concurrency or queue-count cap.
HTTP 429 gates only the matching endpoint, honors `Retry-After`, and otherwise
uses cancellable exponential backoff with fair session rotation.

### Model and Tool placement

Model location and Tool location are independent. Placement is deterministic:
a server model uses an advertised player-client Tool when available, otherwise
a server Tool. Explicit server execution remains server-authoritative; client
observations remain client-visible. A script is not parsed, rerouted, or retried
elsewhere because of its data access.

Clients advertise only the initiating player's enabled, registered read-only
Tool IDs; the server intersects them with its trusted registry. Calls/results
remain bound to actor, request, invocation, and frozen capabilities. Invalid
client settings advertise an empty set, not all Tools. Live capture runs on the
owning Minecraft thread. Tool execution, normalization, and chunk encoding run
on a virtual worker; packet sends return to the client thread.

Remote unavailability, malformed results, and the five-minute Tool deadline
become structured Tool failures. Cancellation, disconnect, and shutdown remain
terminal. Incomplete assemblies are sparse, active-request-only, and expire at
the deadline or terminal cleanup. This bridge adds no cross-side world RPC,
remote unrestricted Java, server commands, or client ghost writes.

The optional `world` binding captures focused loaded blocks/entities through the
selected execution side. Values detach before Rhino; entity observation IDs are
opaque and request-local. Unloaded areas remain explicit partial coverage.
Cancellation/disconnect stops later capture slices and closes the workspace.

### Real model context and durable records

The provider-neutral transcript owns model context; UI events are its projection,
not an input reconstruction recipe. Preserve original user/assistant
exchanges, actual ToolUse arguments, paired complete compact ToolResult text and
its error bit, and terminal outcomes. Do not fabricate restored arguments or
replace useful errors with display summaries. Durable records exclude provider-private reasoning, framework model configuration,
authentication headers, HTTP bodies, live objects, and canonical workspace trees.
Player and tool data are not scanned or rewritten for sensitive-looking content.

Original recorded exchanges remain separate from the active compacted context
snapshot/checkpoint. Persist both without reversing a UI projection.
Compaction summarizes older complete units against the selected model's actual
budget without overwriting originals, inventing malformed-result errors, or
filtering by hardcoded domain fields. Summaries are not game-fact evidence.
Active context has complete use/result pairs; original diagnostic history may
honestly mark an unfinished call. Failure preserves completed exchanges/errors;
cancellation never marks unfinished work successful. Process loss restores
`INTERRUPTED` and never replays a provider call or Tool side effect.

The session/lease owns only retained Skill range facts; the active model projection
owns the instruction text. Each request publishes a concise loaded-document manifest
with exact full/partial ranges and available references. It is factual state, not a
prompt prohibition. Duplicate loads can remain visible calls but reuse valid retained
ranges without appending the text again. Client document identity stays client-owned
when switching between local and shared-server models. Compaction loss, changed or
deleted documents, and cancelled old leases update only their actual ranges.

Hydrate only a missing live session from matching durable context. Later asks in
the same connection reuse it. Skill plaintext already present after compaction
is reused by exact document fingerprint and loaded range, not a permanent loaded
flag. Changed/deleted documents invalidate only their text; a dropped range can
be loaded again. Model changes retain recorded history and re-estimate the active
context against the newly selected budget.

The stable file is `config/openallay/history.sqlite3`, with current shape
validation and no internal version/migration/old-layout reader. Malformed data
reports an error without implicit reset, rewrite, or deletion. Partition keys
hash player UUID, connection kind, and normalized world path/server address;
raw paths/addresses are not stored. Session/actor/world partitions and late-event
generation checks remain intact. One ordered background worker owns writes.
Startup restores session metadata; UI pages remain separate from context reads.
Loading temporarily disables submission. Persistence failure leaves the
in-memory Agent usable but marks new records non-durable.

Both loaders use the same current common packet codec. Malformed events fail
only their correlated request, with no compatibility alias or silent fallback.
Server-model context carries real authorized exchanges, actual context budget,
and canonical model identity, not captured capabilities, credentials, provider
reasoning, or live objects. Strict SHA-256-checked 24 KiB chunks carry long
requests. Relative attempt budgets keep client display clocks separate from the
server-owned watchdog.

Durable Tool IDs stay request-qualified. Provider codecs preserve valid IDs and
deterministically map invalid outbound IDs without losing Tool-call/result pairs.
OpenAI-compatible IDs follow the endpoint's 64-character field and alphabet,
not a new product cap.

### UI and native views

`K` (`key.openallay.open_guide`) and bare `/guide` open the same non-pausing native
Screen on both loaders. Closing it detaches only the UI subscription; work
continues. Reopening projects the latest immutable snapshot. Enter sends from
the composer, Shift+Enter inserts a newline, and Ctrl+Enter also sends.
Other focused widgets keep their normal Enter action.

The common UI virtualizes wrapped, variable-height history with stable anchors,
safe Markdown, validated game references, controlled components, and semantic
fallbacks. Model text and Tool cards follow actual event order; reasoning is
absent from the UI type. Progress clocks reproject locally without transcript
rows or history writes. A terminal raw pending Tool displays **Stopped before a
result was recorded** without altering its status or inventing an outcome.
Retry resends the question rather than resuming a step; Stop prevents future
work, not completed effects. Escape closes details before the Guide.

Session deletion has two confirmations bound to the selected session; deletion
removes durable history and stops active work. Clipboard actions are local.
Export uses original records in actual order, including useful recorded
calls, results, and errors, independently of active context compaction. It writes
UTF-8 atomically below `openallay/exports` in the game directory, accepts no path,
and rejects symlink escape. Provider reasoning/configuration and live workspace state are excluded; player
and tool content is preserved rather than scanned. Existing exports stay unchanged.

`recipe_grid` binds only to the complete normalized result of its same-request
Tool invocation. Native views have a client-thread-only lifecycle and release
when rows leave the viewport or the screen closes. JEI 26.2 re-resolves exact
references against its current generation and embeds public
`IRecipeLayoutDrawable` drawing, overlays/tooltips, and ticks. Missing, stale,
oversized, or failed layouts use the neutral labelled slot canvas. REI reports
`rei_exact_embedding_unsupported` and uses that fallback; it does not claim an
unverified exact durable-reference contract.

## JavaScript and Extension contracts

### Analysis surface

The model-facing Tools are `openallay:run_javascript`, `openallay:load_skill`, and
`openallay:manage_skill`, subject to the frozen request catalog/policy. Removed
domain Tool families are not a parallel catalog. Recipes, inventory, knowledge,
game state, and adapters live in the JavaScript host graph. Recipe capture options
remain at `config/openallay/tools/recipes-options.json`, not per-Tool settings.

OpenAllay embeds the KubeJS-Mods Rhino fork, not KubeJS itself. Each invocation
has a fresh context over detached immutable values. `MinecraftAgentHostGraph`
keeps original records/lists; `RhinoHostAdapter` exposes components, collections,
String-keyed maps, optionals, scalars, and Gson leaves as lazy read-only views.
It does not serialize input into source or a second Gson tree, use `JSON.parse`,
or expose Java methods/reflection through detached host views.

There is no `roots` input. One lazy invocation view exposes all already authorized
detached `mc` roots. Access `mc.player.position`, `mc.game`, or `mc.items` directly.
Enumeration/schema discovery does not resolve unrelated suppliers. A declared
but uncaptured root gives an actionable unavailable error; unknown ordinary
properties retain JavaScript semantics. No parser guesses dependencies or retries
execution elsewhere. Return supported computation data, not executable
modules/functions; an invalid return does not prove terrain or an adapter is absent.
For example, both programs need only their `source` text, without a data selector:

```javascript
return 6 * 7;
return mc.items.filter(item => /chest/.test(item.id)).map(item => item.id);
```

Available captured views include:

```text
mc.capabilities
mc.items / blocks / fluids / effects / enchantments / entities
mc.registryEntries / registries
mc.recipes / recipeCatalog
mc.player / game
mc.knowledge / knowledgeCatalog
mc.extensions / extensionCatalog / extensionDiagnostics
```

`world` and `commands` depend on frozen runtime capabilities, not model selection.
The Agent chooses observations when useful; no fixed observe/build/check workflow is imposed.

`world.focus()` samples current client-visible focus, camera, hit target, hands, and known
screen/menu/hover facts. It is distinct from the optional, detached `mc.ui.focus` sample in an invocation
snapshot and from the input's original `inputObservation` anchor. `world.capture({target:"WORLD"})`
returns a managed image reference with native source identity and time:

- `WORLD` captures before native GUI rendering. It includes the real first-person hand
  and 3D effects, but not 2D HUD or the Guide.
- `GAME_UI` captures the currently visible native game UI/HUD. It is unavailable while
  OpenAllay is foreground; it does not swap screens to fabricate a current game view.
- `ASSOCIATED_UI` reads the exact retained pre-Guide source associated with this request.
  Its source ID, capture ID, time, image and screen remain those of the original source.
  The result target identifies the associated-view query, not a new source frame. Missing sources are unavailable, not replaced
  with a current frame.

Image-bearing Tool results keep typed references in canonical `ToolResult.images`.
Anthropic receives native nested Tool images. OpenAI receives its text Tool reply followed
by an attributed provider-only visual supplement; canonical history does not gain a player
turn. Server-provided models use the existing request-scoped client Tool transport. Native
image production, import, resolver authority and final image handoff retain actor/request
ownership. Trusted native completion waiting is excluded from the interpreter budget;
argument decoding, result adaptation and model-authored JavaScript remain budgeted.

The canonical `ModelMessage.inputObservation` records what a player input referred to.
Queue, edit, Steer, retry, history and exports keep that input's own reference, rather than
reading the latest draft. Ordinary image attachments remain separate. When an anchor image is submitted without text or ordinary attachments,
the input uses an empty text carrier and projects that anchor image once, without filler
words or a duplicate ordinary attachment.
The UI lets players refresh/remove focus, attach/remove a frame and inspect real Tool images.
Opening Guide from a native menu uses the configured key after native focused-text handling;
it retains the source frame before opening the Guide.

`schema.list()` reports roots/availability without resolving data;
`schema.describe("game.mods.installed")` walks stable paths from the same
KubeJS-Rhino `TypeInfo` catalog. Use a focused `helpers.schema` sample for dynamic
registry properties, recipe extensions, or adapter values.

Host arrays support normal non-mutating `filter`, `map`, `reduce`, and `flatMap`.
Copy with those operations or `slice` before `sort`, `reverse`, or `splice`.
For nested lookups in repeated callbacks, use an indexed `for (var index = 0; ...)`
loop: Rhino 2101 can report redeclarations for inner `find`/`map` callbacks with
block-scoped locals. Bundled examples use the tested form.

Models should include short player-language `title` and `description` on every
new JavaScript call. Missing/null/blank strings use localized defaults;
non-string live values fail `invalid_arguments`. Cards render literal model
text as **Planned action**, separate from status/results. Intent changes neither
source, duplicate identity, permission, nor factual authority. The submitted
arguments belong to the original exchange, not just its UI projection.

A supported return is a successful computation, even with no sources. Generic
JavaScript execution has no post-execution evidence gate; never fabricate a read
to admit a result. Actual factual snapshots and genuinely `EvidenceBearing`
types retain strict immutable metadata and normalization. The `source` argument
is the JavaScript program, not a provenance request.

Origins are auxiliary. Successful accesses record actual first/last capture times
for a stable source identity; authority, coverage, scope, provenance, and external
versions stay distinct. Repeated references do not create per-block source lists
or count inherited workspace values as fresh captures. Plain computation has no
origins; qualifications appear where they affect interpretation, not as a required
footer or success condition.

Canonical results stay internal for UI/workspace/normalization. Providers receive
compact text or an explicit preview; the recorded exchange retains that actual
text and error state. Large values use opaque handles reopened with
`workspace.open(handle)`, inheriting only actually used origins. Handles and
canonical values last for the current request only, never across questions or
restart; terminal cleanup closes local and callback workspaces.

### Safe and unrestricted execution

Safe mode denies arbitrary Java wrappers, reflection, class loading, network,
process, filesystem, and live game access. Instruction observation checks
cancellation and a monotonic deadline. Unsupported, cyclic, or non-finite results
fail with sanitized useful type/message/location and stable `javascript_*` codes.
Safe resource budgets and bounded previews apply without inventing empty results,
domain limits, or success claims.

Unrestricted JavaScript is a separate default-off setting at
`config/openallay/unrestricted-javascript.json`. Only client-local model requests
can capture it. Server-model requests and all server-originated client callbacks
always capture safe mode, even when the local toggle is on. Missing/invalid
configuration disables it; changes affect future requests only.

Authorized execution gets Rhino standard objects and `Java.type`, bypassing
OpenAllay source, interpreter-time, result-normalization, workspace,
handle-selection, and preview budgets. Immutable `mc` views, request correlation,
cancellation, factual-type validation, and terminal cleanup remain. Provider input
is re-estimated against the selected budget before every continuation in both modes.
JVM file/network/process/live-object effects are possible; cancellation is not
rollback and cannot guarantee interruption of blocking Java/native calls. Live
Minecraft access must be scheduled to the owning thread. Never disclose
JVM-accessible credentials through context, results, traces, logs, or answers.

The frozen authorized Skill catalog automatically includes
`unrestricted-javascript` guidance and its loadable interop reference. Guidance
teaches existing authority; it cannot grant it, override deny policy, or make
Java values evidence. Ordinary/server requests do not advertise it. See
[decision 033](isme/decisions/2026-09-29-033-unrestricted-javascript-mode.md).

### Trusted adapters and packages

Optional integrations capture public mod API state on the owning Minecraft
thread and detach it. Worker-side `JavascriptDataModule` implementations project
only immutable `ToolInvocationContext` values into `mc.extensions` with actual
snapshot metadata; they cannot call live APIs or use reflection. Capture failure
isolates the module; unsupported nested values fail at the accessed property. Unrestricted
Java interop does not turn adapter values into mutable/live objects.

JEI and REI are loader-discovered first-party Extensions. Their detached data
reports provider availability, completeness, generation-bearing references,
categories, diagnostics, and navigation capabilities. JEI supports exact
navigation/native layouts; REI supports item-focused recipe/usage navigation,
not exact durable-reference navigation. Provider loss degrades only that
projection. EMI has no verified compatible 26.2 public API artifact and is not
compiled against or advertised.

External startup registration uses `OpenAllayFabric.registerExtension(...)` in a
Fabric `ModInitializer` or `OpenAllayNeoForge.registerExtension(...)` in a
NeoForge constructor. Early candidates queue; late ones validate against the
live registry. There is no class scan or hot-loading; JAR installation requires
a loader restart.

Reusable modules, such as `require("openallay:crafting")`, compose analysis in
one JavaScript batch. Crafting reports observed allocation, missing requirements,
maximum crafts, and conclusiveness; it does not recursively craft intermediates.
Exact module IDs resolve only bundled resources, cached within an execution,
and grant no authority.

Extensions settings projects installed roots, adapters, modules, Skills, and
native views without capture/invocation. Community packages use the strict
schema-2 catalog at
`https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Extensions/main/catalog.json`,
cached at `config/openallay/catalogs/extensions.json`. Each logical ID/version
has loader-specific URL, SHA-256, and mod IDs. Local and community JARs must pass
actual checksum, compatibility, loader metadata, and embedded schema-1
`META-INF/openallay-extension.json` validation before atomic installation under
a managed name in `mods`. Local imports need no catalog entry. Status stays
`restart_required` until startup registers the Extension.

The public authoring repository's `examples/hello-extension` builds both loaders.
Current product 0.4.2 implements public Extension API 0.4.0. Loader product
ranges and `openAllayApiVersionRange` are separate compatibility contracts:
`[0.4.0,0.5.0)` accepts API 0.4.0, but a loader product range excluding
0.4.2 rejects this release. Existing four-list and five-list contribution
constructors remain supported. Match each independent Extension's declared
requirements rather than copying the product version into its API range.

### Invocation scopes, requirements, and Builder

API 0.2.1 introduced optional `JavascriptInvocationParticipant` contributions.
Participants open on the JavaScript worker and close in reverse order on that
worker. `JavascriptInvocationContext` supplies immutable invocation state,
cancellation, `requireActive()`, evidence recording, and
`completedSuccessfully()`. The latter means the body returned normally in an
active scope, not that a domain action, normalization, or evidence validation
succeeded. Closing revokes scope lifetime. Queued owner-thread actions must
recheck scope and exact connection/world identity. Participants alone add no live
host binding or domain Tool.

Legacy API 0.2.2 introduced namespaced `JavascriptHostBinding` methods.
The current native-neutral SDK 0.4.0 exposes explicit methods with controlled
argument/return types, not arbitrary Java reflection. It has no Extension-private
capability declarations, grant files, or approval switches. Enabling an Extension
makes its registered operations available to future requests. Each invocation still
checks its active scope, cancellation, owning Extension, and exact native session.
Unrestricted JavaScript includes game commands and enabled Extension operations;
it does not bypass Minecraft server permissions or missing components.

Skills use advisory `openallay/requires-capabilities`,
`openallay/requires-extensions`, and `openallay/requires-skills` metadata;
Extension descriptors/manifests/catalogs use equivalent `requirements` arrays.
These grant nothing and add no installation/runtime gate. Package review can
enable available requirements explicitly, cancel, or Continue anyway. Continuing
publishes only the validated package, without dependency installation or hidden
authorization. Required-mods compatibility and actual Tool policy remain separate.
See [decision 035](isme/decisions/2026-09-30-035-advisory-extension-skill-requirements.md).

Builder code, Skill, modules, native scheduling, templates, and journals belong
to `OpenAllay-Extensions`, not core. Development Builder 0.4.0 declares exact
candidate target/loader pairs, product `[0.4.1,)`, public Extension API
`[0.4.0,0.5.0)`, and the actual `minecraft:world-access` host feature. These source
declarations do not establish game acceptance or widen the published binary ranges.
The native adapter uses the active integrated server through invocation-scoped
`WorldSession` operations. The published v0.4.1 Builder 0.2.1 contract remains historical.
Enabling Builder includes its building and world-write operations. It does not need
unrestricted JavaScript or another private grant. It does not edit client world
mirrors, open offline saves, add a remote write protocol, or fall back to commands.
Unsupported contexts fail explicitly. Native scans, geometry, paste, and undo
batch owner-thread work; a cooperative quantum is scheduling, not a volume cap.

Builder exposes fresh dense `read_region` and sparse ordered `get_blocks` for batched
verification. `batch(callback)` groups placements with read/lifecycle barriers.
`update_connections` performs deterministic native shape normalization. The explicit
`sync_physics` API performs full neighbor/comparator notifications; normal building
no longer runs this expensive full-volume phase implicitly. Native hooks can exceed
the cooperative admission deadline. Neither call promises settled later game physics.

Current journals/templates have no internal version or old-format reader.
Journals use a base checkpoint plus strictly sequenced atomic deltas. Force intent
before mutation and persist actual readback before the next quantum. Publish a
complete checkpoint before deleting covered deltas; missing/corrupt uncovered
records fail explicitly. IO stays off Minecraft owners and no Rhino callback runs
on them. Restart never replays writes or rolls back effects automatically;
conflict checks and honest partial outcomes remain. Batching does not establish
a runtime speed claim. See decision 041 for the selected representation and
[decision 034](isme/decisions/2026-09-30-034-extension-online-construction.md) for
unchanged online authority and partial-failure boundaries.

### Experimental commands

In isolated JavaScript mode, `config/openallay/experimental-commands.json`
controls the optional game-command setting. Unrestricted JavaScript already includes
game commands. A request captures that effective choice and the actual player route
before execution. When the route is available, it exposes the binding directly:

```javascript
commands.list()
commands.describe(path)
commands.run(command)
```

This is a top-level binding, not `mc.commands`. Without an effective command
setting and a captured player route, the binding and matching Skill are absent.
Explicitly disabled Tools, Skills, and Extensions remain disabled. The detached Brigadier catalog includes
all vanilla/server/loader/mod commands visible to that player. Execution removes
at most one leading slash, then uses the normal player route on the client
thread. OpenAllay adds no allowlist, argument restriction, or call-count cap;
Minecraft parsing, connection, identity, and permissions remain authoritative.

Calls serialize per player. The Rhino worker waits for the ordered non-overlay
feedback window and returns `state`, `messages`, and `durationMillis` without
blocking render. `feedback`/`no_feedback` is not universal success. Submitted
commands are not rolled back by later failure or cancellation; verifying world
effects needs a later observation.

## Knowledge and Skills

Patchouli is read from active client resources without a binary dependency.
Locale order is active locale, `zh_cn`, then `en_us`. Entries whose gated
visibility cannot be proven are excluded. Text, item/recipe links, and
multiblocks are indexed. Each successful reload atomically publishes one
immutable in-memory index and matching evidence. Search protects exact document
and linked item/recipe IDs, then weights aliases, metadata, titles, and headings
before Unicode-aware BM25-style section scoring. Results retain
`sourceId`/`documentId`, stable heading-derived `sectionId`, and evidenced excerpt.

The retriever is pure Java over detached generations, not a second SQLite FTS
lifecycle. Future ranking adapters must retain exact identities, provenance,
evidence, and the offline path. See
[retrieval decision 022](isme/decisions/2026-07-19-022-native-domain-views-retrieval-memory.md).
Optional FTB Quests uses allowlisted public method handles, not private reflection,
and captures only team-visible content. Absent compatible 26.2 APIs produce an
integration diagnostic, not Agent failure.

Skills are non-executable instruction packages:

```text
skills/<skill-name>/
├── SKILL.md
├── references/     # optional read-only Markdown/text
└── assets/         # optional non-executable resources
```

Frontmatter requires `name` and `description`; directory/name must match.
Optional fields and namespaced string metadata are strictly validated.
`allowed-tools` is a dependency, not permission. Scripts, URL references, root
escape, unsafe symlinks, arbitrary paths, and unsupported files are rejected.

Bundled packages are immutable; valid local packages under
`config/openallay/skills/` override the same bundled name. Settings and
`openallay:manage_skill` share the confined contract. Create/update/delete take
an exact name, complete `SKILL.md`, and optional Markdown references, never a
path. Candidates stage, parse, dependency-check, and publish atomically for
future requests; deleting an override reveals the bundled package.
`openallay:load_skill` reads at most 8192 characters per chunk. Continue incomplete
reads with the returned opaque cursor, bound to document and fingerprint;
malformed, foreign, or stale cursors fail closed.

The schema-2 community catalog includes display name, description, and publisher,
plus validated package/version/source/archive/checksum/compatibility. Catalog
copy is not Agent instruction authority; the installed validated `SKILL.md` is.
`openallay/version` is the durable package version. Skill API compatibility remains
0.2, independent of product patch versions. Cache lives at `config/openallay/catalogs/skills.json`.

Either community tab starts one non-blocking refresh on first visit while its
valid cache stays usable. Failure keeps the cache and one diagnostic; explicit
refresh retries. Catalog/install/import are configuration operations, not Tools.
Skills cannot fetch URLs, execute modules, register Tools, expand the sandbox,
or grant authority.

## Verification

### Offline gates and acceptance scope

Run common tests and both loader builds after relevant changes; use the full
pinned-distribution gate before shipping. The `Quality` workflow also validates
automation, distribution scripts, distribution/SQLite packaging, and one production
JAR per loader. Deterministic tests/builds do not establish graphical gameplay or
paid-provider acceptance. OpenAI-compatible optional `tool_calls`, usage, and
usage-detail fields may be absent/null; invalid non-null shapes and malformed
Tool arguments remain failures.

The [refactor plan](superpowers/plans/2026-10-01-execution-context-simplification.md)
tracks common, Extension, both-loader, and pinned-package gates. Earlier hotfix
results do not verify this refactor or measure its speed. This source task does
not change product versions, create a tag, or publish a release.

Authorized releases use a separate annotated strict-SemVer tag workflow matching
`gradle.properties`, with pinned source/tests/packaging gates and
`MODRINTH_TOKEN`. Publication to Modrinth then GitHub is ordered, not atomic;
verify both services and hashes. Curated notes live in `docs/releases/<version>.md`.

### Agent benchmark

The strict current corpus is
`engine-core/src/main/resources/data/openallay/benchmarks/core.json`. Offline fixtures
verify predicates, selection, and capability coverage:

```bash
scripts/run-agent-benchmark.sh deterministic
```

Live mode is explicit and billable. It runs the production Agent, protocol
adapter, registry, Rhino, Skills, normalization, and trace path for the local
JavaScript fixture:

```bash
OPENALLAY_MODEL_BASE_URL='https://provider.example/v1/' \
OPENALLAY_MODEL='provider/model-id' \
OPENALLAY_API_KEY='...' \
OPENALLAY_BENCHMARK_REPEATS=3 \
scripts/run-agent-benchmark.sh live
```

For reproducible named-profile selection, use credential-free current model
configuration with an environment reference resolved in the benchmark process:

```bash
OPENROUTER_API_KEY='...' \
scripts/run-agent-benchmark.sh live \
  --profile config/openallay/models.json \
  --profile-id openrouter-main \
  --repeats 3
```

`--cases` (or comma-separated `OPENALLAY_BENCHMARK_CASES`), `--include-commands`
(or `OPENALLAY_BENCHMARK_INCLUDE_COMMANDS=true`), and `--output` select cases,
opt-in writes, and report location. Command cases are excluded by default.
Unavailable cases print `OPENALLAY_BENCHMARK_SKIPPED` and retain a current-shape
selection plan; explicitly selected unavailable cases fail fast. Detached food
ranking and recipe/inventory fixtures do not expose expected answers to the model.
Server routing has a separate deterministic GuideService fixture, not the local
provider fixture.

Reports under `build/reports/openallay/benchmarks/` retain corpus identity/commit,
profile/canonical model identity, redacted authority, typed attempts, complete
provider-neutral traces, success probability, counters, and median calls.
Sibling `*-audit.json` records evidence/code/event/Tool IDs for unambiguous stable
errors; ambiguous/multi-domain failures remain `UNRESOLVED`. Wall-clock timing is
diagnostic, not scoring. Neither report contains keys or authorization data.

### Live provider and settings probes

Normal tests skip network calls. Export keys only in the environment:

```bash
OPENALLAY_MODEL_BASE_URL=https://provider.example/v1/ \
OPENALLAY_MODEL=model-id \
OPENALLAY_API_KEY=... \
OPENALLAY_MODEL_PROTOCOL=ANTHROPIC_MESSAGES \
./scripts/live-model-smoke.sh
```

This checks streaming, Tool continuation, grounded Chinese output, and separation
of framework provider configuration from the recorded conversation.
For Rhino/Skill/batched sword and container scenarios:

```bash
OPENALLAY_MODEL_BASE_URL=https://provider.example/v1/ \
OPENALLAY_MODEL=model-id \
OPENALLAY_API_KEY=... \
OPENALLAY_MODEL_PROTOCOL=OPENAI_CHAT \
./scripts/live-model-smoke.sh javascript-agent
```

`OPENALLAY_LIVE_STREAM=false` isolates stream transport.
`OPENALLAY_LIVE_JAVASCRIPT_SCENARIO=sword` or `container` selects one scenario;
default `all` runs both. Retained diagnostics are task, Tool ID, script,
result-summary, and invocation-count projections.

For the exact native connection-probe contract, put a strict current profile in
an ignored file such as `run/openallay/settings-probe.json`, using
`"credentialRef": "env:PROVIDER_KEY_NAMED_BY_THE_FILE"`:

```bash
export OPENALLAY_SETTINGS_PROBE_CONFIG="$PWD/run/openallay/settings-probe.json"
export PROVIDER_KEY_NAMED_BY_THE_FILE='...'
./scripts/live-model-smoke.sh settings-probe
```

The script rejects credentials on argv, inline keys, malformed current shapes,
URL credentials/query/fragment, and non-HTTPS remote endpoints through the
production loader. Output is only terminal code and successful profile/protocol/redacted
authority/latency, never assistant text or provider bodies. Never commit raw keys.

Transport diagnostics use default-off `-Dopenallay.model.diagnostics=true`;
the packaged launcher also accepts `--model-diagnostics`. It logs exception
classes, one allowlisted model frame, HTTP status, field types, and decoder
counts, not exception messages, provider values/bodies, reasoning, or secrets.
It is separate from player Debug Mode and makes no provider request itself.

### Opt-in graphical clients

The development controller requires `openallay.e2e.enabled=true`, a real player,
and the same `GuideService`. It retains transitions, Tool/evidence
records, timings, and payload hashes, then can request clean shutdown. Reports
belong to the harness, not an Agent Tool.

```bash
./scripts/run-real-client-e2e.sh fabric
./scripts/run-real-client-e2e.sh neoforge
```

Connect to a disposable world/server or set `OPENALLAY_E2E_QUICK_PLAY_WORLD`.
The loopback OpenAI/SSE fixture waits for history hydration and current non-empty
recipe-viewer catalogs. It uses production JavaScript capture and exact current
references with pre-authored component responses. This tests capture, evidence,
chronology, and native presentation, not model planning or live provider quality.
Only `COMPLETED` passes. The harness isolates `models.json` and restores the exact
prior file on exit.

The default recipe is `minecraft:iron_block`; a compatible mod recipe can use:

```bash
OPENALLAY_E2E_RECIPE_OUTPUT=farmersdelight:apple_cider \
OPENALLAY_E2E_RECIPE_ID=farmersdelight:cooking/apple_cider \
OPENALLAY_E2E_RECIPE_LABEL=苹果酒 \
./scripts/run-real-client-e2e.sh fabric
```

The fixture reads the actual recipe reference/result, never fabricates handles or
craftability. `OPENALLAY_E2E_HISTORY_SEED_REQUESTS` creates durable seed requests;
`OPENALLAY_E2E_MIN_HISTORY_REQUESTS` asserts totals.
`OPENALLAY_E2E_REQUIRE_PAGED_HISTORY=true` additionally checks restart hydration
loads fewer requests than the durable total and exposes an earlier-page cursor.

Packaged Builder testing uses `scripts/run-packaged-builder-acceptance.py`,
production-named loader JARs, and the nested Extension after an explicit reviewed
launch. See the [packaged acceptance guide](verification/packaged-builder-acceptance.md)
for isolated instances, native owner-thread world oracles, restart/copy/undo,
journal checks, and prior receipts. Java exit status or model narration is not
acceptance; every selected native check must pass.

### Retained evidence and trace replay

For detailed history rather than current-gate claims, see:

- [Archived early-development evidence](verification/legacy-development-evidence.md)
  for recipe integration, durable restart, semantic presentation, native settings,
  and the original interaction corrections
- [Rhino live acceptance](verification/rhino-agent-runtime/live-javascript-agent.md)
- [UI polish plan](superpowers/plans/2026-10-01-ui-ux-polish.md) and
  [decision index](isme/SKMB.md)

The earlier packaged UI scenes and Builder receipts cover only their recorded
artifacts/scenarios, not later corrective source. CI checks harness/controller
syntax and both loader hooks; it does not launch a graphical client.

Extension-owned deterministic traces may still use
`data/<namespace>/agent_traces/<trace-id>.json`. The current strict shape rejects
unknown fields, duplicate keys, invalid IDs, and filename/ID mismatch. Active
server resources reload the available traces. Required registry/recipe/player
captures detach on the owning server thread; console player traces fail
`player_required`. Tool steps use the real registry with `exact`, `contains`, or
`schema` expectations; assistant steps are pre-authored, not a live model.
Replay adds no domain-count, step, or report cap; execution safety and provider
context budgets remain separate. Core bundles no traces for removed domain Tools.
