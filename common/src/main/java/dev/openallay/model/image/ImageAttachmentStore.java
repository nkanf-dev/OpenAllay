package dev.openallay.model.image;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Actor-scoped images imported from bytes, never from model-supplied paths or URLs. */
public interface ImageAttachmentStore {
    ImageInputLimits limits();

    /** Imports and validates actual PNG/JPEG content, without trusting a declared MIME type. */
    ImageReference importImage(UUID actor, byte[] encodedImage) throws IOException;

    /**
     * Imports and durably adds the image to an owner's retained set under the same collection
     * lock. Use this overload when collection may run before a later transcript/draft retain.
     * Existing images owned by this identifier remain retained until retain or release.
     */
    ImageReference importImage(UUID actor, String owner, byte[] encodedImage) throws IOException;

    /** Resolves only this actor's managed artifact and verifies its hash and all metadata. */
    byte[] read(UUID actor, ImageReference reference) throws IOException;

    /**
     * Atomically replaces one durable owner's complete retained set. An empty list releases it.
     * Forked histories and outstanding export snapshots must use distinct owners and retain
     * before their source owner can be released. Owners survive store reopening.
     */
    void retain(UUID actor, String owner, List<ImageReference> references) throws IOException;

    /**
     * Atomically replaces all owners in one namespace, preserving direct owners and every
     * other namespace. Useful for reconciling a complete persisted history after a mutation.
     * No collection occurs here. A namespace and its owner names are opaque identifiers.
     */
    void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
            throws IOException;

    /** Releases only this owner. Does not immediately delete any image bytes. */
    void release(UUID actor, String owner) throws IOException;

    /**
     * Deletes unretained images for this actor and returns the number removed.
     * Imported images need a retain before collection; reads alone do not retain them.
     */
    int collect(UUID actor) throws IOException;
}
