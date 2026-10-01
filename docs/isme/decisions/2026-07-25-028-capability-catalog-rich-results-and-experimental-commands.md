# SKMB-2026-07-25-028: Capability Catalog, Typed Rhino Results, and Experimental Commands

> Subsequent decision: [041](2026-10-01-041-execution-context-simplification.md)
> authorizes a breaking simplification of mandatory origins for generic JavaScript, roots declarations, and display-only model-context restoration.
> Its implementation/verification is tracked separately; this document retains
> the historical decision and is not proof that the new behavior is delivered.

- Status: accepted
- Decided by: designer
- Approval source: >-
    The designer approved the capability-first design and explicitly required
    the experimental command surface to include the complete active Minecraft
    command registry, including commands registered by mods, with no
    OpenAllay-specific command allowlist, argument restriction, or call limit.
- Date: 2026-07-25
- Commit: b9f0d84
- Scope: Rhino host graph, schema discovery, extension settings, result
  presentation, Skills, and experimental command execution
- Patterns: B, C, E, F, G
- Supersedes: >-
    The blanket command-mutation prohibition in SKMB-2026-07-24-025 only when
    the experimental command capability is explicitly enabled. The normal
    detached analytical surface remains unchanged when it is disabled.

## Decision basis

```yaml
decision_basis:
  decision_id: SKMB-2026-07-25-028
  trigger: >-
    Manual real-client use showed captured data that was not mounted in Rhino,
    documentation that disagreed with the host graph, repeated schema-probing
    calls, generic Tool result cards that hid useful output, and model-authored
    Rich UI that was rarely emitted. The designer also requested an opt-in
    complete Minecraft command registry and execution object inside JavaScript.
  authority:
    - designer approval of the capability catalog and typed-result design
    - designer instruction to prioritize connecting complete capability before governance
    - designer instruction that experimental commands expose the full active registry
    - designer instruction that mod-registered commands are included
    - designer instruction that OpenAllay adds no command limits or allowlist
  selected_behavior:
    schema_source: one Java-side descriptor catalog derived from the closed Rhino host types
    settings_surface: rename the player-facing Tools section to Extensions
    result_presentation: canonical JSON plus trusted semantic type sidecar
    command_default: disabled
    command_visibility: absent from bindings, schema, and matching Skill when disabled
    command_registry: active dispatcher tree visible to the current player, including mod commands
    command_execution: >-
      submit the exact command as the requesting player on the owning Minecraft
      thread, then return its observed client-visible feedback
    command_policy: no OpenAllay command allowlist, argument restriction, or call-count limit
    request_capture: settings changes affect future requests only
    rollback: submitted commands are never rolled back by later script failure or cancellation
  retained_boundaries:
    - Minecraft's own command parsing and player permission checks remain authoritative
    - arbitrary Java class access, reflection, filesystem, network, process, and shell remain unavailable
    - live Minecraft objects never enter the Rhino worker thread
    - server-hosted requests remain correlated to and executed for the initiating player
```

## Capability and schema catalog

Every Rhino root is represented by a Java-owned descriptor containing its
stable name, generic type, availability, source/provider, summary, and evidence
ownership. Schema generation reuses the KubeJS Rhino `TypeInfo` model and the
same closed type algebra accepted by `RhinoHostAdapter`: scalars, enums,
temporals, optionals, records, collections, string-keyed maps, and Gson values.

The descriptor catalog is the single source for:

- the roots and fields exposed in Rhino;
- progressive `schema.list()` and `schema.describe(...)` discovery;
- the Extensions settings page;
- generated Skill references and stable examples;
- validation of extension-provided detached values.

Open maps and Gson trees remain explicitly dynamic. The catalog describes their
stable container path without inventing mod-specific child fields.

The host graph exposes all captured request data that satisfies the closed
adapter contract. In particular, recipe provider/group/diagnostic metadata,
knowledge catalog metadata, registry-wide search data, extension descriptors,
and stable evidence are not discarded behind abbreviated count-only records.

## Evidence collection and attribution

A successful `run_javascript` result reports evidence gathered for that execution,
not every snapshot present in its request context. A detached data root contributes
its evidence only when the script dereferences that root. Merely selecting a root,
enumerating root names, or discovering schemas does not claim that the root data
was used. Successful `schema.list()` and `schema.describe(...)` calls carry the
separate evidence record for the declared host catalog.

The evidence list is scoped to one script execution. Opening a prior result from
`workspace.open(handle)` also carries the evidence retained with that handle.
`world` observations carry the evidence returned by each completed observation.
The optional `commands` bridge records evidence for an observed command catalog
lookup or the feedback window; it does not claim a command's world effect was
verified. A later world/data observation is required to verify such effects.

This is root- or operation-level lineage, not field-level dataflow or proof that
every returned claim depends on every listed source. A composite root such as
`mc.game` can expose several source records. OpenAllay does not provide a separate
`mc.evidence` root; evidence is attached to the normalized Tool result so it cannot
accumulate across earlier calls or be reported before the corresponding access.
A factual success with no data or metadata source still fails closed.

## Typed results and player presentation

Canonical Gson remains the only factual Tool-result representation. During
normalization, trusted host wrappers contribute a Java-side semantic type
sidecar. The sidecar is not visible to scripts and does not alter canonical
JSON or model-facing factual fields.

The presentation registry resolves results in this order:

1. trusted exact recipes;
2. trusted registry items;
3. homogeneous derived tables;
4. scalar or key/value data;
5. generic bounded preview.

Field-name guessing alone cannot turn a model-created object into a trusted
recipe or item. Recipe and item views retain same-request evidence and stable
references. Classification happens from the complete canonical result before
the model preview is bounded.

Normal Tool detail shows a closed, useful projection of the invocation plus
the typed output. Live Debug additionally shows the complete submitted
JavaScript and the bounded normalized Tool envelope. Durable history never
stores canonical workspace values or raw invocation arguments; it may store a
closed normal invocation projection such as roots, opaque handles, and module
IDs. Restored Debug explicitly reports that raw live diagnostics were not
retained.

## Experimental command capability

The setting is disabled by default and captured when a request starts. When
disabled, the command object, command schema/catalog, and command Skill are
absent. Enabling or disabling the setting does not mutate an active request.

When enabled, Rhino receives:

```javascript
commands.list()
commands.describe(path)
commands.run(command)
```

`run_javascript` accepts `commands` in its `roots` input as the explicit
selection for this top-level binding. It is removed before selecting `mc`
dataset roots; `mc.commands` does not exist. Selecting `commands` while the
request capability is disabled fails `javascript_root_unavailable`.

`list` and `describe` are projections of the current command dispatcher visible
to the requesting player. They include vanilla, loader, server, and
mod-registered commands that exist in that dispatcher. The catalog exposes
literal paths, argument nodes/types, executable state, redirects, and usage
text without exposing Brigadier or Minecraft live objects.

`run` accepts the exact command text, normalizes only an optional leading slash,
and submits it through the same player command route used by Minecraft. It then
waits on the Rhino worker for that player's ordered non-overlay game-message
feedback window and returns the observed lines. It adds
no OpenAllay-specific command allowlist, argument filter, mutation category, or
call-count limit. Minecraft parsing, connection state, and player permission
remain the authority.

Command calls are ordered within one JavaScript execution and serialized per
player across sessions so feedback cannot be assigned to two concurrent calls.
Worker-side Rhino code never touches a live dispatcher or connection:
list/describe use a detached request snapshot, while run marshals one immutable
string to the owning client thread. The worker waits until messages become
quiet or the feedback deadline expires. A result reports `feedback` with
ordered messages or `no_feedback`; it never fabricates a universal success bit
that Minecraft's protocol does not provide.

Command submission is an irreversible boundary. If a later line throws, the
script times out, or the Agent is cancelled, commands already submitted remain
submitted. Commands not yet marshalled after cancellation are not submitted.

## Failure semantics

- An unavailable requested root fails `javascript_root_unavailable`.
- An extension whose declared type cannot be represented fails only that
  extension descriptor and publishes a diagnostic.
- Unknown dynamic child fields remain discoverable from representative runtime
  values; they are not fabricated in the declared schema.
- A malformed trusted semantic result degrades deterministically to a table or
  generic preview; it never crashes the screen.
- A disabled command capability makes `commands` absent, rather than returning
  a fake empty registry.
- A disconnected client fails command submission
  `command_connection_unavailable`.
- A command rejected before submission fails `command_submission_failed`.
- A command accepted for submission returns its sequence, submitted text,
  observed messages, duration, and `feedback`/`no_feedback` state.
- Unrelated non-overlay game messages arriving during the active feedback
  window may be included because vanilla command feedback has no correlation
  identifier. Per-player serialization prevents overlap between OpenAllay
  command calls but cannot alter the Minecraft protocol.
- Partial command side effects are never rolled back.

## Required evidence

1. recipe providers/groups/diagnostics, stable game paths, knowledge metadata,
   registry-wide data, extension descriptors, and evidence round-trip through
   the host graph;
2. declared schemas are generated from the same accepted types as the host
   adapter and do not resolve large roots;
3. simple installed-mod lookup needs no probing Skill, while unfamiliar
   mod-added fields need at most one focused schema/sample call;
4. direct recipe and item host results select native views; derived homogeneous
   objects select tables; forged IDs cannot acquire trusted native views;
5. normal Tool details show useful input/output and Debug shows live source
   without eager full-document layout;
6. disabled command settings expose no command object or Skill;
7. enabled command discovery includes fixture mod commands and argument nodes;
8. command execution is ordered, player-scoped, marshalled to the owning thread,
   permission-preserving, and explicitly non-transactional;
9. Fabric and NeoForge remain in parity and both artifacts build.
