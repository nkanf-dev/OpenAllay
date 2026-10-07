package dev.openallay.settings.model;

import dev.openallay.client.ClientModelRuntimeRegistry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.catalog.ModelCatalog;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.model.catalog.ProviderModelCatalogClient;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfileSettingsStore;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ModelProfilesConfigWriter;
import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.CredentialResolver;
import dev.openallay.model.config.LocalCredentialStore;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.Function;

/** Secure model-domain adapter used by the common settings coordinator. */
public final class ModelSettingsBackend implements ClientSettingsService.ModelActions {
    private final Path profilesPath;
    private final Supplier<Map<String, String>> environment;
    private final ClientModelRuntimeRegistry registry;
    private final LocalCredentialStore credentialStore;
    private final CredentialResolver credentials;
    private final ModelConnectionProbe probe;
    private final Function<java.time.Duration, ProviderModelCatalogClient> catalogClients;
    private final ModelProfileSettingsStore store;
    private final ModelProfilesConfigLoader loader = new ModelProfilesConfigLoader();
    private final ModelProfilesConfigWriter writer = new ModelProfilesConfigWriter();

    public ModelSettingsBackend(
            Path profilesPath,
            Supplier<Map<String, String>> environment,
            ClientModelRuntimeRegistry registry,
            ModelConnectionProbe probe) {
        this(
                profilesPath,
                environment,
                registry,
                probe,
                new LocalCredentialStore(
                        profilesPath.toAbsolutePath().normalize().resolveSibling(
                                "credentials.sqlite3"),
                        java.time.Clock.systemUTC()),
                ProviderModelCatalogClient::new);
    }

    public ModelSettingsBackend(
            Path profilesPath,
            Supplier<Map<String, String>> environment,
            ClientModelRuntimeRegistry registry,
            ModelConnectionProbe probe,
            LocalCredentialStore credentialStore) {
        this(
                profilesPath,
                environment,
                registry,
                probe,
                credentialStore,
                ProviderModelCatalogClient::new);
    }

    public ModelSettingsBackend(
            Path profilesPath,
            Supplier<Map<String, String>> environment,
            ClientModelRuntimeRegistry registry,
            ModelConnectionProbe probe,
            LocalCredentialStore credentialStore,
            Function<java.time.Duration, ProviderModelCatalogClient> catalogClients) {
        this.profilesPath = Objects.requireNonNull(profilesPath, "profilesPath")
                .toAbsolutePath().normalize();
        this.environment = Objects.requireNonNull(environment, "environment");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.catalogClients = Objects.requireNonNull(catalogClients, "catalogClients");
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.credentials = CredentialResolver.composite(
                credentialStore, environmentSnapshot());
        this.store = new ModelProfileSettingsStore(this.profilesPath);
    }

    public ToolResult<ClientSettingsService.ModelState> loadInitial(
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        ToolResult<ModelProfilesConfigLoader.Load> loaded = loader.load(
                profilesPath,
                credentials,
                Map.copyOf(metadata));
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfilesConfigLoader.Load> value; ToolResult.Success<ModelProfilesConfigLoader.Load> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<ModelProfilesConfigLoader.Load>) $oaPattern0_holder.value) != null))) {
            collectUnreferenced($oaPattern0_holder.bound.value().config());
        }
        return mapLoad(loaded);
    }

    public Set<String> presentEnvironmentNames() {
        TreeSet<String> names = new TreeSet<>();
        environmentSnapshot().forEach((name, value) -> {
            if (value != null && !value.isBlank()) {
                names.add(name);
            }
        });
        return java.util.Collections.unmodifiableSet(names);
    }

    @Override
    public ToolResult<ClientSettingsService.ModelState> save(
            ModelProfilesConfig candidate,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        return save(candidate, null, null, metadata);
    }

    @Override
    public ToolResult<ClientSettingsService.ModelState> save(
            ModelProfilesConfig candidate,
            String replacementProfileId,
            SecretValue replacement,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        CredentialReference inserted = null;
        ModelProfilesConfig prepared = candidate;
        if (replacement != null) {
            ToolResult<CredentialReference> created = credentialStore.insert(replacement);
            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.CredentialReference> value; ToolResult.Failure<CredentialReference> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = created) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<CredentialReference>) $oaPattern1_holder.value) != null))) {
                return new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message());
            }
            inserted = ((ToolResult.Success<CredentialReference>) created).value();
            try {
                prepared = replaceCredential(candidate, replacementProfileId, inserted);
            } catch (RuntimeException failure) {
                credentialStore.deleteIfUnreferenced(inserted, Set.of());
                return new ToolResult.Failure<>(
                        "invalid_model_config", "Unable to prepare model profile settings");
            }
        }
        ToolResult<ModelProfileSettingsStore.Saved> saved = store.save(
                prepared, credentials, Map.copyOf(metadata), registry);
        final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfileSettingsStore.Saved> value; ToolResult.Failure<ModelProfileSettingsStore.Saved> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = saved) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern2_holder.bound = (ToolResult.Failure<ModelProfileSettingsStore.Saved>) $oaPattern2_holder.value) != null))) {
            if (inserted != null) {
                credentialStore.deleteIfUnreferenced(inserted, Set.of());
            }
            return new ToolResult.Failure<>($oaPattern2_holder.bound.code(), $oaPattern2_holder.bound.message());
        }
        ModelProfileSettingsStore.Saved value =
                ((ToolResult.Success<ModelProfileSettingsStore.Saved>) saved).value();
        collectUnreferenced(value.config());
        return new ToolResult.Success<>(new ClientSettingsService.ModelState(
                value.config(),
                value.profiles().stream()
                        .map(profile -> ModelProfileSettingsView.Resolution.from(
                                profile, credentialPresent(profile.definition())))
                        .toList()));
    }

    @Override
    public ToolResult<ClientSettingsService.ModelState> reload(
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        ToolResult<ModelProfilesConfigLoader.Load> loaded = loader.load(
                profilesPath,
                credentials,
                Map.copyOf(metadata));
        final class $oaPattern3_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfilesConfigLoader.Load> value; ToolResult.Failure<ModelProfilesConfigLoader.Load> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern3_holder.bound = (ToolResult.Failure<ModelProfilesConfigLoader.Load>) $oaPattern3_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern3_holder.bound.code(), $oaPattern3_holder.bound.message());
        }
        ModelProfilesConfigLoader.Load value =
                ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value();
        ClientModelRuntimeRegistry.PreparedReplacement prepared = registry.prepare(value);
        prepared.publishCommitted();
        return new ToolResult.Success<>(state(value));
    }

    @Override
    public ToolResult<ResolvedModelProfile> resolve(
            ModelProfileDefinition candidate,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        return resolve(candidate, null, metadata);
    }

    @Override
    public ToolResult<ResolvedModelProfile> resolve(
            ModelProfileDefinition candidate,
            SecretValue replacement,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        ModelProfilesConfig isolated = new ModelProfilesConfig(
                candidate.id(),
                java.util.List.of(candidate));
        ToolResult<ModelProfilesConfigLoader.Load> loaded = replacement == null
                ? decode(isolated, metadata)
                : decode(isolated, reference -> {
                    CredentialReference expected = CredentialReference.parse(
                            candidate.credentialRef());
                    return expected.equals(reference)
                            ? new ToolResult.Success<>(replacement)
                            : credentials.resolve(reference);
                }, metadata);
        final class $oaPattern4_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfilesConfigLoader.Load> value; ToolResult.Failure<ModelProfilesConfigLoader.Load> bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern4_holder.bound = (ToolResult.Failure<ModelProfilesConfigLoader.Load>) $oaPattern4_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern4_holder.bound.code(), $oaPattern4_holder.bound.message());
        }
        return new ToolResult.Success<>(
                ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded)
                        .value().profiles().get(0));
    }

    @Override
    public ToolResult<ClientSettingsService.PreparedModels> prepare(
            ModelProfilesConfig candidate,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        ToolResult<ModelProfilesConfigLoader.Load> loaded = decode(candidate, metadata);
        final class $oaPattern5_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfilesConfigLoader.Load> value; ToolResult.Failure<ModelProfilesConfigLoader.Load> bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern5_holder.bound = (ToolResult.Failure<ModelProfilesConfigLoader.Load>) $oaPattern5_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern5_holder.bound.code(), $oaPattern5_holder.bound.message());
        }
        ModelProfilesConfigLoader.Load value =
                ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value();
        ClientModelRuntimeRegistry.PreparedReplacement prepared = registry.prepare(value);
        return new ToolResult.Success<>(new ClientSettingsService.PreparedModels(
                state(value), prepared::publish));
    }

    @Override
    public CompletableFuture<ModelConnectionResult> probe(
            ResolvedModelProfile profile,
            CancellationSignal cancellation) {
        return probe.test(profile, cancellation);
    }

    @Override
    public CompletableFuture<ToolResult<ModelCatalog>> fetchCatalog(
            ModelCatalogRequest request,
            SecretValue replacement,
            CancellationSignal cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "model_catalog_cancelled", "The model catalog request was cancelled"));
        }
        SecretValue credential = replacement;
        if (credential == null) {
            ToolResult<SecretValue> resolved;
            try {
                resolved = credentials.resolve(CredentialReference.parse(request.credentialRef()));
            } catch (RuntimeException failure) {
                resolved = null;
            }
            final class $oaPattern6_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.SecretValue> value; ToolResult.Success<SecretValue> bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if (!((($oaPattern6_holder.value = resolved) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern6_holder.bound = (ToolResult.Success<SecretValue>) $oaPattern6_holder.value) != null)))) {
                return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                        "model_catalog_credential_missing",
                        "A model provider credential is required to fetch models"));
            }
            credential = $oaPattern6_holder.bound.value();
        }
        try {
            return Objects.requireNonNull(
                    catalogClients.apply(request.connectTimeout()),
                    "model catalog client")
                    .fetch(request, credential, cancellation);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "model_catalog_transport_failed",
                    "The model provider catalog could not be reached"));
        }
    }

    private ToolResult<ModelProfilesConfigLoader.Load> decode(
            ModelProfilesConfig config,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        return decode(config, credentials, metadata);
    }

    private ToolResult<ModelProfilesConfigLoader.Load> decode(
            ModelProfilesConfig config,
            CredentialResolver resolver,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        try {
            return loader.load(
                    new StringReader(writer.encode(config)),
                    resolver,
                    Map.copyOf(metadata));
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "invalid_model_config", "Unable to prepare model profile settings");
        }
    }

    private Map<String, String> environmentSnapshot() {
        return Map.copyOf(environment.get());
    }

    public void closeCredentials() {
        credentialStore.close();
    }

    public void collectUnreferencedCredentials(ModelProfilesConfig config) {
        collectUnreferenced(Objects.requireNonNull(config, "config"));
    }

    private static ModelProfilesConfig replaceCredential(
            ModelProfilesConfig candidate,
            String profileId,
            CredentialReference reference) {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("replacement profile id is required");
        }
        boolean found = false;
        java.util.List<ModelProfileDefinition> definitions = new java.util.ArrayList<>();
        for (ModelProfileDefinition definition : candidate.profiles()) {
            if (!definition.id().equals(profileId)) {
                definitions.add(definition);
                continue;
            }
            found = true;
            definitions.add(new ModelProfileDefinition(
                    definition.id(),
                    definition.displayName(),
                    definition.enabled(),
                    definition.protocol(),
                    definition.baseUri(),
                    definition.model(),
                    reference.encoded(),
                    definition.contextWindowTokens(),
                    definition.maxOutputTokens(),
                    definition.connectTimeout(),
                    definition.requestTimeout(),
                    definition.metadata(),
                    definition.reasoningEffort(), definition.tokenEncoding(),
                    definition.imageInputCapabilityOverride()));
        }
        if (!found) {
            throw new IllegalArgumentException("replacement profile is unavailable");
        }
        return new ModelProfilesConfig(
                candidate.defaultProfileId(),
                definitions);
    }

    private void collectUnreferenced(ModelProfilesConfig config) {
        java.util.Set<CredentialReference> retained = new java.util.HashSet<>();
        for (ModelProfileDefinition definition : config.profiles()) {
            CredentialReference reference = CredentialReference.parse(definition.credentialRef());
            if (reference.kind() == CredentialReference.Kind.LOCAL) {
                retained.add(reference);
            }
        }
        credentialStore.collectUnreferenced(retained);
    }

    private ToolResult<ClientSettingsService.ModelState> mapLoad(
            ToolResult<ModelProfilesConfigLoader.Load> loaded) {
        final class $oaPattern7_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfilesConfigLoader.Load> value; ToolResult.Failure<ModelProfilesConfigLoader.Load> bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern7_holder.bound = (ToolResult.Failure<ModelProfilesConfigLoader.Load>) $oaPattern7_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern7_holder.bound.code(), $oaPattern7_holder.bound.message());
        }
        return new ToolResult.Success<>(state(
                ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value()));
    }

    public ClientSettingsService.ModelState state(
            ModelProfilesConfigLoader.Load load) {
        return new ClientSettingsService.ModelState(
                load.config(),
                load.profiles().stream()
                        .map(profile -> ModelProfileSettingsView.Resolution.from(
                                profile, credentialPresent(profile.definition())))
                        .toList());
    }

    private boolean credentialPresent(ModelProfileDefinition definition) {
        CredentialReference reference;
        try {
            reference = CredentialReference.parse(definition.credentialRef());
        } catch (RuntimeException failure) {
            return false;
        }
        if (reference.kind() == CredentialReference.Kind.ENVIRONMENT) {
            String value = environmentSnapshot().get(reference.value());
            return value != null && !value.isBlank();
        }
        ToolResult<Boolean> present = credentialStore.contains(reference);
        final class $oaPattern8_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Success<Boolean> bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
return (($oaPattern8_holder.value = present) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern8_holder.bound = (ToolResult.Success<Boolean>) $oaPattern8_holder.value) != null)) && $oaPattern8_holder.bound.value();
    }
}
