package dev.openallay.server;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
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
    public static ToolResult<ServerGuideRuntime> create(
            OpenAllayRuntime runtime,
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
            OpenAllayRuntime runtime,
            Path configPath,
            Map<String, String> environment,
            ServerAgentService.ContextProvider contexts,
            ServerGuideEvents events,
            PlayerClientToolRouter.Transport clientToolTransport) {
        ToolResult<ModelConfig> loaded = new ModelConfigLoader().load(configPath, environment);
        if (loaded instanceof ToolResult.Failure<ModelConfig> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        ModelConfig config = ((ToolResult.Success<ModelConfig>) loaded).value();
        if (!config.enabled()) {
            return new ToolResult.Failure<>("model_disabled", "Server model is disabled");
        }
        Gson gson = new Gson();
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
                            () -> clientTools.close(actor, payload.requestId())));
                },
                sessions,
                contexts,
                events,
                gson,
                prompt,
                scheduled::awaitReady);
        return new ToolResult.Success<>(
                new ServerGuideRuntime(config, service, contextSpec, clientTools));
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
