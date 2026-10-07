package dev.openallay.platform;

import java.util.List;
import java.util.Map;

/** Loader-neutral public metadata detached from the loader before Agent work starts. */
@dev.openallay.value.ValueType(InstalledModMetadata.ValueSchemaProvider.class)
public final class InstalledModMetadata {
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> authors;
    private final List<String> licenses;
    private final Map<String, String> contacts;
    private final String environment;
    private final List<String> dependencies;
    public InstalledModMetadata(String id, String name, String version, String description, List<String> authors, List<String> licenses, Map<String, String> contacts, String environment, List<String> dependencies) {

        id = require(id, "id");
        name = require(name, "name");
        version = require(version, "version");
        description = description == null ? "" : description;
        authors = dev.openallay.util.Java8Collections.listCopyOf(authors);
        licenses = dev.openallay.util.Java8Collections.listCopyOf(licenses);
        contacts = dev.openallay.util.Java8Collections.mapCopyOf(contacts);
        environment = require(environment, "environment");
        dependencies = dev.openallay.util.Java8Collections.listCopyOf(dependencies);

        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.authors = authors;
        this.licenses = licenses;
        this.contacts = contacts;
        this.environment = environment;
        this.dependencies = dependencies;
    }
    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public String description() { return description; }
    public List<String> authors() { return authors; }
    public List<String> licenses() { return licenses; }
    public Map<String, String> contacts() { return contacts; }
    public String environment() { return environment; }
    public List<String> dependencies() { return dependencies; }
private static String require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof InstalledModMetadata)) return false;
        InstalledModMetadata that = (InstalledModMetadata) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(authors, that.authors) && java.util.Objects.equals(licenses, that.licenses) && java.util.Objects.equals(contacts, that.contacts) && java.util.Objects.equals(environment, that.environment) && java.util.Objects.equals(dependencies, that.dependencies);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(authors);
        hash = 31 * hash + java.util.Objects.hashCode(licenses);
        hash = 31 * hash + java.util.Objects.hashCode(contacts);
        hash = 31 * hash + java.util.Objects.hashCode(environment);
        hash = 31 * hash + java.util.Objects.hashCode(dependencies);
        return hash;
    }
    @Override public String toString() { return "InstalledModMetadata[id=" + id + ", name=" + name + ", version=" + version + ", description=" + description + ", authors=" + authors + ", licenses=" + licenses + ", contacts=" + contacts + ", environment=" + environment + ", dependencies=" + dependencies + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<InstalledModMetadata> schema() {
            return new dev.openallay.value.ValueSchema<>(InstalledModMetadata.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<InstalledModMetadata>>asList(new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "id", InstalledModMetadata::id), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "name", InstalledModMetadata::name), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "version", InstalledModMetadata::version), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "description", InstalledModMetadata::description), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "authors", InstalledModMetadata::authors), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "licenses", InstalledModMetadata::licenses), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "contacts", InstalledModMetadata::contacts), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "environment", InstalledModMetadata::environment), new dev.openallay.value.ValueSchema.Component<>(InstalledModMetadata.class, "dependencies", InstalledModMetadata::dependencies)), arguments -> new InstalledModMetadata((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (List) arguments[4], (List) arguments[5], (Map) arguments[6], (String) arguments[7], (List) arguments[8]));
        }
    }
}
