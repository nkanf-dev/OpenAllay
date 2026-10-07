package dev.openallay.model.config;

import dev.openallay.client.ClientModelRuntimeRegistry;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.settings.AtomicSettingsFile;
import dev.openallay.settings.SettingsWriteException;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates and prepares a complete model registry before atomically replacing its file. */
public final class ModelProfileSettingsStore {
    @FunctionalInterface
    interface FileReplacement {
        void replace(Path target, String contents);
    }

    @FunctionalInterface
    interface RuntimePreparer {
        RuntimePublication prepare(ModelProfilesConfigLoader.Load load);
    }

    @FunctionalInterface
    interface RuntimePublication {
        void publish();
    }

    @dev.openallay.value.ValueType(Saved.ValueSchemaProvider.class)
public static final class Saved {
    private final ModelProfilesConfig config;
    private final List<ResolvedModelProfile> profiles;
    public Saved(ModelProfilesConfig config, List<ResolvedModelProfile> profiles) {

            Objects.requireNonNull(config, "config");
            profiles = List.copyOf(profiles);
            if (profiles.size() != config.profiles().size()) {
                throw new IllegalArgumentException("every saved profile must have a resolution");
            }

        this.config = config;
        this.profiles = profiles;
    }
    public ModelProfilesConfig config() { return config; }
    public List<ResolvedModelProfile> profiles() { return profiles; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Saved)) return false;
        Saved that = (Saved) other;
        return java.util.Objects.equals(config, that.config) && java.util.Objects.equals(profiles, that.profiles);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + java.util.Objects.hashCode(profiles);
        return hash;
    }
    @Override public String toString() { return "Saved[config=" + config + ", profiles=" + profiles + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Saved> schema() {
            return new dev.openallay.value.ValueSchema<>(Saved.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Saved>>asList(new dev.openallay.value.ValueSchema.Component<>(Saved.class, "config", Saved::config), new dev.openallay.value.ValueSchema.Component<>(Saved.class, "profiles", Saved::profiles)), arguments -> new Saved((ModelProfilesConfig) arguments[0], (List) arguments[1]));
        }
    }
}

    private final Path path;
    private final FileReplacement files;
    private final ModelProfilesConfigWriter writer = new ModelProfilesConfigWriter();
    private final ModelProfilesConfigLoader loader = new ModelProfilesConfigLoader();

    public ModelProfileSettingsStore(Path path) {
        this(path, new AtomicSettingsFile()::replace);
    }

    ModelProfileSettingsStore(Path path, FileReplacement files) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.files = Objects.requireNonNull(files, "files");
    }

    public ToolResult<Saved> save(
            ModelProfilesConfig candidate,
            Map<String, String> environment,
            Map<ModelMetadata.Key, ModelMetadata> metadata,
            ClientModelRuntimeRegistry registry) {
        return save(
                candidate,
                CredentialResolver.environment(environment),
                metadata,
                registry);
    }

    public ToolResult<Saved> save(
            ModelProfilesConfig candidate,
            CredentialResolver credentials,
            Map<ModelMetadata.Key, ModelMetadata> metadata,
            ClientModelRuntimeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        return save(
                candidate,
                credentials,
                metadata,
                load -> registry.prepare(load)::publishCommitted);
    }

    ToolResult<Saved> save(
            ModelProfilesConfig candidate,
            Map<String, String> environment,
            Map<ModelMetadata.Key, ModelMetadata> metadata,
            RuntimePreparer preparer) {
        return save(
                candidate,
                CredentialResolver.environment(environment),
                metadata,
                preparer);
    }

    ToolResult<Saved> save(
            ModelProfilesConfig candidate,
            CredentialResolver credentials,
            Map<ModelMetadata.Key, ModelMetadata> metadata,
            RuntimePreparer preparer) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(preparer, "preparer");

        String encoded;
        ModelProfilesConfigLoader.Load resolved;
        RuntimePublication publication;
        try {
            encoded = writer.encode(candidate);
            ToolResult<ModelProfilesConfigLoader.Load> loaded = loader.load(
                    new StringReader(encoded), credentials, Map.copyOf(metadata));
            if (loaded instanceof ToolResult.Failure<ModelProfilesConfigLoader.Load> failure) {
                return new ToolResult.Failure<>(failure.code(), failure.message());
            }
            resolved = ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value();
            publication = Objects.requireNonNull(
                    preparer.prepare(resolved), "runtime publication");
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "invalid_model_config", "Unable to prepare model profile settings");
        }

        try {
            files.replace(path, encoded);
        } catch (SettingsWriteException failure) {
            return new ToolResult.Failure<>(failure.code(), failure.getMessage());
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "settings_write_failed", "Unable to save settings");
        }

        publication.publish();
        return new ToolResult.Success<>(new Saved(resolved.config(), resolved.profiles()));
    }
}
