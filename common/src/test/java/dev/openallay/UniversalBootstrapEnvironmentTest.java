package dev.openallay;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.api.extension.*;
import dev.openallay.context.CallerKind;
import dev.openallay.context.CallerSnapshot;
import dev.openallay.context.ContextMetrics;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.OpenAllayExtensionState;
import dev.openallay.extension.universal.UniversalExtensionBridge;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.PlatformService;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class UniversalBootstrapEnvironmentTest {
    @ParameterizedTest
    @CsvSource({"Fabric,fabric", "NeoForge,neoforge"})
    void actualBootstrapFactsAdmitCurrentSdkAndRunDetachedHostInRestrictedPlayerScope(
            String displayName, String expectedLoader) throws Exception {
        AtomicInteger worldOpens = new AtomicInteger();
        MinecraftWorldAccess worldAccess = invocation -> {
            worldOpens.incrementAndGet();
            throw new AssertionError("Detached declarations and host calls must not capture a world");
        };
        var environment = OpenAllayBootstrap.universalEnvironment(platform(displayName, worldAccess));
        var host = UniversalExtensionBridge.host(environment, worldAccess);
        var registry = registry(environment);
        List<String> lifecycle = new ArrayList<>();
        AtomicReference<ExtensionInvocation> participantInvocation = new AtomicReference<>();
        UUID caller = UUID.fromString("7e115620-f3a9-4b16-87ab-9c70ddc6ca42");
        Instant capturedAt = Instant.parse("2026-10-04T16:25:37Z");
        var invocation = new ToolInvocationContext("restricted-bootstrap-" + expectedLoader, capturedAt,
                new CallerSnapshot(CallerKind.PLAYER, caller, "Ordinary player", false),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                new ContextMetrics(0, 0, 0, 0, 0), false);
        var expectedEvidence = new EvidenceMetadata(DataAuthority.DETERMINISTIC_TEST, DataCompleteness.COMPLETE,
                capturedAt, "test:detached_echo", "test:bootstrap_extension", "26.2", expectedLoader,
                Map.of("test:operation", "echo"));
        var sdkEvidence = new ExtensionEvidence(ExtensionEvidence.Authority.DETERMINISTIC_TEST,
                ExtensionEvidence.Completeness.COMPLETE, capturedAt, "test:detached_echo",
                "test:bootstrap_extension", "26.2", expectedLoader, Map.of("test:operation", "echo"));
        var input = dev.openallay.json.JsonTrees.parse("{\"message\":\"detached\",\"values\":[true,7,null]}");
        String argumentJson = input.toString();
        var participant = new JavascriptInvocationParticipant() {
            @Override public String id() { return "test:bootstrap_participant"; }
            @Override public AutoCloseable open(ExtensionInvocation context) {
                context.requireActive();
                participantInvocation.set(context);
                lifecycle.add("open");
                context.onCancel(() -> lifecycle.add("revoke"));
                return () -> {
                    lifecycle.add("close");
                    assertTrue(context.completedSuccessfully());
                    assertTrue(context.isCancelled());
                };
            }
        };
        var method = new JavascriptHostMethod("echo", List.of(JavascriptHostValueType.JSON),
                JavascriptHostValueType.JSON, (context, arguments) -> {
                    context.requireActive();
                    assertSame(participantInvocation.get(), context);
                    assertEquals("test:bootstrap_extension", context.extensionId());
                    assertEquals(ExtensionInvocation.CallerKind.PLAYER, context.callerKind());
                    assertEquals(caller, context.callerUuid());
                    assertEquals(invocation.correlationId(), context.correlationId());
                    assertEquals(capturedAt, context.capturedAt());
                    assertEquals(List.of(argumentJson), arguments);
                    assertThrows(UnsupportedOperationException.class, () -> arguments.add("null"));
                    lifecycle.add("invoke");
                    context.recordEvidence(sdkEvidence);
                    return arguments.getFirst();
                });
        var contribution = new ExtensionContribution(List.of(), List.of(), List.of(), List.of(participant),
                List.of(new JavascriptHostBinding("test:bootstrap_binding", List.of(method))));
        var descriptor = descriptor("test:bootstrap_extension", expectedLoader, "[0.4.0]");
        var extension = new OpenAllayExtension() {
            @Override public ExtensionDescriptor descriptor() { return descriptor; }
            @Override public ExtensionContribution contribution(ExtensionHost actualHost) {
                assertSame(host, actualHost);
                lifecycle.add("contribute");
                return contribution;
            }
        };
        var beforeAdmission = registry.snapshot();
        var retiredDescriptor = descriptor("test:retired_sdk", expectedLoader, "[0.3,0.4)");
        var retired = new OpenAllayExtension() {
            @Override public ExtensionDescriptor descriptor() { return retiredDescriptor; }
            @Override public ExtensionContribution contribution(ExtensionHost ignored) {
                lifecycle.add("retired-contribute");
                throw new AssertionError("A removed SDK must not contribute");
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new UniversalExtensionBridge(retired, retiredDescriptor, host)));
        assertEquals(beforeAdmission, registry.snapshot());
        assertTrue(lifecycle.isEmpty());

        var registration = registry.register(new UniversalExtensionBridge(extension, descriptor, host));
        assertEquals(OpenAllayExtensionState.ACTIVE, registration.state(), registration.toString());
        assertEquals(List.of("contribute"), lifecycle);
        assertEquals(0, worldOpens.get(), "Registration must leave native world capture lazy");
        assertEquals(List.of("test:bootstrap_binding"),
                registry.snapshot().extensions().getFirst().hostBindings());
        assertEquals(1, registry.snapshot().generation());
        List<EvidenceMetadata> evidence = new ArrayList<>();
        var scope = registry.prepareJavascriptInvocation(invocation, new CancellationSignal());
        try (scope) {
            assertEquals(List.of("contribute"), lifecycle, "Preparing work must not open its participants");
            scope.open(evidence::add);
            assertTrue(evidence.isEmpty(), "Admission and opening are not detached-operation evidence");
            var returned = scope.invokeHostMethod("test:bootstrap_binding", "echo", List.of(input));
            assertEquals(input, returned);
            input.getAsJsonObject().addProperty("message", "changed by caller");
            assertEquals("detached", returned.getAsJsonObject().get("message").getAsString());
            assertEquals(List.of(expectedEvidence), evidence);
            assertEquals(List.of("contribute", "open", "invoke"), lifecycle);
            scope.complete();
        }
        assertEquals(List.of("contribute", "open", "invoke", "revoke", "close"), lifecycle);
        assertEquals(0, registry.activeJavascriptInvocations());
        assertThrows(JavascriptExecutionException.class, participantInvocation.get()::requireActive);
        assertThrows(JavascriptExecutionException.class, () -> participantInvocation.get().recordEvidence(sdkEvidence));
        assertThrows(JavascriptExecutionException.class,
                () -> scope.invokeHostMethod("test:bootstrap_binding", "echo", List.of(input)));
        scope.close();
        assertEquals(List.of("contribute", "open", "invoke", "revoke", "close"), lifecycle);
        assertEquals(List.of(expectedEvidence), evidence);
        assertEquals(0, worldOpens.get());
        assertTrue(registry.shutdown().isDone(), "Closed scopes must release framework shutdown");
    }

    @ParameterizedTest
    @CsvSource({"Fabric,fabric", "NeoForge,neoforge"})
    void currentBootstrapStillAdmitsTheSeparateLegacyCoreExtensionAbi(String displayName, String expectedLoader) {
        MinecraftWorldAccess unusedWorld = invocation -> {
            throw new AssertionError("Legacy declaration admission must not capture a world");
        };
        var environment = OpenAllayBootstrap.universalEnvironment(platform(displayName, unusedWorld));
        var registry = registry(environment);
        AtomicInteger contributions = new AtomicInteger();
        var descriptor = new dev.openallay.extension.OpenAllayExtensionDescriptor(
                "test:legacy_extension", "Legacy Test", "1.0.0", "Test", "Legacy core ABI",
                Set.of(expectedLoader), "[26.2]", "[0.2.2]", "test:legacy_source");
        var registration = registry.register(new dev.openallay.extension.OpenAllayExtension() {
            @Override public dev.openallay.extension.OpenAllayExtensionDescriptor descriptor() { return descriptor; }
            @Override public dev.openallay.extension.OpenAllayExtensionContribution contribution() {
                contributions.incrementAndGet();
                return dev.openallay.extension.OpenAllayExtensionContribution.empty();
            }
        });
        assertEquals(OpenAllayExtensionState.ACTIVE, registration.state(), registration.toString());
        assertEquals(1, contributions.get());
        assertEquals(descriptor, registry.snapshot().extensions().getFirst().descriptor());
    }

    private static ExtensionDescriptor descriptor(String id, String loader, String apiRange) {
        return new ExtensionDescriptor(id, "Bootstrap Test", "1.0.0", "Test", "Detached bootstrap test",
                "test:bootstrap_source", new SupportDeclaration(
                        List.of(new SupportTarget(loader, "[26.2]", "[0.4.1]", apiRange)), 8,
                        Set.of("openallay:javascript_host", "minecraft:world-access"), Set.of()),
                ExtensionRequirements.EMPTY);
    }

    private static OpenAllayExtensionRegistry registry(ExtensionEnvironment environment) {
        return new OpenAllayExtensionRegistry(new OpenAllayExtensionEnvironment(environment.loader(),
                environment.minecraftVersion(), OpenAllayConstants.EXTENSION_API_VERSION,
                environment.openAllayApiVersions()), new JavascriptDataModuleRegistry(),
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of("openallay:run_javascript")), Set.of());
    }

    private static PlatformService platform(String displayName, MinecraftWorldAccess worldAccess) {
        return new PlatformService() {
            @Override public String platformName() { return displayName; }
            @Override public String gameVersion() { return "26.2"; }
            @Override public String productVersion() { return "0.4.1"; }
            @Override public Optional<MinecraftWorldAccess> minecraftWorldAccess() { return Optional.of(worldAccess); }
            @Override public boolean isModLoaded(String id) { return false; }
            @Override public boolean isDevelopmentEnvironment() { return true; }
        };
    }
}
