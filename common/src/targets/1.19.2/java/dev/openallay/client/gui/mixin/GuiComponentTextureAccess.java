package dev.openallay.client.gui.mixin;

import com.mojang.math.Matrix4f;
import net.minecraft.client.gui.GuiComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exact private native normalized quad, not a second rendering implementation. */
@Mixin(GuiComponent.class)
public interface GuiComponentTextureAccess {
    @Invoker("innerBlit") static void openallay$blit(Matrix4f matrix,
            int x0, int x1, int y0, int y1, int z, float u0, float u1, float v0, float v1) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
