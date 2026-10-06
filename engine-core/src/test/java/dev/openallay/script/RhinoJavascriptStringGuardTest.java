package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openallay.model.CancellationSignal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class RhinoJavascriptStringGuardTest {
    @ParameterizedTest(name = "{0}, unrestricted={1}")
    @MethodSource("nativeOperationCases")
    void keepsNativeOperationsAndTheirExactUserSources(String source, boolean unrestricted, String expected) {
        assertEquals(dev.openallay.json.JsonTrees.parse(expected), new RhinoJavascriptRuntime().execute(source,
                Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value());
    }

    @ParameterizedTest
    @MethodSource("legalCoercionCases")
    void preservesEcmaScriptCoercionBeforeCallingTheNativeMethod(
            String source, boolean unrestricted, String expected) {
        assertEquals(dev.openallay.json.JsonTrees.parse(expected), new RhinoJavascriptRuntime().execute(source,
                Map.of(), Map.of(), Map.of(), new CancellationSignal(), null, null, unrestricted).value());
    }

    private static Stream<Arguments> legalCoercionCases() {
        List<String[]> cases = List.of(
                new String[]{"return [\"x\".repeat(),\"x\".repeat(undefined),\"x\".repeat(NaN),\"x\".repeat(-0.9)];",
                        "[\"\",\"\",\"\",\"\"]"},
                new String[]{"return [\"x\".padStart(),\"x\".padStart(NaN),\"x\".padStart(-1),\"x\".padEnd(-Infinity)];",
                        "[\"x\",\"x\",\"x\",\"x\"]"},
                new String[]{"return [\"ab\".repeat(2.9),\"x\".padStart(3.9,\"a\"),\"x\".padEnd(3.9,\"a\")];",
                        "[\"abab\",\"aax\",\"xaa\"]"},
                new String[]{"return [\"\".repeat(1e15),\"x\".padStart(Infinity,\"\"),\"x\".padEnd(1e15,\"\")];",
                        "[\"\",\"x\",\"x\"]"},
                new String[]{"return [\"x\".repeat(524288).length,\"😀\".repeat(262144).length,\"x\".padStart(524288).length];",
                        "[524288,524288,524288]"});
        return cases.stream().flatMap(entry -> Stream.of(false, true)
                .map(unrestricted -> Arguments.of(entry[0], unrestricted, entry[1])));
    }

    @ParameterizedTest
    @MethodSource("allocationCases")
    void rejectsOversizedSafeAllocationsBeforeTheNativeOperation(String source) {
        JavascriptExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                JavascriptExecutionException.class, () -> new RhinoJavascriptRuntime().execute(
                        source, Map.of(), Map.of(), new CancellationSignal()));
        assertEquals("javascript_result_budget_exceeded", failure.code());
        assertEquals(600_000, new RhinoJavascriptRuntime().execute(source, Map.of(), Map.of(), Map.of(),
                new CancellationSignal(), null, null, true).value().getAsInt());
    }

    private static Stream<String> allocationCases() {
        return Stream.of("return \"abcd\".repeat(150000).length;",
                "return \"😀\".repeat(300000).length;",
                "return \"x\".padStart(600000,\"a\").length;",
                "return \"x\".padEnd(600000,\"a\").length;");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void coercesGuestReceiversAndArgumentsOnceInNativeOrder(boolean unrestricted) {
        String source = """
                let calls = [];
                const receiver = {toString() { calls.push("receiver"); return "ab"; }};
                const count = {valueOf() { calls.push("count"); return 2; }};
                const fill = {toString() { calls.push("fill"); return "c"; }};
                const repeated = String.prototype.repeat.call(receiver, count);
                const padded = String.prototype.padStart.call(receiver, 5, fill);
                return {repeated, padded, calls};
                """;
        assertEquals(dev.openallay.json.JsonTrees.parse("""
                {"repeated":"abab","padded":"cccab","calls":["receiver","count","receiver","fill"]}
                """), new RhinoJavascriptRuntime().execute(source, Map.of(), Map.of(), Map.of(),
                new CancellationSignal(), null, null, unrestricted).value());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void retainsNativeRangeErrorsAndNonConstructorMethods(boolean unrestricted) {
        String source = """
                const errors = [];
                try { "x".repeat(-1); } catch (error) { errors.push(error.name); }
                try { "x".repeat(Infinity); } catch (error) { errors.push(error.name); }
                try { new String.prototype.repeat(1); } catch (error) { errors.push(error.name); }
                return errors;
                """;
        assertEquals(dev.openallay.json.JsonTrees.parse("[\"RangeError\",\"RangeError\",\"TypeError\"]"),
                new RhinoJavascriptRuntime().execute(source, Map.of(), Map.of(), Map.of(),
                        new CancellationSignal(), null, null, unrestricted).value());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "return \"abcd\".repeat(5).length;",
            "return \"😀\".repeat(10).length;",
            "return \"x\".padStart(20,\"a\").length;",
            "return \"x\".padEnd(20,\"a\").length;"
    })
    void usesTheSelectedRuntimeCharacterLimitRatherThanAFixedGlobalLimit(String source) {
        var limits = new JavascriptRuntimeLimits(65_536, 64, 250_000, 250_000, 16_384, 16);
        var runtime = new RhinoJavascriptRuntime(java.time.Duration.ofSeconds(2), limits);
        JavascriptExecutionException failure = org.junit.jupiter.api.Assertions.assertThrows(
                JavascriptExecutionException.class, () -> runtime.execute(
                        source, Map.of(), Map.of(), new CancellationSignal()));
        assertEquals("javascript_result_budget_exceeded", failure.code());
        assertEquals(20, runtime.execute(source, Map.of(), Map.of(), Map.of(),
                new CancellationSignal(), null, null, true).value().getAsInt());
    }

    private static Stream<Arguments> nativeOperationCases() {
        List<String[]> cases = List.of(
                new String[]{"return Array(700).fill(\"x\".repeat(1000)).join(\"\").length;", "700000"},
                new String[]{"return \"x\".repeat(1000).length;", "1000"},
                new String[]{"return [1,2,3].fill(\"x\");", "[\"x\",\"x\",\"x\"]"},
                new String[]{"return [\"x\",\"x\",\"x\"].join(\"\");", "\"xxx\""},
                new String[]{"return Array(3).fill(\"x\");", "[\"x\",\"x\",\"x\"]"},
                new String[]{"return Array(3).join(\"x\");", "\"xx\""},
                new String[]{"return new Array(3).fill(\"x\");", "[\"x\",\"x\",\"x\"]"},
                new String[]{"return Array.of(1,2,3).fill(\"x\");", "[\"x\",\"x\",\"x\"]"},
                new String[]{"return \"x\".padStart(3, \"a\");", "\"aax\""},
                new String[]{"return \"x\".padEnd(3, \"a\");", "\"xaa\""});
        return cases.stream().flatMap(entry -> Stream.of(false, true)
                .map(unrestricted -> Arguments.of(entry[0], unrestricted, entry[1])));
    }
}
