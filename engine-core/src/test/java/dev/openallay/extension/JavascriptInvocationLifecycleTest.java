package dev.openallay.extension;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.SourceObservation;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public final class JavascriptInvocationLifecycleTest {
    @Test
    void contributionRetainsFormalLegacyConstructorsAndOnlyCurrentDeclarationFields() throws Exception {
        assertNotNull(OpenAllayExtensionContribution.class.getConstructor(
                List.class, List.class, List.class, List.class));
        assertNotNull(OpenAllayExtensionContribution.class.getConstructor(
                List.class, List.class, List.class, List.class, List.class));
        assertNotNull(OpenAllayExtensionContribution.class.getConstructor(
                List.class, List.class, List.class, List.class, List.class, List.class));
        assertEquals(Set.of(4, 5, 6), java.util.Arrays.stream(
                OpenAllayExtensionContribution.class.getConstructors())
                .map(java.lang.reflect.Constructor::getParameterCount)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of("dataModules", "javascriptModules", "skills", "resultViews",
                "javascriptInvocationParticipants", "hostBindings"), java.util.Arrays.stream(
                OpenAllayExtensionContribution.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList());
        assertTrue(new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of())
                .hostBindings().isEmpty());
        assertTrue(new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of(), List.of())
                .hostBindings().isEmpty());
    }

    @Test
    void validatesNamespacedIdsAndDuplicatesBeforePublishingOtherContributions() {
        Fixture fixture = new Fixture();
        for (String id : List.of("unqualified", "BAD:identifier")) {
            var result = fixture.register("test:invalid", List.of(participant(id, ignored -> () -> {})),
                    List.of(new JavascriptModuleSource("test:unpublished", "module.exports = 1;")));
            assertEquals("extension_registration_failed", result.diagnostic());
            assertEquals(0, fixture.registry.snapshot().generation());
            assertTrue(fixture.modules.ids().isEmpty());
        }
        assertEquals(OpenAllayExtensionState.ACTIVE,
                fixture.register("test:first", List.of(participant("test:shared", ignored -> () -> {}))).state());
        var rejected = fixture.register("test:second", List.of(participant("test:shared", ignored -> () -> {})),
                List.of(new JavascriptModuleSource("test:unpublished", "module.exports = 1;")));
        assertEquals("duplicate_contribution_id", rejected.diagnostic());
        assertEquals(1, fixture.registry.snapshot().generation());
        assertTrue(fixture.modules.ids().isEmpty());
        assertEquals(List.of("test:shared"), fixture.registry.javascriptInvocationParticipants().stream()
                .map(JavascriptInvocationParticipant::id).toList());
        assertTrue(new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of())
                .javascriptInvocationParticipants().isEmpty());
    }

    @Test
    void capturesForeignParticipantIdOnceBeforePublication() {
        Fixture fixture = new Fixture();
        AtomicInteger reads = new AtomicInteger();
        fixture.register("test:extension", List.of(new JavascriptInvocationParticipant() {
            public String id() {
                if (reads.incrementAndGet() != 1) throw new IllegalStateException("mutable id");
                return "test:stable";
            }
            public AutoCloseable open(JavascriptInvocationContext context) { return () -> {}; }
        }));
        assertEquals(List.of("test:stable"), fixture.registry.javascriptInvocationParticipants().stream()
                .map(JavascriptInvocationParticipant::id).toList());
        assertEquals(1, reads.get());
    }

    @Test
    void setupFailureClosesEarlierScopesInReverseOrderAndHidesForeignSecrets() {
        Fixture fixture = new Fixture();
        List<String> events = new ArrayList<>();
        AtomicReference<JavascriptInvocationContext> captured = new AtomicReference<>();
        fixture.register("test:extension", List.of(
                participant("test:a", context -> { captured.set(context); events.add("a+"); return () -> events.add("a-"); }),
                participant("test:b", context -> { events.add("b+"); return () -> { events.add("b-"); throw new Error("SECRET"); }; }),
                participant("test:c", context -> { events.add("c+"); throw new IllegalStateException("SECRET"); })));
        var failure = assertInstanceOf(ToolResult.Failure.class, fixture.invoke("setup", "return 1;"));
        assertEquals("javascript_participant_setup_failed", failure.code());
        assertFalse(failure.message().contains("SECRET"));
        assertEquals(List.of("a+", "b+", "c+", "b-", "a-"), events);
        assertTrue(captured.get().cancellation().isCancelled());
        assertFalse(captured.get().completedSuccessfully());
        assertThrows(JavascriptExecutionException.class, captured.get()::requireActive);
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void closesOnSuccessAndScriptErrorOnOpeningWorkerAndRevokesBeforeCleanup() {
        Fixture fixture = new Fixture();
        List<String> events = new ArrayList<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        List<Boolean> completions = new ArrayList<>();
        fixture.register("test:extension", List.of(
                participant("test:a", context -> {
                    worker.set(Thread.currentThread());
                    events.add("a+");
                    return () -> {
                        assertSame(worker.get(), Thread.currentThread());
                        completions.add(context.completedSuccessfully());
                        assertTrue(context.cancellation().isCancelled());
                        assertThrows(JavascriptExecutionException.class, context::requireActive);
                        assertThrows(JavascriptExecutionException.class, () -> context.recordEvidence(evidence("late")));
                        events.add("a-");
                    };
                }),
                participant("test:b", context -> { events.add("b+"); return () -> events.add("b-"); })));
        assertInstanceOf(ToolResult.Success.class, fixture.invoke("success", "return mc.caller.displayName;"));
        assertEquals(List.of("a+", "b+", "b-", "a-"), events);
        events.clear();
        assertInstanceOf(ToolResult.Failure.class, fixture.invoke("error", "throw new Error('failed');"));
        assertEquals(List.of("a+", "b+", "b-", "a-"), events);
        assertEquals(List.of(true, false), completions);
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void unexpectedHostErrorStillSettlesFutureAndReleasesAdmittedScope() throws Exception {
        Fixture fixture = new Fixture();
        RunJavascriptTool broken = new RunJavascriptTool(new RhinoJavascriptRuntime(),
                context -> { throw new AssertionError("Native capture failed: token=secret-token"); },
                new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter(),
                new CommandCapabilityRuntime(), new WorldObservationRuntime(), fixture.registry);
        var result = broken.invokeAsync(ToolInvocationContext.developmentConsole("host-error"),
                new RunJavascriptTool.Input("return 1;", List.of()), new CancellationSignal());
        var failure = assertInstanceOf(ToolResult.Failure.class, result.get(5, TimeUnit.SECONDS));
        assertEquals("javascript_failure", failure.code());
        assertTrue(failure.message().startsWith("AssertionError:"));
        assertTrue(failure.message().contains("Native capture failed: token=secret-token"));
        assertFalse(failure.message().contains("[REDACTED]"));
        assertFalse(failure.message().contains("JavascriptInvocationLifecycleTest.java"));
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void nullAndErrorHooksFailClosedWithoutLeakingScopesOrExceptionMessages() {
        for (boolean returnNull : List.of(true, false)) {
            Fixture fixture = new Fixture();
            AtomicInteger closed = new AtomicInteger();
            fixture.register("test:extension", List.of(
                    participant("test:a", context -> closed::incrementAndGet),
                    participant("test:b", context -> {
                        if (returnNull) return null;
                        throw new AssertionError("token=SECRET");
                    })));
            var failure = assertInstanceOf(ToolResult.Failure.class, fixture.invoke("invalid-hook", "return 1;"));
            assertEquals("javascript_participant_setup_failed", failure.code());
            assertFalse(failure.message().contains("SECRET"));
            assertEquals(1, closed.get());
            assertEquals(0, fixture.registry.activeJavascriptInvocations());
        }
    }

    @Test
    void cleanupFailureIsStableAndStillClosesEveryEarlierScope() {
        Fixture fixture = new Fixture();
        AtomicInteger closed = new AtomicInteger();
        fixture.register("test:extension", List.of(
                participant("test:a", context -> closed::incrementAndGet),
                participant("test:b", context -> () -> { throw new IllegalStateException("token=SECRET"); })));
        var failure = assertInstanceOf(ToolResult.Failure.class, fixture.invoke("cleanup", "return mc.caller.displayName;"));
        assertEquals("javascript_participant_cleanup_failed", failure.code());
        assertFalse(failure.message().contains("SECRET"));
        assertEquals(1, closed.get());
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void registrationAloneAddsNoEvidenceAndFrozenAuthorityIsUnchanged() {
        Fixture fixture = new Fixture();
        AtomicReference<JavascriptInvocationContext> captured = new AtomicReference<>();
        fixture.register("test:extension", List.of(participant("test:observer", context -> {
            captured.set(context);
            assertFalse(context.invocation().unrestrictedJavascript());
            return () -> {};
        })));
        @SuppressWarnings("unchecked") var success = (ToolResult.Success<RunJavascriptTool.Output>)
                assertInstanceOf(ToolResult.Success.class, fixture.invoke("no-evidence", "return 42;"));
        assertEquals(42, success.value().preview().getAsInt());
        assertTrue(success.value().sources().isEmpty());
        assertTrue(captured.get().completedSuccessfully());
        assertFalse(captured.get().invocation().unrestrictedJavascript());
    }

    @Test
    void trustedCompletedCaptureEvidenceIsIsolatedAcrossConcurrentInvocations() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch opened = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<JavascriptInvocationContext> contexts = new java.util.concurrent.CopyOnWriteArrayList<>();
        fixture.register("test:extension", List.of(participant("test:capture", context -> {
            contexts.add(context);
            opened.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            context.recordEvidence(evidence(context.invocation().correlationId()));
            return () -> {};
        })));
        var first = fixture.invokeAsync("first", "return 1;", new CancellationSignal());
        var second = fixture.invokeAsync("second", "return 2;", new CancellationSignal());
        assertTrue(opened.await(5, TimeUnit.SECONDS));
        release.countDown();
        for (var item : Map.of("first", first, "second", second).entrySet()) {
            @SuppressWarnings("unchecked") var success = (ToolResult.Success<RunJavascriptTool.Output>)
                    assertInstanceOf(ToolResult.Success.class, item.getValue().get(5, TimeUnit.SECONDS));
            assertEquals(List.of(new SourceObservation(evidence(item.getKey()))), success.value().sources());
        }
        assertNotSame(contexts.get(0), contexts.get(1));
        contexts.forEach(context -> assertThrows(JavascriptExecutionException.class,
                () -> context.recordEvidence(evidence("late"))));
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    /** Test-only native facade: context is never installed into the Rhino host graph. */
    public static final class NativeFacade {
        private static final ThreadLocal<JavascriptInvocationContext> CURRENT = new ThreadLocal<>();

        public static String cancel() {
            CURRENT.get().cancellation().cancel();
            return "cancelled";
        }

        public static String capture() {
            JavascriptInvocationContext context = CURRENT.get();
            if (context == null) throw new IllegalStateException("No invocation binding");
            context.requireActive();
            String value = context.invocation().correlationId();
            context.recordEvidence(evidence(value));
            return value;
        }
    }

    @Test
    void unrestrictedNativeFacadePublishesOnlyActualCaptureAndClosesWorkerLocalBinding() {
        Fixture fixture = new Fixture();
        AtomicInteger closes = new AtomicInteger();
        List<Boolean> outcomes = new ArrayList<>();
        fixture.register("test:extension", List.of(participant("test:native", context -> {
            assertNull(NativeFacade.CURRENT.get());
            if (!context.invocation().unrestrictedJavascript()) return () -> {};
            NativeFacade.CURRENT.set(context);
            return () -> {
                outcomes.add(context.completedSuccessfully());
                NativeFacade.CURRENT.remove();
                closes.incrementAndGet();
            };
        })));
        ToolInvocationContext base = ToolInvocationContext.developmentConsole("native-capture");
        ToolInvocationContext authorized = new ToolInvocationContext(base.correlationId(), base.capturedAt(),
                base.caller(), base.player(), base.registries(), base.recipes(), base.observableGameState(),
                base.metrics(), true);
        @SuppressWarnings("unchecked") var captured = (ToolResult.Success<RunJavascriptTool.Output>)
                assertInstanceOf(ToolResult.Success.class, fixture.tool.invokeAsync(authorized,
                        new RunJavascriptTool.Input("return Java.type('dev.openallay.extension.JavascriptInvocationLifecycleTest$NativeFacade').capture();", List.of()),
                        new CancellationSignal()).join());
        assertEquals("native-capture", captured.value().preview().getAsString());
        assertEquals(List.of(new SourceObservation(evidence("native-capture"))), captured.value().sources());
        @SuppressWarnings("unchecked") var unused = (ToolResult.Success<RunJavascriptTool.Output>)
                assertInstanceOf(ToolResult.Success.class, fixture.tool.invokeAsync(authorized,
                        new RunJavascriptTool.Input("return 1;", List.of()), new CancellationSignal()).join());
        assertEquals(1, unused.value().preview().getAsInt());
        assertTrue(unused.value().sources().isEmpty());
        assertInstanceOf(ToolResult.Failure.class, fixture.tool.invokeAsync(authorized,
                new RunJavascriptTool.Input("Java.type('dev.openallay.extension.JavascriptInvocationLifecycleTest$NativeFacade').capture(); throw new Error('failed');", List.of()),
                new CancellationSignal()).join());
        assertThrows(java.util.concurrent.CompletionException.class, () -> fixture.tool.invokeAsync(authorized,
                new RunJavascriptTool.Input("return Java.type('dev.openallay.extension.JavascriptInvocationLifecycleTest$NativeFacade').cancel();", List.of()),
                new CancellationSignal()).join());
        assertEquals(List.of(true, true, false, false), outcomes);
        assertEquals(4, closes.get());
        assertThrows(IllegalStateException.class, NativeFacade::capture);
    }

    @Test
    void requestCloseRevokesScopesDuringOpenButCleanupStaysOnWorker() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<JavascriptInvocationContext> context = new AtomicReference<>();
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicInteger closed = new AtomicInteger();
        fixture.register("test:extension", List.of(participant("test:race", invocation -> {
            context.set(invocation);
            worker.set(Thread.currentThread());
            opened.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return () -> { assertSame(worker.get(), Thread.currentThread()); closed.incrementAndGet(); };
        })));
        var result = fixture.invokeAsync("close", "return mc.caller.displayName;", new CancellationSignal());
        assertTrue(opened.await(5, TimeUnit.SECONDS));
        fixture.tool.closeRequestScope("close");
        assertTrue(context.get().cancellation().isCancelled());
        assertFalse(context.get().completedSuccessfully());
        assertThrows(JavascriptExecutionException.class, context.get()::requireActive);
        assertThrows(JavascriptExecutionException.class, () -> context.get().recordEvidence(evidence("late")));
        assertEquals(0, closed.get());
        release.countDown();
        assertInstanceOf(ToolResult.Failure.class, result.get(5, TimeUnit.SECONDS));
        assertEquals(1, closed.get());
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void requestCancellationRevokesSinkAndClosedLifetimeCannotOpenLateCallback() {
        Fixture fixture = new Fixture();
        AtomicInteger opens = new AtomicInteger();
        fixture.register("test:extension", List.of(participant("test:cancel", context -> {
            opens.incrementAndGet(); return () -> {};
        })));
        CancellationSignal cancellation = new CancellationSignal();
        var pending = fixture.registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("cancel"), cancellation);
        cancellation.cancel();
        fixture.registry.closeJavascriptRequest("cancel");
        assertTrue(pending.cancellation().isCancelled());
        assertThrows(ModelClientException.class, () -> pending.open(ignored -> {}));
        assertThrows(ModelClientException.class, pending::complete);
        pending.close();
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> fixture.invokeAsync("cancel", "return 1;", cancellation).join());
        assertEquals(0, opens.get());
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void cancellingOneConcurrentExecutionDoesNotCancelAnother() {
        Fixture fixture = new Fixture();
        CancellationSignal firstCancellation = new CancellationSignal();
        CancellationSignal secondCancellation = new CancellationSignal();
        var first = fixture.registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("request"), firstCancellation);
        var second = fixture.registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("request"), secondCancellation);
        first.open(ignored -> {});
        second.open(ignored -> {});
        firstCancellation.cancel();
        assertThrows(ModelClientException.class, first::requireActive);
        assertDoesNotThrow(second::requireActive);
        first.close();
        second.close();
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
    }

    @Test
    void frameworkShutdownWaitsForRequestRemovedWorkerToUnwindAndRejectsNewScopes() {
        Fixture fixture = new Fixture();
        AtomicInteger closes = new AtomicInteger();
        fixture.register("test:shutdown", List.of(participant("test:shutdown_hook", context -> {
            return closes::incrementAndGet;
        })));
        var scope = fixture.registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("removed-before-shutdown"), new CancellationSignal());
        scope.open(ignored -> {});
        fixture.registry.closeJavascriptRequest("removed-before-shutdown");
        assertEquals(0, fixture.registry.activeJavascriptInvocations());
        var shutdown = fixture.registry.shutdown();
        assertFalse(shutdown.isDone(), "Request-map removal is not a worker release receipt");
        assertEquals(0, closes.get());
        assertThrows(JavascriptExecutionException.class, () -> fixture.registry.prepareJavascriptInvocation(
                ToolInvocationContext.developmentConsole("late"), new CancellationSignal()));
        assertSame(shutdown, fixture.registry.shutdown());
        scope.close();
        assertEquals(1, closes.get());
        assertTrue(shutdown.isDone());
    }

    @Test
    void completionCallbackCancellationDoesNotInterruptSettledWorkerOrAdmitReuse() throws Exception {
        for (String source : List.of("return 1;", "throw new Error('failed');")) {
            Fixture fixture = new Fixture();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Thread> worker = new AtomicReference<>();
            fixture.register("test:completion", List.of(participant("test:gate", context -> {
                worker.set(Thread.currentThread());
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return () -> {};
            })));
            CancellationSignal cancellation = new CancellationSignal();
            var pending = fixture.invokeAsync("completion-cancel", source, cancellation);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var continuation = pending.handle((result, failure) -> {
                    assertSame(worker.get(), Thread.currentThread());
                    assertFalse(Thread.currentThread().isInterrupted());
                    assertTrue(cancellation.cancel());
                    assertFalse(Thread.currentThread().isInterrupted(), "Terminal callbacks are not active Tool work");
                    assertTrue(cancellation.isCancelled());
                    var denied = assertThrows(java.util.concurrent.CompletionException.class,
                            () -> fixture.invokeAsync("completion-cancel", "return 2;", cancellation).join());
                    assertEquals("agent_cancelled", assertInstanceOf(ModelClientException.class,
                            denied.getCause()).failure().code());
                    assertFalse(Thread.currentThread().isInterrupted());
                    return result;
                });
                release.countDown();
                assertNotNull(continuation.get(5, TimeUnit.SECONDS));
                worker.get().join(2000);
                assertFalse(worker.get().isInterrupted());
                assertEquals(0, fixture.registry.activeJavascriptInvocations());
            } finally {
                release.countDown();
                cancellation.cancel();
            }
        }
    }

    @Test
    void exceptionalCompletionCancellationDoesNotInterruptSettledWorker() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        RunJavascriptTool failing = new RunJavascriptTool(new RhinoJavascriptRuntime(), context -> {
            worker.set(Thread.currentThread());
            entered.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            throw new ModelClientException(new dev.openallay.model.ModelFailure(
                    "test_failure", "Test model failure", null));
        }, new AgentResultWorkspaceRegistry(), new JavascriptResultPresenter(),
                new CommandCapabilityRuntime(), new WorldObservationRuntime(), fixture.registry);
        CancellationSignal cancellation = new CancellationSignal();
        var pending = failing.invokeAsync(ToolInvocationContext.developmentConsole("exceptional-completion"),
                new RunJavascriptTool.Input("return 1;", List.of()), cancellation);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var continuation = pending.handle((result, failure) -> {
                assertSame(worker.get(), Thread.currentThread());
                assertInstanceOf(ModelClientException.class, failure);
                assertFalse(Thread.currentThread().isInterrupted());
                cancellation.cancel();
                assertFalse(Thread.currentThread().isInterrupted());
                return true;
            });
            release.countDown();
            assertTrue(continuation.get(5, TimeUnit.SECONDS));
            worker.get().join(2000);
            assertFalse(worker.get().isInterrupted());
            assertEquals(0, fixture.registry.activeJavascriptInvocations());
        } finally {
            release.countDown();
            cancellation.cancel();
        }
    }

    @Test
    void activeCancellationStillInterruptsBlockingWorkerAndRevokesAdmission() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fixture.register("test:active", List.of(participant("test:interrupt", context -> {
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException expected) {
                interrupted.countDown();
                throw expected;
            }
            return () -> {};
        })));
        CancellationSignal cancellation = new CancellationSignal();
        var pending = fixture.invokeAsync("active-interrupt", "return 1;", cancellation);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(cancellation.cancel());
            assertTrue(cancellation.isCancelled());
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            pending.handle((result, failure) -> null).get(5, TimeUnit.SECONDS);
            assertEquals(0, fixture.registry.activeJavascriptInvocations());
        } finally {
            release.countDown();
            cancellation.cancel();
        }
    }

    private static JavascriptInvocationParticipant participant(String id, Opener opener) {
        return new JavascriptInvocationParticipant() {
            public String id() { return id; }
            public AutoCloseable open(JavascriptInvocationContext context) throws Exception {
                return opener.open(context);
            }
        };
    }

    private interface Opener { AutoCloseable open(JavascriptInvocationContext context) throws Exception; }

    private static EvidenceMetadata evidence(String source) {
        return new EvidenceMetadata(DataAuthority.INTEGRATION_API, DataCompleteness.COMPLETE,
                Instant.EPOCH, "test:" + source, "test:completed_capture", "26.2", "fabric", Map.of());
    }

    private static final class Fixture {
        final JavascriptModuleCatalog modules = new JavascriptModuleCatalog(Map.of());
        final OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.1"),
                new JavascriptDataModuleRegistry(), modules,
                new SkillRepository(new SkillParser(), List.of(RunJavascriptTool.ID)), Set.of());
        final RunJavascriptTool tool = new RunJavascriptTool(new RhinoJavascriptRuntime(),
                MinecraftAgentHostGraph::new, new AgentResultWorkspaceRegistry(),
                new JavascriptResultPresenter(), new CommandCapabilityRuntime(),
                new WorldObservationRuntime(), registry);

        OpenAllayExtensionRegistry.Registration register(String id, List<JavascriptInvocationParticipant> participants) {
            return register(id, participants, List.of());
        }

        OpenAllayExtensionRegistry.Registration register(String id,
                List<JavascriptInvocationParticipant> participants, List<JavascriptModuleSource> modules) {
            return registry.register(new OpenAllayExtension() {
                public OpenAllayExtensionDescriptor descriptor() {
                    return new OpenAllayExtensionDescriptor(id, id, "1.0", "test", "test",
                            Set.of("fabric"), "[26.2,26.3)", "[0.2,0.3)", "test");
                }
                public OpenAllayExtensionContribution contribution() {
                    return new OpenAllayExtensionContribution(List.of(), modules, List.of(), List.of(), participants);
                }
            });
        }

        ToolResult<RunJavascriptTool.Output> invoke(String id, String source) {
            return invokeAsync(id, source, new CancellationSignal()).join();
        }

        CompletableFuture<ToolResult<RunJavascriptTool.Output>> invokeAsync(
                String id, String source, CancellationSignal cancellation) {
            return tool.invokeAsync(ToolInvocationContext.developmentConsole(id),
                    new RunJavascriptTool.Input(source, List.of()), cancellation);
        }
    }
}
