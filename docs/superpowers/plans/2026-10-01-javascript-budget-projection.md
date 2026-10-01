# JavaScript Result Budget Projection Implementation Plan

> **For agentic workers:** Execute these independent projection and test tasks with review checkpoints.

**Goal:** Keep complete JavaScript results available to the current request while sending useful model views that fit the actual remaining request budget.

**Architecture:** Execution permission, canonical workspace storage, and model transport are separate contracts. Both modes use the same structural presenter. AgentToolResult accepts an explicit encoded UTF-8 budget and retains the unchanged normalized result for diagnostics. Original history records only the first actual provider-visible exchange; canonical values are not placed in durable history.

**Tech Stack:** Java 25, Gson, Rhino, JUnit 5.

---

- [x] Add failing tests in `common/src/test/java/dev/openallay/script/workspace/JavascriptResultBudgetTest.java` and `common/src/test/java/dev/openallay/agent/tool/ModelToolResultBudgetTest.java`: 1.6 MB world-shaped results, multiple encoded budgets, late scalar findings, workspace exactness, closed handles, and exact normalized failures.
- [x] Replace the unrestricted full-dump path in `JavascriptResultPresenter` with the shared adaptive presenter. Add an explicit encoded UTF-8 budget overload. Render metadata, structural schema, size, scalar summary, and bounded previews without domain field lists.
- [x] Add `ModelToolResultProjection` and `AgentToolResult.modelValue(int)` to support actual remaining-budget projection without changing canonical normalized results or execution authority.
- [x] Change `RunJavascriptTool` to call the same presenter in both modes. Keep canonical store/select behavior and Java/JVM execution unchanged.
- [x] Review real safe extracted world-observation fixture, preserve its complete canonical value, and assert bounded model results with useful summary/schema/handles.
- [x] Parent schedules focused Gradle tests after other changes finish. No game or paid provider starts. Report reviewed files and verification results before coherent commit/push batches.

## Verification checkpoint

Initial focused native Gradle gate: 56 tests, no failures or errors. Log: `/tmp/openallay-js-projection-focused.log`. The later lazy source, scalar typing, and provenance changes require the next serial gate. Real world values are not checked in; the 1,597,174-byte temporary fixture is an exact-size, exact-shape synthetic clone, not a semantic record of the user world.

Final focused gate: 62 tests passed, no failures/errors/skips. Includes explicitly supplied pure shape fixture. Log: `/tmp/openallay-js-projection-focused-4.log`. Parent still owns integration and the final control-plane sampling-purpose policy.
