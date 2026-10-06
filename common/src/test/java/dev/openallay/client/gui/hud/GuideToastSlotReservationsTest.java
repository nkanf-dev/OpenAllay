package dev.openallay.client.gui.hud;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.Test;

/** Pure occupancy tests. Strings are test owners, not substitute native Toast classes. */
final class GuideToastSlotReservationsTest {
    @Test void mixedNormalAndGuideDemandFitsExactlyFivePhysicalSlots() {
        var slots = new GuideToastSlotReservations<>(new String[5]);
        slots.reserve("normal", 0, 1);
        slots.reserve("guide-a", 1, 2);
        slots.reserve("guide-b", 3, 2);
        for (int index = 0; index < 5; index++) assertFalse(slots.canReserve(index, 1));
        assertFalse(slots.canReserve(4, 2));
        assertEquals(160, 5 * 32);
        assertEquals(32, 1 * 32);
        assertEquals(96, 3 * 32);
        assertEquals(160, 3 * 32 + 64);
    }

    @Test void reservedContinuationAndFragmentedTailDoNotConsumeOrReorderTheHead() {
        var slots = new GuideToastSlotReservations<>(new String[5]);
        slots.reserve("guide-visible", 0, 2);
        slots.reserve("normal-visible", 3, 1);
        Deque<String> queue = new ArrayDeque<>();
        queue.addLast("guide-head");
        queue.addLast("normal-next");
        assertFalse(slots.canReserve(1, 2), "continuation has no native instance but stays occupied");
        assertFalse(slots.canReserve(2, 2), "fragmented space cannot accept two adjacent slots");
        assertFalse(slots.canReserve(4, 2), "a two-slot head cannot exceed the five-slot tail");
        assertEquals("guide-head", queue.peekFirst());
        assertEquals(2, queue.size());
        assertTrue(slots.release("normal-visible", 3));
        assertTrue(slots.canReserve(2, 2));
        slots.reserve(queue.removeFirst(), 2, 2);
        assertEquals("normal-next", queue.peekFirst());
        assertTrue(slots.canReserve(4, 1));
        slots.reserve(queue.removeFirst(), 4, 1);
        assertTrue(queue.isEmpty());
    }

    @Test void completionReleasesOnlyItsHeadOnceAndAllowsImmediateCapacityReuse() {
        var slots = new GuideToastSlotReservations<>(new String[5]);
        String guide = new String("guide");
        slots.reserve(guide, 1, 2);
        assertFalse(slots.release(guide, 2), "continuation is not a native instance head");
        assertFalse(slots.release(new String("guide"), 1), "native identity must match");
        assertFalse(slots.canReserve(1, 2), "no release while native hide animation is active");
        assertTrue(slots.release(guide, 1));
        assertFalse(slots.release(guide, 1));
        assertTrue(slots.canReserve(1, 2));
        slots.reserve("next", 1, 2);
        assertFalse(slots.release(guide, 1), "stale completion cannot free a new owner");
        assertFalse(slots.canReserve(1, 2));
    }

    @Test void twoNativeInstancesOfTheSameObjectOwnSeparateSpans() {
        var slots = new GuideToastSlotReservations<>(new String[5]);
        String repeated = new String("same-object");
        slots.reserve(repeated, 0, 2);
        slots.reserve(repeated, 2, 2);
        assertTrue(slots.release(repeated, 0));
        assertTrue(slots.canReserve(0, 2));
        assertFalse(slots.canReserve(2, 2));
        assertTrue(slots.release(repeated, 2));
    }

    @Test void nativeClearDropsEveryReservationAndOldCompletionCannotReleaseNewOwners() {
        var slots = new GuideToastSlotReservations<>(new String[5]);
        String guide = new String("guide");
        slots.reserve(guide, 0, 2);
        slots.reserve("normal", 2, 1);
        slots.reserve("guide-2", 3, 2);
        slots.clear();
        for (int index = 0; index < 5; index++) assertTrue(slots.canReserve(index, 1));
        assertFalse(slots.release(guide, 0));
        slots.reserve("new", 0, 2);
        assertFalse(slots.release(guide, 0));
        assertFalse(slots.canReserve(0, 2));
    }

    @Test void invalidDemandFailsInsteadOfClampingGeometry() {
        var slots = new GuideToastSlotReservations<>(new String[5]);
        assertFalse(slots.canReserve(-1, 2));
        assertFalse(slots.canReserve(0, 0));
        assertFalse(slots.canReserve(0, 6));
        assertFalse(slots.canReserve(4, 2));
        assertThrows(IllegalStateException.class, () -> slots.reserve("too-tall", 0, 6));
        assertThrows(NullPointerException.class, () -> slots.reserve(null, 0, 1));
    }
}
