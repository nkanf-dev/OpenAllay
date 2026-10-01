# Budgeted Context and Scoped Extension Authority Implementation Plan

> **For agentic workers:** Use isolated, file-owned worktrees. Root reviews and serializes Gradle and graphical verification. Preserve the validated Builder algorithms. Do not read credentials or rewrite player history/worlds.

**Goal:** Fix unbounded model-result transport and unsummarizable current exchanges, replace byte-based token estimates with a maintained tokenizer, repair the actual Rhino schema helper, and decouple trusted native Extension calls from Agent JVM authority.

**Architecture:** Full computation data stays in a request-owned external result environment. A permission-independent, model-budgeted projection carries useful metadata and selected data. One estimator/admission owner counts actual provider-bound requests using a published tokenizer, and compaction preserves complete structural exchanges and original records. Trusted Extensions expose explicit invocation-scoped JSON method bindings through the existing Rhino module system; native action grants are per Extension and independent of unrestricted Java. Prompts stay minimal and generic.

**Tech Stack:** Java 25, existing Rhino/Gson/JDK HTTP/SQLite, a published offline BPE tokenizer component, Gradle wrapper, JUnit. No new handwritten tokenizer, parser-based task routing, or model-specific workaround.

## Confirmed evidence

- The new session-2 result has about 1.6 MB of model-visible text. Its normalized preview is complete and contains no omitted rows/fields.
- `presentUnrestricted` bypasses model-view projection. Unrestricted execution authority is not a reason to bypass provider context admission.
- First request has no older history and protects all current exchanges, leaving no summary prefix. The next request can summarize a small historical prefix yet retain the unsendable large result.
- Skill text and system manifest are tens of KB, not the cause of the 1.6 MB result. Do not hardcode any Extension/task/model names into system guidance.
- The actual submitted schema calls contain no `samples` declaration. The failure is in a recursive callback helper in the embedded runtime, not user variable naming or stale scope.
- Builder calls currently require Agent Java interop only because its module uses `Java.type` and its participant gates on unrestricted. The trusted native implementation already owns the game/thread/session checks.

## Independent implementation

- [ ] **Tokenizer:** Use a published Java BPE library and its verified resource tables. Count content with that component and count the actual protocol representation. Expose encoder selection/source/mode; do not claim an unknown gateway or Anthropic tokenizer is exact. No arbitrary percentage margin or bytes/4 conversion.
- [ ] **Result environment:** Preserve canonical data and typed shape in the request workspace. Use one projection path for safe and unrestricted execution. Model output carries real handle lifetime, cardinality/schema/size and bounded selected data. Never invent a handle for restored old strings.
- [ ] **Admission:** Budget candidate complete ToolUse/ToolResult groups against the actual final system prompt, manifest, tools and tokenizer. Split result shares without confusing encoded byte units with token units. Keep the actual first delivered projection as the original exchange; older active context can be reduced without rewriting original history.
- [ ] **Compaction:** Budget both summary inputs and requested summary output. Preserve current user goals and complete pairs. Use a real structural candidate that can fit its suffix, not an arbitrary maximal prefix followed by an over-budget projection. No no-progress/infinite retry loop or silent full-history deletion.
- [ ] **Schema helper:** Repair the recursive callback implementation while preserving dynamic heterogeneous schema behavior. Keep the runtime/parser and user program untouched. Test actual nested world-like data, field order, sparse arrays, cycles/depth and both reported source forms.
- [ ] **Scoped native bindings:** Add an independently versioned public Extension API with declared JSON method contracts implemented through existing Rhino functions. Expose no Java wrappers/classes/live host objects or JavaScript callbacks. Tie bindings to invocation cancellation and trusted native owner scheduling.
- [ ] **Authorization:** Native world-action grants are per Extension, default off, captured for the request and separate from Agent unrestricted Java. Read capabilities do not imply write grants. Installation does not grant authority; server-origin client callbacks do not inherit local native action grants.
- [ ] **Builder:** Replace `Java.type` open with a scoped public facade. Preserve all batching, section proofs, journal durability, identity/chunk checks and undo/conflict behavior. Builder operation can run with Agent Java absent.
- [ ] **Guidance:** State only generic instructions and current result/Skill contracts. Extension examples live in that Extension's Skill and analyse before returning concise task-relevant data. No Extension names, model names, scene sizes, hard routing or prohibitions in the system prompt.

## Independent whole-project review

- [ ] Context/Skills/projection/retrieval: identify crude limits, duplicate representations and custom infrastructure that has a mature replacement.
- [ ] Runtime/Extension/world/threading: identify permission propagation, unsafe exposure, scheduling/late-event races and unbounded capture costs.
- [ ] Persistence/config/install/packets: inspect atomicity, failed-write ownership, crash windows and true complexity without adding Latest Only migrations.
- [ ] Provider/HTTP/SSE/UI: inspect framing, deadline/cancel behavior and capability/output/reserve semantics. Existing JDK/Gson/SQLite reuse is evidence against gratuitous replacement.

## Verification and delivery

- [ ] Reproduce the large-result and helper faults through synthetic, same-shape fixtures. Do not track real user world data or secret-bearing trace bodies.
- [ ] Focused native-project tests, then common full suite and both loaders, Extension common/loaders/package checks, exact pinned distribution and SQLite/script gates.
- [ ] Record remaining unknown tokenizer/model limits honestly. No live provider calls without explicit authority.
- [ ] Commit and push coherent batches, not one aggregate commit. Pin the committed independent Extension before the default distribution gate. No release/tag publication is implied.
- [ ] Start only one test client after verification if requested; preserve existing model settings, history, journals, exports and worlds. No automatic reset.
