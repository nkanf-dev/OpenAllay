package dev.openallay.client.context;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;

/** Actual old Biome value is resolved in the live connection's dynamic biome registry. */
public final class MinecraftClientBiomeFacts {
    private MinecraftClientBiomeFacts() {}
    public static Optional<String> id(Minecraft client, LocalPlayer player) {
        var connection = client.getConnection();
        if (connection == null) return Optional.empty();
        var biome = dev.openallay.client.MinecraftLocalPlayerLevel.get(player).getBiome(player.blockPosition());
        var registry = connection.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        var key = registry.getKey(biome);
        if (key == null || !registry.containsKey(key) || registry.getOptional(key).orElse(null) != biome) return Optional.empty();
        return Optional.of(key.toString());
    }
}
