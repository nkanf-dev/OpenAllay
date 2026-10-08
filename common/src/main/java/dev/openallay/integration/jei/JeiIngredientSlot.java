package dev.openallay.integration.jei;

import java.util.List;

/** Only semantic roles used by the shared provider, not publication slot geometry. */
@dev.openallay.value.ValueType(JeiIngredientSlot.ValueSchemaProvider.class)
final class JeiIngredientSlot {
    private final Role role;
    private final List<JeiIngredientValue> values;
    JeiIngredientSlot(Role role, List<JeiIngredientValue> values) {
 values = dev.openallay.util.Java8Collections.listCopyOf(values);
        for (JeiIngredientValue value : values) JeiIngredientValue.requireKnown(value);
        this.role = role;
        this.values = values;
    }
    public Role role() { return role; }
    public List<JeiIngredientValue> values() { return values; }
enum Role { INPUT, OUTPUT, WORKSTATION, RENDER_ONLY }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JeiIngredientSlot)) return false;
        JeiIngredientSlot that = (JeiIngredientSlot) other;
        return java.util.Objects.equals(role, that.role) && java.util.Objects.equals(values, that.values);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(role);
        hash = 31 * hash + java.util.Objects.hashCode(values);
        return hash;
    }
    @Override public String toString() { return "JeiIngredientSlot[role=" + role + ", values=" + values + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<JeiIngredientSlot> schema() {
            return new dev.openallay.value.ValueSchema<>(JeiIngredientSlot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<JeiIngredientSlot>>asList(new dev.openallay.value.ValueSchema.Component<>(JeiIngredientSlot.class, "role", JeiIngredientSlot::role), new dev.openallay.value.ValueSchema.Component<>(JeiIngredientSlot.class, "values", JeiIngredientSlot::values)), arguments -> new JeiIngredientSlot((Role) arguments[0], (List) arguments[1]));
        }
    }
}
