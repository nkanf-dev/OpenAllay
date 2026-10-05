package dev.openallay.guide.e2e;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.DataPackConfig;

/** Native constructor binding for the explicitly disposable development world. */
final class GuideProbeWorldSettings {
    private GuideProbeWorldSettings() {}
    static LevelSettings create(String name) { return new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.PEACEFUL, false,
                new net.minecraft.world.level.GameRules(),
                DataPackConfig.DEFAULT); }
    /** Setup only: resumed fixtures must retain the rule saved by their original acceptance. */
    static void prepareBuilderFixture(net.minecraft.server.MinecraftServer server, boolean resumed) {
        if (!server.isSameThread()) throw new IllegalStateException("Fixture setup requires the server owner thread");
        var rules = server.getGameRules();
        var key = net.minecraft.world.level.GameRules.RULE_RANDOMTICKING;
        if (!resumed) rules.getRule(key).set(0, server);
        if (rules.getInt(key) != 0) throw new IllegalStateException("Disposable Builder fixture requires random tick speed zero");
    }
    static boolean isFlat(net.minecraft.server.MinecraftServer server) {
        return server.getWorldData().worldGenSettings().isFlatWorld();
    }
    static boolean commandsAllowed(net.minecraft.server.MinecraftServer server) {
        return server.getWorldData().getAllowCommands();
    }
    static void open(net.minecraft.client.Minecraft client, String name, Runnable cancelled) {
        client.createWorldOpenFlows().loadLevel(new net.minecraft.client.gui.screens.Screen(
                dev.openallay.platform.minecraft.MinecraftComponents.empty()) {
            private boolean reported;
            @Override
            protected void init() {
                if (!reported) {
                    reported = true;
                    cancelled.run();
                }
            }
        }, name);
    }
    static void createFresh(net.minecraft.client.Minecraft client, String name) {
        var registries = net.minecraft.core.RegistryAccess.builtinCopy();
        var preset = registries.registryOrThrow(net.minecraft.core.Registry.WORLD_PRESET_REGISTRY)
                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value();
        client.createWorldOpenFlows().createFreshLevel(name, create(name), registries,
                preset.createWorldGenSettings(17L, false, false));
    }
}
