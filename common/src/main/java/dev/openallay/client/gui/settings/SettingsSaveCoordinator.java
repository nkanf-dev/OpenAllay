package dev.openallay.client.gui.settings;

import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One editor save at a time. Only the dispatched persistence receipt can commit or close it. */
public final class SettingsSaveCoordinator {
    private long nextId;
    private long activeId;

    public boolean busy() { return activeId != 0; }
    public long activeId() { return activeId; }

    public boolean save(Supplier<? extends CompletableFuture<? extends ToolResult<?>>> action,
            Executor dispatcher, Runnable started, Consumer<ToolResult<?>> completed) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(completed, "completed");
        if (busy()) return false;
        long id = activeId = ++nextId;
        started.run();
        CompletableFuture<? extends ToolResult<?>> receipt;
        try {
            receipt = Objects.requireNonNull(action.get(), "save receipt");
        } catch (RuntimeException rejected) {
            receipt = CompletableFuture.completedFuture(failed());
        }
        receipt.whenComplete((result, failure) -> dispatcher.execute(() -> {
            if (activeId != id) return;
            activeId = 0;
            completed.accept(failure == null && result != null ? result : failed());
        }));
        return true;
    }

    private static ToolResult.Failure<Boolean> failed() {
        return new ToolResult.Failure<>("settings_save_failed", "Unable to save settings");
    }
}
