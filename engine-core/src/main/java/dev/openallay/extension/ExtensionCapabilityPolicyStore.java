package dev.openallay.extension;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.openallay.settings.AtomicSettingsFile;
import dev.openallay.settings.SettingsWriteException;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Strict latest-only grant document, persisted before publishing its immutable generation. */
public final class ExtensionCapabilityPolicyStore {
    @FunctionalInterface
    interface FileReplacement {
        void replace(Path target, String contents);
    }

    private final Path path;
    private final FileReplacement files;
    private volatile ExtensionCapabilityPolicy current = ExtensionCapabilityPolicy.defaults();

    public ExtensionCapabilityPolicyStore(Path path) {
        this(path, new AtomicSettingsFile()::replace);
    }

    ExtensionCapabilityPolicyStore(Path path, FileReplacement files) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.files = Objects.requireNonNull(files, "files");
    }

    public ExtensionCapabilityPolicy current() {
        return current;
    }

    /** Missing or failed configuration never retains authority from an earlier generation. */
    public synchronized ToolResult<ExtensionCapabilityPolicy> load() {
        current = ExtensionCapabilityPolicy.defaults();
        if (Files.notExists(path)) return new ToolResult.Success<>(current);
        try (Reader reader = Files.newBufferedReader(path)) {
            ToolResult<ExtensionCapabilityPolicy> result = decode(reader);
            if (result instanceof ToolResult.Success<ExtensionCapabilityPolicy> success) {
                current = success.value();
            }
            return result;
        } catch (IOException | RuntimeException failure) {
            return invalid();
        }
    }

    public synchronized ToolResult<ExtensionCapabilityPolicy> save(
            ExtensionCapabilityPolicy candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String contents = encode(candidate);
        ToolResult<ExtensionCapabilityPolicy> decoded = decode(new StringReader(contents));
        if (decoded instanceof ToolResult.Failure<ExtensionCapabilityPolicy> failure) return failure;
        ExtensionCapabilityPolicy validated =
                ((ToolResult.Success<ExtensionCapabilityPolicy>) decoded).value();
        try {
            files.replace(path, contents);
        } catch (SettingsWriteException failure) {
            return new ToolResult.Failure<>(failure.code(), failure.getMessage());
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>("settings_write_failed", "Unable to save settings");
        }
        current = validated;
        return new ToolResult.Success<>(validated);
    }

    /** Exact shape: {"grants":{"extension:id":["capability:id"]}}. */
    static ToolResult<ExtensionCapabilityPolicy> decode(Reader source) {
        Objects.requireNonNull(source, "source");
        try {
            JsonReader reader = dev.openallay.json.JsonReaders.strict(source);

            reader.beginObject();
            if (!reader.hasNext() || !reader.nextName().equals("grants")) return invalid();
            Map<String, Set<String>> grants = new TreeMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String extensionId = ExtensionCapabilityPolicy.requireExtensionId(reader.nextName());
                if (grants.containsKey(extensionId)) return invalid();
                TreeSet<String> capabilities = new TreeSet<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.STRING) return invalid();
                    String capability = ExtensionCapabilityPolicy.requireCapabilityId(reader.nextString());
                    if (!capabilities.add(capability)) return invalid();
                }
                reader.endArray();
                grants.put(extensionId, capabilities);
            }
            reader.endObject();
            if (reader.hasNext()) return invalid();
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) return invalid();
            return new ToolResult.Success<>(new ExtensionCapabilityPolicy(grants));
        } catch (IOException | RuntimeException failure) {
            return invalid();
        }
    }

    private static String encode(ExtensionCapabilityPolicy policy) {
        JsonObject grants = new JsonObject();
        policy.grants().forEach((extensionId, capabilities) -> {
            JsonArray scopes = new JsonArray();
            capabilities.forEach(scopes::add);
            grants.add(extensionId, scopes);
        });
        JsonObject root = new JsonObject();
        root.add("grants", grants);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    private static ToolResult.Failure<ExtensionCapabilityPolicy> invalid() {
        return new ToolResult.Failure<>(
                "invalid_extension_capability_config", "Invalid Extension capability settings");
    }
}
