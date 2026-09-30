# Automated packaged Builder acceptance plan

User explicitly authorized graphical Minecraft clients and real provider calls.
Use only OMP gpt-kanglives/gpt-6-luna. The user explicitly excluded mikumiku.
Keys remain environment-only.

1. Implement inert-by-default E2E-only fresh native world creation and Builder
fixture phases through production GuideService, with independent server readback.
2. Launch actual packaged loader JARs in isolated instance directories, not source
classes; retain original and instrumented artifact hashes.
3. Run disabled authorization, full construction/template/undo, partial/cancel,
request toggle freeze, reload persistence and model isolation where viable.
4. Both Fabric and NeoForge run the deterministic game scenario. Native reads
verify effects, not model result strings. Capture screenshots when supported.
5. With deterministic acceptance complete, run an ordinary real Luna model
copy/transform task, then a linked undo task in the same disposable world. Retain
both native world states. Do not run a secondary provider.
6. Inspect complete redacted traces, operation/state evidence and actual readbacks;
fix root failures, rerun failed stage. Do not relax acceptance to make it pass.
7. Retain report under build/e2e and publish concise source/version/provider and
verification summary ~/docs. Re-run deterministic tests/builds for harness/code
changes; preserve .DS_Store and all existing world/model settings.

Fixture successes are not model planning acceptance. New harness instrumentation
must be explicitly recorded and disabled in normal runtime. Runtime compatibility
and visible world outcome require retained real-client reports. No automation/
Baritone implementation is part of this task.

## Runtime checkpoint — 2026-09-30

Actual packaged Minecraft26.2 / Java25 clients were run by the root operator.
All passing rows below have request `COMPLETED` and independent native `PASSED`.
The artifacts were OpenAllay0.2.3 acceptance-instrumented JARs with nested Builder.

| Loader / phase | Native checks | Retained report |
| --- | ---: | --- |
| Fabric disabled authorization | 1 | `build/e2e/packaged-builder/fabric/20260930-disabled-04/report.json` |
| Fabric composite construction, partial/cancel/undo, frozen request authority | 85 | `build/e2e/packaged-builder/fabric/20260930-full-01/report.json` |
| Fabric same-world restart | 85 | `build/e2e/packaged-builder/fabric/20260930-full-01/phases/20260930-reload-01/report.json` |
| NeoForge disabled authorization | 1 | `build/e2e/packaged-builder/neoforge/20260930-disabled-04/report.json` |
| NeoForge composite construction, partial/cancel/undo, frozen request authority | 85 | `build/e2e/packaged-builder/neoforge/20260930-full-01/report.json` |
| NeoForge same-world restart | 85 | `build/e2e/packaged-builder/neoforge/20260930-full-01/phases/20260930-reload-02/report.json` |
| Luna ordinary copy/90-degree transform on Fabric | 58 | `build/e2e/packaged-builder/fabric/20260930-luna-copy-02/report.json` |
| Luna linked undo in that same native world | 80 | `build/e2e/packaged-builder/fabric/20260930-luna-copy-02/phases/20260930-luna-undo-01/report.json` |

Both deterministic full reports record `frozenAuthorityTimingPassed: true`.
Both passing restart reports record `exactPersistencePassed: true`. The NeoForge
restart uses the retained original world anchor, not the current player position.
Its new phase records the explicit harness JAR upgrade and unchanged nested
Builder SHA-256. The original full manifest and report were not rewritten.

The real provider was only `gpt-kanglives` / `gpt-6-luna`, configured with an
explicit 1,000,000-token context. No secondary-provider run was used. The strict
copy validator passed and is retained at
`build/e2e/builder-acceptance-2026-09-30/luna-copy-proof.json`: 75 journal entries,
29 changed entries, and exact copy operation ID linkage from the structured
production Tool result. Native undo passed 80 readbacks. The separate strict
undo validator also passed after its JSON count grammar was corrected to accept
integral numeric values such as `75.0`; 10 validator tests passed. It linked exact
undo operation `76d5a754-584a-4c8f-afb9-91ed113bff3d` to copy operation
`ca746c4c-9127-4ce8-809b-329609b699c3` and verified all 75 inverse journal
positions/postimages in the same world, with no conflicts or uncertain results.
The root operator is retaining the CLI proof separately; native reports and the
external validator remain distinct evidence.

Full artifact hashes and result scope are in
`docs/verification/packaged-builder-acceptance.md`. Earlier failed attempts remain
in ignored output and were not treated as passing evidence. No existing user
worlds or model profiles were changed.

The builtin model catalog and intent features are separate product work. These
Builder reports do not claim GUI acceptance for those features or for a later
OpenAllay release. The final combined source/build gate is still pending.
