# Controlled Extension host methods (API 0.2.2)

An Extension is trusted loader code. Its authority does not become the Agent's
Java/JVM authority. A safe script can call only the methods that the Extension
registers through `JavascriptHostBinding`, using `require(bindingId)`.

The public contribution keeps the original four-list and five-list constructors.
The current constructor appends `hostBindings` and `capabilities`, in that order.
Existing 0.2.x binaries keep their constructor contracts. Independently released
Extensions that use the new contracts must require API 0.2.2 or later.

## Method contract

`JavascriptHostMethod` declares an exact method name, ordered parameter types,
return type, required capability IDs, and a trusted Java implementation.
`JavascriptHostValueType` accepts `STRING`, `BOOLEAN`, `INTEGER`, `NUMBER`,
`JSON`, or `NULL`. Integers must be exact safe integers. Numbers must be finite.
The callback receives detached Gson `JsonElement` arguments, not Rhino values.
It returns detached JSON, not a Java session, game object, function, or callback.
The bridge rejects Java wrappers, executable values, cycles, and undefined data.

Every invocation uses the same Extension-owned `JavascriptInvocationContext`
for its participant and host methods. Another Extension's grants are not present.
Every method call checks activity, worker identity, and declared capabilities.
The implementation must repeat activity/capability checks immediately before
native owner-thread actions and revalidate its exact backend/session identity.
It owns thread scheduling, cooperative cancellation, native deadlines, and cleanup.
A game owner thread must never run an Agent JavaScript callback or wait for a worker.

## Grants

Installing an Extension registers its read APIs. It grants no world action or JVM
access. Each actual operation scope is declared as an `ExtensionCapability` with
user-facing risk text. The Extension detail page offers a separate default-off
switch for each declared scope. There is no global “all native” switch.

Grants are persisted independently in `extension-capabilities.json` with the
exact latest-only shape `{"grants":{"extension:id":["scope:id"]}}`. Unknown
Extension/scope pairs do not become runtime authority. Missing/invalid settings
fail closed. Publication follows successful atomic persistence.

Client-local requests freeze their active declared grants at request start.
Server-origin requests freeze an empty grant set. Changing settings affects new
requests. Request cancellation/close revokes admitted work, including queued
native actions. The independent unrestricted-Java setting stays default-off;
its previous enabled state never implies an Extension grant.

## Resource domains

Only the trusted host implementation's native execution/wait time is excluded
from the safe interpreter deadline. Argument/result validation and subsequent
JavaScript keep the interpreter budget. Cancellation and interruption remain
active. This does not enable Java classes or make pure JavaScript loops unlimited.

Host transport is exact, not a model preview. Large native batch-plan strings are
not truncated or limited by model-result preview budgets. Closed JSON transport
retains nesting/cycle/type checks. Final Agent return normalization, workspace
results, source limits, and model-context projection remain their own domains.

## Errors

A reviewed `JavascriptExecutionException` preserves its stable domain code and
safe message. Other native exceptions/errors become
`javascript_extension_host_failed` without native messages or stacks. Extensions
must not put unchecked backend exception text into their domain messages.
