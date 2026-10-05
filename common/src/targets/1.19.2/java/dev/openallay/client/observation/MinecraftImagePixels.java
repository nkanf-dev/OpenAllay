package dev.openallay.client.observation;

import com.mojang.blaze3d.platform.NativeImage;

/** 1.19.2's RGBA-named API exposes packed ABGR; consumers use packed ARGB. */
public final class MinecraftImagePixels {
    private MinecraftImagePixels() {}
    private static int swapRedBlue(int color) {
        return (color & 0xFF00FF00) | ((color & 0x00FF0000) >>> 16) | ((color & 0x000000FF) << 16);
    }
    public static int[] argb(NativeImage image) {
        int[] pixels = new int[Math.multiplyExact(image.getWidth(), image.getHeight())];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y * image.getWidth() + x] = swapRedBlue(image.getPixelRGBA(x, y));
            }
        }
        return pixels;
    }
    public static void setArgb(NativeImage image, int x, int y, int argb) {
        image.setPixelRGBA(x, y, swapRedBlue(argb));
    }
}
