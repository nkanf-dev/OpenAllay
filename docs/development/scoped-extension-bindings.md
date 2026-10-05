# Controlled Extension host methods

The native-neutral public SDK 0.4.0 exposes trusted Extension methods through
`JavascriptHostBinding`. Ordinary JavaScript calls these methods with `require(bindingId)`;
it does not need Java/JVM access to use an enabled Extension's normal functions.

## Current declaration contract

Public `ExtensionContribution` contains five lists: JavaScript modules, Skills,
result views, invocation participants and host bindings. `JavascriptHostMethod`
contains a method name, ordered parameter types, result type and trusted invoker.
There are no Extension-private capability declarations or required-permission sets.
The core's separate legacy loader-mod contribution retains its released four-list
and five-list constructors; its current internal representation has six lists.

Public invokers receive detached JSON argument strings and return one JSON value
as a string. The core bridge parses, validates types and detaches results. It does
not forward Java sessions, live game objects, Rhino values or callbacks into the
host implementation. Exact arity, finite numbers, JSON shape and nesting validation
remain in place.

## Invocation lifetime

Each active Extension receives its own `ExtensionInvocation` identity for that
admitted execution. It exposes scoped caller identity, cancellation, active-state
checks, evidence and normal completion. It contains no private grant API.

Every host call checks active lifetime and the exact script worker. Before queued
native work, implementations recheck activity and their exact connection/player/
world session. They own native scheduling, cooperative cancellation and cleanup.
A game owner thread must not run Agent JavaScript callbacks or wait for a worker.
Cancelling or closing the invocation revokes queued native activity; it does not
roll back completed operations.

## Player access

Enabling Minecraft Builder includes building operations in ordinary JavaScript
mode. No additional private write switch or grant store is involved. Full-access
JavaScript includes game commands and enabled Extension operations; the lower
OpenAllay command-only toggle does not block full access. Actual Minecraft server
permissions and native session validity remain authoritative.

Do not create an Extension-private permission system. If a host API genuinely
provides a standardized permission, use that API contract. Do not add an independent
approval barrier when the framework has no such permission, or build a new permission
framework merely to justify a redundant gate.

## Resource domains

Only the trusted host implementation's native execution/wait time is excluded
from the safe interpreter deadline. Argument/result validation and subsequent
JavaScript retain the interpreter budget. Cancellation and interruption remain
active. This does not expose Java classes or make pure JavaScript loops unlimited.

Host transport is exact, not a model preview. Large native plan strings are not
truncated by model-result preview budgets. Final return normalization, canonical
workspace results and model-context projection remain independent mechanisms.

## Errors

Declared domain failures retain stable codes and player-safe summaries. Other host
failures become controlled Extension-host failures without foreign native messages
or stacks. Report verified partial progress and actual readback separately from a
normal method return; never turn a missing or cancelled operation into success.
