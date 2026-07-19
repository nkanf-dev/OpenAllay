# SKMB-2026-07-19-021: Model Ownership and Player Tool Bridge

- status: accepted
- decided_by: designer
- approval_source: the designer required that client settings configure only client-owned models, server models remain server-configured and appear on clients only as synchronized read-only choices, model IDs can be fetched with the current or saved API key, and a server-hosted Agent can invoke the requesting player's client read-only tools instead of terminating on an unavailable local execution path
- date: 2026-07-19
- commit: pending
- patterns:
  - A_async_wait
  - B_state_persistence
  - C_concurrent_operations
  - D_external_dependency
  - E_security_boundary
  - F_fail_semantics
- scope: model-origin presentation, model catalog listing, tool-name compatibility, and request-scoped client Tool execution for server-hosted Agents

## Context

The final Phase 4 walkthrough exposed three related boundary failures. A
durable server-model preference could reappear on a later connection even
though the advertised server model and payer are connection-scoped. The native
model editor did not provide a generic authenticated model-list operation or a
clear in-field indication that a local credential already existed. Finally, a
model returning the registered canonical Tool ID instead of its schema-safe
alias caused an executor exception. A server-hosted Agent also had no reverse
bridge to the requesting player's client-observable settings, packs, and F3
state.

## Decision

Client settings own only local client model profiles and the client's local
credential store. A server model is configured only by `server-model.json` and
is projected to a connected client as a read-only, server-provided Guide model
choice. New sessions and recovered sessions at a new connection begin with the
configured default client profile. A server-model preference is connection
scoped and must be selected again after reconnect; it is never silently
restored against a different server capability.

The Models page performs a configuration-layer, cancellable `GET` against the
profile base URL's `models` resource. OpenAI-compatible profiles use Bearer
authentication and Anthropic profiles use the provider's API-key headers. The
unsaved password-field value is used only for that request when present;
otherwise the saved `credentialRef` is resolved. The response contributes only
validated model IDs to an ephemeral searchable picker. The model field remains
free-form. Credentials, headers, response bodies, and endpoint details never
enter settings snapshots, notices, history, traces, or logs. A saved-key hint
is based on actual credential-store presence rather than the shape of a
reference.

For a server-hosted Agent request, the client sends only the IDs of currently
enabled, registered read-only client Tools. The server intersects those IDs
with its own trusted common Tool registry and derives descriptions and schemas
locally. It freezes the resulting player-scoped capability set for the
request. A selected client Tool invocation is correlated by authenticated
actor, request ID, and invocation ID, sent to that same client, checked again
against the frozen client capability snapshot, captured and executed on the
client's owning thread, normalized, chunked, and returned to the same server
request. No capability is globalized or reusable by another player.

One logical Tool ID is exposed once. Placement policy is code-owned. The
player-observable `inspect_game_state` Tool is client-first for client options,
mods, packs, shaders, HUD/F3, and player-visible state, while explicitly
server-authoritative query sections remain server-owned when available.
Placement is selected before execution and does not silently change authority
after a failure.

Tool dispatch accepts both the schema-safe model name and the canonical ID only
when either maps to a Tool registered in the frozen request catalog. Events,
history, trace, and presentation always use the canonical ID. An unknown name,
malformed arguments, unavailable placement, bridge rejection, remote Tool
failure, or lost result is normalized into a complete error Tool result and
returned to the model so it can recover or explain the limitation. Explicit
request cancellation, disconnect, and shutdown cancel outstanding client Tool
calls and suppress late results.

The generic player-facing phrase “using a read-only Tool” is removed. Known
Tools show their concrete localized action; unknown Tools retain their normal
card title/status without an invented generic action sentence.

## States and Transitions

- `settings_idle -> model_catalog_loading`: validate the draft, select the
  transient or stored credential, and start one cancellable non-inference
  request.
- `model_catalog_loading -> settings_idle`: publish only validated model IDs or
  one stable redacted failure; stale completion after draft change is ignored.
- `server_model_wait -> client_tool_wait`: freeze the authenticated player's
  client Tool placement and send one correlated call.
- `client_tool_wait -> server_model_wait`: return a normalized success or error
  Tool result and continue the same Agent chronology.
- `client_tool_wait -> cancelled`: request cancel, disconnect, or shutdown
  cancels the correlation and suppresses every late result.
- `connection lost -> session recovered`: retain provider-neutral history but
  replace a server-model pointer with the configured local default.

## Invariants

1. Client settings cannot create, edit, delete, test, or persist a server model.
2. A server model appears on the client only while the current connection
   advertises it and is explicitly labeled server-provided/read-only.
3. Client Tool schemas used by a server Agent come from trusted server code;
   the client can only reduce the registered read-only ID set.
4. Every reverse Tool call is bound to one authenticated actor and one active
   request, and cannot survive cancellation, disconnect, or capability-snapshot
   replacement.
5. Tool execution failures are model-visible structured Tool results unless
   the enclosing request itself was explicitly cancelled or disconnected.
6. Canonical Tool aliases never widen authority: only an already-registered
   Tool in the frozen catalog can be selected.
7. Listing models is configuration-layer network I/O, not an Agent Tool and not
   evidence that inference works.
8. A raw credential is never persisted outside the dedicated credential store
   or represented in UI/service/protocol/trace state.

## Failure Semantics

- Missing credential: `model_catalog_credential_missing`; send no request.
- Authentication/rate/transport/timeout/malformed catalog: return the matching
  `model_catalog_*` failure, retain the typed model ID and prior candidates.
- Stale catalog completion: ignore without changing the newer draft.
- Client Tool absent or disabled in the frozen request set:
  `client_tool_unavailable` Tool result; continue the Agent.
- Client Tool bridge rejects, times out, disconnects, or returns malformed
  chunks: a redacted `client_tool_*` Tool result, unless the whole request was
  explicitly cancelled.
- Unknown model Tool name: `tool_unavailable` Tool result; never throw an
  executor exception that terminates the request.

## Applies To

- Guide model selection/recovery and native model-origin labels
- client settings service, credential presence, generic model catalog client,
  model picker, and localization
- common Tool-name binding and error normalization
- common bridge payloads/correlation and Fabric/NeoForge client/server adapters
- server Agent per-request Tool placement, cancellation, disconnect, and tests
- opt-in redacted live-provider and graphical acceptance

## Supersedes

- Refines SKMB-2026-07-18-009: client-profile selections remain durable, while
  a server-model selection is connection-scoped and does not auto-resume.
- Refines SKMB-2026-07-17-001/004 by making read-only Tool location independent
  in both directions and making remote Tool failure recoverable by the model.
- Refines SKMB-2026-07-18-015/019 with authenticated model listing and actual
  saved-credential presence while preserving probe isolation and redaction.

## Superseded By

None.
