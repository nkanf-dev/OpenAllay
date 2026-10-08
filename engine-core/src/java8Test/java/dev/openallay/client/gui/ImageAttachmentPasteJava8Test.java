package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class ImageAttachmentPasteJava8Test {
    @Test void completeSyntheticImageStorageClipboardAndComposerOracle() throws Exception {
        String[] vectors = ImageAttachmentPasteJava8Fixture.vectors();
        assertTrue(vectors.length >= 90);
        assertTrue(java.util.Arrays.stream(vectors).anyMatch(line -> line.equals("Scope.privateStaticABI=true")));
        assertTrue(java.util.Arrays.stream(vectors).anyMatch(line -> line.equals("composer.detachDiscardsLateImport=true")));
        assertTrue(java.util.Arrays.stream(vectors).anyMatch(line -> line.equals("storage.releaseCollect=true")));
    }
}
