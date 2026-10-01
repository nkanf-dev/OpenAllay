# Execution and Context Simplification Implementation Plan

> **For agentic workers:** Use executing-plans to implement assigned tasks with
> root-owned review checkpoints and serialized native verification. All workers
> edit isolated worktrees. Root alone runs builds and graphical clients.

**Goal:** Remove citation-pipeline coupling from execution, preserve real model
context and errors, and make Builder work batch-efficient.

**Architecture:** One lazy detached host graph and one actual model transcript.
Execution results stand alone; optional origins and UI are projections. Keep
original recorded exchanges separate from budgeted active context. Use new
current history, bridge and Builder journal formats without internal version
identifiers or legacy adapters. Before formal 1.0 this is a rapid-iteration product.

**Tech Stack:** Java 25, Minecraft 26.2, existing Rhino/Gson/SQLite, Fabric and
NeoForge; checked-in Gradle wrappers.

---

## Ownership and ordering

Implementation is authorized by the user; no additional workflow approval gate.
Decision041 and the matching design are authoritative for this change. Native
commands are forbidden while any manual GUI is running. Client pid66675 exited
normally before the first focused gate. Do not touch user saves, prior databases,
exports, model configurations, credentials, or the three untracked `.DS_Store`s.

Workers: data-result-contract owns runtime/graph/origin collector/routing;
skill-context-reuse owns model history, Skill reuse and protocol;
source-details owns UI; builder-terrain-batching owns the Extension. Root owns
integration, docs, export, review, all native gates, packaging and publication.
No worker commits or pushes. Root stages explicit reviewed paths only.

### Task 1: Establish retained regressions

Files: ignored `build/verification/context-rework-20261001/`; existing manual
exports and traces are inputs only.

- [x] Read both exported conversations and correlate actual tool errors by ID.
- [x] Separate immediate model continuation from next-ask restoration. Exact
  TypeError reaches immediate continuation, then disappears on next ask.
- [x] Identify plaintext/JSON reducer mismatch separately from observed failures;
  no actual COMPACTING event occurred in these traces.
- [x] Verify the world metadata construction fix in its isolated tree:

```bash
./gradlew -PopenallayExtensionsDir=/Users/nkanf/projs/OpenAllay/.gradle/distribution-sources/openallay-extensions :common:test --tests 'dev.openallay.world.*' --console=plain
```

Result: 31 tests passed at the pre-refactor input/output contract. These must run
again after the new JavaScript contract; this result is not full acceptance.

### Task 2: Plain execution and automatic lazy access

Files: `tool/builtin/RunJavascriptTool.java`,
`script/data/MinecraftAgentHostGraph.java`, `script/RhinoJavascriptRuntime.java`,
`script/workspace/AgentResultWorkspace.java`, `context/SourceObservation.java`,
`guide/GuideSource.java`, `guide/GuideStateReducer.java`,
`bridge/server/PlayerClientToolRouter.java`, `RemoteToolServer.java`; adjacent tests.

- [x] Replace obsolete rejection/selection tests with these executable program cases:

```javascript
return 6 * 7;
return mc.items.filter(x => /chest/.test(x.id)).map(x => x.id);
return workspace.open(handle).map(x => x + 1);
```

Assert first case succeeds with no origins; second needs no roots argument;
third retains only origins of the handle actually opened. Add enabled Java IO
temp-file test proving one side effect succeeds without artificial player access.
Disabled Java and server callbacks remain unauthorized.

- [x] Remove roots from input/schema/prompt/examples and graph masking. Keep lazy
  suppliers, unavailable-root errors and owner-thread capture tests.
- [x] Remove generic EvidenceBearing and post-execution evidence gate. Keep
  normalizer tests for actual factual EvidenceBearing outputs.
- [x] Add single grouped SourceObservation path through workspace and GuideSource.
  Assert repeated same-origin reads do not grow one object per observation;
  different authority/coverage/scope stays distinct and capture ranges are true.
  Do not count inherited workspace references as fresh observations.
- [x] Return sanitized type/message/script location for script and Java failures.
  Verify failure text reaches AgentToolResult.modelValue unchanged after redaction.
- [x] Delete roots-based route parsing; test deterministic client-first/server
  fallback plus existing explicit server route, no hidden execution retry.
- [x] Run root focused script/tool/bridge/extension tests and inspect failures.

### Task 3: Truthful context and durable history

Files: `agent/GameGuideAgent.java`, `AgentEvent.java`, `AgentSessionStore.java`,
`agent/context/ContextCompactor.java`, new strict transcript codec,
`client/ClientGuideRuntime.java`, `guide/GuideService.java`, `GuideLocalEndpoint.java`,
`guide/history/*`, `bridge/protocol/BridgeProtocol.java` and shared event codec;
`skill/LoadSkillTool.java`; adjacent tests and exhaustive event consumers.

- [x] Delete UI-to-model reconstruction and fabricated durableProjection calls.
- [x] Persist original redacted model exchanges independently of active compacted
  context. Do not store canonical workspace trees, reasoning, config or HTTP data.
- [x] Hydrate only an absent active session. Publish safe context on completion
  and failure; retain structurally complete exchanges on cancellation/interruption.
- [x] Delete ToolResultContextReducer/ReducedToolResult knowledge-field filtering;
  budget compaction preserves actual plaintext/errors and complete call pairs.
- [x] Keep history.sqlite3 and current exact table/message shapes. Remove internal
  schema/protocol versions, old-layout fixtures, migrations and compatibility
  branches. Validate current malformed shapes without silently resetting files.
- [x] Prime Skill reuse only from actual post-projection successful loaded text,
  exact document fingerprint and ranges. A changed/deleted document invalidates
  only its own text; compaction removal requires actual re-emission next time.
- [x] Scripted ModelClient regressions: first ask loads Skill+reference, performs
  tool work, second ask sees both texts and prior exact result with no new load.
  Repeat across restart, provider/budget change, separate actors/sessions, deletion,
  disconnect and source update. Verify real error after a failed ask remains usable.
- [x] Verify strict protocol event encode/decode on both transport adapters.

### Task 4: Useful details and export

Files: `guide/ui/GuideToolDetailView.java`, `GuideToolDetailPresenter.java`,
`GuideEvidencePresentation.java`, `client/gui/OpenAllayScreen.java`, EN/ZH language
resources, `guide/export/GuideSessionExportSnapshot.java`,
`GuideSessionExportCollector.java`, `client/gui/export/GuideSessionExporter.java`.

- [x] Put actual safe error/result before auxiliary origins. Debug code/input/output
  precedes grouped, initially collapsed source details. Preserve Debug-only raw data.
- [x] Render distinct-source counts/time ranges and inspect origin details on demand without
  per-record paragraph flooding. Keep selection identity and scissor/layout tests.
- [x] Export original recorded tool program/result/error in chronological order,
  not status-only entries or current compressed context. Redact credentials at the
  record boundary and export boundary. Test after restart and after compaction.
- [x] Update English/Chinese labels and normal/debug privacy projection tests.

### Task 5: Batch Builder and remove quadratic journal IO

Repository: OpenAllay-Extensions; files under
`extensions/minecraft-builder/src/shared/{java/dev/openallay/builder,resources/assets/openallay_builder}`
and common tests. Existing native scan patch belongs to this same worker branch.

- [x] Compare TerrainScan against shipped JavaScript semantics for 49x49 ordering,
  custom bounds/air/ground, vegetation/liquids, unavailable columns and cancellation.
- [x] Batch scan_structure with readRegion; batch geometry/paste/undo owner actions.
  Assert dispatch count scales by cooperative quanta, not by individual positions.
- [x] Implement current unversioned base+atomic sequential delta records; remove full growing
  snapshot publication per quantum. Force intent before writes and returned
  outcomes before later writes. Compact safely at terminal/recovery boundaries.
- [x] Crash-cut tests cover before/after intent, mid-apply, before/after readback,
  missing/corrupt segments and checkpoint publication before segment cleanup.
  Never replay writes on startup; preserve conflict checks and partial outcomes.
- [x] Verify malformed current journals are diagnosed without implicit rewrite or
  replay. No internal journal/template version field or old-format adapter.
- [x] Run focused and complete Extension common tests plus both loader builds.

### Task 6: Integrate, verify, and deliver source only

- [x] Root reviews exact changed files and applies independent worktree diffs;
  resolve protocol/source/history overlap once, not by keeping parallel paths.
- [x] Update SKMB, relevant supersession notes, development guide, README status,
  implementation plan and verification report to match actual behavior.
- [x] Run serialized full common tests and both production loaders:

```bash
./gradlew clean :common:test :fabric:build :neoforge:build
```

- [x] Run distribution and SQLite/protocol/fixture checks with newly pinned verified
  Builder source. Inspect normalized output/redaction and actual artifact contents.
- [ ] If an explicitly requested client acceptance is run, keep one GUI only and
  no builds while active. Retain real execution timings instead of inferring seconds
  from dispatch-count tests. Never edit the player's existing world for automation.
- [x] Independent source review of failure/cancellation/lifetime and complexity.
- [ ] Explicit coherent commits to Extension then core; pin tested Extension hash,
  push intended branches and verify CI truth. Do not change product versions,
  create a tag, move v0.2.4 or publish a release.

## Status

2026-10-01: implementation and independent source review complete. The final clean
common suite passed 1,123 tests (six opt-in skips), both core and Extension loader
builds passed, Builder passed 155 tests, packaging checks passed and 117 offline
script tests passed. Graphical latency/live-provider acceptance was not run.
Source publication and real core CI status are recorded separately.

### Task 7: Remove pre-1.0 internal version scaffolding

User correction: before formal 1.0, all atomically released internal contracts are
Latest Only. This replaces the earlier numbered-format proposal in this plan.
Keep independently published Extension/public core API versions and external
Minecraft/loader/model/dataVersion facts. No format migration is required.

Files and owners:
- Context worker: history store/table checks, internal bridge packet records and
  codecs on both loaders, model-context/checkpoint codecs, Skill cursors.
- `latest-config`: client models and credentials, capability/recipe/display/command/
  unrestricted settings, private metadata caches/catalog format, diagnostics/UI
  schema indicators and dedicated tests. Stable existing filenames remain.
- `latest-formats`: semantic document and rich-component envelopes/registry,
  live and deterministic trace records/fixtures, benchmark corpus/audit and their
  script validators. No internal version field or version fallback remains.
- Builder worker: internal journal/template/world-identity format numbers.
  Actual Minecraft game/dataVersion and external package/API compatibility stay.

- [x] Delete version fields/constants and constructor arguments, not replace them
  with a frozen number or a renamed format marker.
- [x] Keep exact current shape validation, useful malformed-data diagnostics,
  atomic publication and actual permission checks.
- [x] Remove old-version fixtures/translation/migration paths. Test current
  create/read/write, corrupt fields and cancellation instead.
- [x] Remove UI/internal diagnostics schema-number displays rather than showing
  a null/zero placeholder. Update English and Chinese text where needed.
- [x] Keep NeoForge's required nonempty external registrar label `openallay`;
  it is an API registration label, not a numbered internal wire contract.
- [x] Search all source/resources/scripts for remaining internal version gates;
  classify external/community/game metadata deliberately, not by bulk deletion.
- [x] Run dedicated and full deterministic gates after all constructor/codec
  call sites are integrated. Do not change runtime files merely to make tests pass.
