# SKMB-2026-07-24-025: Rhino Agent Runtime, Request Workspace, and Managed Skills

- Status: accepted; implemented and verified
- Date: 2026-07-24
- Scope: post-0.1.0 Agent tool architecture
- Patterns: A, B, C, D, E, F, G

## Decision basis

```yaml
decision_basis:
  decision_id: SKMB-2026-07-24-025
  trigger: >-
    Real long-running provider sessions showed that a growing collection of
    domain Tools forced repeated narrow calls, inflated context with structured
    payloads, and could not express cross-domain ranking and transformation
    naturally. The attempted Resource VFS architecture was explicitly rejected
    in favor of a general embedded JavaScript analysis runtime.
  owner: GameGuideAgent, Rhino runtime, request workspace, Skill repository
  lifecycle: one captured Agent request, plus explicitly managed local Skill files
  concurrency_scope: one isolated Rhino Context and workspace per request correlation ID
  selected_defaults:
    engine: KubeJS-Mods Rhino fork embedded as a shaded runtime dependency
    isolation: one Context and one detached immutable data graph per execution
    standard_library: Rhino safe standard objects plus OpenAllay-owned bindings
    model_tools:
      - openallay:run_javascript
      - openallay:load_skill
      - openallay:manage_skill
      - openallay:calculate_craftability
    large_results: retain canonical JSON in a request workspace and return a handle, schema, cardinality, and bounded preview
    continuation: later scripts reopen prior results by opaque handle and transform them without copying the full result into model context
    time_budget: two seconds of wall-clock execution, checked by Rhino instruction observation and cancellation
    script_budget: 65536 source characters; result depth 64; 250000 nodes and array elements; 16384 object fields; 524288 characters per string
    workspace_budget: 16 results and 32 MiB estimated canonical units per request; four handles and 8 MiB selected per execution
    skill_read_budget: 8192 characters per chunk with snapshot-bound opaque continuation cursors
    preview_budget: six rows, sixteen fields per object, five nested levels, and bounded scalar/model text
    skill_writes: local managed directory only, strict Agent Skills subset, atomic replace, explicit create/update/delete operation
    extension_bridge: code-registered read-only adapters contribute detached values and helper modules
  authority:
    - user explicitly rejected the VFS architecture and requested embedded JavaScript
    - user explicitly selected KubeJS Rhino as the implementation reference
    - user delegated detailed architecture and implementation decisions
    - user explicitly requested Agent-created, updated, and deleted Skills
  forbidden:
    - arbitrary Java class loading, Packages, JavaAdapter, reflection, ClassLoader access, or unrestricted host-object wrapping
    - network access, process execution, shell syntax, real filesystem access, arbitrary paths, or native library loading
    - live Minecraft objects on model or script worker threads
    - world, inventory, configuration, command, or registry mutation
    - sharing a mutable Rhino scope between requests or sessions
    - treating a workspace summary or preview as complete factual evidence
```

## Context and supersession

This decision supersedes the “scripts are unrepresentable” part of
SKMB-2026-07-19-024/I80. It does not weaken the immutable snapshot, evidence,
thread-ownership, authority, or fail-closed requirements from earlier
decisions. JavaScript is a controlled analysis language over already captured
data, not a new authority source.

The implementation follows the useful seams in KubeJS and its Rhino fork:

- a dedicated `ContextFactory`/`Context` rather than a global evaluator;
- explicit bindings and type conversion rather than exposing the JVM;
- safe standard objects and sealed host capabilities;
- instruction observation for cancellation and execution limits;
- class visibility and reflection denial at the runtime boundary.

OpenAllay does not depend on KubeJS itself. It embeds the
[`KubeJS-Mods/Rhino`](https://github.com/KubeJS-Mods/Rhino) fork and owns a
smaller, read-only binding layer suitable for model-authored programs.

## Selected behavior

### Runtime ownership and execution

`run_javascript` receives source text and executes it on a virtual worker
thread. It creates a fresh Rhino Context and scope for that invocation. The
scope contains safe ECMAScript built-ins and immutable OpenAllay bindings only.
The final expression or explicit `return` value becomes the canonical result.

The instruction observer checks both the Agent cancellation signal and a
two-second wall-clock deadline. Cancellation wins over timeout. A timed-out or
cancelled execution publishes no successful workspace value and the Context is
discarded.

Scripts cannot retain Java objects or Rhino scopes. Any accepted result is
normalized immediately into the repository Gson `JsonElement` tree. Unsupported
values, cycles, functions, promises, symbols, host objects, and non-finite
numbers fail explicitly.

### Injected Minecraft data

The `mc` binding is assembled from the request's detached
`ToolInvocationContext`. It includes every captured category that can be
represented without crossing authority or thread boundaries:

- caller and evidence metadata;
- player identity, inventory, and client-visible state;
- registry entries and their codec-derived properties/components;
- recipes, ingredients, outputs, processing metadata, and provenance;
- observable settings, packs, mods, diagnostics, position, and query results;
- loaded knowledge metadata where it is part of the captured request.

Missing categories are absent and listed in `mc.capabilities`; they are never
fabricated as empty authoritative datasets. Stable IDs and evidence fields are
preserved through transformations.

`run_javascript.roots` selects the smallest top-level host views required by
the program. Registry rows appear once under `mc.items`, `mc.blocks`, and the
other kind-specific views; `mc.registries` carries only catalog metadata.
Likewise, recipe rows appear once under `mc.recipes`, while
`mc.recipeCatalog` carries metadata. Omitting `roots` is reserved for bounded
schema discovery.

The ergonomic surface is JavaScript-native. Arrays support `filter`, `map`,
`reduce`, `sort`, `flatMap`, grouping helpers, and user-defined functions. The
runtime also exposes small deterministic helper modules for common operations,
but those helpers are ordinary JS/library functions rather than additional
model Tool IDs.

### Request workspace and model projection

Every successful result is stored as canonical JSON in a workspace owned by the
request correlation ID. Handles are opaque and cannot be constructed from
paths. A later `run_javascript` call in the same request may use
`workspace.open(handle)` to transform the prior value.

The model-facing result is a projection, not the canonical JSON:

- scalar and compact values are rendered directly;
- collections report type, cardinality, discovered field paths, and a concise
  preview;
- larger values return the same metadata plus a workspace handle;
- evidence/provenance required for factual use remains present in the
  projection;
- omission is explicit, with instructions to reopen, filter, aggregate, or
  project the stored result.

The workspace is closed on request completion, failure, cancellation,
disconnect, or shutdown. Handles from another request or a closed workspace
fail `workspace_handle_unavailable`.

Canonical results are admitted atomically only after resource accounting. An
oversized result, an over-full workspace, or an oversized handle selection
fails without creating a partial handle. Player UI and model text are both
derived from the same bounded structured preview; debug UI never renders the
raw normalized JSON tree.

### Extension adapters

Community or mod integrations capture public API values on the owning
Minecraft thread and place immutable records in `ToolInvocationContext`.
`JavascriptDataModule` is then a pure worker-side projector over that already
detached request context. It must not call live mod APIs or use reflection.
The projected values declare authority and evidence and mount under a stable
`mc.extensions.<namespace>` key.

Shared pure helpers are audited OpenAllay runtime functions. Extension-specific
analysis operates over the detached values contributed under
`mc.extensions`; extension code does not inject executable host functions.
Ordinary Agent scripts never receive `Class`, `Method`, `Field`,
`AccessibleObject`, `ClassLoader`, a Minecraft live object, or a general Java
bridge.

## Verification outcome

Deterministic runtime, sandbox, workspace, extension, model-projection, bridge,
managed-Skill, and three analytical acceptance suites pass. The common suite
and both loader builds pass with Rhino packaged in each artifact.

An opt-in real OpenAI-compatible `deepseek-v4-flash` run loaded
`analyze-game-data`, loaded its matching examples reference, and solved the
highest-damage sword and minimum-material container tasks without per-row Tool
calls. The redacted retained report is
`docs/verification/rhino-agent-runtime/live-javascript-agent.md`.

### Managed Skills

Bundled Skills remain immutable. `manage_skill` may create, update, or delete
only a local package below the OpenAllay managed Skills root. Names and relative
reference paths use the existing strict Agent Skills subset.

Create/update stages a complete package, parses every file, validates metadata,
references, and allowed Tool dependencies, then atomically replaces the target
directory. Delete resolves one exact managed Skill name and removes only that
package. A failed validation or write leaves the previously active Skill
unchanged. The active request retains its captured Skill catalog; changes apply
to later requests.

`load_skill` never injects a whole large document. It returns an 8192-character
chunk plus an opaque cursor bound to the Skill name, exact document, and
content fingerprint. Continuing with a mismatched or stale cursor fails closed.

## Failure semantics

- Syntax or runtime errors return `javascript_error` with a bounded,
  credential-free location summary.
- Cancellation returns the existing Agent cancellation outcome and stores no
  result.
- The instruction deadline returns `javascript_timeout`.
- Unsupported/cyclic/non-finite output returns `javascript_result_invalid`.
- Oversized source or result graphs return `javascript_source_too_large` or
  `javascript_result_budget_exceeded` and publish no handle.
- Workspace admission and selection failures return
  `workspace_result_too_large`, `workspace_budget_exceeded`, or
  `workspace_selection_too_large`.
- A missing or foreign result handle returns `workspace_handle_unavailable`.
- Invalid, mismatched, or stale Skill cursors return `skill_cursor_invalid`.
- Missing captured data remains explicit in capabilities and cannot be
  converted into a successful empty fact.
- Adapter failure marks only that extension dataset unavailable and preserves
  all independent datasets.
- Managed Skill validation fails `skill_invalid`; confinement or atomic write
  failures return `skill_write_failed`; the prior active package remains.

## Required evidence

Deterministic coverage must prove:

- safe standard JavaScript data transforms;
- denial of Java/class/reflection/filesystem/network/process access;
- cancellation, timeout, and scope disposal;
- canonical JSON normalization and cyclic/unsupported rejection;
- handle isolation, reuse, and request cleanup;
- compact scalar output and large-result projection without full context copy;
- generic discovery of mod-added nested fields;
- atomic Skill create/update/delete with rollback and future-request visibility;
- unchanged Fabric/NeoForge common-core boundaries.
- provider input is re-estimated before every model dispatch after Tool
  results; an uncompactable current request fails locally rather than relying
  on an endpoint HTTP 400.

The acceptance scenarios are:

1. find the highest-damage sword with one analysis execution;
2. identify the strongest poison-causing obtainable item and trace its
   production path using component/effect and recipe data;
3. find the craftable container recipe requiring the fewest materials.
