package dev.openallay.extension.universal;

import com.google.gson.JsonElement;
import dev.openallay.api.extension.ExtensionDescriptor;
import dev.openallay.api.extension.ExtensionEnvironment;
import dev.openallay.api.extension.ExtensionException;
import dev.openallay.api.extension.ExtensionHost;
import dev.openallay.api.extension.MinecraftWorldAccess;
import dev.openallay.extension.JavascriptHostBinding;
import dev.openallay.extension.JavascriptHostMethod;
import dev.openallay.extension.JavascriptHostValueType;
import dev.openallay.extension.JavascriptInvocationContext;
import dev.openallay.extension.JavascriptInvocationParticipant;
import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionContribution;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/** Maps verified public declarations onto the current core registries without widening authority. */
public final class UniversalExtensionBridge implements OpenAllayExtension {
    private final dev.openallay.api.extension.OpenAllayExtension delegate;
    private final ExtensionHost host;
    private final OpenAllayExtensionDescriptor descriptor;
    private OpenAllayExtensionContribution contribution;
    private RuntimeException contributionFailure;
    private boolean contributionAttempted;

    public UniversalExtensionBridge(dev.openallay.api.extension.OpenAllayExtension delegate,
            ExtensionDescriptor verifiedDescriptor, ExtensionHost host) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.host = Objects.requireNonNull(host, "host");
        Objects.requireNonNull(verifiedDescriptor, "verifiedDescriptor");
        dev.openallay.api.extension.SupportTarget target = UniversalExtensionSupport.matchingTarget(verifiedDescriptor.support(), host.environment())
                .orElseThrow(() -> new IllegalArgumentException("Extension support is incompatible"));
        descriptor = UniversalExtensionSupport.legacyDescriptor(verifiedDescriptor, target);
    }

    public static ExtensionHost host(ExtensionEnvironment environment, MinecraftWorldAccess worldAccess) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(worldAccess, "worldAccess");
        return new ExtensionHost() {
            @Override public ExtensionEnvironment environment() { return environment; }
            @Override public MinecraftWorldAccess minecraftWorldAccess() { return worldAccess; }
        };
    }

    @Override public OpenAllayExtensionDescriptor descriptor() { return descriptor; }

    /** Called only after the root registry has checked this descriptor and duplicate identity. */
    @Override public synchronized OpenAllayExtensionContribution contribution() {
        if (contributionAttempted) {
            if (contributionFailure != null) throw contributionFailure;
            return contribution;
        }
        contributionAttempted = true;
        try {
            dev.openallay.api.extension.ExtensionContribution declared = Objects.requireNonNull(delegate.contribution(host), "contribution");
            java.util.List<dev.openallay.extension.JavascriptModuleSource> modules = declared.javascriptModules().stream().map(value ->
                    new dev.openallay.extension.JavascriptModuleSource(value.id(), value.source())).toList();
            java.util.List<dev.openallay.skill.SkillSource> skills = declared.skills().stream().map(value ->
                    new dev.openallay.skill.SkillSource(value.provenance(), value.entryPath(), value.files(),
                            dev.openallay.skill.SkillSource.Origin.valueOf(value.origin().name()))).toList();
            List<JavascriptResultViewProvider> views = declared.resultViews().stream().map(value ->
                    (JavascriptResultViewProvider) new JavascriptResultViewProvider.Declaration(value.id(),
                            JavascriptSemanticKind.valueOf(value.kind().name()), value.summary())).toList();
            List<JavascriptInvocationParticipant> participants = new ArrayList<>();
            for (dev.openallay.api.extension.JavascriptInvocationParticipant value : declared.javascriptInvocationParticipants()) {
                String id = value.id(); // Capture the only foreign declaration accessor exactly once.
                participants.add(new JavascriptInvocationParticipant() {
                    @Override public String id() { return id; }
                    @Override public AutoCloseable open(JavascriptInvocationContext context) throws Exception {
                        context.requireActive();
                        try {
                            AutoCloseable opened = Objects.requireNonNull(
                                    value.open(context.sdkInvocation()), "invocation scope");
                            return () -> {
                                try { opened.close(); }
                                catch (Throwable failure) {
                                    throw safeFailure(null, failure, "javascript_participant_cleanup_failed",
                                            "JavaScript participant cleanup failed");
                                }
                            };
                        } catch (Throwable failure) {
                            throw safeFailure(context, failure, "javascript_participant_setup_failed",
                                    "JavaScript participant setup failed");
                        }
                    }
                });
            }
            List<JavascriptHostBinding> bindings = declared.hostBindings().stream().map(binding ->
                    new JavascriptHostBinding(binding.id(), binding.methods().stream().map(method -> {
                        List<JavascriptHostValueType> parameters = method.parameters().stream()
                                .map(type -> JavascriptHostValueType.valueOf(type.name())).toList();
                        JavascriptHostValueType result = JavascriptHostValueType.valueOf(method.result().name());
                        return new JavascriptHostMethod(method.name(), parameters, result,
                                (context, arguments) -> {
                                    context.requireActive();
                                    if (arguments.size() != parameters.size()) throw invalidHost();
                                    List<String> json = new ArrayList<>();
                                    for (int index = 0; index < arguments.size(); index++) {
                                        JsonElement argument = arguments.get(index);
                                        if (!parameters.get(index).accepts(argument)) throw invalidHost();
                                        try {
                                            requireFiniteHostNumbers(argument);
                                            json.add(UniversalExtensionJson.parse(argument.toString()).toString());
                                        } catch (RuntimeException invalid) { throw invalidHost(); }
                                    }
                                    String returned;
                                    try {
                                        returned = method.invoker().invoke(context.sdkInvocation(),
                                                List.copyOf(json));
                                    } catch (Throwable failure) {
                                        throw safeFailure(context, failure, "javascript_extension_host_failed",
                                                "Extension host method failed");
                                    }
                                    context.requireActive();
                                    try {
                                        JsonElement parsed = UniversalExtensionJson.parse(returned);
                                        requireFiniteHostNumbers(parsed);
                                        if (!result.accepts(parsed)) throw invalidHost();
                                        return parsed;
                                    } catch (RuntimeException invalid) { throw invalidHost(); }
                                });
                    }).toList())).toList();
            contribution = new OpenAllayExtensionContribution(List.of(), modules, skills, views,
                    participants, bindings);
            return contribution;
        } catch (Throwable failure) {
            contributionFailure = safeFailure(null, failure, "extension_registration_failed",
                    "Extension contribution could not be registered");
            throw contributionFailure;
        }
    }

    /** Existing host NUMBER algebra is finite, including numbers nested in JSON values. */
    private static void requireFiniteHostNumbers(JsonElement value) {
        java.util.ArrayDeque<com.google.gson.JsonElement> pending = new java.util.ArrayDeque<JsonElement>();
        pending.push(value);
        while (!pending.isEmpty()) {
            JsonElement current = pending.pop();
            if (current.isJsonArray()) current.getAsJsonArray().forEach(pending::push);
            else if (current.isJsonObject()) current.getAsJsonObject().entrySet()
                    .forEach(entry -> pending.push(entry.getValue()));
            else if (current.isJsonPrimitive() && current.getAsJsonPrimitive().isNumber()
                    && !JavascriptHostValueType.NUMBER.accepts(current)) throw invalidHost();
        }
    }

    private static JavascriptExecutionException invalidHost() {
        return new JavascriptExecutionException("javascript_extension_host_invalid",
                "Extension host method requires valid JSON matching its declaration");
    }

    private static RuntimeException safeFailure(JavascriptInvocationContext context, Throwable failure,
            String code, String summary) {
        // The request tombstone wins over a late foreign failure, including an SDK diagnostic.
        if (context != null) context.requireActive();
        if (failure instanceof ModelClientException cancelled
                && "agent_cancelled".equals(cancelled.failure().code())) {
            return new ModelClientException(new dev.openallay.model.ModelFailure(
                    "agent_cancelled", "Agent request was cancelled", null));
        }
        if (failure instanceof CancellationException) {
            return new ModelClientException(new dev.openallay.model.ModelFailure(
                    "agent_cancelled", "Agent request was cancelled", null));
        }
        if (failure instanceof ExtensionException declared) {
            return new JavascriptExecutionException(declared.code(), declared.summary());
        }
        return new JavascriptExecutionException(code, summary);
    }
}
