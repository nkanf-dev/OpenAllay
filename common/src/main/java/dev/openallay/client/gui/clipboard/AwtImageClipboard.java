package dev.openallay.client.gui.clipboard;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/** Standard Windows/Linux image flavor; file-list and URI flavors are deliberately ignored. */
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
    public Read read() {
        try {
            Transferable value = contents.get();
            if (value == null || !value.isDataFlavorSupported(DataFlavor.imageFlavor)) return Read.empty();
            Object transferred = value.getTransferData(DataFlavor.imageFlavor);
            if (!(transferred instanceof Image image)) return Read.unavailable();
            return Read.image(ClipboardImageEncoder.bitmap(image));
        } catch (IOException | java.awt.datatransfer.UnsupportedFlavorException
                | RuntimeException | LinkageError | java.awt.AWTError unavailable) {
            return Read.unavailable();
        }
    }
}
