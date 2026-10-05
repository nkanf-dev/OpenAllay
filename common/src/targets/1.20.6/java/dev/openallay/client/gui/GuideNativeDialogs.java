package dev.openallay.client.gui;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/** Pre-1.21 native desktop operations; shared settings own only the resulting draft change. */
public final class GuideNativeDialogs {
    private GuideNativeDialogs() {}
    public static void openDirectory(Path path) { net.minecraft.Util.getPlatform().openFile(path.toFile()); }
    public static CompletableFuture<String> selectDirectory(Minecraft client, String title, String initialPath) {
        try {
            return CompletableFuture.completedFuture(org.lwjgl.util.tinyfd.TinyFileDialogs
                    .tinyfd_selectFolderDialog(title, initialPath));
        } catch (RuntimeException | LinkageError unavailable) {
            return CompletableFuture.failedFuture(unavailable);
        }
    }
}
