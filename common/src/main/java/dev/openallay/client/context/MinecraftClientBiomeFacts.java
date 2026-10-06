package dev.openallay.client.context;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Native biome identity detaches to a canonical string at the client facts boundary. */
public final class MinecraftClientBiomeFacts {
    private MinecraftClientBiomeFacts() {}
    public static Optional<String> id(Minecraft client, LocalPlayer player) {
        return dev.openallay.client.MinecraftLocalPlayerLevel.get(player).getBiome(player.blockPosition()).unwrapKey()
                .map(key -> dev.openallay.platform.minecraft.MinecraftResourceIds.keyId(key).toString());
    }
}
