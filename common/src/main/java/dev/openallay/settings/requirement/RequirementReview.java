package dev.openallay.settings.requirement;

import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementReport;
import java.util.List;
import java.util.Objects;

/** Immutable advisory review of the exact privately prepared package. */
public record RequirementReview(
        Token token,
        RequirementKind kind,
        String id,
        String name,
        String version,
        String sha256,
        RequirementReport report,
        List<RequirementChange> changes,
        boolean catalogRequirementsDiffer) {
    public RequirementReview {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(report, "report");
        changes = List.copyOf(changes);
    }

    public RequirementReview(Token token, RequirementKind kind, String id, String name,
            String version, String sha256, RequirementReport report, List<RequirementChange> changes) {
        this(token, kind, id, name, version, sha256, report, changes, false);
    }

    /** Identity-based, single-review token. Its contents do not authorize runtime capabilities. */
    public static final class Token {
        public Token() {}
    }
}
