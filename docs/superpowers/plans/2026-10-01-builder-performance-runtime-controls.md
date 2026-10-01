# Builder Performance and Runtime Controls Implementation Plan

> **For agentic workers:** Execute independent file-owned tasks in isolated worktrees. Root reviews and serializes native verification. Do not start another graphical client while the user is testing. No publication, credential reads, world edits, or data resets are authorized by this plan.

**Goal:** Remove demonstrated Builder scheduling/allocation amplification, repair request-consistent command discovery, provide real per-profile reasoning effort, and reduce prompt restrictions.

**Architecture:** Builder remains independently released in OpenAllay-Extensions. Batch full-region terrain preparation and transport exact sparse observations; cache immutable native states; preserve owner-thread identity, optimistic before-images, durable intent/readback and cancellation. Core freezes command authority and derives guidance from its actual binding. Effort is captured model configuration and encoded per provider, with AUTO omitted. Prompts stay short and task-oriented; known credentials are redacted by code before model continuation and persistence.

**Tech Stack:** Java 25, Minecraft 26.2, Rhino, JavaScript, Gradle wrapper, JUnit, existing JSON/SQLite codecs.

## Evidence and scope

- Core base: 1dd8a07. Extension base: 174d2f5. Manual Fabric JAR SHA matches current source artifact; running process started after its replacement.
- flatten_area 100x100 at y64/depth3/maxY320 issues 10,000 sequential region reads and transports about 2.59 million cells, including canonical air.
- Each native 256-unit action awaits client validation and server execution. Source identity includes Builder capture timestamps and cannot aggregate repeated regions.
- Current operation journal deltas are already linear on successful batches. Remaining abort-tail scans and checkpoint allocations require separate tests.
- Commands and unrestricted settings in the manual instance are both enabled. The model's mc catalog inspection is not proof of a missing top-level commands global.
- Initial current game sampling found UI/render and idle server activity, not an active Builder operation. Do not claim a live Builder timing baseline from that sample.

## Task 1: Native Builder work

Files: `extensions/minecraft-builder/src/shared/java/dev/openallay/builder/{BuilderSession,NativeBinding,NativeBlockCodec,OwnerThreadBridge,TerrainScan}.java`; matching common tests.

- [ ] Add exact sparse readRegion observations, omitting only canonical minecraft:air; retain cave/void/custom air, block entities, all bounds and complete coverage.
- [ ] Bound owner work by elapsed action time plus work quantum, not a total-volume or relaxed-timeout gate; preserve exact identity and chunk availability checks.
- [ ] Cache detached immutable state encodings/decoded palette; never cache live block entities.
- [ ] Reuse verified native write outcomes without redundant full JSON readbacks.
- [ ] Compare exact writes/readback/undo/conflicts/cancellation and batch counts at scale.

## Task 2: JavaScript algorithms

Files: Builder `building.js`, `terrain.js`, `presets.js`; JS contract tests.

- [ ] Tile full-height terrain reads across columns, use exact sparse native contract, avoid reading cached path regions again.
- [ ] Enumerate wall perimeter rather than XZ interior; preserve original position order and duplicate last-write semantics.
- [ ] Reuse immutable normalized geometry/preset palette and transforms rather than clone/decode per voxel.
- [ ] Add explicit `b.batch(callback)` using existing flush barriers. Preserve completed effects when later work fails.
- [ ] Verify full 100x100 outputs against an independent frozen baseline, plus native call/cell transport counts. Do not shrink fixtures or call count evidence a Minecraft latency measurement.

## Task 3: Journal and source aggregation

Files: Builder `storage/{OperationJournal,AtomicJsonFiles}.java` and tests. Core `context/{SourceObservationCollector,SourceObservation}.java`, UI evidence projection and tests.

- [ ] Stream current JSON format writes instead of building redundant tree/string/byte copies.
- [ ] Avoid full entry rescans for known-unstarted tail aborts; retain exact recovery semantics and real crash-cut tests.
- [ ] Separate only validated Builder capture extent timestamps from stable source identity. Keep all authority/completeness/provenance and other details distinct.
- [ ] Preserve earliest/latest actual capture bounds; no arbitrary caps, fake observations, migration or automatic reset.

## Task 4: Command binding consistency

Files: common context/provider capture, command runtime/capability resolution, Skill eligibility and tests. Prompt text owned by Task 6.

- [ ] Freeze commands at request submission independently of unrestricted mode, release at terminal cleanup.
- [ ] Derive request catalog and guidance from actual captured command bridge and explicit Skill policy, not current UI setting.
- [ ] Make top-level commands binding discoverable without pretending it is an mc root.
- [ ] Test enabled/disabled, local safe/unrestricted, save/reload and current-versus-next-request setting changes.

## Task 5: Model reasoning effort

Files: model effort enum/config/profile loaders/writers/draft/runtime, native Models form, OpenAI and Anthropic codecs and tests.

- [ ] Implement AUTO plus current confirmed protocol effort choices, filtered by protocol. AUTO omits wire field and does not assert a gateway default.
- [ ] Encode OpenAI `reasoning_effort`; encode Anthropic `output_config.effort`. Do not enable adaptive thinking or invent a token budget.
- [ ] Persist exact optional current shape, preserve manual choice and captured active runtime; reject invalid values.
- [ ] Test stream/nonstream codecs, strict config round trips, draft editing and active runtime capture. Do not hardcode gateway/model capability bans from name similarity.

## Task 6: Lean prompt and credential boundary

Files: AgentSystemPrompt and selected bundled Skills/tests in prompt worktree. Agent/context/redaction/runtime files and tests in redaction worktree.

- [ ] Remove repeated warnings and task-specific restrictions. Briefly prioritize completing tasks with actual available capabilities and correcting recoverable errors.
- [ ] Preserve structural JS/Tool contracts; authorization stays in code.
- [ ] Redact known configured credentials before ToolResult reaches next model request, durable original context or UI/export; redact JSON keys as well as values.
- [ ] Test known-secret continuation, object-key/value, arguments and durable/export paths. Do not claim arbitrary unrestricted JVM environment access is sandboxed.

## Task 7: Skill context ownership

Files: `skill/SkillInstructionContext.java`, `skill/LoadSkillTool.java`, Agent/session/context lifecycle and new regression tests in the Skill worktree. Integrate on the redaction worker's stable Agent boundary changes.

- [ ] Replace request-local hidden receipts with session-owned retained range facts derived only from actual active plaintext; do not create a second document-body store or permanent loaded flags.
- [ ] Publish a concise model-visible loaded-document/range manifest. It is factual state, not another prohibition or task checklist.
- [ ] Avoid duplicate document I/O and duplicated plaintext when the same valid range is retained. Distinguish model-issued duplicate calls from actual rereading.
- [ ] Test new asks, exact reference ranges, incomplete cursor continuation, changed/deleted documents, compaction loss, model switch, restart hydration and session/world isolation.
- [ ] Preserve original historical success/error records independently of refreshed active context. Verify context refresh does not rewrite original outcomes.

## Root review and verification

- [ ] Review worker diffs and exact semantics; serialize focused Gradle checks using native project environments.
- [ ] Run full Builder common tests and both Extension loader packages against integrated core API.
- [ ] Run full core common tests, both loaders, script suites, distribution and SQLite package verification.
- [ ] Keep existing worlds, operation journals, history databases, exports and credentials untouched. Do not overwrite the user's running instance.
- [ ] Record deterministic before/after counts and measured fixture timings separately from real-game latency. Real 100x100 game acceptance requires the user to finish current testing and a separately coordinated single-client run.
- [ ] Report remaining gaps and provide a restart-safe test artifact only after build identity checks; no release tag or remote publication without authorization.

## First deterministic gates

- Core prompt/source/command/effort focus: 173 tests, zero failures/errors/skips.
- Builder native/storage/JavaScript full package focus: 189 tests, zero failures/errors/skips.
- These do not establish real-game build or finalization latency. New section-proof and Skill/redaction changes need subsequent gates.

## Explicit connection-repair behavior decision

The previous public `update_connections` also swept the whole halo through neighbor and comparator notifications. This is a real behavior change, not an unchanged implementation detail. The requested performance refactor separates native connection-shape normalization (the existing `update_connections` call) from an explicit `sync_physics` full notification pass. Default building/presets keep shape repair and exact journals/readback; they no longer run the full empty-volume physics pass implicitly. External mod native shape rules stay conservative. Later physics remains outside explicit direct-edit undo guarantees. Docs and tests must state this boundary. No release or remote publication is implied.

## Manual trace distinction

The exported session-1 partial snapshot belongs to a request that finished in about 304 seconds. Its failed build tool lasted about 235 seconds and reported `concurrent_edit`, not a timeout; the submitted source explicitly repaired an expanded 310,329-cell halo. It is not proof of the currently reported 40-minute pending operation. The current session-3 request is inspected separately, with read-only request metadata/source extraction and no model result/environment/credential dumps.

## Bulk verification and actual request correlation

- Public dense `read_region` and sparse ordered `get_blocks` expose fresh batched observations. Existing scalar reads stay fresh; no script parser or implicit cache changes their meaning. Duplicate sparse coordinates keep input order. Unknown bounds/chunks fail honestly without implicit chunk generation.
- Java 25 virtual-thread sampling identified a real scalar `get_block` loop in a completed request's `filter`. That tool lasted about 16 seconds; the whole request lasted about 11 minutes. This is a verified slow path, not the reported 40-minute operation.
- A separately retained cancelled trace lasts about 41 minutes 43 seconds. Its source and request identity are investigated separately before assigning the reported long-running build's root cause.
- Skill source ownership must describe the data source (`client` or `server`), not where its model runs. Moving the same client Skill between local and server models must not invent a new document identity.

## User-authorized delivery

Commit and push in small coherent batches after corresponding verification. Do not create one aggregate commit. Independent Builder storage improvements can precede its native/JS performance changes; Extension source commits precede the core distribution lock update. Core prompt, model effort, command regression, credential boundary and Skill lifecycle changes stay separate where their dependencies allow. No release tag or product publication was requested.
