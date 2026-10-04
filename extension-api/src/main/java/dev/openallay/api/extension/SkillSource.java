package dev.openallay.api.extension;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Detached Skill files with provenance. Paths are root-relative and copied after normalization. */
public final class SkillSource {
    private final String provenance;
    private final String entryPath;
    private final Map<String, String> files;
    private final Origin origin;

    public SkillSource(String provenance, String entryPath, Map<String, String> files, Origin origin) {
        this.provenance = ApiValidation.text(provenance, "provenance");
        this.entryPath = requireEntry(entryPath);
        this.files = requireFiles(files);
        if (!this.files.containsKey(this.entryPath))
            throw new IllegalArgumentException("Skill entry is missing from files: " + this.entryPath);
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public String provenance() { return provenance; }
    public String entryPath() { return entryPath; }
    public Map<String, String> files() { return files; }
    public Origin origin() { return origin; }

    public enum Origin { BUNDLED, LOCAL, EXTERNAL }
    public SkillSource(String provenance, String entryPath, Map<String, String> files) {
        this(provenance, entryPath, files, Origin.EXTERNAL);
    }
    public String directoryName() {
        String directory = entryPath.substring(0, entryPath.lastIndexOf('/'));
        return directory.substring(directory.lastIndexOf('/') + 1);
    }
    private static String requireEntry(String path) {
        String entry = ApiValidation.skillPath(path);
        if (!entry.endsWith("/SKILL.md") && !entry.endsWith("/skill.md"))
            throw new IllegalArgumentException("Skill entry must be a directory's SKILL.md or skill.md");
        return entry;
    }
    private static Map<String, String> requireFiles(Map<String, String> files) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : Objects.requireNonNull(files, "files").entrySet()) {
            String path = ApiValidation.skillPath(entry.getKey());
            String content = Objects.requireNonNull(entry.getValue(), "Skill file contents");
            if (copy.put(path, content) != null)
                throw new IllegalArgumentException("Duplicate normalized Skill path: " + path);
        }
        return Collections.unmodifiableMap(copy);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillSource)) return false;
        SkillSource that = (SkillSource) other;
        return Objects.equals(provenance, that.provenance) &&
                Objects.equals(entryPath, that.entryPath) &&
                Objects.equals(files, that.files) &&
                Objects.equals(origin, that.origin);
    }
    @Override public int hashCode() { return Objects.hash(provenance, entryPath, files, origin); }
}
