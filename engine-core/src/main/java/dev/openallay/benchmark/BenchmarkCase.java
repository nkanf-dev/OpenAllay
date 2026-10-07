package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(BenchmarkCase.ValueSchemaProvider.class)
public final class BenchmarkCase {
    private final String id;
    private final String category;
    private final String prompt;
    private final String fixture;
    private final List<String> requiredCapabilities;
    private final int attempts;
    private final int maxModelTurns;
    private final Verifier verifier;
    public BenchmarkCase(String id, String category, String prompt, String fixture, List<String> requiredCapabilities, int attempts, int maxModelTurns, Verifier verifier) {

        id = nonBlank(id, "id");
        category = nonBlank(category, "category");
        prompt = nonBlank(prompt, "prompt");
        fixture = nonBlank(fixture, "fixture");
        requiredCapabilities = List.copyOf(requiredCapabilities);
        if (requiredCapabilities.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("requiredCapabilities must not contain blanks");
        }
        if (new java.util.HashSet<>(requiredCapabilities).size()
                != requiredCapabilities.size()) {
            throw new IllegalArgumentException("requiredCapabilities must be unique");
        }
        if (attempts <= 0 || maxModelTurns <= 0) {
            throw new IllegalArgumentException("attempts and maxModelTurns must be positive");
        }
        Objects.requireNonNull(verifier, "verifier");

        this.id = id;
        this.category = category;
        this.prompt = prompt;
        this.fixture = fixture;
        this.requiredCapabilities = requiredCapabilities;
        this.attempts = attempts;
        this.maxModelTurns = maxModelTurns;
        this.verifier = verifier;
    }
    public String id() { return id; }
    public String category() { return category; }
    public String prompt() { return prompt; }
    public String fixture() { return fixture; }
    public List<String> requiredCapabilities() { return requiredCapabilities; }
    public int attempts() { return attempts; }
    public int maxModelTurns() { return maxModelTurns; }
    public Verifier verifier() { return verifier; }
public BenchmarkCase(
            String id,
            String category,
            String prompt,
            int attempts,
            int maxModelTurns,
            Verifier verifier) {
        this(id, category, prompt, "unit", List.of(), attempts, maxModelTurns, verifier);
    }
public boolean applicableTo(String fixtureId, java.util.Set<String> capabilities) {
        Objects.requireNonNull(capabilities, "capabilities");
        return fixture.equals(fixtureId) && capabilities.containsAll(requiredCapabilities);
    }
public BenchmarkCase withAttempts(int nextAttempts) {
        return new BenchmarkCase(
                id,
                category,
                prompt,
                fixture,
                requiredCapabilities,
                nextAttempts,
                maxModelTurns,
                verifier);
    }
@dev.openallay.value.ValueType(Verifier.ValueSchemaProvider.class)
public static final class Verifier {
    private final Kind kind;
    private final String path;
    private final JsonElement expected;
    private final String contains;
    public Verifier(Kind kind, String path, JsonElement expected, String contains) {

            Objects.requireNonNull(kind, "kind");
            path = path == null ? "" : path.strip();
            expected = expected == null ? null : dev.openallay.json.JsonTrees.copy(expected);
            contains = contains == null ? "" : contains;
            if (kind == Kind.JSON_PATH_EQUALS && (path.isBlank() || expected == null)) {
                throw new IllegalArgumentException(
                        "json_path_equals requires path and expected");
            }
            if ((kind == Kind.ANSWER_CONTAINS || kind == Kind.EFFECT_CONTAINS)
                    && contains.isBlank()) {
                throw new IllegalArgumentException(kind + " requires contains");
            }

        this.kind = kind;
        this.path = path;
        this.expected = expected;
        this.contains = contains;
    }
    public Kind kind() { return kind; }
    public String path() { return path; }
    public String contains() { return contains; }

        public JsonElement expected() {
            return expected == null ? null : dev.openallay.json.JsonTrees.copy(expected);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Verifier)) return false;
        Verifier that = (Verifier) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(path, that.path) && java.util.Objects.equals(expected, that.expected) && java.util.Objects.equals(contains, that.contains);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(path);
        hash = 31 * hash + java.util.Objects.hashCode(expected);
        hash = 31 * hash + java.util.Objects.hashCode(contains);
        return hash;
    }
    @Override public String toString() { return "Verifier[kind=" + kind + ", path=" + path + ", expected=" + expected + ", contains=" + contains + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Verifier> schema() {
            return new dev.openallay.value.ValueSchema<>(Verifier.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Verifier>>asList(new dev.openallay.value.ValueSchema.Component<>(Verifier.class, "kind", Verifier::kind), new dev.openallay.value.ValueSchema.Component<>(Verifier.class, "path", Verifier::path), new dev.openallay.value.ValueSchema.Component<>(Verifier.class, "expected", Verifier::expected), new dev.openallay.value.ValueSchema.Component<>(Verifier.class, "contains", Verifier::contains)), arguments -> new Verifier((Kind) arguments[0], (String) arguments[1], (JsonElement) arguments[2], (String) arguments[3]));
        }
    }
}
public enum Kind {
        JSON_PATH_EQUALS,
        ANSWER_CONTAINS,
        EFFECT_CONTAINS,
        NON_EMPTY_RESULT
    }
private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BenchmarkCase)) return false;
        BenchmarkCase that = (BenchmarkCase) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(category, that.category) && java.util.Objects.equals(prompt, that.prompt) && java.util.Objects.equals(fixture, that.fixture) && java.util.Objects.equals(requiredCapabilities, that.requiredCapabilities) && attempts == that.attempts && maxModelTurns == that.maxModelTurns && java.util.Objects.equals(verifier, that.verifier);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(category);
        hash = 31 * hash + java.util.Objects.hashCode(prompt);
        hash = 31 * hash + java.util.Objects.hashCode(fixture);
        hash = 31 * hash + java.util.Objects.hashCode(requiredCapabilities);
        hash = 31 * hash + Integer.hashCode(attempts);
        hash = 31 * hash + Integer.hashCode(maxModelTurns);
        hash = 31 * hash + java.util.Objects.hashCode(verifier);
        return hash;
    }
    @Override public String toString() { return "BenchmarkCase[id=" + id + ", category=" + category + ", prompt=" + prompt + ", fixture=" + fixture + ", requiredCapabilities=" + requiredCapabilities + ", attempts=" + attempts + ", maxModelTurns=" + maxModelTurns + ", verifier=" + verifier + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BenchmarkCase> schema() {
            return new dev.openallay.value.ValueSchema<>(BenchmarkCase.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BenchmarkCase>>asList(new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "id", BenchmarkCase::id), new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "category", BenchmarkCase::category), new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "prompt", BenchmarkCase::prompt), new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "fixture", BenchmarkCase::fixture), new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "requiredCapabilities", BenchmarkCase::requiredCapabilities), new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "attempts", BenchmarkCase::attempts), new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "maxModelTurns", BenchmarkCase::maxModelTurns), new dev.openallay.value.ValueSchema.Component<>(BenchmarkCase.class, "verifier", BenchmarkCase::verifier)), arguments -> new BenchmarkCase((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (List) arguments[4], (Integer) arguments[5], (Integer) arguments[6], (Verifier) arguments[7]));
        }
    }
}
