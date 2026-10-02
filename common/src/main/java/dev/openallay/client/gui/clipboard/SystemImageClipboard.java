package dev.openallay.client.gui.clipboard;

import java.util.Locale;

/** Lazy native clipboard selection. Constructing a screen never opens the system clipboard. */
public final class SystemImageClipboard implements ImageClipboard {
    @Override
    public Read read() { return capture().read(); }

    @Override
    public ImageClipboard capture() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            // macOS Java is commonly launched with -XstartOnFirstThread by GLFW. Initializing
            // AWT Toolkit there can hang, so this path must never touch the AWT clipboard.
            if (os.contains("mac") || os.contains("darwin")) return MacImageClipboard.capture();
            return AwtImageClipboard.system();
        } catch (RuntimeException | LinkageError | java.awt.AWTError unavailable) {
            return Read::unavailable;
        }
    }
}
