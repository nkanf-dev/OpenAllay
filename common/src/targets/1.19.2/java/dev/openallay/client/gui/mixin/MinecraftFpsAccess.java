package dev.openallay.client.gui.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the native one-second FPS counter without parsing the debug text. */
@Mixin(Minecraft.class)
public interface MinecraftFpsAccess {
    @Accessor("fps")
    static int openallay$fps() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
