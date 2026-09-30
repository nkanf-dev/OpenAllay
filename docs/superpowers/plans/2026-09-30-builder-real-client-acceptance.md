# Builder Real-Client Acceptance Implementation Plan

> **For agentic workers:** Execute the narrow E2E harness tasks with parent review checkpoints. Do not commit before review.

**Goal:** Exercise the separately bundled Builder Extension through the real GuideService, production JavaScript Tool, and Minecraft integrated server, while retaining independent native readback evidence.

**Architecture:** A loopback-only deterministic provider emits explicitly pre-authored Tool calls. Builder fixture programs call the shipped Extension modules and native backend, never a detached test backend. The opt-in E2E controller independently reads native server block states, checks fixed landmarks and exact lifecycle summaries, and writes a redacted report. It never grants unrestricted access.

**Tech Stack:** Existing Java E2E controller, Minecraft 26.2 owning-thread APIs, Gson, Python stdlib fixture and unittest, checked-in Gradle wrapper.

---

### Task 1: Deterministic model fixture

- [x] Add `scripts/e2e-builder-fixture.js` with all six compact presets, small geometry/decor/terrain sites and native template transforms.
- [x] Extend `scripts/e2e-model-fixture.py` to recognize `OpenAllay E2E Builder <phase>`, load `minecraft-builder`, emit the actual program, and only narrate observed Tool results.
- [x] Test that Builder calls use `openallay__run_javascript`, no injected backend, and failure never becomes fabricated success with `python3 -m unittest discover -s scripts -p 'test_e2e_model_fixture.py'`.

### Task 2: Independent readback

- [x] Add `common/src/main/java/dev/openallay/guide/e2e/GuideBuilderE2EProbe.java` containing fixed expectation positions, pure result validation, and integrated-server owner-thread block-state capture.
- [x] Extend the controller report with a separate `nativeAcceptance` object. A successful returned status alone does not pass acceptance. Check exact native IDs/properties and real Extension activation.
- [x] Run `./gradlew :common:test --tests 'dev.openallay.guide.e2e.*'` and retain test results.

### Task 3: Native disposable world lifecycle

- [x] Add opt-in `openallay.e2e.createWorld` at TitleScreen using `createFreshLevel`, survival, cheats off, and flat preset; reject existing names and non-`openallay-builder-` names.
- [x] Add elapsed timeout reports and optional explicit `openallay.e2e.revokeUnrestrictedAfterCapture=true` test-only revocation through `ClientSettingsService`; never enable access.
- [x] Packaged launcher worker and root ran the actual clients, retain report/trace/screenshots and original versus harness-instrumented JAR hashes. This worker does not launch clients or make paid calls.

### Task 4: Lifecycle phases

- [x] Fixture/controller validate disabled native access, failed-partial with earlier write retained, explicit session cancellation with no later write, conflict-aware undo, and reload of native world/template/journal state.
- [x] Keep the separate server-model Java denial fixture and common isolation tests distinct from graphical acceptance; no server-model graphical result is claimed.
- [x] Report unsupported or failed cases as failures or not exercised, never waive or label them complete.


## Retained runtime results — 2026-09-30

Root ran packaged graphical clients in new disposable native survival worlds with
cheats off. This worker did not launch clients or make provider calls. Deterministic
loopback fixture responses are explicitly pre-authored, not live-model output.
The report preserves the request outcome separately from the independent native
acceptance outcome. These completed runs have `outcome: COMPLETED` and
`nativeAcceptance.outcome: PASSED`:

| Loader / phase | Retained report path | Native checks |
| --- | --- | ---: |
| Fabric disabled | `build/e2e/packaged-builder/fabric/20260930-disabled-04/report.json` | 1 |
| NeoForge disabled | `build/e2e/packaged-builder/neoforge/20260930-disabled-04/report.json` | 1 |
| Fabric full fixture | `build/e2e/packaged-builder/fabric/20260930-full-01/report.json` | 85 |
| NeoForge full fixture | `build/e2e/packaged-builder/neoforge/20260930-full-01/report.json` | 85 |
| Fabric same-world reload | `build/e2e/packaged-builder/fabric/20260930-full-01/phases/20260930-reload-01/report.json` | 85 |
| NeoForge same-world reload | `build/e2e/packaged-builder/neoforge/20260930-full-01/phases/20260930-reload-02/report.json` | 85 |
| Fabric primary Luna copy | `build/e2e/packaged-builder/fabric/20260930-luna-copy-02/report.json` | 58 |
| Fabric primary Luna exact undo | `build/e2e/packaged-builder/fabric/20260930-luna-copy-02/phases/20260930-luna-undo-01/report.json` | 80 |

Each report directory retains its actual `trace.json`, `client.log`, and launch
metadata. Launch metadata records artifact identity and controlled resume
provenance. Screenshot files are retained where requested; these runs do not
claim a separate subjective approval of every GUI or build's visual quality.

The full fixtures exercised all six compact presets, geometry and supported
linked decoration, controlled terrain paths, native template rotation and both
mirrors, failed-partial writes, explicit session cancellation, and conflict-aware
undo. Both full reports also passed frozen-authority timing checks: the setting
was revoked before the first observed JavaScript call and before the first
trusted native evidence. Reload compares the prior recorded origin and exact
journal IDs/statuses plus template names; a moved player is not treated as a
world persistence failure.

Primary Luna used the user's explicit 1,000,000-token context budget. Its normal
copy task and later exact-ID undo task were real provider requests, not the
loopback fixture. The copied platform and clockwise-90-degree directional states
were independently observed before the separate undo phase. Root's strict copy
audit passed and is retained at
`build/e2e/builder-acceptance-2026-09-30/luna-copy-proof.json`. The strict external
undo audit also passed. It linked copy operation
`ca746c4c-9127-4ce8-809b-329609b699c3` to exact undo operation
`76d5a754-584a-4c8f-afb9-91ed113bff3d` in world UUID
`a7c7f228-55ea-4dd2-ac64-dbd677a96b95`. It verified 75 restored cells and the
inverse journal's exact same 75 positions/postimages, with no conflicts or
uncertain entries. Root is retaining the strict undo proof under
`build/e2e/builder-acceptance-2026-09-30/luna-undo-proof.json`.

### Earlier attempts remain failures

- Fabric `20260930-disabled-03` denied native Java correctly, but the request failed
  after a fixture continuation rejected the production plain-text failure
  projection. The Python fixture regression fix was verified, then disabled-04
  completed. Disabled-03 is not counted as acceptance.
- NeoForge `20260930-reload-01` ended `HARNESS_FAILED` before a request because the
  harness incorrectly required unchanged player coordinates. The retained-origin
  correction kept native block and journal checks intact. Reload-02 completed.
- Fabric primary Luna `20260930-luna-copy-01` failed with
  `model_transport_error`. Copy-02 is the completed live copy run; the failed
  attempt is retained and is not waived.

Graphical server-model JVM isolation and in-flight GuideService cancellation were
not exercised by these client-local runs. Common isolation tests and explicit
Builder session cancellation are separate evidence, not substitutes for those
unrun graphical scenarios.
