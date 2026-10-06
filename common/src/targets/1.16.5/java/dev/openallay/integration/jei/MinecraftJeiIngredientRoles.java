package dev.openallay.integration.jei;

/** JEI7 workstation projection comes only from the manager category catalyst list. */
final class MinecraftJeiIngredientRoles {
    private MinecraftJeiIngredientRoles() {}
    static boolean craftingStation(JeiIngredientSlot.Role role) { return role == JeiIngredientSlot.Role.WORKSTATION; }
}
