package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;

/** Selected native image custody. Only this typed leaf exposes the actual NativeImage. */
public final class GuideImageBitmaps {
    private GuideImageBitmaps() {}
    private static final class Bitmap implements GuideImageBitmap {
        private NativeImage image;
        private Bitmap(int width, int height) { image = new NativeImage(width, height, false); }
        @Override public void close() {
            NativeImage current = image;
            image = null;
            if (current != null) current.close();
        }
    }
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
