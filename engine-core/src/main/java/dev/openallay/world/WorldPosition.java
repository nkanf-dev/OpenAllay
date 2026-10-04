package dev.openallay.world;

/** Detached integer block coordinate used by spatial observations. */
public record WorldPosition(int x, int y, int z) {
    public WorldPosition subtract(WorldPosition origin) {
        return new WorldPosition(x - origin.x, y - origin.y, z - origin.z);
    }
}
