package dev.openallay.context.minecraft;

import java.util.List;

/** Typed native holder operations used by the shared development-only recipe bootstrap. */
public interface MinecraftRecipeSeed {
    String holderId();
    String nativeRecipeClass();
    List<MinecraftRecipeInput> inputs();
    boolean known();
    int award();
}
