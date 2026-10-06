package dev.openallay.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.extension.JavascriptInvocationContext;
import dev.openallay.extension.JavascriptInvocationParticipant;
import dev.openallay.extension.JavascriptInvocationScope;
import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionContribution;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.extension.OpenAllayExtensionState;
import dev.openallay.integration.patchouli.PatchouliMultiblockStore;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.PlatformService;
import dev.openallay.script.UnrestrictedJavascriptConfig;
import dev.openallay.script.UnrestrictedJavascriptRuntime;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class MinecraftGuideExtensionCapabilitiesTest {
    @Test
    void activeExtensionParticipatesWithoutAnAdditionalPlayerGrant() {
        Fixture fixture = new Fixture();
        fixture.provider.freezeRequest("local", true);
        JavascriptInvocationScope scope = fixture.open("local");
        JavascriptInvocationContext admitted = fixture.authority.get();
        admitted.requireActive();

        assertEquals("sample:extension", admitted.extensionId());
        assertEquals("local", admitted.invocation().correlationId());
        assertFalse(fixture.unrestricted.enabledFor("local"));
        assertEquals(1, fixture.runtime.extensions().activeJavascriptInvocations());
        fixture.provider.closeRequest("local");
        assertTrue(scope.cancellation().isCancelled());
        assertThrows(RuntimeException.class, admitted::requireActive);
        assertEquals(0, fixture.runtime.extensions().activeJavascriptInvocations());
        scope.close();

        fixture.provider.freezeRequest("local", true);
        try (JavascriptInvocationScope replacement = fixture.open("local")) {
            JavascriptInvocationContext readmitted = fixture.authority.get();
            readmitted.requireActive();
            assertNotSame(admitted, readmitted);
            assertEquals("sample:extension", readmitted.extensionId());
            assertEquals("local", readmitted.invocation().correlationId());
            assertSame(readmitted.cancellation(), replacement.cancellation());
        }
        fixture.provider.closeRequest("local");
        assertEquals(0, fixture.runtime.extensions().activeJavascriptInvocations());
    }

    @Test
    void fullAccessDoesNotChangeRegisteredComponentAvailabilityOrExecutionLifetime() {
        Fixture fixture = new Fixture();
        fixture.unrestricted.replace(new UnrestrictedJavascriptConfig(true));
        fixture.provider.freezeRequest("jvm", true);
        JavascriptInvocationContext admitted;
        try (JavascriptInvocationScope scope = fixture.open("jvm")) {
            admitted = fixture.authority.get();
            admitted.requireActive();
            assertEquals("sample:extension", admitted.extensionId());
            assertTrue(fixture.unrestricted.enabledFor("jvm"));
            assertFalse(scope.cancellation().isCancelled());
        }
        assertTrue(admitted.cancellation().isCancelled());
        assertThrows(RuntimeException.class, admitted::requireActive);
        fixture.provider.closeRequest("jvm");
        assertEquals(0, fixture.runtime.extensions().activeJavascriptInvocations());
    }

    @Test
    void capturePathsFreezeSettingsBeforeContextCaptureIncludingServerFallback() throws Exception {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path root = current.getFileName().toString().equals("common") ? current.getParent() : current;
        String source = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/MinecraftGuideContextProvider.java"));
        int server = source.indexOf("public ToolResult<ToolInvocationContext> captureServerToolContext(");
        int capture = source.indexOf("private ToolResult<ToolInvocationContext> capture(");
        assertTrue(source.substring(server, capture).contains("capture(capabilities, correlationId, false)"));
        String captureBody = source.substring(capture, source.indexOf("public RecipeProviderReadiness", capture));
        assertTrue(captureBody.contains("freezeJavascriptAndCommands(correlationId, clientLocalModel)"));
        assertTrue(captureBody.indexOf("freezeJavascriptAndCommands(") < captureBody.indexOf("client.player == null"));
        assertFalse(source.contains("runtime.extensions().freezeJavascriptRequest("));
    }

    private static final class Fixture {
        private final AtomicReference<JavascriptInvocationContext> authority = new AtomicReference<>();
        private final OpenAllayRuntime runtime;
        private final UnrestrictedJavascriptRuntime unrestricted = new UnrestrictedJavascriptRuntime();
        private final MinecraftGuideContextProvider provider;

        private Fixture() {
            ToolRegistry tools = new ToolRegistry();
            runtime = new OpenAllayRuntime(new PlatformService() {
                @Override public String platformName() { return "fabric"; }
                @Override public String gameVersion() { return "26.2"; }
                @Override public boolean isModLoaded(String modId) { return false; }
                @Override public boolean isDevelopmentEnvironment() { return true; }
            }, tools, new KnowledgeRegistry(), new PatchouliMultiblockStore(),
                    new SkillRepository(new SkillParser(), List.of()), new DevelopmentToolInspector(tools), null);
            assertEquals(OpenAllayExtensionState.ACTIVE, runtime.extensions().register(new OpenAllayExtension() {
                @Override public OpenAllayExtensionDescriptor descriptor() {
                    return new OpenAllayExtensionDescriptor("sample:extension", "Sample", "1.0.0", "Provider",
                            "Native actions", Set.of("fabric"), "[26.2,26.3)", "[0.4,0.5)", "bundled");
                }
                @Override public OpenAllayExtensionContribution contribution() {
                    return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of(),
                            List.of(new JavascriptInvocationParticipant() {
                                @Override public String id() { return "sample:invocation"; }
                                @Override public AutoCloseable open(JavascriptInvocationContext context) {
                                    authority.set(context);
                                    return () -> {};
                                }
                            }), List.of());
                }
            }).state());
            provider = new MinecraftGuideContextProvider(runtime, null, dev.openallay.json.EngineJson.create(), getClass().getClassLoader());
            provider.setUnrestrictedJavascriptRuntime(unrestricted);
        }

        private JavascriptInvocationScope open(String id) {
            JavascriptInvocationScope scope = runtime.extensions().prepareJavascriptInvocation(
                    ToolInvocationContext.developmentConsole(id), new CancellationSignal());
            scope.open(ignored -> {});
            return scope;
        }
    }
}
