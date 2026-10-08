package dev.openallay.guide.ui.hud;

import dev.openallay.guide.ui.GuideUiConfig;
import java.util.Objects;

/** Pure visibility policy. It never reads Minecraft or starts Guide work. */
public final class GuideHudVisibility {
    private GuideHudVisibility() {}

    @dev.openallay.value.ValueType(Context.ValueSchemaProvider.class)
public static final class Context {
    private final boolean worldPresent;
    private final boolean playerPresent;
    private final boolean hudHidden;
    private final boolean debug;
    private final boolean hasScreen;
    private final boolean hasOverlay;
    public Context(boolean worldPresent, boolean playerPresent, boolean hudHidden, boolean debug, boolean hasScreen, boolean hasOverlay) {
        this.worldPresent = worldPresent;
        this.playerPresent = playerPresent;
        this.hudHidden = hudHidden;
        this.debug = debug;
        this.hasScreen = hasScreen;
        this.hasOverlay = hasOverlay;
    }
    public boolean worldPresent() { return worldPresent; }
    public boolean playerPresent() { return playerPresent; }
    public boolean hudHidden() { return hudHidden; }
    public boolean debug() { return debug; }
    public boolean hasScreen() { return hasScreen; }
    public boolean hasOverlay() { return hasOverlay; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Context)) return false;
        Context that = (Context) other;
        return worldPresent == that.worldPresent && playerPresent == that.playerPresent && hudHidden == that.hudHidden && debug == that.debug && hasScreen == that.hasScreen && hasOverlay == that.hasOverlay;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(worldPresent);
        hash = 31 * hash + Boolean.hashCode(playerPresent);
        hash = 31 * hash + Boolean.hashCode(hudHidden);
        hash = 31 * hash + Boolean.hashCode(debug);
        hash = 31 * hash + Boolean.hashCode(hasScreen);
        hash = 31 * hash + Boolean.hashCode(hasOverlay);
        return hash;
    }
    @Override public String toString() { return "Context[worldPresent=" + worldPresent + ", playerPresent=" + playerPresent + ", hudHidden=" + hudHidden + ", debug=" + debug + ", hasScreen=" + hasScreen + ", hasOverlay=" + hasOverlay + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Context> schema() {
            return new dev.openallay.value.ValueSchema<>(Context.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Context>>asList(new dev.openallay.value.ValueSchema.Component<>(Context.class, "worldPresent", Context::worldPresent), new dev.openallay.value.ValueSchema.Component<>(Context.class, "playerPresent", Context::playerPresent), new dev.openallay.value.ValueSchema.Component<>(Context.class, "hudHidden", Context::hudHidden), new dev.openallay.value.ValueSchema.Component<>(Context.class, "debug", Context::debug), new dev.openallay.value.ValueSchema.Component<>(Context.class, "hasScreen", Context::hasScreen), new dev.openallay.value.ValueSchema.Component<>(Context.class, "hasOverlay", Context::hasOverlay)), arguments -> new Context((Boolean) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5]));
        }
    }
}

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
