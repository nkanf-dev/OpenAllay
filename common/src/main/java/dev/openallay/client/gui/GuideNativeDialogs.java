package dev.openallay.client.gui;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Native desktop operations; shared settings own only the resulting draft change. */
public final class GuideNativeDialogs {
    private GuideNativeDialogs() {}
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
