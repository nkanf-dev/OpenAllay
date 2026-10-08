package dev.openallay.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Read-only discovery of local Agent Skills below one trusted configuration root. */
public final class FilesystemSkillLoader {
    private static final Set<String> EXECUTABLE_EXTENSIONS = dev.openallay.util.Java8Collections.setOf("sh", "bash", "zsh", "command", "bat", "cmd", "ps1", "exe", "dll", "dylib", "so", "class", "jar", "py", "pyc", "js", "mjs", "cjs");

    @dev.openallay.value.ValueType(RejectedSkill.ValueSchemaProvider.class)
public static final class RejectedSkill {
    private final String skillName;
    private final SkillDiagnostic diagnostic;
    public RejectedSkill(String skillName, SkillDiagnostic diagnostic) {

            if (skillName == null || dev.openallay.util.Java8Strings.isBlank(skillName)) {
                throw new IllegalArgumentException("Rejected Skill name must not be blank");
            }
            diagnostic = java.util.Objects.requireNonNull(diagnostic, "diagnostic");

        this.skillName = skillName;
        this.diagnostic = diagnostic;
    }
    public String skillName() { return skillName; }
    public SkillDiagnostic diagnostic() { return diagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RejectedSkill)) return false;
        RejectedSkill that = (RejectedSkill) other;
        return java.util.Objects.equals(skillName, that.skillName) && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(skillName);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "RejectedSkill[skillName=" + skillName + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RejectedSkill> schema() {
            return new dev.openallay.value.ValueSchema<>(RejectedSkill.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RejectedSkill>>asList(new dev.openallay.value.ValueSchema.Component<>(RejectedSkill.class, "skillName", RejectedSkill::skillName), new dev.openallay.value.ValueSchema.Component<>(RejectedSkill.class, "diagnostic", RejectedSkill::diagnostic)), arguments -> new RejectedSkill((String) arguments[0], (SkillDiagnostic) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(LoadResult.ValueSchemaProvider.class)
public static final class LoadResult {
    private final List<SkillSource> sources;
    private final List<RejectedSkill> rejected;
    public LoadResult(List<SkillSource> sources, List<RejectedSkill> rejected) {

            sources = dev.openallay.util.Java8Collections.listCopyOf(sources);
            rejected = dev.openallay.util.Java8Collections.listCopyOf(rejected);

        this.sources = sources;
        this.rejected = rejected;
    }
    public List<SkillSource> sources() { return sources; }
    public List<RejectedSkill> rejected() { return rejected; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LoadResult)) return false;
        LoadResult that = (LoadResult) other;
        return java.util.Objects.equals(sources, that.sources) && java.util.Objects.equals(rejected, that.rejected);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + java.util.Objects.hashCode(rejected);
        return hash;
    }
    @Override public String toString() { return "LoadResult[sources=" + sources + ", rejected=" + rejected + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<LoadResult> schema() {
            return new dev.openallay.value.ValueSchema<>(LoadResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<LoadResult>>asList(new dev.openallay.value.ValueSchema.Component<>(LoadResult.class, "sources", LoadResult::sources), new dev.openallay.value.ValueSchema.Component<>(LoadResult.class, "rejected", LoadResult::rejected)), arguments -> new LoadResult((List) arguments[0], (List) arguments[1]));
        }
    }
}

    public LoadResult load(Path configuredRoot) {
        Path root = java.util.Objects.requireNonNull(configuredRoot, "configuredRoot")
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return new LoadResult(dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf());
        }
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return rootFailure(root, "Skill configuration root must be a real directory");
        }
        List<SkillSource> sources = new ArrayList<>();
        List<RejectedSkill> rejected = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : dev.openallay.util.Java8Collections.toList(children.sorted(Comparator.comparing(path -> path.getFileName().toString())))) {
                if (child.getFileName().toString().startsWith(".")) {
                    // Atomic settings staging/tombstones are never discoverable Skill packages.
                    continue;
                }
                if (Files.isSymbolicLink(child)) {
                    rejected.add(rejected(child, "Symbolic-link Skill directories are not supported"));
                    continue;
                }
                if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try {
                    sources.add(loadDirectory(root, child));
                } catch (IllegalArgumentException | IOException failure) {
                    rejected.add(rejected(child, failure.getMessage()));
                }
            }
        } catch (IOException failure) {
            return rootFailure(root, "Unable to list Skill configuration root");
        }
        return new LoadResult(sources, rejected);
    }

    private static SkillSource loadDirectory(Path root, Path directory) throws IOException {
        String name = directory.getFileName().toString();
        Path entry = directory.resolve("SKILL.md");
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(entry)) {
            throw new IllegalArgumentException("Local Skill requires uppercase SKILL.md");
        }
        Path realRoot = root.toRealPath();
        Path realDirectory = directory.toRealPath();
        if (!realDirectory.startsWith(realRoot)) {
            throw new IllegalArgumentException("Skill directory escapes its configuration root");
        }
        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : dev.openallay.util.Java8Collections.toList(tree.sorted())) {
                if (path.equals(directory)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("Skill packages cannot contain symbolic links");
                }
                if (!path.toRealPath().startsWith(realDirectory)) {
                    throw new IllegalArgumentException("Skill file escapes its package root");
                }
                String relative = directory.relativize(path).toString()
                        .replace(java.io.File.separatorChar, '/');
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!relative.equals("references")
                            && !relative.startsWith("references/")
                            && !relative.equals("assets")
                            && !relative.startsWith("assets/")) {
                        throw new IllegalArgumentException("Unsupported Skill directory: " + relative);
                    }
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("Unsupported Skill filesystem entry: " + relative);
                }
                if (!relative.equals("SKILL.md")
                        && !relative.startsWith("references/")
                        && !relative.startsWith("assets/")) {
                    throw new IllegalArgumentException("Unsupported Skill file: " + relative);
                }
                if (isExecutable(relative)) {
                    throw new IllegalArgumentException("Executable Skill files are not supported: " + relative);
                }
                files.put(name + "/" + relative, dev.openallay.util.Java8Files.readString(path));
            }
        }
        return new SkillSource(
                "local:" + directory,
                name + "/SKILL.md",
                files,
                SkillSource.Origin.LOCAL);
    }

    private static boolean isExecutable(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && EXECUTABLE_EXTENSIONS.contains(filename.substring(dot + 1));
    }

    private static RejectedSkill rejected(Path directory, String message) {
        String safeMessage = message == null || dev.openallay.util.Java8Strings.isBlank(message)
                ? "Unable to read local Skill"
                : message;
        return new RejectedSkill(
                directory.getFileName().toString(),
                new SkillDiagnostic(
                        "skill_validation_failed", safeMessage, "local:" + directory.toAbsolutePath().normalize()));
    }

    private static LoadResult rootFailure(Path root, String message) {
        return new LoadResult(
                dev.openallay.util.Java8Collections.listOf(),
                dev.openallay.util.Java8Collections.listOf(new RejectedSkill(
                        ".",
                        new SkillDiagnostic("skill_discovery_failed", message, "local:" + root))));
    }
}
