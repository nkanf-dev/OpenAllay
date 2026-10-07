package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict benchmark corpus codec. */
public final class BenchmarkCorpusCodec {
    private static final Set<String> ROOT_FIELDS =
            dev.openallay.util.Java8Collections.setOf("cases");
    private static final Set<String> CASE_FIELDS =
            dev.openallay.util.Java8Collections.setOf("id", "category", "prompt", "fixture", "requiredCapabilities", "attempts", "maxModelTurns", "verifier");
    private static final Set<String> VERIFIER_FIELDS =
            dev.openallay.util.Java8Collections.setOf("kind", "path", "expected", "contains");

    public BenchmarkCorpus decode(Reader reader) {
        JsonElement parsed = dev.openallay.json.JsonTrees.parse(reader);
        if (!(parsed instanceof JsonObject root)) {
            throw invalid("Benchmark corpus root must be an object");
        }
        exactFields(root, ROOT_FIELDS, "corpus");
        if (!root.has("cases") || !root.get("cases").isJsonArray()) {
            throw invalid("cases must be an array");
        }
        ArrayList<BenchmarkCase> cases = new ArrayList<>();
        root.getAsJsonArray("cases").forEach(element -> {
            if (!(element instanceof JsonObject object)) {
                throw invalid("case must be an object");
            }
            exactFields(object, CASE_FIELDS, "case");
            cases.add(new BenchmarkCase(
                    string(object, "id"),
                    string(object, "category"),
                    string(object, "prompt"),
                    string(object, "fixture"),
                    strings(object, "requiredCapabilities"),
                    integer(object, "attempts"),
                    integer(object, "maxModelTurns"),
                    verifier(object.getAsJsonObject("verifier"))));
        });
        return new BenchmarkCorpus(cases);
    }

    private static BenchmarkCase.Verifier verifier(JsonObject object) {
        if (object == null) {
            throw invalid("verifier must be an object");
        }
        exactFields(object, VERIFIER_FIELDS, "verifier");
        BenchmarkCase.Kind kind;
        try {
            kind = BenchmarkCase.Kind.valueOf(string(object, "kind"));
        } catch (IllegalArgumentException failure) {
            throw invalid("Unknown verifier kind");
        }
        return new BenchmarkCase.Verifier(
                kind,
                optionalString(object, "path"),
                object.has("expected") ? object.get("expected") : null,
                optionalString(object, "contains"));
    }

    private static void exactFields(JsonObject object, Set<String> allowed, String owner) {
        for (String field : dev.openallay.json.JsonTrees.keys(object)) {
            if (!allowed.contains(field)) {
                throw invalid("Unknown " + owner + " field " + field);
            }
        }
    }

    private static String string(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.getAsJsonPrimitive(name).isString()) {
            throw invalid(name + " must be a string");
        }
        return object.get(name).getAsString();
    }

    private static String optionalString(JsonObject object, String name) {
        return object.has(name) ? string(object, name) : "";
    }

    private static int integer(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.getAsJsonPrimitive(name).isNumber()) {
            throw invalid(name + " must be an integer");
        }
        try {
            return object.get(name).getAsInt();
        } catch (NumberFormatException failure) {
            throw invalid(name + " must be an integer");
        }
    }

    private static java.util.List<String> strings(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonArray()) {
            throw invalid(name + " must be an array");
        }
        ArrayList<String> values = new ArrayList<>();
        object.getAsJsonArray(name).forEach(value -> {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw invalid(name + " must contain only strings");
            }
            values.add(value.getAsString());
        });
        return dev.openallay.util.Java8Collections.listCopyOf(values);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
