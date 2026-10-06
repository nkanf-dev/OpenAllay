package dev.openallay.client.observation;
/** BufferedImage capture pixels detach through the selected owning bitmap. */
public final class MinecraftImagePixels {
    private MinecraftImagePixels() {}
    public static int[] argb(GuideImageBitmap image) { return image.argb(); }
}
