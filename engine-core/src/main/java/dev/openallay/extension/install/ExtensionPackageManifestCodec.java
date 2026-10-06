package dev.openallay.extension.install;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.requirement.RequirementCodec;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Strict codec for {@value ExtensionPackageManifest#JAR_PATH}. */
public final class ExtensionPackageManifestCodec {
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion",
            "id",
            "name",
            "version",
            "provider",
            "summary",
            "loaders",
            "minecraftVersionRange",
            "openAllayApiVersionRange",
            "modIds",
            "source");

    public ExtensionPackageManifest decode(String json) {
        try {
            JsonElement parsed = dev.openallay.json.JsonTrees.parse(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Extension package manifest must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            Set<String> fields = new HashSet<>(dev.openallay.json.JsonTrees.keys(root));
            fields.remove("requirements");
            if (!fields.equals(FIELDS)) {
                throw new IllegalArgumentException(
                        "Extension package manifest fields do not match schema");
            }
            return new ExtensionPackageManifest(
                    integer(root, "schemaVersion"),
                    new OpenAllayExtensionDescriptor(
                            string(root, "id"),
                            string(root, "name"),
                            string(root, "version"),
                            string(root, "provider"),
                            string(root, "summary"),
                            strings(root, "loaders"),
                            string(root, "minecraftVersionRange"),
                            string(root, "openAllayApiVersionRange"),
                            string(root, "source"),
                            RequirementCodec.decode(root.get("requirements"))),
                    strings(root, "modIds"));
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException) {
                throw failure;
            }
            throw new IllegalArgumentException("Invalid Extension package manifest", failure);
        }
    }

    /** Writes schema 1 with the explicitly supported optional advisory expansion. */
    public String encode(ExtensionPackageManifest manifest) {
        JsonObject root = new JsonObject();
        OpenAllayExtensionDescriptor descriptor = manifest.descriptor();
        root.addProperty("schemaVersion", manifest.schemaVersion());
        root.addProperty("id", descriptor.id());
        root.addProperty("name", descriptor.name());
        root.addProperty("version", descriptor.version());
        root.addProperty("provider", descriptor.provider());
        root.addProperty("summary", descriptor.summary());
        root.add("loaders", strings(descriptor.loaders()));
        root.addProperty("minecraftVersionRange", descriptor.minecraftVersionRange());
        root.addProperty("openAllayApiVersionRange", descriptor.openAllayApiVersionRange());
        root.add("modIds", strings(manifest.modIds()));
        root.addProperty("source", descriptor.source());
        if (!descriptor.requirements().isEmpty()) {
            root.add("requirements", RequirementCodec.encode(descriptor.requirements()));
        }
        return dev.openallay.json.EngineJson.create(builder -> builder.setPrettyPrinting()).toJson(root) + "\n";
    }

    private static JsonArray strings(Set<String> values) {
        JsonArray encoded = new JsonArray();
        values.stream().sorted().forEach(encoded::add);
        return encoded;
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.getAsString();
    }

    private static Set<String> strings(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        TreeSet<String> values = new TreeSet<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonPrimitive()
                    || !item.getAsJsonPrimitive().isString()
                    || item.getAsString().isBlank()
                    || !values.add(item.getAsString())) {
                throw new IllegalArgumentException(
                        field + " must contain unique strings");
            }
        }
        return Set.copyOf(values);
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        int parsed = value.getAsInt();
        if (value.getAsDouble() != parsed) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return parsed;
    }
}
