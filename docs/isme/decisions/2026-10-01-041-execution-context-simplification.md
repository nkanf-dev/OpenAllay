# SKMB-2026-10-01-041: Execution results and truthful model context

Status: accepted and implemented by explicit designer delegation. Deterministic
verification and both loader builds pass; see the verification record. This is
not a release or a claim of graphical/live-provider acceptance.

## Approval and problem

The user requested a root-cause refactor of the legacy data-source architecture,
review of exported conversations and the entire model-context lifecycle, faster
construction, and removal of unnecessary constraints. They explicitly authorized
breaking formats and deletion rather than compatibility layers. No world save,
existing history database, exported conversation, or operation journal is deleted
by this authorization. This change is source delivery only: no release tag or
product version change.

The retained manual records show these distinct defects:

- Valid JavaScript ran before an empty evidence list changed its outcome to
  `context_evidence_unavailable`. This can misdescribe already completed effects.
- Root declarations hid already captured data. An omitted `items` selector
  produced an undefined value instead of access to the existing item snapshot.
- Actual JavaScript errors reached the immediate model continuation, but the next
  question restored display summaries without arguments, return values or errors.
- That restoration fabricated ToolUse arguments `{durableProjection:true}`.
- Already compact plaintext tool results were incompatible with a reducer that
  expected normalized JSON; reduction could invent `context_result_malformed`.
- Skill document text was lost between asks, requiring repeated model-driven loads.
- Native reads emitted repeated source records; details displayed those before
  useful code/results. Exported conversations retained only tool status.
- Terrain/structure reads and undo used per-block owner dispatch. Construction
  repeatedly rewrote an ever-growing journal, amplifying disk work quadratically.

The field `source` remains the JavaScript program body. It does not request
provenance from the model.

## Selected architecture

### Execution, access, and origins

A supported JavaScript return value is a successful computation. The generic
execution result does not implement `EvidenceBearing`. It can have no sources.
Remove the post-execution evidence gate; never fabricate a source to admit a
computation. Actual factual snapshots and genuinely `EvidenceBearing` tool types
retain immutable source metadata and strict construction/normalization. This
boundary is automatic code behavior, not a model or player obligation.

Remove `run_javascript.roots` and the root-masking selection path. Expose all
already authorized detached roots through one lazy invocation view. Enumeration
and schema discovery do not resolve unrelated suppliers. Reading a declared but
uncaptured root returns an actionable unavailable error. Unknown ordinary object
properties retain JavaScript semantics. World and command bindings depend on the
frozen runtime capability, not a model declaration. The host graph and framework
capture path never move live Minecraft objects to workers. The explicit Java
interop mode of 033 is unchanged; code using live Minecraft state still needs
the correct owner thread. No script parser guesses dependencies or retries a
program elsewhere.

Tool placement is deterministic. A server model uses an advertised player-client
tool where available and a server tool otherwise. The old special case that used
`roots:["world"]` to reroute an entire program is deleted. Client observations
are honestly client-visible; explicit server tool execution remains authoritative.
This does not add cross-side world RPCs, client ghost writes, remote unrestricted
Java, or server command execution. Model location and tool location remain separate.

Origin information is auxiliary. A single immutable observation summary stores an
actual first capture and last capture time for a stable source identity.
Do not add a counter that confuses inherited workspace references with fresh
captures. It is not a second list of per-block copies. Distinct authority,
completeness, scope, provenance and versions remain distinguishable. No synthetic
capture claims or arbitrary observation caps are introduced. Plain computation
has an empty source list. Workspace reopening inherits only actual used origins.
Model results have no mandatory provenance footer; coverage/authority qualifications
appear where they affect interpretation, not as a success prerequisite. Model
result text can be a declared preview; preserving the exchange does not mean
persisting the canonical workspace. Workspace handles last for the current
request only and never resume across questions or process restart.

### Real context, not reversed presentation

The provider-neutral model transcript owns model context. UI events/history are
its display projection, never an input reconstruction recipe. Preserve genuine
ToolUse arguments, complete paired compact ToolResult text and its error bit,
assistant segments, and user messages. Do not retain provider-private reasoning,
credentials, HTTP bodies or live runtime objects in durable transcript records.
Use the existing redaction boundary for model arguments and outputs.

Keep original recorded exchanges separate from the active compacted context.
Compaction may summarize older complete units to fit the selected model's actual
budget, but does not overwrite original history, invent malformed errors, or use
a hardcoded knowledge-domain field whitelist. Summaries are not factual evidence.
Safe request failure retains completed exchanges and useful errors, including a
terminal request outcome when no tool was called. A parallel turn interrupted
after one result retains that actual result; active provider context never has an
unpaired use/result, while original diagnostic history may label an unfinished
call honestly. Cancellation never marks an unfinished call successful. Process loss
restores interrupted work and never replays a provider call or tool side effect.

Hydrate only a missing live session from the matching durable context. Do not
replace an existing same-connection session after every question. Skill plaintext
already present in real context is reused. Exact Skill document fingerprints and
loaded ranges distinguish unchanged text from a changed/deleted document. Reuse
receipts derive from actual post-compaction context, not a permanent loaded flag.
A dropped range can be loaded again; unrelated documents do not reload together.

Before formal 1.0, internal formats are Latest Only. History, model context,
checkpoints, common bridge packets and other atomically released structures carry
no internal version number. Delete migration/version-switch/alias paths rather
than freeze a new numbered format. The stable history file is
`config/openallay/history.sqlite3`. Current shape validation reports malformed
data without an implicit reset. Both loaders use the same current codec.
Independently released community Extension packages and their public core API
retain compatibility versions; game/loader/data-version facts remain factual
data. Production compatibility policy is a later decision based on real need. Session/actor/world partitions, deletion and
late-event generation checks remain intact.

### Debugging and export

Normal details show actual safe error code/message and useful result first.
Debug details put the submitted program and input/output before auxiliary origins.
Program/raw JSON remain Debug-only. Sources appear as grouped, initially collapsed
details with distinct-source counts and capture ranges, not a paragraph batch
per observation. No error
is hidden to make a request look successful.

Player-requested export includes useful recorded calls/results/errors in actual
order, not merely `Tool FAILED`. It uses original redacted records, independent
of the current model's compacted context. No provider reasoning/configuration is
exported. Existing exports remain unchanged.

### Builder performance and durability

Builder remains an independent Extension. Native terrain and structure scans
batch owner-thread work and retain existing selection/classification semantics.
Geometry, paste and undo batch native work rather than dispatch each block.
A cooperative quantum is a scheduling choice, never a total-volume limit.
A contiguous geometry plan keeps the last assignment to a repeated position;
intermediate duplicate states do not run hooks. Bed/door placement and explicit
connection repair can form deliberate plan barriers inside a preset. Do not
claim a whole preset is one atomic plan. Terrain-derived edits carry the block
image used for classification into existing native expected-before checks.
Flattening, vegetation removal and path preparation batch their reads as well
as their writes; optimizing only stand-alone scans is insufficient.

Current operation storage uses one base checkpoint and strictly sequenced atomic
delta records. Persist and force intent before world mutation; persist actual
readback before continuing to the next quantum. Publish a complete checkpoint
atomically before deleting covered deltas. Covered sequences can be ignored after
that publication; missing/corrupt uncovered sequences fail explicitly. No restart
replay, implicit rollback, or weakened conflict checks are added. There is no internal journal or template format version and no old-format
reader. Malformed current records report their actual format error; files are
not silently rewritten or deleted.
All IO stays off Minecraft owners. No Rhino callbacks execute on an owner thread.

## States, ownership, and terminal behavior

- Captured request -> lazy invocation -> computation success/failure -> scope closed.
- Origin collection observes successful accesses; it never authorizes execution.
- Missing live context -> durable hydration -> active session; later asks reuse it.
- Safe completed model exchange -> original record; projected context -> checkpoint.
- Context budget pressure -> summarize complete older units -> new active context.
- Request failure retains completed exchanges. Explicit Stop marks the visible
  request terminal and revokes its signal immediately; arbitrary cancellation
  listeners and physical cleanup run off the Minecraft owner and outside the
  session monitor. A replacement request can acquire a new identity immediately.
- Ordinary events from a terminated request are suppressed. One explicit final
  context handoff may archive that cancelled request's completed original
  exchanges under the same live connection/session/request generation. It may
  replace active context only when no newer request has been submitted. It must
  never overwrite a successor. Deletion and disconnect reject that handoff too.
- Builder intent durable -> owner apply/readback -> outcome durable -> next quantum.
- Interrupted Builder operation -> inspected journal, never automatic mutation.

GuideService remains connection/session state owner. AgentSessionStore owns active
provider-neutral messages under that lifetime. The ordered history repository owns
durable writes. Origin metadata cannot grant capability or turn partial data into
complete data. No additional provider calls are made to prove deterministic work.

## Supersedes and verification

This decision supersedes root selection and mandatory generic-JavaScript evidence
in 025/026/028/040, the display-only tool projection used for model restoration in
018/027/028, and the no-evidence execution rejection retained by 034. It supersedes the numbered internal format policy in 032 for the current
pre-1.0 development phase, not the protection against accidental file deletion.
It replaces 034's journal representation, not its thread, conflict and partial
write semantics. 033 authorization and 035 advisory requirements remain unchanged.

Required regressions: pure computations and enabled Java IO without fake reads;
lazy all-root access; precise errors in immediate and next-request context;
two-question Skill reuse; changed text/compaction/restart/session isolation;
original error export after compaction; no reasoning/credential persistence;
source aggregation without lost authority; exact 49x49 terrain results with
batch dispatch; construction/copy/undo dispatch and journal crash cuts; common
suite, both Extension loaders, both core loaders, and pinned package checks.
Real-client timing requires retained measurements and is not implied by unit tests.
