package dev.openallay.settings.skill;

import dev.openallay.community.CommunityCatalogManifest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable settings-backend projection for the public Skill catalog. */
@dev.openallay.value.ValueType(SkillCommunityView.ValueSchemaProvider.class)
public final class SkillCommunityView {
    private final boolean available;
    private final Optional<Instant> generatedAt;
    private final List<Package> packages;
    private final Optional<Notice> notice;
    public SkillCommunityView(boolean available, Optional<Instant> generatedAt, List<Package> packages, Optional<Notice> notice) {

        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        packages = dev.openallay.util.Java8Collections.toList(dev.openallay.util.Java8Collections.listCopyOf(packages).stream()
                .sorted(Comparator.comparing(Package::id)));
        notice = Objects.requireNonNull(notice, "notice");

        this.available = available;
        this.generatedAt = generatedAt;
        this.packages = packages;
        this.notice = notice;
    }
    public boolean available() { return available; }
    public Optional<Instant> generatedAt() { return generatedAt; }
    public List<Package> packages() { return packages; }
    public Optional<Notice> notice() { return notice; }
public static SkillCommunityView unavailable() {
        return new SkillCommunityView(false, Optional.empty(), dev.openallay.util.Java8Collections.listOf(), Optional.empty());
    }
@dev.openallay.value.ValueType(Package.ValueSchemaProvider.class)
public static final class Package {
    private final String id;
    private final String displayName;
    private final String description;
    private final String publisher;
    private final String availableVersion;
    private final boolean installed;
    private final boolean updateAvailable;
    private final boolean compatible;
    private final String source;
    private final String archive;
    private final String sha256;
    public Package(String id, String displayName, String description, String publisher, String availableVersion, boolean installed, boolean updateAvailable, boolean compatible, String source, String archive, String sha256) {

            require(id, "id");
            require(displayName, "displayName");
            require(description, "description");
            require(publisher, "publisher");
            require(availableVersion, "availableVersion");
            require(source, "source");
            require(archive, "archive");
            require(sha256, "sha256");

        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.publisher = publisher;
        this.availableVersion = availableVersion;
        this.installed = installed;
        this.updateAvailable = updateAvailable;
        this.compatible = compatible;
        this.source = source;
        this.archive = archive;
        this.sha256 = sha256;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String publisher() { return publisher; }
    public String availableVersion() { return availableVersion; }
    public boolean installed() { return installed; }
    public boolean updateAvailable() { return updateAvailable; }
    public boolean compatible() { return compatible; }
    public String source() { return source; }
    public String archive() { return archive; }
    public String sha256() { return sha256; }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Package)) return false;
        Package that = (Package) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(publisher, that.publisher) && java.util.Objects.equals(availableVersion, that.availableVersion) && installed == that.installed && updateAvailable == that.updateAvailable && compatible == that.compatible && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(archive, that.archive) && java.util.Objects.equals(sha256, that.sha256);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(publisher);
        hash = 31 * hash + java.util.Objects.hashCode(availableVersion);
        hash = 31 * hash + Boolean.hashCode(installed);
        hash = 31 * hash + Boolean.hashCode(updateAvailable);
        hash = 31 * hash + Boolean.hashCode(compatible);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(archive);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        return hash;
    }
    @Override public String toString() { return "Package[id=" + id + ", displayName=" + displayName + ", description=" + description + ", publisher=" + publisher + ", availableVersion=" + availableVersion + ", installed=" + installed + ", updateAvailable=" + updateAvailable + ", compatible=" + compatible + ", source=" + source + ", archive=" + archive + ", sha256=" + sha256 + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Package> schema() {
            return new dev.openallay.value.ValueSchema<>(Package.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Package>>asList(new dev.openallay.value.ValueSchema.Component<>(Package.class, "id", Package::id), new dev.openallay.value.ValueSchema.Component<>(Package.class, "displayName", Package::displayName), new dev.openallay.value.ValueSchema.Component<>(Package.class, "description", Package::description), new dev.openallay.value.ValueSchema.Component<>(Package.class, "publisher", Package::publisher), new dev.openallay.value.ValueSchema.Component<>(Package.class, "availableVersion", Package::availableVersion), new dev.openallay.value.ValueSchema.Component<>(Package.class, "installed", Package::installed), new dev.openallay.value.ValueSchema.Component<>(Package.class, "updateAvailable", Package::updateAvailable), new dev.openallay.value.ValueSchema.Component<>(Package.class, "compatible", Package::compatible), new dev.openallay.value.ValueSchema.Component<>(Package.class, "source", Package::source), new dev.openallay.value.ValueSchema.Component<>(Package.class, "archive", Package::archive), new dev.openallay.value.ValueSchema.Component<>(Package.class, "sha256", Package::sha256)), arguments -> new Package((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (String) arguments[8], (String) arguments[9], (String) arguments[10]));
        }
    }
}
@dev.openallay.value.ValueType(Notice.ValueSchemaProvider.class)
public static final class Notice {
    private final String code;
    private final String message;
    public Notice(String code, String message) {

            require(code, "code");
            require(message, "message");

        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Notice)) return false;
        Notice that = (Notice) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Notice[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Notice> schema() {
            return new dev.openallay.value.ValueSchema<>(Notice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Notice>>asList(new dev.openallay.value.ValueSchema.Component<>(Notice.class, "code", Notice::code), new dev.openallay.value.ValueSchema.Component<>(Notice.class, "message", Notice::message)), arguments -> new Notice((String) arguments[0], (String) arguments[1]));
        }
    }
}
private static void require(String value, String label) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillCommunityView)) return false;
        SkillCommunityView that = (SkillCommunityView) other;
        return available == that.available && java.util.Objects.equals(generatedAt, that.generatedAt) && java.util.Objects.equals(packages, that.packages) && java.util.Objects.equals(notice, that.notice);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(generatedAt);
        hash = 31 * hash + java.util.Objects.hashCode(packages);
        hash = 31 * hash + java.util.Objects.hashCode(notice);
        return hash;
    }
    @Override public String toString() { return "SkillCommunityView[available=" + available + ", generatedAt=" + generatedAt + ", packages=" + packages + ", notice=" + notice + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkillCommunityView> schema() {
            return new dev.openallay.value.ValueSchema<>(SkillCommunityView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkillCommunityView>>asList(new dev.openallay.value.ValueSchema.Component<>(SkillCommunityView.class, "available", SkillCommunityView::available), new dev.openallay.value.ValueSchema.Component<>(SkillCommunityView.class, "generatedAt", SkillCommunityView::generatedAt), new dev.openallay.value.ValueSchema.Component<>(SkillCommunityView.class, "packages", SkillCommunityView::packages), new dev.openallay.value.ValueSchema.Component<>(SkillCommunityView.class, "notice", SkillCommunityView::notice)), arguments -> new SkillCommunityView((Boolean) arguments[0], (Optional) arguments[1], (List) arguments[2], (Optional) arguments[3]));
        }
    }
}
