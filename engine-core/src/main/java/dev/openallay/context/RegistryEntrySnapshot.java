package dev.openallay.context;

import com.google.gson.JsonElement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@dev.openallay.value.ValueType(RegistryEntrySnapshot.ValueSchemaProvider.class)
public final class RegistryEntrySnapshot {
    private final String id;
    private final String kind;
    private final String displayName;
    private final String namespace;
    private final String provenance;
    private final List<String> aliases;
    private final Set<String> tags;
    private final Set<String> components;
    private final Map<String, JsonElement> properties;
    public RegistryEntrySnapshot(String id, String kind, String displayName, String namespace, String provenance, List<String> aliases, Set<String> tags, Set<String> components, Map<String, JsonElement> properties) {

        id = ContextValidation.identifier(id, "id");
        kind = ContextValidation.nonBlank(kind, "kind");
        displayName = ContextValidation.nonBlank(displayName, "displayName");
        namespace = ContextValidation.nonBlank(namespace, "namespace");
        provenance = ContextValidation.identifier(provenance, "provenance");
        aliases = dev.openallay.util.Java8Collections.listCopyOf(aliases);
        tags = Collections.unmodifiableSet(new TreeSet<>(tags));
        components = Collections.unmodifiableSet(new TreeSet<>(components));
        TreeMap<String, JsonElement> propertyCopy = new TreeMap<>();
        properties.forEach((key, value) -> propertyCopy.put(
                ContextValidation.identifier(key, "property key"),
                dev.openallay.json.JsonTrees.copy(java.util.Objects.requireNonNull(value, "property value"))));
        properties = Collections.unmodifiableMap(propertyCopy);

        this.id = id;
        this.kind = kind;
        this.displayName = displayName;
        this.namespace = namespace;
        this.provenance = provenance;
        this.aliases = aliases;
        this.tags = tags;
        this.components = components;
        this.properties = properties;
    }
    public String id() { return id; }
    public String kind() { return kind; }
    public String displayName() { return displayName; }
    public String namespace() { return namespace; }
    public String provenance() { return provenance; }
    public List<String> aliases() { return aliases; }
    public Set<String> tags() { return tags; }
    public Set<String> components() { return components; }
public RegistryEntrySnapshot(
            String id, String kind, String displayName, String namespace, String provenance) {
        this(id, kind, displayName, namespace, provenance, dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.mapOf());
    }

    public Map<String, JsonElement> properties() {
        TreeMap<String, JsonElement> copy = new TreeMap<>();
        properties.forEach((key, value) -> copy.put(key, dev.openallay.json.JsonTrees.copy(value)));
        return Collections.unmodifiableMap(copy);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegistryEntrySnapshot)) return false;
        RegistryEntrySnapshot that = (RegistryEntrySnapshot) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(namespace, that.namespace) && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(aliases, that.aliases) && java.util.Objects.equals(tags, that.tags) && java.util.Objects.equals(components, that.components) && java.util.Objects.equals(properties, that.properties);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(namespace);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(aliases);
        hash = 31 * hash + java.util.Objects.hashCode(tags);
        hash = 31 * hash + java.util.Objects.hashCode(components);
        hash = 31 * hash + java.util.Objects.hashCode(properties);
        return hash;
    }
    @Override public String toString() { return "RegistryEntrySnapshot[id=" + id + ", kind=" + kind + ", displayName=" + displayName + ", namespace=" + namespace + ", provenance=" + provenance + ", aliases=" + aliases + ", tags=" + tags + ", components=" + components + ", properties=" + properties + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RegistryEntrySnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(RegistryEntrySnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RegistryEntrySnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "id", RegistryEntrySnapshot::id), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "kind", RegistryEntrySnapshot::kind), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "displayName", RegistryEntrySnapshot::displayName), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "namespace", RegistryEntrySnapshot::namespace), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "provenance", RegistryEntrySnapshot::provenance), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "aliases", RegistryEntrySnapshot::aliases), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "tags", RegistryEntrySnapshot::tags), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "components", RegistryEntrySnapshot::components), new dev.openallay.value.ValueSchema.Component<>(RegistryEntrySnapshot.class, "properties", RegistryEntrySnapshot::properties)), arguments -> new RegistryEntrySnapshot((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (List) arguments[5], (Set) arguments[6], (Set) arguments[7], (Map) arguments[8]));
        }
    }
}
