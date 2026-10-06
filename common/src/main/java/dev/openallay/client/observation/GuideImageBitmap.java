package dev.openallay.client.observation;

/** Product preview pixel custody. Registration consumes this value's selected native image. */
public interface GuideImageBitmap extends AutoCloseable {
    @Override void close();
}
