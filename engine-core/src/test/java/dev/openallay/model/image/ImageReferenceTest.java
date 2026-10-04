package dev.openallay.model.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class ImageReferenceTest {
    private static final String HASH = "0123456789abcdef".repeat(4);

    @Test
    void containsOnlyPortableMetadata() {
        assertArrayEquals(new String[] {"sha256", "mimeType", "width", "height", "byteSize"},
                Arrays.stream(ImageReference.class.getRecordComponents())
                        .map(component -> component.getName()).toArray(String[]::new));
        assertEquals(7, new ImageReference(HASH, "image/jpeg", 1, 2, 7).byteSize());
    }

    @Test
    void rejectsPathsNoncanonicalHashesAndInvalidMetadata() {
        for (String hash : new String[] {"../outside", HASH.toUpperCase(), "a".repeat(63), "g".repeat(64)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ImageReference(hash, "image/png", 1, 1, 1));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ImageReference(HASH, "image/gif", 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ImageReference(HASH, "image/png", 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ImageReference(HASH, "image/png", 1, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ImageReference(HASH, "image/png", 1, 1, 0));
    }

    @Test
    void defaultLimitsHaveSeparateProviderEnvelopeAndApplicationDecodeBudget() throws Exception {
        ImageInputLimits limits = ImageInputLimits.defaults();
        assertEquals(5L * 1024 * 1024, limits.maxByteSize());
        assertEquals(8000, limits.maxDimension());
        assertEquals(ImageInputLimits.DEFAULT_DECODE_MEMORY_BUDGET
                / ImageInputLimits.DECODE_BUDGET_BYTES_PER_PIXEL, limits.maxPixels());
        // Thin images may exceed 4096: the memory limit is pixels, not a hidden axis clamp.
        limits.validate(new ImageReference(HASH, "image/png", 7999, 1, 10));
        assertThrows(IOException.class,
                () -> limits.validate(new ImageReference(HASH, "image/png", 8001, 1, 10)));
        assertThrows(IOException.class,
                () -> limits.validate(new ImageReference(HASH, "image/png", 8000, 8000, 10)));
        assertThrows(IllegalArgumentException.class, () -> new ImageInputLimits(0, 8000, 1));
        assertThrows(IllegalArgumentException.class, () -> new ImageInputLimits(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ImageInputLimits(1, 1, 0));
    }
}
