package dev.openallay.client.gui.mixin;

import dev.openallay.client.gui.MinecraftTeardownState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftTeardownAccess extends MinecraftTeardownState {
    @Accessor("clientLevelTeardownInProgress") boolean openallay$teardownInProgress();
}
