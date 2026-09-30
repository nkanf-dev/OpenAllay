package dev.openallay.extension;

import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.script.schema.RhinoTypeSchema;
import dev.openallay.skill.SkillRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Common owner of loader-registered Extension declarations.
 *
 * <p>A candidate is completely validated before any contribution registry changes. Rejected
 * candidates never replace the last published generation.
 */
public final class OpenAllayExtensionRegistry {
    private final OpenAllayExtensionEnvironment environment;
    private final JavascriptDataModuleRegistry dataModules;
    private final JavascriptModuleCatalog javascriptModules;
    private final SkillRepository skills;
    private final Set<String> installedMods;
    private final Map<String, RegisteredExtension> active = new TreeMap<>();
    private final Map<String, String> contributionOwners = new TreeMap<>();
    private final Map<String, Set<JavascriptInvocationScope>> invocations = new java.util.HashMap<>();
    private List<JavascriptInvocationParticipant> javascriptInvocationParticipants = List.of();
    private long generation;

    public OpenAllayExtensionRegistry(
            OpenAllayExtensionEnvironment environment,
            JavascriptDataModuleRegistry dataModules,
            JavascriptModuleCatalog javascriptModules,
            SkillRepository skills,
            Set<String> installedMods) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.dataModules = Objects.requireNonNull(dataModules, "dataModules");
        this.javascriptModules = Objects.requireNonNull(javascriptModules, "javascriptModules");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.installedMods = Set.copyOf(installedMods);
    }

    public synchronized Registration register(OpenAllayExtension extension) {
        Objects.requireNonNull(extension, "extension");
        OpenAllayExtensionDescriptor descriptor;
        OpenAllayExtensionContribution contribution;
        try {
            descriptor = Objects.requireNonNull(extension.descriptor(), "descriptor");
            OpenAllayExtensionContribution declared =
                    Objects.requireNonNull(extension.contribution(), "contribution");
            // Capture foreign IDs exactly once before any registry mutation.
            contribution = new OpenAllayExtensionContribution(
                    declared.dataModules(), declared.javascriptModules(), declared.skills(),
                    declared.resultViews(), declared.javascriptInvocationParticipants().stream()
                            .map(participant -> (JavascriptInvocationParticipant)
                                    new RegisteredParticipant(participant.id(), participant))
                            .toList());
        } catch (RuntimeException failure) {
            return rejected("", OpenAllayExtensionState.UNAVAILABLE, "extension_registration_failed");
        }
        if (active.containsKey(descriptor.id())) {
            return rejected(
                    descriptor.id(), OpenAllayExtensionState.UNAVAILABLE, "duplicate_extension_id");
        }
        String incompatibility = environment.incompatibility(descriptor);
        if (!incompatibility.isEmpty()) {
            return rejected(descriptor.id(), OpenAllayExtensionState.INCOMPATIBLE, incompatibility);
        }
        try {
            validateContribution(descriptor.id(), contribution);
            publish(descriptor, contribution);
            generation++;
            return new Registration(
                    descriptor.id(), OpenAllayExtensionState.ACTIVE, "", generation);
        } catch (DuplicateContribution failure) {
            return rejected(
                    descriptor.id(),
                    OpenAllayExtensionState.UNAVAILABLE,
                    "duplicate_contribution_id");
        } catch (RuntimeException failure) {
            return rejected(
                    descriptor.id(),
                    OpenAllayExtensionState.UNAVAILABLE,
                    "extension_registration_failed");
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                generation,
                active.values().stream()
                        .map(RegisteredExtension::view)
                        .toList());
    }

    public synchronized List<JavascriptInvocationParticipant> javascriptInvocationParticipants() {
        return javascriptInvocationParticipants;
    }

    public OpenAllayExtensionEnvironment environment() {
        return environment;
    }

    /**
     * Admits work synchronously before its worker is launched. The caller owns the request
     * cancellation signal and must revoke it before terminal request cleanup. A queued callback
     * retains that revoked signal as its tombstone; no closed IDs are retained by this registry.
     */
    public synchronized JavascriptInvocationScope prepareJavascriptInvocation(
            dev.openallay.context.ToolInvocationContext invocation,
            dev.openallay.model.CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        String requestId = invocation.correlationId();
        Set<JavascriptInvocationScope> scopes =
                invocations.computeIfAbsent(requestId, ignored -> new HashSet<>());
        JavascriptInvocationScope[] reference = new JavascriptInvocationScope[1];
        JavascriptInvocationScope scope = new JavascriptInvocationScope(
                invocation, cancellation, javascriptInvocationParticipants,
                () -> releaseInvocation(requestId, reference[0]));
        reference[0] = scope;
        scopes.add(scope);
        return scope;
    }

    /** Revokes native activity now; worker-local scopes still unwind on their own workers. */
    public void closeJavascriptRequest(String correlationId) {
        Set<JavascriptInvocationScope> scopes;
        synchronized (this) {
            scopes = invocations.remove(correlationId);
        }
        if (scopes != null) scopes.forEach(JavascriptInvocationScope::revoke);
    }

    public synchronized int activeJavascriptInvocations() {
        return invocations.values().stream().mapToInt(Set::size).sum();
    }

    private synchronized void releaseInvocation(String requestId, JavascriptInvocationScope scope) {
        Set<JavascriptInvocationScope> scopes = invocations.get(requestId);
        if (scopes == null) return;
        scopes.remove(scope);
        if (scopes.isEmpty()) invocations.remove(requestId);
    }

    private void validateContribution(
            String extensionId, OpenAllayExtensionContribution contribution) {
        Set<String> batch = new HashSet<>();
        for (JavascriptDataModule module : contribution.dataModules()) {
            Objects.requireNonNull(module, "dataModule");
            claim(extensionId, module.id(), batch);
            RhinoTypeSchema.require(module.valueType());
        }
        LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
        for (JavascriptModuleSource module : contribution.javascriptModules()) {
            Objects.requireNonNull(module, "javascriptModule");
            claim(extensionId, module.id(), batch);
            moduleSources.put(module.id(), module.source());
        }
        for (JavascriptResultViewProvider view : contribution.resultViews()) {
            Objects.requireNonNull(view, "resultView");
            claim(extensionId, view.id(), batch);
            Objects.requireNonNull(view.kind(), "resultView.kind");
            if (view.summary() == null || view.summary().isBlank()) {
                throw new IllegalArgumentException("Result view summary is required");
            }
        }
        for (JavascriptInvocationParticipant participant : contribution.javascriptInvocationParticipants()) {
            Objects.requireNonNull(participant, "javascriptInvocationParticipant");
            String id = participant.id();
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid invocation participant ID");
            }
            claim(extensionId, id, batch);
        }
        dataModules.validateRegistration(extensionId, contribution.dataModules());
        javascriptModules.validateRegistration(extensionId, moduleSources);
        skills.validateExternal(contribution.skills(), installedMods);
    }

    private void claim(String extensionId, String contributionId, Set<String> batch) {
        if (contributionId == null || contributionId.isBlank()) {
            throw new IllegalArgumentException("Contribution ID is required");
        }
        if (!batch.add(contributionId)) {
            throw new DuplicateContribution();
        }
        String owner = contributionOwners.get(contributionId);
        if (owner != null && !owner.equals(extensionId)) {
            throw new DuplicateContribution();
        }
    }

    private void publish(
            OpenAllayExtensionDescriptor descriptor,
            OpenAllayExtensionContribution contribution) {
        LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
        contribution.javascriptModules().forEach(module ->
                moduleSources.put(module.id(), module.source()));
        skills.registerExternal(contribution.skills(), installedMods);
        dataModules.register(descriptor.id(), contribution.dataModules());
        javascriptModules.register(descriptor.id(), moduleSources);
        contribution.dataModules().forEach(module ->
                contributionOwners.put(module.id(), descriptor.id()));
        contribution.javascriptModules().forEach(module ->
                contributionOwners.put(module.id(), descriptor.id()));
        contribution.resultViews().forEach(view ->
                contributionOwners.put(view.id(), descriptor.id()));
        RegisteredExtension registered = new RegisteredExtension(descriptor, contribution);
        contribution.javascriptInvocationParticipants().forEach(participant ->
                contributionOwners.put(participant.id(), descriptor.id()));
        active.put(descriptor.id(), registered);
        javascriptInvocationParticipants = active.values().stream()
                .flatMap(value -> value.contribution().javascriptInvocationParticipants().stream())
                .toList();
    }

    private Registration rejected(
            String extensionId, OpenAllayExtensionState state, String diagnostic) {
        return new Registration(extensionId, state, diagnostic, generation);
    }

    public record Registration(
            String extensionId,
            OpenAllayExtensionState state,
            String diagnostic,
            long generation) {}

    public record Snapshot(long generation, List<ExtensionView> extensions) {
        public Snapshot {
            extensions = List.copyOf(extensions);
        }
    }

    public record ExtensionView(
            OpenAllayExtensionDescriptor descriptor,
            OpenAllayExtensionState state,
            List<String> dataModules,
            List<String> javascriptModules,
            List<String> skills,
            List<String> resultViews,
            String diagnostic) {
        public ExtensionView {
            dataModules = List.copyOf(dataModules);
            javascriptModules = List.copyOf(javascriptModules);
            skills = List.copyOf(skills);
            resultViews = List.copyOf(resultViews);
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    private record RegisteredExtension(
            OpenAllayExtensionDescriptor descriptor,
            OpenAllayExtensionContribution contribution) {
        private ExtensionView view() {
            return new ExtensionView(
                    descriptor,
                    OpenAllayExtensionState.ACTIVE,
                    contribution.dataModules().stream().map(JavascriptDataModule::id).sorted().toList(),
                    contribution.javascriptModules().stream()
                            .map(JavascriptModuleSource::id)
                            .sorted()
                            .toList(),
                    contribution.skills().stream()
                            .map(source -> source.directoryName())
                            .sorted()
                            .toList(),
                    contribution.resultViews().stream()
                            .map(JavascriptResultViewProvider::id)
                            .sorted()
                            .toList(),
                    "");
        }
    }

    private record RegisteredParticipant(String id, JavascriptInvocationParticipant delegate)
            implements JavascriptInvocationParticipant {
        @Override
        public AutoCloseable open(JavascriptInvocationContext context) throws Exception {
            return delegate.open(context);
        }
    }

    private static final class DuplicateContribution extends RuntimeException {}
}
