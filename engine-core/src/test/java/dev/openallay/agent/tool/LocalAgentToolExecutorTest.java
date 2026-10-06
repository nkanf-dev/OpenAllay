package dev.openallay.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class LocalAgentToolExecutorTest {
    record Input(int value) {}
    record Output(int fact) {}

    record VisualOutput(List<dev.openallay.model.image.ImageReference> images, String fact)
            implements ModelImageToolOutput {}

    @Test
    void extractsTypedVisualOutputWithoutInferringImagesFromOrdinaryJson() {
        var image = new dev.openallay.model.image.ImageReference("a".repeat(64), "image/png", 2, 2, 12);
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(new Tool<Input, VisualOutput>() {
            @Override public ToolDescriptor<Input, VisualOutput> descriptor() {
                return new ToolDescriptor<>("test:visual", "Visual fact", Input.class, VisualOutput.class, ToolAccess.READ_ONLY);
            }
            @Override public ToolResult<VisualOutput> invoke(ToolInvocationContext context, Input input) {
                return new ToolResult.Success<>(new VisualOutput(List.of(image), "captured"));
            }
        }));
        var result = new LocalAgentToolExecutor(registry, dev.openallay.json.EngineJson.create()).execute("test:visual",
                dev.openallay.json.JsonTrees.parse("{\"value\":1}").getAsJsonObject(),
                ToolInvocationContext.developmentConsole("visual"), new CancellationSignal()).join();
        assertFalse(result.failure());
        assertEquals(List.of(image), result.images());
        assertEquals("captured", result.normalized().getAsJsonObject("value").get("fact").getAsString());
    }


    @Test
    void exposesSchemasAndInvokesTheRealRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(new Tool<Input, Output>() {
            private final ToolDescriptor<Input, Output> descriptor = new ToolDescriptor<>(
                    "test:fact",
                    "Return a fact",
                    Input.class,
                    Output.class,
                    ToolAccess.READ_ONLY,
                    Set.of(ContextCapability.RECIPES));

            @Override public ToolDescriptor<Input, Output> descriptor() { return descriptor; }
            @Override public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
                return new ToolResult.Success<>(new Output(input.value()));
            }
        }));
        LocalAgentToolExecutor executor = new LocalAgentToolExecutor(registry, dev.openallay.json.EngineJson.create());

        assertEquals(Set.of(ContextCapability.RECIPES), executor.requiredContext());
        assertEquals("test__fact", executor.definitions().getFirst().name());
        AgentToolResult result = executor.execute(
                        "test__fact",
                        dev.openallay.json.JsonTrees.parse("{\"value\":42}").getAsJsonObject(),
                        ToolInvocationContext.developmentConsole("test"),
                        new CancellationSignal())
                .join();
        assertFalse(result.failure());
        assertEquals(42, result.normalized().getAsJsonObject("value").get("fact").getAsInt());

        AgentToolResult canonicalAlias = executor.execute(
                        "test:fact",
                        dev.openallay.json.JsonTrees.parse("{\"value\":43}").getAsJsonObject(),
                        ToolInvocationContext.developmentConsole("test"),
                        new CancellationSignal())
                .join();
        assertFalse(canonicalAlias.failure());
        assertEquals("test:fact", canonicalAlias.toolId());
        assertEquals(43, canonicalAlias.normalized()
                .getAsJsonObject("value").get("fact").getAsInt());

        AgentToolResult caseFoldedAlias = executor.execute(
                        "test__FACT",
                        dev.openallay.json.JsonTrees.parse("{\"value\":44}").getAsJsonObject(),
                        ToolInvocationContext.developmentConsole("test"),
                        new CancellationSignal())
                .join();
        assertFalse(caseFoldedAlias.failure());
        assertEquals("test:fact", caseFoldedAlias.toolId());
        assertEquals(44, caseFoldedAlias.normalized()
                .getAsJsonObject("value").get("fact").getAsInt());
    }

    @Test
    void returnsNormalizedInvalidArguments() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(new Tool<Input, Output>() {
            private final ToolDescriptor<Input, Output> descriptor = new ToolDescriptor<>(
                    "test:fact", "Return a fact", Input.class, Output.class, ToolAccess.READ_ONLY);
            @Override public ToolDescriptor<Input, Output> descriptor() { return descriptor; }
            @Override public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
                return new ToolResult.Success<>(new Output(input.value()));
            }
        }));
        AgentToolResult result = new LocalAgentToolExecutor(registry, dev.openallay.json.EngineJson.create())
                .execute(
                        "test__fact",
                        dev.openallay.json.JsonTrees.parse("{\"value\":{}}").getAsJsonObject(),
                        ToolInvocationContext.developmentConsole("test"),
                        new CancellationSignal())
                .join();
        assertTrue(result.failure());
        assertEquals("invalid_arguments", result.normalized().get("code").getAsString());
    }

    @Test
    void filteredCatalogDoesNotExposeContextOrExecuteDisabledTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("test-provider", List.of(new Tool<Input, Output>() {
            private final ToolDescriptor<Input, Output> descriptor = new ToolDescriptor<>(
                    "test:fact",
                    "Return a fact",
                    Input.class,
                    Output.class,
                    ToolAccess.READ_ONLY,
                    Set.of(ContextCapability.RECIPES));

            @Override public ToolDescriptor<Input, Output> descriptor() { return descriptor; }
            @Override public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
                return new ToolResult.Success<>(new Output(input.value()));
            }
        }));
        ToolRuntimeCatalog catalog = ToolRuntimeCatalog.from(
                registry.registrations(), Set.of("test:fact"));
        LocalAgentToolExecutor executor = new LocalAgentToolExecutor(catalog, dev.openallay.json.EngineJson.create());

        assertTrue(executor.definitions().isEmpty());
        assertTrue(executor.requiredContext().isEmpty());
        AgentToolResult result = executor.execute(
                        "test__fact",
                        dev.openallay.json.JsonTrees.parse("{\"value\":42}").getAsJsonObject(),
                        ToolInvocationContext.developmentConsole("test"),
                        new CancellationSignal())
                .join();
        assertTrue(result.failure());
        assertEquals("tool_unavailable", result.normalized().get("code").getAsString());
        assertEquals("test:fact", result.toolId());
        assertEquals(1, registry.descriptors().size());

        AgentToolResult canonicalAlias = executor.execute(
                        "test:fact",
                        dev.openallay.json.JsonTrees.parse("{\"value\":42}").getAsJsonObject(),
                        ToolInvocationContext.developmentConsole("test"),
                        new CancellationSignal())
                .join();
        assertTrue(canonicalAlias.failure());
        assertEquals("tool_unavailable", canonicalAlias.normalized().get("code").getAsString());
    }

    @Test
    void waitsForAnAsynchronousToolBeforeNormalizingItsResult() {
        ToolRegistry registry = new ToolRegistry();
        CompletableFuture<ToolResult<Output>> pending = new CompletableFuture<>();
        registry.register("test", List.of(new Tool<Input, Output>() {
            private final ToolDescriptor<Input, Output> descriptor = new ToolDescriptor<>(
                    "test:async", "Async fact", Input.class, Output.class, ToolAccess.READ_ONLY);
            @Override public ToolDescriptor<Input, Output> descriptor() { return descriptor; }
            @Override public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
                throw new AssertionError("synchronous path must not run");
            }
            @Override
            public CompletableFuture<ToolResult<Output>> invokeAsync(
                    ToolInvocationContext context,
                    Input input,
                    CancellationSignal cancellation) {
                return pending;
            }
        }));
        CompletableFuture<AgentToolResult> execution = new LocalAgentToolExecutor(
                registry, dev.openallay.json.EngineJson.create()).execute(
                        "test__async",
                        dev.openallay.json.JsonTrees.parse("{\"value\":7}").getAsJsonObject(),
                        ToolInvocationContext.developmentConsole("test"),
                        new CancellationSignal());

        assertFalse(execution.isDone());
        pending.complete(new ToolResult.Success<>(new Output(7)));
        assertEquals(7, execution.join().normalized()
                .getAsJsonObject("value").get("fact").getAsInt());
    }
}
