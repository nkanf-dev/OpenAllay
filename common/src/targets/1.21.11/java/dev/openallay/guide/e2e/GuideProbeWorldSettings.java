package dev.openallay.guide.e2e;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;

/** Native constructor binding for the explicitly disposable development world. */
final class GuideProbeWorldSettings {
    private GuideProbeWorldSettings() {}
    static LevelSettings create(String name) { return new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false,
                new net.minecraft.world.level.gamerules.GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS),
                WorldDataConfiguration.DEFAULT); }
    /** Setup only: resumed fixtures must retain the rule saved by their original acceptance. */
    static void prepareBuilderFixture(net.minecraft.server.MinecraftServer server, boolean resumed) {
        if (!server.isSameThread()) throw new IllegalStateException("Fixture setup requires the server owner thread");
        var rules = server.overworld().getGameRules();
        var key = net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED;
        if (!resumed) rules.set(key, 0, server);
        if (rules.get(key) != 0) throw new IllegalStateException("Disposable Builder fixture requires random tick speed zero");
    }
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
