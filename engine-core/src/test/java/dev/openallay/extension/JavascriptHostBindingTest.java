package dev.openallay.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import dev.openallay.world.WorldObservationRuntime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class JavascriptHostBindingTest {
    private static final String OWNER = "test:extension";
    private static final String BINDING = "test_native:methods";
    private static final String WRITE = "test_native:world_write";

    @Test
    void safeMethodsUseCanonicalOwnerContextWithoutExposingJavaOrCallbacks() {
        Fixture fixture = new Fixture();
        AtomicReference<JavascriptInvocationContext> opened = new AtomicReference<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        fixture.register(OWNER, List.of(method("echo", List.of(JavascriptHostValueType.JSON),
                JavascriptHostValueType.JSON, Set.of(), (context, arguments) -> {
                    assertSame(opened.get(), context);
                    assertSame(worker.get(), Thread.currentThread());
                    assertEquals(OWNER, context.extensionId());
                    assertFalse(context.invocation().unrestrictedJavascript());
                    return arguments.getFirst();
                })), List.of(participant("test:scope", context -> {
                    opened.set(context); worker.set(Thread.currentThread()); return () -> {};
                })), List.of());
        var roundTrip = fixture.success("safe", """
                const native = require("test_native:methods");
                const value = native.echo({groups: [{values: [1, 2]}], empty: []});
                // Verify the complete host value before any model-facing representative sampling.
                return JSON.stringify({ java: typeof Java, packages: typeof Packages, getClass: typeof native.getClass,
                  resultClass: typeof value.getClass, prototype: Object.getPrototypeOf(native) === null,
                  value: JSON.parse(JSON.stringify(value)), same: native === require("test_native:methods") });
                """);
        var value = JsonParser.parseString(roundTrip.getAsString());
        assertEquals("undefined", value.getAsJsonObject().get("java").getAsString());
        assertEquals("undefined", value.getAsJsonObject().get("packages").getAsString());
        assertEquals("undefined", value.getAsJsonObject().get("getClass").getAsString());
        assertEquals("undefined", value.getAsJsonObject().get("resultClass").getAsString());
        assertTrue(value.getAsJsonObject().get("prototype").getAsBoolean());
        assertTrue(value.getAsJsonObject().get("same").getAsBoolean());
        assertEquals(JsonParser.parseString("""
                {"groups":[{"values":[1,2]}],"empty":[]}
                """),
                value.getAsJsonObject().get("value"));
        assertTrue(opened.get().cancellation().isCancelled());
    }

    @Test
    void argumentsHaveExactCountAndTypesAndRejectExecutableOrWrappedValues() {
        Fixture fixture = new Fixture();
        AtomicInteger calls = new AtomicInteger();
        fixture.register(OWNER, List.of(method("echo", List.of(JavascriptHostValueType.JSON),
                JavascriptHostValueType.JSON, Set.of(), (context, arguments) -> {
                    calls.incrementAndGet(); return arguments.getFirst();
                })), List.of(), List.of());
        for (String argument : List.of("", "1, 2", "function() {}", "NaN", "undefined",
                "{field: undefined}", "[undefined]", "Promise.resolve(1)",
                "(() => { const x = {}; x.self = x; return x; })()")) {
            assertInstanceOf(ToolResult.Failure.class, fixture.invoke("invalid-" + calls.get(),
                    "return require('test_native:methods').echo(" + argument + ");", false), argument);
        }
        assertInstanceOf(ToolResult.Failure.class, fixture.invoke("wrapped",
                "return require('test_native:methods').echo(new (Java.type('java.util.ArrayList'))());", true));
        assertEquals(0, calls.get());
    }

    @Test
    void methodDeclarationRejectsConstructorsUndeclaredCapabilitiesAndCollisionsAtomically() {
        assertThrows(IllegalArgumentException.class, () -> method("constructor", List.of(),
                JavascriptHostValueType.NULL, Set.of(), (context, arguments) -> JsonNull.INSTANCE));
        Fixture fixture = new Fixture();
        var denied = fixture.register(OWNER, List.of(method("write", List.of(),
                JavascriptHostValueType.NULL, Set.of(WRITE), (context, arguments) -> JsonNull.INSTANCE)),
                List.of(), List.of());
        assertEquals("extension_registration_failed", denied.diagnostic());
        assertEquals(0, fixture.registry.snapshot().generation());
        assertTrue(fixture.modules.ids().isEmpty());
        assertEquals(OpenAllayExtensionState.ACTIVE,
                fixture.register(OWNER, List.of(), List.of(), List.of()).state());
        var collision = fixture.register("other:extension", List.of(), List.of(), List.of());
        assertEquals("duplicate_contribution_id", collision.diagnostic());
        assertEquals(1, fixture.registry.snapshot().generation());
    }

    @Test
    void registrationUnrestrictedAndServerRequestsNeverGrantWrites() {
        Fixture fixture = writeFixture();
        assertEquals("javascript_extension_capability_denied", fixture.failure("default", false));
        assertEquals("javascript_extension_capability_denied", fixture.failure("unrestricted", true));
        fixture.registry.replaceCapabilityPolicy(new ExtensionCapabilityPolicy(Map.of(OWNER, Set.of(WRITE))));
        fixture.registry.freezeJavascriptRequest("server", false);
        assertEquals("javascript_extension_capability_denied", fixture.failure("server", true));
        fixture.registry.freezeJavascriptRequest("approved", true);
        var value = fixture.success("approved", "return {java: typeof Java, value: require('test_native:methods').write()};");
        assertEquals("undefined", value.getAsJsonObject().get("java").getAsString());
        assertEquals("written", value.getAsJsonObject().get("value").getAsString());
    }

    @Test
    void requestFreezesOldGrantButNewRequestsUseChangedSettingsAndUnknownGrantsAreNotInherited() {
        Fixture fixture = writeFixture();
        fixture.registry.freezeJavascriptRequest("old-denied", true);
        fixture.registry.replaceCapabilityPolicy(new ExtensionCapabilityPolicy(Map.of(OWNER, Set.of(WRITE))));
        fixture.registry.freezeJavascriptRequest("approved", true);
        fixture.registry.replaceCapabilityPolicy(ExtensionCapabilityPolicy.defaults());
        assertEquals("javascript_extension_capability_denied", fixture.failure("old-denied", false));
        assertEquals("written", fixture.success("approved", "return require('test_native:methods').write();").getAsString());
        fixture.registry.freezeJavascriptRequest("new-denied", true);
        assertEquals("javascript_extension_capability_denied", fixture.failure("new-denied", false));
        Fixture future = new Fixture();
        future.registry.replaceCapabilityPolicy(new ExtensionCapabilityPolicy(Map.of(OWNER, Set.of(WRITE))));
        future.register(OWNER, List.of(writeMethod()), List.of(), List.of(capability()));
        future.registry.freezeJavascriptRequest("future", true);
        assertEquals("javascript_extension_capability_denied", future.failure("future", false));
    }

    @Test
    void eachExtensionGetsOnlyItsOwnGrantEvenWhenCallerHasOtherExtensionAuthority() {
        Fixture fixture = new Fixture();
        AtomicReference<JavascriptInvocationContext> context = new AtomicReference<>();
        fixture.register(OWNER, List.of(writeMethod()), List.of(), List.of(capability()));
        fixture.registry.register(new OpenAllayExtension() {
            public OpenAllayExtensionDescriptor descriptor() { return descriptorFor("other:extension"); }
            public OpenAllayExtensionContribution contribution() {
                return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of(),
                        List.of(participant("other:scope", authority -> {
                            context.set(authority);
                            assertFalse(authority.hasCapability(WRITE));
                            assertEquals("javascript_extension_capability_denied", assertThrows(
                                    JavascriptExecutionException.class, () -> authority.requireCapability(WRITE)).code());
                            return () -> {};
                        })),
                        List.of(), List.of());
            }
        });
        fixture.registry.replaceCapabilityPolicy(new ExtensionCapabilityPolicy(Map.of(OWNER, Set.of(WRITE))));
        fixture.registry.freezeJavascriptRequest("own", true);
        fixture.success("own", "return require('test_native:methods').write();");
        assertEquals("other:extension", context.get().extensionId());
        assertFalse(context.get().completedSuccessfully() && context.get().invocation().unrestrictedJavascript());
    }

    @Test
    void readOnlyBindingFunctionsAndDetachedResultsCannotBeMutatedOrConstructed() {
        Fixture fixture = echoFixture();
        for (String source : List.of("require('test_native:methods').echo = () => 1; return 1;",
                "require('test_native:methods').extra = 1; return 1;",
                "new (require('test_native:methods').echo)(1); return 1;",
                "const value = require('test_native:methods').echo({list:[1]}); value.list.push(2); return value;",
                "const value = require('test_native:methods').echo({field:1}); value.field = 2; return value;")) {
            assertInstanceOf(ToolResult.Failure.class, fixture.invoke("readonly", source, false));
        }
    }

    @Test
    void hostResultContractAndNativeErrorsFailClosedWithoutForeignMessages() {
        Fixture fixture = new Fixture();
        fixture.register(OWNER, List.of(
                method("badType", List.of(), JavascriptHostValueType.STRING, Set.of(),
                        (context, arguments) -> new JsonPrimitive(1)),
                method("nonFinite", List.of(), JavascriptHostValueType.JSON, Set.of(),
                        (context, arguments) -> new JsonPrimitive(Double.NaN)),
                method("cycle", List.of(), JavascriptHostValueType.JSON, Set.of(),
                        (context, arguments) -> {
                            var value = new com.google.gson.JsonObject(); value.add("self", value); return value;
                        }),
                method("error", List.of(), JavascriptHostValueType.NULL, Set.of(),
                        (context, arguments) -> { throw new AssertionError("secret-token"); }),
                method("domain", List.of(), JavascriptHostValueType.NULL, Set.of(),
                        (context, arguments) -> { throw new JavascriptExecutionException("unsupported_topology", "This topology is unsupported"); })),
                List.of(), List.of());
        for (String name : List.of("badType", "nonFinite", "cycle")) {
            assertEquals("javascript_extension_host_invalid", assertInstanceOf(ToolResult.Failure.class,
                    fixture.invoke("bad-result", "return require('test_native:methods')." + name + "();", false)).code());
        }
        var failure = assertInstanceOf(ToolResult.Failure.class, fixture.invoke("native-error",
                "return require('test_native:methods').error();", false));
        assertEquals("javascript_extension_host_failed", failure.code());
        assertFalse(failure.message().contains("secret-token"));
        assertEquals("unsupported_topology", assertInstanceOf(ToolResult.Failure.class,
                fixture.invoke("domain-error", "return require('test_native:methods').domain();", false)).code());
    }

    @Test
    void nativeDenialCannotBecomeAJavaExceptionWrapperInGuestCatchScope() {
        Fixture fixture = writeFixture();
        var result = fixture.invoke("caught-denial", """
                try { require('test_native:methods').write(); }
                catch (error) {
                  return {getClass: typeof error.getClass, javaException: typeof error.javaException,
                    rhinoException: typeof error.rhinoException, hidden: typeof __exception__, java: typeof Java};
                }
                return {unexpected: true};
                """, false);
        if (result instanceof ToolResult.Success<RunJavascriptTool.Output> success) {
            // If the engine projects a catchable native error, only ECMAScript data is acceptable.
            var value = success.value().preview().getAsJsonObject();
            for (String property : List.of("getClass", "javaException", "rhinoException", "hidden", "java")) {
                assertEquals("undefined", value.get(property).getAsString());
            }
        } else {
            // Raw control failures are deliberately not catchable in this Rhino build.
            assertEquals("javascript_extension_capability_denied",
                    assertInstanceOf(ToolResult.Failure.class, result).code());
        }
    }

    @Test
    void internalHostTransportIsExactAndIndependentOfModelResultStringBudget() {
        Fixture fixture = new Fixture();
        fixture.register(OWNER, List.of(method("length", List.of(JavascriptHostValueType.STRING),
                JavascriptHostValueType.INTEGER, Set.of(), (context, arguments) ->
                        new JsonPrimitive(arguments.getFirst().getAsString().length()))), List.of(), List.of());
        String row = "x".repeat(1000);
        assertEquals(700_000, fixture.success("large-transport",
                "const rows = []; for (let i = 0; i < 700; i++) rows.push('" + row + "'); "
                        + "return require('test_native:methods').length(rows.join(''));").getAsInt());
    }

    @Test
    void nativeWaitLongerThanDefaultDeadlineDoesNotSpendInterpreterBudgetButLoopsStillTimeout() {
        Fixture fixture = new Fixture(new RhinoJavascriptRuntime(Duration.ofMillis(300)));
        fixture.register(OWNER, List.of(method("wait", List.of(), JavascriptHostValueType.STRING, Set.of(),
                (context, arguments) -> {
                    CountDownLatch done = new CountDownLatch(1);
                    CompletableFuture.delayedExecutor(2100, TimeUnit.MILLISECONDS).execute(done::countDown);
                    assertTrue(done.await(5, TimeUnit.SECONDS));
                    context.requireActive();
                    return new JsonPrimitive("done");
                })), List.of(), List.of());
        assertEquals("done", fixture.success("native-wait", "return require('test_native:methods').wait();").getAsString());
        var failed = assertInstanceOf(ToolResult.Failure.class, fixture.invoke("loop",
                "require('test_native:methods').wait(); while (true) {}", false));
        assertEquals("javascript_timeout", failed.code());
    }

    @Test
    void cancellationMidNativeWaitRevokesEveryListenerAndUnwindsOnOpeningWorker() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        AtomicReference<Thread> worker = new AtomicReference<>();
        fixture.register(OWNER, List.of(method("wait", List.of(), JavascriptHostValueType.NULL, Set.of(),
                (context, arguments) -> {
                    context.cancellation().onCancel(() -> { throw new Error("secret-token"); });
                    context.cancellation().onCancel(release::countDown);
                    entered.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    context.requireActive();
                    return JsonNull.INSTANCE;
                })), List.of(participant("test:scope", context -> {
                    worker.set(Thread.currentThread());
                    return () -> { assertSame(worker.get(), Thread.currentThread()); closes.incrementAndGet(); };
                })), List.of());
        CancellationSignal cancellation = new CancellationSignal();
        cancellation.onCancel(() -> { throw new AssertionError("secret-token"); });
        var future = fixture.tool.invokeAsync(ToolInvocationContext.developmentConsole("cancel"),
                new RunJavascriptTool.Input("return require('test_native:methods').wait();", List.of()), cancellation);
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> { cancellation.cancel(); });
        assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertEquals(1, closes.get());
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void directCallsRequireTheOpeningWorkerAndRejectClosedScopes() throws Exception {
        Fixture fixture = echoFixture();
        var scope = fixture.registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("worker"), new CancellationSignal());
        scope.open(ignored -> {});
        var result = CompletableFuture.supplyAsync(() -> assertThrows(JavascriptExecutionException.class,
                () -> scope.invokeHostMethod(BINDING, "echo", List.of(new JsonPrimitive(1)))));
        assertEquals("javascript_host_access_denied", result.get(5, TimeUnit.SECONDS).code());
        scope.close();
        assertThrows(JavascriptExecutionException.class,
                () -> scope.invokeHostMethod(BINDING, "echo", List.of(new JsonPrimitive(1))));
    }

    private static Fixture echoFixture() {
        Fixture fixture = new Fixture();
        fixture.register(OWNER, List.of(method("echo", List.of(JavascriptHostValueType.JSON),
                JavascriptHostValueType.JSON, Set.of(), (context, arguments) -> arguments.getFirst())),
                List.of(), List.of());
        return fixture;
    }

    private static Fixture writeFixture() {
        Fixture fixture = new Fixture();
        fixture.register(OWNER, List.of(writeMethod()), List.of(), List.of(capability()));
        return fixture;
    }

    private static ExtensionCapability capability() { return new ExtensionCapability(WRITE, "World operations", "May change the active world."); }
    private static JavascriptHostMethod writeMethod() {
        return method("write", List.of(), JavascriptHostValueType.STRING, Set.of(WRITE),
                (context, arguments) -> { context.requireCapability(WRITE); return new JsonPrimitive("written"); });
    }
    private static JavascriptHostMethod method(String name, List<JavascriptHostValueType> arguments,
            JavascriptHostValueType result, Set<String> capabilities, JavascriptHostMethod.Invoker callback) {
        return new JavascriptHostMethod(name, arguments, result, capabilities, callback);
    }
    private static JavascriptInvocationParticipant participant(String id, Opener callback) {
        return new JavascriptInvocationParticipant() {
            public String id() { return id; }
            public AutoCloseable open(JavascriptInvocationContext context) throws Exception { return callback.open(context); }
        };
    }
    private interface Opener { AutoCloseable open(JavascriptInvocationContext context) throws Exception; }
    private static OpenAllayExtensionDescriptor descriptorFor(String id) {
        return new OpenAllayExtensionDescriptor(id, id, "1.0", "test", "test", Set.of("fabric"),
                "[26.2,26.3)", "[0.2.2,0.3)", "test");
    }
    private static final class Fixture {
        final JavascriptModuleCatalog modules = new JavascriptModuleCatalog(Map.of());
        final OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.2"), new JavascriptDataModuleRegistry(),
                modules, new SkillRepository(new SkillParser(), List.of(RunJavascriptTool.ID)), Set.of());
        final RunJavascriptTool tool;
        Fixture() { this(new RhinoJavascriptRuntime()); }
        Fixture(RhinoJavascriptRuntime runtime) {
            tool = new RunJavascriptTool(runtime, MinecraftAgentHostGraph::new,
                    new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter(),
                    new CommandCapabilityRuntime(), new WorldObservationRuntime(), registry);
        }
        OpenAllayExtensionRegistry.Registration register(String owner, List<JavascriptHostMethod> methods,
                List<JavascriptInvocationParticipant> participants, List<ExtensionCapability> capabilities) {
            return registry.register(new OpenAllayExtension() {
                public OpenAllayExtensionDescriptor descriptor() { return descriptorFor(owner); }
                public OpenAllayExtensionContribution contribution() {
                    return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of(),
                            participants, List.of(new JavascriptHostBinding(BINDING, methods)), capabilities);
                }
            });
        }
        ToolResult<RunJavascriptTool.Output> invoke(String id, String source, boolean unrestricted) {
            ToolInvocationContext base = ToolInvocationContext.developmentConsole(id);
            var context = new ToolInvocationContext(id, base.capturedAt(), base.caller(), base.player(),
                    base.registries(), base.recipes(), base.observableGameState(), base.metrics(), unrestricted);
            return tool.invokeAsync(context, new RunJavascriptTool.Input(source, List.of()), new CancellationSignal()).join();
        }
        JsonElement success(String id, String source) {
            var result = invoke(id, source, false);
            assertInstanceOf(ToolResult.Success.class, result, result.toString());
            return ((ToolResult.Success<RunJavascriptTool.Output>) result).value().preview();
        }
        String failure(String id, boolean unrestricted) {
            return assertInstanceOf(ToolResult.Failure.class,
                    invoke(id, "return require('test_native:methods').write();", unrestricted)).code();
        }
    }
}
