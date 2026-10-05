package dev.openallay.fixture;

import dev.openallay.api.extension.*;
import java.time.Instant;
import java.util.*;

/** One payload compiled once against the SDK, then exercised unchanged on JVM 8 upward. */
public final class HelloExtension implements OpenAllayExtension {
    public HelloExtension() {}
    @Override public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor("fixture:hello", "Hello", "1.0.0", "OpenAllay", "SDK ABI fixture",
                "fixture:source", new SupportDeclaration(Arrays.asList(
                    new SupportTarget("forge", "1.12.2", "[0.4.1,)", "[0.4.0,0.5.0)"),
                    new SupportTarget("future-loader", "26.3", "[0.4.1,)", "[0.4.0,0.5.0)")),
                    8, Collections.<String>emptySet(), Collections.<String>emptySet()), ExtensionRequirements.EMPTY);
    }
    @Override public ExtensionContribution contribution(ExtensionHost host) {
        if (!host.environment().openAllayApiVersions().contains("0.4.0"))
            throw new ExtensionException("fixture_api_missing", "The fixture needs Extension API 0.4.0.");
        // Intentionally never asks for native world access during discovery/contribution.
        JavascriptHostMethod echo = new JavascriptHostMethod("echo", Arrays.asList(JavascriptHostValueType.STRING),
                JavascriptHostValueType.STRING, new JavascriptHostMethod.Invoker() {
                    @Override public String invoke(ExtensionInvocation context, List<String> argumentJson) {
                        context.requireActive();
                        return argumentJson.get(0);
                    }
                });
        return new ExtensionContribution(Arrays.asList(new JavascriptModuleSource("fixture:module",
                "module.exports = { hello: function() { return 'hello'; } };")),
                Collections.<SkillSource>emptyList(), Collections.<ResultViewDeclaration>emptyList(),
                Collections.<JavascriptInvocationParticipant>emptyList(),
                Arrays.asList(new JavascriptHostBinding("fixture:host", Arrays.asList(echo))));
    }
    public static void main(String[] args) throws Exception {
        HelloExtension extension = new HelloExtension();
        ExtensionHost host = new ExtensionHost() {
            @Override public ExtensionEnvironment environment() {
                return new ExtensionEnvironment("future-loader", "26.3", "0.4.1",
                        new HashSet<String>(Arrays.asList("0.2.2", "0.4.0")), 8, Collections.<String>emptySet());
            }
            @Override public MinecraftWorldAccess minecraftWorldAccess() {
                throw new AssertionError("Pure-JS contribution must not capture native state");
            }
        };
        Invocation invocation = new Invocation();
        ExtensionContribution contribution = extension.contribution(host);
        String value = contribution.hostBindings().get(0).methods().get(0).invoker().invoke(
                invocation, Arrays.asList("\"hello\""));
        if (!"\"hello\"".equals(value) || !"fixture:hello".equals(extension.descriptor().id()))
            throw new AssertionError("Detached ABI result mismatch");
        invocation.onCancel(new Runnable() { @Override public void run() { invocation.notified = true; } });
        invocation.cancel();
        if (!invocation.notified) throw new AssertionError("Cancel callback missing");
        WorldSession.WriteOutcome write = new WorldSession.WriteOutcome("{}", true, null);
        if (!write.changed() || write.failure() != null) throw new AssertionError("Write accounting mismatch");
        System.out.println("HELLO_EXTENSION_SDK_OK");
    }
    private static final class Invocation implements ExtensionInvocation {
        private boolean cancelled;
        private boolean notified;
        private Runnable listener;
        void cancel() { cancelled = true; if (listener != null) listener.run(); }
        @Override public String extensionId() { return "fixture:hello"; }
        @Override public String correlationId() { return "fixture:invocation"; }
        @Override public Instant capturedAt() { return Instant.EPOCH; }
        @Override public CallerKind callerKind() { return CallerKind.CONSOLE; }
        @Override public UUID callerUuid() { return null; }
        @Override public Optional<String> playerDimension() { return Optional.empty(); }
        @Override public void requireActive() {
            if (cancelled) throw new ExtensionException("fixture_cancelled", "The fixture was cancelled.");
        }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void onCancel(Runnable callback) {
            listener = Objects.requireNonNull(callback, "callback"); if (cancelled) callback.run();
        }
        @Override public boolean completedSuccessfully() { return false; }
        @Override public void recordEvidence(ExtensionEvidence evidence) { Objects.requireNonNull(evidence, "evidence"); }
    }
}
