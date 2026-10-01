# SKMB-2026-07-24-027: JavaScript Modules, Complete Live E2E Traces, and Silent History Saves

> Subsequent decision: [041](2026-10-01-041-execution-context-simplification.md)
> authorizes a breaking simplification of display-only tool persistence where it replaced actual model context.
> Its implementation/verification is tracked separately; this document retains
> the historical decision and is not proof that the new behavior is delivered.

- Status: accepted
- Date: 2026-07-24
- Scope: Rhino module loading, craftability exposure, real-client trace retention,
  durable-history presentation, and VFS branch disposition
- Patterns: B, C, E, F, G
- Supersedes: the model-facing `openallay:calculate_craftability` Tool requirement
  in prior repository guidance

## Decision basis

```yaml
decision_basis:
  decision_id: SKMB-2026-07-24-027
  trigger: >-
    The designer selected direct Rhino host objects as the unified analytical
    surface, rejected the earlier Resource VFS implementation, identified
    calculate_craftability as a redundant model Tool, requested reusable
    Skill-plus-JavaScript "prepared dishes", requested continued real-client
    E2E with complete traces, and required successful history saves to stop
    moving the transcript.
  authority:
    - designer instruction to archive and discard the Resource VFS implementation
    - designer instruction that craftability belongs in reusable JS modules/Skills
    - designer instruction to run harder real-client E2E and retain complete traces
    - designer instruction that successful history saving stays invisible
  selected_behavior:
    analytical_surface: one run_javascript Tool over direct detached Java snapshots
    modules: closed bundled CommonJS-style catalog resolved by exact module ID
    craftability: bundled openallay:crafting module documented by Skills
    history_ui: loading and failure are visible; successful pending writes are silent
    debug_tool_detail: >-
      retain exact current-request invocation arguments in memory and render
      JavaScript source plus execution metadata only when debug mode is enabled
    e2e_trace: full decoded Agent inference trace, untruncated and credential-redacted
    vfs_history: archive the old implementation on deprecated/resource-vfs-v1
    main_line: Rhino branch becomes authoritative without Resource VFS registration
  forbidden:
    - arbitrary module paths, filesystem loading, network imports, or Java class access
    - exposing calculate_craftability as a second model Tool
    - using successful pending history writes as transcript rows
    - persisting model-authored Tool arguments in durable conversation history
    - persisting authorization headers, API keys, cookies, or raw provider secrets
    - deleting the VFS implementation before retaining a remote archival branch
```

## JavaScript modules

`require(id)` resolves only exact IDs in an OpenAllay-owned bundled catalog.
Each Rhino execution owns a fresh module cache. A module executes inside the
same denied-host Rhino scope as the model program and receives no filesystem,
network, Java, reflection, Minecraft live object, or mutation authority.

The first bundled module is `openallay:crafting`. It provides reusable pure
functions for recipe cost and deterministic non-recursive inventory allocation.
The model calls it inside the same `run_javascript` program that filters,
joins, ranks, and formats the requested result. Skills teach the stable module
contract and examples through progressive disclosure.

Module identifiers used by an execution are returned in the canonical Tool
result and therefore appear in the live trace. Bundled module sources remain
normal versioned repository resources; the trace does not duplicate source
text on every call.

## Tool catalog

`openallay:calculate_craftability` is not registered or advertised. The
canonical Java calculator may remain as an implementation reference or test
oracle, but the Agent receives one analytical surface: `run_javascript`.

This decision replaces the prior invariant that required the separate Tool.
The invariant that model arithmetic must not decide globally overlapping
ingredient allocation is preserved by the reviewed bundled module algorithm
and parity tests against the canonical Java calculator.

## Real-client E2E trace

The opt-in real-client harness may use an explicitly supplied existing client
profile instead of replacing it with the deterministic fixture. The harness
never prints or copies credential values into reports.

For each terminal local-model request, the harness retains:

- the request identity and user message;
- every outbound decoded `ModelRequest`, including the system prompt, message
  chronology, Tool definitions, and streaming flag;
- every decoded `ModelTurn`;
- every Tool call with exact arguments;
- every canonical Tool result;
- state transitions, final text, and terminal failure code.

This is the complete provider-neutral Agent trace, not raw HTTP traffic.
Authorization headers and provider transport bodies remain outside the trace.
Encoding is pretty-printed, untruncated, and redacts every configured secret.

The terminal E2E report waits until the request's trace has been recorded.
Missing trace publication fails the harness rather than claiming acceptance.

## Durable-history presentation

`SAVING` remains a real persistence state and continues to control diagnostics,
shutdown flushes, deletion interlocks, and failure transitions. It no longer
creates a `GuideUiRow.Persistence` row. Successful writes therefore cannot
change transcript height or move the viewport.

`LOADING` remains visible because submission is unavailable. `UNAVAILABLE`
remains visible with its stable failure because the player must know that the
conversation is not durable. Debug diagnostics may still show pending-write
counts.

## Debug Tool detail

`ToolStarted` carries an immutable copy of the exact decoded invocation
arguments into the live `GuideToolActivity`. They are current-request
diagnostic state only: durable-history projection and codec output omit them.

For `openallay:run_javascript`, debug detail renders the complete submitted
`source` as a code section, followed by selected `roots`, explicit `handles`,
loaded module IDs, result handle/type/cardinality/completeness, omissions, and
elapsed time. Ordinary player detail continues to show only the bounded,
friendly data preview. Source lines retain their order and are drawn only when
visible inside the existing scissored, scrollable detail panel.

The preview has no fixed row count. Canonical JSON stays complete in the
request workspace; one conservative 8192-token UTF-8 budget governs the entire
model-facing CLI-like projection, including metadata, preview, continuation
guidance, and evidence. Values are admitted until that budget is exhausted.
Omission remains explicit and names the same opaque handle for a later focused
JavaScript transformation.

## VFS disposition and main-line transition

The exact old `main` tree containing the Resource VFS is retained on the remote
branch `deprecated/resource-vfs-v1`. The new main line is based on the direct
Rhino branch. It does not merge or re-register Resource VFS Tools.

The dirty local `main` worktree is not reset, cleaned, or overwritten. Final
main publication uses a clean integration ref after tests and real-client E2E
have completed.

## Failure semantics

- Unknown or invalid module IDs fail `javascript_module_unavailable`.
- A module cycle or evaluation failure fails `javascript_module_error`, stores
  no successful workspace result, and publishes no partial output.
- A complete local Agent trace that is not available to the E2E harness causes
  `trace_unavailable`; the harness does not emit a successful report.
- Trace persistence or redaction failure emits no final trace file and never
  falls back to unredacted output.
- History write failure still transitions to `persistence_unavailable` and is
  player-visible; only successful pending work is silent.

## Required evidence

1. exact bundled module resolution, per-execution caching, unknown-module and
   cycle failures, and host-access denial;
2. crafting module parity with the deterministic Java calculator for
   overlapping alternatives, catalysts, missing requirements, and maximum
   crafts;
3. the separate craftability Tool is absent from registration, prompt, Skills,
   and advertised settings;
4. `SAVING` produces no transcript row while `LOADING` and `UNAVAILABLE` do;
5. a real Fabric client uses the existing DeepSeek profile for multiple
   non-trivial tasks and retains full redacted traces;
6. both loader builds and focused deterministic tests pass;
7. the VFS archival branch exists remotely before the new main line is
   published.
8. normal Tool detail excludes invocation arguments, while debug detail exposes
   the exact current-request JavaScript source and never restores it from
   durable history.
9. small arrays larger than twelve rows remain complete, while genuinely large
   projections stay within the total model-text token estimate and preserve a
   reopenable handle.
