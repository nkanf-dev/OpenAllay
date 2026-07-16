# Trace Replay and Knowledge Tools Design

## 1. Purpose

This phase builds TomeWisp's first grounded knowledge execution path without a
model provider. A versioned Agent trace supplies the model-authored steps, while
TomeWisp executes every tool call against the current Minecraft server and
verifies the real result. The same tool and context boundaries will later be
used by a live model runtime.

## 2. Confirmed Decisions

| Area | Decision |
| --- | --- |
| Model integration | Deferred until a model endpoint is available |
| Simulation | Replay a recorded Agent trace; do not build a rule-based fake model |
| Tool results | Execute real tools and compare against trace expectations |
| Trace format | Versioned JSON resources |
| Execution | Sequential, deterministic, read-only |
| First tools | Resource resolution, recipe lookup, and player context |
| User interface | Development command and structured chat output |
| Loaders | Fabric and NeoForge remain equal targets |
| Context safety | Immutable, complete snapshots captured on the server thread |

## 3. Scope

This phase includes:

- a context-aware Tool invocation contract;
- immutable caller, player, registry, and recipe snapshots without artificial size limits;
- three first-party knowledge tools;
- JSON trace discovery, parsing, validation, and replay;
- structured expectation matching and replay reports;
- `/tomewisp dev replay <trace-id>` on both loaders;
- unit, integration, architecture, and headless server verification.

This phase excludes:

- model SDKs, API keys, HTTP model calls, token accounting, and streaming;
- natural-language intent parsing or a rule-based mock Agent;
- FTB Quests, Patchouli, private guides, reflective adapters, and Skills;
- search, embeddings, vector databases, and ranking;
- world mutation, command execution, item grants, or quest completion;
- parallel tool calls, branching traces, retries, and persisted conversations.

## 4. Architecture

```text
Versioned trace JSON
  -> TraceRepository
  -> TraceParser and TraceValidator
  -> AgentTraceReplayer
       -> ToolArgumentCodec
       -> ToolRegistry
       -> real Tool invocation
       -> ToolResultNormalizer
       -> ExpectationMatcher
  -> ReplayReport
  -> development command output
```

Trace code depends only on common Tool contracts and JSON DTOs. It does not
depend on Fabric, NeoForge, or a future model client. Loader modules only adapt
their command source to the common replay service.

## 5. Context Engineering

Context engineering is a hard product boundary, not an incidental DTO layer.
Minecraft state must be selected, normalized, complete for the requested
capabilities, and detached before it
can reach tools, traces, or a future model.

### 5.1 Invocation contract

The Tool contract becomes conceptually:

```java
ToolResult<O> invoke(ToolInvocationContext context, I input);
```

`ToolInvocationContext` contains only immutable records:

- `CallerSnapshot`: caller kind, stable UUID when present, display name, and
  effective permission category;
- optional `PlayerSnapshot`: dimension, block position, game mode, main-hand
  item, off-hand item, and complete inventory summary;
- `RegistrySnapshot`: normalized item/block entries required by the invocation;
- `RecipeSnapshot`: normalized recipes required by the invocation;
- invocation timestamp and a trace/request correlation ID.

The context never contains `MinecraftServer`, `ServerPlayer`, `RecipeManager`,
registry handles, levels, entities, item stacks, or other mutable game objects.

### 5.2 Capture boundary

`MinecraftContextCapture` runs on the server thread. It reads Minecraft state,
applies visibility rules, converts the complete requested data to immutable Java
records, and then releases all live game references. Replay and future model
work consume only this detached context.

The capture request declares which context capabilities are needed. A recipe
trace does not receive player inventory, and a resource-resolution trace does
not receive recipes. This keeps context minimal and prevents accidental data
expansion.

### 5.3 No preemptive size limits

This phase does not impose product-defined limits on context size, recipe
results, ingredient alternatives, text length, trace length, or report size.
Limits cannot be selected responsibly before a real Agent runs against real
modpacks and produces measured failures. No value is silently truncated or
dropped to satisfy a guessed budget.

The runtime records context item counts, serialized byte sizes, capture
duration, tool-result sizes, and replay duration for observation. A future limit
requires evidence from an actual model context overflow, latency failure, memory
failure, or unusable response, followed by a separate design decision. JVM,
Minecraft, loader, and transport implementation limits still exist, but
TomeWisp does not add an arbitrary policy limit in this phase.

### 5.4 Normalization

- Resource IDs are lowercase namespaced IDs.
- Positions are integer block coordinates; no mutable vector type crosses the
  boundary.
- Inventory summaries preserve every player inventory slot, including empty
  slots when slot identity is relevant.
- Recipe lookup returns every matching recipe.
- Recipe ingredients preserve every declared alternative item ID.
- Text fields are preserved in full through replay reports.
- Every knowledge result carries source provenance such as `minecraft:registry`
  or `minecraft:recipe_manager`.
- DTOs do not contain a truncation mode in this phase because TomeWisp does not
  intentionally truncate data.

### 5.5 Caller semantics

Player commands capture the executing player's snapshot. Server-console replay
has no player snapshot. A trace that requires player context fails with the
structured code `player_required`; TomeWisp never invents a synthetic player in
production. Tests may inject fixed snapshots through the common context API.

## 6. First Knowledge Tools

### 6.1 `tomewisp:resolve_resource`

Input contains a namespaced ID and an optional requested kind. Output contains
the canonical ID, resource kind, display name, owning namespace, existence
status, and provenance. Unknown IDs return a successful structured
`exists=false` result rather than throwing.

### 6.2 `tomewisp:find_recipes`

Input contains an output item ID. Output contains every normalized matching
recipe with recipe ID, recipe type, complete ingredient slots, output
item/count, and provenance. Invalid IDs fail as
`invalid_arguments`; a valid item with no recipes returns an empty success.

### 6.3 `tomewisp:player_context`

Input is empty. Output contains the detached player snapshot available to the
invocation. Console invocation fails as `player_required`. The tool is read-only
and never exposes hidden server state beyond the executing player.

## 7. Trace Format

Trace files live in server data resources and use schema version 1:

```json
{
  "schemaVersion": 1,
  "id": "iron-ingot-recipe",
  "userMessage": "铁锭怎么做？",
  "requiredContext": ["recipes"],
  "steps": [
    {
      "type": "tool_call",
      "tool": "tomewisp:find_recipes",
      "arguments": {
        "outputItem": "minecraft:iron_ingot"
      },
      "expect": {
        "status": "success",
        "match": "contains",
        "value": {
          "outputItem": "minecraft:iron_ingot"
        }
      }
    },
    {
      "type": "assistant_message",
      "content": "铁锭可以通过熔炼铁矿类材料获得。"
    }
  ]
}
```

Schema version, trace ID, step type, tool ID, arguments, expectation mode, and
assistant content are validated before execution. Unknown fields are rejected
in schema version 1 so malformed fixtures cannot appear to pass.

## 8. Replay Semantics

Replay is strictly sequential. For each tool step the replayer:

1. verifies that the tool exists;
2. checks that the captured context satisfies required capabilities;
3. converts JSON arguments to the tool's declared input record;
4. invokes the real ToolRegistry entry;
5. converts the result to canonical JSON;
6. applies the declared expectation matcher;
7. records duration, status, provenance, and mismatch detail.

An assistant-message step records the pre-authored message in the report. It
does not claim that TomeWisp generated new language.

Expectation modes are:

- `exact`: canonical actual JSON must equal expected JSON;
- `contains`: expected JSON must be a recursive structured subset of actual
  JSON; arrays retain order;
- `schema`: validates result status and declared output type without comparing
  values.

Replay stops at the first failed step and preserves all prior step reports.

## 9. Commands and Output

Both loaders expose:

```text
/tomewisp dev replay <trace-id>
```

The command remains game-master-only. Trace IDs receive command suggestions.
Output shows trace ID, each step index and status, invoked tool, elapsed time,
and final assistant message or structured failure. Full mismatch JSON is logged
server-side. Chat output may use multiple messages for presentation, but it does
not discard report data.

## 10. Errors

The replay layer uses stable codes:

- `unknown_trace`
- `invalid_trace`
- `unknown_tool`
- `invalid_arguments`
- `missing_context`
- `player_required`
- `tool_failure`
- `expectation_mismatch`

Tool exceptions become `tool_failure`; stack traces remain in development logs.
No failure falls back to another tool, another trace, or fabricated data.

## 11. Testing

### 11.1 Pure Java tests

- strict JSON parsing and schema-version rejection;
- record argument conversion;
- exact, contains, and schema matching;
- unknown trace/tool and invalid argument failures;
- replay stops on first failure and retains prior reports;
- complete preservation of trace and report text;
- observation metrics report counts, serialized sizes, and durations without
  enforcing policy limits.

### 11.2 Context and Tool tests

- capability-minimal capture requests;
- fixed immutable player, registry, and recipe snapshots;
- complete inventory slots, recipe sets, and ingredient alternatives;
- resource canonicalization and unknown-resource behavior;
- console invocation of player context returns `player_required`;
- no common DTO field uses a mutable Minecraft runtime type.

### 11.3 Headless server verification

Fabric and NeoForge dedicated servers replay the same bundled trace. Both must
report successful real tool execution and their normal shutdown path. No client
or graphical run configuration is part of this phase's verification.

## 12. Acceptance Criteria

- A bundled trace is discovered and validated on both loaders.
- `/tomewisp dev replay <trace-id>` invokes the real ToolRegistry.
- Resource, recipe, and player tools return complete immutable results for the
  requested context capabilities.
- No TomeWisp-defined size, count, text, trace-step, or report limit is enforced.
- The same deterministic trace produces a passing ReplayReport on Fabric and
  NeoForge when run against equivalent state.
- Player-required traces fail explicitly from the server console.
- No live Minecraft object crosses the context boundary.
- No model client, external API, or API key is present.
- Unit tests, architecture tests, dual-loader builds, headless smoke tests, and
  GitHub Actions pass.

## 13. Deferred Follow-ups

After a model endpoint is available, a live Agent driver will emit the same
tool-call events consumed by this replay design. Later phases add Skills, FTB
Quests, Patchouli, private guide providers, search, and Ponder generation
without weakening the context boundary established here.
