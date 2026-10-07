package dev.openallay.extension.universal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.api.extension.ExtensionDescriptor;
import dev.openallay.api.extension.ExtensionRequirements;
import dev.openallay.api.extension.SupportDeclaration;
import dev.openallay.api.extension.SupportTarget;
import dev.openallay.extension.ExtensionCompatibility;
import dev.openallay.requirement.RequirementCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Exact external community-package shape. Released loader-mod schema 1 is not reinterpreted. */
public record UniversalExtensionManifest(String entrypoint, ExtensionDescriptor descriptor) {
    public static final String JAR_PATH = "META-INF/openallay-extension.json";
    private static final Set<String> FIELDS = Set.of("schemaVersion", "id", "name", "version", "provider",
            "summary", "source", "entrypoint", "support");
    private static final Set<String> SUPPORT_FIELDS = Set.of("targets", "minimumJavaVersion",
            "requiredHostFeatures", "validatedTargetIds");
    private static final Set<String> TARGET_FIELDS = Set.of("loader", "minecraftVersionRange",
            "openAllayVersionRange", "openAllayApiVersionRange");

    public UniversalExtensionManifest {
        if (entrypoint == null || !entrypoint.matches(
                "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new IllegalArgumentException("Invalid explicit Extension entrypoint");
        }
        Objects.requireNonNull(descriptor, "descriptor");
    }

    public static UniversalExtensionManifest decode(String json) {
        JsonObject root = object(UniversalExtensionJson.parse(json));
        Set<String> fields = new HashSet<>(dev.openallay.json.JsonTrees.keys(root));
        fields.remove("requirements");
        if (!fields.equals(FIELDS)) throw new IllegalArgumentException("Universal package fields do not match");
        if (integer(root, "schemaVersion") != 2) {
            throw new IllegalArgumentException("Not a universal Extension package");
        }
        JsonObject support = object(root.get("support"));
        exact(support, SUPPORT_FIELDS);
        JsonElement targetsJson = support.get("targets");
        if (targetsJson == null || !targetsJson.isJsonArray()) {
            throw new IllegalArgumentException("Support targets must be an array");
        }
        List<SupportTarget> targets = new ArrayList<>();
        for (JsonElement encoded : targetsJson.getAsJsonArray()) {
            JsonObject target = object(encoded);
            exact(target, TARGET_FIELDS);
            targets.add(new SupportTarget(string(target, "loader"),
                    range(target, "minecraftVersionRange"), range(target, "openAllayVersionRange"),
                    range(target, "openAllayApiVersionRange")));
        }
        dev.openallay.requirement.RequirementSet requirements = RequirementCodec.decode(root.get("requirements"));
        ExtensionDescriptor descriptor = new ExtensionDescriptor(string(root, "id"), string(root, "name"),
                string(root, "version"), string(root, "provider"), string(root, "summary"), string(root, "source"),
                new SupportDeclaration(targets, integer(support, "minimumJavaVersion"),
                        strings(support, "requiredHostFeatures"), strings(support, "validatedTargetIds")),
                new ExtensionRequirements(requirements.capabilities(), requirements.extensions(), requirements.skills()));
        return new UniversalExtensionManifest(string(root, "entrypoint"), descriptor);
    }

    private static String range(JsonObject value, String field) {
        return ExtensionCompatibility.requireRange(string(value, field), field);
    }
    private static void exact(JsonObject value, Set<String> fields) {
        if (!dev.openallay.json.JsonTrees.keys(value).equals(fields)) throw new IllegalArgumentException("Package fields do not match");
    }
    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected package object");
        return value.getAsJsonObject();
    }
    private static String string(JsonObject value, String field) {
        JsonElement encoded = value.get(field);
        if (encoded == null || !encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive().isString()
                || encoded.getAsString().isBlank()) throw new IllegalArgumentException("Expected package string");
        return encoded.getAsString();
    }
    private static int integer(JsonObject value, String field) {
        JsonElement encoded = value.get(field);
        if (encoded == null || !encoded.isJsonPrimitive() || !encoded.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected package integer");
        }
        try { return encoded.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("Expected package integer"); }
    }
    private static Set<String> strings(JsonObject value, String field) {
        JsonElement encoded = value.get(field);
        if (encoded == null || !encoded.isJsonArray()) throw new IllegalArgumentException("Expected package array");
        Set<String> values = new TreeSet<>();
        for (JsonElement item : encoded.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                    || !values.add(item.getAsString())) throw new IllegalArgumentException("Expected unique strings");
        }
        return Set.copyOf(values);
    }
}
