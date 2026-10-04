package dev.openallay.integration.jei;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.Identifier;

/** Only the JEI native UID override changes with Minecraft's resource-ID naming family. */
public abstract class MinecraftJeiPluginUid implements IModPlugin {
    @Override public final Identifier getPluginUid() {
        return MinecraftResourceIds.fromNamespaceAndPath("openallay", "jei_plugin");
    }
}
