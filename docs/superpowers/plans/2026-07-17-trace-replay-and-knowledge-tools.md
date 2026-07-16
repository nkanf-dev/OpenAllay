# Trace Replay and Knowledge Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add immutable Minecraft context snapshots, three real read-only knowledge tools, and deterministic JSON Agent-trace replay on Fabric and NeoForge without a model SDK.

**Architecture:** Common code captures complete requested Minecraft state on the server thread into immutable records. A strict trace parser drives the existing ToolRegistry with real tool calls, canonicalizes results, verifies declared expectations, and emits a structured report. Loader modules only register the shared replay command.

**Tech Stack:** Java 25, Minecraft 26.2 Mojang mappings (`Identifier` API), Gson bundled by Minecraft, JUnit Jupiter 5.11.4, Fabric command API v2, NeoForge 26.2.

---

## Scope and file map

- Context contracts: `common/src/main/java/dev/tomewisp/context/**`.
- Minecraft capture adapter: `common/src/main/java/dev/tomewisp/context/minecraft/MinecraftContextCapture.java`.
- Knowledge tools: `common/src/main/java/dev/tomewisp/tool/builtin/**`.
- Trace DTOs and strict parser: `common/src/main/java/dev/tomewisp/trace/model/**` and `trace/json/**`.
- Replay engine: `common/src/main/java/dev/tomewisp/trace/replay/**`.
- Server resource repository and command service: `common/src/main/java/dev/tomewisp/trace/minecraft/**`.
- Bundled traces: `common/src/main/resources/data/tomewisp/agent_traces/**`.
- Loader command adapters: existing Fabric and NeoForge development command files.
- Tests mirror every common package under `common/src/test/java/dev/tomewisp/**`.

## Task 1: Make Tool invocation context-aware

**Files:**
- Create: `common/src/main/java/dev/tomewisp/context/ContextCapability.java`
- Create: `common/src/main/java/dev/tomewisp/context/CallerKind.java`
- Create: `common/src/main/java/dev/tomewisp/context/CallerSnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/BlockPositionSnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/ItemStackSnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/InventorySlotSnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/PlayerSnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/RegistryEntrySnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/RegistrySnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/IngredientSlotSnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/RecipeEntrySnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/RecipeSnapshot.java`
- Create: `common/src/main/java/dev/tomewisp/context/ContextMetrics.java`
- Create: `common/src/main/java/dev/tomewisp/context/ToolInvocationContext.java`
- Modify: `common/src/main/java/dev/tomewisp/tool/Tool.java`
- Modify: all current Tool implementations and tests.
- Test: `common/src/test/java/dev/tomewisp/context/ToolInvocationContextTest.java`

- [ ] **Step 1: Add a failing context invariant test**

```java
package dev.tomewisp.context;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

final class ToolInvocationContextTest {
    @Test void preservesCompleteImmutableContext() {
        var slots = new ArrayList<>(List.of(
                new InventorySlotSnapshot(0,
                        new ItemStackSnapshot("minecraft:stone", 64, "Stone"))));
        var player = new PlayerSnapshot(UUID.randomUUID(), "Player", "minecraft:overworld",
                new BlockPositionSnapshot(1, 64, 2), "survival",
                new ItemStackSnapshot("minecraft:stone", 64, "Stone"),
                ItemStackSnapshot.empty(), slots);
        var context = new ToolInvocationContext("trace:test", Instant.EPOCH,
                new CallerSnapshot(CallerKind.PLAYER, player.uuid(), "Player", true),
                Optional.of(player), Optional.empty(), Optional.empty(),
                new ContextMetrics(1, 0, 0, 0, 0));
        slots.clear();
        assertEquals(1, context.player().orElseThrow().inventory().size());
        assertThrows(UnsupportedOperationException.class,
                () -> context.player().orElseThrow().inventory().clear());
    }
}
```

- [ ] **Step 2: Implement immutable context records**

Use compact constructors with `List.copyOf`, `Set.copyOf`, nonblank namespaced-ID
validation, nonnegative counts/metrics, and `Objects.requireNonNull`. Define:

```java
public enum ContextCapability { REGISTRIES, RECIPES, PLAYER }
public enum CallerKind { CONSOLE, PLAYER }
public record CallerSnapshot(CallerKind kind, UUID uuid, String displayName,
        boolean gameMaster) {}
public record BlockPositionSnapshot(int x, int y, int z) {}
public record ItemStackSnapshot(String itemId, int count, String displayName) {
    public static ItemStackSnapshot empty() {
        return new ItemStackSnapshot("minecraft:air", 0, "Air");
    }
}
public record InventorySlotSnapshot(int slot, ItemStackSnapshot stack) {}
public record PlayerSnapshot(UUID uuid, String displayName, String dimension,
        BlockPositionSnapshot position, String gameMode, ItemStackSnapshot mainHand,
        ItemStackSnapshot offHand, List<InventorySlotSnapshot> inventory) {
    public PlayerSnapshot { inventory = List.copyOf(inventory); }
}
public record RegistryEntrySnapshot(String id, String kind, String displayName,
        String namespace, String provenance) {}
public record RegistrySnapshot(List<RegistryEntrySnapshot> entries) {
    public RegistrySnapshot { entries = List.copyOf(entries); }
}
public record IngredientSlotSnapshot(List<String> alternatives) {
    public IngredientSlotSnapshot { alternatives = List.copyOf(alternatives); }
}
public record RecipeEntrySnapshot(String id, String type,
        List<IngredientSlotSnapshot> ingredients, List<ItemStackSnapshot> outputs,
        String provenance) {
    public RecipeEntrySnapshot {
        ingredients = List.copyOf(ingredients); outputs = List.copyOf(outputs);
    }
}
public record RecipeSnapshot(List<RecipeEntrySnapshot> recipes) {
    public RecipeSnapshot { recipes = List.copyOf(recipes); }
}
public record ContextMetrics(long registryEntries, long recipes, long inventorySlots,
        long estimatedSerializedBytes, long captureNanos) {}
public record ToolInvocationContext(String correlationId, Instant capturedAt,
        CallerSnapshot caller, Optional<PlayerSnapshot> player,
        Optional<RegistrySnapshot> registries, Optional<RecipeSnapshot> recipes,
        ContextMetrics metrics) {}
```

`CallerSnapshot.uuid` is nullable only for `CONSOLE`; all other fields are
non-null. Do not add count, text, byte, or collection limits.

- [ ] **Step 3: Change the Tool contract and current callers**

```java
public interface Tool<I, O> {
    ToolDescriptor<I, O> descriptor();
    ToolResult<O> invoke(ToolInvocationContext context, I input);
}
```

Update `PlatformInfoTool`, registry tests, and development-inspector tests to
pass a shared test context. Change `DevelopmentToolInspector.invokeNoArgument`
to accept `ToolInvocationContext` and pass it to `invokeUnchecked`.

- [ ] **Step 4: Run tests and commit**

```bash
./gradlew-curl :common:test --tests 'dev.tomewisp.context.*' --tests 'dev.tomewisp.tool.*'
git add common/src/main/java/dev/tomewisp/context common/src/main/java/dev/tomewisp/tool common/src/main/java/dev/tomewisp/devmode common/src/test
git commit -m "feat: add immutable tool invocation context"
```

Expected: context and existing tool tests pass.

## Task 2: Capture complete Minecraft context on the server thread

**Files:**
- Create: `common/src/main/java/dev/tomewisp/context/minecraft/MinecraftContextCapture.java`
- Test: `common/src/test/java/dev/tomewisp/context/ContextArchitectureTest.java`

- [ ] **Step 1: Add the architecture test**

The test reflects over every record component reachable from
`ToolInvocationContext` and fails if a component type package begins with
`net.minecraft`, `net.fabricmc`, or `net.neoforged`.

- [ ] **Step 2: Implement capture using verified 26.2 APIs**

`MinecraftContextCapture.capture(CommandSourceStack, Set<ContextCapability>,
String)` must first assert `source.getServer().isSameThread()`. It captures:

```java
CallerSnapshot caller = source.isPlayer()
        ? new CallerSnapshot(CallerKind.PLAYER, source.getPlayer().getUUID(),
                source.getTextName(), source.permissions()
                        .hasPermission(Permissions.COMMANDS_GAMEMASTER))
        : new CallerSnapshot(CallerKind.CONSOLE, null, source.getTextName(), true);
```

For PLAYER, iterate every `Inventory#getContainerSize()` slot and preserve every
slot, including empty stacks. Convert stacks with
`BuiltInRegistries.ITEM.getKey(stack.getItem())`, `getCount()`, and
`getHoverName().getString()`. Dimension uses
`player.level().dimension().identifier().toString()`; position uses
`player.blockPosition()`; game mode uses `player.gameMode().getName()`.

For REGISTRIES, iterate every item and block in `BuiltInRegistries.ITEM` and
`BuiltInRegistries.BLOCK`. Preserve both entries when an ID exists in both
registries. Provenance is `minecraft:registry`.

For RECIPES, iterate `source.getServer().getRecipeManager().getRecipes()`.
Inputs come from `holder.value().placementInfo().ingredients()` and each
`Ingredient.items()` holder key. Outputs come from every
`holder.value().display()` result resolved by
`SlotDisplayContext.fromLevel(source.getLevel())`. Type uses
`BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())`; ID uses
`holder.id().identifier()`; provenance is `minecraft:recipe_manager`.

Metrics record complete collection counts, `System.nanoTime()` duration, and a
deterministic UTF-8 JSON byte estimate computed after capture. No metric enforces
a limit.

- [ ] **Step 3: Verify and commit**

```bash
./gradlew-curl :common:test --tests dev.tomewisp.context.ContextArchitectureTest :fabric:compileJava :neoforge:compileJava
git add common/src/main/java/dev/tomewisp/context/minecraft common/src/test/java/dev/tomewisp/context
git commit -m "feat: capture complete Minecraft context snapshots"
```

## Task 3: Add resource, recipe, and player knowledge tools

**Files:**
- Create: `common/src/main/java/dev/tomewisp/tool/builtin/ResolveResourceTool.java`
- Create: `common/src/main/java/dev/tomewisp/tool/builtin/FindRecipesTool.java`
- Create: `common/src/main/java/dev/tomewisp/tool/builtin/PlayerContextTool.java`
- Test: matching tests under `common/src/test/java/dev/tomewisp/tool/builtin/`.
- Modify: `TomeWispBootstrap.java`

- [ ] **Step 1: Write tests from fixed snapshots**

Verify resource resolution supports optional kind `item`/`block`, unknown IDs
return `exists=false`, recipe lookup returns every matching recipe, and player
context returns `player_required` for console context.

- [ ] **Step 2: Implement `ResolveResourceTool`**

Input and output are:

```java
public record Input(String id, String kind) {}
public record Match(String id, String kind, String displayName,
        String namespace, String provenance) {}
public record Output(String requestedId, boolean exists, List<Match> matches) {
    public Output { matches = List.copyOf(matches); }
}
```

Validate with `Identifier.tryParse`. Missing registry context returns
`ToolResult.Failure("missing_context", ...)`. Null/blank kind means both kinds.

- [ ] **Step 3: Implement `FindRecipesTool`**

Input is `record Input(String outputItem)`. Output is
`record Output(String outputItem, List<RecipeEntrySnapshot> recipes)` with a
defensive copy. Return every recipe whose outputs contain the canonical item ID.
Do not add a limit or truncation field.

- [ ] **Step 4: Implement `PlayerContextTool`**

Input is an empty record and output wraps `PlayerSnapshot`. Missing player
returns `ToolResult.Failure("player_required", ...)`.

- [ ] **Step 5: Register tools and commit**

Register all three under provider `tomewisp:builtins` beside platform info.

```bash
./gradlew-curl :common:test --tests 'dev.tomewisp.tool.builtin.*'
git add common/src/main/java/dev/tomewisp/tool/builtin common/src/test/java/dev/tomewisp/tool/builtin common/src/main/java/dev/tomewisp/TomeWispBootstrap.java
git commit -m "feat: add grounded Minecraft knowledge tools"
```

## Task 4: Define strict versioned Agent traces

**Files:**
- Create: `common/src/main/java/dev/tomewisp/trace/model/AgentTrace.java`
- Create: `TraceStep.java`, `ToolCallStep.java`, `AssistantMessageStep.java`,
  `TraceExpectation.java`, and `ExpectationMatch.java` in the same package.
- Create: `common/src/main/java/dev/tomewisp/trace/json/TraceParser.java`
- Test: `common/src/test/java/dev/tomewisp/trace/json/TraceParserTest.java`

- [ ] **Step 1: Write parser fixtures and failing tests**

Tests cover valid schema 1, unknown top-level and step fields, unsupported
schema, duplicate/blank IDs, unknown step types, invalid tool IDs, and missing
expectation fields.

- [ ] **Step 2: Implement immutable trace records**

```java
public enum ExpectationMatch { EXACT, CONTAINS, SCHEMA }
public sealed interface TraceStep permits ToolCallStep, AssistantMessageStep {}
public record ToolCallStep(String tool, JsonObject arguments,
        TraceExpectation expect) implements TraceStep {}
public record AssistantMessageStep(String content) implements TraceStep {}
public record TraceExpectation(String status, ExpectationMatch match,
        JsonElement value, String outputType) {}
public record AgentTrace(int schemaVersion, String id, String userMessage,
        Set<ContextCapability> requiredContext, List<TraceStep> steps) {
    public AgentTrace { requiredContext = Set.copyOf(requiredContext);
        steps = List.copyOf(steps); }
}
```

- [ ] **Step 3: Implement strict manual Gson parsing**

`TraceParser` receives a `Reader`, checks exact allowed-key sets for every
object, converts capability strings `registries`, `recipes`, and `player`, and
returns `ToolResult<AgentTrace>` with `invalid_trace` on any validation error.
It must not use a lenient polymorphic Gson adapter.

- [ ] **Step 4: Test and commit**

```bash
./gradlew-curl :common:test --tests dev.tomewisp.trace.json.TraceParserTest
git add common/src/main/java/dev/tomewisp/trace common/src/test/java/dev/tomewisp/trace
git commit -m "feat: parse strict versioned agent traces"
```

## Task 5: Implement canonical replay and expectation matching

**Files:**
- Create all files under `common/src/main/java/dev/tomewisp/trace/replay/`:
  `ToolArgumentCodec`, `ToolResultNormalizer`, `ExpectationMatcher`,
  `ReplayStepReport`, `ReplayReport`, `ReplayMetrics`, and `AgentTraceReplayer`.
- Test: matching replay tests under `common/src/test/java/dev/tomewisp/trace/replay/`.

- [ ] **Step 1: Test exact, contains, and schema semantics**

Object subset comparison is recursive and key-order independent. Arrays retain
order and `contains` requires each expected array element at the same index but
allows additional actual tail elements. Numbers compare by numeric value.

- [ ] **Step 2: Implement argument conversion and normalization**

`ToolArgumentCodec` uses Gson to convert `JsonObject` to the descriptor input
record and returns `invalid_arguments` on conversion failure.

`ToolResultNormalizer` emits canonical objects:

```json
{"status":"success","outputType":"fully.qualified.Record","value":{}}
{"status":"failure","code":"player_required","message":"..."}
```

Canonicalization recursively sorts object keys. It preserves all array elements
and complete strings.

- [ ] **Step 3: Implement replay reports and engine**

Each report records step index/type, tool ID when present, pass/fail, elapsed
nanos, normalized actual value, expected value, and error. `ReplayMetrics`
records context counts/bytes, total tool-result serialized bytes, and total
duration without enforcing limits.

`AgentTraceReplayer.replay(trace, context)` executes sequentially, stops on the
first failure, and retains prior reports. It performs type-safe invocation via
one private audited unchecked cast. Assistant messages are copied verbatim.

- [ ] **Step 4: Test and commit**

```bash
./gradlew-curl :common:test --tests 'dev.tomewisp.trace.replay.*'
git add common/src/main/java/dev/tomewisp/trace/replay common/src/test/java/dev/tomewisp/trace/replay
git commit -m "feat: replay and verify real tool calls"
```

## Task 6: Load traces from server data resources

**Files:**
- Create: `common/src/main/java/dev/tomewisp/trace/minecraft/TraceRepository.java`
- Create: `common/src/main/java/dev/tomewisp/trace/minecraft/TraceReplayService.java`
- Create: `common/src/main/resources/data/tomewisp/agent_traces/platform-info.json`
- Create: `common/src/main/resources/data/tomewisp/agent_traces/iron-ingot-recipe.json`
- Create: `common/src/main/resources/data/tomewisp/agent_traces/player-context.json`
- Test: `common/src/test/java/dev/tomewisp/trace/minecraft/TraceRepositoryTest.java`
- Modify: `TomeWispRuntime.java`, `TomeWispBootstrap.java`.

- [ ] **Step 1: Implement resource discovery**

Use `ResourceManager.listResources("agent_traces", id ->
id.getPath().endsWith(".json"))`. Open with `Resource.openAsReader()`. Require JSON
trace ID to equal the filename without `.json`; reject duplicate IDs from
different namespaces as `invalid_trace`. Return IDs in sorted order.

- [ ] **Step 2: Implement replay service**

`TraceReplayService.replay(CommandSourceStack source, String traceId)` loads the
trace from `source.getServer().getResourceManager()`, captures exactly its
declared capabilities through `MinecraftContextCapture`, and calls the replayer.
The service verifies it runs on the server thread.

- [ ] **Step 3: Add bundled traces**

`platform-info.json` requires no context and schema-matches the platform tool.
`iron-ingot-recipe.json` requires registries and recipes, calls
`tomewisp:find_recipes` for `minecraft:iron_ingot`, and uses a contains
expectation. `player-context.json` requires player and schema-matches the player
tool; console replay must fail `player_required`.

- [ ] **Step 4: Wire runtime and commit**

Add `TraceReplayService traceReplay` to `TomeWispRuntime`; construct it once in
bootstrap from the registry, parser, codecs, matcher, and context capture.

```bash
./gradlew-curl :common:test
git add common/src/main/java/dev/tomewisp/trace/minecraft common/src/main/resources/data common/src/test/java/dev/tomewisp/trace/minecraft common/src/main/java/dev/tomewisp/TomeWispRuntime.java common/src/main/java/dev/tomewisp/TomeWispBootstrap.java
git commit -m "feat: load and replay server data traces"
```

## Task 7: Expose replay commands on both loaders

**Files:**
- Modify: `fabric/src/main/java/dev/tomewisp/fabric/FabricDevelopmentCommands.java`
- Modify: `neoforge/src/main/java/dev/tomewisp/neoforge/NeoForgeDevelopmentCommands.java`

- [ ] **Step 1: Add the command tree to each adapter**

Under the existing operator-only `tomewisp dev` node add:

```java
literal("replay")
    .then(argument("trace", StringArgumentType.word())
        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
            runtime.traceReplay().traceIds(context.getSource().getServer()
                .getResourceManager()), builder))
        .executes(context -> {
            String id = StringArgumentType.getString(context, "trace");
            ReplayReport report = runtime.traceReplay().replay(
                context.getSource(), id);
            report.chatLines().forEach(line -> context.getSource()
                .sendSuccess(() -> Component.literal(line), false));
            return report.success() ? 1 : 0;
        }))
```

`chatLines()` emits every report line; it may use multiple chat messages but
does not truncate data.

- [ ] **Step 2: Compile and commit**

```bash
./gradlew-curl :fabric:compileJava :neoforge:compileJava
git add fabric/src/main/java/dev/tomewisp/fabric/FabricDevelopmentCommands.java neoforge/src/main/java/dev/tomewisp/neoforge/NeoForgeDevelopmentCommands.java
git commit -m "feat: expose agent trace replay commands"
```

## Task 8: Verify context completeness, headless replay, and CI

**Files:**
- Modify: `docs/development.md`
- Modify: `README.md`
- Modify: `.github/workflows/build.yml` only if a new verification task is needed.

- [ ] **Step 1: Run the full local verification**

```bash
TOMEWISP_CURL_PROXY=socks5h://127.0.0.1:7890 ./gradlew-curl clean :common:test :fabric:build :neoforge:build --max-workers=1
```

Expected: all tests pass and both loader JARs are produced.

- [ ] **Step 2: Run Fabric headless replay**

Start `:fabric:runServer --args nogui`, then execute:

```text
tomewisp dev replay platform-info
tomewisp dev replay iron-ingot-recipe
tomewisp dev replay player-context
stop
```

Expected: first two pass; player-context fails exactly with `player_required`;
server stops normally.

- [ ] **Step 3: Run NeoForge headless replay**

Start `:neoforge:runServer` with no client task and execute the same commands.
Expected results match Fabric except platform-info names NeoForge.

- [ ] **Step 4: Document and commit**

Document trace location, strict schema, replay command, no-model status,
unlimited current policy, observation metrics, and headless test commands.

```bash
git add README.md docs/development.md .github/workflows/build.yml
git commit -m "docs: document deterministic agent trace replay"
git diff --check
git status --short --branch
```

## Completion gate

- No model SDK, endpoint, key, or network model call exists.
- Tool invocation accepts immutable context and no context DTO references live
  Minecraft or loader objects.
- Complete requested registry, recipe, and player state is preserved with no
  TomeWisp-defined size limit.
- Three real knowledge tools pass fixed-context tests.
- Strict trace parsing rejects unknown schema and fields.
- Replay invokes ToolRegistry and validates exact/contains/schema expectations.
- Fabric and NeoForge headless servers replay the same bundled traces.
- Full tests, dual-loader builds, JAR inspection, and GitHub Actions pass.
