package dev.openallay.client.gui;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLMisc;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;

/** SDL desktop binding; native callbacks remain owned until delivered on the client thread. */
public final class GuideNativeDialogs {
    private static final Set<SDL_DialogFileCallback> pending = ConcurrentHashMap.newKeySet();
    private GuideNativeDialogs() {}
    public static net.minecraft.client.gui.screens.Screen confirm(java.util.function.Consumer<Boolean> result,
            net.minecraft.network.chat.Component title, net.minecraft.network.chat.Component message,
            net.minecraft.network.chat.Component yes, net.minecraft.network.chat.Component no) {
        return new net.minecraft.client.gui.screens.ConfirmScreen(result::accept, title, message, yes, no);
    }
    public static void openDirectory(Path path) {
        if (!SDLMisc.SDL_OpenURL(path.toAbsolutePath().toUri().toASCIIString())) {
            throw new IllegalStateException("Native directory opening failed");
        }
    }
    public static CompletableFuture<String> selectDirectory(Minecraft client, String title, String initialPath) {
        CompletableFuture<String> result = new CompletableFuture<>();
        SDL_DialogFileCallback callback = new SDL_DialogFileCallback() {
            @Override public void invoke(long userdata, long files, int filter) {
                String selected = null;
                Throwable failure = null;
                try {
                    if (files == MemoryUtil.NULL) failure = new IllegalStateException("Native directory chooser failed");
                    else {
                        long first = MemoryUtil.memGetAddress(files);
                        if (first != MemoryUtil.NULL) selected = MemoryUtil.memUTF8(first);
                    }
                } catch (RuntimeException error) { failure = error; }
                String value = selected;
                Throwable error = failure;
                client.execute(() -> {
                    try {
                        if (error == null) result.complete(value);
                        else result.completeExceptionally(error);
                    } finally { pending.remove(this); free(); }
                });
            }
        };
        pending.add(callback);
        try {
            SDLDialog.SDL_ShowOpenFolderDialog(callback, MemoryUtil.NULL, client.getWindow().handle(), initialPath, false);
        } catch (RuntimeException | LinkageError unavailable) {
            pending.remove(callback);
            callback.free();
            result.completeExceptionally(unavailable);
        }
        return result;
    }
}
