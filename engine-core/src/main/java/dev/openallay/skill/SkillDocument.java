package dev.openallay.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable source documents; identities are computed once when the catalog is built. */
public final class SkillDocument {
    @dev.openallay.value.ValueType(Text.ValueSchemaProvider.class)
public static final class Text {
    private final String contents;
    private final String fingerprint;
    private final java.util.List<SkillCatalogManifest.Chunk> chunks;
    public Text(String contents, String fingerprint, java.util.List<SkillCatalogManifest.Chunk> chunks) {
 chunks = dev.openallay.util.Java8Collections.listCopyOf(chunks);
        this.contents = contents;
        this.fingerprint = fingerprint;
        this.chunks = chunks;
    }
    public String contents() { return contents; }
    public String fingerprint() { return fingerprint; }
    public java.util.List<SkillCatalogManifest.Chunk> chunks() { return chunks; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Text)) return false;
        Text that = (Text) other;
        return java.util.Objects.equals(contents, that.contents) && java.util.Objects.equals(fingerprint, that.fingerprint) && java.util.Objects.equals(chunks, that.chunks);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(contents);
        hash = 31 * hash + java.util.Objects.hashCode(fingerprint);
        hash = 31 * hash + java.util.Objects.hashCode(chunks);
        return hash;
    }
    @Override public String toString() { return "Text[contents=" + contents + ", fingerprint=" + fingerprint + ", chunks=" + chunks + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Text> schema() {
            return new dev.openallay.value.ValueSchema<>(Text.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Text>>asList(new dev.openallay.value.ValueSchema.Component<>(Text.class, "contents", Text::contents), new dev.openallay.value.ValueSchema.Component<>(Text.class, "fingerprint", Text::fingerprint), new dev.openallay.value.ValueSchema.Component<>(Text.class, "chunks", Text::chunks)), arguments -> new Text((String) arguments[0], (String) arguments[1], (java.util.List) arguments[2]));
        }
    }
}

    private final SkillMetadata metadata;
    private final String instructions;
    private final Map<String, String> references;
    private final Map<String, Text> documents;
    private final String sourceFingerprint;

    public SkillDocument(SkillMetadata metadata, String instructions, Map<String, String> references) {
        this(metadata, instructions, references,
                LoadSkillTool.fingerprint(metadata.origin().name() + "\0" + metadata.provenance()));
    }

    private SkillDocument(SkillMetadata metadata, String instructions, Map<String, String> references,
            String sourceFingerprint) {
        if (instructions == null || dev.openallay.util.Java8Strings.isBlank(instructions)) {
            throw new IllegalArgumentException("Skill instructions must not be blank");
        }
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.sourceFingerprint = sourceFingerprint;
        this.instructions = instructions;
        this.references = dev.openallay.util.Java8Collections.mapCopyOf(references);
        Map<String, Text> captured = new HashMap<>();
        captured.put("SKILL.md", text(instructions));
        this.references.forEach((name, contents) -> captured.put(name, text(contents)));
        documents = dev.openallay.util.Java8Collections.mapCopyOf(captured);
    }

    private static Text text(String contents) {
        java.util.List<SkillCatalogManifest.Chunk> chunks = new java.util.ArrayList<>();
        int offset = 0;
        do {
            int end = LoadSkillTool.chunkEnd(contents, offset);
            chunks.add(new SkillCatalogManifest.Chunk(offset, end,
                    LoadSkillTool.fingerprint(contents.substring(offset, end))));
            offset = end;
        } while (offset < contents.length());
        return new Text(contents, LoadSkillTool.fingerprint(contents), chunks);
    }

    String sourceFingerprint() { return sourceFingerprint; }
    public SkillMetadata metadata() { return metadata; }
    public String instructions() { return instructions; }
    public Map<String, String> references() { return references; }
    public Map<String, Text> documents() { return documents; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SkillDocument)) return false;
        SkillDocument document = (SkillDocument) other;
        return metadata.equals(document.metadata)
                && instructions.equals(document.instructions)
                && references.equals(document.references);
    }

    @Override
    public int hashCode() { return Objects.hash(metadata, instructions, references); }
}
