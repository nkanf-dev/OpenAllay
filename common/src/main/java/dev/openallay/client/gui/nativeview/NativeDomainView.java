package dev.openallay.client.gui.nativeview;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.guide.ui.GuideUiLayout;
import net.minecraft.client.gui.Font;

/** Visible client-thread object; never persisted or exposed to model context. */
public interface NativeDomainView extends AutoCloseable {
    String providerId();

    NativeDomainViewBinding.Family family();

    default void tick() {}

    void render(RenderContext context);

    @Override
    default void close() {}

    @dev.openallay.value.ValueType(RenderContext.ValueSchemaProvider.class)
public static final class RenderContext {
    private final GuideGraphics graphics;
    private final Font font;
    private final GuideUiLayout.Rect bounds;
    private final int mouseX;
    private final int mouseY;
    private final long presentationTicks;
    public RenderContext(GuideGraphics graphics, Font font, GuideUiLayout.Rect bounds, int mouseX, int mouseY, long presentationTicks) {

            java.util.Objects.requireNonNull(graphics, "graphics");
            java.util.Objects.requireNonNull(font, "font");
            java.util.Objects.requireNonNull(bounds, "bounds");

        this.graphics = graphics;
        this.font = font;
        this.bounds = bounds;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.presentationTicks = presentationTicks;
    }
    public GuideGraphics graphics() { return graphics; }
    public Font font() { return font; }
    public GuideUiLayout.Rect bounds() { return bounds; }
    public int mouseX() { return mouseX; }
    public int mouseY() { return mouseY; }
    public long presentationTicks() { return presentationTicks; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RenderContext)) return false;
        RenderContext that = (RenderContext) other;
        return java.util.Objects.equals(graphics, that.graphics) && java.util.Objects.equals(font, that.font) && java.util.Objects.equals(bounds, that.bounds) && mouseX == that.mouseX && mouseY == that.mouseY && presentationTicks == that.presentationTicks;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(graphics);
        hash = 31 * hash + java.util.Objects.hashCode(font);
        hash = 31 * hash + java.util.Objects.hashCode(bounds);
        hash = 31 * hash + Integer.hashCode(mouseX);
        hash = 31 * hash + Integer.hashCode(mouseY);
        hash = 31 * hash + Long.hashCode(presentationTicks);
        return hash;
    }
    @Override public String toString() { return "RenderContext[graphics=" + graphics + ", font=" + font + ", bounds=" + bounds + ", mouseX=" + mouseX + ", mouseY=" + mouseY + ", presentationTicks=" + presentationTicks + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RenderContext> schema() {
            return new dev.openallay.value.ValueSchema<>(RenderContext.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RenderContext>>asList(new dev.openallay.value.ValueSchema.Component<>(RenderContext.class, "graphics", RenderContext::graphics), new dev.openallay.value.ValueSchema.Component<>(RenderContext.class, "font", RenderContext::font), new dev.openallay.value.ValueSchema.Component<>(RenderContext.class, "bounds", RenderContext::bounds), new dev.openallay.value.ValueSchema.Component<>(RenderContext.class, "mouseX", RenderContext::mouseX), new dev.openallay.value.ValueSchema.Component<>(RenderContext.class, "mouseY", RenderContext::mouseY), new dev.openallay.value.ValueSchema.Component<>(RenderContext.class, "presentationTicks", RenderContext::presentationTicks)), arguments -> new RenderContext((GuideGraphics) arguments[0], (Font) arguments[1], (GuideUiLayout.Rect) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (Long) arguments[5]));
        }
    }
}
}
