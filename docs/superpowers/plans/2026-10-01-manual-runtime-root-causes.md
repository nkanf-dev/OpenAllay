# Manual Runtime Root-Cause Correction Implementation Plan

> **For agentic workers:** Execute the independently owned tasks with root-only native gates. No new release/tag.

**Goal:** Correct the actual0.2.4 manualLuna protocol, JS recovery, diagnostics and output-budget failures.

**Architecture:** Provider adapters map outbound IDs without modifying durable history. Existing strict root/result contracts supply corrective feedback. Diagnostics consume actual immutable runtime sources/estimates. Model configuration preserves nullable automatic output ownership with concrete runtime limits.

**Tech Stack:** Java25, Gson/JUnit5, existing ModelClient/GuideService/settings/history5/serverprotocol5, Fabric/NeoForge.

## Task 1: Outbound ID protocol (history worker)
- [x] Inspect actual UUID+call29=66 cause and preserve internal IDs.
- [x] Implement provider-safe deterministic fullSHA256Base64url mapping, reserve valid IDs and pair outputs, protocol-specific constraints.
- [x] Add collisions/currentshort IDs/restoredhistory/providerfakevalidation tests; classify allowlisted call_id400 without bodies.
- [x] Root runs focused modelcodec/history tests only after file-ready.

## Task 2: JavaScript recovery (roots worker)
- [x] Add one root-selector vsaccess-path contract sentence and Tool examples; stablecode invalid vsunavailable messages plus knownmc.prefix hint/listcurrentbareIDs.
- [x] Keep exactselection/noaliasnormalization/no duplicatewrite bypass.
- [x] Add generic executable-return JSON-operation guidance, no permissive normalization.
- [x] Test invalidroot→correctedAgent→success/no missingdataset inference/functionstillrejected.

## Task 3: Diagnostics (diagnostics worker)
- [x] Replace empty sourceinput with immutable counts-only KnowledgeRegistry published source state, bound settings supplier.
- [x] Distinguish unknown vs knownzero, document currentcounts/checkpointcount scope.
- [x] Feed actual request estimate independently from checkpointsum; clear connectionstale state; no provider/wire schemachanges without rootdecision update.
- [x] Test source success/partial/failed/empty/unknown and real under-budget estimate nonzero, no liveobjects/worker capture.

## Task 4: Automatic output maximum (output worker)
- [x] Preserve explicitinteger lower budgets; omit/clear output -> exacttrustedmetadata ->bundledBEST max; unresolvedmanual-required.
- [x] Nullable definition/projections/saveownership/GUIoutputautomatic distinct, actual1Mcontext unchanged; remove8192newprofiledefaults only.
- [x] Test malformedfields/types disablednull/no NPE/provider precedence/manualsave/refresh/modelpick/contextbudget.

## Task 5: Root integration/full gate/delivery
- [x] Review worker file-ready contracts/040 states; record SKMB040 anddevelopment/README conciseactualbehavior.
- [x] Rootfocused thenfull `./gradlew clean :common:test :fabric:build :neoforge:build --console=plain` with verifiedBuilderpin/defaultbundling.
- [x] Packaging/ENZH/stricthistory5/server5/API/secret diff checks; compare retainedmanualtraces without private endpoint/key output.
- [ ] Independentsource/rootcause review, commitcoherentfix, integrate/pushmain andverifyQualityCI. NOversionbump/tag/release.
- [ ] Resume039experiment onlyaftercurrentrootcausecorrection delivered.

## Verification result

The final focused Java25 wrapper gate passed **647 tests, 0 failures, 0 errors,
6 skips**. The first focused attempt exposed two invalid source-evidence IDs in
new test fixtures; the fixtures were corrected to namespaced IDs without changing
production validation.

The final clean gate passed **955 common tests, 0 failures, 0 errors, 6 skips**,
and both default Fabric/NeoForge builds. It used the clean pinned Builder source
`53548537bb7db2b4c3cee09af60f0c7b098bbd79` via the existing prepared source option;
default bundling stayed enabled. Distribution, Phase4 and SQLite packaging checks
passed. The 37 offline distribution automation tests passed. English/zh-CN key
parity, JSON parsing, diff checks and credential-value scans passed.

Independent source review found one connection-lifetime defect in the first
source-diagnostics implementation: old knowledge documents/index/source status
survived disconnect. It was corrected through the common context-provider clear
hook, with two-world/manager/runtime tests; the reviewer then approved the final
source with no unresolved P0/P1.

These are deterministic and build checks for the corrected source. The initial
user manual0.2.4 run and its failures remain retained separately; no new graphical
or paid-provider success is claimed. The task does not bump version or create a
release/tag. The independent docs/development.md simplification runs separately
and does not block this correction.

## Source integration

The coherent implementation and regression suite is committed as `d17ff22`.
Main contains the same verified tree. Version remains 0.2.4, released tag remains
unchanged, and no new release is created. Push and remote Quality status are
reported separately when completed.
