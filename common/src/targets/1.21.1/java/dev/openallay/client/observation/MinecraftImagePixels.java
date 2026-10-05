package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;

/** 1.21/1.21.1's RGBA-named API exposes packed ABGR; consumers use packed ARGB. */
public final class MinecraftImagePixels {
    private MinecraftImagePixels() {}
    private static int swapRedBlue(int color) {
        return (color & 0xFF00FF00) | ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16);
    }
    public static int[] argb(NativeImage image) {
        int[] pixels = image.getPixelsRGBA();
        for (int index = 0; index < pixels.length; index++) pixels[index] = swapRedBlue(pixels[index]);
        return pixels;
    }
    public static void setArgb(NativeImage image, int x, int y, int argb) {
        image.setPixelRGBA(x, y, swapRedBlue(argb));
    }
}
