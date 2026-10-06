package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.adapter.minecraft.v26_2.world.mixin.NativeDataFixerVersionAccessor;
import net.minecraft.server.MinecraftServer;
/** External native DataVersion, read from the actual running server's DataFixer. */
final class NativeWorldVersionFacts {
    private NativeWorldVersionFacts() {}
    static int dataVersion(MinecraftServer server) {
        return ((NativeDataFixerVersionAccessor)server.getDataFixer()).openallay$getVersion();
    }
}
