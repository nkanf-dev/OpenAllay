package dev.openallay.context;

import java.util.List;

@dev.openallay.value.ValueType(IngredientAlternativeSnapshot.ValueSchemaProvider.class)
public final class IngredientAlternativeSnapshot {
    private final String kind;
    private final String id;
    private final List<String> resolvedItems;
    public IngredientAlternativeSnapshot(String kind, String id, List<String> resolvedItems) {

        kind = ContextValidation.nonBlank(kind, "kind");
        if (!kind.equals("item") && !kind.equals("tag")) {
            throw new IllegalArgumentException("ingredient alternative kind must be item or tag");
        }
        id = ContextValidation.identifier(id, "id");
        resolvedItems = dev.openallay.util.Java8Collections.listCopyOf(resolvedItems);
        resolvedItems.forEach(value -> ContextValidation.identifier(value, "resolved item"));
            this.kind = kind;
        this.id = id;
        this.resolvedItems = resolvedItems;
    }
    public String kind() { return kind; }
    public String id() { return id; }
    public List<String> resolvedItems() { return resolvedItems; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IngredientAlternativeSnapshot)) return false;
        IngredientAlternativeSnapshot that = (IngredientAlternativeSnapshot) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(id, that.id) && java.util.Objects.equals(resolvedItems, that.resolvedItems);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(resolvedItems);
        return hash;
    }
    @Override public String toString() { return "IngredientAlternativeSnapshot[kind=" + kind + ", id=" + id + ", resolvedItems=" + resolvedItems + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<IngredientAlternativeSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(IngredientAlternativeSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<IngredientAlternativeSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(IngredientAlternativeSnapshot.class, "kind", IngredientAlternativeSnapshot::kind),
                    new dev.openallay.value.ValueSchema.Component<>(IngredientAlternativeSnapshot.class, "id", IngredientAlternativeSnapshot::id),
                    new dev.openallay.value.ValueSchema.Component<>(IngredientAlternativeSnapshot.class, "resolvedItems", IngredientAlternativeSnapshot::resolvedItems)), arguments -> new IngredientAlternativeSnapshot((String) arguments[0], (String) arguments[1], (List) arguments[2]));
        }
    }
}
