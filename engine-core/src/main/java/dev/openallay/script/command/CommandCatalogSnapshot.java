package dev.openallay.script.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Detached projection of the complete Brigadier tree visible to one requesting player. */
@dev.openallay.value.ValueType(CommandCatalogSnapshot.ValueSchemaProvider.class)
public final class CommandCatalogSnapshot {
    private final Instant capturedAt;
    private final List<CommandNodeSnapshot> nodes;
    public CommandCatalogSnapshot(Instant capturedAt, List<CommandNodeSnapshot> nodes) {

        Objects.requireNonNull(capturedAt, "capturedAt");
        nodes = dev.openallay.util.Java8Collections.listCopyOf(nodes);

        this.capturedAt = capturedAt;
        this.nodes = nodes;
    }
    public Instant capturedAt() { return capturedAt; }
    public List<CommandNodeSnapshot> nodes() { return nodes; }
public Optional<CommandNodeSnapshot> describe(String path) {
        if (path == null) {
            return Optional.empty();
        }
        String canonical = canonical(path);
        return nodes.stream().filter(node -> node.path().equals(canonical)).findFirst();
    }
@dev.openallay.value.ValueType(CommandNodeSnapshot.ValueSchemaProvider.class)
public static final class CommandNodeSnapshot {
    private final String path;
    private final String name;
    private final String kind;
    private final String argumentType;
    private final boolean executable;
    private final String redirect;
    private final List<String> usage;
    private final List<String> children;
    public CommandNodeSnapshot(String path, String name, String kind, String argumentType, boolean executable, String redirect, List<String> usage, List<String> children) {

            path = require(path, "path");
            name = require(name, "name");
            kind = require(kind, "kind");
            argumentType = argumentType == null ? "" : argumentType;
            redirect = redirect == null ? "" : redirect;
            usage = dev.openallay.util.Java8Collections.listCopyOf(usage);
            children = dev.openallay.util.Java8Collections.listCopyOf(children);

        this.path = path;
        this.name = name;
        this.kind = kind;
        this.argumentType = argumentType;
        this.executable = executable;
        this.redirect = redirect;
        this.usage = usage;
        this.children = children;
    }
    public String path() { return path; }
    public String name() { return name; }
    public String kind() { return kind; }
    public String argumentType() { return argumentType; }
    public boolean executable() { return executable; }
    public String redirect() { return redirect; }
    public List<String> usage() { return usage; }
    public List<String> children() { return children; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommandNodeSnapshot)) return false;
        CommandNodeSnapshot that = (CommandNodeSnapshot) other;
        return java.util.Objects.equals(path, that.path) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(argumentType, that.argumentType) && executable == that.executable && java.util.Objects.equals(redirect, that.redirect) && java.util.Objects.equals(usage, that.usage) && java.util.Objects.equals(children, that.children);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(path);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(argumentType);
        hash = 31 * hash + Boolean.hashCode(executable);
        hash = 31 * hash + java.util.Objects.hashCode(redirect);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        return hash;
    }
    @Override public String toString() { return "CommandNodeSnapshot[path=" + path + ", name=" + name + ", kind=" + kind + ", argumentType=" + argumentType + ", executable=" + executable + ", redirect=" + redirect + ", usage=" + usage + ", children=" + children + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CommandNodeSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(CommandNodeSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CommandNodeSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "path", CommandNodeSnapshot::path), new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "name", CommandNodeSnapshot::name), new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "kind", CommandNodeSnapshot::kind), new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "argumentType", CommandNodeSnapshot::argumentType), new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "executable", CommandNodeSnapshot::executable), new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "redirect", CommandNodeSnapshot::redirect), new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "usage", CommandNodeSnapshot::usage), new dev.openallay.value.ValueSchema.Component<>(CommandNodeSnapshot.class, "children", CommandNodeSnapshot::children)), arguments -> new CommandNodeSnapshot((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (Boolean) arguments[4], (String) arguments[5], (List) arguments[6], (List) arguments[7]));
        }
    }
}
static String canonical(String value) {
        String result = dev.openallay.util.Java8Strings.strip(value);
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result.replaceAll("\\s+", " ");
    }
private static String require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommandCatalogSnapshot)) return false;
        CommandCatalogSnapshot that = (CommandCatalogSnapshot) other;
        return java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(nodes, that.nodes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(nodes);
        return hash;
    }
    @Override public String toString() { return "CommandCatalogSnapshot[capturedAt=" + capturedAt + ", nodes=" + nodes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CommandCatalogSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(CommandCatalogSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CommandCatalogSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(CommandCatalogSnapshot.class, "capturedAt", CommandCatalogSnapshot::capturedAt), new dev.openallay.value.ValueSchema.Component<>(CommandCatalogSnapshot.class, "nodes", CommandCatalogSnapshot::nodes)), arguments -> new CommandCatalogSnapshot((Instant) arguments[0], (List) arguments[1]));
        }
    }
}
