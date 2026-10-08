package dev.openallay.client.context;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.biome.Biome;
/** Actual registered biome object, guarded against registry default fallback. */
public final class MinecraftClientBiomeFacts {
    private MinecraftClientBiomeFacts() {}
    public static Optional<String> id(Minecraft client,EntityPlayerSP player) {
        net.minecraft.world.biome.Biome biome=client.world.getBiome(player.getPosition());
        net.minecraft.util.ResourceLocation key=Biome.REGISTRY.getNameForObject(biome);
        if (key==null || !Biome.REGISTRY.containsKey(key) || Biome.REGISTRY.getObject(key)!=biome) return Optional.empty();
        return Optional.of(key.toString());
    }
}
