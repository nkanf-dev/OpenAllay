package dev.openallay.guide.e2e;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/** Native settings/launch operations for the explicitly disposable fixture only. */
final class GuideProbeWorldSettings {
    private GuideProbeWorldSettings() {}
    static WorldSettings create(String name) {
        return new WorldSettings(17L, GameType.SURVIVAL, false, false, WorldType.FLAT).enableCommands();
    }
    static void prepareBuilderFixture(MinecraftServer server, boolean resumed) {
        if (!server.isCallingFromMinecraftThread()) throw new IllegalStateException("Fixture setup requires the server owner thread");
        var rules = server.getWorld(0).getGameRules();
        if (!resumed) rules.setOrCreateGameRule("randomTickSpeed", "0");
        if (rules.getInt("randomTickSpeed") != 0) throw new IllegalStateException("Disposable Builder fixture requires random tick speed zero");
    }
    static boolean isFlat(MinecraftServer server) {
        return server.getWorld(0).getWorldInfo().getTerrainType() == WorldType.FLAT;
    }
    static boolean commandsAllowed(MinecraftServer server) {
        return server.getWorld(0).getWorldInfo().areCommandsAllowed();
    }
    static void open(Minecraft client, String name, Runnable cancelled) {
        GuideProbeWorldReload.open(client, name, cancelled);
    }
    static void createFresh(Minecraft client, String name) {
        client.launchIntegratedServer(name, name, create(name));
        var server = java.util.Objects.requireNonNull(client.getIntegratedServer(), "Fixture server unavailable");
        server.addScheduledTask(() -> server.setDifficultyForAllWorlds(EnumDifficulty.PEACEFUL));
    }
}
