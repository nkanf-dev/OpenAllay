package dev.openallay.platform.minecraft;

/** MinecraftServer.getMinecraftVersion() in the accepted native 1.12.2 source returns this name.
 * The native version fact is available before either client or server singleton exists. */
public final class MinecraftGameVersionFacts {
    private MinecraftGameVersionFacts() {}
    public static String name() { return "1.12.2"; }
}
