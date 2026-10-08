package dev.openallay.context.minecraft;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Owner-thread native recipe facts. Shared code detaches these into immutable engine snapshots. */
@dev.openallay.value.ValueType(MinecraftRecipeInput.ValueSchemaProvider.class)
public final class MinecraftRecipeInput {
    private final String id;
    private final String type;
    private final List<Ingredient> ingredients;
    private final List<ItemStack> outputs;
    private final int width;
    private final int height;
    private final boolean shaped;
    private final boolean crafting;
    private final String workstation;
    public MinecraftRecipeInput(String id, String type, List<Ingredient> ingredients, List<ItemStack> outputs, int width, int height, boolean shaped, boolean crafting, String workstation) {

        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        ingredients = List.copyOf(ingredients);
        outputs = outputs.stream().map(ItemStack::copy).toList();

        this.id = id;
        this.type = type;
        this.ingredients = ingredients;
        this.outputs = outputs;
        this.width = width;
        this.height = height;
        this.shaped = shaped;
        this.crafting = crafting;
        this.workstation = workstation;
    }
    public String id() { return id; }
    public String type() { return type; }
    public List<Ingredient> ingredients() { return ingredients; }
    public List<ItemStack> outputs() { return outputs; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean shaped() { return shaped; }
    public boolean crafting() { return crafting; }
    public String workstation() { return workstation; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MinecraftRecipeInput)) return false;
        MinecraftRecipeInput that = (MinecraftRecipeInput) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(ingredients, that.ingredients) && java.util.Objects.equals(outputs, that.outputs) && width == that.width && height == that.height && shaped == that.shaped && crafting == that.crafting && java.util.Objects.equals(workstation, that.workstation);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(ingredients);
        hash = 31 * hash + java.util.Objects.hashCode(outputs);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Boolean.hashCode(shaped);
        hash = 31 * hash + Boolean.hashCode(crafting);
        hash = 31 * hash + java.util.Objects.hashCode(workstation);
        return hash;
    }
    @Override public String toString() { return "MinecraftRecipeInput[id=" + id + ", type=" + type + ", ingredients=" + ingredients + ", outputs=" + outputs + ", width=" + width + ", height=" + height + ", shaped=" + shaped + ", crafting=" + crafting + ", workstation=" + workstation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MinecraftRecipeInput> schema() {
            return new dev.openallay.value.ValueSchema<>(MinecraftRecipeInput.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MinecraftRecipeInput>>asList(new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "id", MinecraftRecipeInput::id), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "type", MinecraftRecipeInput::type), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "ingredients", MinecraftRecipeInput::ingredients), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "outputs", MinecraftRecipeInput::outputs), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "width", MinecraftRecipeInput::width), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "height", MinecraftRecipeInput::height), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "shaped", MinecraftRecipeInput::shaped), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "crafting", MinecraftRecipeInput::crafting), new dev.openallay.value.ValueSchema.Component<>(MinecraftRecipeInput.class, "workstation", MinecraftRecipeInput::workstation)), arguments -> new MinecraftRecipeInput((String) arguments[0], (String) arguments[1], (List) arguments[2], (List) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Boolean) arguments[6], (Boolean) arguments[7], (String) arguments[8]));
        }
    }
}
