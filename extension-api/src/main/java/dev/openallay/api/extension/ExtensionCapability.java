package dev.openallay.api.extension;

import java.util.Objects;

/** An Extension-owned operation scope. Registration never grants this authority to an Agent. */
public final class ExtensionCapability {
    private final String id;
    private final String name;
    private final String description;

    public ExtensionCapability(String id, String name, String description) {
        this.id = ApiValidation.id(id, "capability ID");
        this.name = ApiValidation.text(name, "name");
        this.description = ApiValidation.text(description, "description");
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionCapability)) return false;
        ExtensionCapability that = (ExtensionCapability) other;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description);
    }
    @Override public int hashCode() { return Objects.hash(id, name, description); }
}
