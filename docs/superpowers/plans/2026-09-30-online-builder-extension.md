# Complete Online Builder Extension Implementation Plan

> **For agentic workers:** Execute independent tasks in parallel and review integration checkpoints. Use the executing-plans workflow. No reduced-feature release satisfies this plan.

**Goal:** Deliver full online construction as an independently committed Extension, default-included in OpenAllay distributions, and finish prior unrestricted guidance.

**Architecture:** Core supplies only a generic invocation lifecycle/evidence context and advisory dependency metadata. OpenAllay-Extensions owns all geometry, terrain, presets, native world execution, templates and undo. Existing unrestricted client-local authorization is required by the native facade; no implicit remote write capability.

**Tech Stack:** Java25, Minecraft26.2, Rhino, existing Gson, Gradle wrapper, Fabric and NeoForge. No Python runtime or offline save editor.

## Repositories and ownership

- `/Users/nkanf/projs/OpenAllay`: generic SPI, requirement metadata/UI, distribution integration, accepted decisions.
- `/Users/nkanf/projs/OpenAllay-Extensions/extensions/minecraft-builder`: complete native Extension.
- `~/docs`: research only for player automation; no Baritone implementation.

## 1. Existing guidance closeout

- [ ] Review frozen enabled/disabled/denied/server request snapshots and load_skill symmetry.
- [ ] Update stale prompt assertions after approved wording reduction.
- [ ] Run focused tests, then clean common and both loader builds; package scripts; commit separately.

## 2. Generic invocation lifecycle (main only)

- [ ] Add `dev.openallay.extension.JavascriptInvocationParticipant` with `String id()` and `AutoCloseable open(JavascriptInvocationContext context)`.
- [ ] Add context with `invocation()`, `cancellation()`, `recordEvidence(EvidenceMetadata)`, and `requireActive()`; closing invalidates it.
- [ ] Add transactional registered participant list to Extension contribution/registry with old four-list constructor retained.
- [ ] Inject registry at bootstrap into RunJavascriptTool. Open per execution, close reverse-order always; request-close revokes active scopes and prevents stale reopen.
- [ ] Test setup failure cleanup, two invocation isolation, cancellation/close races, no evidence on mere registration, trusted operation evidence only and no server permission escalation.

## 3. Native Extension and packages (Extensions repo)

- [ ] Independent Java25/MC26.2 Gradle wrapper project, shared/Fabric/NeoForge source sets and separate loader jars, strict embedded manifests, no core classes packaged.
- [ ] `BuilderExtension` contributes `openallay_builder:building`, Skill and invocation participant; no registration on classpath presence alone.
- [ ] `BuilderRuntime.open(String optionsJson)` returns an invocation-bound `BuilderSession`. JSON DTO block is `{id,properties,blockEntity?:SNBT}`.
- [ ] Implement context/read/write/readRegion/writeRegion/updateConnections/transformState/status/cancel/close. Native transform uses `BlockState.mirror/rotate` and block-entity handling; unknown coverage is not air.
- [ ] Bind integrated world/current actor on owner thread; revalidate exact session/dimension and active scope before each action. Unrestricted is the actual user grant, not creative/GM status.
- [ ] Reads/writes are scheduled Java actions, no Rhino callback on game thread; file IO remains off-thread. Cooperative slices are not operation limits.
- [ ] Test real compilation against native mappings, scheduling fake adapters and explicit unavailable/error/cancel paths. Build both jars.

## 4. Complete JS library (Extensions repo)

- [ ] `src/shared/resources/assets/openallay_builder/building.js` exports `create(backend,options)` for deterministic tests and `open(options)` through the installed Java helper.
- [ ] Online setup equivalents: ensure_deps/resolve_save_path/open_world/save_and_close/detect_version/get_player_pos/quick_setup never open game saves or install dependencies.
- [ ] Blocks: place/get/get_full and connection repair; all eight geometry primitives.
- [ ] Six decorations and six furnished presets: house/skyscraper/cottage/windmill/farm/dock.
- [ ] Terrain and ground scans/bounds/flatten including blending/selective vegetation, deterministic smart terrain path; no straight-line fallback on failure.
- [ ] scan/save/load/list/paste structure templates, native rotation/mirror, explicit air semantics and seeded variation.
- [ ] Rhino contract goldens for every function and preset, negative coordinates/full dimension heights, obstacle path failure/no writes, templates and transformations.

## 5. Versioned templates and online journal (Extensions repo)

- [ ] Dedicated `dev.openallay.builder.storage` classes with Gson strict codecs, immutable records, path-safe names, sorted IDs and atomic replacement.
- [ ] Durable before-image intent before write, verified postimage and status after. Preserve interrupted records; no startup replay.
- [ ] Undo compares current and expected states, restores matching entries in reverse order; report conflicts without force unless explicit.
- [ ] Test corrupted/unknown schema, round trips, namespace/state/SNBT preservation, traversal/atomic write failure, partial journal/recovery, undo conflicts.

## 6. Advisory metadata/UI (main plus schema docs in Extensions repo)

- [ ] Decision035 determines generic strings for Skill extra metadata and optional Extension requirements object, maintaining strict unknown-field rejection.
- [ ] Show requirements and satisfaction in install/detail UI; enable/cancel/Continue anyway. Do not hard-block installation or load based on advisory requirements.
- [ ] Actual authority remains frozen and explicit; Continue anyway grants nothing. Missing dependencies are diagnostics, no fallback.
- [ ] Localize English/Chinese; deterministic codec/evaluator/state/UI projection tests.

## 7. Default inclusion and acceptance

- [ ] Default artifact assembly includes separately built Extension loader jar by pinned source/artifact mechanism, no domain source in core and no circular build dependency.
- [ ] CI independently builds Extension from core compile artifacts then product distribution attaches it; validate nested normal loader discovery or explicit distribution bundle selected design.
- [ ] Run complete core suite and both loader packages; independent Extension tests/builds; scripts and diff/credential checks.
- [ ] Review no unrelated files (.DS_Store preserved), commit coherent changes in owning repo, push intended branches and verify remote refs.
- [ ] Publish automation research under ~/docs. State unrun graphical/live-provider checks; never claim gameplay acceptance from compilation.
