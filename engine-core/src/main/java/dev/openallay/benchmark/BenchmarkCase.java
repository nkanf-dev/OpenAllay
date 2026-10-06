package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Objects;

public record BenchmarkCase(
        String id,
        String category,
        String prompt,
        String fixture,
        List<String> requiredCapabilities,
        int attempts,
        int maxModelTurns,
        Verifier verifier) {
    public BenchmarkCase {
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
    }

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

    public record Verifier(
            Kind kind,
            String path,
            JsonElement expected,
            String contains) {
        public Verifier {
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
        }

        @Override
        public JsonElement expected() {
            return expected == null ? null : dev.openallay.json.JsonTrees.copy(expected);
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
}
