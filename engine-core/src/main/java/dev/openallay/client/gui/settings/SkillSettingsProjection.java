package dev.openallay.client.gui.settings;

import dev.openallay.settings.skill.SkillSettingsView;
import dev.openallay.settings.skill.SkillCommunityView;
import dev.openallay.skill.SkillSource;
import dev.openallay.requirement.RequirementEnvironment;
import java.util.Map;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Readable Skill catalog projection with editing limited to explicit local overrides. */
@dev.openallay.value.ValueType(SkillSettingsProjection.ValueSchemaProvider.class)
public final class SkillSettingsProjection {
    private final List<Skill> skills;
    private final Community community;
    private final int diagnosticCount;
    private final boolean debugMode;
    public SkillSettingsProjection(List<Skill> skills, Community community, int diagnosticCount, boolean debugMode) {

        skills = dev.openallay.util.Java8Collections.listCopyOf(skills);
        Objects.requireNonNull(community, "community");
        if (diagnosticCount < 0) {
            throw new IllegalArgumentException("diagnosticCount must not be negative");
        }

        this.skills = skills;
        this.community = community;
        this.diagnosticCount = diagnosticCount;
        this.debugMode = debugMode;
    }
    public List<Skill> skills() { return skills; }
    public Community community() { return community; }
    public int diagnosticCount() { return diagnosticCount; }
    public boolean debugMode() { return debugMode; }
public static SkillSettingsProjection from(SkillSettingsView view, boolean debugMode) {
        return from(view, SkillCommunityView.unavailable(), debugMode);
    }
public static SkillSettingsProjection from(
            SkillSettingsView view,
            SkillCommunityView community,
            boolean debugMode) {
        return from(view, community, new RequirementEnvironment(dev.openallay.util.Java8Collections.mapOf(), dev.openallay.util.Java8Collections.mapOf(), dev.openallay.util.Java8Collections.mapOf()), debugMode);
    }
public static SkillSettingsProjection from(
            SkillSettingsView view,
            SkillCommunityView community,
            RequirementEnvironment environment,
            boolean debugMode) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(community, "community");
        return new SkillSettingsProjection(
                dev.openallay.util.Java8Collections.toList(view.skills().stream().map(skill -> Skill.from(skill, environment))),
                Community.from(community),
                view.diagnostics().size(),
                debugMode);
    }
public Optional<Skill> find(String name) {
        return skills.stream().filter(skill -> skill.name().equals(name)).findFirst();
    }
@dev.openallay.value.ValueType(Skill.ValueSchemaProvider.class)
public static final class Skill {
    private final String name;
    private final String description;
    private final String body;
    private final String markdown;
    private final SkillSource.Origin origin;
    private final boolean createsOverrideOnSave;
    private final boolean canDeleteOverride;
    private final String provenance;
    private final RequirementSettingsProjection requirements;
    public Skill(String name, String description, String body, String markdown, SkillSource.Origin origin, boolean createsOverrideOnSave, boolean canDeleteOverride, String provenance, RequirementSettingsProjection requirements) {
        this.name = name;
        this.description = description;
        this.body = body;
        this.markdown = markdown;
        this.origin = origin;
        this.createsOverrideOnSave = createsOverrideOnSave;
        this.canDeleteOverride = canDeleteOverride;
        this.provenance = provenance;
        this.requirements = requirements;
    }
    public String name() { return name; }
    public String description() { return description; }
    public String body() { return body; }
    public String markdown() { return markdown; }
    public SkillSource.Origin origin() { return origin; }
    public boolean createsOverrideOnSave() { return createsOverrideOnSave; }
    public boolean canDeleteOverride() { return canDeleteOverride; }
    public String provenance() { return provenance; }
    public RequirementSettingsProjection requirements() { return requirements; }
static Skill from(SkillSettingsView.Skill skill, RequirementEnvironment environment) {
            return new Skill(
                    skill.metadata().name(),
                    skill.metadata().description(),
                    skill.body(),
                    skill.markdown(),
                    skill.origin(),
                    skill.createsOverrideOnSave(),
                    skill.canDeleteOverride(),
                    skill.metadata().provenance(),
                    RequirementSettingsProjection.evaluate(skill.metadata().requirements(), environment));
        }
public boolean localOverride() {
            return origin == SkillSource.Origin.LOCAL;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Skill)) return false;
        Skill that = (Skill) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(body, that.body) && java.util.Objects.equals(markdown, that.markdown) && java.util.Objects.equals(origin, that.origin) && createsOverrideOnSave == that.createsOverrideOnSave && canDeleteOverride == that.canDeleteOverride && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(requirements, that.requirements);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        hash = 31 * hash + java.util.Objects.hashCode(markdown);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + Boolean.hashCode(createsOverrideOnSave);
        hash = 31 * hash + Boolean.hashCode(canDeleteOverride);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(requirements);
        return hash;
    }
    @Override public String toString() { return "Skill[name=" + name + ", description=" + description + ", body=" + body + ", markdown=" + markdown + ", origin=" + origin + ", createsOverrideOnSave=" + createsOverrideOnSave + ", canDeleteOverride=" + canDeleteOverride + ", provenance=" + provenance + ", requirements=" + requirements + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Skill> schema() {
            return new dev.openallay.value.ValueSchema<>(Skill.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Skill>>asList(new dev.openallay.value.ValueSchema.Component<>(Skill.class, "name", Skill::name), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "description", Skill::description), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "body", Skill::body), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "markdown", Skill::markdown), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "origin", Skill::origin), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "createsOverrideOnSave", Skill::createsOverrideOnSave), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "canDeleteOverride", Skill::canDeleteOverride), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "provenance", Skill::provenance), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "requirements", Skill::requirements)), arguments -> new Skill((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (SkillSource.Origin) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (String) arguments[7], (RequirementSettingsProjection) arguments[8]));
        }
    }
}
@dev.openallay.value.ValueType(Community.ValueSchemaProvider.class)
public static final class Community {
    private final boolean available;
    private final Optional<Instant> generatedAt;
    private final List<Package> packages;
    private final Optional<Notice> notice;
    public Community(boolean available, Optional<Instant> generatedAt, List<Package> packages, Optional<Notice> notice) {

            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            packages = dev.openallay.util.Java8Collections.listCopyOf(packages);
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
static Community from(SkillCommunityView view) {
            return new Community(
                    view.available(),
                    view.generatedAt(),
                    dev.openallay.util.Java8Collections.toList(view.packages().stream().map(Package::from)),
                    view.notice().map(value -> new Notice(value.code(), value.message())));
        }
public Optional<Package> find(String id) {
            return packages.stream().filter(value -> value.id().equals(id)).findFirst();
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Community)) return false;
        Community that = (Community) other;
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
    @Override public String toString() { return "Community[available=" + available + ", generatedAt=" + generatedAt + ", packages=" + packages + ", notice=" + notice + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Community> schema() {
            return new dev.openallay.value.ValueSchema<>(Community.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Community>>asList(new dev.openallay.value.ValueSchema.Component<>(Community.class, "available", Community::available), new dev.openallay.value.ValueSchema.Component<>(Community.class, "generatedAt", Community::generatedAt), new dev.openallay.value.ValueSchema.Component<>(Community.class, "packages", Community::packages), new dev.openallay.value.ValueSchema.Component<>(Community.class, "notice", Community::notice)), arguments -> new Community((Boolean) arguments[0], (Optional) arguments[1], (List) arguments[2], (Optional) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(Package.ValueSchemaProvider.class)
public static final class Package {
    private final String id;
    private final String displayName;
    private final String description;
    private final String publisher;
    private final String version;
    private final PackageState state;
    private final String source;
    private final String archive;
    private final String sha256;
    public Package(String id, String displayName, String description, String publisher, String version, PackageState state, String source, String archive, String sha256) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.publisher = publisher;
        this.version = version;
        this.state = state;
        this.source = source;
        this.archive = archive;
        this.sha256 = sha256;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String publisher() { return publisher; }
    public String version() { return version; }
    public PackageState state() { return state; }
    public String source() { return source; }
    public String archive() { return archive; }
    public String sha256() { return sha256; }
static Package from(SkillCommunityView.Package value) {
            PackageState state;
            if (!value.compatible()) {
                state = PackageState.INCOMPATIBLE;
            } else if (value.installed() && value.updateAvailable()) {
                state = PackageState.UPDATE_AVAILABLE;
            } else if (value.installed()) {
                state = PackageState.INSTALLED;
            } else {
                state = PackageState.AVAILABLE;
            }
            return new Package(
                    value.id(),
                    value.displayName(),
                    value.description(),
                    value.publisher(),
                    value.availableVersion(),
                    state,
                    value.source(),
                    value.archive(),
                    value.sha256());
        }
public boolean installable() {
            return state == PackageState.AVAILABLE || state == PackageState.UPDATE_AVAILABLE;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Package)) return false;
        Package that = (Package) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(publisher, that.publisher) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(archive, that.archive) && java.util.Objects.equals(sha256, that.sha256);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(publisher);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(archive);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        return hash;
    }
    @Override public String toString() { return "Package[id=" + id + ", displayName=" + displayName + ", description=" + description + ", publisher=" + publisher + ", version=" + version + ", state=" + state + ", source=" + source + ", archive=" + archive + ", sha256=" + sha256 + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Package> schema() {
            return new dev.openallay.value.ValueSchema<>(Package.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Package>>asList(new dev.openallay.value.ValueSchema.Component<>(Package.class, "id", Package::id), new dev.openallay.value.ValueSchema.Component<>(Package.class, "displayName", Package::displayName), new dev.openallay.value.ValueSchema.Component<>(Package.class, "description", Package::description), new dev.openallay.value.ValueSchema.Component<>(Package.class, "publisher", Package::publisher), new dev.openallay.value.ValueSchema.Component<>(Package.class, "version", Package::version), new dev.openallay.value.ValueSchema.Component<>(Package.class, "state", Package::state), new dev.openallay.value.ValueSchema.Component<>(Package.class, "source", Package::source), new dev.openallay.value.ValueSchema.Component<>(Package.class, "archive", Package::archive), new dev.openallay.value.ValueSchema.Component<>(Package.class, "sha256", Package::sha256)), arguments -> new Package((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (PackageState) arguments[5], (String) arguments[6], (String) arguments[7], (String) arguments[8]));
        }
    }
}
public enum PackageState {
        AVAILABLE,
        INSTALLED,
        UPDATE_AVAILABLE,
        INCOMPATIBLE
    }
@dev.openallay.value.ValueType(Notice.ValueSchemaProvider.class)
public static final class Notice {
    private final String code;
    private final String message;
    public Notice(String code, String message) {
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillSettingsProjection)) return false;
        SkillSettingsProjection that = (SkillSettingsProjection) other;
        return java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(community, that.community) && diagnosticCount == that.diagnosticCount && debugMode == that.debugMode;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(community);
        hash = 31 * hash + Integer.hashCode(diagnosticCount);
        hash = 31 * hash + Boolean.hashCode(debugMode);
        return hash;
    }
    @Override public String toString() { return "SkillSettingsProjection[skills=" + skills + ", community=" + community + ", diagnosticCount=" + diagnosticCount + ", debugMode=" + debugMode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkillSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(SkillSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkillSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(SkillSettingsProjection.class, "skills", SkillSettingsProjection::skills), new dev.openallay.value.ValueSchema.Component<>(SkillSettingsProjection.class, "community", SkillSettingsProjection::community), new dev.openallay.value.ValueSchema.Component<>(SkillSettingsProjection.class, "diagnosticCount", SkillSettingsProjection::diagnosticCount), new dev.openallay.value.ValueSchema.Component<>(SkillSettingsProjection.class, "debugMode", SkillSettingsProjection::debugMode)), arguments -> new SkillSettingsProjection((List) arguments[0], (Community) arguments[1], (Integer) arguments[2], (Boolean) arguments[3]));
        }
    }
}
