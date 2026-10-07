package dev.openallay.skill;

import java.util.LinkedHashMap;
import java.util.Map;

@dev.openallay.value.ValueType(SkillSource.ValueSchemaProvider.class)
public final class SkillSource {
    private final String provenance;
    private final String entryPath;
    private final Map<String, String> files;
    private final Origin origin;
    public SkillSource(String provenance, String entryPath, Map<String, String> files, Origin origin) {

        if (provenance == null || dev.openallay.util.Java8Strings.isBlank(provenance)) {
            throw new IllegalArgumentException("Skill provenance must not be blank");
        }
        entryPath = normalize(entryPath);
        if (!entryPath.endsWith("/SKILL.md") && !entryPath.endsWith("/skill.md")) {
            throw new IllegalArgumentException("Skill entry must be SKILL.md or skill.md");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Skill origin must not be null");
        }
        LinkedHashMap<String, String> normalizedFiles = new LinkedHashMap<>();
        files.forEach((path, contents) -> {
            String normalizedPath = normalize(path);
            if (contents == null) {
                throw new IllegalArgumentException("Skill file contents must not be null: " + path);
            }
            if (normalizedFiles.put(normalizedPath, contents) != null) {
                throw new IllegalArgumentException("Duplicate normalized Skill path: " + path);
            }
        });
        files = dev.openallay.util.Java8Collections.mapCopyOf(normalizedFiles);

        this.provenance = provenance;
        this.entryPath = entryPath;
        this.files = files;
        this.origin = origin;
    }
    public String provenance() { return provenance; }
    public String entryPath() { return entryPath; }
    public Map<String, String> files() { return files; }
    public Origin origin() { return origin; }
public enum Origin {
        BUNDLED,
        LOCAL,
        EXTERNAL
    }
public SkillSource(String provenance, String entryPath, Map<String, String> files) {
        this(provenance, entryPath, files, Origin.EXTERNAL);
    }
public String directoryName() {
        int separator = entryPath.lastIndexOf('/');
        String directory = entryPath.substring(0, separator);
        int parent = directory.lastIndexOf('/');
        return directory.substring(parent + 1);
    }
static String normalize(String path) {
        if (path == null || dev.openallay.util.Java8Strings.isBlank(path) || path.startsWith("/") || path.contains("\\")) {
            throw new IllegalArgumentException("Invalid Skill path: " + path);
        }
        java.nio.file.Path normalized = java.nio.file.Paths.get(path).normalize();
        String value = normalized.toString().replace(java.io.File.separatorChar, '/');
        if (value.equals("..") || value.startsWith("../")) {
            throw new IllegalArgumentException("Skill path escapes its root: " + path);
        }
        return value;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillSource)) return false;
        SkillSource that = (SkillSource) other;
        return java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(entryPath, that.entryPath) && java.util.Objects.equals(files, that.files) && java.util.Objects.equals(origin, that.origin);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(entryPath);
        hash = 31 * hash + java.util.Objects.hashCode(files);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        return hash;
    }
    @Override public String toString() { return "SkillSource[provenance=" + provenance + ", entryPath=" + entryPath + ", files=" + files + ", origin=" + origin + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkillSource> schema() {
            return new dev.openallay.value.ValueSchema<>(SkillSource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkillSource>>asList(new dev.openallay.value.ValueSchema.Component<>(SkillSource.class, "provenance", SkillSource::provenance), new dev.openallay.value.ValueSchema.Component<>(SkillSource.class, "entryPath", SkillSource::entryPath), new dev.openallay.value.ValueSchema.Component<>(SkillSource.class, "files", SkillSource::files), new dev.openallay.value.ValueSchema.Component<>(SkillSource.class, "origin", SkillSource::origin)), arguments -> new SkillSource((String) arguments[0], (String) arguments[1], (Map) arguments[2], (Origin) arguments[3]));
        }
    }
}
