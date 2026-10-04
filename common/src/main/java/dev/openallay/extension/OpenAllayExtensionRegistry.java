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
    private ExtensionCapabilityPolicy capabilityPolicy = ExtensionCapabilityPolicy.defaults();
    private final Map<String, ExtensionCapabilityPolicy> javascriptRequests = new java.util.HashMap<>();
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
        } catch (Throwable failure) {
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
            OpenAllayExtensionContribution declared =
                    Objects.requireNonNull(extension.contribution(), "contribution");
            // Capture foreign IDs only after identity and compatibility admission, before mutation.
            contribution = new OpenAllayExtensionContribution(
                    declared.dataModules(), declared.javascriptModules(), declared.skills(),
                    declared.resultViews(), declared.javascriptInvocationParticipants().stream()
                            .map(participant -> (JavascriptInvocationParticipant)
                                    new RegisteredParticipant(participant.id(), participant))
                            .toList(), declared.hostBindings(), declared.capabilities());
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
        } catch (Throwable failure) {
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

    public synchronized Map<String, List<ExtensionCapability>> capabilityDescriptors() {
        Map<String, List<ExtensionCapability>> descriptors = new TreeMap<>();
        active.forEach((id, value) -> descriptors.put(id, value.contribution().capabilities()));
        return Map.copyOf(descriptors);
    }

    public synchronized ExtensionCapabilityPolicy capabilityPolicy() { return capabilityPolicy; }

    /** Explicit settings publication only. Neither package installation nor Java access calls this. */
    public synchronized void replaceCapabilityPolicy(ExtensionCapabilityPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        Map<String, Set<String>> accepted = new TreeMap<>();
        active.forEach((id, extension) -> {
            Set<String> scopes = extension.contribution().capabilities().stream()
                    .map(ExtensionCapability::id).filter(scope -> policy.allows(id, scope))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!scopes.isEmpty()) accepted.put(id, scopes);
        });
        capabilityPolicy = new ExtensionCapabilityPolicy(accepted);
    }

    /** Server-origin requests always carry no Extension operation grants. */
    public synchronized void freezeJavascriptRequest(String correlationId, boolean clientLocalModel) {
        Objects.requireNonNull(correlationId, "correlationId");
        if (!clientLocalModel) {
            javascriptRequests.put(correlationId, ExtensionCapabilityPolicy.defaults());
            return;
        }
        javascriptRequests.computeIfAbsent(correlationId, ignored -> capabilityPolicy);
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
        ExtensionCapabilityPolicy frozen = javascriptRequests.getOrDefault(
                requestId, ExtensionCapabilityPolicy.defaults());
        List<JavascriptInvocationScope.Participant> participants = active.values().stream()
                .flatMap(value -> value.contribution().javascriptInvocationParticipants().stream()
                        .map(participant -> new JavascriptInvocationScope.Participant(
                                value.descriptor().id(), participant))).toList();
        List<JavascriptInvocationScope.Binding> bindings = active.values().stream()
                .flatMap(value -> value.contribution().hostBindings().stream()
                        .map(binding -> new JavascriptInvocationScope.Binding(
                                value.descriptor().id(), binding))).toList();
        Map<String, Set<String>> grants = new TreeMap<>();
        active.forEach((id, value) -> grants.put(id, value.contribution().capabilities().stream()
                .map(ExtensionCapability::id).filter(capability -> frozen.allows(id, capability))
                .collect(java.util.stream.Collectors.toUnmodifiableSet())));
        JavascriptInvocationScope scope = new JavascriptInvocationScope(
                invocation, cancellation, participants, bindings, grants,
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
            javascriptRequests.remove(correlationId);
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
        Set<String> declaredCapabilities = new HashSet<>();
        for (ExtensionCapability capability : contribution.capabilities()) {
            Objects.requireNonNull(capability, "capability");
            claim(extensionId, capability.id(), batch);
            declaredCapabilities.add(capability.id());
        }
        for (JavascriptHostBinding binding : contribution.hostBindings()) {
            Objects.requireNonNull(binding, "hostBinding");
            claim(extensionId, binding.id(), batch);
            if (javascriptModules.ids().contains(binding.id())) {
                throw new DuplicateContribution();
            }
            for (JavascriptHostMethod method : binding.methods()) {
                if (!declaredCapabilities.containsAll(method.requiredCapabilities())) {
                    throw new IllegalArgumentException("Host method requires an undeclared owner capability");
                }
            }
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
        contribution.hostBindings().forEach(binding ->
                contributionOwners.put(binding.id(), descriptor.id()));
        contribution.capabilities().forEach(capability ->
                contributionOwners.put(capability.id(), descriptor.id()));
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
            String diagnostic,
            List<String> hostBindings,
            List<ExtensionCapability> capabilities) {
        public ExtensionView {
            dataModules = List.copyOf(dataModules);
            javascriptModules = List.copyOf(javascriptModules);
            skills = List.copyOf(skills);
            resultViews = List.copyOf(resultViews);
            diagnostic = diagnostic == null ? "" : diagnostic;
            hostBindings = List.copyOf(hostBindings);
            capabilities = List.copyOf(capabilities);
        }

        public ExtensionView(OpenAllayExtensionDescriptor descriptor, OpenAllayExtensionState state,
                List<String> dataModules, List<String> javascriptModules, List<String> skills,
                List<String> resultViews, String diagnostic) {
            this(descriptor, state, dataModules, javascriptModules, skills, resultViews,
                    diagnostic, List.of(), List.of());
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
                    "",
                    contribution.hostBindings().stream().map(JavascriptHostBinding::id).sorted().toList(),
                    contribution.capabilities());
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
