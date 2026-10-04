package dev.openallay.settings.skill;

import dev.openallay.community.CommunityCatalogManifest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable settings-backend projection for the public Skill catalog. */
public record SkillCommunityView(
        boolean available,
        Optional<Instant> generatedAt,
        List<Package> packages,
        Optional<Notice> notice) {
    public SkillCommunityView {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        packages = List.copyOf(packages).stream()
                .sorted(Comparator.comparing(Package::id))
                .toList();
        notice = Objects.requireNonNull(notice, "notice");
    }

    public static SkillCommunityView unavailable() {
        return new SkillCommunityView(false, Optional.empty(), List.of(), Optional.empty());
    }

    public record Package(
            String id,
            String displayName,
            String description,
            String publisher,
            String availableVersion,
            boolean installed,
            boolean updateAvailable,
            boolean compatible,
            String source,
            String archive,
            String sha256) {
        public Package {
            require(id, "id");
            require(displayName, "displayName");
            require(description, "description");
            require(publisher, "publisher");
            require(availableVersion, "availableVersion");
            require(source, "source");
            require(archive, "archive");
            require(sha256, "sha256");
        }

        static Package from(
                CommunityCatalogManifest.PackageEntry entry,
                boolean installed,
                Optional<String> installedVersion,
                boolean compatible) {
            return new Package(
                    entry.id(),
                    entry.displayName(),
                    entry.description(),
                    entry.publisher(),
                    entry.version(),
                    installed,
                    installed
                            && (installedVersion.isEmpty()
                                    || !installedVersion.orElseThrow().equals(entry.version())),
                    compatible,
                    entry.source().toString(),
                    entry.archive().toString(),
                    entry.sha256());
        }
    }

    public record Notice(String code, String message) {
        public Notice {
            require(code, "code");
            require(message, "message");
        }
    }

    private static void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
