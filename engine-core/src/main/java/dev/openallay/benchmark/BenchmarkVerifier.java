package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

public final class BenchmarkVerifier {
    public Verification verify(BenchmarkCase testCase, BenchmarkOutcome outcome) {
        BenchmarkCase.Verifier verifier = testCase.verifier();
        return switch (verifier.kind()) {
            case NON_EMPTY_RESULT -> outcome.canonicalResult().isJsonNull()
                    ? Verification.failure("canonical result is empty")
                    : Verification.success();
            case ANSWER_CONTAINS -> answerContains(
                            outcome.canonicalResult(), verifier.contains())
                    ? Verification.success()
                    : Verification.failure("final answer does not contain expected value");
            case EFFECT_CONTAINS -> outcome.observedEffects().stream()
                    .anyMatch(effect -> contains(effect, verifier.contains()))
                    ? Verification.success()
                    : Verification.failure("observed effects do not contain expected value");
            case JSON_PATH_EQUALS -> {
                JsonElement actual = resolve(outcome.canonicalResult(), verifier.path());
                yield actual != null && actual.equals(verifier.expected())
                        ? Verification.success()
                        : Verification.failure("canonical path did not equal expected value");
            }
        };
    }

    private static JsonElement resolve(JsonElement root, String path) {
        JsonElement current = root;
        for (String segment : path.split("\\.")) {
            final class $oaPattern0_Holder { com.google.gson.JsonElement value; JsonObject bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = current) instanceof com.google.gson.JsonObject && (($oaPattern0_holder.bound = (JsonObject) $oaPattern0_holder.value) != null))) || !$oaPattern0_holder.bound.has(segment)) {
                return null;
            }
            current = $oaPattern0_holder.bound.get(segment);
        }
        return current;
    }

    private static boolean contains(String value, String expected) {
        return value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private static boolean answerContains(JsonElement canonicalResult, String expected) {
        final class $oaPattern1_Holder { com.google.gson.JsonElement value; JsonObject bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if (!((($oaPattern1_holder.value = canonicalResult) instanceof com.google.gson.JsonObject && (($oaPattern1_holder.bound = (JsonObject) $oaPattern1_holder.value) != null)))
                || !$oaPattern1_holder.bound.has("answer")
                || !$oaPattern1_holder.bound.get("answer").isJsonPrimitive()
                || !$oaPattern1_holder.bound.getAsJsonPrimitive("answer").isString()) {
            return false;
        }
        return contains($oaPattern1_holder.bound.get("answer").getAsString(), expected);
    }

    @dev.openallay.value.ValueType(Verification.ValueSchemaProvider.class)
public static final class Verification {
    private final boolean passed;
    private final String diagnostic;
    public Verification(boolean passed, String diagnostic) {

            diagnostic = diagnostic == null ? "" : diagnostic;

        this.passed = passed;
        this.diagnostic = diagnostic;
    }
    public boolean passed() { return passed; }
    public String diagnostic() { return diagnostic; }
public static Verification success() {
            return new Verification(true, "");
        }
public static Verification failure(String diagnostic) {
            return new Verification(false, diagnostic);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Verification)) return false;
        Verification that = (Verification) other;
        return passed == that.passed && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(passed);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "Verification[passed=" + passed + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Verification> schema() {
            return new dev.openallay.value.ValueSchema<>(Verification.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Verification>>asList(new dev.openallay.value.ValueSchema.Component<>(Verification.class, "passed", Verification::passed), new dev.openallay.value.ValueSchema.Component<>(Verification.class, "diagnostic", Verification::diagnostic)), arguments -> new Verification((Boolean) arguments[0], (String) arguments[1]));
        }
    }
}
}
