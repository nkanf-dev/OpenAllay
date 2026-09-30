# SKMB-2026-10-01-038: Native UI evidence and lifecycle clarity

- status: accepted
- decided_by: root implementer
- approval_source: current root task approval of independent Minecraft UI/UX findings under the user's request for adequate, honest, friendly information; not a claim that the user separately approved each evidence field
- user_request: “另外最后也需要有一个 sub agent 去独立，以专业的 Minecraft UI UX 设计师的角度去评审 UI，包括它的文案方面，就是能不能用户友好，同时能不能给用户足够的信息，这是非常重要的。就是目前的话，感觉 UI 不是特别的好，很多地方都感觉有点混淆的意思在里面，我们不是要愚弄用户”
- date: 2026-10-01
- commit: pending
- patterns: B_state_persistence, E_security_boundary, F_fail_semantics
- scope: common Guide layout, normal evidence presentation, terminal pending-Tool display, detail input, General/About viewport, and EN/zh copy

## Context

An independent Minecraft UI/UX reviewer inspected the actual Fabric packaged
Builder screenshots 00–10 from the 20260930-full-01 run. Header controls were
clipped or overlapped the title at the observed GUI sizes. The third composer
action extended below its region. Source details in normal mode asserted generic
in-game support without exposing the source or its limitations. The fixed Tool
wait label implied a read-only query even during construction actions. Terminal
or recovered requests could retain a raw pending Tool state and display an active
indicator indefinitely. General/About content had no route to content under the
footer. Detail background clicks closed the panel and Escape closed the Guide
before the detail.

The root accepted these findings as implementation fixes for the user's request.
The reviewer remains independent; source completion or unit tests are not final
visual acceptance.

## Decision

Keep the existing small Minecraft-native frame. Common deterministic layout owns
responsive header wrapping and reserves the full composer action height. The
Guide screen remains only a projection and intent sender. GuideService retains
request, session, routing, cancellation, retry, and orchestration ownership.

Normal Tool and source detail shows friendly recorded source, actual authority
meaning, Complete/Partial/Unknown coverage, and localized capture date/time.
Unknown sources use a neutral additional-source label. Client-visible evidence
is not called server-confirmed. Imported or integration data is not generically
called in-game proof. Capture date/time identifies the recorded observation
without repeating a freshness disclaimer on every source. Missing recorded sources
are explicit for completed JavaScript actions. No current-connection
freshness is inferred from a timestamp. Source actions remain in-game.

Raw source/tool/request IDs, authority/completeness enum spellings, provenance,
metadata detail values, JVM/source internals, and normalized JSON remain in the
existing authorized Debug Mode-only projection. No credentials, raw provider
bodies, history scopes, foreign-player state, or model reasoning become visible.
Evidence objects, capture semantics, and durable history remain unchanged.

Tool wait copy says Running action, without claiming a query or inventing intent.
If a terminal request contains a raw pending/RUNNING Tool, derive a UI-only
NO_RESULT_RECORDED state: Stopped before a result was recorded. The normal card
and detail share this projection and no longer show an active spinner or pending
summary. Do not fabricate failure/success or mutate the actual Tool status.
Debug retains the original recorded status. Recorded successes and failures
remain exactly those states. This UI enum is not persisted or added to wire
schemas.

Retry sends the latest failed, stopped, or interrupted question again. It does
not resume an execution step. A tooltip identifies that question. Stop cancels
future work and does not undo actions already completed; the existing GuideService
behavior is unchanged.

Detail has an explicit close target. Escape closes detail first without stopping
a request or closing the Guide. Background clicks do not dismiss detail or click
through its output. F6 and confirmation use detail actions while detail is open;
arrow/page/home/end keys scroll it. Composer draft and transcript anchor survive
layout rebuild/resize. General/About use their existing editor viewport for
scrolling and clipping, preserve drafts, and never paint helpers over the footer.

Failure copy refers to actions rather than read-only queries, treats both
invalid_arguments and invalid_tool_arguments consistently, and does not blame
the player. Unclassified failures stay concise. Unreviewed raw failure reasons
remain unrepresented in normal UI.

## Supersedes

Supersedes only SKMB-2026-07-18-010's normal-mode exclusion of capture time and
friendly authority/coverage presentation. Its technical metadata and privacy
boundary remains in force. Historical approval evidence in SKMB-010 is not
changed or reinterpreted. Other runtime/cancellation/authority decisions retain
their accepted semantics.

## Verification

Deterministic source tests cover wide/narrow GUI logical bounds, all composer
buttons, explicit close routing, detail-first Escape/focus policy, friendly
normal evidence redaction, terminal pending-Tool projection, preserved raw state,
neutral failure code mapping, capture-time formatting, and General/About scroll
geometry. Parent owns focused execution and integrated common/both-loader gates.
Final post-integration actual GUI screenshots require independent review before
release. No visual approval is inferred from implementation or tests.
