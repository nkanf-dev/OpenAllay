package dev.openallay.integration.jei;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.ResourceLocation;

/** Only the JEI native UID override changes with Minecraft's resource-ID naming family. */
public abstract class MinecraftJeiPluginUid implements IModPlugin {
    @Override public final ResourceLocation getPluginUid() {
        return MinecraftResourceIds.fromNamespaceAndPath("openallay", "jei_plugin");
    }
}
