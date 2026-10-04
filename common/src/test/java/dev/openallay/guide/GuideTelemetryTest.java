package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.bridge.protocol.ServerAgentEventCodec;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelUsage;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideTelemetryTest {
    @Test
    void unknownImageCostPreservesTextEstimateAndActualFeeReceiptsWithoutClaimingTotal() {
        Local local = new Local();
        GuideService service = service(local, new Remote());
        UUID request = ask(service);
        var capturedBudget = new ContextBudget(10_000, 1_000);
        local.estimates.put("main", new GuideContextEstimate(request, 321, capturedBudget, "model-a",
                dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN));
        local.send(request, new AgentEvent.ModelProgress(new ModelEvent.AttemptStarted(1, null)));
        GuideContextEstimate estimate = service.telemetry().context();
        assertNotNull(estimate);
        assertEquals(321, estimate.estimatedTokens());
        assertEquals(capturedBudget, estimate.budget());
        assertEquals(dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN,
                estimate.imageAccounting());
        UUID call = UUID.randomUUID();
        local.send(request, new AgentEvent.ModelUsageStarted(call, "model-a"));
        local.send(request, new AgentEvent.ModelUsageObserved(call, "model-a", new ModelUsage(500, 40, 50)));
        assertEquals(1, service.telemetry().sessionUsage().actualCalls());
        assertEquals(500, service.telemetry().sessionUsage().inputTokens());
        assertEquals(40, service.telemetry().sessionUsage().outputTokens());
        assertEquals(50, service.telemetry().sessionUsage().cacheReadTokens());
        assertEquals(dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN,
                service.telemetry().context().imageAccounting(),
                "provider billing facts do not retroactively invent an offline image estimate");
    }

    @Test
    void imageUnknownLabelsTextOnlyWhileOnlyKnownAccountingCanRenderTheTotalWindowBar() throws Exception {
        java.nio.file.Path current = java.nio.file.Path.of("").toAbsolutePath().normalize();
        java.nio.file.Path root = current.getFileName() != null
                && current.getFileName().toString().equals("common") ? current.getParent() : current;
        String screen = java.nio.file.Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java"));
        String refresh = screen.substring(screen.indexOf("private void refreshTelemetry()"),
                screen.indexOf("static String compactTokens"));
        String render = screen.substring(screen.indexOf("private void renderTelemetry("),
                screen.indexOf("private boolean telemetryImageBarEligible()"));
        String eligible = screen.substring(screen.indexOf("private boolean telemetryImageBarEligible()"),
                screen.indexOf("private void renderProgress("));
        assertTrue(refresh.contains("context.imageAccounting()"));
        assertTrue(refresh.contains("screen.openallay.telemetry.text_estimate"));
        assertTrue(refresh.contains("screen.openallay.telemetry.image_unknown"));
        // Compact bubbles retain the text-only label. The restored native card admits both
        // the total-window track and its fill only under the same known-accounting predicate.
        assertTrue(render.contains("graphics.text(font, telemetryCompact"));
        assertTrue(render.contains("graphics.text(font, telemetryContext"));
        assertTrue(refresh.contains("telemetryCompact = Component.translatable(\"screen.openallay.telemetry.compact\", occupancy, cache, cost)"));
        int knownGuard = render.indexOf("if (telemetryImageBarEligible())");
        int barTrack = render.indexOf("graphics.fill(x, y + 25");
        int ratio = render.indexOf("double ratio");
        assertTrue(knownGuard >= 0 && barTrack > knownGuard && ratio > barTrack);
        assertTrue(eligible.contains("telemetry.context() != null && telemetry.context().budget() != null"));
        assertTrue(eligible.contains("telemetry.context().imageAccounting()"));
        assertTrue(eligible.contains("!= dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN"));
        assertTrue(render.contains("/ telemetry.context().budget().contextWindowTokens()"));
        assertTrue(refresh.contains("compactTokens(context.budget().contextWindowTokens())"));
        assertTrue(refresh.contains("context.budget().inputTokens()"));
        assertTrue(refresh.contains("context.budget().reservedTokens()"));
        assertTrue(refresh.contains("context.budget().maxOutputTokens()"));
        assertFalse(refresh.contains("contextSpec("), "native telemetry must reuse captured estimates");
        for (String locale : List.of("en_us", "zh_cn")) {
            var labels = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve(
                    "common/src/main/resources/assets/openallay/lang/" + locale + ".json"))).getAsJsonObject();
            assertTrue(labels.get("screen.openallay.telemetry.text_estimate").getAsString().contains("%s"));
            assertFalse(labels.get("screen.openallay.telemetry.image_unknown").getAsString().isBlank());
        }
    }

    @Test
    void selectedSessionRequestAndModelOwnCachedContextAndCounts() {
        Local local = new Local();
        GuideService service = service(local, new Remote());
        assertUnknown(service);
        UUID first = ask(service);
        assertNull(service.telemetry().context());
        local.estimates.put("main", new GuideContextEstimate(first, 321, new ContextBudget(10_000, 1_000), "model-a"));
        local.send(first, new AgentEvent.ModelProgress(new ModelEvent.AttemptStarted(1, null)));
        local.send(first, new AgentEvent.ModelUsageObserved(UUID.randomUUID(), "model-a", new ModelUsage(20, 4, 0)));
        local.send(first, new AgentEvent.ModelProgress(new ModelEvent.MessageComplete("stop")));
        local.send(first, new AgentEvent.FinalText("answer"));
        assertEquals(321, service.telemetry().context().estimatedTokens());
        assertEquals(8_000, service.telemetry().context().budget().inputTokens());
        assertEquals(20, service.telemetry().requestUsage().inputTokens());
        assertSame(service.telemetry(), service.telemetry(), "reads reuse the immutable cache");
        assertEquals(0, local.contextSpecReads, "the footer must not tokenize prompt/tools via contextSpec");

        service.selectSession("other").join();
        assertUnknown(service);
        service.selectSession("main").join();
        assertEquals(first, service.telemetry().requestId());
        service.setModelSelection(GuideModelSelection.client("other")).join();
        assertEquals(20, service.telemetry().sessionUsage().inputTokens());
        assertNull(service.telemetry().context());
        service.setModelSelection(GuideModelSelection.client("default")).join();
        local.model = "replacement-model";
        service.refreshCapabilities().join();
        assertEquals(20, service.telemetry().sessionUsage().inputTokens());
        assertNull(service.telemetry().context());
        local.model = "model-a";
        service.refreshCapabilities().join();
        UUID next = ask(service);
        assertNotEquals(first, next);
        assertNull(service.telemetry().context(), "prior request estimate must not leak into a new request");
        assertFalse(service.telemetry().requestUsage().known());
        assertEquals(20, service.telemetry().sessionUsage().inputTokens());
        service.cancel().join();
        local.send(next, new AgentEvent.ModelProgress(new ModelEvent.UsageUpdate(new ModelUsage(999, 99, 0))));
        assertFalse(service.telemetry().requestUsage().known(), "late ordinary progress cannot reopen or bill");
        UUID lateCall = UUID.randomUUID();
        local.send(next, new AgentEvent.ModelUsageObserved(lateCall, "model-a", new ModelUsage(7, 2, 0)));
        local.send(next, new AgentEvent.ModelUsageObserved(lateCall, "model-a", new ModelUsage(7, 2, 0)));
        assertEquals(27, service.telemetry().sessionUsage().inputTokens());
        assertTrue(service.snapshot().sessions().stream().filter(s -> s.sessionId().equals("main"))
                .findFirst().orElseThrow().requests().getLast().terminal());
        CompletableFuture<Void> disconnected = service.disconnect();
        assertFalse(disconnected.isDone(), "numeric late receipts do not release the actual endpoint");
        local.releaseCancelled(next);
        disconnected.join();
        assertUnknown(service);
    }

    @Test
    void serverUsesExistingCountsWireEventsWithoutInventingContextOrPrice() {
        Remote remote = new Remote();
        GuideService service = service(null, remote);
        service.setModelSelection(GuideModelSelection.server()).join();
        UUID id = ask(service);
        ServerAgentEventCodec codec = new ServerAgentEventCodec(new Gson());
        for (ModelEvent event : List.<ModelEvent>of(
                new ModelEvent.UsageUpdate(new ModelUsage(0, 0, 0)),
                new ModelEvent.MessageComplete("stop"),
                new ModelEvent.MessageComplete("stop"))) {
            var encoded = codec.encode(id, new AgentEvent.ModelProgress(event));
            remote.events.accept(codec.decode(encoded, id));
        }
        UUID call = UUID.randomUUID();
        var usage = codec.encode(id, new AgentEvent.ModelUsageObserved(call, "unknown-model", new ModelUsage(0, 0, 0)));
        remote.events.accept(codec.decode(usage, id));
        remote.events.accept(codec.decode(usage, id));
        remote.events.accept(new AgentEvent.FinalText("answer"));
        remote.events.accept(new AgentEvent.RequestReleased());
        assertNull(service.telemetry().context());
        assertTrue(service.telemetry().requestUsage().known());
        assertEquals(1, service.telemetry().requestUsage().reportedCalls());
        assertEquals(0, service.telemetry().requestUsage().inputTokens());
        assertNull(service.telemetry().requestUsage().estimatedUsd());
        service.clearSelectedSession().join();
        assertUnknown(service);
    }

    @Test
    void endpointEventsAreAppliedOnlyOnTheClientDispatcher() {
        java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();
        Local local = new Local();
        GuideService service = new GuideService(UUID.randomUUID(), local, new Remote(),
                (capabilities, id) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(id)),
                queue::add, Clock.systemUTC(), new Gson());
        var submitted = service.ask("question");
        while (!queue.isEmpty()) queue.removeFirst().run();
        UUID id = ((ToolResult.Success<UUID>) submitted.join()).value();
        local.send(id, new AgentEvent.ModelUsageObserved(UUID.randomUUID(), "model-a", new ModelUsage(7, 2, 0)));
        local.send(id, new AgentEvent.ModelProgress(new ModelEvent.MessageComplete("stop")));
        assertFalse(service.telemetry().requestUsage().known());
        while (!queue.isEmpty()) queue.removeFirst().run();
        assertEquals(7, service.telemetry().requestUsage().inputTokens());
    }

    private static void assertUnknown(GuideService service) {
        assertNull(service.telemetry().context());
        assertFalse(service.telemetry().requestUsage().known());
        assertEquals(0, service.telemetry().requestUsage().actualCalls());
    }
    private static UUID ask(GuideService service) {
        return ((ToolResult.Success<UUID>) service.ask("question").join()).value();
    }
    private static GuideService service(Local local, Remote remote) {
        return new GuideService(UUID.randomUUID(), local, remote,
                (capabilities, id) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(id)),
                Runnable::run, Clock.systemUTC(), new Gson());
    }
    private static final class Local implements GuideLocalEndpoint {
        private final Map<UUID, Consumer<AgentEvent>> events = new HashMap<>();
        private final Map<UUID, CompletableFuture<AgentResult>> completions = new HashMap<>();
        private final Map<String, GuideContextEstimate> estimates = new HashMap<>();
        private String model = "model-a";
        private int contextSpecReads;
        @Override public List<GuideClientModelProfile> profiles() {
            return List.of(new GuideClientModelProfile("default", "Default", true, true, model, null),
                    new GuideClientModelProfile("other", "Other", true, true, "model-b", null));
        }
        @Override public Optional<GuideContextSpec> contextSpec(String profile) {
            contextSpecReads++;
            throw new AssertionError("Telemetry must use the captured request budget");
        }
        @Override public Optional<GuideContextEstimate> contextEstimate(String profile, UUID actor, String session) {
            return Optional.ofNullable(estimates.get(session));
        }
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID id, String question,
                ToolInvocationContext context, Consumer<AgentEvent> sink) {
            events.put(id, sink);
            sink.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            CompletableFuture<AgentResult> completion = new CompletableFuture<>();
            completions.put(id, completion);
            return completion;
        }
        @Override public boolean cancel(UUID actor, String session) { return true; }
        @Override public void clearSession(UUID actor, String session) { estimates.remove(session); }
        @Override public void clearActor(UUID actor) { estimates.clear(); }
        void releaseCancelled(UUID id) {
            completions.get(id).complete(new AgentResult(AgentState.CANCELLED, null,
                    "agent_cancelled", "Agent request was cancelled", null));
        }
        void send(UUID id, AgentEvent event) {
            events.get(id).accept(event);
            if (event instanceof AgentEvent.FinalText text) {
                completions.get(id).complete(new AgentResult(AgentState.COMPLETED, text.text(), null, null, null));
            }
        }
    }
    private static final class Remote implements GuideRemoteEndpoint {
        private Consumer<AgentEvent> events;
        @Override public boolean serverModelAvailable() { return true; }
        @Override public boolean serverToolsAvailable() { return false; }
        @Override public boolean ask(UUID id, String session, String question, Consumer<AgentEvent> sink) {
            events = sink;
            return true;
        }
        @Override public boolean cancel(UUID id) { return true; }
        @Override public void disconnect() { }
    }
}
