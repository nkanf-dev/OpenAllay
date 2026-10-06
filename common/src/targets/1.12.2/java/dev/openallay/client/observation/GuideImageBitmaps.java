package dev.openallay.client.observation;

import java.awt.image.BufferedImage;

/** Actual legacy bitmap storage; no GL allocation occurs before the texture owner takes custody. */
public final class GuideImageBitmaps {
    private GuideImageBitmaps() {}
    private static final class Bitmap implements GuideImageBitmap {
        private BufferedImage image;
        private Bitmap(int width, int height) { image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); }
        @Override public void close() {
            BufferedImage current = image;
            image = null;
            if (current != null) current.flush();
        }
    }
    public static GuideImageBitmap create(int width, int height) { return new Bitmap(width, height); }
    private static Bitmap bitmap(GuideImageBitmap image) {
        if (!(image instanceof Bitmap bitmap)) throw new IllegalArgumentException("Preview bitmap belongs to another native binding");
        if (bitmap.image == null) throw new IllegalStateException("Preview bitmap custody was consumed or closed");
        return bitmap;
    }
    public static void setArgb(GuideImageBitmap image, int x, int y, int argb) { bitmap(image).image.setRGB(x, y, argb); }
    public static BufferedImage take(GuideImageBitmap image) {
        Bitmap bitmap = bitmap(image);
        BufferedImage nativeImage = bitmap.image;
        bitmap.image = null;
        return nativeImage;
    }
}
