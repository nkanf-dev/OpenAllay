package dev.openallay.neoforge;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraft.launchwrapper.Launch;

/** Physical side and launch environment from actual FML14/LaunchWrapper facts. */
final class NeoForgeNativeEnvironment {
    private NeoForgeNativeEnvironment() {}
    static boolean isClient() { return FMLCommonHandler.instance().getSide().isClient(); }
    static boolean isDevelopment() { return Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment")); }
}
