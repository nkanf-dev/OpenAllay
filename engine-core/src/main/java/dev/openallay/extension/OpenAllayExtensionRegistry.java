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
    private final Set<JavascriptInvocationScope> liveInvocations = new HashSet<>();
    private boolean closing;
    private java.util.concurrent.CompletableFuture<Void> shutdown;
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
                            .toList(), declared.hostBindings());
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

    /**
     * Admits work synchronously before its worker is launched. The caller owns the request
     * cancellation signal and must revoke it before terminal request cleanup. A queued callback
     * retains that revoked signal as its tombstone; no closed IDs are retained by this registry.
     */
    public synchronized JavascriptInvocationScope prepareJavascriptInvocation(
            dev.openallay.context.ToolInvocationContext invocation,
            dev.openallay.model.CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        if (closing) throw new dev.openallay.script.JavascriptExecutionException(
                "javascript_invocation_closed", "Extension runtime is closing");
        String requestId = invocation.correlationId();
        Set<JavascriptInvocationScope> scopes =
                invocations.computeIfAbsent(requestId, ignored -> new HashSet<>());
        JavascriptInvocationScope[] reference = new JavascriptInvocationScope[1];
        List<JavascriptInvocationScope.Participant> participants = active.values().stream()
                .flatMap(value -> value.contribution().javascriptInvocationParticipants().stream()
                        .map(participant -> new JavascriptInvocationScope.Participant(
                                value.descriptor().id(), participant))).toList();
        List<JavascriptInvocationScope.Binding> bindings = active.values().stream()
                .flatMap(value -> value.contribution().hostBindings().stream()
                        .map(binding -> new JavascriptInvocationScope.Binding(
                                value.descriptor().id(), binding))).toList();
        JavascriptInvocationScope scope = new JavascriptInvocationScope(
                invocation, cancellation, participants, bindings, Set.copyOf(active.keySet()),
                () -> releaseInvocation(requestId, reference[0]));
        reference[0] = scope;
        scopes.add(scope);
        liveInvocations.add(scope);
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

    /** Revoke now, but keep classloaders alive until every admitted worker has closed its hooks. */
    public java.util.concurrent.CompletableFuture<Void> shutdown() {
        List<JavascriptInvocationScope> captured;
        java.util.concurrent.CompletableFuture<Void> receipt;
        synchronized (this) {
            if (shutdown != null) return shutdown;
            closing = true;
            captured = List.copyOf(liveInvocations);
            shutdown = new java.util.concurrent.CompletableFuture<>();
            receipt = shutdown;
        }
        captured.forEach(JavascriptInvocationScope::revoke);
        java.util.concurrent.CompletableFuture.allOf(captured.stream()
                .map(JavascriptInvocationScope::releasedFuture)
                .toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) receipt.complete(null);
                    else receipt.completeExceptionally(failure);
                });
        return receipt;
    }

    private synchronized void releaseInvocation(String requestId, JavascriptInvocationScope scope) {
        liveInvocations.remove(scope);
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
        for (JavascriptHostBinding binding : contribution.hostBindings()) {
            Objects.requireNonNull(binding, "hostBinding");
            claim(extensionId, binding.id(), batch);
            if (javascriptModules.ids().contains(binding.id())) {
                throw new DuplicateContribution();
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
        active.put(descriptor.id(), registered);
        javascriptInvocationParticipants = active.values().stream()
                .flatMap(value -> value.contribution().javascriptInvocationParticipants().stream())
                .toList();
    }

    private Registration rejected(
            String extensionId, OpenAllayExtensionState state, String diagnostic) {
        return new Registration(extensionId, state, diagnostic, generation);
    }

    @dev.openallay.value.ValueType(Registration.ValueSchemaProvider.class)
public static final class Registration {
    private final String extensionId;
    private final OpenAllayExtensionState state;
    private final String diagnostic;
    private final long generation;
    public Registration(String extensionId, OpenAllayExtensionState state, String diagnostic, long generation) {
        this.extensionId = extensionId;
        this.state = state;
        this.diagnostic = diagnostic;
        this.generation = generation;
    }
    public String extensionId() { return extensionId; }
    public OpenAllayExtensionState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public long generation() { return generation; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Registration)) return false;
        Registration that = (Registration) other;
        return java.util.Objects.equals(extensionId, that.extensionId) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(diagnostic, that.diagnostic) && generation == that.generation;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(extensionId);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        hash = 31 * hash + Long.hashCode(generation);
        return hash;
    }
    @Override public String toString() { return "Registration[extensionId=" + extensionId + ", state=" + state + ", diagnostic=" + diagnostic + ", generation=" + generation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Registration> schema() {
            return new dev.openallay.value.ValueSchema<>(Registration.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Registration>>asList(new dev.openallay.value.ValueSchema.Component<>(Registration.class, "extensionId", Registration::extensionId), new dev.openallay.value.ValueSchema.Component<>(Registration.class, "state", Registration::state), new dev.openallay.value.ValueSchema.Component<>(Registration.class, "diagnostic", Registration::diagnostic), new dev.openallay.value.ValueSchema.Component<>(Registration.class, "generation", Registration::generation)), arguments -> new Registration((String) arguments[0], (OpenAllayExtensionState) arguments[1], (String) arguments[2], (Long) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Snapshot.ValueSchemaProvider.class)
public static final class Snapshot {
    private final long generation;
    private final List<ExtensionView> extensions;
    public Snapshot(long generation, List<ExtensionView> extensions) {

            extensions = List.copyOf(extensions);

        this.generation = generation;
        this.extensions = extensions;
    }
    public long generation() { return generation; }
    public List<ExtensionView> extensions() { return extensions; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Snapshot)) return false;
        Snapshot that = (Snapshot) other;
        return generation == that.generation && java.util.Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        return hash;
    }
    @Override public String toString() { return "Snapshot[generation=" + generation + ", extensions=" + extensions + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Snapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(Snapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Snapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "generation", Snapshot::generation), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "extensions", Snapshot::extensions)), arguments -> new Snapshot((Long) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(ExtensionView.ValueSchemaProvider.class)
public static final class ExtensionView {
    private final OpenAllayExtensionDescriptor descriptor;
    private final OpenAllayExtensionState state;
    private final List<String> dataModules;
    private final List<String> javascriptModules;
    private final List<String> skills;
    private final List<String> resultViews;
    private final String diagnostic;
    private final List<String> hostBindings;
    public ExtensionView(OpenAllayExtensionDescriptor descriptor, OpenAllayExtensionState state, List<String> dataModules, List<String> javascriptModules, List<String> skills, List<String> resultViews, String diagnostic, List<String> hostBindings) {

            dataModules = List.copyOf(dataModules);
            javascriptModules = List.copyOf(javascriptModules);
            skills = List.copyOf(skills);
            resultViews = List.copyOf(resultViews);
            diagnostic = diagnostic == null ? "" : diagnostic;
            hostBindings = List.copyOf(hostBindings);

        this.descriptor = descriptor;
        this.state = state;
        this.dataModules = dataModules;
        this.javascriptModules = javascriptModules;
        this.skills = skills;
        this.resultViews = resultViews;
        this.diagnostic = diagnostic;
        this.hostBindings = hostBindings;
    }
    public OpenAllayExtensionDescriptor descriptor() { return descriptor; }
    public OpenAllayExtensionState state() { return state; }
    public List<String> dataModules() { return dataModules; }
    public List<String> javascriptModules() { return javascriptModules; }
    public List<String> skills() { return skills; }
    public List<String> resultViews() { return resultViews; }
    public String diagnostic() { return diagnostic; }
    public List<String> hostBindings() { return hostBindings; }
public ExtensionView(OpenAllayExtensionDescriptor descriptor, OpenAllayExtensionState state,
                List<String> dataModules, List<String> javascriptModules, List<String> skills,
                List<String> resultViews, String diagnostic) {
            this(descriptor, state, dataModules, javascriptModules, skills, resultViews,
                    diagnostic, List.of());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionView)) return false;
        ExtensionView that = (ExtensionView) other;
        return java.util.Objects.equals(descriptor, that.descriptor) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(dataModules, that.dataModules) && java.util.Objects.equals(javascriptModules, that.javascriptModules) && java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(resultViews, that.resultViews) && java.util.Objects.equals(diagnostic, that.diagnostic) && java.util.Objects.equals(hostBindings, that.hostBindings);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(descriptor);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(dataModules);
        hash = 31 * hash + java.util.Objects.hashCode(javascriptModules);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(resultViews);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        hash = 31 * hash + java.util.Objects.hashCode(hostBindings);
        return hash;
    }
    @Override public String toString() { return "ExtensionView[descriptor=" + descriptor + ", state=" + state + ", dataModules=" + dataModules + ", javascriptModules=" + javascriptModules + ", skills=" + skills + ", resultViews=" + resultViews + ", diagnostic=" + diagnostic + ", hostBindings=" + hostBindings + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionView> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionView>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "descriptor", ExtensionView::descriptor), new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "state", ExtensionView::state), new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "dataModules", ExtensionView::dataModules), new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "javascriptModules", ExtensionView::javascriptModules), new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "skills", ExtensionView::skills), new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "resultViews", ExtensionView::resultViews), new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "diagnostic", ExtensionView::diagnostic), new dev.openallay.value.ValueSchema.Component<>(ExtensionView.class, "hostBindings", ExtensionView::hostBindings)), arguments -> new ExtensionView((OpenAllayExtensionDescriptor) arguments[0], (OpenAllayExtensionState) arguments[1], (List) arguments[2], (List) arguments[3], (List) arguments[4], (List) arguments[5], (String) arguments[6], (List) arguments[7]));
        }
    }
}

    @dev.openallay.value.ValueType(RegisteredExtension.ValueSchemaProvider.class)
private static final class RegisteredExtension {
    private final OpenAllayExtensionDescriptor descriptor;
    private final OpenAllayExtensionContribution contribution;
    private RegisteredExtension(OpenAllayExtensionDescriptor descriptor, OpenAllayExtensionContribution contribution) {
        this.descriptor = descriptor;
        this.contribution = contribution;
    }
    public OpenAllayExtensionDescriptor descriptor() { return descriptor; }
    public OpenAllayExtensionContribution contribution() { return contribution; }
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
                    contribution.hostBindings().stream().map(JavascriptHostBinding::id).sorted().toList());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegisteredExtension)) return false;
        RegisteredExtension that = (RegisteredExtension) other;
        return java.util.Objects.equals(descriptor, that.descriptor) && java.util.Objects.equals(contribution, that.contribution);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(descriptor);
        hash = 31 * hash + java.util.Objects.hashCode(contribution);
        return hash;
    }
    @Override public String toString() { return "RegisteredExtension[descriptor=" + descriptor + ", contribution=" + contribution + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RegisteredExtension> schema() {
            return new dev.openallay.value.ValueSchema<>(RegisteredExtension.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RegisteredExtension>>asList(new dev.openallay.value.ValueSchema.Component<>(RegisteredExtension.class, "descriptor", RegisteredExtension::descriptor), new dev.openallay.value.ValueSchema.Component<>(RegisteredExtension.class, "contribution", RegisteredExtension::contribution)), arguments -> new RegisteredExtension((OpenAllayExtensionDescriptor) arguments[0], (OpenAllayExtensionContribution) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(RegisteredParticipant.ValueSchemaProvider.class)
private static final class RegisteredParticipant implements JavascriptInvocationParticipant {
    private final String id;
    private final JavascriptInvocationParticipant delegate;
    private RegisteredParticipant(String id, JavascriptInvocationParticipant delegate) {
        this.id = id;
        this.delegate = delegate;
    }
    public String id() { return id; }
    public JavascriptInvocationParticipant delegate() { return delegate; }
@Override
        public AutoCloseable open(JavascriptInvocationContext context) throws Exception {
            return delegate.open(context);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegisteredParticipant)) return false;
        RegisteredParticipant that = (RegisteredParticipant) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(delegate, that.delegate);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(delegate);
        return hash;
    }
    @Override public String toString() { return "RegisteredParticipant[id=" + id + ", delegate=" + delegate + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RegisteredParticipant> schema() {
            return new dev.openallay.value.ValueSchema<>(RegisteredParticipant.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RegisteredParticipant>>asList(new dev.openallay.value.ValueSchema.Component<>(RegisteredParticipant.class, "id", RegisteredParticipant::id), new dev.openallay.value.ValueSchema.Component<>(RegisteredParticipant.class, "delegate", RegisteredParticipant::delegate)), arguments -> new RegisteredParticipant((String) arguments[0], (JavascriptInvocationParticipant) arguments[1]));
        }
    }
}

    private static final class DuplicateContribution extends RuntimeException {}
}
