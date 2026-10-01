package dev.openallay.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable source documents; identities are computed once when the catalog is built. */
public final class SkillDocument {
    public record Text(String contents, String fingerprint, java.util.List<SkillCatalogManifest.Chunk> chunks) {
        public Text { chunks = java.util.List.copyOf(chunks); }
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
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("Skill instructions must not be blank");
        }
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.sourceFingerprint = sourceFingerprint;
        this.instructions = instructions;
        this.references = Map.copyOf(references);
        Map<String, Text> captured = new HashMap<>();
        captured.put("SKILL.md", text(instructions));
        this.references.forEach((name, contents) -> captured.put(name, text(contents)));
        documents = Map.copyOf(captured);
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
        return other instanceof SkillDocument document
                && metadata.equals(document.metadata)
                && instructions.equals(document.instructions)
                && references.equals(document.references);
    }

    @Override
    public int hashCode() { return Objects.hash(metadata, instructions, references); }
}
