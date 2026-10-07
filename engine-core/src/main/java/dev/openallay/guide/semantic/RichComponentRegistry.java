package dev.openallay.guide.semantic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.context.RecipeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Code-owned registry and exact decoder for controlled dynamic components. */
public final class RichComponentRegistry {
    @FunctionalInterface
    public interface Decoder {
        RichComponent decode(
                String nodeId,
                RichComponentEnvelope envelope,
                SemanticReferenceIndex references);
    }

    private static final Set<String> ENVELOPE_KEYS = dev.openallay.util.Java8Collections.setOf("type", "properties", "fallback", "narration");
    private final Map<String, Decoder> decoders;

    public RichComponentRegistry(Map<String, Decoder> decoders) {
        TreeMap<String, Decoder> copy = new TreeMap<>();
        Objects.requireNonNull(decoders, "decoders").forEach((type, decoder) -> {
            if (type == null || !type.matches("[a-z][a-z0-9_]*") || decoder == null) {
                throw new IllegalArgumentException("rich component registration is invalid");
            }
            if (copy.put(type, decoder) != null) {
                throw new IllegalArgumentException("rich component type is duplicated");
            }
        });
        this.decoders = dev.openallay.util.Java8Collections.mapCopyOf(copy);
    }

    public static RichComponentRegistry builtins() {
        return new RichComponentRegistry(BuiltinRichComponents.decoders());
    }

    Set<String> registeredTypes() {
        return decoders.keySet();
    }

    public Decode decode(String encoded, String nodeId, SemanticReferenceIndex references) {
        Objects.requireNonNull(references, "references");
        String fallback = encoded == null || dev.openallay.util.Java8Strings.isBlank(encoded)
                ? "Unsupported component" : dev.openallay.util.Java8Strings.strip(encoded);
        try {
            JsonElement parsed = dev.openallay.json.JsonTrees.parse(encoded);
            if (!parsed.isJsonObject()) {
                return Decode.failure(fallback, "semantic_component_unsupported");
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonElement fallbackElement = object.get("fallback");
            if (fallbackElement != null && fallbackElement.isJsonPrimitive()
                    && fallbackElement.getAsJsonPrimitive().isString()
                    && !dev.openallay.util.Java8Strings.isBlank(fallbackElement.getAsString())) {
                fallback = fallbackElement.getAsString();
            }
            exact(object, ENVELOPE_KEYS);
            RichComponentEnvelope envelope = new RichComponentEnvelope(
                    string(object, "type"),
                    object(object, "properties"),
                    string(object, "fallback"),
                    string(object, "narration"));
            Decoder decoder = decoders.get(envelope.type());
            if (decoder == null) {
                return Decode.failure(fallback, "semantic_component_unsupported");
            }
            return Decode.success(decoder.decode(nodeId, envelope, references));
        } catch (RuntimeException invalid) {
            return Decode.failure(fallback, "semantic_component_unsupported");
        }
    }

    static RichComponent.Item item(JsonObject object, SemanticReferenceIndex references) {
        exact(object, dev.openallay.util.Java8Collections.setOf("itemId", "count", "label"));
        String itemId = string(object, "itemId");
        String origin = requireOrigin(references, SemanticReferenceKind.ITEM, itemId);
        return new RichComponent.Item(
                itemId, nonnegativeLong(object, "count"), nullableString(object, "label"), origin);
    }

    static RecipeBinding recipe(JsonObject object, SemanticReferenceIndex references) {
        String handle = RecipeSemanticHandle.encode(new RecipeReference(
                string(object, "sourceId"),
                string(object, "generation"),
                string(object, "recipeId")));
        return new RecipeBinding(
                RecipeSemanticHandle.decode(handle),
                requireOrigin(references, SemanticReferenceKind.RECIPE, handle));
    }

    static void exact(JsonObject object, Set<String> keys) {
        if (!dev.openallay.json.JsonTrees.keys(object).equals(keys)) {
            throw new IllegalArgumentException("component object has unknown or missing keys");
        }
    }

    static JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    static JsonObject object(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || dev.openallay.util.Java8Strings.isBlank(value.getAsString())) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return value.getAsString();
    }

    static String nullableString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    static long positiveLong(JsonObject object, String field) {
        long value = nonnegativeLong(object, field);
        if (value == 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    static long nonnegativeLong(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        long result = value.getAsLong();
        if (result < 0) throw new IllegalArgumentException(field + " must not be negative");
        return result;
    }

    static boolean bool(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return value.getAsBoolean();
    }

    static List<JsonObject> objects(JsonArray values) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("array entry must be object");
            result.add(value.getAsJsonObject());
        }
        return dev.openallay.util.Java8Collections.listCopyOf(result);
    }

    static String requireOrigin(
            SemanticReferenceIndex references, SemanticReferenceKind kind, String target) {
        return references.origin(kind, target).orElseThrow(() ->
                new IllegalArgumentException("component reference is not authorized"));
    }

    @dev.openallay.value.ValueType(RecipeBinding.ValueSchemaProvider.class)
static final class RecipeBinding {
    private final RecipeReference reference;
    private final String originInvocationId;
    RecipeBinding(RecipeReference reference, String originInvocationId) {
        this.reference = reference;
        this.originInvocationId = originInvocationId;
    }
    public RecipeReference reference() { return reference; }
    public String originInvocationId() { return originInvocationId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeBinding)) return false;
        RecipeBinding that = (RecipeBinding) other;
        return java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(originInvocationId, that.originInvocationId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(originInvocationId);
        return hash;
    }
    @Override public String toString() { return "RecipeBinding[reference=" + reference + ", originInvocationId=" + originInvocationId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeBinding> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeBinding.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeBinding>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeBinding.class, "reference", RecipeBinding::reference), new dev.openallay.value.ValueSchema.Component<>(RecipeBinding.class, "originInvocationId", RecipeBinding::originInvocationId)), arguments -> new RecipeBinding((RecipeReference) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Decode.ValueSchemaProvider.class)
public static final class Decode {
    private final RichComponent component;
    private final String fallbackText;
    private final String failureCode;
    public Decode(RichComponent component, String fallbackText, String failureCode) {

            if ((component == null) == (failureCode == null)) {
                throw new IllegalArgumentException("component decode must succeed or fail");
            }
            if (fallbackText == null || dev.openallay.util.Java8Strings.isBlank(fallbackText)) {
                throw new IllegalArgumentException("component decode fallback is required");
            }
            if (component != null) RichComponent.requireKnown(component);

        this.component = component;
        this.fallbackText = fallbackText;
        this.failureCode = failureCode;
    }
    public RichComponent component() { return component; }
    public String fallbackText() { return fallbackText; }
    public String failureCode() { return failureCode; }
static Decode success(RichComponent component) {
            return new Decode(
                    Objects.requireNonNull(component, "component"), component.fallbackText(), null);
        }
static Decode failure(String fallback, String code) {
            return new Decode(null, fallback, code);
        }
public boolean successful() {
            return component != null;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Decode)) return false;
        Decode that = (Decode) other;
        return java.util.Objects.equals(component, that.component) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(failureCode, that.failureCode);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(component);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        return hash;
    }
    @Override public String toString() { return "Decode[component=" + component + ", fallbackText=" + fallbackText + ", failureCode=" + failureCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Decode> schema() {
            return new dev.openallay.value.ValueSchema<>(Decode.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Decode>>asList(new dev.openallay.value.ValueSchema.Component<>(Decode.class, "component", Decode::component), new dev.openallay.value.ValueSchema.Component<>(Decode.class, "fallbackText", Decode::fallbackText), new dev.openallay.value.ValueSchema.Component<>(Decode.class, "failureCode", Decode::failureCode)), arguments -> new Decode((RichComponent) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
}
