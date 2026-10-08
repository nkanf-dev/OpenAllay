package dev.openallay.integration.jei;

import java.util.Objects;
import java.util.OptionalLong;


/** Detached publication projection. Native item custody remains inside optional JEI code. */
interface JeiIngredientValue {
        /** Exact published variant admission at the real aggregate/provider boundary. Null is unchanged. */
        static JeiIngredientValue requireKnown(JeiIngredientValue value) {
            if (value == null) return null;
            Class<?> actual = value.getClass();
            if (actual == Item.class || actual == Fluid.class || actual == Unsupported.class) return value;
            throw new IncompatibleClassChangeError("Unknown native JeiIngredientValue subtype");
        }

    @dev.openallay.value.ValueType(Item.ValueSchemaProvider.class)
public static final class Item implements JeiIngredientValue {
    private final net.minecraft.world.item.ItemStack stack;
    public Item(net.minecraft.world.item.ItemStack stack) {
 Objects.requireNonNull(stack, "stack");
        this.stack = stack;
    }
    public net.minecraft.world.item.ItemStack stack() { return stack; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Item)) return false;
        Item that = (Item) other;
        return java.util.Objects.equals(stack, that.stack);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(stack);
        return hash;
    }
    @Override public String toString() { return "Item[stack=" + stack + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Item> schema() {
            return new dev.openallay.value.ValueSchema<>(Item.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Item>>asList(new dev.openallay.value.ValueSchema.Component<>(Item.class, "stack", Item::stack)), arguments -> new Item((net.minecraft.world.item.ItemStack) arguments[0]));
        }
    }
}
    @dev.openallay.value.ValueType(Fluid.ValueSchemaProvider.class)
public static final class Fluid implements JeiIngredientValue {
    private final String id;
    private final OptionalLong amount;
    public Fluid(String id, OptionalLong amount) {
 Objects.requireNonNull(id, "id"); Objects.requireNonNull(amount, "amount");
        this.id = id;
        this.amount = amount;
    }
    public String id() { return id; }
    public OptionalLong amount() { return amount; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Fluid)) return false;
        Fluid that = (Fluid) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(amount, that.amount);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(amount);
        return hash;
    }
    @Override public String toString() { return "Fluid[id=" + id + ", amount=" + amount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Fluid> schema() {
            return new dev.openallay.value.ValueSchema<>(Fluid.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Fluid>>asList(new dev.openallay.value.ValueSchema.Component<>(Fluid.class, "id", Fluid::id), new dev.openallay.value.ValueSchema.Component<>(Fluid.class, "amount", Fluid::amount)), arguments -> new Fluid((String) arguments[0], (OptionalLong) arguments[1]));
        }
    }
}
    @dev.openallay.value.ValueType(Unsupported.ValueSchemaProvider.class)
public static final class Unsupported implements JeiIngredientValue {
    private final String ingredientType;
    public Unsupported(String ingredientType) {
        this.ingredientType = ingredientType;
    }
    public String ingredientType() { return ingredientType; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Unsupported)) return false;
        Unsupported that = (Unsupported) other;
        return java.util.Objects.equals(ingredientType, that.ingredientType);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(ingredientType);
        return hash;
    }
    @Override public String toString() { return "Unsupported[ingredientType=" + ingredientType + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Unsupported> schema() {
            return new dev.openallay.value.ValueSchema<>(Unsupported.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Unsupported>>asList(new dev.openallay.value.ValueSchema.Component<>(Unsupported.class, "ingredientType", Unsupported::ingredientType)), arguments -> new Unsupported((String) arguments[0]));
        }
    }
}
}
