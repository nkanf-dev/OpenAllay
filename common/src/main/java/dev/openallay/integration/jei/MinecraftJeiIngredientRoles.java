package dev.openallay.integration.jei;

import mezz.jei.api.recipe.RecipeIngredientRole;

/** Native recipe viewer role naming; catalyst detachment and task behavior stay shared. */
final class MinecraftJeiIngredientRoles {
    private MinecraftJeiIngredientRoles() {}
    static boolean craftingStation(RecipeIngredientRole role) {
        return role == RecipeIngredientRole.CRAFTING_STATION;
    }
}
