package dev.openallay.client;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.ClientCapabilityResolver;
import dev.openallay.capability.ClientCapabilitySnapshot;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.GuideClientModelProfile;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideContextSpec;
import dev.openallay.guide.GuideLocalEndpoint;
import dev.openallay.guide.GuideModelProfileException;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.ProviderModelClients;
import dev.openallay.agent.trace.LiveTraceStore;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/** Atomic named-profile registry whose runtimes share provider-neutral sessions. */
public final class ClientModelRuntimeRegistry implements GuideLocalEndpoint {
    private final Gson gson;
    private final ClientEventDispatcher dispatcher;
    private final AgentToolExecutor extension;
    private final Function<ResolvedModelProfile, ModelClient> modelFactory;
    private final AgentSessionStore sessions = new AgentSessionStore();
    private final AtomicReference<State> state = new AtomicReference<>();
    private final Path traceDirectory;
    private final java.util.function.BooleanSupplier tracePersistenceEnabled;

    ClientModelRuntimeRegistry(
            OpenAllayRuntime productRuntime,
            ModelProfilesConfigLoader.Load initial,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Function<ResolvedModelProfile, ModelClient> modelFactory) {
        this(productRuntime, initial, gson, dispatcher, extension, modelFactory,
                null, () -> false);
    }

    ClientModelRuntimeRegistry(
            OpenAllayRuntime productRuntime,
            ModelProfilesConfigLoader.Load initial,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Function<ResolvedModelProfile, ModelClient> modelFactory,
            Path traceDirectory,
            java.util.function.BooleanSupplier tracePersistenceEnabled) {
        Objects.requireNonNull(productRuntime, "productRuntime");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.extension = extension;
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.traceDirectory = traceDirectory == null
                ? null : traceDirectory.toAbsolutePath().normalize();
        this.tracePersistenceEnabled = Objects.requireNonNull(
                tracePersistenceEnabled, "tracePersistenceEnabled");
        state.set(build(initial, modelFactory, resolveDefaultCapabilities(productRuntime)));
    }

    public static ToolResult<ClientModelRuntimeRegistry> create(
            OpenAllayRuntime runtime,
            Path profilesPath,
            Map<String, String> environment,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension) {
        ToolResult<ModelProfilesConfigLoader.Load> loaded = new ModelProfilesConfigLoader()
                .load(profilesPath, environment);
        if (loaded instanceof ToolResult.Failure<ModelProfilesConfigLoader.Load> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        Gson gson = new Gson();
        ModelProfilesConfigLoader.Load value =
                ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value();
        return new ToolResult.Success<>(create(
                runtime, value, gson, dispatcher, extension));
    }

    public static ClientModelRuntimeRegistry create(
            OpenAllayRuntime runtime,
            ModelProfilesConfigLoader.Load initial,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension) {
        return create(runtime, initial, gson, dispatcher, extension, null, () -> false);
    }

    public static ClientModelRuntimeRegistry create(
            OpenAllayRuntime runtime,
            ModelProfilesConfigLoader.Load initial,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Path traceDirectory,
            java.util.function.BooleanSupplier tracePersistenceEnabled) {
        Function<ResolvedModelProfile, ModelClient> factory = profile ->
                ProviderModelClients.create(profile.runtimeConfig(), gson);
        return new ClientModelRuntimeRegistry(
                runtime, initial, gson, dispatcher, extension, factory,
                traceDirectory, tracePersistenceEnabled);
    }

    public void replace(
            ModelProfilesConfigLoader.Load replacement,
            Function<ResolvedModelProfile, ModelClient> replacementFactory) {
        Objects.requireNonNull(replacementFactory, "replacementFactory");
        State expected = state.get();
        new PreparedReplacement(this, expected,
                build(replacement, replacementFactory, expected.capabilities())).publishCommitted();
    }

    public void replace(ModelProfilesConfigLoader.Load replacement) {
        prepare(replacement).publishCommitted();
    }

    public PreparedReplacement prepare(
            ModelProfilesConfigLoader.Load replacement) {
        State expected = state.get();
        return new PreparedReplacement(this, expected,
                build(replacement, modelFactory, expected.capabilities()));
    }

    /** Publishes a prepared capability view for future requests without replacing endpoints. */
    public synchronized void replaceCapabilities(ClientCapabilitySnapshot replacement) {
        Objects.requireNonNull(replacement, "replacement");
        State current = state.get();
        state.set(current.withCapabilities(replacement));
    }

    public ClientCapabilitySnapshot capabilities() {
        return state.get().capabilities();
    }

    /** Returns one complete recorded local Agent trace. Model credentials are not trace inputs. */
    public java.util.Optional<String> encodedTrace(String profileId, UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        ClientGuideRuntime runtime;
        try {
            runtime = runtime(state.get(), profileId);
        } catch (GuideModelProfileException unavailable) {
            return java.util.Optional.empty();
        }
        return runtime.traces().find(requestId)
                .map(ignored -> runtime.traces().encoded(requestId));
    }

    Object endpointIdentity(String profileId) {
        return runtime(state.get(), profileId).endpointIdentity();
    }

    @Override
    public String defaultProfileId() {
        return state.get().defaultProfileId();
    }

    @Override
    public List<GuideClientModelProfile> profiles() {
        return state.get().profiles();
    }

    @Override
    public Set<ContextCapability> requiredContext() {
        State captured = state.get();
        ClientGuideRuntime runtime = captured.runtimes().get(captured.defaultProfileId());
        return runtime == null ? Set.of() : runtime.requiredContext();
    }

    @Override
    public Set<ContextCapability> requiredContext(String profileId) {
        return runtime(state.get(), profileId).requiredContext();
    }

    @Override
    public java.util.Optional<GuideContextSpec> contextSpec(String profileId) {
        return runtime(state.get(), profileId).contextSpec(profileId);
    }

    @Override
    public java.util.Optional<dev.openallay.guide.GuideContextEstimate> contextEstimate(
            String profileId, UUID actor, String sessionId) {
        try {
            return runtime(state.get(), profileId).contextEstimate(profileId, actor, sessionId);
        } catch (GuideModelProfileException unavailable) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public CompletableFuture<AgentResult> ask(
            UUID actor,
            String sessionId,
            UUID requestId,
            String question,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        State captured = state.get();
        return ask(captured, captured.defaultProfileId(), actor, sessionId, requestId,
                ModelMessage.userText(question), dev.openallay.agent.AgentRequest.unavailableImages(), context, events);
    }

    @Override
    public CompletableFuture<AgentResult> ask(
            String profileId,
            UUID actor,
            String sessionId,
            UUID requestId,
            String question,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        return ask(profileId, actor, sessionId, requestId, ModelMessage.userText(question),
                dev.openallay.agent.AgentRequest.unavailableImages(), context, events);
    }

    @Override
    public CompletableFuture<AgentResult> ask(
            UUID actor, String sessionId, UUID requestId, ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images,
            ToolInvocationContext context, Consumer<AgentEvent> events) {
        State captured = state.get();
        return ask(captured, captured.defaultProfileId(), actor, sessionId,
                requestId, userInput, images, context, events);
    }

    @Override
    public CompletableFuture<AgentResult> ask(
            String profileId, UUID actor, String sessionId, UUID requestId,
            ModelMessage userInput, dev.openallay.model.image.ImagePayloadResolver images,
            ToolInvocationContext context, Consumer<AgentEvent> events) {
        return ask(state.get(), profileId, actor, sessionId, requestId, userInput, images, context, events);
    }

    private CompletableFuture<AgentResult> ask(
            State captured, String profileId, UUID actor, String sessionId, UUID requestId,
            ModelMessage userInput, dev.openallay.model.image.ImagePayloadResolver images,
            ToolInvocationContext context, Consumer<AgentEvent> events) {
        try {
            dev.openallay.agent.AgentRequest.validateUserInput(userInput);
            ClientGuideRuntime selected = runtime(captured, profileId);
            GuideClientModelProfile profile = captured.profiles().stream()
                    .filter(value -> value.id().equals(profileId)).findFirst().orElseThrow();
            boolean containsImages = hasImages(List.of(userInput))
                    || hasImages(sessions.history(new AgentSessionKey(actor, sessionId)));
            if (containsImages
                    && profile.imageInputCapability()
                            != dev.openallay.model.image.ImageInputCapability.SUPPORTED) {
                throw new GuideModelProfileException(
                        profile.imageInputCapability() == dev.openallay.model.image.ImageInputCapability.UNKNOWN
                                ? "image_input_unknown" : "image_input_unsupported",
                        "The selected model has no confirmed image input support. Select an image-capable model or remove images from this conversation.");
            }
            return selected.ask(actor, sessionId, requestId, userInput, images, context, events);
        } catch (GuideModelProfileException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static boolean hasImages(List<ModelMessage> messages) {
        return messages.stream().flatMap(message -> message.content().stream())
                .anyMatch(dev.openallay.model.ModelContent.Image.class::isInstance);
    }

    @Override
    public boolean cancel(UUID actor, String sessionId) {
        return sessions.cancel(new AgentSessionKey(actor, sessionId));
    }

    @Override
    public boolean cancel(UUID actor, String sessionId, UUID expectedRequestId) {
        return sessions.cancel(new AgentSessionKey(actor, sessionId), expectedRequestId);
    }

    @Override
    public void clearSession(UUID actor, String sessionId) {
        sessions.clear(new AgentSessionKey(actor, sessionId));
        state.get().runtimes().values().forEach(runtime -> runtime.clearContextEstimate(actor, sessionId));
    }

    @Override
    public void clearActor(UUID actor) {
        sessions.clearActor(actor);
        state.get().runtimes().values().forEach(runtime -> runtime.clearContextEstimates(actor));
    }



    @Override
    public boolean hasContext(UUID actor, String sessionId) {
        return sessions.hasContext(new AgentSessionKey(actor, sessionId));
    }

    @Override
    public void hydrateContext(
            UUID actor,
            String sessionId,
            List<ModelMessage> messages,
            List<ContextCheckpoint> checkpoints) {
        sessions.hydrate(new AgentSessionKey(actor, sessionId), messages, checkpoints);
    }

    private State build(
            ModelProfilesConfigLoader.Load load,
            Function<ResolvedModelProfile, ModelClient> factory,
            ClientCapabilitySnapshot capturedCapabilities) {
        Objects.requireNonNull(load, "load");
        List<GuideClientModelProfile> summaries = new ArrayList<>();
        Map<String, ClientGuideRuntime> runtimes = new LinkedHashMap<>();
        for (ResolvedModelProfile profile : load.profiles()) {
            GuideFailure failure = profile.failure();
            summaries.add(new GuideClientModelProfile(
                    profile.definition().id(),
                    profile.definition().displayName(),
                    profile.definition().enabled(),
                    profile.available(),
                    profile.canonicalModelId(),
                    failure,
                    profile.imageCapability().capability(),
                    profile.imageCapability().origin().name().toLowerCase(java.util.Locale.ROOT)
                            + (profile.imageCapability().source() == null
                                    ? "" : ":" + profile.imageCapability().source())));
            if (profile.available()) {
                ModelClient model = Objects.requireNonNull(
                        factory.apply(profile), "model factory result");
                runtimes.put(profile.definition().id(), new ClientGuideRuntime(
                        model,
                        sessions,
                        gson,
                        dispatcher,
                        extension,
                        new LiveTraceStore(
                                traceDirectory,
                                tracePersistenceEnabled),
                        profile.runtimeConfig().contextBudget(),
                        profile.canonicalModelId(),
                        capturedCapabilities,
                        dev.openallay.model.tokenizer.ModelContextTokenEstimator.create(
                                profile.runtimeConfig().protocol(), profile.canonicalModelId(),
                                profile.runtimeConfig().tokenEncoding())));
            }
        }
        return new State(load.config(), summaries, runtimes, capturedCapabilities);
    }

    private static ClientCapabilitySnapshot resolveDefaultCapabilities(OpenAllayRuntime runtime) {
        ToolResult<ClientCapabilitySnapshot> resolved = new ClientCapabilityResolver().resolve(
                CapabilityPolicy.defaults(), runtime.tools().registrations(), runtime.skills());
        if (resolved instanceof ToolResult.Success<ClientCapabilitySnapshot> success) {
            return success.value();
        }
        ToolResult.Failure<ClientCapabilitySnapshot> failure =
                (ToolResult.Failure<ClientCapabilitySnapshot>) resolved;
        throw new IllegalStateException(failure.code() + ": " + failure.message());
    }

    private static ClientGuideRuntime runtime(State state, String profileId) {
        GuideClientModelProfile profile = state.profiles().stream()
                .filter(value -> value.id().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new GuideModelProfileException(
                        "model_not_configured", "The selected client model profile does not exist"));
        if (!profile.available()) {
            throw new GuideModelProfileException(profile.failure().code(), profile.failure().message());
        }
        ClientGuideRuntime runtime = state.runtimes().get(profileId);
        if (runtime == null) {
            throw new GuideModelProfileException(
                    "invalid_model_config", "The selected client model runtime is unavailable");
        }
        return runtime;
    }

    private record State(
            ModelProfilesConfig config,
            List<GuideClientModelProfile> profiles,
            Map<String, ClientGuideRuntime> runtimes,
            ClientCapabilitySnapshot capabilities) {
        private State {
            Objects.requireNonNull(config, "config");
            profiles = List.copyOf(profiles);
            runtimes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(runtimes));
            Objects.requireNonNull(capabilities, "capabilities");
        }

        private String defaultProfileId() {
            return config.defaultProfileId();
        }

        private State withCapabilities(ClientCapabilitySnapshot replacement) {
            Map<String, ClientGuideRuntime> updated = new LinkedHashMap<>();
            runtimes.forEach((id, runtime) -> updated.put(id, runtime.withCapabilities(replacement)));
            return new State(config, profiles, updated, replacement);
        }
    }

    /** Fully built replacement bound to the exact model and capability state it captured. */
    public static final class PreparedReplacement {
        private final ClientModelRuntimeRegistry owner;
        private final State expected;
        private final State replacement;
        private final AtomicBoolean published = new AtomicBoolean();

        private PreparedReplacement(
                ClientModelRuntimeRegistry owner,
                State expected,
                State replacement) {
            this.owner = owner;
            this.expected = expected;
            this.replacement = replacement;
        }

        /** Background reconciliation must match both the captured settings and registry revision. */
        public boolean publish() {
            synchronized (owner) {
                claimPublication();
                return expected.config().equals(replacement.config())
                        && owner.state.compareAndSet(expected, replacement);
            }
        }

        /**
         * Commits already-persisted settings, preserving the latest capability snapshot.
         * Preparation and file I/O occur before this short publication critical section.
         */
        public void publishCommitted() {
            synchronized (owner) {
                claimPublication();
                State current;
                State rebased;
                do {
                    current = owner.state.get();
                    rebased = current.capabilities() == replacement.capabilities()
                            ? replacement : replacement.withCapabilities(current.capabilities());
                } while (!owner.state.compareAndSet(current, rebased));
            }
        }

        private void claimPublication() {
            if (!published.compareAndSet(false, true)) {
                throw new IllegalStateException("Prepared model replacement was already published");
            }
        }
    }
}
