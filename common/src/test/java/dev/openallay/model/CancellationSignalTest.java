package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CancellationSignalTest {
    @Test
    void failingListenerDoesNotPreventOtherRevocationsOrTerminalCleanup() {
        CancellationSignal signal = new CancellationSignal();
        List<String> calls = new ArrayList<>();
        signal.onCancel(() -> { calls.add("first"); throw new IllegalStateException("secret-token"); });
        signal.onCancel(() -> calls.add("second"));
        assertTrue(signal.cancel());
        assertTrue(signal.isCancelled());
        assertEquals(List.of("first", "second"), calls);
        assertFalse(signal.cancel());
        assertEquals(List.of("first", "second"), calls);
    }
}
