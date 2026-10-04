package dev.openallay.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.script.result.JavascriptSemanticKind;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class OpenAllayExtensionRegistryTest {
    @Test
    void publishesCompatibleContributionsInStableExtensionOrder() {
        Fixture fixture = new Fixture();

        OpenAllayExtensionRegistry.Registration second =
                fixture.registry.register(extension("test:zeta", "test:zeta_data", "test:zeta_js"));
        OpenAllayExtensionRegistry.Registration first =
                fixture.registry.register(extension("test:alpha", "test:alpha_data", "test:alpha_js"));

        assertEquals(OpenAllayExtensionState.ACTIVE, first.state());
        assertEquals(OpenAllayExtensionState.ACTIVE, second.state());
        assertEquals(
                List.of("test:alpha", "test:zeta"),
                fixture.registry.snapshot().extensions().stream()
                        .map(extension -> extension.descriptor().id())
                        .toList());
        assertEquals(
                Set.of("test:alpha_js", "test:zeta_js"),
                fixture.modules.ids());
        assertEquals(
                List.of("test:alpha_data", "test:zeta_data"),
                fixture.dataModules.descriptors().stream()
                        .map(JavascriptDataModuleRegistry.Descriptor::module)
                        .toList());
    }

    @Test
    void rejectsDuplicateExtensionAndContributionIdsWithoutChangingActiveGeneration() {
        Fixture fixture = new Fixture();
        fixture.registry.register(extension("test:first", "test:shared", "test:first_js"));
        long generation = fixture.registry.snapshot().generation();

        OpenAllayExtensionRegistry.Registration duplicateExtension =
                fixture.registry.register(extension("test:first", "test:other", "test:other_js"));
        OpenAllayExtensionRegistry.Registration duplicateContribution =
                fixture.registry.register(extension("test:second", "test:shared", "test:second_js"));

        assertEquals(OpenAllayExtensionState.UNAVAILABLE, duplicateExtension.state());
        assertEquals("duplicate_extension_id", duplicateExtension.diagnostic());
        assertEquals(OpenAllayExtensionState.UNAVAILABLE, duplicateContribution.state());
        assertEquals("duplicate_contribution_id", duplicateContribution.diagnostic());
        assertEquals(generation, fixture.registry.snapshot().generation());
        assertEquals(List.of("test:first"), fixture.registry.snapshot().extensions().stream()
                .map(extension -> extension.descriptor().id())
                .toList());
        assertFalse(fixture.modules.ids().contains("test:second_js"));
    }

    @Test
    void rejectsIncompatibleLoaderGameAndApiIndependently() {
        Fixture fixture = new Fixture();

        assertEquals(
                "incompatible_loader",
                fixture.registry.register(extension(
                                descriptor("test:loader", Set.of("neoforge"), "[26.2,26.3)", "[0.2,0.3)"),
                                OpenAllayExtensionContribution.empty()))
                        .diagnostic());
        assertEquals(
                "incompatible_game_version",
                fixture.registry.register(extension(
                                descriptor("test:game", Set.of("fabric"), "[1.21,1.22)", "[0.2,0.3)"),
                                OpenAllayExtensionContribution.empty()))
                        .diagnostic());
        assertEquals(
                "incompatible_openallay_api",
                fixture.registry.register(extension(
                                descriptor("test:api", Set.of("fabric"), "[26.2,26.3)", "[1.0,2.0)"),
                                OpenAllayExtensionContribution.empty()))
                        .diagnostic());
        assertTrue(fixture.registry.snapshot().extensions().isEmpty());
    }

    @Test
    void invalidLiveObjectSchemaRejectsOnlyCandidateAndCaptureFailureDegradesOnlyItsModule() {
        Fixture fixture = new Fixture();
        OpenAllayExtensionContribution invalid = new OpenAllayExtensionContribution(
                List.of(new TestModule("test:live", Object.class, false)),
                List.of(),
                List.of(),
                List.of());
        OpenAllayExtensionRegistry.Registration rejected =
                fixture.registry.register(extension(descriptor("test:bad"), invalid));
        fixture.registry.register(extension(
                descriptor("test:good"),
                new OpenAllayExtensionContribution(
                        List.of(
                                new TestModule("test:failing", DetachedValue.class, true),
                                new TestModule("test:healthy", DetachedValue.class, false)),
                        List.of(),
                        List.of(),
                        List.of())));

        JavascriptDataModuleRegistry.Snapshot snapshot =
                fixture.dataModules.capture(ToolInvocationContext.developmentConsole("extension-test"));

        assertEquals(OpenAllayExtensionState.UNAVAILABLE, rejected.state());
        assertEquals("extension_registration_failed", rejected.diagnostic());
        assertEquals(Set.of("test:healthy"), snapshot.values().keySet());
        assertEquals(List.of("test:failing"), snapshot.diagnostics().stream()
                .map(JavascriptDataModuleRegistry.Diagnostic::module)
                .toList());
    }

    @Test
    void rejectsIdsDuplicatedAcrossContributionKinds() {
        Fixture fixture = new Fixture();
        OpenAllayExtensionContribution contribution = new OpenAllayExtensionContribution(
                List.of(new TestModule("test:shared", DetachedValue.class, false)),
                List.of(new JavascriptModuleSource("test:shared", "module.exports = 1;")),
                List.of(),
                List.of(new JavascriptResultViewProvider.Declaration(
                        "test:view",
                        JavascriptSemanticKind.TABLE,
                        "A table view")));

        OpenAllayExtensionRegistry.Registration result =
                fixture.registry.register(extension(descriptor("test:duplicate"), contribution));

        assertEquals(OpenAllayExtensionState.UNAVAILABLE, result.state());
        assertEquals("duplicate_contribution_id", result.diagnostic());
        assertTrue(fixture.dataModules.descriptors().isEmpty());
    }

    @Test
    void rejectsIncompatibleCandidateBeforeInvokingItsContribution() {
        Fixture fixture = new Fixture();
        java.util.concurrent.atomic.AtomicInteger contributions = new java.util.concurrent.atomic.AtomicInteger();
        OpenAllayExtension incompatible = new OpenAllayExtension() {
            @Override public OpenAllayExtensionDescriptor descriptor() {
                return OpenAllayExtensionRegistryTest.descriptor(
                        "test:incompatible", Set.of("forge"), "1.12.2", "[0.2,0.3)");
            }
            @Override public OpenAllayExtensionContribution contribution() {
                contributions.incrementAndGet();
                throw new AssertionError("Incompatible candidate contribution must not run");
            }
        };
        var rejected = fixture.registry.register(incompatible);
        assertEquals(OpenAllayExtensionState.INCOMPATIBLE, rejected.state());
        assertEquals("incompatible_loader", rejected.diagnostic());
        assertEquals(0, contributions.get());
        assertTrue(fixture.registry.snapshot().extensions().isEmpty());
    }

    @Test
    void rejectsDuplicateCandidateBeforeInvokingItsContribution() {
        Fixture fixture = new Fixture();
        fixture.registry.register(extension("test:existing", "test:existing_data", "test:existing_js"));
        long generation = fixture.registry.snapshot().generation();
        java.util.concurrent.atomic.AtomicInteger contributions = new java.util.concurrent.atomic.AtomicInteger();
        OpenAllayExtension duplicate = new OpenAllayExtension() {
            @Override public OpenAllayExtensionDescriptor descriptor() {
                return OpenAllayExtensionRegistryTest.descriptor("test:existing");
            }
            @Override public OpenAllayExtensionContribution contribution() {
                contributions.incrementAndGet();
                throw new AssertionError("Duplicate candidate contribution must not run");
            }
        };
        var rejected = fixture.registry.register(duplicate);
        assertEquals(OpenAllayExtensionState.UNAVAILABLE, rejected.state());
        assertEquals("duplicate_extension_id", rejected.diagnostic());
        assertEquals(0, contributions.get());
        assertEquals(generation, fixture.registry.snapshot().generation());
    }

    private static OpenAllayExtension extension(
            String extensionId, String dataModuleId, String javascriptModuleId) {
        return extension(
                descriptor(extensionId),
                new OpenAllayExtensionContribution(
                        List.of(new TestModule(dataModuleId, DetachedValue.class, false)),
                        List.of(new JavascriptModuleSource(
                                javascriptModuleId, "module.exports = { value: 1 };")),
                        List.of(),
                        List.of(new JavascriptResultViewProvider.Declaration(
                                extensionId + "_view",
                                JavascriptSemanticKind.TABLE,
                                "Test view"))));
    }

    private static OpenAllayExtension extension(OpenAllayExtensionDescriptor descriptor) {
        return extension(descriptor, OpenAllayExtensionContribution.empty());
    }

    private static OpenAllayExtension extension(
            OpenAllayExtensionDescriptor descriptor,
            OpenAllayExtensionContribution contribution) {
        return new OpenAllayExtension() {
            @Override
            public OpenAllayExtensionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public OpenAllayExtensionContribution contribution() {
                return contribution;
            }
        };
    }

    private static OpenAllayExtensionDescriptor descriptor(String id) {
        return descriptor(id, Set.of("fabric"), "[26.2,26.3)", "[0.2,0.3)");
    }

    private static OpenAllayExtensionDescriptor descriptor(
            String id, Set<String> loaders, String gameRange, String apiRange) {
        return new OpenAllayExtensionDescriptor(
                id,
                id,
                "1.0.0",
                "Test Provider",
                "Test extension",
                loaders,
                gameRange,
                apiRange,
                "test");
    }

    private static EvidenceMetadata evidence() {
        return new EvidenceMetadata(
                DataAuthority.CLIENT_VISIBLE,
                DataCompleteness.COMPLETE,
                Instant.EPOCH,
                "test:extension",
                "test:extension",
                "26.2",
                "fabric",
                Map.of());
    }

    private record DetachedValue(String value) {}

    private record TestModule(String id, Type valueType, boolean fail)
            implements JavascriptDataModule {
        @Override
        public Snapshot capture(ToolInvocationContext context) {
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return new Snapshot(new DetachedValue(id), List.of(evidence()));
        }
    }

    private static final class Fixture {
        private final JavascriptDataModuleRegistry dataModules =
                new JavascriptDataModuleRegistry();
        private final JavascriptModuleCatalog modules =
                new JavascriptModuleCatalog(Map.of());
        private final SkillRepository skills =
                new SkillRepository(new SkillParser(), List.of("openallay:run_javascript"));
        private final OpenAllayExtensionRegistry registry =
                new OpenAllayExtensionRegistry(
                        new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"),
                        dataModules,
                        modules,
                        skills,
                        Set.of());
    }
}
