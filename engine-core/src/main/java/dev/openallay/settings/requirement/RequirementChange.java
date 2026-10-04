package dev.openallay.settings.requirement;

import dev.openallay.requirement.RequirementKind;
import java.util.Objects;

/** One exact, user-confirmed setting change; never a dependency closure. */
public record RequirementChange(
        RequirementKind kind, String id, boolean unrestrictedConsentRequired) {
    public RequirementChange {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }
}
