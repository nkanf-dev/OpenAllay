package dev.openallay.recipe;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.RecipeEntrySnapshot;
import dev.openallay.context.RecipeReference;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(RecipeSemanticGroup.ValueSchemaProvider.class)
public final class RecipeSemanticGroup {
    private final String fingerprint;
    private final RecipeEntrySnapshot representative;
    private final List<RecipeReference> references;
    private final List<EvidenceMetadata> evidence;
    public RecipeSemanticGroup(String fingerprint, RecipeEntrySnapshot representative, List<RecipeReference> references, List<EvidenceMetadata> evidence) {

        fingerprint = RecipeReference.requireGeneration(fingerprint);
        Objects.requireNonNull(representative, "representative");
        references = dev.openallay.util.Java8Collections.listCopyOf(references);
        evidence = dev.openallay.util.Java8Collections.listCopyOf(evidence);
        if (references.isEmpty() || evidence.isEmpty() || references.size() != evidence.size()) {
            throw new IllegalArgumentException("semantic group must retain every reference and evidence record");
        }
        if (!references.contains(representative.reference())) {
            throw new IllegalArgumentException("semantic group representative is not retained");
        }
            this.fingerprint = fingerprint;
        this.representative = representative;
        this.references = references;
        this.evidence = evidence;
    }
    public String fingerprint() { return fingerprint; }
    public RecipeEntrySnapshot representative() { return representative; }
    public List<RecipeReference> references() { return references; }
    public List<EvidenceMetadata> evidence() { return evidence; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeSemanticGroup)) return false;
        RecipeSemanticGroup that = (RecipeSemanticGroup) other;
        return java.util.Objects.equals(fingerprint, that.fingerprint) && java.util.Objects.equals(representative, that.representative) && java.util.Objects.equals(references, that.references) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(fingerprint);
        hash = 31 * hash + java.util.Objects.hashCode(representative);
        hash = 31 * hash + java.util.Objects.hashCode(references);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "RecipeSemanticGroup[fingerprint=" + fingerprint + ", representative=" + representative + ", references=" + references + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeSemanticGroup> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeSemanticGroup.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeSemanticGroup>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSemanticGroup.class, "fingerprint", RecipeSemanticGroup::fingerprint),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSemanticGroup.class, "representative", RecipeSemanticGroup::representative),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSemanticGroup.class, "references", RecipeSemanticGroup::references),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeSemanticGroup.class, "evidence", RecipeSemanticGroup::evidence)), arguments -> new RecipeSemanticGroup((String) arguments[0], (RecipeEntrySnapshot) arguments[1], (List) arguments[2], (List) arguments[3]));
        }
    }
}
