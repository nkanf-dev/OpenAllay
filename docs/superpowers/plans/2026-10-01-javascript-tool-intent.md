# JavaScript Tool Intent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show each JavaScript invocation's model-written player-language title and description in its pending and completed card.

**Architecture:** `CoreGuideService` and the reducer remain the state owners. The UI projects immutable invocation intent separately from actual status, normalized results and sources. Optional input properties preserve older calls; existing `INVOCATION_RUN_JAVASCRIPT` presentation-message arguments carry the closed display projection across history and the server bridge without changing their strict schemas.

**Tech Stack:** Java 25, Gson, common Minecraft Components, JUnit 5, checked-in Gradle wrapper.

---

## Scope and verified baseline

Work only in `/tmp/openallay-javascript-intent-20261001`, branch
`feat/javascript-intent-20261001`, base `d254bff`. Do not mix runtime acceptance
or model-catalog changes. Do not commit or push; root reviews and integrates one
feature commit. No Gradle until root grants an explicit slot. No game, provider,
network or graphical acceptance operation belongs to this implementation.

Read AGENTS, README, development guide, SKMB, decisions 005, 010, 018, 025,
028, and 032 before code. Implementation confirms
that live invocation arguments are not durable or sent in server ToolStarted
messages. Schema-5 history stores only roots/handles/modules and existing closed
presentation messages. Preserve that privacy contract; do not persist source or
raw arguments and do not add mandatory/unversioned strict fields.

## Files and responsibilities

- `common/src/main/java/dev/openallay/tool/builtin/RunJavascriptTool.java`: optional input title/description; model-facing descriptions; execution-key argument projection.
- `common/src/main/java/dev/openallay/trace/replay/ToolArgumentCodec.java`: reject non-string JSON for declared String components before Gson can coerce it; stable `invalid_arguments`.
- `common/src/main/java/dev/openallay/agent/AgentSystemPrompt.java`: one lean instruction requiring both intent fields for every new call.
- `common/src/main/java/dev/openallay/agent/GameGuideAgent.java`: use execution arguments, not valid display labels, for the existing repeated-call key.
- Create `common/src/main/java/dev/openallay/guide/GuideToolIntent.java`: closed immutable title/description projection, plaintext normalization and legacy fallback.
- `common/src/main/java/dev/openallay/guide/GuideToolActivity.java`: derived intent accessor, preserving the existing stored/wire fields.
- `common/src/main/java/dev/openallay/guide/GuideToolInvocationPresentation.java`: encode intent as zero legacy or two new string arguments on the existing invocation key.
- `common/src/main/java/dev/openallay/guide/GuideToolMessageCodec.java`: unchanged; malformed persisted argument types/control text remain strict corrupt-history failures.
- `common/src/main/java/dev/openallay/guide/ui/GuideToolDetailView.java` and `GuideToolDetailPresenter.java`: immutable intent in detail projection; no change to native result classification.
- `common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java`: literal intent title/description in collapsed/detail cards, wrapped title measurement, separate status and technical identity in Debug.
- `common/src/main/resources/assets/openallay/lang/en_us.json` and `zh_cn.json`: localized no-intent fallback title and description; never translate model text.
- Tests in `tool/builtin`, `guide`, `guide/ui`, `guide/history`, `agent`, `bridge`, and `client/gui`: input, no-execution effect, immutable chronology, restore/wire and literal rendering.
- Create decision `docs/isme/decisions/2026-10-01-037-javascript-tool-intent.md`; update `docs/isme/SKMB.md` and `docs/development.md`.

### Task 1: Input contract and execution independence

- [ ] Add deterministic schema and input tests before implementation. Generated properties `title` and `description` have type `string`; `required` remains `["source"]`. Missing/null/blank fields are valid legacy inputs. Numbers, booleans, objects and arrays fail `invalid_arguments` without starting JS.
- [ ] Extend Input with `@ToolOptional String title` and `@ToolOptional String description`; retain the existing two/three-argument Java constructors using null metadata. Add short Tool descriptions and the system instruction: every new call must include both fields in the player's language; they describe intended work, not success/evidence.
- [ ] Before Gson decode, inspect declared record String components and reject present non-null non-string JSON. Do not add arbitrary length/pattern constraints.
- [ ] For the existing JavaScript repeated-call key, copy raw arguments and remove `title`/`description` only when both are absent, null or strings. Malformed input keeps its failure key so a corrected call can recover. The exact source, handles, roots, unknown properties and execution input remain untouched.
- [ ] Prove changing valid title/description does not change results, selected roots, evidence, command permissions or duplicate execution suppression. Actual call arguments and traces still retain metadata.

### Task 2: Immutable per-invocation display and transport

- [ ] Add tests for pending intent from actual arguments, completed result fields attempting to overwrite it, two IDs with distinct titles, out-of-order completion, assistant stream chronology and ignored reasoning.
- [ ] Implement `GuideToolIntent(String title, String description)` and a `none()` empty projection. Only String fields become display text; normalize control characters to spaces without interpreting markup, commands or translation keys. Use no arbitrary character caps.
- [ ] `GuideToolActivity.intent()` reads live title/description when those fields exist; otherwise reads the first existing `INVOCATION_RUN_JAVASCRIPT` message with two arguments. Missing/blank/malformed projection falls back to empty intent. Never read normalized Tool result fields for intent.
- [ ] `GuideToolInvocationPresentation.messages` writes the existing key with two sanitized strings when intent exists; the zero-argument form remains unchanged for old calls. No new vocabulary key, history field, invocation field, bridge field or schema version.
- [ ] Keep all history/wire schema, argument type/control, identity, status and source decoding strict. Derived intent falls back for legacy zero arguments, blank strings, unsupported string arity and malformed raw live metadata. Producers sanitize controls before constructing messages.
- [ ] Round-trip pending/completed calls through `GuideHistoryCodec` and `ServerAgentEventCodec`. Assert intent survives and source/raw arguments/results/reasoning remain absent from durable/live-start payloads as before.

### Task 3: Player cards and first-class JavaScript detail

- [ ] Add projection tests for normal/debug detail and literal title/description Components; include Markdown-like, translation-like, command-like, quotes, Unicode and long text.
- [ ] Append intent to the closed detail view with compatibility constructors. Preserve every existing JS typed recipe/item/table/scalar/generic-bounded result path and live Debug source display.
- [ ] In the collapsed card, render the dynamic title with `Component.literal`, wrap it, and render description before actual result summary. Localized title/description fallback applies only to empty legacy intent. Keep invocationId row/focus identity and independent localized status.
- [ ] In detail, wrap title/description as literal text before separate status/input/output. Leave actual result narration and source binding unchanged. Full technical tool ID stays visible in the existing Debug section.
- [ ] Do not run model text through semantic markup, Component JSON, translation lookup, callback registration, or script evaluation. Do not add loader hooks.

### Task 4: Decision, review and verification

- [ ] Record the exact user approval and zero-or-two message semantics in decision 037; add the SKMB index row, with commit `pending` until root integration. Add a concise development contract note.
- [ ] Send file-ready notification to root and the read-only reviewer with changed paths and invariants; resolve concrete findings.
- [ ] Run `git diff --check` and parse both language JSON files offline. No Gradle without explicit root slot.
- [ ] When authorized, run focused tests:
  `./gradlew :common:test --tests '*RunJavascriptIntentTest' --tests '*GuideToolIntentTest' --tests '*GuideStateReducerTest' --tests '*GuideToolDetailPresenterTest' --tests '*GuideHistoryCodecTest' --tests '*ServerAgentEventCodecTest' --tests '*GameGuideAgentTest' --tests '*AgentSystemPromptTest' --tests '*OpenAllayScreenProjectionTest'`.
  Expected: zero failures/errors; no network/game calls.
- [ ] Root runs the common suite and both loader build gate after integration. Report actual execution results; do not claim runtime/UI acceptance here.

## Implementation and verification record

- Implemented the optional input/model guidance, immutable plaintext intent,
  existing-message history/wire representation, literal card/detail rendering,
  and deterministic contract/regression tests.
- Independent read-only review is closed. Corrected neutral legacy fallback
  wording and the decision-index placement. Added actual malformed-to-corrected
  Agent recovery coverage.
- Root ran the final focused native Gradle gate: **83 tests, 0 failures,
  0 errors, 0 skipped**. This result includes the fallback meaning assertions
  and recovery regression.
- Language JSON duplicate-key parsing and `git diff --check` passed.
- After the focused gate, root approved the neutral JavaScript Tool descriptor
  wording and ran the full native common suite on the final source:
  **834 tests, 0 failures, 0 errors, 4 skipped**.
- Both default loader builds passed with the nested Builder Extension from the
  verified clean pinned `5354853` checkout. The isolated-worktree gate supplied
  the prepared Extension directory explicitly; default bundling remained enabled.
- Root owns final integration with UI polish, runtime screenshot review,
  the separate feature commit and push. This worktree remains uncommitted.

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
