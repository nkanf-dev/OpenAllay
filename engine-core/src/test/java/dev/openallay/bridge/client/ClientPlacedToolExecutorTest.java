package dev.openallay.bridge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.bridge.protocol.RemoteToolCallPayload;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ClientPlacedToolExecutorTest {
    @Test
    void exposesOneLogicalDefinitionAndPrefersTheLocalPlacement() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("test", List.of(new InspectTool()));
        RemoteCapabilityStore capabilities = new RemoteCapabilityStore();
        capabilities.replace(new CapabilityPayload(
                List.of(
                        capability("test:fact"),
                        capability("unique:fact")),
                false, 0, 0, 0, ""));
        AtomicReference<RemoteToolCallPayload> sent = new AtomicReference<>();
        RemoteToolExecutor remote = new RemoteToolExecutor(
                capabilities,
                new RemoteToolExecutor.Transport() {
                    @Override public void call(RemoteToolCallPayload payload) { sent.set(payload); }
                    @Override public void cancel(
                            dev.openallay.bridge.protocol.RemoteCancelPayload payload) {}
                });
        ClientPlacedToolExecutor tools = new ClientPlacedToolExecutor(
                new LocalAgentToolExecutor(registry, new Gson()), remote);

        assertEquals(2, tools.definitions().size());
        assertEquals(1, tools.definitions().stream()
                .filter(definition -> tools.canonicalToolId(definition.name()).orElseThrow()
                        .equals("test:fact"))
                .count());
        assertTrue(tools.definitions().stream().noneMatch(
                definition -> definition.name().startsWith("server__")));

        JsonObject options = new JsonObject();
        options.addProperty("value", "local");
        var local = tools.execute(
                        "test__fact",
                        options,
                        ToolInvocationContext.developmentConsole("local"),
                        new CancellationSignal())
                .join();
        assertFalse(local.failure());
        assertEquals("client", local.normalized()
                .getAsJsonObject("value").get("placement").getAsString());
        assertEquals(null, sent.get());

        assertEquals(null, sent.get());
    }

    private static CapabilityPayload.RemoteToolCapability capability(String id) {
        return new CapabilityPayload.RemoteToolCapability(
                id, "Read " + id, "{\"type\":\"object\"}");
    }

    private static final class InspectTool
            implements Tool<InspectTool.Input, InspectTool.Output> {
        record Input(String value) {}
        record Output(String placement) {}

        private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
                "test:fact",
                "Return a local fact",
                Input.class,
                Output.class,
                ToolAccess.READ_ONLY);

        @Override public ToolDescriptor<Input, Output> descriptor() { return DESCRIPTOR; }

        @Override
        public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
            return new ToolResult.Success<>(new Output("client"));
        }
    }
}
