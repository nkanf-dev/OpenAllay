package dev.openallay.agent.context;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import dev.openallay.model.openai.OpenAiJsonCodec;
import dev.openallay.model.tokenizer.TokenizerMetadata;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ContextCompactorImageTest {
    private static final Gson GSON = dev.openallay.json.EngineJson.create();
    private static final String SUMMARY = """
            {"goals":[],"preferences":[],"completedTopics":[],"currentTasks":[],
             "decisions":[],"unresolvedQuestions":[],"evidenceReferences":[]}
            """;
    private static final ImageReference PNG =
            new ImageReference("a".repeat(64), "image/png", 32, 24, 6);
    private static final ImageReference JPEG =
            new ImageReference("b".repeat(64), "image/jpeg", 48, 32, 6);
    private static final List<ImageReference> PREFIX_IMAGES = List.of(PNG, JPEG, PNG);

    @Test
    void associatedFrameSurvivesSummaryRestoreReuseAndRepeatedSummaryWithoutRetainingFullFocus() {
        var anchor = dev.openallay.world.InputObservationFixtures.anchor(PNG);
        var requests = new ArrayList<ModelRequest>();
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            assertEquals(List.of(PNG), dev.openallay.model.image.ModelImages.occurrences(request.messages()));
            String nativeBody = new OpenAiJsonCodec(GSON).requestBody(config(ModelProtocol.OPENAI_CHAT), request);
            assertTrue(nativeBody.contains("data:image/png;base64,"));
            assertFalse(nativeBody.contains("minecraft:custom_data"));
            return CompletableFuture.completedFuture(textTurn(SUMMARY));
        };
        var compactor = new ContextCompactor(model, GSON, new CharacterFixtureEstimator(),
                new ContextBudget(20_000, 1_000), "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var original = List.of(ModelMessage.userText("old question " + "x".repeat(2_500)).withInputObservation(anchor),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("old answer " + "y".repeat(2_500)))),
                ModelMessage.userText("current question"));
        var first = compactor.compactManually(ignored -> "system", original, 2, List.of(),
                "actor:associated", new CancellationSignal(), image -> new byte[6], ignored -> {}).join();
        assertTrue(first.successful(), first.failureMessage());
        assertNotNull(first.checkpoint());
        assertTrue(first.projection().messages().stream().allMatch(message -> message.inputObservation().isEmpty()));
        assertEquals(List.of(PNG), dev.openallay.model.image.ModelImages.occurrences(first.projection().messages()));
        assertNull(first.projection().messages().getFirst().content().stream()
                .filter(ModelContent.Image.class::isInstance).map(ModelContent.Image.class::cast)
                .findFirst().orElseThrow().originToolUseId());
        assertTrue(first.projection().messages().getFirst().content().stream()
                .filter(ModelContent.Text.class::isInstance).map(ModelContent.Text.class::cast)
                .anyMatch(text -> text.text().contains(anchor.image().orElseThrow().capturedAt().toString())));
        var codec = new ModelContextCodec();
        var restoredOriginal = codec.decode(codec.encode(original));
        assertEquals(anchor, restoredOriginal.getFirst().inputObservation().orElseThrow());
        var restored = new ArrayList<>(codec.decode(codec.encode(first.projection().messages())));
        var reused = compactor.reuse(first.checkpoint(), "system", restoredOriginal, 2, List.of()).orElseThrow();
        assertEquals(List.of(PNG), dev.openallay.model.image.ModelImages.occurrences(reused.messages()));
        restored.add(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("answer " + "z".repeat(2_500)))));
        restored.add(ModelMessage.userText("next protected question"));
        var second = compactor.compactManually(ignored -> "system", restored, restored.size() - 1, List.of(),
                "actor:again", new CancellationSignal(), image -> new byte[6], ignored -> {}).join();
        assertTrue(second.successful(), second.failureMessage());
        assertNotNull(second.checkpoint());
        assertEquals(List.of(PNG), dev.openallay.model.image.ModelImages.occurrences(
                codec.decode(codec.encode(second.projection().messages()))));
        assertEquals(anchor, original.getFirst().inputObservation().orElseThrow());
    }

    @Test
    void nativeSummaryChunksAndFinalProjectionKeepActualImagesIncludingRepeatedReferences() {
        var requests = new ArrayList<ModelRequest>();
        var reads = new ArrayList<ImageReference>();
        ImagePayloadResolver resolver = image -> {
            reads.add(image);
            return new byte[(int) image.byteSize()];
        };
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            assertSame(resolver, request.images());
            assertEquals(ModelRole.USER, request.messages().getLast().role());
            // A real provider codec must resolve actual image input, not just JSON-as-text metadata.
            assertDoesNotThrow(() -> new OpenAiJsonCodec(GSON).requestBody(config(ModelProtocol.OPENAI_CHAT), request));
            return CompletableFuture.completedFuture(textTurn(SUMMARY));
        };
        var compactor = compactor(model);
        List<ModelMessage> source = source();
        assertTrue(compactor.requiresCompaction("system", source, List.of()));
        ContextCompactor.Result result = compactor.compact("system", source, 2, List.of(),
                true, "actor:images", new CancellationSignal(), resolver).join();

        assertTrue(result.successful(), result.failureMessage());
        assertEquals(ContextProjection.Kind.SUMMARIZED, result.projection().kind());
        assertEquals(2, result.checkpoint().sourceToIndexExclusive());
        assertEquals(2, requests.size(), "complete image-bearing source units require separate admitted chunks");
        assertEquals(List.of(PNG, JPEG), images(requests.getFirst().messages()));
        assertEquals(PREFIX_IMAGES, images(requests.getLast().messages()),
                "later chunks carry prior actual images as well as their own unit's images");
        assertEquals(List.of(PNG, JPEG, PNG, JPEG, PNG), reads);
        for (ModelRequest request : requests) {
            assertEquals("actor:images", request.sessionKey());
            assertFalse(request.stream());
            assertNotNull(request.maxOutputTokens());
            assertEquals(TokenizerMetadata.ImageAccounting.UNKNOWN,
                    compactor.estimator().imageAccounting(request.messages()));
            assertTrue(compactor.estimateTokens(request.systemPrompt(), request.messages(), List.of())
                    <= compactor.inputTokenBudget());
        }
        String firstSource = ((ModelContent.Text) requests.getFirst().messages()
                .getLast().content().getFirst()).text();
        assertTrue(firstSource.contains(PNG.sha256()), "source hashing and summary text still contain metadata");
        assertFalse(firstSource.contains("base64"));
        assertEquals(PREFIX_IMAGES, images(result.projection().messages()));
        assertEquals(source.getLast(), result.projection().messages().getLast());
        assertEquals(ModelRole.USER, result.projection().messages().getFirst().role());
        assertEquals(new ModelContent.Text("[OpenAllay derived conversation memory; NOT factual evidence]\n"
                        + dev.openallay.json.JsonTrees.parse(SUMMARY)),
                result.projection().messages().getFirst().content().getFirst());
        assertEquals(result.projection().messages(), ModelContextCodec.safe(result.projection().messages()));
        assertTrue(compactor.matches(result.checkpoint(), source));
    }

    @Test
    void targetedSummaryRetryKeepsScopedResolverAndTheSameNativeImageBlocks() {
        var requests = new ArrayList<ModelRequest>();
        var reads = new AtomicInteger();
        ImagePayloadResolver resolver = image -> { reads.incrementAndGet(); return new byte[6]; };
        JsonObject overlong = dev.openallay.json.JsonTrees.parse(SUMMARY).getAsJsonObject();
        overlong.getAsJsonArray("goals").add("oversized".repeat(1_000));
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            assertSame(resolver, request.images());
            assertDoesNotThrow(() -> new AnthropicJsonCodec(GSON)
                    .requestBody(config(ModelProtocol.ANTHROPIC_MESSAGES), request));
            return CompletableFuture.completedFuture(textTurn(
                    requests.size() == 1 ? overlong.toString() : SUMMARY));
        };
        var result = compactor(model).compact("system", source(), 2, List.of(),
                true, "actor:retry", new CancellationSignal(), resolver).join();

        assertTrue(result.successful(), result.failureMessage());
        assertEquals(3, requests.size());
        assertEquals(requests.getFirst().messages(), requests.get(1).messages());
        assertEquals(requests.getFirst().maxOutputTokens(), requests.get(1).maxOutputTokens());
        assertTrue(requests.get(1).systemPrompt().contains("previous memory exceeded"));
        assertEquals(List.of(PNG, JPEG), images(requests.get(1).messages()));
        assertEquals(PREFIX_IMAGES, images(result.projection().messages()));
        assertEquals(7, reads.get());
    }

    @Test
    void persistedProjectionAndRestoredCheckpointReuseRetainOldImagesForTheNextQuestion() {
        ModelClient model = (request, events, cancellation) ->
                CompletableFuture.completedFuture(textTurn(SUMMARY));
        var compactor = compactor(model);
        var source = source();
        var result = compactor.compact("system", source, 2, List.of(), true,
                "actor:restore", new CancellationSignal(), image -> new byte[6]).join();
        assertTrue(result.successful(), result.failureMessage());
        var contextCodec = new ModelContextCodec();
        var projectedRestored = contextCodec.decode(contextCodec.encode(result.projection().messages()));
        assertEquals(result.projection().messages(), projectedRestored);
        assertEquals(PREFIX_IMAGES, images(projectedRestored));

        // Checkpoint reuse hydrates image blocks from durable original prefix, never summary text.
        var checkpointCodec = new ContextCheckpointCodec();
        var checkpoint = checkpointCodec.decode(checkpointCodec.encode(result.checkpoint()));
        var originalRestored = new ArrayList<>(contextCodec.decode(contextCodec.encode(source)));
        originalRestored.add(ModelMessage.userText("What is shown in the old images?"));
        var reused = compactor.reuse(checkpoint, "system", originalRestored,
                source.size(), List.of()).orElseThrow();
        assertEquals(PREFIX_IMAGES, images(reused.messages()));
        assertEquals(originalRestored.getLast(), reused.messages().getLast());
        assertEquals(reused.messages(), ModelContextCodec.safe(reused.messages()));
        assertEquals(TokenizerMetadata.ImageAccounting.UNKNOWN,
                compactor.estimator().imageAccounting(reused.messages()));
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var reads = new ArrayList<ImageReference>();
            var request = new ModelRequest("system", reused.messages(), List.of(), false,
                    "actor:restore", null, image -> { reads.add(image); return new byte[6]; });
            String body = protocol == ModelProtocol.OPENAI_CHAT
                    ? new OpenAiJsonCodec(GSON).requestBody(config(protocol), request)
                    : new AnthropicJsonCodec(GSON).requestBody(config(protocol), request);
            assertEquals(PREFIX_IMAGES, reads);
            assertTrue(body.contains(protocol == ModelProtocol.OPENAI_CHAT ? "data:image/png;base64," : "\"type\":\"base64\""));
        }
    }

    @Test
    void missingHistoricalImageCannotPublishSuccessfulPlaceholderOnlySummary() {
        var requests = new ArrayList<ModelRequest>();
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            try {
                new OpenAiJsonCodec(GSON).requestBody(config(ModelProtocol.OPENAI_CHAT), request);
                return CompletableFuture.completedFuture(textTurn(SUMMARY));
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        };
        var result = compactor(model).compact("system", source(), 2, List.of(), true,
                "actor:missing", new CancellationSignal(), image -> {
                    throw new IOException("Historical image payload is unavailable");
                }).join();
        assertFalse(result.successful());
        assertEquals(ContextCheckpoint.Status.FAILED, result.checkpoint().status());
        assertEquals("summary_failure", result.checkpoint().failureCode());
        assertNull(result.projection());
        assertEquals(1, requests.size());
        assertEquals(List.of(PNG, JPEG), images(requests.getFirst().messages()));
    }

    @Test
    void nestedToolImagesSurviveResultFittingSummaryAndCheckpointReuse() {
        var calls = new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                "visual", "capture", new JsonObject())));
        var output = new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                "visual", new com.google.gson.JsonPrimitive("data ".repeat(1_000)), false, List.of(PNG, JPEG, PNG))));
        var exchange = List.of(calls, output);
        var requests = new ArrayList<ModelRequest>();
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            assertEquals(PREFIX_IMAGES, dev.openallay.model.image.ModelImages.occurrences(request.messages()));
            assertEquals(List.of("visual", "visual", "visual"), request.messages().stream()
                    .flatMap(message -> message.content().stream()).filter(ModelContent.Image.class::isInstance)
                    .map(ModelContent.Image.class::cast).map(ModelContent.Image::originToolUseId).toList());
            assertDoesNotThrow(() -> new OpenAiJsonCodec(GSON).requestBody(config(ModelProtocol.OPENAI_CHAT), request));
            return CompletableFuture.completedFuture(textTurn(SUMMARY));
        };
        var compactor = new ContextCompactor(model, GSON, new CharacterFixtureEstimator(),
                new ContextBudget(8_000, 1_000), "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        List<ModelMessage> fitted = compactor.prepareModelView(exchange);
        assertEquals(PREFIX_IMAGES, dev.openallay.model.image.ModelImages.occurrences(fitted));
        assertEquals(TokenizerMetadata.ImageAccounting.UNKNOWN, compactor.estimator().imageAccounting(fitted));
        var source = new ArrayList<>(exchange);
        source.add(ModelMessage.userText("current:" + "c".repeat(1_300)));
        var compacted = compactor.compactManually(ignored -> "system", source, 2, List.of(),
                "actor:nested", new CancellationSignal(), image -> new byte[6], ignored -> {}).join();
        assertTrue(compacted.successful(), compacted.failureMessage());
        assertNotNull(compacted.checkpoint());
        assertFalse(requests.isEmpty());
        assertEquals(PREFIX_IMAGES, dev.openallay.model.image.ModelImages.occurrences(compacted.projection().messages()));
        var restored = new ModelContextCodec().decode(new ModelContextCodec().encode(compacted.projection().messages()));
        assertEquals(compacted.projection().messages(), restored);
        var reused = compactor.reuse(compacted.checkpoint(), "system", source, 2, List.of()).orElseThrow();
        assertEquals(PREFIX_IMAGES, dev.openallay.model.image.ModelImages.occurrences(reused.messages()));
    }


    @Test
    void repeatedCompactionAfterCodecRestoreKeepsEveryOriginalToolImageOrigin() {
        var requests = new ArrayList<ModelRequest>();
        ModelClient model = (request, events, cancellation) -> {
            requests.add(request);
            assertEquals(List.of(PNG, PNG), dev.openallay.model.image.ModelImages.occurrences(request.messages()));
            assertEquals(List.of("original-view", "original-view"), request.messages().stream()
                    .flatMap(message -> message.content().stream()).filter(ModelContent.Image.class::isInstance)
                    .map(ModelContent.Image.class::cast).map(ModelContent.Image::originToolUseId).toList());
            assertTrue(new OpenAiJsonCodec(GSON).requestBody(config(ModelProtocol.OPENAI_CHAT), request)
                    .contains("tool observation for tool call original-view"));
            assertTrue(new AnthropicJsonCodec(GSON).requestBody(config(ModelProtocol.ANTHROPIC_MESSAGES), request)
                    .contains("tool observation for tool call original-view"));
            return CompletableFuture.completedFuture(textTurn(SUMMARY));
        };
        var compactor = new ContextCompactor(model, GSON, new CharacterFixtureEstimator(),
                new ContextBudget(12_000, 1_000), "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var original = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "original-view", "capture", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("original-view",
                        new com.google.gson.JsonPrimitive("view ".repeat(1_000)), false, List.of(PNG, PNG)))),
                ModelMessage.userText("first current question " + "x".repeat(500)));
        var first = compactor.compactManually(ignored -> "system", original, 2, List.of(),
                "actor:first", new CancellationSignal(), image -> new byte[6], ignored -> {}).join();
        assertTrue(first.successful(), first.failureMessage());
        assertNotNull(first.checkpoint());
        var codec = new ModelContextCodec();
        var restored = new ArrayList<>(codec.decode(codec.encode(first.projection().messages())));
        restored.add(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("answer ".repeat(150)))));
        restored.add(ModelMessage.userText("eligible old follow up " + "y".repeat(800)));
        restored.add(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("completed ".repeat(150)))));
        restored.add(ModelMessage.userText("new protected question"));
        int firstCallCount = requests.size();
        var second = compactor.compactManually(ignored -> "system", restored, 5, List.of(),
                "actor:second", new CancellationSignal(), image -> new byte[6], ignored -> {}).join();
        assertTrue(second.successful(), second.failureMessage());
        assertNotNull(second.checkpoint());
        assertTrue(requests.size() > firstCallCount);
        var twice = codec.decode(codec.encode(second.projection().messages()));
        assertEquals(List.of("original-view", "original-view"), twice.stream()
                .flatMap(message -> message.content().stream()).filter(ModelContent.Image.class::isInstance)
                .map(ModelContent.Image.class::cast).map(ModelContent.Image::originToolUseId).toList());
        assertEquals(List.of(PNG, PNG), dev.openallay.model.image.ModelImages.occurrences(twice));
        var checkpointCodec = new ContextCheckpointCodec();
        var checkpoint = checkpointCodec.decode(checkpointCodec.encode(second.checkpoint()));
        var reused = compactor.reuse(checkpoint, "system", restored, 5, List.of()).orElseThrow();
        assertEquals(twice, reused.messages());
    }


    private static List<ModelMessage> source() {
        return List.of(ModelMessage.userInput("a".repeat(600), List.of(PNG, JPEG)),
                ModelMessage.userInput("b".repeat(600), List.of(PNG)),
                ModelMessage.userText("current:" + "c".repeat(1_300)));
    }

    private static List<ImageReference> images(List<ModelMessage> messages) {
        return messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.Image.class::isInstance)
                .map(ModelContent.Image.class::cast).map(ModelContent.Image::reference).toList();
    }

    private static ContextCompactor compactor(ModelClient model) {
        return new ContextCompactor(model, GSON, new CharacterFixtureEstimator(),
                new ContextBudget(2_800, 400), "test-model", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static ModelTurn textTurn(String text) {
        return new ModelTurn("test", "test-model", List.of(new ModelContent.Text(text)),
                "end_turn", ModelUsage.empty());
    }

    private static ModelConfig config(ModelProtocol protocol) {
        return new ModelConfig(true, protocol, URI.create("https://example.invalid/v1/"),
                "compatible-model", SecretValue.of("test-secret"), 128_000, 400,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    /** Deterministic text/framing fixture; it does not invent any visual token cost. */
    private static final class CharacterFixtureEstimator implements ContextTokenEstimator {
        @Override
        public int estimate(String systemPrompt, List<ModelMessage> messages, List<ModelToolDefinition> tools) {
            int estimate = systemPrompt.length();
            for (ModelMessage message : messages) {
                estimate += 10;
                for (ModelContent content : message.content()) {
                    estimate += content instanceof ModelContent.Text text ? text.text().length()
                            : content instanceof ModelContent.ToolResult result ? result.value().toString().length() : 8;
                }
            }
            return estimate;
        }

        @Override
        public int estimateText(String text) {
            return text.length();
        }
    }
}
