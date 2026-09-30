package dev.openallay.requirement;

import java.util.Objects;

/** One immutable, caller-projected current settings/catalog state. */
public record RequirementAvailability(String name, RequirementStatus status, String detail) {
    public RequirementAvailability {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Requirement name must not be blank");
        }
        Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNullElse(detail, "");
    }
}
