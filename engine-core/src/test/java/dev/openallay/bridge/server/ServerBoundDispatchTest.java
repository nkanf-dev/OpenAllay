package dev.openallay.bridge.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Deterministic pure-host retirement/error contract; no native isolation claim. */
final class ServerBoundDispatchTest {
    @Test void fatalActionRetiresAndPreservesOriginalError() {
        ServerBridgeSession.Transport transport = (actor, kind, json) -> true;
        AssertionError fatal = new AssertionError("action");
        IllegalStateException cleanup = new IllegalStateException("cleanup");
        AtomicInteger retired = new AtomicInteger();
        Error caught = assertThrows(Error.class, () -> transport.dispatch(UUID.randomUUID(),
                () -> { throw fatal; }, () -> { retired.incrementAndGet(); throw cleanup; }));
        assertSame(fatal, caught);
        assertEquals(1, retired.get());
        assertArrayEquals(new Throwable[] {cleanup}, fatal.getSuppressed());
    }
    @Test void identicalFatalActionAndRetirementDoesNotSelfSuppress() {
        ServerBridgeSession.Transport transport = (actor, kind, json) -> true;
        AssertionError fatal = new AssertionError("same fatal");
        Error caught = assertThrows(Error.class, () -> transport.dispatch(UUID.randomUUID(),
                () -> { throw fatal; }, () -> { throw fatal; }));
        assertSame(fatal, caught);
        assertEquals(0, caught.getSuppressed().length);
    }

}
