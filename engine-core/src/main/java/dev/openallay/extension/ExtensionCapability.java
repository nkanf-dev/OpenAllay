package dev.openallay.extension;

import java.util.Objects;

/** An Extension-owned operation scope. Registration never grants this authority to an Agent. */
public record ExtensionCapability(String id, String name, String description) {
    public ExtensionCapability {
        id = requireIdentity(id);
        name = requireText(name, "name");
        description = requireText(description, "description");
    }

    static String requireIdentity(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid Extension capability or binding ID");
        }
        return id;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
}
