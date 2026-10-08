package dev.openallay.client.gui.clipboard;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** JDK image readers with explicit, in-memory stream ownership. The caller owns the byte stream. */
final class ClipboardImageDecoder {
    private ClipboardImageDecoder() {}

    static BufferedImage read(InputStream bytes) throws IOException {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(bytes)) {
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                // ImageIO.read(ImageInputStream) closes input itself. Using its ImageReader
                // lets this scope close input once, without masking success with a second close.
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}
