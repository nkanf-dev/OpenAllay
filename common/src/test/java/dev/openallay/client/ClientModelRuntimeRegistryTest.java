package dev.openallay.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.agent.AgentResult;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.ClientCapabilityResolver;
import dev.openallay.capability.ClientCapabilitySnapshot;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.guide.GuideModelProfileException;
import dev.openallay.integration.patchouli.PatchouliMultiblockStore;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.SecretValue;
import dev.openallay.platform.PlatformService;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClientModelRuntimeRegistryTest {
    @TempDir Path temporary;

    @Test
    void routesByProfileAndSharesProviderNeutralHistory() {
        RecordingModel modelA = new RecordingModel("model-a");
        RecordingModel modelB = new RecordingModel("model-b");
        ClientModelRuntimeRegistry registry = registry(
                load("a", "a", "b"), Map.of("a", modelA, "b", modelB));
        UUID actor = UUID.randomUUID();

        AgentResult first = registry.ask(
                "a", actor, "main", UUID.randomUUID(), "first question",
                dev.openallay.context.ToolInvocationContext.developmentConsole("test"), ignored -> {})
                .join();
        AgentResult second = registry.ask(
                "b", actor, "main", UUID.randomUUID(), "second question",
                dev.openallay.context.ToolInvocationContext.developmentConsole("test"), ignored -> {})
                .join();

        assertEquals("answer-model-a", first.text());
        assertEquals("answer-model-b", second.text());
        assertEquals(1, modelA.requests.size());
        assertEquals(1, modelB.requests.size());
        List<String> texts = modelB.requests.getFirst().messages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast)
                .map(ModelContent.Text::text)
                .toList();
        assertEquals(List.of("first question", "answer-model-a", "second question"), texts);
    }

    @Test
    void capturedRuntimeKeepsAuthWhilePlayerTextIsNotScannedAcrossReplacement() {
        String retiredSecret = "opaqueRetiredCredentialAlpha";
        String replacementSecret = "opaqueReplacementCredentialBeta";
        String question = "password=player-game-value token=player-token-value";
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        List<ModelConfig> capturedConfigs = new ArrayList<>();
        RecordingModel captured = new RecordingModel("a", pending);
        RecordingModel next = new RecordingModel("b");
        ClientModelRuntimeRegistry registry = new ClientModelRuntimeRegistry(runtime(),
                replaceTestCredential(load("a", "a"), retiredSecret), new Gson(), Runnable::run, null,
                profile -> {
                    capturedConfigs.add(profile.runtimeConfig());
                    return capturedConfigs.size() == 1 ? captured : next;
                });
        List<dev.openallay.agent.AgentEvent> events = new ArrayList<>();
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        var active = registry.ask("a", actor, "main", requestId, question,
                ToolInvocationContext.developmentConsole("synthetic-player-data"), events::add);
        registry.replace(replaceTestCredential(load("b", "b"), replacementSecret));
        String answer = "token=game-result Authorization: Bearer player-evidence";
        pending.complete(new ModelTurn("test", "a", List.of(new ModelContent.Text(answer)),
                "stop", ModelUsage.empty()));
        var result = active.join();

        assertTrue(result.successful());
        assertEquals(answer, result.text());
        assertEquals(retiredSecret, capturedConfigs.getFirst().apiKey().reveal());
        assertEquals(replacementSecret, capturedConfigs.getLast().apiKey().reveal());
        assertEquals(List.of(question), captured.requests.getFirst().messages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.Text.class::isInstance).map(ModelContent.Text.class::cast)
                .map(ModelContent.Text::text).toList());
        assertTrue(new Gson().toJson(events).contains("player-evidence"));
        String trace = new dev.openallay.agent.trace.LiveTraceJson().encode(result.trace());
        assertTrue(trace.contains("player-game-value"));
        assertTrue(trace.contains("player-evidence"));
        for (String credential : List.of(retiredSecret, replacementSecret)) {
            assertFalse(trace.contains(credential), "framework credentials are not Agent trace inputs");
            assertFalse(new Gson().toJson(result).contains(credential));
            assertFalse(new Gson().toJson(events).contains(credential));
        }
        registry.ask("b", actor, "main", UUID.randomUUID(), "follow up",
                ToolInvocationContext.developmentConsole("synthetic-player-data"), events::add).join();
        assertEquals(List.of(question, answer, "follow up"), next.requests.getFirst().messages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.Text.class::isInstance).map(ModelContent.Text.class::cast)
                .map(ModelContent.Text::text).toList());
        String profileSnapshot = new Gson().toJson(registry.profiles());
        assertFalse(profileSnapshot.contains(retiredSecret));
        assertFalse(profileSnapshot.contains(replacementSecret));
    }

    @Test
    void missingProfileFailsClosedAndNeverRoutesToDefault() {
        RecordingModel modelA = new RecordingModel("model-a");
        ClientModelRuntimeRegistry registry = registry(
                load("a", "a"), Map.of("a", modelA));

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> registry.ask(
                        "missing", UUID.randomUUID(), "main", UUID.randomUUID(), "question",
                        dev.openallay.context.ToolInvocationContext.developmentConsole("test"),
                        ignored -> {}).join());

        GuideModelProfileException profileFailure = assertInstanceOf(
                GuideModelProfileException.class, failure.getCause());
        assertEquals("model_not_configured", profileFailure.code());
        assertTrue(modelA.requests.isEmpty());
    }

    @Test
    void atomicReplacementDoesNotCancelCapturedInFlightRuntime() {
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        RecordingModel modelA = new RecordingModel("model-a", pending);
        RecordingModel modelB = new RecordingModel("model-b");
        ClientModelRuntimeRegistry registry = registry(
                load("a", "a", "b"), Map.of("a", modelA, "b", modelB));

        CompletableFuture<AgentResult> inFlight = registry.ask(
                "a", UUID.randomUUID(), "main", UUID.randomUUID(), "question",
                dev.openallay.context.ToolInvocationContext.developmentConsole("test"), ignored -> {});
        registry.replace(load("b", "b"), profile -> modelB);
        pending.complete(turn("model-a"));

        assertEquals("answer-model-a", inFlight.join().text());
        assertEquals(List.of("b"), registry.profiles().stream().map(value -> value.id()).toList());
        assertEquals("b", registry.defaultProfileId());
    }

    @Test
    void builtinThenTrustedContextReplacementLeavesCapturedRequestRuntimeFrozen() {
        var definition = new ModelProfileDefinition("a", "A", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://openrouter.ai/api/v1/"), "openai/gpt-6-luna", "env:KEY",
                null, 8192, Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var config = new ModelProfilesConfig("a", List.of(definition));
        String json = new dev.openallay.model.config.ModelProfilesConfigWriter().encode(config);
        var loader = new ModelProfilesConfigLoader();
        var initial = ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loader.load(
                new java.io.StringReader(json), Map.of("KEY", "private-sentinel"))).value();
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        RecordingModel old = new RecordingModel("old", pending);
        RecordingModel replacement = new RecordingModel("replacement");
        ClientModelRuntimeRegistry registry = registry(initial, Map.of("a", old));
        var captured = registry.contextSpec("a").orElseThrow();
        assertEquals(1_050_000, captured.budget().contextWindowTokens());
        var active = registry.ask("a", UUID.randomUUID(), "main", UUID.randomUUID(), "question",
                ToolInvocationContext.developmentConsole("frozen-context"), ignored -> {});
        var metadata = new dev.openallay.model.metadata.ModelMetadata("openrouter", definition.model(),
                definition.model(), 2_000_000, 256_000, java.time.Instant.EPOCH);
        var updated = ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loader.load(
                new java.io.StringReader(json), Map.of("KEY", "private-sentinel"),
                Map.of(metadata.key(), metadata))).value();
        registry.replace(updated, profile -> replacement);
        assertEquals(2_000_000, registry.contextSpec("a").orElseThrow().budget().contextWindowTokens());
        assertEquals(1_050_000, captured.budget().contextWindowTokens());
        pending.complete(turn("old"));
        assertEquals("answer-old", active.join().text());
        assertTrue(replacement.requests.isEmpty());
    }

    @Test
    void effortReplacementAffectsOnlyFutureRequestsIncludingToolContinuation() {
        java.util.function.Function<dev.openallay.model.config.ModelReasoningEffort,
                ModelProfilesConfigLoader.Load> configured = effort -> {
            var definition = new ModelProfileDefinition("a", "A", true, ModelProtocol.OPENAI_CHAT,
                    URI.create("https://gateway.example/v1/"), "gateway/luna", "env:KEY",
                    1_000_000, 8192, Duration.ofSeconds(30), Duration.ofSeconds(300), null, effort);
            String json = new dev.openallay.model.config.ModelProfilesConfigWriter().encode(
                    new ModelProfilesConfig("a", List.of(definition)));
            return ((ToolResult.Success<ModelProfilesConfigLoader.Load>) new ModelProfilesConfigLoader()
                    .load(new java.io.StringReader(json), Map.of("KEY", "fixture-key"))).value();
        };
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        List<com.google.gson.JsonObject> oldBodies = new ArrayList<>();
        List<com.google.gson.JsonObject> newBodies = new ArrayList<>();
        var codec = new dev.openallay.model.openai.OpenAiJsonCodec(new Gson());
        ClientModelRuntimeRegistry registry = new ClientModelRuntimeRegistry(runtimeWithFactTool(),
                configured.apply(dev.openallay.model.config.ModelReasoningEffort.HIGH), new Gson(),
                Runnable::run, null, profile -> {
                    ModelConfig capturedConfig = profile.runtimeConfig();
                    return (request, events, cancellation) -> {
                        boolean old = capturedConfig.reasoningEffort()
                                == dev.openallay.model.config.ModelReasoningEffort.HIGH;
                        List<com.google.gson.JsonObject> bodies = old ? oldBodies : newBodies;
                        bodies.add(com.google.gson.JsonParser.parseString(
                                codec.requestBody(capturedConfig, request)).getAsJsonObject());
                        return old && bodies.size() == 1 ? pending
                                : CompletableFuture.completedFuture(turn(old ? "old" : "new"));
                    };
                });
        UUID actor = UUID.randomUUID();
        var active = registry.ask("a", actor, "main", UUID.randomUUID(), "first question",
                ToolInvocationContext.developmentConsole("frozen-effort"), ignored -> {});
        registry.replace(configured.apply(dev.openallay.model.config.ModelReasoningEffort.LOW));
        pending.complete(toolTurn("effort-call", "test__fact", 42));
        assertEquals("answer-old", active.join().text());
        assertEquals(2, oldBodies.size());
        assertTrue(newBodies.isEmpty());
        for (var body : oldBodies) assertEquals("high", body.get("reasoning_effort").getAsString());
        assertEquals("answer-new", registry.ask("a", actor, "main", UUID.randomUUID(), "next question",
                ToolInvocationContext.developmentConsole("future-effort"), ignored -> {}).join().text());
        assertEquals(1, newBodies.size());
        assertEquals("low", newBodies.getFirst().get("reasoning_effort").getAsString());
        assertTrue(newBodies.getFirst().getAsJsonArray("messages").size() > 2,
                "replacing the effort keeps provider-neutral session context");
    }

    @Test
    void preparedReplacementDoesNotPublishUntilExplicitOneTimeCommit() {
        RecordingModel modelA = new RecordingModel("model-a");
        RecordingModel modelB = new RecordingModel("model-b");
        ClientModelRuntimeRegistry registry = registry(
                load("a", "a"), Map.of("a", modelA, "b", modelB));

        ClientModelRuntimeRegistry.PreparedReplacement prepared =
                registry.prepare(load("b", "b"));

        assertEquals(List.of("a"), registry.profiles().stream()
                .map(value -> value.id()).toList());
        assertEquals("a", registry.defaultProfileId());

        prepared.publishCommitted();

        assertEquals(List.of("b"), registry.profiles().stream()
                .map(value -> value.id()).toList());
        assertEquals("b", registry.defaultProfileId());
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, prepared::publishCommitted);
    }

    @Test
    void matchingMetadataPreparationPublishesOnceWithoutChangingSettings() {
        RecordingModel model = new RecordingModel("model-a");
        var registry = registry(load("a", "a"), Map.of("a", model));
        var metadata = registry.prepare(load("a", "a"));

        assertTrue(metadata.publish());
        assertEquals("a", registry.defaultProfileId());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, metadata::publish);
    }

    @Test
    void committedReplacementPreservesLatestCapabilitiesInBothDirections() {
        for (boolean disableFact : List.of(true, false)) {
            OpenAllayRuntime product = runtimeWithFactTool();
            RecordingModel modelA = new RecordingModel("model-a");
            RecordingModel modelB = new RecordingModel("model-b");
            var registry = registry(product, load("a", "a"), Map.of("a", modelA, "b", modelB));
            var enabled = registry.capabilities();
            var disabled = success(new ClientCapabilityResolver().resolve(
                    new CapabilityPolicy(Set.of("test:fact"), Set.of()),
                    product.tools().registrations(), product.skills()));
            if (!disableFact) registry.replaceCapabilities(disabled);
            var committed = registry.prepare(load("b", "b"));
            var latest = disableFact ? disabled : enabled;
            registry.replaceCapabilities(latest);

            committed.publishCommitted();

            assertSame(latest, registry.capabilities());
            assertEquals("b", registry.defaultProfileId());
            registry.ask("b", UUID.randomUUID(), "main", UUID.randomUUID(), "future question",
                    ToolInvocationContext.developmentConsole("committed-caps-" + disableFact), ignored -> {}).join();
            assertEquals(!disableFact, modelB.requests.getFirst().tools().stream()
                    .anyMatch(tool -> tool.name().equals("test__fact")));
            assertTrue(modelA.requests.isEmpty());
        }
    }

    @Test
    void preparationFromOldSettingsCannotPublishEvenWhenItCapturesTheLatestRegistryRevision() {
        RecordingModel modelA = new RecordingModel("model-a");
        RecordingModel modelB = new RecordingModel("model-b");
        var registry = registry(load("a", "a"), Map.of("a", modelA, "b", modelB));
        var oldSettings = load("a", "a");
        registry.replace(load("b", "b"));
        var stale = registry.prepare(oldSettings); // Decode captured alpha before beta committed.

        assertFalse(stale.publish());
        assertEquals("b", registry.defaultProfileId());
        registry.ask("b", UUID.randomUUID(), "main", UUID.randomUUID(), "future question",
                ToolInvocationContext.developmentConsole("matching-settings"), ignored -> {}).join();
        assertEquals(1, modelB.requests.size());
        assertTrue(modelA.requests.isEmpty());
    }

    @Test
    void stalePreparedReplacementCannotRollBackNewerPublishedProfile() {
        RecordingModel modelA = new RecordingModel("model-a");
        RecordingModel modelB = new RecordingModel("model-b");
        ClientModelRuntimeRegistry registry = registry(
                load("a", "a"), Map.of("a", modelA, "b", modelB));
        var stale = registry.prepare(load("a", "a"));
        registry.replace(load("b", "b"));

        assertFalse(stale.publish());
        assertEquals("b", registry.defaultProfileId());
        assertEquals("answer-model-b", registry.ask("b", UUID.randomUUID(), "main",
                UUID.randomUUID(), "future question", ToolInvocationContext.developmentConsole("future-profile"),
                ignored -> {}).join().text());
        assertTrue(modelA.requests.isEmpty());
    }

    @Test
    void stalePreparedCapabilitiesCannotRollBackEitherCapabilityDirection() {
        for (boolean disableFact : List.of(true, false)) {
            OpenAllayRuntime product = runtimeWithFactTool();
            RecordingModel model = new RecordingModel("model-a");
            ClientModelRuntimeRegistry registry = registry(
                    product, load("a", "a"), Map.of("a", model));
            var enabled = registry.capabilities();
            var disabled = success(new ClientCapabilityResolver().resolve(
                    new CapabilityPolicy(Set.of("test:fact"), Set.of()),
                    product.tools().registrations(), product.skills()));
            if (!disableFact) registry.replaceCapabilities(disabled);
            var stale = registry.prepare(load("a", "a"));
            var latest = disableFact ? disabled : enabled;
            registry.replaceCapabilities(latest);

            assertFalse(stale.publish());
            assertSame(latest, registry.capabilities());
            registry.ask("a", UUID.randomUUID(), "main", UUID.randomUUID(), "future question",
                    ToolInvocationContext.developmentConsole("future-capabilities"), ignored -> {}).join();
            assertEquals(!disableFact, model.requests.getFirst().tools().stream()
                    .anyMatch(tool -> tool.name().equals("test__fact")));
        }
    }

    @Test
    void preparingRuntimeNeverBlocksOwnerPublicationOrCancelsCapturedRequest() throws Exception {
        OpenAllayRuntime product = runtimeWithFactTool();
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        RecordingModel modelA = new RecordingModel("model-a", pending);
        RecordingModel modelB = new RecordingModel("model-b");
        var preparing = new java.util.concurrent.CountDownLatch(1);
        var releasePreparation = new java.util.concurrent.CountDownLatch(1);
        ClientModelRuntimeRegistry registry = new ClientModelRuntimeRegistry(product, load("a", "a"),
                new Gson(), Runnable::run, null, profile -> {
                    if (profile.definition().id().equals("b")) {
                        preparing.countDown();
                        try {
                            if (!releasePreparation.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                                throw new AssertionError("The test did not release preparation");
                            }
                        } catch (InterruptedException failure) {
                            throw new AssertionError(failure);
                        }
                        return modelB;
                    }
                    return modelA;
                });
        var active = registry.ask("a", UUID.randomUUID(), "main", UUID.randomUUID(), "captured question",
                ToolInvocationContext.developmentConsole("nonblocking-publication"), ignored -> {});
        var prepared = CompletableFuture.supplyAsync(() -> registry.prepare(load("b", "b")));
        assertTrue(preparing.await(5, java.util.concurrent.TimeUnit.SECONDS));
        var latest = success(new ClientCapabilityResolver().resolve(
                new CapabilityPolicy(Set.of("test:fact"), Set.of()),
                product.tools().registrations(), product.skills()));
        try {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> registry.replaceCapabilities(latest));
            assertSame(latest, registry.capabilities());
            assertFalse(prepared.isDone(), "Runtime construction remains deliberately blocked");
            assertFalse(active.isDone());
        } finally {
            releasePreparation.countDown();
        }
        assertFalse(prepared.get(5, java.util.concurrent.TimeUnit.SECONDS).publish());
        pending.complete(turn("model-a"));
        assertEquals("answer-model-a", active.join().text());
        assertSame(latest, registry.capabilities());
        assertEquals("a", registry.defaultProfileId());
    }

    @Test
    void committedReplacementRebasesLatestCapabilitiesWithoutCancelingCapturedRequest() {
        OpenAllayRuntime product = runtimeWithFactTool();
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        RecordingModel modelA = new RecordingModel("model-a", pending);
        RecordingModel modelB = new RecordingModel("model-b");
        ClientModelRuntimeRegistry registry = registry(
                product, load("a", "a"), Map.of("a", modelA, "b", modelB));
        var active = registry.ask("a", UUID.randomUUID(), "main", UUID.randomUUID(), "first question",
                ToolInvocationContext.developmentConsole("captured-capabilities"), ignored -> {});
        var committed = registry.prepare(load("b", "b"));
        var latest = success(new ClientCapabilityResolver().resolve(
                new CapabilityPolicy(Set.of("test:fact"), Set.of()),
                product.tools().registrations(), product.skills()));
        registry.replaceCapabilities(latest);

        committed.publishCommitted();
        pending.complete(turn("model-a"));

        assertEquals("answer-model-a", active.join().text());
        assertTrue(modelA.requests.getFirst().tools().stream()
                .anyMatch(tool -> tool.name().equals("test__fact")));
        assertEquals("b", registry.defaultProfileId());
        assertSame(latest, registry.capabilities());
        registry.ask("b", UUID.randomUUID(), "main", UUID.randomUUID(), "future question",
                ToolInvocationContext.developmentConsole("committed-capabilities"), ignored -> {}).join();
        assertFalse(modelB.requests.getFirst().tools().stream()
                .anyMatch(tool -> tool.name().equals("test__fact")));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, committed::publishCommitted);
    }

    @Test
    void capabilityReplacementAffectsFutureRequestsOnlyAndSharesEndpointAndHistory() {
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        ToolSequenceModel model = new ToolSequenceModel(pending);
        OpenAllayRuntime product = runtimeWithFactTool();
        ClientModelRuntimeRegistry registry = registry(
                product, load("a", "a"), Map.of("a", model));
        UUID actor = UUID.randomUUID();
        Object endpointBefore = registry.endpointIdentity("a");

        CompletableFuture<AgentResult> active = registry.ask(
                "a", actor, "main", UUID.randomUUID(), "first question",
                ToolInvocationContext.developmentConsole("test"), ignored -> {});
        ClientCapabilitySnapshot withoutFact = success(new ClientCapabilityResolver().resolve(
                new CapabilityPolicy(Set.of("test:fact"), Set.of()),
                product.tools().registrations(),
                product.skills()));
        registry.replaceCapabilities(withoutFact);
        pending.complete(toolTurn("call-1", "test__fact", 42));

        assertTrue(active.join().successful());
        assertSame(endpointBefore, registry.endpointIdentity("a"));
        assertTrue(model.requests.getFirst().tools().stream()
                .anyMatch(tool -> tool.name().equals("test__fact")));
        assertTrue(model.requests.get(1).tools().stream()
                .anyMatch(tool -> tool.name().equals("test__fact")));

        AgentResult next = registry.ask(
                "a", actor, "main", UUID.randomUUID(), "second question",
                ToolInvocationContext.developmentConsole("test"), ignored -> {}).join();

        assertTrue(next.successful());
        ModelRequest nextRequest = model.requests.get(2);
        assertFalse(nextRequest.tools().stream()
                .anyMatch(tool -> tool.name().equals("test__fact")));
        List<String> text = nextRequest.messages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast)
                .map(ModelContent.Text::text)
                .toList();
        assertEquals(List.of("first question", "tool-complete", "second question"), text);
    }

    @Test
    void javaGuidanceUsesFrozenRequestModeAndDoesNotLeakIntoLaterRequests() {
        OpenAllayRuntime product = runtime();
        assertTrue(product.skills().reload(List.of(new dev.openallay.skill.SkillSource(
                "test", "unrestricted-javascript/SKILL.md", Map.of("unrestricted-javascript/SKILL.md", """
                ---
                name: unrestricted-javascript
                description: Use when unrestricted Java is enabled
                ---
                Request Java guidance sentinel: use Java.type.
                """, "unrestricted-javascript/references/java-jvm.md", "Captured Java reference sentinel."))), Set.of()));
        product.tools().register("test-skills", List.of(new dev.openallay.skill.LoadSkillTool(product.skills())));
        CompletableFuture<ModelTurn> pending = new CompletableFuture<>();
        ToolSequenceModel model = new ToolSequenceModel(pending);
        ClientModelRuntimeRegistry registry = registry(product, load("a", "a"), Map.of("a", model));
        var disabled = ToolInvocationContext.developmentConsole("java-disabled");
        var authorization = new dev.openallay.script.UnrestrictedJavascriptRuntime();
        authorization.replace(new dev.openallay.script.UnrestrictedJavascriptConfig(true));
        var enabled = new ToolInvocationContext("java-enabled", disabled.capturedAt(), disabled.caller(),
                disabled.player(), disabled.registries(), disabled.recipes(), disabled.observableGameState(),
                disabled.metrics(), authorization.freeze("java-enabled"));
        UUID actor = UUID.randomUUID();
        int reserved = registry.contextSpec("a").orElseThrow().promptAndToolTokens();
        var active = registry.ask("a", actor, "main", UUID.randomUUID(), "first", enabled, ignored -> {});
        authorization.replace(dev.openallay.script.UnrestrictedJavascriptConfig.defaults());
        assertTrue(authorization.freeze("java-enabled"));
        assertFalse(authorization.freeze("java-disabled"));
        assertTrue(product.skills().reload(List.of(), Set.of()));
        registry.replaceCapabilities(success(new ClientCapabilityResolver().resolve(
                CapabilityPolicy.defaults(), product.tools().registrations(), product.skills())));
        var input = new com.google.gson.JsonObject();
        input.addProperty("name", "unrestricted-javascript");
        input.addProperty("reference", "references/java-jvm.md");
        pending.complete(new ModelTurn("test", "model-a",
                List.of(new ModelContent.ToolUse("load-java-reference", "openallay__load_skill", input)),
                "tool_use", ModelUsage.empty()));
        assertTrue(active.join().successful());
        ModelRequest first = model.requests.getFirst();
        assertTrue(first.systemPrompt().contains("Request Java guidance sentinel"));
        assertTrue(first.systemPrompt().contains("<name>unrestricted-javascript</name>"));
        assertFalse(first.systemPrompt().contains("Captured Java reference sentinel"));
        assertTrue(reserved >= registry.contextSpec("a").orElseThrow().estimator()
                .estimate(first.systemPrompt(), List.of(), first.tools()));
        assertTrue(model.requests.get(1).messages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ModelContent.ToolResult.class::isInstance)
                .map(ModelContent.ToolResult.class::cast)
                .anyMatch(result -> !result.error()
                        && result.value().toString().contains("Captured Java reference sentinel")));
        registry.ask("a", actor, "main", UUID.randomUUID(), "second", disabled, ignored -> {}).join();
        String next = model.requests.get(2).systemPrompt();
        assertFalse(next.contains("Request Java guidance sentinel"));
        assertFalse(next.contains("<name>unrestricted-javascript</name>"));
        assertTrue(next.contains("JavaScript uses the default isolated mode"));
        registry.ask("a", actor, "main", UUID.randomUUID(), "third", enabled, ignored -> {}).join();
        assertFalse(model.requests.get(3).systemPrompt().contains("Request Java guidance sentinel"));
    }

    @Test
    void commandPromptAndLoadSkillCatalogFollowTheActualTopLevelBindingInBothJavascriptModes() {
        for (boolean unrestricted : List.of(false, true)) {
            for (boolean enabled : List.of(false, true)) {
                var commands = new dev.openallay.script.command.CommandCapabilityRuntime();
                commands.replace(new dev.openallay.script.command.CommandCapabilityConfig(enabled));
                var workspaces = new dev.openallay.script.workspace.AgentResultWorkspaceRegistry();
                var javascript = new dev.openallay.tool.builtin.RunJavascriptTool(
                        new dev.openallay.script.RhinoJavascriptRuntime(),
                        dev.openallay.script.data.MinecraftAgentHostGraph::new, workspaces,
                        new dev.openallay.script.workspace.JavascriptResultPresenter(), commands);
                ToolRegistry tools = new ToolRegistry();
                tools.register("test:javascript", List.of(javascript));
                SkillRepository skills = new SkillRepository(new SkillParser(), List.of(dev.openallay.tool.builtin.RunJavascriptTool.ID));
                assertTrue(skills.reload(new dev.openallay.skill.BundledSkillLoader().load(), Set.of()));
                skills.setRuntimeDisabledSkills(enabled ? Set.of() : Set.of("run-game-commands"));
                tools.register("test:skills", List.of(new dev.openallay.skill.LoadSkillTool(skills)));
                OpenAllayRuntime product = new OpenAllayRuntime(new PlatformService() {
                    public String platformName() { return "test"; }
                    public String gameVersion() { return "26.2-test"; }
                    public boolean isModLoaded(String id) { return false; }
                    public boolean isDevelopmentEnvironment() { return true; }
                }, tools, new KnowledgeRegistry(), new PatchouliMultiblockStore(),
                        new dev.openallay.script.extension.JavascriptDataModuleRegistry(), commands,
                        skills, new DevelopmentToolInspector(tools), null,
                        new dev.openallay.capability.CapabilitySettingsCatalog());
                String correlation = "commands-" + enabled + "-java-" + unrestricted;
                commands.capture(correlation, UUID.randomUUID(),
                        new dev.openallay.script.command.CommandCatalogSnapshot(java.time.Instant.EPOCH, List.of()),
                        (actor, command, cancellation) -> CompletableFuture.completedFuture(null));
                var base = ToolInvocationContext.developmentConsole(correlation);
                var context = new ToolInvocationContext(correlation, base.capturedAt(), base.caller(),
                        base.player(), base.registries(), base.recipes(), base.observableGameState(),
                        base.metrics(), unrestricted);
                var toolInput = new com.google.gson.JsonObject();
                toolInput.addProperty("source", "return {commands: typeof commands, java: typeof Java};");
                CompletableFuture<ModelTurn> tool = CompletableFuture.completedFuture(new ModelTurn(
                        "test", "model-a", List.of(new ModelContent.ToolUse("inspect-binding",
                                "openallay__run_javascript", toolInput)), "tool_use", ModelUsage.empty()));
                ToolSequenceModel model = new ToolSequenceModel(tool);
                ClientModelRuntimeRegistry registry = registry(product, load("a", "a"), Map.of("a", model));

                List<dev.openallay.agent.AgentEvent> executionEvents = new ArrayList<>();
                assertTrue(registry.ask("a", UUID.randomUUID(), "main", UUID.randomUUID(), "inspect binding",
                        context, executionEvents::add).join().successful());

                String prompt = model.requests.getFirst().systemPrompt();
                assertEquals(enabled, prompt.contains("<name>run-game-commands</name>"));
                assertEquals(enabled, prompt.contains("commands.run(text) are available as top-level"));
                assertEquals(!enabled, prompt.contains("The commands binding is not present for this request"));
                assertEquals(unrestricted, prompt.contains("<name>unrestricted-javascript</name>"));
                dev.openallay.agent.AgentEvent.ToolCompleted result = executionEvents.stream()
                        .filter(dev.openallay.agent.AgentEvent.ToolCompleted.class::isInstance)
                        .map(dev.openallay.agent.AgentEvent.ToolCompleted.class::cast)
                        .findFirst().orElseThrow();
                assertFalse(result.failure());
                var preview = result.normalized().getAsJsonObject("value").getAsJsonObject("preview");
                assertEquals(enabled ? "object" : "undefined", preview.get("commands").getAsString());
                assertEquals(unrestricted ? "object" : "undefined", preview.get("java").getAsString());
            }
        }
    }

    @Test
    void latestEstimateIncludesExactPromptToolsAndMessagesAndDoesNotLeakAcrossScopes() {
        RecordingModel modelA = new RecordingModel("model-a");
        RecordingModel modelB = new RecordingModel("model-b");
        OpenAllayRuntime product = runtimeWithFactTool();
        ClientModelRuntimeRegistry registry = registry(
                product, load("a", "a", "b"), Map.of("a", modelA, "b", modelB));
        UUID actor = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        assertTrue(registry.contextEstimate("a", actor, "main").isEmpty());
        assertTrue(registry.ask("a", actor, "main", requestId, "small request",
                ToolInvocationContext.developmentConsole("estimate"), ignored -> {}).join().successful());
        ModelRequest actual = modelA.requests.getLast();
        int expected = registry.contextSpec("a").orElseThrow().estimator().estimate(
                actual.systemPrompt(), actual.messages(), actual.tools());
        var estimate = registry.contextEstimate("a", actor, "main").orElseThrow();
        assertEquals(requestId, estimate.requestId());
        assertEquals(expected, estimate.estimatedTokens());
        assertTrue(expected > 0);
        assertTrue(registry.contextEstimate("b", actor, "main").isEmpty());
        assertTrue(registry.contextEstimate("a", UUID.randomUUID(), "main").isEmpty());
        assertTrue(registry.contextEstimate("a", actor, "other").isEmpty());
        registry.replaceCapabilities(registry.capabilities());
        assertEquals(estimate, registry.contextEstimate("a", actor, "main").orElseThrow());
        registry.clearSession(actor, "main");
        assertTrue(registry.contextEstimate("a", actor, "main").isEmpty());
        registry.ask("a", actor, "main", UUID.randomUUID(), "again",
                ToolInvocationContext.developmentConsole("estimate"), ignored -> {}).join();
        registry.clearActor(actor);
        assertTrue(registry.contextEstimate("a", actor, "main").isEmpty());
        registry.ask("a", actor, "main", UUID.randomUUID(), "replacement",
                ToolInvocationContext.developmentConsole("estimate"), ignored -> {}).join();
        registry.replace(load("a", "a", "b"));
        assertTrue(registry.contextEstimate("a", actor, "main").isEmpty());
    }

    @Test
    void ordinaryFifoMetadataCannotRollBackRealBackendSavePublication() throws Exception {
        assertMetadataAndCommittedBackendPublication(false);
    }

    @Test
    void ordinaryFifoMetadataCannotRollBackRealBackendReloadPublication() throws Exception {
        assertMetadataAndCommittedBackendPublication(true);
    }

    private void assertMetadataAndCommittedBackendPublication(boolean reload) throws Exception {
        Path profiles = temporary.resolve(reload ? "reload-models.json" : "save-models.json");
        var initial = load("a", "a");
        var candidate = load("b", "b").config();
        var writer = new dev.openallay.model.config.ModelProfilesConfigWriter();
        Files.writeString(profiles, writer.encode(initial.config()));
        RecordingModel modelA = new RecordingModel("model-a");
        RecordingModel modelB = new RecordingModel("model-b");
        var registry = registry(initial, Map.of("a", modelA, "b", modelB));
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor dispatcher = new ManualExecutor();
        try (var credentials = new dev.openallay.model.config.LocalCredentialStore(
                temporary.resolve(reload ? "reload-credentials.sqlite3" : "save-credentials.sqlite3"),
                java.time.Clock.systemUTC())) {
            var backend = new dev.openallay.settings.model.ModelSettingsBackend(profiles,
                    () -> Map.of("KEY_A", "fixture-a", "KEY_B", "fixture-b"), registry,
                    unusedProbe(), credentials);
            var service = new dev.openallay.settings.ClientSettingsService(
                    dev.openallay.guide.ui.GuideDisplayConfig.defaults(), backend.state(initial), Set.of(),
                    backend, metadataActions(), dispatcher::execute, worker);
            service.acceptMetadataUpdate(new dev.openallay.model.metadata.ModelMetadataUpdate(Map.of(), null));
            worker.runNext(); // Alpha metadata is prepared and its owner completion is queued.
            if (reload) Files.writeString(profiles, writer.encode(candidate));
            var operation = reload ? service.reloadModels(true) : service.saveModels(candidate);
            worker.runNext(); // Real backend publishes beta on the worker, before owner completion.
            assertEquals("b", registry.defaultProfileId());
            assertEquals("a", service.snapshot().models().config().defaultProfileId());
            assertFalse(operation.isDone());
            assertEquals(candidate, loadedConfig(profiles));

            while (!dispatcher.tasks.isEmpty()) {
                dispatcher.runNext(); // Normal FIFO, never runLast.
                assertEquals("b", registry.defaultProfileId(),
                        "Every completion must preserve the already-committed runtime");
            }
            worker.runAll();
            dispatcher.runAll();

            assertInstanceOf(ToolResult.Success.class, operation.join());
            assertEquals(candidate, loadedConfig(profiles));
            assertEquals(candidate, service.snapshot().models().config());
            assertEquals("b", registry.defaultProfileId());
            assertEquals("answer-model-b", registry.ask("b", UUID.randomUUID(), "main", UUID.randomUUID(),
                    "future question", ToolInvocationContext.developmentConsole("real-backend-publication"),
                    ignored -> {}).join().text());
            assertTrue(modelA.requests.isEmpty());
        }
    }

    @Test
    void metadataCannotOverwriteRealCapabilityWorkerPublicationInEitherCompletionOrder() throws Exception {
        for (boolean disableFact : List.of(true, false)) {
            for (boolean metadataCompletionFirst : List.of(true, false)) {
                String suffix = disableFact + "-" + metadataCompletionFirst;
                Path profiles = temporary.resolve("cap-models-" + suffix + ".json");
                var initial = load("a", "a");
                Files.writeString(profiles, new dev.openallay.model.config.ModelProfilesConfigWriter()
                        .encode(initial.config()));
                OpenAllayRuntime product = runtimeWithFactTool();
                RecordingModel model = new RecordingModel("model-a");
                var registry = registry(product, initial, Map.of("a", model));
                var capabilities = new dev.openallay.settings.capability.CapabilitySettingsBackend(
                        temporary.resolve("capabilities-" + suffix + ".json"), product, registry);
                if (!disableFact) assertInstanceOf(ToolResult.Success.class, capabilities.saveCapabilities(
                        new CapabilityPolicy(Set.of("test:fact"), Set.of())));
                ManualExecutor worker = new ManualExecutor();
                ManualExecutor dispatcher = new ManualExecutor();
                try (var credentials = new dev.openallay.model.config.LocalCredentialStore(
                        temporary.resolve("cap-credentials-" + suffix + ".sqlite3"), java.time.Clock.systemUTC())) {
                    var backend = new dev.openallay.settings.model.ModelSettingsBackend(profiles,
                            () -> Map.of("KEY_A", "fixture-a"), registry, unusedProbe(), credentials);
                    var service = capabilityService(backend, initial, capabilities, dispatcher, worker);
                    service.acceptMetadataUpdate(new dev.openallay.model.metadata.ModelMetadataUpdate(Map.of(), null));
                    worker.runNext();
                    var latestPolicy = disableFact ? new CapabilityPolicy(Set.of("test:fact"), Set.of())
                            : CapabilityPolicy.defaults();
                    var save = service.saveCapabilities(latestPolicy);
                    worker.runNext(); // Actual capability worker publishes before finishCapabilities.
                    var latest = registry.capabilities();
                    assertEquals(latestPolicy, latest.policy());
                    assertFalse(save.isDone());
                    if (!metadataCompletionFirst) dispatcher.runLast(); // Opposite owner completion order.
                    dispatcher.runAll();
                    worker.runAll();
                    dispatcher.runAll();

                    assertInstanceOf(ToolResult.Success.class, save.join());
                    assertSame(latest, registry.capabilities());
                    assertEquals(latestPolicy, service.snapshot().capabilities().policy());
                    registry.ask("a", UUID.randomUUID(), "main", UUID.randomUUID(), "future question",
                            ToolInvocationContext.developmentConsole("real-capability-publication"), ignored -> {}).join();
                    assertEquals(!disableFact, model.requests.getFirst().tools().stream()
                            .anyMatch(tool -> tool.name().equals("test__fact")));
                }
            }
        }
    }

    @Test
    void metadataCannotOverwriteExperimentalCommandCapabilityRefreshInEitherDirection() throws Exception {
        for (boolean enableCommands : List.of(true, false)) {
            for (boolean metadataCompletionFirst : List.of(true, false)) {
                String suffix = enableCommands + "-" + metadataCompletionFirst;
                var initial = load("a", "a");
                Path profiles = temporary.resolve("command-models-" + suffix + ".json");
                Files.writeString(profiles, new dev.openallay.model.config.ModelProfilesConfigWriter()
                        .encode(initial.config()));
                OpenAllayRuntime product = runtimeWithFactTool();
                assertTrue(product.skills().reload(List.of(new dev.openallay.skill.SkillSource(
                        "test", "run-game-commands/SKILL.md", Map.of("run-game-commands/SKILL.md", """
                        ---
                        name: run-game-commands
                        description: Synthetic experimental-command capability
                        allowed-tools: test:fact
                        ---
                        Command refresh publication sentinel.
                        """))), Set.of()));
                product.tools().register("test-skills", List.of(new dev.openallay.skill.LoadSkillTool(product.skills())));
                product.tools().register("test-javascript", List.of(new dev.openallay.tool.builtin.RunJavascriptTool(
                        new dev.openallay.script.RhinoJavascriptRuntime(),
                        dev.openallay.script.data.MinecraftAgentHostGraph::new,
                        new dev.openallay.script.workspace.AgentResultWorkspaceRegistry(),
                        new dev.openallay.script.workspace.JavascriptResultPresenter(), product.commands())));
                var prior = new dev.openallay.script.command.CommandCapabilityConfig(!enableCommands);
                product.commands().replace(prior);
                product.skills().setRuntimeDisabledSkills(prior.enabled() ? Set.of() : Set.of("run-game-commands"));
                RecordingModel model = new RecordingModel("model-a");
                var registry = registry(product, initial, Map.of("a", model));
                var capabilities = new dev.openallay.settings.capability.CapabilitySettingsBackend(
                        temporary.resolve("command-capabilities-" + suffix + ".json"), product, registry);
                var commandStore = new dev.openallay.script.command.CommandCapabilityConfigStore(
                        temporary.resolve("commands-" + suffix + ".json"));
                assertInstanceOf(ToolResult.Success.class, commandStore.save(prior));
                var publishCommandConfig = dev.openallay.settings.ClientSettingsRuntime.class.getDeclaredMethod(
                        "publishCommandConfig", ToolResult.class,
                        dev.openallay.script.command.CommandCapabilityConfigStore.class,
                        dev.openallay.FeatureServices.class,
                        dev.openallay.settings.capability.CapabilitySettingsBackend.class);
                publishCommandConfig.setAccessible(true);
                ManualExecutor worker = new ManualExecutor();
                ManualExecutor dispatcher = new ManualExecutor();
                try (var credentials = new dev.openallay.model.config.LocalCredentialStore(
                        temporary.resolve("command-credentials-" + suffix + ".sqlite3"), java.time.Clock.systemUTC())) {
                    var backend = new dev.openallay.settings.model.ModelSettingsBackend(profiles,
                            () -> Map.of("KEY_A", "fixture-a"), registry, unusedProbe(), credentials);
                    var service = new dev.openallay.settings.ClientSettingsService(
                            dev.openallay.guide.ui.GuideDisplayConfig.defaults(), unusedDisplayActions(), backend.state(initial), Set.of(),
                            backend, metadataActions(), capabilities.currentView(), capabilities,
                            dev.openallay.settings.capability.RecipeSettingsView.defaults(), unusedRecipeActions(),
                            dev.openallay.settings.skill.SkillSettingsView.empty(), unusedSkillActions(),
                            dev.openallay.settings.extension.ExtensionSettingsView.defaults(), prior,
                            new dev.openallay.settings.ClientSettingsService.CommandActions() {
                                public ToolResult<dev.openallay.script.command.CommandCapabilityConfig> save(
                                        dev.openallay.script.command.CommandCapabilityConfig candidate) {
                                    try {
                                        @SuppressWarnings("unchecked")
                                        var result = (ToolResult<dev.openallay.script.command.CommandCapabilityConfig>)
                                                publishCommandConfig.invoke(null, commandStore.save(candidate),
                                                        commandStore, product, capabilities);
                                        return result;
                                    } catch (ReflectiveOperationException failure) {
                                        throw new AssertionError(failure);
                                    }
                                }
                                public ToolResult<dev.openallay.script.command.CommandCapabilityConfig> reload() {
                                    throw new AssertionError("Command reload is outside this test");
                                }
                            }, unusedHistoryActions(), dispatcher::execute, worker, null);
                    service.acceptMetadataUpdate(new dev.openallay.model.metadata.ModelMetadataUpdate(Map.of(), null));
                    worker.runNext();
                    var save = service.saveExperimentalCommands(enableCommands);
                    worker.runNext(); // Actual command helper refreshes and publishes capabilities on the worker.
                    var latest = registry.capabilities();
                    assertEquals(enableCommands, latest.skills().metadata().stream()
                            .anyMatch(skill -> skill.name().equals("run-game-commands")));
                    if (!metadataCompletionFirst) dispatcher.runLast();
                    dispatcher.runAll();
                    worker.runAll();
                    dispatcher.runAll();

                    assertInstanceOf(ToolResult.Success.class, save.join());
                    assertSame(latest, registry.capabilities());
                    assertEquals(enableCommands, service.snapshot().experimentalCommands().enabled());
                    String correlation = "command-refresh-publication-" + suffix;
                    product.commands().capture(correlation, UUID.randomUUID(),
                            new dev.openallay.script.command.CommandCatalogSnapshot(java.time.Instant.EPOCH, List.of()),
                            (actor, command, cancellation) -> CompletableFuture.completedFuture(null));
                    registry.ask("a", UUID.randomUUID(), "main", UUID.randomUUID(), "future question",
                            ToolInvocationContext.developmentConsole(correlation), ignored -> {}).join();
                    assertEquals(enableCommands, model.requests.getFirst().systemPrompt()
                            .contains("<name>run-game-commands</name>"));
                }
            }
        }
    }

    private static dev.openallay.settings.ClientSettingsService.DisplayActions unusedDisplayActions() {
        return new dev.openallay.settings.ClientSettingsService.DisplayActions() {
            public ToolResult<dev.openallay.guide.ui.GuideDisplayConfig> saveDisplay(
                    dev.openallay.guide.ui.GuideDisplayConfig candidate) { throw new AssertionError("Unexpected display save"); }
            public ToolResult<dev.openallay.guide.ui.GuideDisplayConfig> reloadDisplay() {
                throw new AssertionError("Unexpected display reload");
            }
        };
    }

    private static dev.openallay.settings.ClientSettingsService.RecipeActions unusedRecipeActions() {
        return new dev.openallay.settings.ClientSettingsService.RecipeActions() {
            public ToolResult<dev.openallay.settings.capability.RecipeSettingsView> saveRecipes(
                    dev.openallay.recipe.config.RecipeClientConfig candidate) { throw new AssertionError("Unexpected recipe save"); }
            public ToolResult<dev.openallay.settings.capability.RecipeSettingsView> reloadRecipes() {
                throw new AssertionError("Unexpected recipe reload");
            }
        };
    }

    private static dev.openallay.settings.ClientSettingsService.SkillActions unusedSkillActions() {
        return new dev.openallay.settings.ClientSettingsService.SkillActions() {
            public ToolResult<dev.openallay.settings.skill.SkillSettingsView> saveOverride(String name, String markdown) {
                throw new AssertionError("Unexpected Skill save");
            }
            public ToolResult<dev.openallay.settings.skill.SkillSettingsView> deleteOverride(String name) {
                throw new AssertionError("Unexpected Skill deletion");
            }
            public ToolResult<dev.openallay.settings.skill.SkillSettingsView> reloadSkills() {
                throw new AssertionError("Unexpected Skill reload");
            }
            public dev.openallay.settings.skill.SkillSettingsView currentView() {
                return dev.openallay.settings.skill.SkillSettingsView.empty();
            }
        };
    }

    private static dev.openallay.settings.ClientSettingsService.HistoryActions unusedHistoryActions() {
        return new dev.openallay.settings.ClientSettingsService.HistoryActions() {
            public dev.openallay.settings.ClientSettingsService.HistoryRuntimeState state() {
                return dev.openallay.settings.ClientSettingsService.HistoryRuntimeState.disconnected();
            }
            public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory() { throw new AssertionError("Unexpected delete"); }
            public CompletableFuture<ToolResult<Boolean>> deleteActorHistory() { throw new AssertionError("Unexpected delete"); }
            public CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() { throw new AssertionError("Unexpected reset"); }
        };
    }

    private static dev.openallay.settings.ClientSettingsService capabilityService(
            dev.openallay.settings.model.ModelSettingsBackend models,
            ModelProfilesConfigLoader.Load initial,
            dev.openallay.settings.capability.CapabilitySettingsBackend capabilities,
            ManualExecutor dispatcher,
            ManualExecutor worker) {
        var recipes = dev.openallay.settings.capability.RecipeSettingsView.defaults();
        return new dev.openallay.settings.ClientSettingsService(
                dev.openallay.guide.ui.GuideDisplayConfig.defaults(), models.state(initial), Set.of(), models,
                metadataActions(), capabilities.currentView(), capabilities, recipes,
                new dev.openallay.settings.ClientSettingsService.RecipeActions() {
                    public ToolResult<dev.openallay.settings.capability.RecipeSettingsView> saveRecipes(
                            dev.openallay.recipe.config.RecipeClientConfig candidate) {
                        throw new AssertionError("Recipe saving is outside this publication test");
                    }
                    public ToolResult<dev.openallay.settings.capability.RecipeSettingsView> reloadRecipes() {
                        return new ToolResult.Success<>(recipes);
                    }
                }, dispatcher::execute, worker, null);
    }

    private static dev.openallay.settings.ClientSettingsService.MetadataActions metadataActions() {
        return new dev.openallay.settings.ClientSettingsService.MetadataActions() {
            public CompletableFuture<Void> refresh() { return CompletableFuture.completedFuture(null); }
            public CompletableFuture<Void> closeAsync() { return CompletableFuture.completedFuture(null); }
        };
    }

    private static dev.openallay.settings.model.ModelConnectionProbe unusedProbe() {
        return new dev.openallay.settings.model.ModelConnectionProbe(
                config -> { throw new AssertionError("Publication tests must not contact a provider"); },
                java.time.Clock.systemUTC(), System::nanoTime);
    }

    private static ModelProfilesConfig loadedConfig(Path profiles) {
        var loaded = new ModelProfilesConfigLoader().load(profiles,
                Map.of("KEY_A", "fixture-a", "KEY_B", "fixture-b"));
        return ((ToolResult.Success<ModelProfilesConfigLoader.Load>) assertInstanceOf(
                ToolResult.Success.class, loaded)).value().config();
    }

    private static final class ManualExecutor implements java.util.concurrent.Executor {
        private final java.util.ArrayDeque<Runnable> tasks = new java.util.ArrayDeque<>();
        public void execute(Runnable task) { tasks.addLast(task); }
        private void runNext() { tasks.removeFirst().run(); }
        private void runLast() { tasks.removeLast().run(); }
        private void runAll() { while (!tasks.isEmpty()) runNext(); }
    }

    private static ClientModelRuntimeRegistry registry(
            ModelProfilesConfigLoader.Load load,
            Map<String, ModelClient> clients) {
        return registry(runtime(), load, clients);
    }

    private static ClientModelRuntimeRegistry registry(
            OpenAllayRuntime runtime,
            ModelProfilesConfigLoader.Load load,
            Map<String, ModelClient> clients) {
        return new ClientModelRuntimeRegistry(
                runtime,
                load,
                new Gson(),
                Runnable::run,
                null,
                profile -> clients.get(profile.definition().id()));
    }

    private static ModelProfilesConfigLoader.Load replaceTestCredential(
            ModelProfilesConfigLoader.Load load, String value) {
        return new ModelProfilesConfigLoader.Load(load.config(), load.profiles().stream().map(profile -> {
            if (!profile.available()) return profile;
            ModelConfig config = profile.runtimeConfig();
            return new ResolvedModelProfile(profile.definition(), new ModelConfig(config.enabled(), config.protocol(),
                    config.baseUri(), config.model(), SecretValue.of(value), config.contextWindowTokens(),
                    config.maxOutputTokens(), config.connectTimeout(), config.requestTimeout()), null);
        }).toList());
    }

    private static ModelProfilesConfigLoader.Load load(
            String defaultId,
            String... ids) {
        List<ModelProfileDefinition> definitions = new ArrayList<>();
        List<ResolvedModelProfile> resolved = new ArrayList<>();
        for (String id : ids) {
            ModelProfileDefinition definition = new ModelProfileDefinition(
                    id,
                    "Profile " + id,
                    true,
                    ModelProtocol.OPENAI_CHAT,
                    URI.create("https://" + id + ".example/v1"),
                    "model-" + id,
                    "KEY_" + id.toUpperCase(),
                    128_000,
                    4096,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(300),
                    null);
            definitions.add(definition);
            resolved.add(new ResolvedModelProfile(
                    definition,
                    new ModelConfig(
                            true,
                            definition.protocol(),
                            definition.baseUri(),
                            definition.model(),
                            SecretValue.of("secret-" + id),
                            definition.contextWindowTokens(),
                            definition.maxOutputTokens(),
                            definition.connectTimeout(),
                            definition.requestTimeout()),
                    null));
        }
        return new ModelProfilesConfigLoader.Load(
                new ModelProfilesConfig(
                        defaultId, definitions),
                resolved);
    }

    private static OpenAllayRuntime runtime() {
        ToolRegistry tools = new ToolRegistry();
        return new OpenAllayRuntime(
                new PlatformService() {
                    @Override public String platformName() { return "test"; }
                    @Override public String gameVersion() { return "26.2-test"; }
                    @Override public boolean isModLoaded(String modId) { return false; }
                    @Override public boolean isDevelopmentEnvironment() { return true; }
                },
                tools,
                new KnowledgeRegistry(),
                new PatchouliMultiblockStore(),
                new SkillRepository(new SkillParser(), List.of()),
                new DevelopmentToolInspector(tools),
                null);
    }

    private static OpenAllayRuntime runtimeWithFactTool() {
        ToolRegistry tools = new ToolRegistry();
        tools.register("test-provider", List.of(new Tool<FactInput, FactOutput>() {
            private final ToolDescriptor<FactInput, FactOutput> descriptor = new ToolDescriptor<>(
                    "test:fact",
                    "Return a fact",
                    FactInput.class,
                    FactOutput.class,
                    ToolAccess.READ_ONLY,
                    Set.of(ContextCapability.RECIPES));

            @Override public ToolDescriptor<FactInput, FactOutput> descriptor() { return descriptor; }
            @Override
            public ToolResult<FactOutput> invoke(ToolInvocationContext context, FactInput input) {
                return new ToolResult.Success<>(new FactOutput(input.value()));
            }
        }));
        return new OpenAllayRuntime(
                new PlatformService() {
                    @Override public String platformName() { return "test"; }
                    @Override public String gameVersion() { return "26.2-test"; }
                    @Override public boolean isModLoaded(String modId) { return false; }
                    @Override public boolean isDevelopmentEnvironment() { return true; }
                },
                tools,
                new KnowledgeRegistry(),
                new PatchouliMultiblockStore(),
                new SkillRepository(new SkillParser(), List.of("test:fact")),
                new DevelopmentToolInspector(tools),
                null);
    }

    private record FactInput(int value) {}
    private record FactOutput(int value) {}

    private static ModelTurn toolTurn(String id, String name, int value) {
        com.google.gson.JsonObject input = new com.google.gson.JsonObject();
        input.addProperty("value", value);
        return new ModelTurn(
                "test",
                "model-a",
                List.of(new ModelContent.ToolUse(id, name, input)),
                "tool_use",
                ModelUsage.empty());
    }

    private static ClientCapabilitySnapshot success(ToolResult<ClientCapabilitySnapshot> result) {
        return ((ToolResult.Success<ClientCapabilitySnapshot>) assertInstanceOf(
                ToolResult.Success.class, result)).value();
    }

    private static ModelTurn turn(String model) {
        return new ModelTurn(
                "test",
                model,
                List.of(new ModelContent.Text("answer-" + model)),
                "end_turn",
                ModelUsage.empty());
    }

    private static final class RecordingModel implements ModelClient {
        private final String model;
        private final CompletableFuture<ModelTurn> fixed;
        private final List<ModelRequest> requests = new ArrayList<>();

        private RecordingModel(String model) {
            this(model, null);
        }

        private RecordingModel(String model, CompletableFuture<ModelTurn> fixed) {
            this.model = model;
            this.fixed = fixed;
        }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request,
                Consumer<ModelEvent> events,
                CancellationSignal cancellation) {
            requests.add(request);
            return fixed == null ? CompletableFuture.completedFuture(turn(model)) : fixed;
        }
    }

    private static final class ToolSequenceModel implements ModelClient {
        private final CompletableFuture<ModelTurn> first;
        private final List<ModelRequest> requests = new ArrayList<>();

        private ToolSequenceModel(CompletableFuture<ModelTurn> first) {
            this.first = first;
        }

        @Override
        public CompletableFuture<ModelTurn> complete(
                ModelRequest request,
                Consumer<ModelEvent> events,
                CancellationSignal cancellation) {
            requests.add(request);
            if (requests.size() == 1) {
                return first;
            }
            return CompletableFuture.completedFuture(new ModelTurn(
                    "test",
                    "model-a",
                    List.of(new ModelContent.Text(requests.size() == 2
                            ? "tool-complete"
                            : "next-complete")),
                    "end_turn",
                    ModelUsage.empty()));
        }
    }
}
