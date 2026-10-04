package dev.openallay.world;

import java.util.Objects;

/** Inclusive normalized cuboid. */
public record WorldBounds(WorldPosition from, WorldPosition to) {
    public WorldBounds {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        WorldPosition first = from;
        WorldPosition second = to;
        from = new WorldPosition(
                Math.min(first.x(), second.x()),
                Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()));
        to = new WorldPosition(
                Math.max(first.x(), second.x()),
                Math.max(first.y(), second.y()),
                Math.max(first.z(), second.z()));
    }

    public long volume() {
        return Math.multiplyExact(
                Math.multiplyExact(
                        (long) to.x() - from.x() + 1,
                        (long) to.y() - from.y() + 1),
                (long) to.z() - from.z() + 1);
    }
}
