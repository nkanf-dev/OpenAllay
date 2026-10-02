package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.tool.ToolResult;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SettingsSaveCoordinatorTest {
    @Test
    void repeatedClicksKeepOneOperationIdAndWaitForTheDispatchedPersistenceReceipt() {
        var coordinator = new SettingsSaveCoordinator();
        var receipt = new CompletableFuture<ToolResult<Boolean>>();
        var client = new ArrayDeque<Runnable>();
        var writes = new AtomicInteger();
        var commits = new AtomicInteger();
        assertTrue(coordinator.save(() -> { writes.incrementAndGet(); return receipt; },
                client::add, () -> {}, result -> commits.incrementAndGet()));
        long operationId = coordinator.activeId();
        assertTrue(operationId > 0);
        assertFalse(coordinator.save(() -> { writes.incrementAndGet(); return receipt; },
                client::add, () -> {}, result -> commits.incrementAndGet()));
        assertEquals(operationId, coordinator.activeId());
        assertEquals(1, writes.get());
        receipt.complete(new ToolResult.Success<>(true));
        assertTrue(coordinator.busy());
        assertEquals(0, commits.get());
        client.removeFirst().run();
        assertFalse(coordinator.busy());
        assertEquals(1, commits.get());
    }

    @Test
    void exceptionalReceiptAndSynchronousRejectionReleaseTheSlotWithoutSuccess() {
        var coordinator = new SettingsSaveCoordinator();
        var receipt = new CompletableFuture<ToolResult<Boolean>>();
        coordinator.save(() -> receipt, Runnable::run, () -> {},
                result -> assertInstanceOf(ToolResult.Failure.class, result));
        receipt.completeExceptionally(new IllegalStateException("synthetic failure"));
        assertFalse(coordinator.busy());
        assertTrue(coordinator.save(() -> { throw new IllegalStateException("rejected"); },
                Runnable::run, () -> {}, result -> assertEquals("settings_save_failed",
                        assertInstanceOf(ToolResult.Failure.class, result).code())));
        assertFalse(coordinator.busy());
    }
}
