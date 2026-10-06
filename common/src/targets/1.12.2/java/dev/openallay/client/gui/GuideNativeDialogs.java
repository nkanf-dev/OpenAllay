package dev.openallay.client.gui;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import net.minecraft.client.Minecraft;

/** Actual desktop operations for the LWJGL 2 family; no absent TinyFD or Util platform alias. */
public final class GuideNativeDialogs {
    private GuideNativeDialogs() {}
    public static net.minecraft.client.gui.GuiScreen confirm(java.util.function.Consumer<Boolean> result,
            net.minecraft.util.text.ITextComponent title, net.minecraft.util.text.ITextComponent message,
            net.minecraft.util.text.ITextComponent yes, net.minecraft.util.text.ITextComponent no) {
        net.minecraft.client.gui.GuiYesNoCallback callback = (answer, id) -> result.accept(answer);
        return new net.minecraft.client.gui.GuiYesNo(callback, title.getFormattedText(), message.getFormattedText(),
                yes.getFormattedText(), no.getFormattedText(), 0);
    }
    public static void openDirectory(Path path) {
        try { Desktop.getDesktop().open(path.toFile()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    public static CompletableFuture<String> selectDirectory(Minecraft client, String title, String initialPath) {
        CompletableFuture<String> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                JFileChooser chooser = new JFileChooser(initialPath);
                chooser.setDialogTitle(title);
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                result.complete(chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION
                        ? chooser.getSelectedFile().getAbsolutePath() : null);
            } catch (RuntimeException | LinkageError failure) { result.completeExceptionally(failure); }
        });
        return result;
    }
}
