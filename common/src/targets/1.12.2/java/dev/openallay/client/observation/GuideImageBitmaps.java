package dev.openallay.client.observation;

import java.awt.image.BufferedImage;

/** Actual legacy bitmap storage; no GL allocation occurs before the texture owner takes custody. */
public final class GuideImageBitmaps {
    private GuideImageBitmaps() {}
    private static final class Bitmap implements GuideImageBitmap {
        private BufferedImage image;
        private Bitmap(int width, int height) { image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); }
        private Bitmap(BufferedImage image) { this.image = java.util.Objects.requireNonNull(image, "image"); }
        @Override public int width() { return bitmap(this).image.getWidth(); }
        @Override public int height() { return bitmap(this).image.getHeight(); }
        @Override public int[] argb() {
            BufferedImage current = bitmap(this).image;
            return current.getRGB(0, 0, current.getWidth(), current.getHeight(), null, 0, current.getWidth());
        }
        @Override public void close() {
            BufferedImage current = image;
            image = null;
            if (current != null) current.flush();
        }
    }
    /** On successful return this wrapper owns the actual image. Caller retains custody if construction fails. */
    public static GuideImageBitmap wrap(BufferedImage image) { return new Bitmap(image); }
    public static GuideImageBitmap create(int width, int height) { return new Bitmap(width, height); }
    private static Bitmap bitmap(GuideImageBitmap image) {
        final class $oaPattern0_Holder { dev.openallay.client.observation.GuideImageBitmap value; Bitmap bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = image) instanceof dev.openallay.client.observation.GuideImageBitmaps.Bitmap && (($oaPattern0_holder.bound = (Bitmap) $oaPattern0_holder.value) != null)))) throw new IllegalArgumentException("Preview bitmap belongs to another native binding");
        if ($oaPattern0_holder.bound.image == null) throw new IllegalStateException("Preview bitmap custody was consumed or closed");
        return $oaPattern0_holder.bound;
    }
    public static void setArgb(GuideImageBitmap image, int x, int y, int argb) { bitmap(image).image.setRGB(x, y, argb); }
    public static BufferedImage take(GuideImageBitmap image) {
        Bitmap bitmap = bitmap(image);
        BufferedImage nativeImage = bitmap.image;
        bitmap.image = null;
        return nativeImage;
    }
}
