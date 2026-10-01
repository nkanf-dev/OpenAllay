package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.GuideContextSpec;
import dev.openallay.guide.GuideLocalEndpoint;
import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRemoteEndpoint;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.tool.ToolResult;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ServerModelRoutingBenchmarkTest {
    private static final UUID ACTOR =
            UUID.fromString("bb4664ae-15e9-4f79-a6b3-ce35dbb95138");

    @Test
    void serverRoutingCaseRunsThroughGuideServiceInsteadOfJavascriptFixture() {
        BenchmarkCorpus corpus = corpus();
        BenchmarkSelector.Selection selection = new BenchmarkSelector().select(
                corpus,
                "server-model-routing",
                Set.of("server-model"),
                Set.of("server-model-routing"),
                3);

        BenchmarkReport report = new BenchmarkRunner(new BenchmarkVerifier()).run(
                selection.selected(),
                (testCase, attempt) -> routeOneRequest(testCase, attempt));

        BenchmarkReport.CaseReport routing = report.cases().getFirst();
        assertEquals("server-model-routing", routing.caseId());
        assertEquals(3, routing.successes());
        assertEquals(1.0D, routing.successProbability());
        assertEquals(1.0D, routing.medianModelTurns());
        assertEquals(0.0D, routing.medianToolCalls());
    }

    private static BenchmarkOutcome routeOneRequest(BenchmarkCase testCase, int attempt) {
        FixtureRemote remote = new FixtureRemote("server/model-a");
        GuideService service = new GuideService(
                ACTOR,
                new FixtureLocal(),
                remote,
                (capabilities, correlationId) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlationId)),
                Runnable::run,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new Gson());

        success(service.setModelSelection(GuideModelSelection.server()).join());
        UUID requestId = success(service.ask(testCase.prompt()).join());
        GuideRequestSnapshot captured = request(service, requestId);
        remote.complete(requestId, "server answer " + attempt);
        GuideSessionSnapshot session = service.snapshot().sessions().getFirst();
        boolean retained = session.sessionId().equals("main")
                && captured.modelSelection().equals(GuideModelSelection.server())
                && session.messages().stream()
                        .map(GuideMessage::text)
                        .toList()
                        .equals(List.of(testCase.prompt(), "server answer " + attempt));

        return new BenchmarkOutcome(
                JsonParser.parseString("""
                        {"sessionId":"%s","selection":"%s","canonicalModelId":"%s"}
                        """.formatted(
                                session.sessionId(),
                                captured.modelSelection().kind(),
                                remote.canonicalModelId())),
                retained
                        ? List.of("server-model:" + remote.canonicalModelId())
                        : List.of(),
                new BenchmarkMetrics(
                        retained,
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        retained ? "completed" : "routing_invariant_failed"));
    }

    private static BenchmarkCorpus corpus() {
        var input = ServerModelRoutingBenchmarkTest.class.getClassLoader()
                .getResourceAsStream("data/openallay/benchmarks/core.json");
        if (input == null) {
            throw new IllegalStateException("Bundled benchmark corpus is unavailable");
        }
        return new BenchmarkCorpusCodec().decode(
                new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private static GuideRequestSnapshot request(GuideService service, UUID requestId) {
        return service.snapshot().sessions().getFirst().requests().stream()
                .filter(request -> request.requestId().equals(requestId))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static final class FixtureRemote implements GuideRemoteEndpoint {
        private final String canonicalModelId;
        private final Map<UUID, Consumer<AgentEvent>> requests = new LinkedHashMap<>();

        private FixtureRemote(String canonicalModelId) {
            this.canonicalModelId = canonicalModelId;
        }

        private String canonicalModelId() {
            return canonicalModelId;
        }

        @Override
        public boolean serverModelAvailable() {
            return true;
        }

        @Override
        public boolean serverToolsAvailable() {
            return true;
        }

        @Override
        public Optional<GuideContextSpec> contextSpec() {
            return Optional.of(new GuideContextSpec(
                    new ContextBudget(128_000, 4_096),
                    4_096,
                    canonicalModelId));
        }

        @Override
        public boolean ask(
                UUID requestId,
                String sessionId,
                String question,
                Consumer<AgentEvent> events) {
            requests.put(requestId, events);
            events.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            return true;
        }

        @Override
        public boolean cancel(UUID requestId) {
            return true;
        }

        @Override
        public void disconnect() {}

        private void complete(UUID requestId, String answer) {
            requests.get(requestId).accept(new AgentEvent.FinalText(answer));
        }
    }

    private static final class FixtureLocal implements GuideLocalEndpoint {
        @Override
        public Set<ContextCapability> requiredContext() {
            return Set.of();
        }

        @Override
        public CompletableFuture<AgentResult> ask(
                UUID actor,
                String sessionId,
                UUID requestId,
                String question,
                ToolInvocationContext context,
                Consumer<AgentEvent> events) {
            return CompletableFuture.failedFuture(
                    new AssertionError("server routing benchmark must not use the local model"));
        }

        @Override
        public boolean cancel(UUID actor, String sessionId) {
            return true;
        }

        @Override
        public void clearSession(UUID actor, String sessionId) {}

        @Override
        public void clearActor(UUID actor) {}
    }
}
