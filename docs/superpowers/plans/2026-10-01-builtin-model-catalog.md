# Builtin Model Catalog Implementation Plan

> **For agentic workers:** Use the executing-plans skill to implement this plan task-by-task. Keep all work in `/tmp/openallay-model-catalog-20261001`. Root reviews one independent feature commit; this worker does not commit or push.

**Goal:** Fill missing model context from a reviewed offline builtin table with deterministic BEST matching, while showing editable published limits and price estimates.

**Architecture:** Add an immutable strict resource-backed metadata source to the existing named-profile resolution route. Explicit limits outrank trusted provider cache, which outranks builtin matches. Prices live only in the separate builtin resource and presentation, never schema-1 provider metadata cache or inference requests.

**Tech Stack:** Java 25, Gson strict JSON reader, JUnit 5, common Minecraft settings screen, English/Simplified Chinese resources, public-only Python developer updater.

---

## Boundaries and ownership

Feature worker owns common Java/resources/tests, this plan, feature spec, decision036, SKMB and development docs. Dataworker owns `scripts/update-builtin-model-catalog.py`, its fixture tests and `docs/verification/builtin-model-catalog/` proposal/evidence. No main-worktree or acceptance-file edits, no game/live/billable operation. No Gradle while root's GUI is active without an explicit root build slot.

## Task 1: Record the behavior and data contract

- [x] Read `AGENTS.md`, README, development, SKMB and decisions009/012/015.
- [x] Agree strict schema with dataworker before code. Resource path: `common/src/main/resources/data/openallay/models/builtin-model-catalog.json`.
- [x] Record accepted automatic-matching decision036 with exact user approval evidence, state/events/ownership/failure semantics. Link from SKMB.
- [x] Write `docs/superpowers/specs/2026-10-01-builtin-model-catalog.md` covering precedence, confidence eligibility, tie-breaks, provenance, update review and immutable request capture.

## Task 2: Strict immutable builtin metadata source

Files: `common/src/main/java/dev/openallay/model/metadata/BuiltinModelCatalog.java`, `BuiltinModelMatcher.java`; tests `common/src/test/java/dev/openallay/model/metadata/BuiltinModelCatalogTest.java`.

- [x] Write fixture tests for exact IDs/aliases, wrappers, suffixes, close spelling, family/version protection, stable ties and unrelated unknowns.
- [x] Implement strict duplicate-key/field/version/numeric/source checks and immutable source/model/pricing records. Missing/malformed bundled data yields an unavailable empty projection, never a fabricated context value.
- [x] Implement BEST order: exact canonical/alias, normalized provider-wrapper identity, family/version-aware similarity. Eligible candidates rank by similarity then direct-provider before aggregator then canonical ID lexicographic. Model ID and endpoint are never rewritten.
- [x] Review and copy dataworker's published public proposal as the committed resource. Validate provenance references, units and nullable limits/prices. No arbitrary row or token cap.

## Task 3: Existing loader/settings resolution route

Files: `common/src/main/java/dev/openallay/model/config/ModelProfilesConfigLoader.java`, `common/src/main/java/dev/openallay/client/gui/settings/ModelProfileDraft.java`, narrow metadata resolution helper if shared; loader/draft tests.

- [x] Add injectable immutable catalog to loader for fixtures, defaulting to bundled data. Resolve context as `explicit != null ? explicit : trusted != null ? trusted.context : builtinMatch.context`.
- [x] Preserve exact trusted provider lookup and canonical provenance. Builtin identity is match provenance only; configured ID/protocol/URL/payer stay unchanged.
- [x] Keep `maxOutputTokens` explicit and unchanged. Published output limit is advisory presentation, not an automatic output default or new cap.
- [x] Make settings draft fill missing context automatically on model edits/select/save, retain manual context and output values, and track provenance so changing model does not retain a stale automatic context.
- [x] Test offline arbitrary OpenAI endpoint, unknown unresolved profiles, trusted-cache precedence, manual 1,000,000-token Luna budget, output override, and no schema-1 cache pricing.

## Task 4: Common native settings presentation

Files: `common/src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java`, focused `client/gui/settings` projection, `assets/openallay/lang/en_us.json`, `zh_cn.json`; presentation tests.

- [x] Show chosen builtin model, match kind, published context/output, all USD-per-million-token tiers, catalog/source/as-of and estimate-not-gateway-charge notice.
- [x] Keep context input editable. Never require extra confirmation for eligible BEST matches. Unknown IDs remain manual required with clear localized hint.
- [x] Reuse existing editor and authenticated `/models` picker; do not add a second provider/model selection system. `/models` lacking limits does not block builtin matching.
- [x] Ensure extra metadata lines scroll in narrow screens and do not expose credentials or raw response bodies.

## Task 5: Verification and update workflow

- [x] Add frozen-runtime regression: capture old runtime, reload changed context, verify old reference unchanged and later requests use replacement.
- [x] Add strict unsupported-cache-schema rejection test without mutation; do not introduce any migration.
- [x] Update development docs with public-only opt-in developer pull to proposal, review, strict fixture validation, reviewed committed resource. Runtime never fetches arbitrary catalog URLs.
- [x] Run public updater fixture tests and `git diff --check` (no game/build required).
- [x] Root ran Java25 wrapper focused metadata/config/draft/settings tests; exact results are recorded below. Root owns broad `:common:test :fabric:build :neoforge:build` gate and final separate commit.

## Verification status (source freeze)

- Implemented source and tests in the isolated feature worktree only.
- Copied 1,121 reviewed public entries with source times, price tiers and a shipped Models.dev MIT notice. Bedrock variants remain distinct; original canonical identities outrank other-provider aliases.
- Public updater fixtures: 14 tests passed (`python3 -m unittest discover -s scripts -p test_update_builtin_model_catalog.py -v`). No runtime source/network/provider request was made by the feature worker.
- `git diff --check`: passed.
- Java focused/common/both-loader tests are not yet executed. Root owns the serial Java25 build slot while graphical acceptance is active.
- Requested focused command: `./gradlew :common:test --tests 'dev.openallay.model.metadata.*' --tests 'dev.openallay.model.config.*' --tests 'dev.openallay.client.gui.settings.*' --tests 'dev.openallay.settings.ClientSettingsRuntimeTest' --tests 'dev.openallay.client.ClientModelRuntimeRegistryTest'`.
- No feature commit or push performed by the worker. Root reviews the independent change before the one feature commit.

Final read-only review found no architecture blocker after picker/shared-context and event-owned rendering fixes. Java verification remains pending root execution. Automatic field clearing recalculates and updates the visible widget; invalid nonblank manual input remains editable and validated normally.

## Root focused gate and approved copy refinement

Root ran the Java25 focused command: **93 tests, 0 failures, 0 skips**, completed in 15.6 seconds. After that gate, root authorized the catalog-only copy refinement: player labels use “Reference model” / “参考模型”, the effective provider context shows its source and capture date, and a context tooltip/line explains clearing the field to return to automatic metadata with provider precedence. The event-owned projection remains unchanged. Root reruns focused tests after this narrow patch; loader build gates remain root-owned.

The approved pricing-note cleanup preserves all 1,121 identities, aliases, limits, rates, tiers and source timestamps byte-equivalently at the parsed-data level except `pricing.note`. Ordinary notes are empty; only 20 material published rate conditions remain. The reviewed resource SHA-256 is `311cab5ed5313e5fe90aa5b16b64da25be9395abd3ad55e815bf312d5a7f5501`. The updater's 15 offline tests passed after this cleanup. The source is frozen for root's narrow Java rerun.

## Final root focused result

Root reran the focused Java25 wrapper gate after the catalog-only UI and notes cleanup: **94 tests, 0 failures, 0 skips — PASS**. The public updater's **15 offline fixture tests passed**. The reviewed production resource remains SHA-256 `311cab5ed5313e5fe90aa5b16b64da25be9395abd3ad55e815bf312d5a7f5501` with 1,121 model references. The independent read-only catalog review has no remaining architecture findings. The UI reviewer will review final integrated runtime screenshots separately. No functional source changes followed this gate. Root owns the final full common/both-loader gate, feature commit, integration and release.

## Final isolated feature full gate

Root ran the full catalog worktree gate with Java25 and the verified clean production Extension pin `5354853`, supplied through `-PopenallayExtensionsDir`: **838 common tests, 0 failures, 0 errors, 4 skips — PASS**. Both **Fabric and NeoForge production loader builds passed**, including their default nested Extension artifacts. The gate completed in 35 seconds. This followed the 94-test focused pass and 15 updater fixture passes. Final integrated GUI review remains a separate root-owned validation step. No functional catalog source changes followed these gates.

## Integrated 0.2.4 verification — 2026-10-01

The main-line integration is complete. Its final clean Java 25 gate passed
907 common tests with zero failures/errors and 6 skips. Both default Fabric and
NeoForge builds passed with the pinned Builder Extension `5354853`.

The final packaged GUI captures are `fabric/20261001-ui-disabled-03`,
`neoforge/20261001-ui-provider-failure-02`, and `fabric/20261001-ui-stop-01` under
`build/e2e/packaged-builder`. They retain 24, 26, and 24 PNGs respectively,
including the final native-world capture. The disabled scene passed its native
no-write check. The failure scene retains a successful player read followed by
an actual HTTP 503 and a failed request. Stop retains an accepted cancellation
with no normalized Tool result. Original display configuration was restored in
all three scenes.

The independently reviewed GUI artifact SHA-256 values are:
- Fabric: `ab75f3b80da321c0806b4d64c32d3ad9ee32a55f90511cf44773f19a92d8910e`.
- NeoForge: `7b9eaf43b1271452427d2f4673553a7e7e28e3fa87a17eb1fe4ed8560bfe0daa`.

The earlier isolated worktree results and source-freeze notes above are retained
as chronological records. This integrated receipt supersedes their pending
integration/commit statements. Independent visual review is recorded separately.

Independent Minecraft UI/UX review on 2026-10-01 inspected all 74 final PNGs
from the three packaged scenes above and approved the reviewed release scope.
The review found no remaining release-blocking P0/P1. Its retained report is
`/Users/nkanf/docs/openallay-ui-ux-review-2026-10-01.md`.

A subsequent final clean gate passed the same 907-test and both-loader checks.
NeoForge retained the same container SHA-256. Fabric's container SHA-256 became
`321c773d4ad5f2b5a6ea8653869e9905cd75aa97e06030f0cc52d2538e5df11b`:
all entry names, order and uncompressed entry SHA-256 values match the reviewed
JAR; only six nested-library ZIP timestamps/UT fields changed. The original
GUI manifest hash above is retained, not relabeled as this later build.
