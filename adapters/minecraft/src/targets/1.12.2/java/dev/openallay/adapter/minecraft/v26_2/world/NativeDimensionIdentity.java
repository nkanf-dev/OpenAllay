package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.World;
/** Dimension IDs are Forge numeric world identities, not modern registry aliases. */
final class NativeDimensionIdentity {
    private NativeDimensionIdentity() {}
    static String id(World world) { return "forge:dimension/"+world.provider.getDimension(); }
}
