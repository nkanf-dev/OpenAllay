package dev.openallay.model.metadata;

import dev.openallay.concurrent.NamedThreads;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.guide.GuideFailure;
import dev.openallay.model.image.ImageInputCapability;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ordered asynchronous cache for validated, credential-free provider metadata. */
public final class ModelMetadataCache {
    private static final Set<String> ROOT_FIELDS = dev.openallay.util.Java8Collections.setOf("entries");
    private static final Set<String> ENTRY_FIELDS = dev.openallay.util.Java8Collections.setOf("source", "providerModelId", "canonicalModelId", "contextWindowTokens", "maxOutputTokens", "capturedAt", "imageInputCapability");

    @dev.openallay.value.ValueType(Snapshot.ValueSchemaProvider.class)
public static final class Snapshot {
    private final Map<ModelMetadata.Key, ModelMetadata> entries;
    private final GuideFailure failure;
    public Snapshot(Map<ModelMetadata.Key, ModelMetadata> entries, GuideFailure failure) {

            entries = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries));

        this.entries = entries;
        this.failure = failure;
    }
    public Map<ModelMetadata.Key, ModelMetadata> entries() { return entries; }
    public GuideFailure failure() { return failure; }
public ModelMetadata find(String source, String providerModelId) {
            return entries.get(new ModelMetadata.Key(source, providerModelId));
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Snapshot)) return false;
        Snapshot that = (Snapshot) other;
        return java.util.Objects.equals(entries, that.entries) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entries);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "Snapshot[entries=" + entries + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Snapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(Snapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Snapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "entries", Snapshot::entries), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "failure", Snapshot::failure)), arguments -> new Snapshot((Map) arguments[0], (GuideFailure) arguments[1]));
        }
    }
}

    private final Path path;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            NamedThreads.daemonFactory("openallay-model-metadata-cache", 0));
    private Snapshot state;
    private boolean closed;

    public ModelMetadataCache(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public synchronized CompletableFuture<Snapshot> load() {
        return submit(this::loadOnWorker);
    }

    public synchronized CompletableFuture<Snapshot> put(ModelMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        return submit(() -> putOnWorker(metadata));
    }

    public synchronized CompletableFuture<Void> closeAsync() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        closed = true;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        worker.execute(() -> {
            completion.complete(null);
            worker.shutdown();
        });
        return completion;
    }

    private synchronized <T> CompletableFuture<T> submit(java.util.function.Supplier<T> task) {
        if (closed) {
            return dev.openallay.util.Java8Futures.failedFuture(new IllegalStateException(
                    "model metadata cache is closed"));
        }
        return CompletableFuture.supplyAsync(task, worker);
    }

    private Snapshot loadOnWorker() {
        if (state != null) {
            return state;
        }
        if (!Files.exists(path)) {
            state = new Snapshot(dev.openallay.util.Java8Collections.mapOf(), null);
            return state;
        }
        try {
            state = new Snapshot(decode(Files.readString(path)), null);
        } catch (IOException | RuntimeException failure) {
            state = new Snapshot(
                    dev.openallay.util.Java8Collections.mapOf(),
                    new GuideFailure(
                            "metadata_cache_invalid",
                            "The local model metadata cache is invalid"));
        }
        return state;
    }

    private Snapshot putOnWorker(ModelMetadata metadata) {
        Snapshot loaded = loadOnWorker();
        if (loaded.failure() != null) {
            return loaded;
        }
        LinkedHashMap<ModelMetadata.Key, ModelMetadata> updated =
                new LinkedHashMap<>(loaded.entries());
        updated.put(metadata.key(), metadata);
        try {
            writeAtomically(encode(dev.openallay.util.Java8Collections.toList(updated.values().stream())));
            state = new Snapshot(updated, null);
            return state;
        } catch (IOException failure) {
            return new Snapshot(
                    loaded.entries(),
                    new GuideFailure(
                            "metadata_cache_write_failed",
                            "Unable to update the local model metadata cache"));
        }
    }

    private void writeAtomically(String json) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(
                parent == null ? java.nio.file.Paths.get(".") : parent,
                path.getFileName().toString(),
                ".tmp");
        try {
            Files.writeString(temporary, json);
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String encode(List<ModelMetadata> entries) {
        JsonObject root = new JsonObject();
        JsonArray encodedEntries = new JsonArray();
        for (ModelMetadata metadata : entries) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("source", metadata.source());
            encoded.addProperty("providerModelId", metadata.providerModelId());
            encoded.addProperty("canonicalModelId", metadata.canonicalModelId());
            encoded.addProperty("contextWindowTokens", metadata.contextWindowTokens());
            if (metadata.maxOutputTokens() == null) {
                encoded.add("maxOutputTokens", com.google.gson.JsonNull.INSTANCE);
            } else {
                encoded.addProperty("maxOutputTokens", metadata.maxOutputTokens());
            }
            encoded.addProperty("capturedAt", metadata.capturedAt().toString());
            encoded.addProperty("imageInputCapability", metadata.imageInputCapability().encoded());
            encodedEntries.add(encoded);
        }
        root.add("entries", encodedEntries);
        return root.toString();
    }

    private static Map<ModelMetadata.Key, ModelMetadata> decode(String json) throws IOException {
        JsonElement parsed = BuiltinModelCatalog.readStrict(new java.io.StringReader(json));
        JsonObject root = object(parsed, "metadata cache");
        requireFields(root, ROOT_FIELDS, "metadata cache");
        JsonElement entries = root.get("entries");
        if (entries == null || !entries.isJsonArray()) {
            throw new IllegalArgumentException("metadata cache entries must be an array");
        }
        LinkedHashMap<ModelMetadata.Key, ModelMetadata> decoded = new LinkedHashMap<>();
        for (JsonElement entry : entries.getAsJsonArray()) {
            JsonObject encoded = object(entry, "metadata cache entry");
            requireFields(encoded, ENTRY_FIELDS, "metadata cache entry");
            JsonElement output = encoded.get("maxOutputTokens");
            Integer maxOutput = output == null || output.isJsonNull() ? null : integer(output);
            ModelMetadata metadata = new ModelMetadata(
                    string(encoded, "source"),
                    string(encoded, "providerModelId"),
                    string(encoded, "canonicalModelId"),
                    integer(encoded.get("contextWindowTokens")),
                    maxOutput,
                    Instant.parse(string(encoded, "capturedAt")),
                    ImageInputCapability.parse(string(encoded, "imageInputCapability")));
            if (decoded.put(metadata.key(), metadata) != null) {
                throw new IllegalArgumentException("duplicate metadata cache key");
            }
        }
        return decoded;
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || dev.openallay.util.Java8Strings.isBlank(value.getAsString())) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.getAsString();
    }

    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("value must be an integer");
        }
        try {
            return new java.math.BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("value must be an integer", failure);
        }
    }

    private static void requireFields(JsonObject object, Set<String> expected, String label) {
        if (!dev.openallay.json.JsonTrees.keys(object).equals(expected)) {
            throw new IllegalArgumentException(label + " schema mismatch");
        }
    }
}
