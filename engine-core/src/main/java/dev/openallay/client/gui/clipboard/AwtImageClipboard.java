package dev.openallay.client.gui.clipboard;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/** Windows/Linux bitmap, encoded-image, and explicitly copied local image-file representations. */
public final class AwtImageClipboard implements ImageClipboard {
    private final Supplier<Transferable> contents;

    public AwtImageClipboard(Supplier<Transferable> contents) {
        this.contents = Objects.requireNonNull(contents, "contents");
    }

    static AwtImageClipboard system() {
        return new AwtImageClipboard(() -> Toolkit.getDefaultToolkit()
                .getSystemClipboard().getContents(null));
    }

    @Override
    public ImageClipboard capture() {
        try {
            Transferable captured = contents.get();
            return new AwtImageClipboard(() -> captured);
        } catch (RuntimeException | LinkageError | java.awt.AWTError unavailable) {
            return Read::unavailable;
        }
    }

    @Override
    public Read read() {
        try {
            Transferable value = contents.get();
            if (value == null) return Read.empty();
            boolean imageOffered = value.isDataFlavorSupported(DataFlavor.imageFlavor);
            if (imageOffered) {
                try {
                    Object transferred = value.getTransferData(DataFlavor.imageFlavor);
                    if (transferred instanceof Image image) return Read.image(ClipboardImageEncoder.bitmap(image));
                } catch (IOException | UnsupportedFlavorException | RuntimeException invalidImage) {
                    // An encoded image or copied image file can still be valid.
                }
            }
            for (DataFlavor flavor : value.getTransferDataFlavors()) {
                if (flavor.equals(DataFlavor.imageFlavor) || !flavor.getPrimaryType().equalsIgnoreCase("image")) continue;
                imageOffered = true;
                try {
                    Object data = value.getTransferData(flavor);
                    InputStream stream = data instanceof InputStream input ? input
                            : data instanceof byte[] bytes ? new ByteArrayInputStream(bytes) : null;
                    if (stream == null) continue;
                    try (stream) {
                        BufferedImage image = ClipboardImageDecoder.read(stream);
                        if (image != null) return Read.image(image);
                    }
                } catch (IOException | UnsupportedFlavorException | RuntimeException invalidImage) {
                    // Continue through representations rather than failing on the first one.
                }
            }
            if (value.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    Object data = value.getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List<?> files) {
                        for (Object entry : files) {
                            if (!(entry instanceof File file) || !isImageFile(file.toPath())) continue;
                            imageOffered = true;
                            BufferedImage image = readFile(file.toPath());
                            if (image != null) return Read.image(image);
                        }
                    }
                } catch (IOException | UnsupportedFlavorException | RuntimeException unreadableFiles) {
                    imageOffered = true;
                }
            }
            for (DataFlavor flavor : value.getTransferDataFlavors()) {
                if (!flavor.isMimeTypeEqual("text/uri-list")) continue;
                try (var reader = new BufferedReader(flavor.getReaderForText(value))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Path file = localFile(line);
                        if (file == null || !isImageFile(file)) continue;
                        imageOffered = true;
                        BufferedImage image = readFile(file);
                        if (image != null) return Read.image(image);
                    }
                } catch (IOException | UnsupportedFlavorException | RuntimeException unreadableUris) {
                    imageOffered = true;
                }
            }
            return imageOffered ? Read.unavailable() : Read.empty();
        } catch (RuntimeException | LinkageError | java.awt.AWTError unavailable) {
            return Read.unavailable();
        }
    }

    private static boolean isImageFile(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String suffix = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(ImageIO.getReaderFileSuffixes()).anyMatch(suffix::equalsIgnoreCase);
    }

    private static BufferedImage readFile(Path file) {
        try (var stream = java.nio.file.Files.newInputStream(file)) {
            return ClipboardImageDecoder.read(stream);
        } catch (IOException | RuntimeException invalidImage) {
            return null;
        }
    }

    private static Path localFile(String line) {
        String text = line.strip();
        if (text.isEmpty() || text.startsWith("#")) return null;
        try {
            URI uri = new URI(text);
            if (!"file".equalsIgnoreCase(uri.getScheme())) return null;
            String host = uri.getAuthority();
            if (host != null && !host.isEmpty()) {
                if (!host.equalsIgnoreCase("localhost")) return null;
                uri = new URI("file", null, uri.getPath(), null);
            }
            return Path.of(uri);
        } catch (URISyntaxException | IllegalArgumentException invalidUri) {
            return null;
        }
    }
}
