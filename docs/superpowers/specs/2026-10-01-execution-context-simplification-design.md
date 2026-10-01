# Execution and context simplification design

Date: 2026-10-01. Status: implemented; deterministic and build verification passed.
Graphical timing and live-provider verification were not run for this refactor.
The user explicitly authorizes a breaking refactor without compatibility layers.
[Decision 041](../../isme/decisions/2026-10-01-041-execution-context-simplification.md)
owns state, authority, persistence and failure semantics.

## Product boundary

OpenAllay is a Minecraft Agent that reads, computes, acts through enabled
capabilities and debugs results. It is not a mandatory citation pipeline.
Useful source identity can explain stale/partial/client-visible data, but is not
proof of arbitrary JavaScript claims and cannot determine execution success.

## Data flow

1. Capture detached game state on its owner. Publish immutable request capabilities.
2. Bind one lazy graph in Rhino. A script reads data without a separate root list.
3. Execute once and normalize only its explicit return. Return actual errors.
4. Collect optional origins automatically from actual accesses; group by identity.
5. Append genuine invocation/result messages to the model transcript.
6. Retain original redacted exchanges; derive UI/export and compacted context.
7. Restore only missing sessions, not each ask. Reuse Skill text still in context.

There is no new orchestration loop, VFS, retrieval tool catalog, arbitrary policy
DSL, duplicate legacy pipeline, source-text dependency scanner or rerun fallback.
Existing optional knowledge/recipe adapters remain useful inputs, not the central
execution abstraction. No domain algorithms move from Builder into core.

## Deletions

- Remove the model-facing roots selector and selected-root masking.
- Remove the generic execution evidence-empty failure and mandatory source footer.
- Remove timestamp-distinct per-read source accumulation where one source summary
  represents the same actual origin without losing scope/coverage.
- Delete UI-history-to-ToolUse reconstruction and fake durableProjection arguments.
- Delete the knowledge-domain conclusion/stable-ID whitelist result reducer.
- Delete per-question replacement of valid live session context.
- Replace quadratic Builder full-journal publication and per-block scan/undo calls.
- Do not retain compatibility constructors, old format adapters, or aliases solely
  to make obsolete tests pass. Rewrite tests around the new contract.

## Kept boundaries

Owner-thread capture, authenticated player scope, explicit runtime permissions,
cancellation/disconnect, exact ToolUse/ToolResult pairing, credential redaction,
true partial/unknown facts, source-scoped lookup IDs, and Builder undo/conflict
semantics still solve real problems. Keep them independent of source presentation.

## Components and implementation ownership

- Data/runtime: RunJavascriptTool, MinecraftAgentHostGraph, Rhino error formatting,
  workspace origins and deterministic tool routing.
- Context/history: Agent events/store, GuideService hydration, strict transcript
  codec, latest-only history/bridge formats, Skill context reuse and budget compaction.
- Presentation: actual errors/results, Debug program/input/output, collapsed sources.
- Export: original safe model exchanges, independent of compacted context.
- Builder Extension: native scan/write/undo batches and unversioned incremental journal.

## Acceptance

The second related question can use the first question's actual result and Skill
text without reloading it. A follow-up can quote the actual failed JavaScript
error. A successful pure computation never needs a fake game read. Available
collections work without a roots declaration. Debug output is readable without
scrolling past repeated origins. Large building work is batched; actual latency is
measured separately from deterministic dispatch and crash-durability tests.

Internal formats are Latest Only before formal 1.0, with no version identifiers,
migrations or old-format adapters. External independently released component API
versions and native game-version facts remain. World saves and exported diagnostic
material are not deleted by this refactor.
No release is created by this work.
