package dev.openallay.skill;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Frozen document identities only. This does not carry plaintext or grant Tool permissions. */
@dev.openallay.value.ValueType(SkillCatalogManifest.ValueSchemaProvider.class)
public final class SkillCatalogManifest {
    private final List<Document> documents;
    public SkillCatalogManifest(List<Document> documents) {

        documents = dev.openallay.util.Java8Collections.listCopyOf(documents);
        Set<String> unique = new HashSet<>();
        for (Document document : documents) {
            if (!unique.add(document.name() + "\0" + document.document())) {
                throw new IllegalArgumentException("Duplicate Skill document identity");
            }
        }
        MapBySkill.requireExactReferences(documents);

        this.documents = documents;
    }
    public List<Document> documents() { return documents; }
public static final SkillCatalogManifest EMPTY = new SkillCatalogManifest(dev.openallay.util.Java8Collections.listOf());
private static final class MapBySkill {
        static void requireExactReferences(List<Document> documents) {
            java.util.Map<java.lang.String, java.util.List<dev.openallay.skill.SkillCatalogManifest.Document>> skills = documents.stream().collect(java.util.stream.Collectors.groupingBy(Document::name));
            skills.values().forEach(skill -> {
                dev.openallay.skill.SkillCatalogManifest.Document entry = skill.stream().filter(document -> document.document().equals("SKILL.md"))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Skill manifest has no entry document"));
                java.util.List<java.lang.String> references = dev.openallay.util.Java8Collections.toList(skill.stream().filter(document -> !document.document().equals("SKILL.md"))
                        .map(Document::document).sorted());
                for (Document document : skill) {
                    if (!document.source().equals(entry.source())
                            || !document.availableReferences().equals(references)) {
                        throw new IllegalArgumentException("Skill references do not match frozen manifest");
                    }
                }
            });
        }
    }
@dev.openallay.value.ValueType(Chunk.ValueSchemaProvider.class)
public static final class Chunk {
    private final int offset;
    private final int end;
    private final String fingerprint;
    public Chunk(int offset, int end, String fingerprint) {

            if (offset < 0 || end < offset || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Invalid Skill chunk identity");
            }

        this.offset = offset;
        this.end = end;
        this.fingerprint = fingerprint;
    }
    public int offset() { return offset; }
    public int end() { return end; }
    public String fingerprint() { return fingerprint; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Chunk)) return false;
        Chunk that = (Chunk) other;
        return offset == that.offset && end == that.end && java.util.Objects.equals(fingerprint, that.fingerprint);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(offset);
        hash = 31 * hash + Integer.hashCode(end);
        hash = 31 * hash + java.util.Objects.hashCode(fingerprint);
        return hash;
    }
    @Override public String toString() { return "Chunk[offset=" + offset + ", end=" + end + ", fingerprint=" + fingerprint + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Chunk> schema() {
            return new dev.openallay.value.ValueSchema<>(Chunk.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Chunk>>asList(new dev.openallay.value.ValueSchema.Component<>(Chunk.class, "offset", Chunk::offset), new dev.openallay.value.ValueSchema.Component<>(Chunk.class, "end", Chunk::end), new dev.openallay.value.ValueSchema.Component<>(Chunk.class, "fingerprint", Chunk::fingerprint)), arguments -> new Chunk((Integer) arguments[0], (Integer) arguments[1], (String) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(Document.ValueSchemaProvider.class)
public static final class Document {
    private final String name;
    private final String document;
    private final String source;
    private final String fingerprint;
    private final int length;
    private final List<Chunk> chunks;
    private final List<String> availableReferences;
    private final String description;
    public Document(String name, String document, String source, String fingerprint, int length, List<Chunk> chunks, List<String> availableReferences, String description) {

            if (name == null || name.length() > 64 || !name.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || document == null || (!document.equals("SKILL.md")
                            && (!document.equals(SkillSource.normalize(document)) || !document.startsWith("references/")))
                    || document.chars().anyMatch(character -> character < 32 || character == 127)
                    || source == null || !source.matches("[A-Za-z0-9_-]+")
                    || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}")
                    || length < 0 || description == null || dev.openallay.util.Java8Strings.isBlank(description) || description.length() > 1024) {
                throw new IllegalArgumentException("Invalid Skill document identity");
            }
            chunks = dev.openallay.util.Java8Collections.listCopyOf(chunks);
            availableReferences = dev.openallay.util.Java8Collections.listCopyOf(availableReferences);
            if (!availableReferences.equals(dev.openallay.util.Java8Collections.toList(availableReferences.stream().distinct().sorted()))) {
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

        this.name = name;
        this.document = document;
        this.source = source;
        this.fingerprint = fingerprint;
        this.length = length;
        this.chunks = chunks;
        this.availableReferences = availableReferences;
        this.description = description;
    }
    public String name() { return name; }
    public String document() { return document; }
    public String source() { return source; }
    public String fingerprint() { return fingerprint; }
    public int length() { return length; }
    public List<Chunk> chunks() { return chunks; }
    public List<String> availableReferences() { return availableReferences; }
    public String description() { return description; }
public RetainedSkillContext.Key key() {
            return new RetainedSkillContext.Key(name, document, source, fingerprint);
        }
public Chunk chunkAt(int offset) {
            return chunks.stream().filter(chunk -> chunk.offset() == offset).findFirst().orElse(null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Document)) return false;
        Document that = (Document) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(document, that.document) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(fingerprint, that.fingerprint) && length == that.length && java.util.Objects.equals(chunks, that.chunks) && java.util.Objects.equals(availableReferences, that.availableReferences) && java.util.Objects.equals(description, that.description);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(document);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(fingerprint);
        hash = 31 * hash + Integer.hashCode(length);
        hash = 31 * hash + java.util.Objects.hashCode(chunks);
        hash = 31 * hash + java.util.Objects.hashCode(availableReferences);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        return hash;
    }
    @Override public String toString() { return "Document[name=" + name + ", document=" + document + ", source=" + source + ", fingerprint=" + fingerprint + ", length=" + length + ", chunks=" + chunks + ", availableReferences=" + availableReferences + ", description=" + description + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Document> schema() {
            return new dev.openallay.value.ValueSchema<>(Document.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Document>>asList(new dev.openallay.value.ValueSchema.Component<>(Document.class, "name", Document::name), new dev.openallay.value.ValueSchema.Component<>(Document.class, "document", Document::document), new dev.openallay.value.ValueSchema.Component<>(Document.class, "source", Document::source), new dev.openallay.value.ValueSchema.Component<>(Document.class, "fingerprint", Document::fingerprint), new dev.openallay.value.ValueSchema.Component<>(Document.class, "length", Document::length), new dev.openallay.value.ValueSchema.Component<>(Document.class, "chunks", Document::chunks), new dev.openallay.value.ValueSchema.Component<>(Document.class, "availableReferences", Document::availableReferences), new dev.openallay.value.ValueSchema.Component<>(Document.class, "description", Document::description)), arguments -> new Document((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (Integer) arguments[4], (List) arguments[5], (List) arguments[6], (String) arguments[7]));
        }
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
        return new SkillCatalogManifest(dev.openallay.util.Java8Collections.toList(catalog.metadata().stream()
                .flatMap(metadata -> catalog.find(metadata.name()).map(java.util.stream.Stream::of).orElseGet(java.util.stream.Stream::empty))
                .flatMap(skill -> skill.documents().entrySet().stream().map(entry -> {
                    SkillDocument.Text text = entry.getValue();
                    return new Document(skill.metadata().name(), entry.getKey(),
                            source(owner, skill), text.fingerprint(), text.contents().length(),
                            text.chunks(), dev.openallay.util.Java8Collections.toList(skill.references().keySet().stream().sorted()),
                            skill.metadata().description());
                }))
                .sorted(java.util.Comparator.comparing(Document::name).thenComparing(Document::document))
                ));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillCatalogManifest)) return false;
        SkillCatalogManifest that = (SkillCatalogManifest) other;
        return java.util.Objects.equals(documents, that.documents);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(documents);
        return hash;
    }
    @Override public String toString() { return "SkillCatalogManifest[documents=" + documents + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkillCatalogManifest> schema() {
            return new dev.openallay.value.ValueSchema<>(SkillCatalogManifest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkillCatalogManifest>>asList(new dev.openallay.value.ValueSchema.Component<>(SkillCatalogManifest.class, "documents", SkillCatalogManifest::documents)), arguments -> new SkillCatalogManifest((List) arguments[0]));
        }
    }
}
