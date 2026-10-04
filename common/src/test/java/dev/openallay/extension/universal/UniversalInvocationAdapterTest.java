package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.api.extension.*;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.extension.ExtensionCapabilityPolicy;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.JavascriptExecutionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UniversalInvocationAdapterTest {
    @Test void exposesDetachedConsoleIdentityAndOwnFrozenGrantsOnly() throws Exception {
        var registry = UniversalExtensionFixtures.registry();
        AtomicReference<ExtensionInvocation> first = new AtomicReference<>();
        AtomicReference<ExtensionInvocation> second = new AtomicReference<>();
        registry.register(UniversalExtensionFixtures.bridge("test:first", contribution("test:a", first, "test:read")));
        registry.register(UniversalExtensionFixtures.bridge("test:second", contribution("test:b", second, "test:other")));
        registry.replaceCapabilityPolicy(new ExtensionCapabilityPolicy(Map.of("test:first", Set.of("test:read"))));
        registry.freezeJavascriptRequest("frozen", true);
        registry.replaceCapabilityPolicy(ExtensionCapabilityPolicy.defaults());
        var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("frozen"),
                new CancellationSignal());
        scope.open(ignored -> {});
        assertEquals("test:first", first.get().extensionId());
        assertEquals("frozen", first.get().correlationId());
        assertEquals(ExtensionInvocation.CallerKind.CONSOLE, first.get().callerKind());
        assertNull(first.get().callerUuid());
        assertTrue(first.get().playerDimension().isEmpty());
        assertTrue(first.get().hasCapability("test:read"));
        assertFalse(first.get().hasCapability("test:other"));
        assertFalse(second.get().hasCapability("test:read"));
        assertFalse(second.get().hasCapability("test:other"));
        assertThrows(JavascriptExecutionException.class, () -> second.get().requireCapability("test:read"));
        scope.complete();
        scope.close();
        assertTrue(first.get().completedSuccessfully());
        assertTrue(first.get().isCancelled());
        assertThrows(JavascriptExecutionException.class, first.get()::requireActive);
    }
    @Test void mapsActualEvidenceAndRevokesSinkAndCallbacksWithoutForeignCleanupLeak() {
        var registry = UniversalExtensionFixtures.registry();
        AtomicReference<ExtensionInvocation> context = new AtomicReference<>();
        registry.register(UniversalExtensionFixtures.bridge("test:first", contribution("test:a", context, "test:read")));
        CancellationSignal cancellation = new CancellationSignal();
        var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("cancel"), cancellation);
        var evidence = new ArrayList<dev.openallay.context.EvidenceMetadata>();
        scope.open(evidence::add);
        ExtensionEvidence source = new ExtensionEvidence(ExtensionEvidence.Authority.INTEGRATION_API,
                ExtensionEvidence.Completeness.PARTIAL, Instant.EPOCH, "test:capture", "test:actual_capture",
                "26.2", "fabric", Map.of("test:detail", "value"));
        context.get().recordEvidence(source);
        assertEquals(1, evidence.size());
        assertEquals(source.sourceId(), evidence.getFirst().sourceId());
        assertEquals(source.provenance(), evidence.getFirst().provenance());
        assertEquals(source.capturedAt(), evidence.getFirst().capturedAt());
        assertEquals(source.details(), evidence.getFirst().details());
        AtomicInteger callbacks = new AtomicInteger();
        context.get().onCancel(() -> { throw new AssertionError("SECRET"); });
        context.get().onCancel(callbacks::incrementAndGet);
        cancellation.cancel();
        assertEquals(1, callbacks.get());
        context.get().onCancel(callbacks::incrementAndGet);
        assertEquals(2, callbacks.get());
        assertThrows(ModelClientException.class, context.get()::requireActive);
        assertThrows(ModelClientException.class, () -> context.get().recordEvidence(source));
        assertFalse(context.get().completedSuccessfully());
        scope.close();
        assertEquals(1, evidence.size());
    }
    @Test void participantHooksCloseInReverseOrderOnOpeningWorkerAfterRevocation() {
        var registry = UniversalExtensionFixtures.registry();
        var events = new ArrayList<String>();
        Thread owner = Thread.currentThread();
        List<JavascriptInvocationParticipant> participants = new ArrayList<>();
        for (String id : List.of("test:a", "test:b")) {
            participants.add(new JavascriptInvocationParticipant() {
                public String id() { return id; }
                public AutoCloseable open(ExtensionInvocation context) {
                    assertSame(owner, Thread.currentThread());
                    events.add(id + "+");
                    return () -> {
                        assertSame(owner, Thread.currentThread());
                        assertTrue(context.isCancelled());
                        assertThrows(JavascriptExecutionException.class, context::requireActive);
                        events.add(id + "-");
                    };
                }
            });
        }
        registry.register(UniversalExtensionFixtures.bridge("test:lifecycle", new ExtensionContribution(
                List.of(), List.of(), List.of(), participants, List.of(), List.of())));
        var scope = registry.prepareJavascriptInvocation(ToolInvocationContext.developmentConsole("hooks"),
                new CancellationSignal());
        scope.open(ignored -> fail("No fabricated evidence"));
        scope.complete();
        scope.close();
        assertEquals(List.of("test:a+", "test:b+", "test:b-", "test:a-"), events);
        assertEquals(0, registry.activeJavascriptInvocations());
    }
    private static ExtensionContribution contribution(String id, AtomicReference<ExtensionInvocation> context,
            String capability) {
        JavascriptInvocationParticipant participant = new JavascriptInvocationParticipant() {
            public String id() { return id; }
            public AutoCloseable open(ExtensionInvocation invocation) { context.set(invocation); return () -> {}; }
        };
        return new ExtensionContribution(List.of(), List.of(), List.of(), List.of(participant), List.of(),
                List.of(new ExtensionCapability(capability, "Capability", "Description")));
    }
}
