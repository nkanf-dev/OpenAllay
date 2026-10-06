package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;

/** Selected native image custody. Only this typed leaf exposes the actual NativeImage. */
public final class GuideImageBitmaps {
    private GuideImageBitmaps() {}
    private static final class Bitmap implements GuideImageBitmap {
        private NativeImage image;
        private Bitmap(int width, int height) { image = new NativeImage(width, height, false); }
        private Bitmap(NativeImage image) { this.image = java.util.Objects.requireNonNull(image, "image"); }
        @Override public int width() { return bitmap(this).image.getWidth(); }
        @Override public int height() { return bitmap(this).image.getHeight(); }
        @Override public int[] argb() { return MinecraftImagePixels.argb(bitmap(this).image); }
        @Override public void close() {
            NativeImage current = image;
            image = null;
            if (current != null) current.close();
        }
    }
    /** On successful return this wrapper owns the actual image. Caller retains custody if construction fails. */
    public static GuideImageBitmap wrap(NativeImage image) { return new Bitmap(image); }
    public static GuideImageBitmap create(int width, int height) { return new Bitmap(width, height); }
    private static Bitmap bitmap(GuideImageBitmap image) {
        if (!(image instanceof Bitmap bitmap)) throw new IllegalArgumentException("Preview bitmap belongs to another native binding");
        if (bitmap.image == null) throw new IllegalStateException("Preview bitmap custody was consumed or closed");
        return bitmap;
    }
    public static void setArgb(GuideImageBitmap image, int x, int y, int argb) {
        MinecraftImagePixels.setArgb(bitmap(image).image, x, y, argb);
    }
    public static NativeImage take(GuideImageBitmap image) {
        Bitmap bitmap = bitmap(image);
        NativeImage nativeImage = bitmap.image;
        bitmap.image = null;
        return nativeImage;
    }
}
