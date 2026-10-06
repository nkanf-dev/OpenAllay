package dev.openallay.client.gui.hud;

import java.util.Arrays;
import java.util.Objects;

/** Fixed-slot occupancy only. Native code owns the queue, instances, rendering, and animation. */
public final class GuideToastSlotReservations<T> {
    private final T[] owners;
    private final int[] spans;

    public GuideToastSlotReservations(T[] emptySlots) {
        owners = Objects.requireNonNull(emptySlots, "emptySlots");
        if (owners.length == 0) throw new IllegalArgumentException("empty slot capacity");
        for (T owner : owners) {
            if (owner != null) throw new IllegalArgumentException("slots must start empty");
        }
        spans = new int[owners.length];
    }

    public boolean canReserve(int firstSlot, int count) {
        if (count <= 0 || firstSlot < 0 || count > owners.length
                || firstSlot > owners.length - count) return false;
        for (int slot = firstSlot; slot < firstSlot + count; slot++) {
            if (owners[slot] != null) return false;
        }
        return true;
    }

    public void reserve(T owner, int firstSlot, int count) {
        Objects.requireNonNull(owner, "owner");
        if (!canReserve(firstSlot, count)) throw new IllegalStateException("slots unavailable");
        Arrays.fill(owners, firstSlot, firstSlot + count, owner);
        spans[firstSlot] = count;
    }

    /** Release only this native instance's span, even if another instance uses the same toast object. */
    public boolean release(T owner, int firstSlot) {
        if (firstSlot < 0 || firstSlot >= owners.length || spans[firstSlot] == 0
                || owners[firstSlot] != owner) return false;
        Arrays.fill(owners, firstSlot, firstSlot + spans[firstSlot], null);
        spans[firstSlot] = 0;
        return true;
    }

    public void clear() {
        Arrays.fill(owners, null);
        Arrays.fill(spans, 0);
    }
}
