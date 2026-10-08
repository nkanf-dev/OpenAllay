package dev.openallay.integration.jei;

import dev.openallay.client.gui.GuideGraphics;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.runtime.IJeiRuntime;

/** Product layout operations. Native publication handles stay in the selected factory. */
interface NativeRecipeLayout<T> {
    int width();
    int height();
    List<JeiIngredientSlot> captureSlots(IJeiRuntime runtime);
    void setPosition(int x, int y);
    void drawRecipe(GuideGraphics graphics, int mouseX, int mouseY);
    /** Preserve real native ingredient and category tooltip callbacks. */
    void drawOverlays(GuideGraphics graphics, int mouseX, int mouseY);
    Optional<Runnable> gameTick();
}
