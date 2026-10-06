package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;

/** Native image pixels at the shared ARGB boundary. */
public final class MinecraftImagePixels {
    private MinecraftImagePixels() {}
    public static int[] argb(GuideImageBitmap image) { return image.argb(); }
    public static int[] argb(NativeImage image) { return image.getPixels(); }
    public static void setArgb(NativeImage image, int x, int y, int argb) { image.setPixel(x, y, argb); }
}
