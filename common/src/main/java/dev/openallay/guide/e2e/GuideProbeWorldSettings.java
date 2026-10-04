package dev.openallay.guide.e2e;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;

/** Native constructor binding for the explicitly disposable development world. */
final class GuideProbeWorldSettings {
    private GuideProbeWorldSettings() {}
    static LevelSettings create(String name) { return new LevelSettings(name, GameType.SURVIVAL,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), false,
                WorldDataConfiguration.DEFAULT); }
}
