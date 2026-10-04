package dev.openallay.client.gui.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftTeardownAccess {
    @Accessor("clientLevelTeardownInProgress") boolean openallay$teardownInProgress();
}
