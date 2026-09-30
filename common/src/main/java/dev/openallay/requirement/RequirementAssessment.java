package dev.openallay.requirement;

import java.util.Objects;

public record RequirementAssessment(
        RequirementKind kind, String id, String name, RequirementStatus status, String detail) {
    public RequirementAssessment {
        Objects.requireNonNull(kind, "kind");
        RequirementSet.requireId(id, kind);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Requirement name must not be blank");
        }
        Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNullElse(detail, "");
    }
}
