package dev.openallay.agent.context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.agent.tool.AgentToolResult;
import dev.openallay.json.EngineJson;
import dev.openallay.model.*;
import dev.openallay.tool.result.JsonResultProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Whole canonical budget/summary/result-projection algorithms, never a fake production tokenizer. */
public final class ContextCompactorJava8Fixture {
    private ContextCompactorJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    private static final String SUMMARY = "{\"goals\":[\"build\"],\"preferences\":[],\"completedTopics\":[],\"currentTasks\":[],\"decisions\":[],\"unresolvedQuestions\":[],\"evidenceReferences\":[]}";
    private static final ContextTokenEstimator ESTIMATOR = (prompt, messages, tools) -> {
        int count = prompt.length();
        for (ModelMessage message : messages) for (ModelContent content : message.content()) {
            if (content instanceof ModelContent.Text) count += ((ModelContent.Text) content).text().length();
            else if (content instanceof ModelContent.ToolUse) count += ((ModelContent.ToolUse) content).input().toString().length() + 20;
            else if (content instanceof ModelContent.ToolResult) count += ((ModelContent.ToolResult) content).value().toString().length() + 20;
            else if (content instanceof ModelContent.Image) count += 100;
        }
        return count;
    };
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("java8")) equal("1.8", System.getProperty("java.specification.version"));
        AtomicInteger calls = new AtomicInteger(); List<ModelRequest> observed = new ArrayList<>();
        ModelClient summaryModel = (request, events, cancellation) -> {
            calls.incrementAndGet(); observed.add(request);
            check(ESTIMATOR.estimate(request.systemPrompt(), request.messages(), request.tools()) <= 10_000, "actual summary admission");
            events.accept(new ModelEvent.UsageUpdate(new ModelUsage(7, 3, 0)));
            return CompletableFuture.completedFuture(new ModelTurn("test", "summary-model", Arrays.<ModelContent>asList(new ModelContent.Text(SUMMARY)), "end", new ModelUsage(7, 3, 0)));
        };
        ContextCompactor compactor = compactor(summaryModel, new ContextBudget(10_000, 512));
        List<ModelMessage> history = Arrays.asList(ModelMessage.userText(repeat("a", 600)), assistant(repeat("b", 600)), ModelMessage.userText("current"));
        ContextCompactor.Result manual = compactor.compactManually(messages -> "system", history, 2,
                Collections.<ModelToolDefinition>emptyList(), "actor:manual", new CancellationSignal()).join();
        check(manual.successful(), "manual summary succeeds"); equal(ContextProjection.Kind.SUMMARIZED, manual.projection().kind());
        equal(1, calls.get()); equal("actor:manual", observed.get(0).sessionKey()); check(!observed.get(0).stream(), "summary not streamed");
        equal(history.get(2), manual.projection().messages().get(manual.projection().messages().size() - 1));
        equal(2, manual.checkpoint().sourceToIndexExclusive());
        equal(ContextSourceHash.compute(EngineJson.create(), history.subList(0, 2)), manual.checkpoint().sourceHash());
        check(compactor.matches(manual.checkpoint(), history), "checkpoint matches source");
        check(!compactor.matches(manual.checkpoint(), Arrays.asList(ModelMessage.userText("changed"), history.get(1), history.get(2))), "checkpoint rejects changed source");
        Optional<ContextProjection> reused = compactor.reuse(manual.checkpoint(), "system", history, 2, Collections.<ModelToolDefinition>emptyList());
        check(reused.isPresent(), "successful checkpoint reusable");
        System.out.println("summary=" + manual.checkpoint().summary());
        System.out.println("summarySourceHash=" + manual.checkpoint().sourceHash());
        System.out.println("manualKind=" + manual.projection().kind() + "/" + manual.projection().messages().size());
        ContextCompactor.Result original = compactor.compact("system", Arrays.asList(ModelMessage.userText("short")), 1,
                Collections.<ModelToolDefinition>emptyList(), false, "actor:short", new CancellationSignal()).join();
        equal(ContextProjection.Kind.ORIGINAL, original.projection().kind()); equal(null, original.checkpoint());
        JsonObject normalized = new JsonObject(); normalized.addProperty("status", "success"); normalized.addProperty("toolId", "test:tool");
        JsonObject largeValue = new JsonObject(); largeValue.addProperty("count", 3); largeValue.addProperty("payload", repeat("x", 5000)); normalized.add("value", largeValue);
        AgentToolResult toolResult = new AgentToolResult("test:tool", normalized, false);
        String before = toolResult.normalized().toString();
        String projected = toolResult.modelValue(512).toString();
        equal(before, toolResult.normalized().toString()); check(JsonResultProjection.serializedBytes(toolResult.modelValue(512)) <= 512, "projection fits byte limit");
        System.out.println("projected=" + projected);
        JsonResultProjection.Projection projection = JsonResultProjection.project(largeValue, (String)null, "source note", 512);
        check(projection.modelText().contains("preview"), "losslabelled preview"); check(!projection.complete(), "large projection incomplete");
        System.out.println("projection=" + projection.modelText().replace("\n", "\\n"));
        equal(1L, JsonResultProjection.cardinality(largeValue.get("count")));
        JsonArray unicode = new JsonArray(); unicode.add(new JsonPrimitive("Ω中\n"));
        equal(unicode.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length, (int)JsonResultProjection.serializedBytes(unicode));
        failure("projectionTiny", () -> JsonResultProjection.project(largeValue, (String)null, "", 10));
        failure("projectionEmpty", () -> new ContextProjection(Collections.<ModelMessage>emptyList(), ContextProjection.Kind.ORIGINAL, 1));
        ModelClient malformedModel = (request, events, cancellation) -> CompletableFuture.completedFuture(new ModelTurn("test", "model", Arrays.<ModelContent>asList(new ModelContent.Text("not-json")), "end", ModelUsage.empty()));
        ContextCompactor.Result malformed = compactor(malformedModel, new ContextBudget(10_000, 512)).compactManually(messages -> "system", history, 2,
                Collections.<ModelToolDefinition>emptyList(), "actor:malformed", new CancellationSignal()).join();
        check(!malformed.successful(), "malformed summary rejected"); equal("summary_malformed", malformed.failureCode());
        System.out.println("malformed=" + malformed.failureCode());
        CompletableFuture<ModelTurn> raw = new CompletableFuture<>();
        ContextCompactor cancelCompactor = compactor((request, events, cancellation) -> raw, new ContextBudget(10_000, 512));
        CancellationSignal cancellation = new CancellationSignal();
        CompletableFuture<ContextCompactor.Result> running = cancelCompactor.compactManually(messages -> "system", history, 2,
                Collections.<ModelToolDefinition>emptyList(), "actor:cancel", cancellation);
        check(!running.isDone(), "summary running"); check(cancellation.cancel(), "canceled"); check(running.isCompletedExceptionally(), "cancel visible");
        raw.complete(new ModelTurn("test", "model", Arrays.<ModelContent>asList(new ModelContent.Text(SUMMARY)), "end", ModelUsage.empty()));
        check(running.isCompletedExceptionally(), "late summary cannot publish checkpoint");
        System.out.println("cancel=agent_cancelled");
        // Observing client real receipt path: UUIDs stay internal and are not cross-VM oracle data.
        AtomicInteger starts = new AtomicInteger(), receipts = new AtomicInteger();
        ModelClient observedModel = ObservingModelClient.observe(summaryModel, "captured-model");
        observedModel.complete(new ModelRequest("system", Arrays.asList(ModelMessage.userText("question")), Collections.<ModelToolDefinition>emptyList(), false, "actor:observe"), event -> {
            if (event instanceof ModelEvent.UsageStarted) starts.incrementAndGet();
            if (event instanceof ModelEvent.UsageObserved) receipts.incrementAndGet();
        }, new CancellationSignal()).join();
        equal(1, starts.get()); equal(1, receipts.get());
        System.out.println("usageReceipts=1/1");
        System.out.println("PASS full canonical context compactor and result projection");
    }
    private static ContextCompactor compactor(ModelClient model, ContextBudget budget) {
        return new ContextCompactor(model, EngineJson.create(), ESTIMATOR, budget, "captured-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }
    private static ModelMessage assistant(String text) { return new ModelMessage(ModelRole.ASSISTANT, Arrays.<ModelContent>asList(new ModelContent.Text(text))); }
    private static String repeat(String value, int count) { return dev.openallay.util.Java8Strings.repeat(value, count); }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | IllegalStateException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
