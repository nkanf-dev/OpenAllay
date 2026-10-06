package dev.openallay.integration.jei;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.ResourceLocation;

/** Exact JEI7 plugin UID type, normalized to actual MCP ResourceLocation at compilation. */
public abstract class MinecraftJeiPluginUid implements IModPlugin {
    @Override public final ResourceLocation getPluginUid() {
        return MinecraftResourceIds.fromNamespaceAndPath("openallay", "jei_plugin");
    }
}
