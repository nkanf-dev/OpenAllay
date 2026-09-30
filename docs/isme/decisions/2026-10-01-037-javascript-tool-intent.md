# SKMB-2026-10-01-037: Model-written JavaScript Tool intent

- status: accepted by the user's explicit request below
- decided_by: user/designer
- date: 2026-10-01
- commit: pending
- patterns: B_state_persistence, E_security_boundary, F_fail_semantics
- scope: JavaScript invocation input guidance and player card presentation

## Approval evidence

The user requested this independent feature and commit:

> analysing game data 目前那个执行 JavaScript 居然在工具卡片，就是在 UI 里面显示给玩家看的卡片都是调这个东西。这个应该就是说让模型去调用这个东西，给它起一个标题，去说明它正在做什么。因为我们相当于现在只有一个工具，就是跑掉 JavaScript 代码。那么应该给它加上一个意图，就是告诉玩家在做什么，然后渲染在那个上面。就是说模型在调这个时候需要加一个额外的字段，就比如 title 和 description，相当于这样的一个意思。这个也作为一个独立的 commit 去做，然后这些做完之后就继续收尾，提交推送就可以了

## Selected behavior

`openallay:run_javascript` accepts optional String `title` and `description`
input properties. The model instructions require both on each new call: a short
player-language title and description of intended work. Optional schema fields
preserve already authored calls, replay fixtures and legacy history. Absent,
null or blank metadata uses localized defaults. Non-string live inputs fail the
normal `invalid_arguments` input contract; Gson must not coerce numbers or
booleans into descriptive text. No arbitrary length or character-count limit
is introduced.

Intent is display-only model text, not an observed fact, execution permission,
status or evidence. It cannot select code, roots, handles, host topology,
command/JVM authority, cancellation or retry. The existing repeated-call key
ignores valid intent values while retaining malformed argument failures so a
corrected call can recover. Execution receives the original source and selected
roots/handles unchanged.

`CoreGuideService` and its reducer remain the state owners. Pending cards expose
intent immediately from the actual start input. Completion updates the same
invocationId in place. Distinct invocations keep distinct intent, even if they
use the same Tool name or complete out of order. Result messages, normalized
result fields and final answers do not overwrite start intent. Actual execution
status and factual source/result projections remain separate.

Cards and details label nonempty model intent as “Planned action” / “计划执行”,
separate from the code-owned execution status, so a past-tense model title is not
presented as proof of completion.

Render intent as literal Minecraft Components, not translated model strings,
semantic markup, JSON Components, commands or callback-bearing content. Display
control characters are normalized to spaces; this does not rewrite execution
input. Known JavaScript results keep their existing first-class typed views and
Debug source details. Technical tool identity remains available in Debug under
decision 010; the dynamic title does not disguise status or source.

## Durable and wire compatibility

Decision 028 deliberately excludes raw invocation arguments/source from durable
history and server ToolStarted transport. Preserve that boundary. Use the
existing `INVOCATION_RUN_JAVASCRIPT` presentation message and its already generic
String argument array: zero arguments means legacy/default intent; two arguments
mean title and description. Empty strings remain valid per-field fallback. This
adds no strict schema field, vocabulary key, database version or protocol
version. Schema-5 history without metadata and old zero-argument events continue
to decode. Existing persistence retains only the closed display projection,
roots, handles, modules and source summaries, not raw arguments or code.

The derived display projection falls back for legacy zero arguments, blank
strings, unsupported String argument arity and malformed raw live metadata.
Persisted/wire non-string or control-bearing message arguments remain strict
schema failures; corrupted history is not silently accepted. Producers normalize
controls before constructing display messages. Identity, source, status, message
keys and surrounding schemas remain strict. Reasoning stays unrepresentable in
the player projection.

## Verification requirements

Deterministic tests cover optional schema fields and malformed live input,
null/empty fallback, unchanged execution/roots/evidence/permission and duplicate
keys, pending/completed and multiple independent calls, out-of-order completion,
stream chronology and reasoning exclusion, immutable start input, restoration
and server start round-trips, legacy/malformed metadata fallback, literal text
rendering and retained first-class JavaScript result/source details. Both loaders
use the common implementation. Root reviews and runs the full build gate before
making the separate feature commit; implementation alone does not claim a real
player or provider acceptance run.
