package dev.openallay.requirement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Strict shared codec for advisory JSON and scalar Skill extra metadata. */
public final class RequirementCodec {
    public static final String CAPABILITIES_KEY = "openallay/requires-capabilities";
    public static final String EXTENSIONS_KEY = "openallay/requires-extensions";
    public static final String SKILLS_KEY = "openallay/requires-skills";
    private static final Set<String> FIELDS = Set.of("capabilities", "extensions", "skills");

    private RequirementCodec() {}

    /** A missing property is empty; explicit null and unknown object members are invalid. */
    public static RequirementSet decode(JsonElement value) {
        if (value == null) {
            return RequirementSet.EMPTY;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("requirements must be an object");
        }
        JsonObject object = value.getAsJsonObject();
        if (!FIELDS.containsAll(dev.openallay.json.JsonTrees.keys(object))) {
            throw new IllegalArgumentException("Unknown requirements fields");
        }
        return new RequirementSet(array(object, "capabilities", RequirementKind.CAPABILITY),
                array(object, "extensions", RequirementKind.EXTENSION),
                array(object, "skills", RequirementKind.SKILL));
    }

    /** Callers omit the requirements property altogether when the set is empty. */
    public static JsonObject encode(RequirementSet requirements) {
        JsonObject result = new JsonObject();
        add(result, "capabilities", requirements.capabilities());
        add(result, "extensions", requirements.extensions());
        add(result, "skills", requirements.skills());
        return result;
    }

    public static RequirementSet fromMetadata(Map<String, String> metadata) {
        return new RequirementSet(scalar(metadata.get(CAPABILITIES_KEY), RequirementKind.CAPABILITY),
                scalar(metadata.get(EXTENSIONS_KEY), RequirementKind.EXTENSION),
                scalar(metadata.get(SKILLS_KEY), RequirementKind.SKILL));
    }

    private static Set<String> scalar(String value, RequirementKind kind) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        TreeSet<String> result = new TreeSet<>();
        for (String id : value.strip().split("\\s+")) {
            addUnique(result, id, kind);
        }
        return result;
    }

    private static Set<String> array(JsonObject object, String field, RequirementKind kind) {
        JsonElement value = object.get(field);
        if (value == null) {
            return Set.of();
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("requirements." + field + " must be an array");
        }
        TreeSet<String> result = new TreeSet<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("requirements." + field + " must contain strings");
            }
            addUnique(result, item.getAsString(), kind);
        }
        return result;
    }

    private static void addUnique(Set<String> ids, String id, RequirementKind kind) {
        if (!ids.add(RequirementSet.requireId(id, kind))) {
            throw new IllegalArgumentException("Duplicate advisory " + kind + " ID: " + id);
        }
    }

    private static void add(JsonObject object, String field, Set<String> values) {
        if (!values.isEmpty()) {
            JsonArray encoded = new JsonArray();
            values.stream().sorted().forEach(encoded::add);
            object.add(field, encoded);
        }
    }
}
