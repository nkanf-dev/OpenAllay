package dev.openallay.guide.e2e;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;

/** Native constructor binding for the explicitly disposable development world. */
final class GuideProbeWorldSettings {
    private GuideProbeWorldSettings() {}
    static LevelSettings create(String name) { return new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false,
                new net.minecraft.world.level.GameRules(),
                WorldDataConfiguration.DEFAULT); }
    static boolean commandsAllowed(net.minecraft.server.MinecraftServer server) {
        return server.getWorldData().isAllowCommands();
    }
    static void open(net.minecraft.client.Minecraft client, String name, Runnable cancelled) {
        client.createWorldOpenFlows().openWorld(name, cancelled);
    }
    static void createFresh(net.minecraft.client.Minecraft client, String name) {
        client.createWorldOpenFlows().createFreshLevel(name, create(name),
                new net.minecraft.world.level.levelgen.WorldOptions(17L, false, false),
                registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                        .value().createWorldDimensions(), dev.openallay.client.gui.MinecraftClientWindow.screen(client));
    }
}
