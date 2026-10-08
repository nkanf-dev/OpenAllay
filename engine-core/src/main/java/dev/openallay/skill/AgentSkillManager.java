package dev.openallay.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic CRUD for Agent-authored Skill packages below one OpenAllay-owned root.
 *
 * <p>The API accepts Skill package names and declared reference contents, never filesystem paths.
 * Bundled packages remain immutable; updating one creates a local override and deleting that
 * override reveals the bundled package again.
 */
public final class AgentSkillManager {
    public enum Operation {
        CREATE,
        UPDATE,
        DELETE
    }

    @dev.openallay.value.ValueType(Result.ValueSchemaProvider.class)
public static final class Result {
    private final Operation operation;
    private final String name;
    private final SkillSource.Origin origin;
    private final List<String> availableReferences;
    public Result(Operation operation, String name, SkillSource.Origin origin, List<String> availableReferences) {

            availableReferences = dev.openallay.util.Java8Collections.listCopyOf(availableReferences);

        this.operation = operation;
        this.name = name;
        this.origin = origin;
        this.availableReferences = availableReferences;
    }
    public Operation operation() { return operation; }
    public String name() { return name; }
    public SkillSource.Origin origin() { return origin; }
    public List<String> availableReferences() { return availableReferences; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Result)) return false;
        Result that = (Result) other;
        return java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(origin, that.origin) && java.util.Objects.equals(availableReferences, that.availableReferences);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + java.util.Objects.hashCode(availableReferences);
        return hash;
    }
    @Override public String toString() { return "Result[operation=" + operation + ", name=" + name + ", origin=" + origin + ", availableReferences=" + availableReferences + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Result> schema() {
            return new dev.openallay.value.ValueSchema<>(Result.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Result>>asList(new dev.openallay.value.ValueSchema.Component<>(Result.class, "operation", Result::operation), new dev.openallay.value.ValueSchema.Component<>(Result.class, "name", Result::name), new dev.openallay.value.ValueSchema.Component<>(Result.class, "origin", Result::origin), new dev.openallay.value.ValueSchema.Component<>(Result.class, "availableReferences", Result::availableReferences)), arguments -> new Result((Operation) arguments[0], (String) arguments[1], (SkillSource.Origin) arguments[2], (List) arguments[3]));
        }
    }
}

    private final Path root;
    private final SkillRepository repository;
    private final SkillParser parser;
    private final List<SkillSource> bundled;
    private final Set<String> installedMods;
    private final Set<String> availableTools;
    private final FilesystemSkillLoader loader = new FilesystemSkillLoader();

    public AgentSkillManager(
            Path root,
            SkillRepository repository,
            SkillParser parser,
            Collection<SkillSource> bundled,
            Set<String> installedMods,
            Set<String> availableTools) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.repository = Objects.requireNonNull(repository, "repository");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.bundled = dev.openallay.util.Java8Collections.listCopyOf(bundled);
        this.installedMods = dev.openallay.util.Java8Collections.setCopyOf(installedMods);
        this.availableTools = dev.openallay.util.Java8Collections.setCopyOf(availableTools);
    }

    public synchronized Result create(
            String name, String markdown, Map<String, String> references) {
        validateName(name);
        if (repository.find(name).isPresent() || localExists(name)) {
            throw new SkillManagementException(
                    "skill_already_exists", "A Skill with this name already exists");
        }
        SkillSource candidate = candidate(name, markdown, references);
        SkillDocument parsed = parser.parse(candidate);
        validateDependencies(parsed);
        Replacement replacement = replaceLocal(name, candidate.files(), false);
        try {
            Result result = publish(Operation.CREATE, parsed);
            replacement.commit();
            return result;
        } catch (RuntimeException failure) {
            rollback(replacement, failure);
            throw failure;
        }
    }

    public synchronized Result update(
            String name, String markdown, Map<String, String> references) {
        validateName(name);
        SkillDocument current = repository.find(name).orElseThrow(() ->
                new SkillManagementException(
                        "skill_not_found", "No available Skill has this name"));
        SkillSource candidate = candidate(name, markdown, references);
        SkillDocument parsed = parser.parse(candidate);
        validateDependencies(parsed);
        Replacement replacement = replaceLocal(name, candidate.files(), true);
        try {
            Result result = publish(Operation.UPDATE, parsed);
            replacement.commit();
            return result;
        } catch (RuntimeException failure) {
            rollback(replacement, failure);
            throw failure;
        }
    }

    public synchronized Result delete(String name) {
        validateName(name);
        if (!localExists(name)) {
            if (bundledNamed(name) != null) {
                throw new SkillManagementException(
                        "bundled_skill_immutable",
                        "Bundled Skills cannot be deleted; only their local override can be removed");
            }
            throw new SkillManagementException(
                    "skill_not_found", "No local Skill has this name");
        }
        Path trustedRoot = requireRoot();
        Path target = child(trustedRoot, name);
        Path tombstone = trustedRoot.resolve(".deleted-" + name + "-" + java.util.UUID.randomUUID());
        try {
            Files.move(target, tombstone, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            throw new SkillManagementException(
                    "skill_delete_failed", "Unable to delete the managed Skill", failure);
        }
        try {
            reload();
            SkillDocument revealed = repository.find(name).orElse(null);
            Result result = new Result(
                    Operation.DELETE,
                    name,
                    revealed == null ? SkillSource.Origin.LOCAL : revealed.metadata().origin(),
                    revealed == null
                            ? dev.openallay.util.Java8Collections.listOf()
                            : dev.openallay.util.Java8Collections.toList(revealed.references().keySet().stream().sorted()));
            deleteTreeQuietly(tombstone);
            return result;
        } catch (RuntimeException failure) {
            restoreDeleted(target, tombstone, failure);
            throw failure;
        }
    }

    private Result publish(Operation operation, SkillDocument candidate) {
        reload();
        SkillDocument published = repository.find(candidate.metadata().name()).orElse(null);
        if (published == null
                || published.metadata().origin() != SkillSource.Origin.LOCAL
                || !published.instructions().equals(candidate.instructions())
                || !published.references().equals(candidate.references())) {
            throw new SkillManagementException(
                    "skill_dependency_unavailable",
                    "The Skill requires an unavailable Tool or mod");
        }
        return new Result(
                operation,
                published.metadata().name(),
                published.metadata().origin(),
                dev.openallay.util.Java8Collections.toList(published.references().keySet().stream().sorted()));
    }

    private SkillSource candidate(
            String name, String markdown, Map<String, String> references) {
        if (markdown == null || dev.openallay.util.Java8Strings.isBlank(markdown)) {
            throw new SkillManagementException(
                    "skill_invalid", "Skill Markdown must not be blank");
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put(name + "/SKILL.md", markdown);
        Map<String, String> safeReferences =
                references == null ? dev.openallay.util.Java8Collections.mapOf() : dev.openallay.util.Java8Collections.mapCopyOf(references);
        safeReferences.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String relative = normalizeReference(entry.getKey());
                    String content = Objects.requireNonNull(
                            entry.getValue(), "reference content");
                    files.put(name + "/" + relative, content);
                });
        return new SkillSource(
                "agent-managed:" + name,
                name + "/SKILL.md",
                files,
                SkillSource.Origin.LOCAL);
    }

    private void validateDependencies(SkillDocument document) {
        if (!installedMods.containsAll(document.metadata().requiredMods())) {
            throw new SkillManagementException(
                    "skill_dependency_unavailable",
                    "The Skill requires an unavailable mod");
        }
        if (!availableTools.containsAll(document.metadata().allowedTools())) {
            throw new SkillManagementException(
                    "skill_dependency_unavailable",
                    "The Skill requires an unavailable Tool");
        }
    }

    private Replacement replaceLocal(
            String name, Map<String, String> files, boolean allowReplacement) {
        Path trustedRoot = ensureRoot();
        Path target = child(trustedRoot, name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!allowReplacement
                        || Files.isSymbolicLink(target)
                        || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new SkillManagementException(
                    "skill_write_conflict", "The managed Skill target is not replaceable");
        }
        Path staging = null;
        Path prior = null;
        try {
            staging = Files.createTempDirectory(trustedRoot, "." + name + "-");
            for (Map.Entry<String, String> file : files.entrySet()) {
                String prefix = name + "/";
                if (!file.getKey().startsWith(prefix)) {
                    throw new IllegalArgumentException("Skill file escapes its package");
                }
                Path destination = staging.resolve(file.getKey().substring(prefix.length())).normalize();
                if (!destination.startsWith(staging)) {
                    throw new IllegalArgumentException("Skill file escapes its package");
                }
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, file.getValue(), StandardOpenOption.CREATE_NEW);
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                prior = trustedRoot.resolve(".previous-" + name + "-" + java.util.UUID.randomUUID());
                Files.move(target, prior, StandardCopyOption.ATOMIC_MOVE);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                staging = null;
            } catch (IOException publishFailure) {
                if (prior != null && Files.exists(prior, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(prior, target, StandardCopyOption.ATOMIC_MOVE);
                    prior = null;
                }
                throw publishFailure;
            }
            Replacement replacement = new Replacement(target, prior);
            prior = null;
            return replacement;
        } catch (IOException | RuntimeException failure) {
            final class $oaPattern0_Holder { java.lang.Exception value; SkillManagementException bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
throw (($oaPattern0_holder.value = failure) instanceof dev.openallay.skill.SkillManagementException && (($oaPattern0_holder.bound = (SkillManagementException) $oaPattern0_holder.value) != null))
                    ? $oaPattern0_holder.bound
                    : new SkillManagementException(
                            "skill_write_failed", "Unable to publish the managed Skill", failure);
        } finally {
            deleteTreeQuietly(staging);
            // A prior package is a recovery artifact, not disposable staging. If publishing the
            // replacement and restoring the prior package both fail, leave it in the managed root
            // so the original Skill can be recovered instead of silently deleting user data.
        }
    }

    private void rollback(Replacement replacement, RuntimeException originalFailure) {
        try {
            deleteTree(replacement.target());
            if (replacement.prior() != null
                    && Files.exists(replacement.prior(), LinkOption.NOFOLLOW_LINKS)) {
                Files.move(
                        replacement.prior(),
                        replacement.target(),
                        StandardCopyOption.ATOMIC_MOVE);
            }
            reload();
        } catch (IOException | RuntimeException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
            throw new SkillManagementException(
                    "skill_rollback_failed",
                    "Unable to restore the previously published Skill",
                    originalFailure);
        }
    }

    private void restoreDeleted(
            Path target, Path tombstone, RuntimeException originalFailure) {
        try {
            Files.move(tombstone, target, StandardCopyOption.ATOMIC_MOVE);
            reload();
        } catch (IOException | RuntimeException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
            throw new SkillManagementException(
                    "skill_rollback_failed",
                    "Unable to restore the deleted Skill",
                    originalFailure);
        }
    }

    @dev.openallay.value.ValueType(Replacement.ValueSchemaProvider.class)
private static final class Replacement {
    private final Path target;
    private final Path prior;
    private Replacement(Path target, Path prior) {
        this.target = target;
        this.prior = prior;
    }
    public Path target() { return target; }
    public Path prior() { return prior; }
private void commit() {
            deleteTreeQuietly(prior);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Replacement)) return false;
        Replacement that = (Replacement) other;
        return java.util.Objects.equals(target, that.target) && java.util.Objects.equals(prior, that.prior);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + java.util.Objects.hashCode(prior);
        return hash;
    }
    @Override public String toString() { return "Replacement[target=" + target + ", prior=" + prior + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Replacement> schema() {
            return new dev.openallay.value.ValueSchema<>(Replacement.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Replacement>>asList(new dev.openallay.value.ValueSchema.Component<>(Replacement.class, "target", Replacement::target), new dev.openallay.value.ValueSchema.Component<>(Replacement.class, "prior", Replacement::prior)), arguments -> new Replacement((Path) arguments[0], (Path) arguments[1]));
        }
    }
}

    private void reload() {
        if (!repository.reload(bundled, loader.load(root), installedMods)) {
            throw new SkillManagementException(
                    "skill_reload_failed", "Unable to publish the managed Skill catalog");
        }
    }

    private boolean localExists(String name) {
        validateName(name);
        return Files.isDirectory(root.resolve(name), LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(root.resolve(name));
    }

    private SkillSource bundledNamed(String name) {
        return bundled.stream()
                .filter(source -> source.directoryName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private Path ensureRoot() {
        try {
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
                throw new SkillManagementException(
                        "skill_root_invalid", "The managed Skill root is a symbolic link");
            }
            Files.createDirectories(root);
            return root.toRealPath();
        } catch (IOException failure) {
            throw new SkillManagementException(
                    "skill_root_unavailable", "Unable to prepare the managed Skill root", failure);
        }
    }

    private Path requireRoot() {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new SkillManagementException(
                    "skill_root_unavailable", "The managed Skill root is unavailable");
        }
        try {
            return root.toRealPath();
        } catch (IOException failure) {
            throw new SkillManagementException(
                    "skill_root_unavailable", "The managed Skill root is unavailable", failure);
        }
    }

    private static Path child(Path trustedRoot, String name) {
        validateName(name);
        Path child = trustedRoot.resolve(name).normalize();
        if (!child.getParent().equals(trustedRoot)) {
            throw new SkillManagementException(
                    "skill_name_invalid", "Skill name escapes the managed root");
        }
        return child;
    }

    private static String normalizeReference(String value) {
        if (value == null) {
            throw new SkillManagementException(
                    "skill_reference_invalid", "Skill reference name must not be null");
        }
        String normalized = value.replace('\\', '/');
        if (!normalized.matches("references/[a-zA-Z0-9][a-zA-Z0-9._/-]*\\.md")
                || normalized.contains("/../")
                || normalized.contains("//")) {
            throw new SkillManagementException(
                    "skill_reference_invalid",
                    "References must be Markdown files below references/");
        }
        return normalized;
    }

    private static void validateName(String name) {
        if (name == null || name.length() > 64 || !name.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new SkillManagementException(
                    "skill_name_invalid", "Invalid Skill name");
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> entries = Files.walk(path)) {
            for (Path entry : dev.openallay.util.Java8Collections.toList(entries.sorted(Comparator.reverseOrder()))) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static void deleteTreeQuietly(Path path) {
        try {
            deleteTree(path);
        } catch (IOException ignored) {
            // Staging and tombstone cleanup never changes the published package.
        }
    }
}
