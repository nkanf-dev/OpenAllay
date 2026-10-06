package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import dev.openallay.api.extension.*;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.JavascriptExecutionException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UniversalExtensionBridgeTest {
    @Test void mapsImmutableDeclarationsOnceInjectsHostAndNeverOpensWorldForPureJs() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger worldOpens = new AtomicInteger();
        ExtensionHost host = UniversalExtensionFixtures.host(worldOpens);
        var descriptor = UniversalExtensionFixtures.descriptor("test:extension");
        var contribution = new ExtensionContribution(
                List.of(new JavascriptModuleSource("test:module", "module.exports = 42;")),
                List.of(new SkillSource("test:source", "test-skill/SKILL.md", Map.of("test-skill/SKILL.md", """
                        ---
                        name: test-skill
                        description: A real Skill
                        ---
                        Run the contributed module.
                        """))),
                List.of(new ResultViewDeclaration("test:view", ResultViewDeclaration.Kind.TABLE, "Table")),
                List.of(), List.of());
        var bridge = new UniversalExtensionBridge(new OpenAllayExtension() {
            public ExtensionDescriptor descriptor() { fail("Verified descriptor must not be read again"); return null; }
            public ExtensionContribution contribution(ExtensionHost actual) {
                assertSame(host, actual); calls.incrementAndGet(); return contribution;
            }
        }, descriptor, host);
        assertEquals(0, calls.get());
        var registry = UniversalExtensionFixtures.registry();
        assertEquals(dev.openallay.extension.OpenAllayExtensionState.ACTIVE, registry.register(bridge).state());
        assertSame(bridge.contribution(), bridge.contribution());
        assertEquals(1, calls.get());
        assertEquals(0, worldOpens.get());
        assertTrue(bridge.contribution().dataModules().isEmpty());
        assertEquals("test-skill", bridge.contribution().skills().getFirst().directoryName());
        assertEquals(dev.openallay.script.result.JavascriptSemanticKind.TABLE,
                bridge.contribution().resultViews().getFirst().kind());
        assertEquals("[0.4,0.5)", bridge.descriptor().openAllayApiVersionRange());
    }
    @Test void duplicateRegistryIdentityNeverCallsAnotherContribution() {
        var registry = UniversalExtensionFixtures.registry();
        registry.register(UniversalExtensionFixtures.bridge("test:duplicate", ExtensionContribution.empty()));
        AtomicInteger calls = new AtomicInteger();
        var descriptor = UniversalExtensionFixtures.descriptor("test:duplicate");
        var bridge = new UniversalExtensionBridge(new OpenAllayExtension() {
            public ExtensionDescriptor descriptor() { return descriptor; }
            public ExtensionContribution contribution(ExtensionHost host) { calls.incrementAndGet(); return ExtensionContribution.empty(); }
        }, descriptor, UniversalExtensionFixtures.host(new AtomicInteger()));
        assertEquals("duplicate_extension_id", registry.register(bridge).diagnostic());
        assertEquals(0, calls.get()); // Requires root's descriptor-first registry hunk.
    }
    @Test void roundTripsOnlyDetachedJsonWithDeclaredTypes() throws Exception {
        AtomicReference<List<String>> arguments = new AtomicReference<>();
        var registry = registryWithMethod(JavascriptHostValueType.JSON, (context, values) -> {
            arguments.set(values); return values.getFirst();
        });
        try (var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("json"),
                new CancellationSignal())) {
            scope.open(ignored -> fail("No fabricated evidence"));
            for (String json : List.of("42", "true", "\"text\"", "null", "[1,{\"a\":2}]")) {
                JsonElement input = dev.openallay.json.JsonTrees.parse(json);
                assertEquals(input, scope.invokeHostMethod("test:binding", "read", List.of(input)));
                assertEquals(List.of(input.toString()), arguments.get());
                assertThrows(UnsupportedOperationException.class, () -> arguments.get().add("1"));
            }
            assertThrows(JavascriptExecutionException.class,
                    () -> scope.invokeHostMethod("test:binding", "read", List.of()));
        }
    }
    @Test void rejectsMalformedTrailingNonFiniteJavaNullAndWrongResultType() throws Exception {
        for (String returned : java.util.Arrays.asList(null, "{", "1 2", "NaN", "Infinity", "1e999", "\"wrong\"")) {
            var registry = registryWithMethod(JavascriptHostValueType.INTEGER, (context, values) -> returned);
            try (var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("invalid"),
                    new CancellationSignal())) {
                scope.open(ignored -> {});
                var failure = assertThrows(JavascriptExecutionException.class,
                        () -> scope.invokeHostMethod("test:binding", "read", List.of(dev.openallay.json.JsonTrees.parse("1"))));
                assertEquals("javascript_extension_host_invalid", failure.code());
            }
        }
    }
    @Test void mapsOnlySdkSafeDiagnosticsAndPreservesCancellation() throws Exception {
        var safe = invokeFailure((context, values) -> { throw new ExtensionException("test_safe", "Safe summary",
                new IllegalStateException("SECRET")); });
        assertEquals("test_safe", safe.code());
        assertEquals("Safe summary", safe.getMessage());
        assertNull(safe.getCause());
        var foreign = invokeFailure((context, values) -> { throw new AssertionError("SECRET"); });
        assertEquals("javascript_extension_host_failed", foreign.code());
        assertFalse(foreign.getMessage().contains("SECRET"));
        var registry = registryWithMethod(JavascriptHostValueType.JSON, (context, values) -> {
            throw new CancellationException("cancelled");
        });
        try (var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("cancellation"),
                new CancellationSignal())) {
            scope.open(ignored -> {});
            assertThrows(ModelClientException.class, () -> scope.invokeHostMethod("test:binding", "read",
                    List.of(dev.openallay.json.JsonTrees.parse("1"))));
        }
        CancellationSignal signal = new CancellationSignal();
        registry = registryWithMethod(JavascriptHostValueType.JSON, (context, values) -> {
            signal.cancel(); throw new ExtensionException("late", "Late failure");
        });
        try (var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("cancel"), signal)) {
            scope.open(ignored -> {});
            assertThrows(ModelClientException.class, () -> scope.invokeHostMethod("test:binding", "read",
                    List.of(dev.openallay.json.JsonTrees.parse("1"))));
        }
    }
    @Test void participantAndHostMethodsShareOneSdkIdentityPerActualExecution() throws Exception {
        var registry = UniversalExtensionFixtures.registry();
        AtomicReference<ExtensionInvocation> participantView = new AtomicReference<>();
        java.util.List<ExtensionInvocation> opened = new java.util.ArrayList<>();
        var participant = new JavascriptInvocationParticipant() {
            public String id() { return "test:identity_participant"; }
            public AutoCloseable open(ExtensionInvocation context) {
                participantView.set(context); opened.add(context); return () -> {};
            }
        };
        var method = new JavascriptHostMethod("identity", List.of(), JavascriptHostValueType.STRING,
                (context, arguments) -> {
                    assertSame(participantView.get(), context, "One actual invocation must retain SDK identity");
                    return "\"same execution\"";
                });
        var contribution = new ExtensionContribution(List.of(), List.of(), List.of(), List.of(participant),
                List.of(new JavascriptHostBinding("test:identity", List.of(method))));
        assertEquals(dev.openallay.extension.OpenAllayExtensionState.ACTIVE,
                registry.register(UniversalExtensionFixtures.bridge("test:identity_extension", contribution)).state());
        for (int index = 0; index < 2; index++) {
            try (var scope = registry.prepareJavascriptInvocation(
                    ToolInvocationContext.developmentConsole("same-correlation"), new CancellationSignal())) {
                scope.open(ignored -> {});
                assertEquals("same execution", scope.invokeHostMethod("test:identity", "identity", List.of()).getAsString());
                assertEquals("same execution", scope.invokeHostMethod("test:identity", "identity", List.of()).getAsString());
            }
        }
        assertNotSame(opened.get(0), opened.get(1), "Correlation equality must not merge execution identities");
        opened.forEach(context -> assertThrows(JavascriptExecutionException.class, context::requireActive));
    }

    @Test void differentActiveExtensionsKeepDifferentSdkFacadesAndExactOwners() throws Exception {
        var registry = UniversalExtensionFixtures.registry();
        java.util.Map<String, ExtensionInvocation> participantViews = new java.util.HashMap<>();
        for (String owner : List.of("first", "second")) {
            String id = "test:" + owner;
            var participant = new JavascriptInvocationParticipant() {
                public String id() { return id + "_participant"; }
                public AutoCloseable open(ExtensionInvocation context) {
                    participantViews.put(id, context); return () -> {};
                }
            };
            var method = new JavascriptHostMethod("identity", List.of(), JavascriptHostValueType.BOOLEAN,
                    (context, arguments) -> {
                        assertSame(participantViews.get(id), context);
                        assertEquals(id, context.extensionId());
                        context.requireActive();
                        return "true";
                    });
            var contribution = new ExtensionContribution(List.of(), List.of(), List.of(), List.of(participant),
                    List.of(new JavascriptHostBinding(id + "_binding", List.of(method))));
            assertEquals(dev.openallay.extension.OpenAllayExtensionState.ACTIVE,
                    registry.register(UniversalExtensionFixtures.bridge(id, contribution)).state());
        }
        try (var scope = registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("shared-request"), new CancellationSignal())) {
            scope.open(ignored -> {});
            assertNotSame(participantViews.get("test:first"), participantViews.get("test:second"));
            for (String owner : List.of("first", "second")) {
                assertTrue(scope.invokeHostMethod("test:" + owner + "_binding", "identity", List.of()).getAsBoolean());
            }
        }
        participantViews.values().forEach(context -> assertThrows(JavascriptExecutionException.class, context::requireActive));
    }

    private static JavascriptExecutionException invokeFailure(JavascriptHostMethod.Invoker invoker) throws Exception {
        var registry = registryWithMethod(JavascriptHostValueType.JSON, invoker);
        try (var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("failure"),
                new CancellationSignal())) {
            scope.open(ignored -> {});
            return assertThrows(JavascriptExecutionException.class, () -> scope.invokeHostMethod("test:binding", "read",
                    List.of(dev.openallay.json.JsonTrees.parse("1"))));
        }
    }
    private static dev.openallay.extension.OpenAllayExtensionRegistry registryWithMethod(
            JavascriptHostValueType result, JavascriptHostMethod.Invoker invoker) {
        var registry = UniversalExtensionFixtures.registry();
        var method = new JavascriptHostMethod("read", List.of(JavascriptHostValueType.JSON), result, invoker);
        var contribution = new ExtensionContribution(List.of(), List.of(), List.of(), List.of(),
                List.of(new JavascriptHostBinding("test:binding", List.of(method))));
        assertEquals(dev.openallay.extension.OpenAllayExtensionState.ACTIVE,
                registry.register(UniversalExtensionFixtures.bridge("test:method", contribution)).state());
        return registry;
    }
}
