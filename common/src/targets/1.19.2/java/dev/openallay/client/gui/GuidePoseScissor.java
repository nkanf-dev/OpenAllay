package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Vector4f;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

/** Pose-transformed clips intersect the actual caller's GL clip and restore exact prior state. */
public final class GuidePoseScissor {
    private record Saved(boolean enabled, int x, int y, int width, int height) {}
    private static final ThreadLocal<Deque<Saved>> CLIPS = ThreadLocal.withInitial(ArrayDeque::new);
    private GuidePoseScissor() {}
    static void enable(PoseStack pose, int x0, int y0, int x1, int y1) {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            Vector4f point = new Vector4f((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, 0, 1);
            point.transform(pose.last().pose());
            left = Math.min(left, point.x()); top = Math.min(top, point.y());
            right = Math.max(right, point.x()); bottom = Math.max(bottom, point.y());
        }
        // An invalid or zero-sized source rectangle stays empty, even after transformation.
        if (x1 <= x0 || y1 <= y0) { right = left; bottom = top; }
        screen(left, top, right, bottom);
    }
    public static void nativeScreen(int x0, int y0, int x1, int y1) {
        screen(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
    }
    private static void screen(float x0, float y0, float x1, float y1) {
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int left = (int) Math.floor(x0 * scale), right = (int) Math.ceil(x1 * scale);
        int bottom = (int) Math.floor(window.getHeight() - y1 * scale);
        int top = (int) Math.ceil(window.getHeight() - y0 * scale);
        Saved saved;
        try (MemoryStack memory = MemoryStack.stackPush()) {
            var box = memory.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            saved = new Saved(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), box.get(0), box.get(1), box.get(2), box.get(3));
        }
        CLIPS.get().push(saved);
        if (saved.enabled()) {
            left = Math.max(left, saved.x()); bottom = Math.max(bottom, saved.y());
            right = Math.min(right, saved.x() + saved.width()); top = Math.min(top, saved.y() + saved.height());
        }
        RenderSystem.enableScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }
    public static void disable() {
        Deque<Saved> clips = CLIPS.get();
        if (clips.isEmpty()) throw new IllegalStateException("Unbalanced native Guide scissor");
        Saved saved = clips.pop();
        // Restore the stored rectangle even when the caller had scissoring disabled.
        RenderSystem.enableScissor(saved.x(), saved.y(), saved.width(), saved.height());
        if (!saved.enabled()) RenderSystem.disableScissor();
        if (clips.isEmpty()) CLIPS.remove();
    }
}
