# SKMB-2026-09-29-033: Client-local unrestricted JavaScript mode

Status: accepted (explicit user approval recorded in the implementation task).

## Decision

Add an independently persisted, strict schema-versioned setting. Missing or
invalid settings resolve to disabled. The player-facing Extensions screen warns
that scripts can execute arbitrary JVM code and cause file, network, process,
and other side effects; changes cannot be rolled back; and JVM-accessible
credentials may be read.

At client-local request start, capture the setting in immutable
`ToolInvocationContext`. It is a capability, not model/prompt input. The chosen
endpoint topology is authoritative: server-model requests are always false.
Every server-originated client Tool callback explicitly captures false even
when the local player setting is enabled. The host server's model configuration
and callbacks cannot enable this option.

Enabled JavaScript uses Rhino standard objects and an explicit `Java.type`
bridge backed by Rhino class visibility/wrapping. It bypasses OpenAllay
JavaScript source, interpreter-time, result-normalization, workspace,
handle-selection, and model-preview budgets. Cancellation and request-scoped
lifetime cleanup remain active. Disabled execution retains safe-standard
objects, denied Java wrappers, and existing limits.

## Automatic capability guidance

Requests with frozen client-local unrestricted authorization automatically receive
bundled `unrestricted-javascript` guidance. The system prompt identifies Java/JVM
interop as available and explains how to use it for the player's task, rather
than applying the default detached-data-only restriction. The core instructions
are provided immediately; detailed examples are declared Skill references.

The request Skill catalog and prompt follow the same captured authorization as
execution. A Skill cannot enable Java access, override the local toggle, or grant
server-model/callback authority. Default and server requests do not advertise this
Skill. Enabling experimental Minecraft commands does not unhide unrelated
optional capabilities. Tool and Skill deny policies still apply.

The guidance must describe the actual Rhino API and live-object threading rules.
It must not imply `mc` views become mutable, that Java results automatically carry
grounding evidence, that cancellation rolls back side effects, or that JVM
credentials can be disclosed in prompts, results, logs, or player answers.

## State and failure semantics

- `disabled` is the only implicit state, including missing, malformed, or
  unsupported configuration.
- `enabled` is stored locally and frozen per selected client-local request.
- Changes affect future requests only. Existing requests retain captured
  authority.
- Disconnect, close, cancellation, or completion does not change the persisted
  setting. Request-scoped workspace data is still discarded at termination.
- Java class lookup failures return a structured script failure.

## Security notes

This feature is an explicit user-authorized escape from the Rhino sandbox, not a
sandbox extension. A script can reach JVM APIs and cause external side effects.
OpenAllay does not provide rollback, secret isolation, or process containment.
The enabled mode may expose credentials available to the Minecraft JVM.
