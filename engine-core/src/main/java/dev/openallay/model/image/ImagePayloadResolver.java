package dev.openallay.model.image;

import java.io.IOException;

/**
 * Request-only access to image bytes. Possessing a reference does not authorize a
 * read: the caller must supply a resolver bound to the request's player scope.
 * Implementations verify the managed bytes match the reference before returning.
 */
@FunctionalInterface
public interface ImagePayloadResolver extends AutoCloseable {
    byte[] read(ImageReference reference) throws IOException;

    /** Optional scope lease cleanup. Plain request resolvers own no resources. */
    @Override
    default void close() {}

    /** Text-only callers have no implicit filesystem or remote URL access. */
    static ImagePayloadResolver unavailable() {
        return reference -> {
            throw new IOException("Image payload resolver is unavailable for this request");
        };
    }
}
