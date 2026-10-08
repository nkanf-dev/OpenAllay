package dev.openallay.client;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.client.context.ClientContextCapture;
import dev.openallay.client.resource.MinecraftClientResourceAccess;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.GuideContextProvider;
import dev.openallay.integration.ftb.quests.FtbQuestsKnowledgeProvider;
import dev.openallay.integration.ftb.quests.ReflectiveFtbQuestsBridge;
import dev.openallay.integration.patchouli.PatchouliKnowledgeProvider;
import dev.openallay.knowledge.KnowledgeSourceProvider;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.recipe.RecipeProviderReadiness;
import dev.openallay.recipe.RecipeProviderReadinessGate;
import dev.openallay.tool.ToolResult;
import dev.openallay.script.command.MinecraftCommandCapture;
import dev.openallay.world.MinecraftClientWorldObservationCoordinator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public final class MinecraftGuideContextProvider implements GuideContextProvider {
    private final OpenAllayRuntime runtime;
    private final net.minecraft.client.Minecraft client;
    private final Gson gson;
    private final ClassLoader integrationLoader;
    private final RecipeClientRuntime recipeClient;
    private volatile dev.openallay.script.UnrestrictedJavascriptRuntime unrestrictedJavascript;
    private final RecipeProviderReadinessGate recipeReadiness = new RecipeProviderReadinessGate();
    private final java.util.Map<String, dev.openallay.world.ClientObservationAnchor> inputObservations =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, List<dev.openallay.model.image.ImageReference>>
            detachedObservationImages = new java.util.concurrent.ConcurrentHashMap<>();

    public MinecraftGuideContextProvider(
            OpenAllayRuntime runtime,
            net.minecraft.client.Minecraft client,
            Gson gson,
            ClassLoader integrationLoader) {
        this(runtime, client, gson, integrationLoader, RecipeClientRuntime.defaults());
    }

    public MinecraftGuideContextProvider(
            OpenAllayRuntime runtime,
            net.minecraft.client.Minecraft client,
            Gson gson,
            ClassLoader integrationLoader,
            RecipeClientRuntime recipeClient) {
        this.runtime = runtime;
        this.client = client;
        this.gson = gson;
        this.integrationLoader = integrationLoader;
        this.recipeClient = java.util.Objects.requireNonNull(recipeClient, "recipeClient");
    }

    public void setUnrestrictedJavascriptRuntime(dev.openallay.script.UnrestrictedJavascriptRuntime runtime) {
        this.unrestrictedJavascript = java.util.Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public void associateInputObservation(String correlationId,
            java.util.Optional<dev.openallay.world.ClientObservationAnchor> observation) {
        java.util.Objects.requireNonNull(correlationId, "correlationId");
        java.util.Objects.requireNonNull(observation, "observation");
        if (observation.isPresent()) inputObservations.put(correlationId, dev.openallay.util.Java8ApiSupport.orElseThrow(observation));
        else inputObservations.remove(correlationId);
    }

    @Override
    public void freezeRequest(String correlationId, boolean clientLocalModel) {
        freezeJavascriptAndCommands(correlationId, clientLocalModel);
    }

    private boolean freezeJavascriptAndCommands(String correlationId, boolean clientLocalModel) {
        dev.openallay.script.UnrestrictedJavascriptRuntime javascript = unrestrictedJavascript;
        if (javascript != null && !clientLocalModel) javascript.freezeDisabled(correlationId);
        boolean unrestricted = clientLocalModel && javascript != null
                && javascript.freeze(correlationId);
        runtime.commands().freezeRequest(correlationId, unrestricted);
        return unrestricted;
    }

    @Override
    public void closeRequest(String correlationId) {
        inputObservations.remove(correlationId);
        runtime.commands().closeRequest(correlationId);
        runtime.extensions().closeJavascriptRequest(correlationId);
        dev.openallay.script.UnrestrictedJavascriptRuntime javascript = unrestrictedJavascript;
        if (javascript != null) javascript.close(correlationId);
        runtime.worldObservations().closeObservations(correlationId);
    }

    @Override
    public List<dev.openallay.model.image.ImageReference> observationImageReferences(String correlationId) {
        List<dev.openallay.model.image.ImageReference> detached = detachedObservationImages.get(correlationId);
        return detached != null ? detached : runtime.worldObservations().producerReferences(correlationId);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> releaseObservationImages(String correlationId) {
        // Exact detached requests are released by their detach callback after the whole
        // connection custody barrier. Do not look up and close a new same-correlation request.
        if (detachedObservationImages.containsKey(correlationId)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return runtime.worldObservations().releaseImageProducers(correlationId);
    }

    @Override
    public ToolResult<ToolInvocationContext> capture(
            Set<ContextCapability> capabilities, String correlationId) {
        return capture(capabilities, correlationId, true);
    }

    public ToolResult<ToolInvocationContext> captureServerToolContext(
            Set<ContextCapability> capabilities, String correlationId) {
        return capture(capabilities, correlationId, false);
    }

    private ToolResult<ToolInvocationContext> capture(
            Set<ContextCapability> capabilities, String correlationId, boolean clientLocalModel) {
        boolean unrestricted = freezeJavascriptAndCommands(correlationId, clientLocalModel);
        if (client.player == null) {
            return new ToolResult.Failure<>(
                    "player_required", "No client player is connected");
        }
        try {
            ToolResult<Integer> refreshed = refreshKnowledge();
            final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<java.lang.Integer> value; ToolResult.Failure<Integer> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = refreshed) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<Integer>) $oaPattern0_holder.value) != null))) {
                return new ToolResult.Failure<>($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
            }
            ToolInvocationContext context =
                    new ClientContextCapture(gson, runtime.platform(), recipeClient)
                            .capture(client, capabilities, correlationId, unrestricted);
            MinecraftCommandCapture.capture(
                    client, runtime.commands(), correlationId, context.capturedAt());
            context.player().ifPresent(player -> runtime.worldObservations().capture(
                    correlationId,
                    new MinecraftClientWorldObservationCoordinator(
                            client,
                            runtime.platform(),
                            player.uuid(),
                            player.dimension(),
                            runtime.worldObservations(),
                            correlationId)));
            dev.openallay.world.ClientObservationAnchor observation = inputObservations.get(correlationId);
            if (observation != null && context.player().isPresent()) {
                if (!observation.focus().actorId().equals(dev.openallay.util.Java8ApiSupport.orElseThrow(context.player()).uuid())) {
                    throw new IllegalArgumentException("Input reference belongs to another player");
                }
                runtime.worldObservations().associate(correlationId, observation);
            }
            return new ToolResult.Success<>(context);
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "context_capture_failed",
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage());
        }
    }

    public RecipeProviderReadiness recipeProviderReadiness() {
        try {
            return new ClientContextCapture(gson, runtime.platform(), recipeClient)
                    .recipeProviderReadiness(client, recipeReadiness);
        } catch (RuntimeException failure) {
            return RecipeProviderReadiness.failed(
                    "recipe_readiness_failed", "Recipe provider readiness check failed");
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> detachConnectionState(
            java.util.concurrent.CompletableFuture<Void> custody) {
        runtime.knowledge().clearConnectionState();
        java.util.Map<java.lang.String, java.util.List<dev.openallay.model.image.ImageReference>> references = runtime.worldObservations().producerReferenceSnapshot();
        java.util.Map<String, List<dev.openallay.model.image.ImageReference>> installed = new java.util.LinkedHashMap<>();
        references.forEach((correlation, images) -> installed.put(correlation,
                detachedObservationImages.compute(correlation, (key, previous) -> {
                    List<dev.openallay.model.image.ImageReference> union = new ArrayList<>(
                            previous == null ? dev.openallay.util.Java8Collections.listOf() : previous);
                    union.addAll(images);
                    return dev.openallay.model.image.ModelImages.unique(union);
                })));
        return runtime.worldObservations().detachConnectionState(custody).thenRun(() ->
                installed.forEach((correlation, images) -> detachedObservationImages.computeIfPresent(
                        correlation, (key, current) -> current == images ? null : current)));
    }

    @Override
    public void clearConnectionState() {
        inputObservations.clear();
        runtime.knowledge().clearConnectionState();
        runtime.worldObservations().clearConnectionState();
    }

    @Override
    public ToolResult<Integer> refreshKnowledge() {
        try {
            List<KnowledgeSourceProvider> providers = new ArrayList<>();
            providers.add(new PatchouliKnowledgeProvider(
                    new MinecraftClientResourceAccess(client.getResourceManager()),
                    MinecraftNativeClientFacts.selectedLanguage(client),
                    runtime.patchouliMultiblocks(),
                    runtime.platform().gameVersion(),
                    runtime.platform().platformName()));
            if (runtime.platform().isModLoaded("ftbquests") && client.player != null) {
                providers.add(new FtbQuestsKnowledgeProvider(
                        new ReflectiveFtbQuestsBridge(integrationLoader),
                        client.player,
                        true,
                        runtime.platform().gameVersion(),
                        runtime.platform().platformName()));
            }
            if (!runtime.knowledge().reload(providers)) {
                return new ToolResult.Failure<>(
                        "knowledge_refresh_failed", runtime.knowledge().diagnostics().toString());
            }
            return new ToolResult.Success<>(runtime.knowledge().snapshot().documents().size());
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "knowledge_refresh_failed",
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage());
        }
    }
}
