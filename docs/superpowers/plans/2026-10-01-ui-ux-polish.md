# Native UI and evidence polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the small Minecraft-native Guide frame useful at supported GUI sizes and show honest, readable action and evidence details.

**Architecture:** Extend the existing deterministic common layout and closed presentation vocabulary. The screen consumes immutable GuideService data; it does not add execution, network access, or an orchestration loop. Normal evidence exposes friendly source, coverage, authority meaning, and recorded capture time; technical IDs, provenance, source details, JavaScript internals, and normalized JSON remain Debug Mode-only.

**Tech Stack:** Java 25, Minecraft 26.2 common native screens, JUnit 5, checked-in Gradle wrapper, English and Simplified Chinese JSON translations.

---

## Scope and coordination

Worktree: `/tmp/openallay-ui-ux-polish-20261001`; branch `fix/ui-ux-polish-20261001`; base `d254bff`.
The task authorizes the friendly normal evidence presentation despite the older SKMB-010 timestamp restriction. No execution authority or persistence contract changes. README/development and SKMB baseline have been read; the final docs worker owns reconciled product prose.

The independent reviewer inspected Fabric screenshots 00–10 from `build/e2e/packaged-builder/fabric/20260930-full-01/screenshots/screenshots`. Send proposed changes before implementation and revised details after source is ready. Parent owns actual post-integration screenshots and final independent review.

Do not run Gradle, GUI clients, providers, or network operations without a parent build slot. Do not commit or push. Parent merges focused source edits with the model-catalog and JavaScript-intent worktrees. Do not copy their whole files. Settings ownership is restricted to General/About page construction/render/scroll, while intent title/description and JavaScript detail content remain the intent worker's responsibility.

## File map

- Modify `common/src/main/java/dev/openallay/guide/ui/GuideUiLayout.java`: responsive header controls and composer rectangles, nonnegative body/detail geometry.
- Modify `common/src/main/java/dev/openallay/guide/ui/GuideUiClickRoute.java`: explicit close, background clicks consume without dismissal.
- Create `common/src/main/java/dev/openallay/guide/ui/GuideEvidencePresentation.java`: closed friendly source/coverage/authority keys and capture time, no raw internals.
- Modify `common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java`: consume layout rectangles, bounded title/status text, full model tooltip, normal evidence detail, explicit close and modal focus/scroll keys.
- Modify `common/src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java`: General/About scroll positions, viewport clipping, bounded page widgets, preserve drafts.
- Modify `common/src/main/java/dev/openallay/client/gui/settings/SettingsLayout.java`: pure page scroll/visibility helpers used by General/About.
- Modify `common/src/main/resources/assets/openallay/lang/en_us.json` and `zh_cn.json`: neutral action progress, header actions, detail close and friendly evidence labels.
- Modify/add focused JUnit tests under `common/src/test/java/dev/openallay/guide/ui/` and `common/src/test/java/dev/openallay/client/gui/` plus `client/gui/settings/SettingsLayoutTest.java`.

### Task 1: Responsive Guide geometry

- [ ] Add layout tests at logical sizes 569×320 (1708×960 at scale 3), 427×320 (1280×960 at scale 3), 320×240, 240×180, and 900×500. Assert every header rectangle is inside the top bar and does not overlap another control; title has its own reserved rectangle. Assert send/stop/retry fit inside composer and screen, and transcript/progress/composer do not overlap. Test detail open and closed.
- [ ] Use a deterministic header action record. Fit measured localized Sessions/Export/Refresh labels with padding, fixed symbolic New/Delete/Settings controls, and a model identity control with full tooltip. Wrap to a separate action row before title overlap; at minimum width wrap controls over two action rows. The body begins after the computed top bar.
- [ ] Reserve 68 logical pixels for `3 * 20 + 2 * 4` composer action height plus the existing bottom margin. Keep multiline composer and native virtualization/scissors.
- [ ] Consume exact control/composer rectangles in `OpenAllayScreen.init`; bounded title/model status/notice rendering must not overlap buttons. Use EN/zh translation keys rather than literal Chinese labels.

### Task 2: Friendly normal evidence and neutral progress

- [ ] Add projection tests for complete/partial/unknown coverage and every authority. Verify no raw tool/source IDs, provenance, JVM details, metadata detail values, normalized JSON, credentials, history scope, or transcript are representable in the normal record.
- [ ] Implement `GuideEvidencePresentation.from(GuideSource)` with closed source keys, closed authority meaning, coverage key, and `Instant capturedAt`. Unknown sources map to an unknown-source label, not their internal ID. Known player/registry/recipe/viewer/resource/data-analysis sources map to friendly names.
- [ ] Render the friendly source, coverage, authority meaning, and localized recorded date/time in selected source and selected Tool detail in both display modes. Use capture date/time instead of a repeated freshness disclaimer. Show explicit no-recorded-source text for a completed JavaScript action with no evidence. Debug retains the existing exact technical evidence renderer and raw IDs/provenance.
- [ ] Replace `screen.openallay.progress.tool_wait` copy with `Running action` / `正在执行操作`. Do not guess read-only behavior or change statuses/permissions. JavaScript intent titles/descriptions remain a separate worker change.

### Task 3: Detail dismissal and keyboard route

- [ ] Update `GuideUiClickRouteTest`: action routes precede close, only the explicit close rectangle dismisses, background clicks return inside-detail, and outside clicks remain normal routes.
- [ ] Add a native-looking close target in the fixed detail header, with stable `detail:close` focus identity and localized narration. Clicking text/background consumes without closing or passing through to transcript.
- [ ] Handle Escape before composer input when detail is open: close only detail and preserve draft/request. F6 cycles only detail actions while detail is open; Up/Down/PageUp/PageDown/Home/End scroll detail. Confirmation activates a focused detail action, not composer submission. Cancel still delegates to GuideService and never closes the Guide.
- [ ] Add pure routing/projection tests for detail-first Escape and modal content focus filtering; preserve existing composer Enter/Shift+Enter and model selector tests.

### Task 4: General/About viewport access

- [ ] Add pure SettingsLayout page tests for scroll maximum, widget visibility, page origin, and footer separation at 427×320, 569×320, 900×500, and 240×180.
- [ ] Add General/About to content wheel route. Capture draft before rebuilding scrolled widgets. Compute scroll maximum from complete page height and existing editor viewport, not from helper guesses.
- [ ] Render General/About with `pageScroll` and a scissor bounded to `layout.editor()`. Move General widgets with the same origin and hide any widget not fully inside the page. Do not render labels over a partly visible input. Put About repository action below the measured banner/description and keep it reachable by scrolling rather than pinned across text/footer.
- [ ] Preserve footer buttons and screen-close semantics. Do not edit Models page methods or catalog resolution.

### Task 5: Terminal pending-Tool and failure clarity (root-approved scope addition)

- [ ] Add `GuideToolDisplayStatus` as a UI-only closed enum; terminal request plus raw RUNNING yields NO_RESULT_RECORDED, while original status/evidence/debug remain unchanged.
- [ ] Add `GuideToolDetailView.forRequest` and consume the same status in normal card/detail. Suppress pending narration/summary only for the derived no-result state. Test cancellation, disconnect failure, interrupted recovery, and all recorded success/failure statuses.
- [ ] Correct Retry copy to resend the latest failed/stopped question, identify its text in the tooltip, and describe Stop's existing completed-action semantics only in its tooltip.
- [ ] Map `invalid_arguments` and `invalid_tool_arguments` to the same neutral closed message. Keep unreviewed raw failure reasons out of normal projection; use action, not query wording.
- [ ] Record accepted root decision038 using the exact original user review/information request and root acceptance of independent findings. Do not alter historical010 approval evidence.

### Task 6: Verification and handoff

- [ ] Inspect `git diff --check` and changed files. Validate both language files with Python JSON parsing, and inspect new key parity without adding generated output.
- [ ] Request parent build slot or send exact focused command: `./gradlew :common:test --tests 'dev.openallay.guide.ui.GuideUiLayoutTest' --tests 'dev.openallay.guide.ui.GuideUiClickRouteTest' --tests 'dev.openallay.guide.ui.GuideEvidencePresentationTest' --tests 'dev.openallay.client.gui.OpenAllayScreenProjectionTest' --tests 'dev.openallay.client.gui.OpenAllaySettingsScreenProjectionTest' --tests 'dev.openallay.client.gui.settings.SettingsLayoutTest'`.
- [ ] Send parent and independent reviewer exact changed methods, known overlap notes, and test status. Parent performs integrated common/both-loader gate and actual GUI screenshot acceptance before commit/push/release.

## Verification results

The root executed verification against the frozen isolated UI patch:

- Focused gate: **61 tests, 0 failures, 0 errors, 0 skipped**.
- Full common suite: **830 tests, 0 failures, 0 errors, 4 skipped**.
- Fabric and NeoForge production builds: **passed**, both with the default nested Builder Extension.

These results do not constitute independent visual approval. Root still owns the
method-aware intent/status/result merge and its regression tests. Final integrated
actual GUI screenshots and independent Minecraft UI/UX review remain pending.

## Integrated intent and catalog verification

Root merged the UI patch with the model catalog and JavaScript intent on the
current main baseline. The merged view retains intent and derived status, puts
execution status first, keeps factual result/failure summaries before the intent
preview, and preserves full literal intent in detail. Terminal pending calls keep
their recorded raw state and planned intent. Factual request/persistence rows wrap;
header notices retain priority and full hover text.

- Final focused projection/layout gate: **66 tests, zero failures/errors/skips**.
- Final integrated common suite: **896 tests, zero failures/errors, 6 skipped**.
- Default Fabric and NeoForge builds with verified pinned Builder: **passed**.
- Actual post-integration screenshots and independent visual review are the next
  retained acceptance step.
