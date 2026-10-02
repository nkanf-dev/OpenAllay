# UI, HUD, Notifications, and Decoupled Voice Implementation Plan

> **For agentic workers:** Use isolated assigned worktrees. Root coordinates shared contracts, serializes checked-in Gradle, reviews exact delivered source, and commits/pushes small verified batches. No actual game, microphone, clipboard, paid-provider calls or private runtime-config inspection in worker tests.

**Goal:** Polish the K fullscreen UI, provide optional configurable interactive HUD and independent native notifications, and ship cross-platform push-to-talk transcription through a decoupled local Native recognition backend and optional HTTP speech backend.

**Architecture:** Existing GuideService owns tasks. Client presentation owns shared drafts and surface visibility; fullscreen/HUD are temporary views. Live accepted result receipts drive notifications independently of snapshot/history loading. A client-only voice runtime owns explicit recording/transcription and inserts final text into the captured session draft. A mature local Native recognition engine is required in the first release. Optional cloud/local HTTP services share the backend interface. Model files are acquired explicitly with integrity checks, not silently bundled or downloaded at startup.

**Tech Stack:** Java 25, Minecraft 26.2 native GUI/Toast and exact Fabric/NeoForge rendering hooks; existing settings/atomic writer/credential resolver/HTTP interfaces; Java Sound or existing OpenAL PCM capture with platform permission preflight; selected mature native recognizer; deterministic fake capture, workers, dispatchers and HTTP tests.

---

## Scope and defaults

- K remains the main conversation. Compact adaptive header/actions, readable transcript budget, scrollable topmost session drawer, severity notices, explicit technical detail expansion and preserved telemetry. Draft text/images survive temporary Screen navigation.
- HUD and notifications are independently optional, initially disabled. Passive HUD does not capture mouse or game keys. Explicit non-pausing editor/interaction Screen supports position/size/scale/opacity and a compact input view of the same selected session. Closing it returns normal game controls; no background pointer capture.
- Background opacity slider previews in memory and keeps text opaque. Anchor/offset/size/scale/opacity persist through current display config. Save once on Apply/release, not per render.
- UI settings has Fullscreen/HUD/Notifications groups. New callbacks and render projections do not create tasks, contexts or token estimates.
- Notifications: new final reply, valid card batch, task finish/failure options; batch per request, never per token. Native Toast supports fullscreen and respects F1. Off-overlay/on-notification works. Restore/page/fork/disconnect cancellation are not new messages. No global toast clearing.
- Voice: explicit push-to-talk and optional mic button, captured PCM to WAV, OpenAI-transcriptions-compatible HTTP provider with configurable endpoint/model/credential reference, editable draft insertion. No auto-send, live duplex or hidden cloud fallback. Select and verify the first local Native recognition engine/model through current research and reproducible offline tests. Backend worker does not touch Minecraft and service remains client-only. State indicator exists even with overlay/notifications disabled. No fake unsupported voice controls.
- Credentials/config worlds/history/exports are preserved. Latest Only exact current formats; no internal versions/migrations or user-data reset.

## Files and ownership

1. Display/settings worker: `guide/ui/GuideDisplayConfig*`, `GuideUiConfig` records and projections, settings `SettingsSection/UI` and `OpenAllaySettingsScreen` UI/Voice sections; async display saves preserve newly added configuration across old name/debug toggles. Voice settings contract supplied by voice worker, not guessed.
2. Fullscreen worker: `OpenAllayScreen`, `GuideUiLayout`, shared draft/state client classes, theme/notice/detail presentation, tests. Supplies presentation draft attachment API for HUD/voice; no settings whole-file changes.
3. HUD worker: HUD projection/render/editor/chat-lite, two loader registration and client coordinator wiring, native Toast view hooks, voice ephemeral status rendering, shared key mappings. Other workers supply typed interfaces; loader ownership remains here.
4. Notification worker: GuideService live presentation receipts/sink, GuideServiceManager binding/fences, independent notification controller/queue/coalescing/read-state, tests. Must not change execution semantics, usage/pricing or persistence.
5. Voice worker: `client/voice` capture/permission/PCM/WAV/HTTP/STT/runtime/config/credential actions, tests; client-only status/actions for Screen/HUD/settings supplied as hooks. No product Screen/loader whole-file edits.
6. Root: plan/spec, exact shared merges, localization delta review, serialized native tests/packages, actual-source reviews and small verified commits/pushes. Fullscreen/settings/loader method owners integrate sibling hooks without duplicating files.

## Tasks and native gates

- [ ] Define/freeze shared draft, presentation receipt, UI config and speech runtime contracts; record exact classes/methods in task coordination before implementation overlap.
- [ ] Write pure config/validation tests: current exact shape, valid range boundaries, unknown/extra/fractional inputs, save failure/last-valid retention, name/debug toggles preserve HUD/notification preferences.
- [ ] Implement UI settings records/projections/save + independent controls and local preview/reset. Focused native: `*GuideDisplay*Test`, `*SettingsLayoutTest`, `*SettingsScreenProjectionTest`, `*ClientSettingsServiceTest`.
- [ ] Write draft and layout regressions: K close/reopen/settings return, session/world ownership, late pasted/transcribed results, receipt pin transfer, correct failed edit preservation; small-screen readable budget and drawer render/hit parity.
- [ ] Implement shared draft/controller and fullscreen polish using existing native cards/virtualizer. Focused native: `*Composer*Test`, `*GuideUiLayoutTest`, `*OpenAllayScreen*Test`, `*GuideUiClickRouteTest`, `*GuideTranscriptVirtualizerTest`.
- [ ] Write HUD/editor pure geometry/focus tests: all anchors/scales/opacity 0/255, resize clamp, no passive mouse capture, explicit editor/interaction only, cancel/apply config with no per-frame IO, same task/session no new Agent.
- [ ] Register exact Fabric rendering module and NeoForge early mod-bus delegate; implement passive HUD, editor and compact explicit input view. Focused native common tests and actual two-loader `compileJava/test/build`.
- [ ] Write live receipt tests: source binds before first admission, initial subscribe/restore/page/fork never toast, duplicate final/card updates coalesce, failed cancellation/disconnect fences, another session not falsely read, own-toast hide not clear all.
- [ ] Implement independent notifications on accepted live transitions and native Toast. Test off/on combinations and current-surface visibility with fake native port.
- [ ] Write voice backend tests: valid WAV/multipart/text parse, configured cloud/local URLs, cancellation/no retry surprise, credential resolution at dispatch without leaking secrets, bounded clip/empty/error response, device fake lifecycle, generation/draft revision prevents overwrite and disconnect closes capture.
- [ ] Implement cross-platform explicit capture via Java Sound with macOS permission/bundle preflight; backend-independent STT port + configurable HTTP provider; configurable voice device/PTT settings and status, same shared draft owner. No actual capture in tests.
- [ ] Integrate on one source snapshot. Root runs `./gradlew -PbundleExtensions=false -Dorg.gradle.jvmargs=-Xmx1536m --max-workers=1 :common:test :fabric:build :neoforge:build` exclusively; assign precise failures by owner, retain strong assertions.
- [ ] Build dependency-safe cumulative delivery snapshots: display settings/shared draft/fullscreen → HUD → notifications → decoupled voice. Each actual committed tree passes focused/full common and both loaders before its small commit/push. Do not stage subsets of an untested all-feature tree.
- [ ] Run final locked Extensions/default-bundled build and offline/package checks. Keep live microphone, OS permission, screenshots and paid-service acceptance explicitly not run unless separately executed/authorized. No release/tag by default.

## Verification success criteria

- Fullscreen body remains readable at scaled 240×180/320×240/427×320, English/Chinese labels, active task plus attachments/pending; original content is not cut. Drawer visible/clickable layer matches, Escape/focus/IME intact.
- Persistent HUD settings independent of notification settings; alpha previews do not dim text or write every frame. Both loaders use shared renderer/state and current source API signatures.
- New message notifications use admitted live identities, not persisted row diffs. No per-token spam or history replay; teardown cannot emit synthetic canceled-task alerts into a new connection.
- Explicit voice input works through a real backend interface and configurable HTTP endpoint, no model name or protocol guessing. Fake device tests cover Windows/macOS/Linux strategy paths; exact microphone/live/native library behavior is not claimed from these tests.
- PTT transcription never silently sends a task, replaces edited text/images, switches model/session, remains recording after focus loss, or uploads to an unselected provider. Native recognition dependencies are declared, platform-specific and verified; no realtime voice dependency is hidden in the package.
- All former task controls, true usage counts/cache/price quotes, fork image/reference ownership, /compact compensation and remote FIFO tests remain green.
