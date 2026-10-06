package dev.openallay.client.gui;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Native desktop operations; shared settings own only the resulting draft change. */
public final class GuideNativeDialogs {
    private GuideNativeDialogs() {}
    public static net.minecraft.client.gui.screens.Screen confirm(java.util.function.Consumer<Boolean> result,
            net.minecraft.network.chat.Component title, net.minecraft.network.chat.Component message,
            net.minecraft.network.chat.Component yes, net.minecraft.network.chat.Component no) {
        return new net.minecraft.client.gui.screens.ConfirmScreen(result::accept, title, message, yes, no);
    }
    public static void openDirectory(Path path) { net.minecraft.util.Util.getPlatform().openPath(path); }
    public static CompletableFuture<String> selectDirectory(Minecraft client, String title, String initialPath) {
        try {
            return CompletableFuture.completedFuture(org.lwjgl.util.tinyfd.TinyFileDialogs
                    .tinyfd_selectFolderDialog(title, initialPath));
        } catch (RuntimeException | LinkageError unavailable) {
            return CompletableFuture.failedFuture(unavailable);
        }
    }
}
