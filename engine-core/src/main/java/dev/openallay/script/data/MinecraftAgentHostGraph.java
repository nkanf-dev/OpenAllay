package dev.openallay.script.data;

import dev.openallay.context.CallerSnapshot;
import dev.openallay.context.ContextMetrics;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.SourceObservation;
import dev.openallay.context.SourceObservationCollector;
import dev.openallay.context.PlayerSnapshot;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeSnapshot;
import dev.openallay.context.RegistryEntrySnapshot;
import dev.openallay.context.RegistrySnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.context.game.ObservableGameStateSnapshot;
import dev.openallay.knowledge.KnowledgeDocument;
import dev.openallay.knowledge.KnowledgeSnapshot;
import dev.openallay.recipe.RecipeCatalogDiagnostic;
import dev.openallay.recipe.RecipeProviderStatus;
import dev.openallay.recipe.RecipeSemanticGroup;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.schema.DeclaredHostRoots;
import dev.openallay.script.schema.HostRootDescriptor;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.script.schema.HostSchemaCatalog;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Request-scoped root graph over the original detached Java snapshots.
 *
 * <p>Every root has a declared descriptor. Schema discovery reads only those descriptors and
 * never invokes a root supplier. Values are resolved only when read and retained for the request.
 */
public final class MinecraftAgentHostGraph {
    private static final String CORE_PROVIDER = "openallay:core";

    private final Map<String, HostRootDescriptor> roots;
    private final HostSchemaCatalog schemaCatalog;
    private final Optional<PlayerSnapshot> playerSnapshot;
    private final Optional<RegistrySnapshot> registrySnapshot;
    private final Optional<RecipeSnapshot> recipeSnapshot;
    private final Optional<ObservableGameStateSnapshot> gameSnapshot;
    private final MemoizedSupplier knowledgeSnapshot;
    private final MemoizedSupplier extensionSnapshot;

    public MinecraftAgentHostGraph(ToolInvocationContext context) {
        this(context, KnowledgeSnapshot::empty, new JavascriptDataModuleRegistry());
    }

    public MinecraftAgentHostGraph(
            ToolInvocationContext context, Supplier<KnowledgeSnapshot> knowledge) {
        this(context, knowledge, new JavascriptDataModuleRegistry());
    }

    /**
     * Builds the exact descriptor-only catalog for an already detached request context.
     *
     * <p>This does not resolve knowledge, extension, or root suppliers. Settings may consume it
     * when they have a current detached context; without one they should show declarations as
     * request-scoped rather than manufacturing availability.
     */
    public static HostSchemaCatalog describeRequest(
            ToolInvocationContext context, JavascriptDataModuleRegistry extensions) {
        return new MinecraftAgentHostGraph(
                        context, KnowledgeSnapshot::empty, extensions)
                .schemaCatalog();
    }

    /**
     * Returns the core declared surface for settings when no request snapshot exists.
     *
     * <p>Every availability is {@code REQUEST_SCOPED}; the caller must not render that state as a
     * captured failure. Registered extension rows are obtained separately from
     * {@link JavascriptDataModuleRegistry#descriptors()}.
     */
    public static HostSchemaCatalog declaredOnlyCatalog() {
        ArrayList<HostRootDescriptor> declared = new ArrayList<>();
        declared.add(requestScoped(
                "caller", "caller", "Request caller", "caller"));
        declared.add(requestScoped(
                "metrics", "metrics", "Captured request metrics", "context"));
        declared.add(requestScoped(
                "capturedAt", "capturedAt", "Request capture time", "context"));
        declared.add(requestScoped(
                "player", "player", "Current player and inventory snapshot", "player"));
        declared.add(requestScoped(
                "registries", "registries", "Registry catalog metadata", "registries"));
        declared.add(requestScoped(
                "registryEntries",
                "registryEntries",
                "All captured registry rows across kinds",
                "registries"));
        for (String name : List.of(
                "items", "blocks", "fluids", "effects", "enchantments", "entities")) {
            declared.add(requestScoped(
                    name,
                    "registryEntries",
                    "Captured " + name + " registry rows",
                    "registries"));
        }
        declared.add(requestScoped(
                "recipeCatalog",
                "recipeCatalog",
                "Recipe providers, semantic groups, diagnostics, and evidence",
                "recipes"));
        declared.add(requestScoped(
                "recipes", "recipes", "All captured normalized recipes", "recipes"));
        declared.add(requestScoped(
                "game",
                "game",
                "Exact player-visible runtime, mod, option, pack, shader, and F3 state",
                "game"));
        declared.add(requestScoped(
                "knowledge",
                "knowledge",
                "Captured guide and knowledge documents",
                "knowledge"));
        declared.add(requestScoped(
                "knowledgeCatalog",
                "knowledgeCatalog",
                "Knowledge source counts, capture time, and evidence",
                "knowledge"));
        declared.add(HostRootDescriptor.requestScopedDynamic(
                "extensions",
                new HostSchema.Dictionary(
                        "map", new HostSchema.DynamicDetached("declared-extension"), true),
                "openallay:extensions",
                "Detached values contributed by registered extension adapters",
                "extensions"));
        declared.add(requestScoped(
                "extensionCatalog",
                "extensionCatalog",
                "Registered extension IDs, providers, availability, and declared schemas",
                "extension_catalog"));
        declared.add(requestScoped(
                "extensionDiagnostics",
                "extensionDiagnostics",
                "Isolated extension declaration and capture diagnostics",
                "extension_diagnostics"));
        declared.add(requestScoped(
                "capabilities",
                "capabilities",
                "Currently available declared host roots",
                "catalog"));
        return new HostSchemaCatalog(declared);
    }

    public MinecraftAgentHostGraph(
            ToolInvocationContext context,
            Supplier<KnowledgeSnapshot> knowledge,
            JavascriptDataModuleRegistry extensions) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(extensions, "extensions");

        knowledgeSnapshot = new MemoizedSupplier(() ->
                Objects.requireNonNull(knowledge.get(), "knowledge snapshot"));
        extensionSnapshot = new MemoizedSupplier(() -> extensions.capture(context));
        LinkedHashMap<String, HostRootDescriptor> declared = new LinkedHashMap<>();
        add(declared, descriptor(
                "caller", type("caller"), true, "Request caller", "context",
                context::caller));
        add(declared, descriptor(
                "metrics", type("metrics"), true, "Captured request metrics", "context",
                context::metrics));
        add(declared, descriptor(
                "capturedAt", type("capturedAt"), true, "Request capture time", "context",
                context::capturedAt));

        playerSnapshot = context.player();
        add(declared, descriptor(
                "player",
                type("player"),
                playerSnapshot.isPresent(),
                "Current player and inventory snapshot",
                "player",
                playerSnapshot::orElseThrow));

        registrySnapshot = context.registries();
        add(declared, descriptor(
                "registries",
                type("registries"),
                registrySnapshot.isPresent(),
                "Registry catalog metadata",
                "registries",
                () -> registryCatalog(registrySnapshot.orElseThrow())));
        add(declared, descriptor(
                "registryEntries",
                type("registryEntries"),
                registrySnapshot.isPresent(),
                "All captured registry rows across kinds",
                "registries",
                () -> registrySnapshot.orElseThrow().entries()));
        for (String name : List.of(
                "items", "blocks", "fluids", "effects", "enchantments", "entities")) {
            add(declared, descriptor(
                    name,
                    type("registryEntries"),
                    registrySnapshot.isPresent(),
                    "Captured " + name + " registry rows",
                    "registries",
                    () -> groupedRegistryRows(registrySnapshot.orElseThrow(), name)));
        }
        registrySnapshot.ifPresent(value -> {
            Map<String, List<RegistryEntrySnapshot>> grouped = group(value.entries());
            grouped.forEach((name, rows) -> {
                if (!declared.containsKey(name)) {
                    add(declared, descriptor(
                            name,
                            type("registryEntries"),
                            true,
                            "Captured " + name + " registry rows",
                            "registries",
                            () -> rows));
                }
            });
        });

        recipeSnapshot = context.recipes();
        add(declared, descriptor(
                "recipeCatalog",
                type("recipeCatalog"),
                recipeSnapshot.isPresent(),
                "Recipe providers, semantic groups, diagnostics, and evidence",
                "recipes",
                () -> recipeCatalog(recipeSnapshot.orElseThrow())));
        add(declared, descriptor(
                "recipes",
                type("recipes"),
                recipeSnapshot.isPresent(),
                "All captured normalized recipes",
                "recipes",
                () -> recipeSnapshot.orElseThrow().recipes()));

        gameSnapshot = context.observableGameState();
        add(declared, descriptor(
                "game",
                type("game"),
                gameSnapshot.isPresent(),
                "Exact player-visible runtime, mod, option, pack, shader, and F3 state",
                "game",
                gameSnapshot::orElseThrow));


        add(declared, descriptor(
                "knowledge",
                type("knowledge"),
                true,
                "Captured guide and knowledge documents",
                "knowledge",
                () -> knowledgeSnapshot(KnowledgeSnapshot.class, knowledgeSnapshot).documents()));
        add(declared, descriptor(
                "knowledgeCatalog",
                type("knowledgeCatalog"),
                true,
                "Knowledge source counts, capture time, and evidence",
                "knowledge",
                () -> knowledgeCatalog(knowledgeSnapshot(KnowledgeSnapshot.class, knowledgeSnapshot))));

        HostSchema extensionValuesSchema = new HostSchema.Dictionary(
                "map", new HostSchema.DynamicDetached("declared-extension"), true);
        add(declared, HostRootDescriptor.declaredDynamic(
                "extensions",
                extensionValuesSchema,
                true,
                "openallay:extensions",
                "Detached values contributed by registered extension adapters",
                "extensions",
                () -> extensionSnapshot(
                                JavascriptDataModuleRegistry.Snapshot.class, extensionSnapshot)
                        .values()));
        add(declared, descriptor(
                "extensionCatalog",
                type("extensionCatalog"),
                true,
                "Registered extension IDs, providers, availability, and declared schemas",
                "extension_catalog",
                extensions::descriptors));
        add(declared, descriptor(
                "extensionDiagnostics",
                type("extensionDiagnostics"),
                true,
                "Isolated extension declaration and capture diagnostics",
                "extension_diagnostics",
                () -> extensionSnapshot(
                                JavascriptDataModuleRegistry.Snapshot.class, extensionSnapshot)
                        .diagnostics()));

        add(declared, descriptor(
                "capabilities",
                type("capabilities"),
                true,
                "Currently available declared host roots",
                "catalog",
                () -> declared.values().stream()
                        .filter(HostRootDescriptor::available)
                        .map(root -> new Capability(
                                root.name(),
                                root.providerId(),
                                root.schema().kind(),
                                root.evidenceOwner()))
                        .toList()));
        roots = Collections.unmodifiableMap(declared);
        schemaCatalog = new HostSchemaCatalog(roots.values());
    }

    /** Opens an invocation-local view over all declared detached data, resolving only actual reads. */
    public InvocationData open() {
        SourceObservationCollector sources = new SourceObservationCollector();
        return new InvocationData(
                new LazyRootMap(roots, List.copyOf(roots.keySet()), schemaCatalog,
                        evidenceOwners(), sources), sources, schemaCatalog);
    }

    public HostSchemaCatalog schemaCatalog() {
        return schemaCatalog;
    }

    private Map<String, Supplier<List<SourceObservation>>> evidenceOwners() {
        Map<String, Supplier<List<SourceObservation>>> owners = new LinkedHashMap<>();
        owners.put("player", () -> playerSnapshot.map(value -> sourceSummaries(
                        java.util.stream.Stream.of(value.evidence(), value.inventory().evidence())))
                .orElseGet(List::of));
        owners.put("registries", () -> registrySnapshot.map(value ->
                        List.of(new SourceObservation(value.evidence())))
                .orElseGet(List::of));
        owners.put("recipes", () -> recipeSnapshot.map(value -> sourceSummaries(
                        java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(value.evidence()),
                                java.util.stream.Stream.concat(
                                        value.recipes().stream().map(RecipeEntrySnapshot::evidence),
                                        value.groups().stream().flatMap(group -> group.evidence().stream())))))
                .orElseGet(List::of));
        owners.put("game", () -> gameSnapshot.map(value -> sourceSummaries(gameEvidence(value).stream()))
                .orElseGet(List::of));
        owners.put("knowledge", () -> {
            KnowledgeSnapshot snapshot = knowledgeSnapshot(KnowledgeSnapshot.class, knowledgeSnapshot);
            return sourceSummaries(java.util.stream.Stream.concat(
                    snapshot.evidence().stream(), snapshot.documents().stream().map(KnowledgeDocument::evidence)));
        });
        owners.put("extensions", () -> sourceSummaries(extensionSnapshot(
                        JavascriptDataModuleRegistry.Snapshot.class, extensionSnapshot).evidence().stream()));
        return Map.copyOf(owners);
    }

    /** Retains distinct real origins without constructing a second per-row metadata collection. */
    private static List<SourceObservation> sourceSummaries(java.util.stream.Stream<EvidenceMetadata> evidence) {
        SourceObservationCollector sources = new SourceObservationCollector();
        evidence.forEach(sources::add);
        return sources.snapshot();
    }

    private static List<EvidenceMetadata> gameEvidence(ObservableGameStateSnapshot game) {
        ArrayList<EvidenceMetadata> values = new ArrayList<>();
        values.add(game.runtime().evidence());
        values.add(game.mods().evidence());
        values.add(game.options().evidence());
        values.add(game.packs().evidence());
        values.add(game.shaders().evidence());
        values.add(game.diagnostics().evidence());
        values.add(game.player().evidence());
        values.add(game.worldQueries().evidence());
        if (game.player().player() != null) {
            values.add(game.player().player().evidence());
            values.add(game.player().player().inventory().evidence());
        }
        return distinct(values);
    }

    private static List<EvidenceMetadata> distinct(Collection<EvidenceMetadata> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    public static final class InvocationData extends AbstractMap<String, Object>
            implements DeclaredHostRoots {
        private final Map<String, Object> roots;
        private final SourceObservationCollector sources;
        private final HostSchemaCatalog schemaCatalog;

        private InvocationData(
                Map<String, Object> roots,
                SourceObservationCollector sources,
                HostSchemaCatalog schemaCatalog) {
            this.roots = roots;
            this.sources = sources;
            this.schemaCatalog = schemaCatalog;
        }

        @Override public HostSchemaCatalog schemaCatalog() { return schemaCatalog; }
        /** Read origins, not a claim that every observed field affected the return value. */
        public List<SourceObservation> sources() { return sources.snapshot(); }
        public void recordEvidence(EvidenceMetadata addition) { sources.add(addition); }
        public void recordSource(SourceObservation addition) { sources.add(addition); }
        @Override public Set<Entry<String, Object>> entrySet() { return roots.entrySet(); }
        @Override public Object get(Object key) { return roots.get(key); }
        @Override public boolean containsKey(Object key) { return roots.containsKey(key); }
    }

    private static HostRootDescriptor descriptor(
            String name,
            Type type,
            boolean available,
            String summary,
            String evidenceOwner,
            Supplier<?> supplier) {
        return new HostRootDescriptor(
                name,
                type,
                available,
                CORE_PROVIDER,
                summary,
                evidenceOwner,
                available ? new MemoizedSupplier(supplier) : null);
    }

    private static HostRootDescriptor requestScoped(
            String name, String declaredType, String summary, String evidenceOwner) {
        return HostRootDescriptor.requestScoped(
                name,
                type(declaredType),
                CORE_PROVIDER,
                summary,
                evidenceOwner);
    }

    private static void add(
            Map<String, HostRootDescriptor> roots, HostRootDescriptor descriptor) {
        roots.put(descriptor.name(), descriptor);
    }

    private static RegistryCatalog registryCatalog(RegistrySnapshot snapshot) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        snapshot.entries().forEach(entry -> counts.merge(entry.kind(), 1, Integer::sum));
        return new RegistryCatalog(snapshot.evidence(), snapshot.entries().size(), counts);
    }

    private static List<RegistryEntrySnapshot> groupedRegistryRows(
            RegistrySnapshot snapshot, String name) {
        return group(snapshot.entries()).getOrDefault(name, List.of());
    }

    private static RecipeCatalogView recipeCatalog(RecipeSnapshot snapshot) {
        return new RecipeCatalogView(
                snapshot.evidence(),
                snapshot.recipes().size(),
                snapshot.providers().stream().map(RecipeProviderStatus::from).toList(),
                snapshot.groups(),
                snapshot.diagnostics());
    }

    private static KnowledgeCatalog knowledgeCatalog(KnowledgeSnapshot snapshot) {
        TreeMap<String, Integer> sources = new TreeMap<>();
        snapshot.documents().forEach(document -> sources.merge(document.sourceId(), 1, Integer::sum));
        return new KnowledgeCatalog(
                snapshot.createdAt(),
                snapshot.documents().size(),
                sources,
                snapshot.evidence());
    }

    private static Map<String, List<RegistryEntrySnapshot>> group(
            List<RegistryEntrySnapshot> entries) {
        LinkedHashMap<String, ArrayList<RegistryEntrySnapshot>> mutable = new LinkedHashMap<>();
        for (RegistryEntrySnapshot entry : entries) {
            mutable.computeIfAbsent(
                    pluralize(entry.kind().toLowerCase(Locale.ROOT)),
                    ignored -> new ArrayList<>()).add(entry);
        }
        LinkedHashMap<String, List<RegistryEntrySnapshot>> result = new LinkedHashMap<>();
        mutable.forEach((name, values) -> result.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(result);
    }

    private static String pluralize(String kind) {
        return switch (kind) {
            case "item" -> "items";
            case "block" -> "blocks";
            case "fluid" -> "fluids";
            case "effect", "mob_effect" -> "effects";
            case "enchantment" -> "enchantments";
            case "entity", "entity_type" -> "entities";
            default -> kind.endsWith("s") ? kind : kind + "s";
        };
    }

    private static Type type(String name) {
        for (RecordComponent component : DeclaredTypes.class.getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component.getGenericType();
            }
        }
        throw new IllegalArgumentException("Unknown declared host type: " + name);
    }

    private static <T> T knowledgeSnapshot(Class<T> type, MemoizedSupplier supplier) {
        return type.cast(supplier.get());
    }

    private static <T> T extensionSnapshot(Class<T> type, MemoizedSupplier supplier) {
        return type.cast(supplier.get());
    }

    public record RegistryCatalog(
            EvidenceMetadata evidence, int entryCount, Map<String, Integer> kinds) {
        public RegistryCatalog {
            kinds = Map.copyOf(kinds);
        }
    }

    public record RecipeCatalogView(
            EvidenceMetadata evidence,
            int recipeCount,
            List<RecipeProviderStatus> providers,
            List<RecipeSemanticGroup> groups,
            List<RecipeCatalogDiagnostic> diagnostics) {
        public RecipeCatalogView {
            providers = List.copyOf(providers);
            groups = List.copyOf(groups);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record KnowledgeCatalog(
            Instant createdAt,
            int documentCount,
            Map<String, Integer> sources,
            List<EvidenceMetadata> evidence) {
        public KnowledgeCatalog {
            sources = Map.copyOf(sources);
            evidence = List.copyOf(evidence);
        }
    }

    public record Capability(
            String root, String provider, String schemaKind, String evidenceOwner) {}

    private record DeclaredTypes(
            CallerSnapshot caller,
            ContextMetrics metrics,
            Instant capturedAt,
            PlayerSnapshot player,
            RegistryCatalog registries,
            List<RegistryEntrySnapshot> registryEntries,
            RecipeCatalogView recipeCatalog,
            List<RecipeEntrySnapshot> recipes,
            ObservableGameStateSnapshot game,
            List<KnowledgeDocument> knowledge,
            KnowledgeCatalog knowledgeCatalog,
            List<JavascriptDataModuleRegistry.Descriptor> extensionCatalog,
            List<JavascriptDataModuleRegistry.Diagnostic> extensionDiagnostics,
            List<Capability> capabilities) {}

    private static final class MemoizedSupplier implements Supplier<Object> {
        private Supplier<?> source;
        private Object value;
        private boolean resolved;

        private MemoizedSupplier(Supplier<?> source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override
        public synchronized Object get() {
            if (!resolved) {
                value = Objects.requireNonNull(source.get(), "host root value");
                source = null;
                resolved = true;
            }
            return value;
        }
    }

    private static final class LazyRootMap extends AbstractMap<String, Object>
            implements DeclaredHostRoots {
        private final Map<String, HostRootDescriptor> roots;
        private final List<String> names;
        private final HostSchemaCatalog schemaCatalog;
        private final Map<String, Supplier<List<SourceObservation>>> evidenceOwners;
        private final SourceObservationCollector sources;
        private final Set<String> observedOwners = new java.util.HashSet<>();
        private final Set<Entry<String, Object>> entries;

        private LazyRootMap(
                Map<String, HostRootDescriptor> roots,
                List<String> names,
                HostSchemaCatalog schemaCatalog,
                Map<String, Supplier<List<SourceObservation>>> evidenceOwners,
                SourceObservationCollector sources) {
            this.roots = roots;
            this.names = names;
            this.schemaCatalog = schemaCatalog;
            this.evidenceOwners = evidenceOwners;
            this.sources = sources;
            entries = new AbstractSet<>() {
                @Override
                public Iterator<Entry<String, Object>> iterator() {
                    return names.stream()
                            .<Entry<String, Object>>map(name -> new Entry<>() {
                                @Override public String getKey() { return name; }
                                @Override public Object getValue() { return resolve(name); }
                                @Override public Object setValue(Object value) {
                                    throw new UnsupportedOperationException("read-only root graph");
                                }
                            })
                            .iterator();
                }

                @Override public int size() { return names.size(); }
            };
        }

        private Object resolve(String name) {
            HostRootDescriptor descriptor = roots.get(name);
            if (!descriptor.available()) {
                throw new JavascriptExecutionException(
                        "javascript_root_unavailable",
                        "Minecraft data mc." + name + " was not captured for this request. "
                                + "Unavailable data is not an empty dataset. "
                                + "Use schema.list() to inspect current availability.");
            }
            Object value = descriptor.resolve();
            if (observedOwners.add(descriptor.evidenceOwner())) {
                Supplier<List<SourceObservation>> owner = evidenceOwners.get(descriptor.evidenceOwner());
                if (owner != null) sources.addAll(owner.get());
            }
            return value;
        }

        @Override
        public HostSchemaCatalog schemaCatalog() { return schemaCatalog; }

        @Override
        public boolean containsKey(Object key) {
            return key instanceof String name && names.contains(name);
        }

        @Override
        public Object get(Object key) {
            return containsKey(key) ? resolve((String) key) : null;
        }

        @Override
        public Set<String> keySet() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(names));
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return entries;
        }
    }
}
