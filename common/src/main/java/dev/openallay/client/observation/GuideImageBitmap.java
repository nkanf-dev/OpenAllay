package dev.openallay.client.observation;

/** Product preview pixel custody. Registration consumes this value's selected native image. */
public interface GuideImageBitmap extends AutoCloseable {
    int width();
    int height();
    int[] argb();
    @Override void close();
}
