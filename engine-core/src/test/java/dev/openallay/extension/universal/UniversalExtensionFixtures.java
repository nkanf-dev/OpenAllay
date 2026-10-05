package dev.openallay.extension.universal;

import dev.openallay.api.extension.*;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class UniversalExtensionFixtures {
    static ExtensionEnvironment environment() {
        return new ExtensionEnvironment("fabric", "26.2", "0.5.0", Set.of("0.2.2", "0.4.0"), 25, Set.of());
    }
    static SupportTarget target() {
        return new SupportTarget("fabric", "[26.2,26.3)", "[0.5,0.6)", "[0.4,0.5)");
    }
    static ExtensionDescriptor descriptor(String id) {
        return new ExtensionDescriptor(id, "Test", "1.0.0", "Test", "Test Extension", "test:source",
                new SupportDeclaration(List.of(target()), 8, Set.of(), Set.of()), ExtensionRequirements.EMPTY);
    }
    static ExtensionHost host(AtomicInteger opens) {
        return UniversalExtensionBridge.host(environment(), invocation -> {
            opens.incrementAndGet();
            throw new ExtensionException("minecraft_world_unavailable", "World access is unavailable");
        });
    }
    static OpenAllayExtensionRegistry registry() {
        return new OpenAllayExtensionRegistry(new OpenAllayExtensionEnvironment("fabric", "26.2", "0.4.0"),
                new JavascriptDataModuleRegistry(), new JavascriptModuleCatalog(java.util.Map.of()),
                new SkillRepository(new SkillParser(), List.of("openallay:run_javascript")), Set.of());
    }
    static UniversalExtensionBridge bridge(String id, ExtensionContribution contribution) {
        var descriptor = descriptor(id);
        return new UniversalExtensionBridge(new OpenAllayExtension() {
            public ExtensionDescriptor descriptor() { return descriptor; }
            public ExtensionContribution contribution(ExtensionHost host) { return contribution; }
        }, descriptor, host(new AtomicInteger()));
    }
    static String manifest(String id, String entrypoint) {
        return """
                {"schemaVersion":2,"id":"%s","name":"Test","version":"1.0.0","provider":"Test",
                "summary":"Test Extension","source":"test:source","entrypoint":"%s",
                "support":{"targets":[{"loader":"fabric","minecraftVersionRange":"[26.2,26.3)",
                "openAllayVersionRange":"[0.5,0.6)","openAllayApiVersionRange":"[0.4,0.5)"}],
                "minimumJavaVersion":8,"requiredHostFeatures":[],"validatedTargetIds":[]}}
                """.formatted(id, entrypoint);
    }
    private UniversalExtensionFixtures() {}
}
