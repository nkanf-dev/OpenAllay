package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.world.storage.DimensionSavedDataManager;

/** Bounded owner-thread receipts for one actual native SavedData read. No game dispatch. */
public final class NativeSavedDataReadObservation {
    private static final ThreadLocal<Deque<Scope>> ACTIVE = new ThreadLocal<>();
    private NativeSavedDataReadObservation() {}
    static Scope begin(DimensionSavedDataManager manager, String id) {
        Deque<Scope> stack = ACTIVE.get();
        if (stack == null) { stack = new ArrayDeque<>(); ACTIVE.set(stack); }
        Scope scope = new Scope(manager, id, stack);
        stack.push(scope);
        return scope;
    }
    /** Called only by the exact native catch observer, after preserving native logging. */
    public static void observe(DimensionSavedDataManager manager, String id, Exception failure) {
        Deque<Scope> stack = ACTIVE.get();
        if (stack == null) return;
        for (Scope scope : stack) {
            if (scope.manager == manager && scope.id.equals(id)) {
                if (scope.failure == null) scope.failure = failure;
                else if (scope.failure != failure) scope.failure.addSuppressed(failure);
                return;
            }
        }
    }
    static final class Scope implements AutoCloseable {
        final DimensionSavedDataManager manager;
        final String id;
        final Deque<Scope> stack;
        Exception failure;
        private boolean closed;
        Scope(DimensionSavedDataManager manager, String id, Deque<Scope> stack) {
            this.manager = manager; this.id = id; this.stack = stack;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (stack.peek() != this) {
                ACTIVE.remove();
                throw new IllegalStateException("Native SavedData observation scopes closed out of order");
            }
            stack.pop();
            if (stack.isEmpty()) ACTIVE.remove();
        }
    }
}
