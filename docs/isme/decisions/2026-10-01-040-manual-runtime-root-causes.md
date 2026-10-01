# SKMB-2026-10-01-040: Manual runtime root-cause corrections

- status: accepted under explicit user implementation delegation
- decided_by: root implementer
- approval_source: user supplied four real-client failures and provider HTTP400 details, required root causes corrected in full, full verification, commit and push; “发版暂时不发”
- date: 2026-10-01
- commit: d17ff22
- patterns: B_state_persistence, D_external_dependency, E_security_boundary, F_fail_semantics
- scope: restored Tool IDs at provider boundaries, strict JS root recovery, truthful source/context diagnostics, automatic output maximum

## Evidence and selected behavior

The actual Fabric0.2.4 release JAR/Luna manual client showed repeated first-turn
HTTP400 only after a Tool-bearing completed request. Durable history qualifies
call IDs by request UUID, yielding 66 characters; the endpoint confirmed maximum
64. A fresh session worked. Preserve durable/internal correlation IDs and strict
history5/serverprotocol5. Map invalid outbound IDs deterministically at protocol
encoding, preserve valid provider IDs, reserve them before assigning mapped IDs,
and maintain every ToolUse/ToolResult pairing. OpenAI's actual64/alphabet constraint
is an external protocol boundary, not a new product cap. Anthropic retains its
own constraints. Do not change history, drop requests or silently clear sessions.
Allowlisted400 parameter/code classifiers identify tool-call protocol rejection;
raw provider response/body/endpoint/credential never enters UI, logs or traces.

The model selected `mc.player` then `mc.game` instead of bare selector IDs.
Keep bare-name selection strict and duplicate execution semantics unchanged.
Document access `mc.player.position` with `roots:["player"]`. Invalid/unknown
selector failure gives declared/available bare IDs and a correction for a known
single mc. access prefix. Declared-but-unavailable snapshot stays explicit;
never turn this into an empty fact or auto-select roots. Returning an executable
module remains invalid; generic recovery asks for JSON operation data instead
of modules/functions. This is not evidence terrain or the module is absent.

Settings diagnostics must consume actual immutable published knowledge/source
state, not hardcoded empty lists. Distinguish known empty, not captured/unknown,
failed and retained snapshots. Pending writes/active requests are current counts;
checkpoint counts are compaction records, not message totals. Context-token
estimate must come from the actual captured/planned request estimate when known,
not sum checkpoint estimates; unavailable is unknown, not a fabricated zero.
The source/context diagnostics projection does not capture live Minecraft data
on a worker and retains scope/connection ownership. A counts-only immutable source
snapshot is published during existing KnowledgeRegistry reload. Unknown/not-loaded
and missing factual evidence remain unknown, not zero; last valid facts can be
retained with an explicit partial/failure state.
GuideContextProvider's common connection-clear hook runs on manager disconnect,
shutdown and actor/history-scope replacement. Minecraft context capture clears
published knowledge documents/index/source diagnostics and old primary provider
handles without querying providers. Persistent supplemental provider configuration
and disabled-primary policy remain, but their previous connection's published
facts do not. Both loader paths consume this same common hook. The next capture
loads fresh connection facts; before it, source status is not loaded/unknown.

An optional local `BiConsumer<AgentRequest,Integer>` observes the existing UTF-8
estimate over the exact `ModelRequest` immediately before each model dispatch.
Existing/server Agent constructors use no-op observers. ClientGuideRuntime owns
latest `(requestId, estimatedTokens)` by actor/session in its endpoint-local map;
capability projections share it. GuideLocalEndpoint's default lookup is unknown.
GuideService accepts only the selected local profile and active/latest request UUID.
HistorySettingsBinding reads that optional value on the settings-owner tick.
A new request before dispatch, a server model, a changed endpoint/profile, cleared
session, or disconnected connection cannot display an older known estimate.
Counts describe this current runtime projection, not lifetime totals. No AgentEvent,
history5, serverprotocol5 or provider wire field is added. Observer failure cannot
break model execution, and no estimate is invented from checkpoint counts.

Output budget defaults to the model's maximum, not fixed8192. Preserve explicit
manual output budgets; omitted/cleared output is automatic. Resolve from exact
trusted metadata then eligible bundled BEST maxOutputTokens, otherwise require
manual output. Preserve explicit1M context and endpoint/model/protocol. Runtime
ModelConfig still has positive concrete integer limits and ContextBudget must
validate their relationship. Saved schema2 permits explicit nullable/omitted
output ownership while keeping unknown fields/version rejection. Disabled
unresolved rows remain representable and cannot crash projections. No guessed
context-derived maximum or arbitrary fallback. Existing explicit8192 histories
are not silently rewritten; root's disposable Luna test profile will be corrected
to model maximum as requested.

## Ownership and terminal semantics

Provider codecs own outbound IDs; history keeps original stable IDs. Root selection
and result normalization own execution recovery; model prose never overrides the
structured failure. KnowledgeRegistry/settings diagnostics consume immutable
snapshots; request estimates follow the selected connection/session runtime.
Mode/source generation changes and disconnect must clear stale values. Missing
source/estimate does not fabricate a successful empty fact. Settings/output
changes affect future requests only. Ordinary cancel/retry/durable lifecycle
semantics remain; no schema migration or automatic provider replay.

## Verification and delivery

Deterministic tests cover collision-safe outbound IDs and Tool pairs, restore
then next-request encoding, root invalid→corrected Agent recovery, executable
module rejection/recovery, source known/unknown/failed publication, actual latest
context estimate and disconnect, nullable automatic output and manual overrides.
Full common suite and both loader builds/packaging/locale/secret checks must pass.
The actual manualclient is retained separately. Commit/push this correction;
no tag, version bump or release publication in this task. Experimental039 work
is paused and not mixed into this correction.
