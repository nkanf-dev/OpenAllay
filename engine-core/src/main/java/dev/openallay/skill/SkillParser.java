package dev.openallay.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Parser for the safe OpenAllay subset of the public Agent Skills format. */
public final class SkillParser {
    private static final Set<String> AGENT_SKILL_FIELDS = dev.openallay.util.Java8Collections.setOf(
            "name", "description", "license", "compatibility", "metadata", "allowed-tools");
    private static final String REQUIRED_MODS_ATTRIBUTE = "openallay/required-mods";
    private static final Set<String> EXECUTABLE_EXTENSIONS = dev.openallay.util.Java8Collections.setOf(
            "sh", "bash", "zsh", "fish", "command", "bat", "cmd", "ps1", "exe", "dll",
            "dylib", "so", "class", "jar", "py", "pyc", "js", "mjs", "cjs");

    public SkillDocument parse(SkillSource source) {
        String entry = source.files().get(source.entryPath());
        if (entry == null) {
            throw new IllegalArgumentException("Missing Skill entry " + source.entryPath());
        }
        boolean agentSkillsFormat = source.entryPath().endsWith("/SKILL.md");
        String root = source.entryPath().substring(0, source.entryPath().lastIndexOf('/') + 1);
        validateFiles(source, root, agentSkillsFormat);
        ParsedFrontmatter parsed = frontmatter(entry);
        return agentSkillsFormat
                ? parseAgentSkill(source, root, parsed)
                : parseLegacySkill(source, root, parsed);
    }

    /**
     * Parses an import package whose files are relative to its package root. The declared Skill
     * name becomes the managed directory identity, so a downloaded ZIP need not add a redundant
     * wrapper directory around its root {@code SKILL.md}.
     */
    public SkillDocument parsePackage(
            String provenance, Map<String, String> relativeFiles, SkillSource.Origin origin) {
        Map<String, String> files = dev.openallay.util.Java8Collections.mapCopyOf(relativeFiles);
        String markdown = files.get("SKILL.md");
        if (markdown == null) {
            throw new IllegalArgumentException("Skill package requires root SKILL.md");
        }
        String name = frontmatter(markdown).requiredScalar("name");
        LinkedHashMap<String, String> rooted = new LinkedHashMap<>();
        files.forEach((path, contents) -> rooted.put(name + "/" + path, contents));
        return parse(new SkillSource(
                provenance,
                name + "/SKILL.md",
                rooted,
                origin));
    }

    private static SkillDocument parseAgentSkill(
            SkillSource source, String root, ParsedFrontmatter parsed) {
        Set<String> unknown = new LinkedHashSet<>(parsed.keys());
        unknown.removeAll(AGENT_SKILL_FIELDS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Skill frontmatter fields: " + unknown);
        }
        String name = parsed.requiredScalar("name");
        if (!source.directoryName().equals(name)) {
            throw new IllegalArgumentException(
                    "Skill directory must match name: " + source.directoryName() + " != " + name);
        }
        Map<String, String> attributes = parsed.stringMap("metadata");
        Set<String> requiredMods = splitDependencies(attributes.getOrDefault(REQUIRED_MODS_ATTRIBUTE, ""));
        Set<String> allowedTools = splitDependencies(parsed.optionalScalar("allowed-tools").orElse(""));
        Map<String, String> references = discoveredReferences(source, root);
        SkillMetadata metadata = new SkillMetadata(
                name,
                parsed.requiredScalar("description"),
                parsed.optionalScalar("license"),
                parsed.optionalScalar("compatibility"),
                attributes,
                requiredMods,
                allowedTools,
                dev.openallay.util.Java8Collections.listCopyOf(references.keySet()),
                source.provenance() + ":" + source.entryPath(),
                source.origin());
        return new SkillDocument(metadata, parsed.body(), references);
    }

    /** In-memory Skill descriptors; filesystem packages use their explicit manifest form. */
    private static SkillDocument parseLegacySkill(
            SkillSource source, String root, ParsedFrontmatter parsed) {
        List<String> referencePaths = parsed.list("references");
        Map<String, String> references = new LinkedHashMap<>();
        for (String reference : referencePaths) {
            if (looksLikeUrl(reference)) {
                throw new IllegalArgumentException("Skill references cannot be URLs: " + reference);
            }
            String path = SkillSource.normalize(root + reference);
            if (!path.startsWith(root)) {
                throw new IllegalArgumentException("Skill reference escapes its root: " + reference);
            }
            String content = source.files().get(path);
            if (content == null) {
                throw new IllegalArgumentException("Missing Skill reference: " + reference);
            }
            references.put(reference, content);
        }
        SkillMetadata metadata = new SkillMetadata(
                parsed.requiredScalar("name"),
                parsed.requiredScalar("description"),
                Optional.empty(),
                Optional.empty(),
                dev.openallay.util.Java8Collections.mapOf(),
                dev.openallay.util.Java8Collections.setCopyOf(parsed.list("required-mods")),
                dev.openallay.util.Java8Collections.setCopyOf(parsed.list("allowed-tools")),
                referencePaths,
                source.provenance() + ":" + source.entryPath(),
                source.origin());
        return new SkillDocument(metadata, parsed.body(), references);
    }

    private static void validateFiles(SkillSource source, String root, boolean agentSkillsFormat) {
        for (String rawPath : source.files().keySet()) {
            String path = SkillSource.normalize(rawPath);
            if (!path.startsWith(root)) {
                throw new IllegalArgumentException("Skill file escapes its package root: " + rawPath);
            }
            String relative = path.substring(root.length());
            String lower = relative.toLowerCase(Locale.ROOT);
            String filename = lower.substring(lower.lastIndexOf('/') + 1);
            int extension = filename.lastIndexOf('.');
            if (extension >= 0
                    && EXECUTABLE_EXTENSIONS.contains(filename.substring(extension + 1))) {
                throw new IllegalArgumentException("Executable Skill files are not supported: " + rawPath);
            }
            if (lower.startsWith("scripts/") || lower.equals("scripts")) {
                throw new IllegalArgumentException("Skill scripts are not supported: " + rawPath);
            }
            if (agentSkillsFormat
                    && !relative.equals("SKILL.md")
                    && !relative.startsWith("references/")
                    && !relative.startsWith("assets/")) {
                throw new IllegalArgumentException("Unsupported Skill file: " + rawPath);
            }
        }
    }

    private static Map<String, String> discoveredReferences(SkillSource source, String root) {
        Map<String, String> references = new TreeMap<>();
        String prefix = root + "references/";
        source.files().forEach((path, contents) -> {
            if (path.startsWith(prefix)) {
                references.put(path.substring(root.length()), contents);
            }
        });
        return dev.openallay.util.Java8Collections.mapCopyOf(references);
    }

    private static Set<String> splitDependencies(String value) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        for (String item : value.split("[,\\s]+")) {
            if (!dev.openallay.util.Java8Strings.isBlank(item)) {
                dependencies.add(item);
            }
        }
        return dev.openallay.util.Java8Collections.setCopyOf(dependencies);
    }

    private static ParsedFrontmatter frontmatter(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) {
            throw new IllegalArgumentException("Skill is missing YAML frontmatter");
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalArgumentException("Skill frontmatter is not terminated");
        }
        Map<String, String> scalars = new LinkedHashMap<>();
        Map<String, List<String>> lists = new LinkedHashMap<>();
        Map<String, Map<String, String>> stringMaps = new LinkedHashMap<>();
        String activeCollection = null;
        CollectionKind collectionKind = null;
        for (String raw : normalized.substring(4, end).split("\n", -1)) {
            if (dev.openallay.util.Java8Strings.isBlank(raw) || dev.openallay.util.Java8Strings.stripLeading(raw).startsWith("#")) {
                continue;
            }
            if (Character.isWhitespace(raw.charAt(0))) {
                if (activeCollection == null) {
                    throw new IllegalArgumentException("Indented frontmatter value has no key");
                }
                String stripped = dev.openallay.util.Java8Strings.stripLeading(raw);
                if (collectionKind == CollectionKind.LIST && stripped.startsWith("- ")) {
                    lists.get(activeCollection).add(unquote(stripped.substring(2).trim()));
                    continue;
                }
                if (collectionKind == CollectionKind.STRING_MAP) {
                    int separator = stripped.indexOf(':');
                    if (separator <= 0) {
                        throw new IllegalArgumentException("Invalid metadata entry: " + raw);
                    }
                    String key = stripped.substring(0, separator).trim();
                    String rawValue = stripped.substring(separator + 1).trim();
                    if (dev.openallay.util.Java8Strings.isBlank(key) || dev.openallay.util.Java8Strings.isBlank(rawValue)) {
                        throw new IllegalArgumentException("Skill metadata keys and values must be strings");
                    }
                    String previous = stringMaps.get(activeCollection)
                            .put(key, metadataString(rawValue));
                    if (previous != null) {
                        throw new IllegalArgumentException("Duplicate Skill metadata key: " + key);
                    }
                    continue;
                }
                throw new IllegalArgumentException("Invalid frontmatter collection item: " + raw);
            }
            int separator = raw.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid frontmatter line: " + raw);
            }
            String key = raw.substring(0, separator).trim();
            if (scalars.containsKey(key) || lists.containsKey(key) || stringMaps.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate Skill frontmatter field: " + key);
            }
            String content = raw.substring(separator + 1).trim();
            activeCollection = null;
            collectionKind = null;
            if (content.isEmpty()) {
                activeCollection = key;
                if (key.equals("metadata")) {
                    collectionKind = CollectionKind.STRING_MAP;
                    stringMaps.put(key, new LinkedHashMap<>());
                } else {
                    collectionKind = CollectionKind.LIST;
                    lists.put(key, new ArrayList<>());
                }
            } else if (content.startsWith("[") && content.endsWith("]")) {
                List<String> values = new ArrayList<>();
                String inside = content.substring(1, content.length() - 1).trim();
                if (!inside.isEmpty()) {
                    for (String item : inside.split(",")) {
                        values.add(unquote(item.trim()));
                    }
                }
                lists.put(key, values);
            } else {
                scalars.put(key, unquote(content));
            }
        }
        String body = dev.openallay.util.Java8Strings.strip(normalized.substring(end + 5));
        if (dev.openallay.util.Java8Strings.isBlank(body)) {
            throw new IllegalArgumentException("Skill instructions must not be blank");
        }
        return new ParsedFrontmatter(scalars, lists, stringMaps, body);
    }

    private static String metadataString(String raw) {
        boolean quoted = raw.length() >= 2
                && ((raw.startsWith("\"") && raw.endsWith("\""))
                        || (raw.startsWith("'") && raw.endsWith("'")));
        if (!quoted) {
            String lower = raw.toLowerCase(Locale.ROOT);
            if (lower.matches("(?:true|false|null|~|[-+]?\\d+(?:\\.\\d+)?)")
                    || raw.startsWith("[")
                    || raw.startsWith("{")) {
                throw new IllegalArgumentException("Skill metadata values must be strings");
            }
        }
        return unquote(raw);
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean looksLikeUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("file:");
    }

    private enum CollectionKind {
        LIST,
        STRING_MAP
    }

    @dev.openallay.value.ValueType(ParsedFrontmatter.ValueSchemaProvider.class)
private static final class ParsedFrontmatter {
    private final Map<String, String> scalars;
    private final Map<String, List<String>> lists;
    private final Map<String, Map<String, String>> stringMaps;
    private final String body;
    private ParsedFrontmatter(Map<String, String> scalars, Map<String, List<String>> lists, Map<String, Map<String, String>> stringMaps, String body) {
        this.scalars = scalars;
        this.lists = lists;
        this.stringMaps = stringMaps;
        this.body = body;
    }
    public Map<String, String> scalars() { return scalars; }
    public Map<String, List<String>> lists() { return lists; }
    public Map<String, Map<String, String>> stringMaps() { return stringMaps; }
    public String body() { return body; }
private Set<String> keys() {
            LinkedHashSet<String> keys = new LinkedHashSet<>(scalars.keySet());
            keys.addAll(lists.keySet());
            keys.addAll(stringMaps.keySet());
            return dev.openallay.util.Java8Collections.setCopyOf(keys);
        }
private String requiredScalar(String key) {
            return optionalScalar(key).orElseThrow(
                    () -> new IllegalArgumentException("Missing Skill frontmatter field: " + key));
        }
private Optional<String> optionalScalar(String key) {
            if (lists.containsKey(key) || stringMaps.containsKey(key)) {
                throw new IllegalArgumentException("Skill frontmatter field must be a string: " + key);
            }
            return Optional.ofNullable(scalars.get(key));
        }
private List<String> list(String key) {
            if (scalars.containsKey(key) || stringMaps.containsKey(key)) {
                throw new IllegalArgumentException("Skill frontmatter field must be a list: " + key);
            }
            return dev.openallay.util.Java8Collections.listCopyOf(lists.getOrDefault(key, dev.openallay.util.Java8Collections.listOf()));
        }
private Map<String, String> stringMap(String key) {
            if (scalars.containsKey(key) || lists.containsKey(key)) {
                throw new IllegalArgumentException("Skill frontmatter field must be a string map: " + key);
            }
            return dev.openallay.util.Java8Collections.mapCopyOf(stringMaps.getOrDefault(key, dev.openallay.util.Java8Collections.mapOf()));
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ParsedFrontmatter)) return false;
        ParsedFrontmatter that = (ParsedFrontmatter) other;
        return java.util.Objects.equals(scalars, that.scalars) && java.util.Objects.equals(lists, that.lists) && java.util.Objects.equals(stringMaps, that.stringMaps) && java.util.Objects.equals(body, that.body);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scalars);
        hash = 31 * hash + java.util.Objects.hashCode(lists);
        hash = 31 * hash + java.util.Objects.hashCode(stringMaps);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        return hash;
    }
    @Override public String toString() { return "ParsedFrontmatter[scalars=" + scalars + ", lists=" + lists + ", stringMaps=" + stringMaps + ", body=" + body + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ParsedFrontmatter> schema() {
            return new dev.openallay.value.ValueSchema<>(ParsedFrontmatter.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ParsedFrontmatter>>asList(new dev.openallay.value.ValueSchema.Component<>(ParsedFrontmatter.class, "scalars", ParsedFrontmatter::scalars), new dev.openallay.value.ValueSchema.Component<>(ParsedFrontmatter.class, "lists", ParsedFrontmatter::lists), new dev.openallay.value.ValueSchema.Component<>(ParsedFrontmatter.class, "stringMaps", ParsedFrontmatter::stringMaps), new dev.openallay.value.ValueSchema.Component<>(ParsedFrontmatter.class, "body", ParsedFrontmatter::body)), arguments -> new ParsedFrontmatter((Map) arguments[0], (Map) arguments[1], (Map) arguments[2], (String) arguments[3]));
        }
    }
}
}
