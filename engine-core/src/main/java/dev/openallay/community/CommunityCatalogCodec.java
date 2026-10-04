package dev.openallay.community;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict schema-2 codec; package ordering is canonicalized by the manifest. */
public final class CommunityCatalogCodec {
    private static final Set<String> ROOT_FIELDS =
            Set.of("schemaVersion", "kind", "generatedAt", "packages");
    private static final Set<String> PACKAGE_FIELDS =
            Set.of(
                    "id",
                    "displayName",
                    "description",
                    "publisher",
                    "version",
                    "archive",
                    "sha256",
                    "compatibility",
                    "source");
    private static final Set<String> COMPATIBILITY_FIELDS =
            Set.of("minecraft", "openallayApi");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public CommunityCatalogManifest decode(String json) {
        try {
            JsonObject root = object(JsonParser.parseString(json), "catalog");
            exactFields(root, ROOT_FIELDS, "catalog");
            int schema = integer(root, "schemaVersion");
            if (schema != CommunityCatalogManifest.SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported community catalog schema");
            }
            String kind = string(root, "kind");
            Instant generated = Instant.parse(string(root, "generatedAt"));
            JsonElement encodedPackages = root.get("packages");
            if (encodedPackages == null || !encodedPackages.isJsonArray()) {
                throw new IllegalArgumentException("catalog packages must be an array");
            }
            List<CommunityCatalogManifest.PackageEntry> packages = new ArrayList<>();
            for (JsonElement encoded : encodedPackages.getAsJsonArray()) {
                JsonObject entry = object(encoded, "package");
                exactFields(entry, PACKAGE_FIELDS, "package");
                JsonObject compatibility = object(entry.get("compatibility"), "compatibility");
                exactFields(compatibility, COMPATIBILITY_FIELDS, "compatibility");
                packages.add(new CommunityCatalogManifest.PackageEntry(
                        string(entry, "id"),
                        string(entry, "displayName"),
                        string(entry, "description"),
                        string(entry, "publisher"),
                        string(entry, "version"),
                        URI.create(string(entry, "archive")),
                        string(entry, "sha256"),
                        new CommunityCatalogManifest.Compatibility(
                                string(compatibility, "minecraft"),
                                string(compatibility, "openallayApi")),
                        URI.create(string(entry, "source"))));
            }
            return new CommunityCatalogManifest(schema, kind, generated, packages);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException) {
                throw failure;
            }
            throw new IllegalArgumentException("Invalid community catalog", failure);
        }
    }

    public String encode(CommunityCatalogManifest manifest) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", manifest.schemaVersion());
        root.addProperty("kind", manifest.kind());
        root.addProperty("generatedAt", manifest.generatedAt().toString());
        var packages = new com.google.gson.JsonArray();
        for (CommunityCatalogManifest.PackageEntry entry : manifest.packages()) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("id", entry.id());
            encoded.addProperty("displayName", entry.displayName());
            encoded.addProperty("description", entry.description());
            encoded.addProperty("publisher", entry.publisher());
            encoded.addProperty("version", entry.version());
            encoded.addProperty("archive", entry.archive().toString());
            encoded.addProperty("sha256", entry.sha256());
            JsonObject compatibility = new JsonObject();
            compatibility.addProperty("minecraft", entry.compatibility().minecraft());
            compatibility.addProperty("openallayApi", entry.compatibility().openallayApi());
            encoded.add("compatibility", compatibility);
            encoded.addProperty("source", entry.source().toString());
            packages.add(encoded);
        }
        root.add("packages", packages);
        return gson.toJson(root) + "\n";
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
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        int parsed = value.getAsInt();
        if (value.getAsDouble() != parsed) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return parsed;
    }

    private static void exactFields(JsonObject object, Set<String> expected, String label) {
        Set<String> actual = new java.util.HashSet<>(object.keySet());
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(label + " fields do not match schema");
        }
    }
}
