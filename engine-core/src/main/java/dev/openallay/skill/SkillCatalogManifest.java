package dev.openallay.skill;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Frozen document identities only. This does not carry plaintext or grant Tool permissions. */
public record SkillCatalogManifest(List<Document> documents) {
    public static final SkillCatalogManifest EMPTY = new SkillCatalogManifest(List.of());

    public SkillCatalogManifest {
        documents = List.copyOf(documents);
        Set<String> unique = new HashSet<>();
        for (Document document : documents) {
            if (!unique.add(document.name() + "\0" + document.document())) {
                throw new IllegalArgumentException("Duplicate Skill document identity");
            }
        }
        MapBySkill.requireExactReferences(documents);
    }

    private static final class MapBySkill {
        static void requireExactReferences(List<Document> documents) {
            var skills = documents.stream().collect(java.util.stream.Collectors.groupingBy(Document::name));
            skills.values().forEach(skill -> {
                var entry = skill.stream().filter(document -> document.document().equals("SKILL.md"))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Skill manifest has no entry document"));
                var references = skill.stream().filter(document -> !document.document().equals("SKILL.md"))
                        .map(Document::document).sorted().toList();
                for (Document document : skill) {
                    if (!document.source().equals(entry.source())
                            || !document.availableReferences().equals(references)) {
                        throw new IllegalArgumentException("Skill references do not match frozen manifest");
                    }
                }
            });
        }
    }

    public record Chunk(int offset, int end, String fingerprint) {
        public Chunk {
            if (offset < 0 || end < offset || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid Skill chunk identity");
            }
        }
    }

    public record Document(String name, String document, String source, String fingerprint,
            int length, List<Chunk> chunks, List<String> availableReferences, String description) {
        public Document {
            if (name == null || name.length() > 64 || !name.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || document == null || (!document.equals("SKILL.md")
                            && (!document.equals(SkillSource.normalize(document)) || !document.startsWith("references/")))
                    || document.chars().anyMatch(character -> character < 32 || character == 127)
                    || source == null || !source.matches("[A-Za-z0-9_-]+")
                    || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")
                    || length < 0 || description == null || description.isBlank() || description.length() > 1024) {
                throw new IllegalArgumentException("Invalid Skill document identity");
            }
            chunks = List.copyOf(chunks);
            availableReferences = List.copyOf(availableReferences);
            if (!availableReferences.equals(availableReferences.stream().distinct().sorted().toList())) {
                throw new IllegalArgumentException("Invalid Skill references");
            }
            for (String reference : availableReferences) {
                if (!reference.startsWith("references/") || !reference.equals(SkillSource.normalize(reference))
                        || reference.chars().anyMatch(character -> character < 32 || character == 127)) {
                    throw new IllegalArgumentException("Invalid Skill reference path");
                }
            }
            int next = 0;
            for (Chunk chunk : chunks) {
                if (chunk.offset() != next || chunk.end() > length
                        || (chunk.end() == next && length != 0)) {
                    throw new IllegalArgumentException("Skill chunks must cover the exact document");
                }
                next = chunk.end();
            }
            if (chunks.isEmpty() || next != length) {
                throw new IllegalArgumentException("Skill chunks do not cover the document");
            }
        }

        public RetainedSkillContext.Key key() {
            return new RetainedSkillContext.Key(name, document, source, fingerprint);
        }

        public Chunk chunkAt(int offset) {
            return chunks.stream().filter(chunk -> chunk.offset() == offset).findFirst().orElse(null);
        }
    }

    public String metadataPrompt() {
        return documents.stream().filter(document -> document.document().equals("SKILL.md"))
                .map(document -> "  <skill>\n    <name>" + xml(document.name())
                        + "</name>\n    <description>" + xml(document.description())
                        + "</description>\n  </skill>")
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    static String source(String owner, SkillDocument skill) {
        return LoadSkillTool.fingerprint(owner + "\0" + skill.sourceFingerprint());
    }

    public static SkillCatalogManifest capture(SkillCatalog catalog, String owner) {
        return new SkillCatalogManifest(catalog.metadata().stream()
                .flatMap(metadata -> catalog.find(metadata.name()).stream())
                .flatMap(skill -> skill.documents().entrySet().stream().map(entry -> {
                    SkillDocument.Text text = entry.getValue();
                    return new Document(skill.metadata().name(), entry.getKey(),
                            source(owner, skill), text.fingerprint(), text.contents().length(),
                            text.chunks(), skill.references().keySet().stream().sorted().toList(),
                            skill.metadata().description());
                }))
                .sorted(java.util.Comparator.comparing(Document::name).thenComparing(Document::document))
                .toList());
    }
}
