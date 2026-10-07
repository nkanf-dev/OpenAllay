package dev.openallay.client;

import com.google.gson.Gson;
import dev.openallay.FeatureServices;
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
import dev.openallay.guide.GuidePreparedCompaction;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.image.ImagePayloadResolver;
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
            FeatureServices productRuntime,
            ModelProfilesConfigLoader.Load initial,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            Function<ResolvedModelProfile, ModelClient> modelFactory) {
        this(productRuntime, initial, gson, dispatcher, extension, modelFactory,
                null, () -> false);
    }

    ClientModelRuntimeRegistry(
            FeatureServices productRuntime,
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
            FeatureServices runtime,
            Path profilesPath,
            Map<String, String> environment,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension) {
        ToolResult<ModelProfilesConfigLoader.Load> loaded = new ModelProfilesConfigLoader()
                .load(profilesPath, environment);
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfilesConfigLoader.Load> value; ToolResult.Failure<ModelProfilesConfigLoader.Load> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<ModelProfilesConfigLoader.Load>) $oaPattern0_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
        }
        Gson gson = dev.openallay.json.EngineJson.create();
        ModelProfilesConfigLoader.Load value =
                ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value();
        return new ToolResult.Success<>(create(
                runtime, value, gson, dispatcher, extension));
    }

    public static ClientModelRuntimeRegistry create(
            FeatureServices runtime,
            ModelProfilesConfigLoader.Load initial,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension) {
        return create(runtime, initial, gson, dispatcher, extension, null, () -> false);
    }

    public static ClientModelRuntimeRegistry create(
            FeatureServices runtime,
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
    public boolean compactAvailable(String profileId) {
        State captured = state.get();
        try {
            ClientGuideRuntime selected = runtime(captured, profileId);
            return selected.compactAvailable(selected.defaultProfileId());
        } catch (GuideModelProfileException unavailable) {
            return false;
        }
    }

    @Override
    public Object compactIdentity(String profileId) {
        State captured = state.get();
        try {
            ClientGuideRuntime selected = runtime(captured, profileId);
            return selected.compactAvailable(selected.defaultProfileId()) ? captured : null;
        } catch (GuideModelProfileException unavailable) {
            return null;
        }
    }

    @Override
    public CompletableFuture<ToolResult<GuidePreparedCompaction>> prepareCompaction(
            String profileId,
            UUID actor,
            String sessionId,
            UUID controlId,
            List<ModelMessage> durableSeed,
            CancellationSignal cancellation,
            ImagePayloadResolver images,
            Consumer<AgentEvent> usage) {
        State captured = state.get();
        ClientGuideRuntime selected;
        try {
            selected = runtime(captured, profileId);
        } catch (GuideModelProfileException unavailable) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    unavailable.code(), unavailable.getMessage()));
        }
        GuideClientModelProfile profile = captured.profiles().stream()
                .filter(value -> value.id().equals(profileId)).findFirst().orElseThrow();
        return selected.prepareCompaction(selected.defaultProfileId(), actor, sessionId, controlId,
                        durableSeed, cancellation, images, usage, profile.imageInputCapability())
                .thenApply(result -> {
                    if (result instanceof ToolResult.Failure<GuidePreparedCompaction>) return result;
                    GuidePreparedCompaction prepared =
                            ((ToolResult.Success<GuidePreparedCompaction>) result).value();
                    synchronized (this) {
                        if (state.get() != captured || !prepared.current()) {
                            prepared.close();
                            return new ToolResult.Failure<GuidePreparedCompaction>(
                                    "compact_stale", "The selected model or session changed during manual compaction");
                        }
                    }
                    return new ToolResult.Success<GuidePreparedCompaction>(new GuidePreparedCompaction() {
                        @Override public dev.openallay.guide.GuideCompactResult outcome() {
                            return prepared.outcome();
                        }
                        @Override public List<ModelMessage> source() { return prepared.source(); }
                        @Override public List<ModelMessage> projection() { return prepared.projection(); }
                        @Override public boolean current() {
                            synchronized (ClientModelRuntimeRegistry.this) {
                                return state.get() == captured && prepared.current();
                            }
                        }
                        @Override public boolean publish() {
                            synchronized (ClientModelRuntimeRegistry.this) {
                                return state.get() == captured && prepared.publish();
                            }
                        }
                        @Override public void close() { prepared.close(); }
                    });
                });
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
        return dev.openallay.model.image.ModelImages.hasImages(messages);
    }

    @Override
    public boolean cancel(UUID actor, String sessionId) {
        return sessions.cancel(new AgentSessionKey(actor, sessionId));
    }

    @Override
    public ToolResult<Boolean> steer(
            UUID actor, String sessionId, UUID requestId, UUID messageId, ModelMessage message) {
        return sessions.steer(new AgentSessionKey(actor, sessionId), requestId, messageId, message);
    }

    @Override
    public boolean cancelSteer(UUID actor, String sessionId, UUID requestId, UUID messageId) {
        return sessions.cancelSteer(new AgentSessionKey(actor, sessionId), requestId, messageId);
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

    private static ClientCapabilitySnapshot resolveDefaultCapabilities(FeatureServices runtime) {
        ToolResult<ClientCapabilitySnapshot> resolved = new ClientCapabilityResolver().resolve(
                CapabilityPolicy.defaults(), runtime.tools().registrations(), runtime.skills());
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.ClientCapabilitySnapshot> value; ToolResult.Success<ClientCapabilitySnapshot> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = resolved) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern1_holder.bound = (ToolResult.Success<ClientCapabilitySnapshot>) $oaPattern1_holder.value) != null))) {
            return $oaPattern1_holder.bound.value();
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

    @dev.openallay.value.ValueType(State.ValueSchemaProvider.class)
private static final class State {
    private final ModelProfilesConfig config;
    private final List<GuideClientModelProfile> profiles;
    private final Map<String, ClientGuideRuntime> runtimes;
    private final ClientCapabilitySnapshot capabilities;
    private State(ModelProfilesConfig config, List<GuideClientModelProfile> profiles, Map<String, ClientGuideRuntime> runtimes, ClientCapabilitySnapshot capabilities) {

            Objects.requireNonNull(config, "config");
            profiles = List.copyOf(profiles);
            runtimes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(runtimes));
            Objects.requireNonNull(capabilities, "capabilities");

        this.config = config;
        this.profiles = profiles;
        this.runtimes = runtimes;
        this.capabilities = capabilities;
    }
    public ModelProfilesConfig config() { return config; }
    public List<GuideClientModelProfile> profiles() { return profiles; }
    public Map<String, ClientGuideRuntime> runtimes() { return runtimes; }
    public ClientCapabilitySnapshot capabilities() { return capabilities; }
private String defaultProfileId() {
            return config.defaultProfileId();
        }
private State withCapabilities(ClientCapabilitySnapshot replacement) {
            Map<String, ClientGuideRuntime> updated = new LinkedHashMap<>();
            runtimes.forEach((id, runtime) -> updated.put(id, runtime.withCapabilities(replacement)));
            return new State(config, profiles, updated, replacement);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof State)) return false;
        State that = (State) other;
        return java.util.Objects.equals(config, that.config) && java.util.Objects.equals(profiles, that.profiles) && java.util.Objects.equals(runtimes, that.runtimes) && java.util.Objects.equals(capabilities, that.capabilities);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + java.util.Objects.hashCode(profiles);
        hash = 31 * hash + java.util.Objects.hashCode(runtimes);
        hash = 31 * hash + java.util.Objects.hashCode(capabilities);
        return hash;
    }
    @Override public String toString() { return "State[config=" + config + ", profiles=" + profiles + ", runtimes=" + runtimes + ", capabilities=" + capabilities + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<State> schema() {
            return new dev.openallay.value.ValueSchema<>(State.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<State>>asList(new dev.openallay.value.ValueSchema.Component<>(State.class, "config", State::config), new dev.openallay.value.ValueSchema.Component<>(State.class, "profiles", State::profiles), new dev.openallay.value.ValueSchema.Component<>(State.class, "runtimes", State::runtimes), new dev.openallay.value.ValueSchema.Component<>(State.class, "capabilities", State::capabilities)), arguments -> new State((ModelProfilesConfig) arguments[0], (List) arguments[1], (Map) arguments[2], (ClientCapabilitySnapshot) arguments[3]));
        }
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
