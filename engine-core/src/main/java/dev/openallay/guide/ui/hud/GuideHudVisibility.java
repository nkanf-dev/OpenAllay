package dev.openallay.guide.ui.hud;

import dev.openallay.guide.ui.GuideUiConfig;
import java.util.Objects;

/** Pure visibility policy. It never reads Minecraft or starts Guide work. */
public final class GuideHudVisibility {
    private GuideHudVisibility() {}

    public record Context(
            boolean worldPresent,
            boolean playerPresent,
            boolean hudHidden,
            boolean debug,
            boolean hasScreen,
            boolean hasOverlay) {}

    public static boolean isVisible(GuideUiConfig.Hud hud, Context context) {
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(context, "context");
        return hud.enabled()
                && context.worldPresent()
                && context.playerPresent()
                && !context.hudHidden()
                && !context.hasOverlay()
                && (!hud.hideWithDebug() || !context.debug())
                && (!hud.hideOnOtherScreens() || !context.hasScreen());
    }
}
