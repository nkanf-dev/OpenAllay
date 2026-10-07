package dev.openallay.client.gui.hud;

import dev.openallay.guide.ui.GuideUiConfig;
import java.util.Objects;

/** GUI-space HUD geometry. Viewport clipping never rewrites the saved placement. */
public final class GuideHudLayout {
    public static final int MARGIN = 6;
    private static final int MAX_OFFSET = 4096;

    private GuideHudLayout() {}

    public static Rect calculate(int viewportWidth, int viewportHeight, GuideUiConfig.Hud hud) {
        requireViewport(viewportWidth, viewportHeight);
        Objects.requireNonNull(hud, "hud");
        double marginX = margin(viewportWidth);
        double marginY = margin(viewportHeight);
        int contentWidth = effectiveDimension(hud.width(), viewportWidth, marginX, hud.scale());
        int contentHeight = effectiveDimension(hud.height(), viewportHeight, marginY, hud.scale());
        double width = contentWidth * hud.scale();
        double height = contentHeight * hud.scale();
        double x = clamp(hud.anchor().xFactor() * (viewportWidth - width) + hud.offsetX(),
                marginX, viewportWidth - marginX - width);
        double y = clamp(hud.anchor().yFactor() * (viewportHeight - height) + hud.offsetY(),
                marginY, viewportHeight - marginY - height);
        return new Rect(x, y, width, height, contentWidth, contentHeight, hud.scale());
    }

    /** Converts a dragged screen position back to offsets for the current anchor. */
    public static GuideUiConfig.Hud placementAt(
            int viewportWidth, int viewportHeight, GuideUiConfig.Hud hud, double x, double y) {
        Rect bounds = calculate(viewportWidth, viewportHeight, hud);
        double clampedX = clamp(x, margin(viewportWidth),
                viewportWidth - margin(viewportWidth) - bounds.width());
        double clampedY = clamp(y, margin(viewportHeight),
                viewportHeight - margin(viewportHeight) - bounds.height());
        int offsetX = offset(clampedX - hud.anchor().xFactor() * (viewportWidth - bounds.width()));
        int offsetY = offset(clampedY - hud.anchor().yFactor() * (viewportHeight - bounds.height()));
        return hud.withPlacement(hud.anchor(), offsetX, offsetY, hud.width(), hud.height(), hud.scale());
    }

    /** Anchor selection changes the origin, not the physical position of the HUD. */
    public static GuideUiConfig.Hud withAnchorKeepingPosition(
            int viewportWidth, int viewportHeight, GuideUiConfig.Hud hud, GuideUiConfig.Anchor anchor) {
        Objects.requireNonNull(anchor, "anchor");
        Rect before = calculate(viewportWidth, viewportHeight, hud);
        GuideUiConfig.Hud reanchored = hud.withPlacement(
                anchor, hud.offsetX(), hud.offsetY(), hud.width(), hud.height(), hud.scale());
        return placementAt(viewportWidth, viewportHeight, reanchored, before.x(), before.y());
    }

    /** A resize changes unscaled content size. It does not change text/UI scale or the top-left. */
    public static GuideUiConfig.Hud resizeAt(
            int viewportWidth, int viewportHeight, GuideUiConfig.Hud hud,
            double x, double y, double screenWidth, double screenHeight) {
        int contentWidth = (int) Math.round(clamp(screenWidth / hud.scale(), 160, 480));
        int contentHeight = (int) Math.round(clamp(screenHeight / hud.scale(), 44, 240));
        GuideUiConfig.Hud resized = hud.withPlacement(hud.anchor(), hud.offsetX(), hud.offsetY(),
                contentWidth, contentHeight, hud.scale());
        return placementAt(viewportWidth, viewportHeight, resized, x, y);
    }

    private static int effectiveDimension(int requested, int viewport, double margin, double scale) {
        return Math.min(requested, Math.max(0, (int) Math.floor((viewport - 2 * margin) / scale)));
    }

    private static double margin(int viewport) {
        // At impossibly small sizes, retain finite on-screen bounds instead of a negative rectangle.
        return Math.min(MARGIN, Math.max(0, (viewport - 1) / 2.0));
    }

    private static int offset(double value) {
        return (int) Math.round(clamp(value, -MAX_OFFSET, MAX_OFFSET));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }

    private static void requireViewport(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("HUD viewport dimensions must not be negative");
        }
    }

    /** Bounds are screen-space; content dimensions are the effective unscaled rendering viewport. */
    @dev.openallay.value.ValueType(Rect.ValueSchemaProvider.class)
public static final class Rect {
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final int contentWidth;
    private final int contentHeight;
    private final double scale;
    public Rect(double x, double y, double width, double height, int contentWidth, int contentHeight, double scale) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.scale = scale;
    }
    public double x() { return x; }
    public double y() { return y; }
    public double width() { return width; }
    public double height() { return height; }
    public int contentWidth() { return contentWidth; }
    public int contentHeight() { return contentHeight; }
    public double scale() { return scale; }
public double right() {
            return x + width;
        }
public double bottom() {
            return y + height;
        }
public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Rect)) return false;
        Rect that = (Rect) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && Double.compare(width, that.width) == 0 && Double.compare(height, that.height) == 0 && contentWidth == that.contentWidth && contentHeight == that.contentHeight && Double.compare(scale, that.scale) == 0;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Double.hashCode(x);
        hash = 31 * hash + Double.hashCode(y);
        hash = 31 * hash + Double.hashCode(width);
        hash = 31 * hash + Double.hashCode(height);
        hash = 31 * hash + Integer.hashCode(contentWidth);
        hash = 31 * hash + Integer.hashCode(contentHeight);
        hash = 31 * hash + Double.hashCode(scale);
        return hash;
    }
    @Override public String toString() { return "Rect[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + ", contentWidth=" + contentWidth + ", contentHeight=" + contentHeight + ", scale=" + scale + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Rect> schema() {
            return new dev.openallay.value.ValueSchema<>(Rect.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Rect>>asList(new dev.openallay.value.ValueSchema.Component<>(Rect.class, "x", Rect::x), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "y", Rect::y), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "width", Rect::width), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "height", Rect::height), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "contentWidth", Rect::contentWidth), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "contentHeight", Rect::contentHeight), new dev.openallay.value.ValueSchema.Component<>(Rect.class, "scale", Rect::scale)), arguments -> new Rect((Double) arguments[0], (Double) arguments[1], (Double) arguments[2], (Double) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Double) arguments[6]));
        }
    }
}
}
