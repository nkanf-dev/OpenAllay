package dev.openallay.server;

import com.google.gson.Gson;
import dev.openallay.FeatureServices;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.context.ContextCompactor;
import dev.openallay.model.tokenizer.ModelContextTokenEstimator;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.bridge.server.PlayerClientToolRouter;
import dev.openallay.model.ModelClient;
import dev.openallay.model.anthropic.AnthropicMessagesClient;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelConfigLoader;
import dev.openallay.model.openai.OpenAiChatClient;
import dev.openallay.model.scheduling.ModelRequestScheduler;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.util.Map;
import java.time.Clock;

public record ServerGuideRuntime(
        ModelConfig config,
        ServerAgentService service,
        dev.openallay.guide.GuideContextSpec contextSpec,
        PlayerClientToolRouter clientTools) {
    public dev.openallay.model.metadata.ModelImageCapabilityResolution imageCapability() {
        return config.imageCapability();
    }

    /** Application bridge JSON envelope selected by the captured runtime protocol.
     * Wire history/Skill metadata and native provider bodies have different overhead;
     * this cap does not claim that the two encodings have the same byte size.
     */
    public int requestBodyLimit() {
        return switch (config.protocol()) {
            case ANTHROPIC_MESSAGES -> dev.openallay.bridge.protocol.BridgeProtocol.MAX_ANTHROPIC_REQUEST_BYTES;
            case OPENAI_CHAT -> dev.openallay.bridge.protocol.BridgeProtocol.MAX_OPENAI_REQUEST_BYTES;
        };
    }

    public static ToolResult<ServerGuideRuntime> create(
            FeatureServices runtime,
            Path configPath,
            Map<String, String> environment,
            ServerAgentService.ContextProvider contexts,
            ServerGuideEvents events) {
        return create(
                runtime,
                configPath,
                environment,
                contexts,
                events,
                new PlayerClientToolRouter.Transport() {
                    @Override
                    public boolean call(
                            java.util.UUID actorId,
                            dev.openallay.bridge.protocol.ClientToolCallPayload payload) {
                        return false;
                    }

                    @Override
                    public void cancel(
                            java.util.UUID actorId,
                            dev.openallay.bridge.protocol.ClientToolCancelPayload payload) {}
                });
    }

    public static ToolResult<ServerGuideRuntime> create(
            FeatureServices runtime,
            Path configPath,
            Map<String, String> environment,
            ServerAgentService.ContextProvider contexts,
            ServerGuideEvents events,
            PlayerClientToolRouter.Transport clientToolTransport) {
        return create(runtime, configPath, environment, contexts, events, clientToolTransport,
                configPath.toAbsolutePath().normalize().getParent().resolve("image-store"));
    }

    public static ToolResult<ServerGuideRuntime> create(
            FeatureServices runtime,
            Path configPath,
            Map<String, String> environment,
            ServerAgentService.ContextProvider contexts,
            ServerGuideEvents events,
            PlayerClientToolRouter.Transport clientToolTransport,
            Path worldImageDirectory) {
        ToolResult<ModelConfig> loaded = new ModelConfigLoader().load(configPath, environment);
        if (loaded instanceof ToolResult.Failure<ModelConfig> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        ModelConfig config = ((ToolResult.Success<ModelConfig>) loaded).value();
        if (!config.enabled()) {
            return new ToolResult.Failure<>("model_disabled", "Server model is disabled");
        }
        Gson gson = dev.openallay.json.EngineJson.create();
        ModelClient raw = switch (config.protocol()) {
            case ANTHROPIC_MESSAGES -> new AnthropicMessagesClient(config, gson);
            case OPENAI_CHAT -> new OpenAiChatClient(config, gson);
        };
        ModelRequestScheduler scheduled = new ModelRequestScheduler(
                dev.openallay.model.ObservingModelClient.observe(raw, config.model()));
        LocalAgentToolExecutor tools = new LocalAgentToolExecutor(runtime.tools(), gson);
        AgentSessionStore sessions = new AgentSessionStore();
        var estimator = ModelContextTokenEstimator.create(config.protocol(), config.model(), config.tokenEncoding());
        ContextCompactor compactor = new ContextCompactor(
                scheduled, gson, estimator,
                config.contextBudget(), config.model(), Clock.systemUTC());
        PlayerClientToolRouter clientTools = new PlayerClientToolRouter(
                runtime.tools(), gson, clientToolTransport, config.requestTimeout());
        String prompt = systemPrompt(runtime.skills(), false);
        int promptAndTools = estimator.estimate(
                prompt, java.util.List.of(), tools.definitions());
        dev.openallay.guide.GuideContextSpec contextSpec =
                new dev.openallay.guide.GuideContextSpec(
                        config.contextBudget(), promptAndTools, config.model(), estimator);
        dev.openallay.model.image.ImageAttachmentStore imageStore =
                new dev.openallay.model.image.FileImageAttachmentStore(worldImageDirectory);
        ServerAgentService service = new ServerAgentService(
                (actor, payload) -> {
                    boolean experimentalCommands = payload.clientToolIds().contains(
                            dev.openallay.bridge.client.ClientToolExecutionEndpoint
                                    .EXPERIMENTAL_COMMANDS_CAPABILITY);
                    dev.openallay.skill.SkillCatalogSnapshot requestSkills =
                            requestSkills(runtime.skills(), experimentalCommands);
                    ToolResult<dev.openallay.agent.tool.AgentToolExecutor> opened = clientTools.open(
                            actor,
                            payload.requestId(),
                            payload.sessionId(),
                            payload.clientToolIds(),
                            requestSkills,
                            payload.skillDocuments());
                    if (opened instanceof ToolResult.Failure<dev.openallay.agent.tool.AgentToolExecutor>
                            failure) {
                        return new ToolResult.Failure<>(failure.code(), failure.message());
                    }
                    dev.openallay.agent.tool.AgentToolExecutor requestTools =
                            ((ToolResult.Success<dev.openallay.agent.tool.AgentToolExecutor>) opened)
                                    .value();
                    GameGuideAgent agent = new GameGuideAgent(
                            scheduled, requestTools, sessions, gson, compactor, (request, tokens) -> {});
                    return new ToolResult.Success<>(new ServerAgentService.RequestRuntime(
                            agent,
                            requestTools,
                            payload.clientToolIds().contains("openallay:load_skill")
                                    ? systemPrompt(payload.skillDocuments(), experimentalCommands)
                                    : systemPrompt(requestSkills, experimentalCommands),
                            steer -> {
                                if (!steer.requestId().equals(payload.requestId())) {
                                    throw new IllegalArgumentException("Steer runtime request correlation changed");
                                }
                                try {
                                    return importSteerImages(actor, payload.requestId(),
                                            steer.message().toModelMessage(), steer.imageAttachments(),
                                            imageStore, config.imageCapability().capability());
                                } catch (java.io.IOException failure) {
                                    throw new java.io.UncheckedIOException(failure);
                                }
                            },
                            () -> {
                                try { clientTools.close(actor, payload.requestId()); }
                                finally {
                                    try { imageStore.release(actor, steerImageOwner(payload.requestId())); }
                                    catch (java.io.IOException ignored) { /* Keep bytes on cleanup failure. */ }
                                }
                            }));
                },
                sessions,
                contexts,
                events,
                gson,
                prompt,
                scheduled::awaitReady,
                imageStore,
                config.imageCapability().capability());
        ServerGuideRuntime configured = new ServerGuideRuntime(config, service, contextSpec, clientTools);
        clientTools.configureResultPreparation(
                service::prepareClientToolImages, configured.requestBodyLimit(), service::ownsRequest);
        return new ToolResult.Success<>(configured);
    }

    /** Frozen runtime hook for a typed Steer. Call only on the off-thread payload worker. */
    public static dev.openallay.model.ModelMessage importSteerImages(
            java.util.UUID actor,
            java.util.UUID requestId,
            dev.openallay.model.ModelMessage userInput,
            java.util.List<dev.openallay.bridge.protocol.ServerAgentImageAttachment> attachments,
            dev.openallay.model.image.ImageAttachmentStore images,
            dev.openallay.model.image.ImageInputCapability capturedCapability) throws java.io.IOException {
        dev.openallay.agent.AgentRequest.validateUserInput(userInput);
        java.util.Map<String, dev.openallay.model.image.ImageReference> needed = new java.util.LinkedHashMap<>();
        userInput.content().stream().filter(dev.openallay.model.ModelContent.Image.class::isInstance)
                .map(dev.openallay.model.ModelContent.Image.class::cast).forEach(image -> {
                    var previous = needed.putIfAbsent(image.reference().sha256(), image.reference());
                    if (previous != null && !previous.equals(image.reference())) {
                        throw new IllegalArgumentException("Conflicting Steer image metadata");
                    }
                });
        if (!needed.isEmpty() && capturedCapability != dev.openallay.model.image.ImageInputCapability.SUPPORTED) {
            throw new IllegalArgumentException("The captured server model has no confirmed image input support");
        }
        java.util.Map<String, dev.openallay.model.image.ImageReference> supplied = new java.util.LinkedHashMap<>();
        for (var attachment : attachments) {
            if (supplied.putIfAbsent(attachment.reference().sha256(), attachment.reference()) != null) {
                throw new IllegalArgumentException("Duplicate Steer image attachment");
            }
        }
        if (!needed.equals(supplied)) {
            throw new IllegalArgumentException("Steer image attachments must exactly match the message references");
        }
        for (var attachment : attachments) {
            var imported = images.importImage(actor, steerImageOwner(requestId), attachment.bytes());
            if (!imported.equals(attachment.reference())) {
                throw new java.io.IOException("Steer image metadata does not match actual image content");
            }
        }
        // The original request's images remain retained. The scoped resolver may now read these
        // newly uploaded images without acquiring any filesystem or URL authority.
        return userInput;
    }

    private static String steerImageOwner(java.util.UUID requestId) {
        return "server-steer:" + requestId;
    }

    static String systemPrompt(
            dev.openallay.skill.SkillRepository skills, boolean experimentalCommands) {
        return systemPrompt(requestSkills(skills, experimentalCommands), experimentalCommands);
    }

    static String systemPrompt(dev.openallay.skill.SkillCatalogSnapshot skills) {
        return systemPrompt(skills, false);
    }

    static String systemPrompt(dev.openallay.skill.SkillCatalogSnapshot skills, boolean commandsAvailable) {
        return dev.openallay.agent.AgentSystemPrompt.compose(
                skills.metadataPrompt(),
                dev.openallay.script.schema.CoreJavascriptContract.render(
                        dev.openallay.script.data.MinecraftAgentHostGraph.declaredOnlyCatalog()),
                false, "", commandsAvailable);
    }

    static String systemPrompt(dev.openallay.skill.SkillCatalogManifest skills) {
        return systemPrompt(skills, false);
    }

    static String systemPrompt(dev.openallay.skill.SkillCatalogManifest skills, boolean commandsAvailable) {
        return dev.openallay.agent.AgentSystemPrompt.compose(
                skills.metadataPrompt(),
                dev.openallay.script.schema.CoreJavascriptContract.render(
                        dev.openallay.script.data.MinecraftAgentHostGraph.declaredOnlyCatalog()),
                false, "", commandsAvailable);
    }

    /** Captures matching prompt and server-local load_skill documents for one request. */
    static dev.openallay.skill.SkillCatalogSnapshot requestSkills(
            dev.openallay.skill.SkillRepository skills, boolean experimentalCommands) {
        java.util.Objects.requireNonNull(skills, "skills");
        return experimentalCommands
                ? skills.snapshotWithRuntimeEnabled(java.util.Set.of(), java.util.Set.of("run-game-commands"))
                : skills.snapshot(java.util.Set.of());
    }
}
