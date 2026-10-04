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
            if (!(current instanceof JsonObject object) || !object.has(segment)) {
                return null;
            }
            current = object.get(segment);
        }
        return current;
    }

    private static boolean contains(String value, String expected) {
        return value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private static boolean answerContains(JsonElement canonicalResult, String expected) {
        if (!(canonicalResult instanceof JsonObject object)
                || !object.has("answer")
                || !object.get("answer").isJsonPrimitive()
                || !object.getAsJsonPrimitive("answer").isString()) {
            return false;
        }
        return contains(object.get("answer").getAsString(), expected);
    }

    public record Verification(boolean passed, String diagnostic) {
        public Verification {
            diagnostic = diagnostic == null ? "" : diagnostic;
        }

        public static Verification success() {
            return new Verification(true, "");
        }

        public static Verification failure(String diagnostic) {
            return new Verification(false, diagnostic);
        }
    }
}
