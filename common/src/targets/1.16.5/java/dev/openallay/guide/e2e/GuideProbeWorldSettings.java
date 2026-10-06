package dev.openallay.guide.e2e;

import net.minecraft.core.Registry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

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
        if (!resumed) {
            server.getCommands().performCommand(server.createCommandSourceStack(), "gamerule randomTickSpeed 0");
        }
        if (rules.getInt(key) != 0) throw new IllegalStateException("Disposable Builder fixture requires random tick speed zero");
    }
    static boolean isFlat(net.minecraft.server.MinecraftServer server) {
        return server.getWorldData().worldGenSettings().isFlatWorld();
    }
    static boolean commandsAllowed(net.minecraft.server.MinecraftServer server) {
        return server.getWorldData().getAllowCommands();
    }
    static void open(net.minecraft.client.Minecraft client, String name, Runnable cancelled) {
        GuideProbeWorldReload.open(client, name, cancelled);
    }
    static void createFresh(net.minecraft.client.Minecraft client, String name) {
        var registries = net.minecraft.core.RegistryAccess.builtin();
        var biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
        var flat = new FlatLevelSource(FlatLevelGeneratorSettings.getDefault(biomes));
        var dimensions = WorldGenSettings.withOverworld(
                registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY),
                DimensionType.defaultDimensions(registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY),
                        biomes, registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY), 17L), flat);
        client.createLevel(name, create(name), registries,
                new WorldGenSettings(17L, false, false, dimensions));
    }
}
