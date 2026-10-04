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
    public record Rect(
            double x, double y, double width, double height,
            int contentWidth, int contentHeight, double scale) {
        public double right() {
            return x + width;
        }

        public double bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }
}
