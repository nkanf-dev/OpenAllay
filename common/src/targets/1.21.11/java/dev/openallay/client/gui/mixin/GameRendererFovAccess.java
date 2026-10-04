package dev.openallay.client.gui.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Current native renderer FOV calculation, not configured user FOV. */
@Mixin(GameRenderer.class)
public interface GameRendererFovAccess {
    @Invoker("getFov") float openallay$computedFov(Camera camera, float partialTick, boolean changingFov);
}
