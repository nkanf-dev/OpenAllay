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

@dev.openallay.value.ValueType(ServerGuideRuntime.ValueSchemaProvider.class)
public final class ServerGuideRuntime {
    private final ModelConfig config;
    private final ServerAgentService service;
    private final dev.openallay.guide.GuideContextSpec contextSpec;
    private final PlayerClientToolRouter clientTools;
    public ServerGuideRuntime(ModelConfig config, ServerAgentService service, dev.openallay.guide.GuideContextSpec contextSpec, PlayerClientToolRouter clientTools) {
        this.config = config;
        this.service = service;
        this.contextSpec = contextSpec;
        this.clientTools = clientTools;
    }
    public ModelConfig config() { return config; }
    public ServerAgentService service() { return service; }
    public dev.openallay.guide.GuideContextSpec contextSpec() { return contextSpec; }
    public PlayerClientToolRouter clientTools() { return clientTools; }
public dev.openallay.model.metadata.ModelImageCapabilityResolution imageCapability() {
        return config.imageCapability();
    }
public int requestBodyLimit() {
        {
int $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((config.protocol())) {
case ANTHROPIC_MESSAGES:
{
$oaSwitch1_exit_result = dev.openallay.bridge.protocol.BridgeProtocol.MAX_ANTHROPIC_REQUEST_BYTES; break $oaSwitch1_exit;
}
case OPENAI_CHAT:
{
$oaSwitch1_exit_result = dev.openallay.bridge.protocol.BridgeProtocol.MAX_OPENAI_REQUEST_BYTES; break $oaSwitch1_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch1_exit_result;
}
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
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelConfig> value; ToolResult.Failure<ModelConfig> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<ModelConfig>) $oaPattern0_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
        }
        ModelConfig config = ((ToolResult.Success<ModelConfig>) loaded).value();
        if (!config.enabled()) {
            return new ToolResult.Failure<>("model_disabled", "Server model is disabled");
        }
        Gson gson = dev.openallay.json.EngineJson.create();
        dev.openallay.model.ModelClient $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((config.protocol())) {
case ANTHROPIC_MESSAGES:
{
$oaSwitch0_exit_result = new AnthropicMessagesClient(config, gson); break $oaSwitch0_exit;
}
case OPENAI_CHAT:
{
$oaSwitch0_exit_result = new OpenAiChatClient(config, gson); break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
ModelClient raw = $oaSwitch0_exit_result;
        ModelRequestScheduler scheduled = new ModelRequestScheduler(
                dev.openallay.model.ObservingModelClient.observe(raw, config.model()));
        LocalAgentToolExecutor tools = new LocalAgentToolExecutor(runtime.tools(), gson);
        AgentSessionStore sessions = new AgentSessionStore();
        dev.openallay.model.tokenizer.ModelContextTokenEstimator estimator = ModelContextTokenEstimator.create(config.protocol(), config.model(), config.tokenEncoding());
        ContextCompactor compactor = new ContextCompactor(
                scheduled, gson, estimator,
                config.contextBudget(), config.model(), Clock.systemUTC());
        PlayerClientToolRouter clientTools = new PlayerClientToolRouter(
                runtime.tools(), gson, clientToolTransport, config.requestTimeout());
        String prompt = systemPrompt(runtime.skills(), false);
        int promptAndTools = estimator.estimate(
                prompt, dev.openallay.util.Java8Collections.listOf(), tools.definitions());
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
                    final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.agent.tool.AgentToolExecutor> value; ToolResult.Failure<dev.openallay.agent.tool.AgentToolExecutor> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = opened) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<dev.openallay.agent.tool.AgentToolExecutor>) $oaPattern1_holder.value) != null))) {
                        return new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message());
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
                                try { clientTools.close(actor, payload.requestId(), requestTools); }
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
                    dev.openallay.model.image.ImageReference previous = needed.putIfAbsent(image.reference().sha256(), image.reference());
                    if (previous != null && !previous.equals(image.reference())) {
                        throw new IllegalArgumentException("Conflicting Steer image metadata");
                    }
                });
        if (!needed.isEmpty() && capturedCapability != dev.openallay.model.image.ImageInputCapability.SUPPORTED) {
            throw new IllegalArgumentException("The captured server model has no confirmed image input support");
        }
        java.util.Map<String, dev.openallay.model.image.ImageReference> supplied = new java.util.LinkedHashMap<>();
        for (dev.openallay.bridge.protocol.ServerAgentImageAttachment attachment : attachments) {
            if (supplied.putIfAbsent(attachment.reference().sha256(), attachment.reference()) != null) {
                throw new IllegalArgumentException("Duplicate Steer image attachment");
            }
        }
        if (!needed.equals(supplied)) {
            throw new IllegalArgumentException("Steer image attachments must exactly match the message references");
        }
        for (dev.openallay.bridge.protocol.ServerAgentImageAttachment attachment : attachments) {
            dev.openallay.model.image.ImageReference imported = images.importImage(actor, steerImageOwner(requestId), attachment.bytes());
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
static dev.openallay.skill.SkillCatalogSnapshot requestSkills(
            dev.openallay.skill.SkillRepository skills, boolean experimentalCommands) {
        java.util.Objects.requireNonNull(skills, "skills");
        return experimentalCommands
                ? skills.snapshotWithRuntimeEnabled(dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf("run-game-commands"))
                : skills.snapshot(dev.openallay.util.Java8Collections.setOf());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerGuideRuntime)) return false;
        ServerGuideRuntime that = (ServerGuideRuntime) other;
        return java.util.Objects.equals(config, that.config) && java.util.Objects.equals(service, that.service) && java.util.Objects.equals(contextSpec, that.contextSpec) && java.util.Objects.equals(clientTools, that.clientTools);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + java.util.Objects.hashCode(service);
        hash = 31 * hash + java.util.Objects.hashCode(contextSpec);
        hash = 31 * hash + java.util.Objects.hashCode(clientTools);
        return hash;
    }
    @Override public String toString() { return "ServerGuideRuntime[config=" + config + ", service=" + service + ", contextSpec=" + contextSpec + ", clientTools=" + clientTools + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerGuideRuntime> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerGuideRuntime.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerGuideRuntime>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerGuideRuntime.class, "config", ServerGuideRuntime::config), new dev.openallay.value.ValueSchema.Component<>(ServerGuideRuntime.class, "service", ServerGuideRuntime::service), new dev.openallay.value.ValueSchema.Component<>(ServerGuideRuntime.class, "contextSpec", ServerGuideRuntime::contextSpec), new dev.openallay.value.ValueSchema.Component<>(ServerGuideRuntime.class, "clientTools", ServerGuideRuntime::clientTools)), arguments -> new ServerGuideRuntime((ModelConfig) arguments[0], (ServerAgentService) arguments[1], (dev.openallay.guide.GuideContextSpec) arguments[2], (PlayerClientToolRouter) arguments[3]));
        }
    }
}
