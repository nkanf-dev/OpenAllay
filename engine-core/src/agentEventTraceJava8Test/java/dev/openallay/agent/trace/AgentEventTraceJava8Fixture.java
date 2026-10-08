package dev.openallay.agent.trace;

import com.google.gson.JsonObject;
import dev.openallay.agent.*;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.json.EngineJson;
import dev.openallay.model.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Real closed events, usage receipt fence and trace stores; fixed constructed timestamps. */
public final class AgentEventTraceJava8Fixture {
    private AgentEventTraceJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("java8")) equal("1.8", System.getProperty("java.specification.version"));
        UUID requestId = new UUID(0, 1), actorId = new UUID(0, 2), callId = new UUID(0, 3);
        JsonObject arguments = new JsonObject(); arguments.addProperty("x", 1);
        AgentEvent.ToolStarted started = new AgentEvent.ToolStarted("invocation", "test:tool", arguments, Collections.emptyList());
        arguments.addProperty("changed", true); check(!started.arguments().has("changed"), "tool start constructor copy");
        started.arguments().addProperty("later", true); check(!started.arguments().has("later"), "tool start accessor copy");
        AgentEvent.ToolCompleted completed = new AgentEvent.ToolCompleted("invocation", "test:tool", false, arguments);
        completed.normalized().addProperty("later", true); check(!completed.normalized().has("later"), "tool complete accessor copy");
        AgentEvent.ModelProgress progress = new AgentEvent.ModelProgress(new ModelEvent.ReasoningDelta("private"));
        equal("", ((ModelEvent.ReasoningDelta)progress.event()).text());
        ModelCallReceipts receipts = new ModelCallReceipts(); AtomicInteger handed = new AtomicInteger();
        receipts.accept(new AgentEvent.ModelUsageStarted(callId, "provider/model"), event -> handed.incrementAndGet());
        CompletableFuture<String> engine = CompletableFuture.completedFuture("answer");
        CompletableFuture<String> fence = receipts.after(engine); check(!fence.isDone(), "usage receipt fences completion");
        receipts.accept(new AgentEvent.ModelUsageObserved(callId, "provider/model", new ModelUsage(2, 1, 0)), event -> handed.incrementAndGet());
        equal("answer", fence.join()); equal(2, handed.get());
        JsonObject payload = new JsonObject(); payload.addProperty("status", "observed");
        LiveTraceEvent event = new LiveTraceEvent("tool", 7, payload);
        payload.addProperty("changed", true); check(!event.payload().getAsJsonObject().has("changed"), "trace constructor copy");
        event.payload().getAsJsonObject().addProperty("later", true); check(!event.payload().getAsJsonObject().has("later"), "trace accessor copy");
        LiveAgentTrace trace = new LiveAgentTrace(requestId, actorId, "session", Instant.EPOCH, Instant.ofEpochSecond(5),
                AgentState.COMPLETED, Arrays.asList(event), "answer", null);
        AgentResult result = new AgentResult(AgentState.COMPLETED, "answer", null, null, trace);
        check(result.successful(), "completed result success");
        int traceHash = 0;
        for (Object field : new Object[] {trace.requestId(), trace.actorId(), trace.sessionId(), trace.startedAt(), trace.completedAt(), trace.finalState(), trace.events(), trace.finalText(), trace.errorCode()}) traceHash = 31 * traceHash + java.util.Objects.hashCode(field);
        equal(traceHash, trace.hashCode());
        equal(trace, EngineJson.create().fromJson(EngineJson.create().toJson(trace), LiveAgentTrace.class));
        String encoded = new LiveTraceJson().encode(trace); System.out.println("trace=" + encoded.replace("\n", "\\n"));
        LiveTraceStore memory = new LiveTraceStore(null); memory.record(trace); equal(trace, memory.find(requestId).get()); equal(Arrays.asList(requestId), memory.ids());
        Path directory = Files.createTempDirectory("openallay-agent-trace-fixture-");
        try {
            LiveTraceStore store = new LiveTraceStore(directory); store.record(trace);
            equal(encoded, new String(Files.readAllBytes(directory.resolve(requestId + ".json")), StandardCharsets.UTF_8));
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                check(!files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")), "trace temporary cleanup");
            }
            LiveTraceStore disabled = new LiveTraceStore(directory, () -> false);
            LiveAgentTrace another = new LiveAgentTrace(new UUID(0, 4), actorId, "session", Instant.EPOCH, Instant.ofEpochSecond(5), AgentState.FAILED, Collections.emptyList(), null, "failure");
            disabled.record(another); check(!Files.exists(directory.resolve(another.requestId() + ".json")), "live persistence setting respected");
        } finally {
            try (java.util.stream.Stream<Path> files = Files.list(directory)) {
                files.forEach(path -> { try { Files.delete(path); } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); } });
            }
            Files.delete(directory);
        }
        AgentRequest request = new AgentRequest(requestId, actorId, "session", "question", "prompt", ToolInvocationContext.developmentConsole("context"), true);
        LiveAgentTraceRecorder recorder = new LiveAgentTraceRecorder(EngineJson.create(), request);
        recorder.modelTurn(new ModelTurn("test", "model", Arrays.<ModelContent>asList(new ModelContent.Reasoning("private", "signature"), new ModelContent.Text("visible")), "end", ModelUsage.empty()));
        recorder.modelRequest(new ModelRequest("system", Arrays.asList(ModelMessage.userText("question")), Collections.emptyList(), false, "actor:session"));
        LiveAgentTrace recorded = recorder.finish(AgentState.COMPLETED, "done", null);
        check(!EngineJson.create().toJson(recorded).contains("signature"), "trace excludes private reasoning");
        check(!EngineJson.create().toJson(recorded).contains("ImagePayloadResolver"), "trace never serializes resolver");
        equal(3, recorded.events().size());
        failure("traceNegative", () -> new LiveTraceEvent("type", -1, null));
        failure("toolIdentity", () -> new AgentEvent.ToolStarted(" ", "test:tool"));
        failure("unknownTrace", () -> memory.encoded(new UUID(0, 99)));
        // Exact known14 list is checked by class onJava8; modern sealedcompiler provides baseline.
        if (args.length == 1) {
            AtomicInteger foreignHandoff = new AtomicInteger();
            try { AgentEvent foreign = (AgentEvent) Class.forName("dev.openallay.agent.trace.ForeignAgentEventFixture").getConstructor().newInstance();
                receipts.accept(foreign, ignored -> foreignHandoff.incrementAndGet()); throw new AssertionError("foreign event admitted"); }
            catch (IncompatibleClassChangeError expected) { equal(0, foreignHandoff.get()); }
        }
        System.out.println("usageFence=2/answer");
        System.out.println("recorderEvents=3/privateReasoningExcluded/resolverExcluded");
        System.out.println("PASS canonical closed events and trace persistence");
    }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | NullPointerException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
