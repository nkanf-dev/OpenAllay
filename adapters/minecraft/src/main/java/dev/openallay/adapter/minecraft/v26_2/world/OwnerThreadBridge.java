package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Only Java-owned actions reach game executors. No JavaScript callback crosses this boundary. */
final class OwnerThreadBridge implements AutoCloseable {
    @dev.openallay.value.ValueType(Owner.ValueSchemaProvider.class)
static final class Owner {
    private final Executor executor;
    private final BooleanSupplier isOwnerThread;
    private final Runnable validate;
    Owner(Executor executor, BooleanSupplier isOwnerThread, Runnable validate) {
        this.executor = executor;
        this.isOwnerThread = isOwnerThread;
        this.validate = validate;
    }
    public Executor executor() { return executor; }
    public BooleanSupplier isOwnerThread() { return isOwnerThread; }
    public Runnable validate() { return validate; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Owner)) return false;
        Owner that = (Owner) other;
        return java.util.Objects.equals(executor, that.executor) && java.util.Objects.equals(isOwnerThread, that.isOwnerThread) && java.util.Objects.equals(validate, that.validate);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(executor);
        hash = 31 * hash + java.util.Objects.hashCode(isOwnerThread);
        hash = 31 * hash + java.util.Objects.hashCode(validate);
        return hash;
    }
    @Override public String toString() { return "Owner[executor=" + executor + ", isOwnerThread=" + isOwnerThread + ", validate=" + validate + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Owner> schema() {
            return new dev.openallay.value.ValueSchema<>(Owner.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Owner>>asList(new dev.openallay.value.ValueSchema.Component<>(Owner.class, "executor", Owner::executor), new dev.openallay.value.ValueSchema.Component<>(Owner.class, "isOwnerThread", Owner::isOwnerThread), new dev.openallay.value.ValueSchema.Component<>(Owner.class, "validate", Owner::validate)), arguments -> new Owner((Executor) arguments[0], (BooleanSupplier) arguments[1], (Runnable) arguments[2]));
        }
    }
}
    private final Thread worker = Thread.currentThread();
    private final Runnable requireActive;
    private final java.util.concurrent.atomic.AtomicLong dispatches = new java.util.concurrent.atomic.AtomicLong();
    private volatile BooleanSupplier anyOwnerThread;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Pending<?>> pending = ConcurrentHashMap.newKeySet();
    @dev.openallay.value.ValueType(Pending.ValueSchemaProvider.class)
private static final class Pending<T> {
    private final CompletableFuture<T> future;
    private final AtomicBoolean claimed;
    private Pending(CompletableFuture<T> future, AtomicBoolean claimed) {
        this.future = future;
        this.claimed = claimed;
    }
    public CompletableFuture<T> future() { return future; }
    public AtomicBoolean claimed() { return claimed; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Pending)) return false;
        Pending that = (Pending) other;
        return java.util.Objects.equals(future, that.future) && java.util.Objects.equals(claimed, that.claimed);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(future);
        hash = 31 * hash + java.util.Objects.hashCode(claimed);
        return hash;
    }
    @Override public String toString() { return "Pending[future=" + future + ", claimed=" + claimed + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Pending> schema() {
            return new dev.openallay.value.ValueSchema<>(Pending.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Pending>>asList(new dev.openallay.value.ValueSchema.Component<>(Pending.class, "future", Pending::future), new dev.openallay.value.ValueSchema.Component<>(Pending.class, "claimed", Pending::claimed)), arguments -> new Pending((CompletableFuture) arguments[0], (AtomicBoolean) arguments[1]));
        }
    }
}

    OwnerThreadBridge(Runnable requireActive, BooleanSupplier anyOwnerThread) {
        this.requireActive = requireActive;
        this.anyOwnerThread = anyOwnerThread;
    }

    void checkWorker() {
        checkActive();
        if (anyOwnerThread.getAsBoolean()) throw new ExtensionException("owner_thread_wait", "Game owner threads cannot wait for native world work");
        if (Thread.currentThread() != worker) throw new ExtensionException("wrong_worker", "Native world sessions belong to the invocation worker");
        checkActive();
    }

    void checkActive() {
        if (closed.get()) throw new ExtensionException("session_closed", "Native world session is closed or cancelled");
        requireActive.run();
    }

    <T> T call(Owner owner, Callable<T> action) { return callAfter(null,owner,action); }

    /** Chain owner validation directly to target dispatch. Neither game owner waits. */
    <T> T callAfter(Owner gate, Owner owner, Callable<T> action) {
        checkWorker();
        if (owner.isOwnerThread().getAsBoolean() || gate != null && gate.isOwnerThread().getAsBoolean())
            throw new ExtensionException("owner_thread_wait", "Cannot wait on a target owner thread");
        CompletableFuture<T> result = new CompletableFuture<>();
        Pending<T> task = new Pending<>(result, new AtomicBoolean());
        pending.add(task);
        Runnable execute = () -> {
            if (!task.claimed().compareAndSet(false, true)) return;
            try {
                validateOwner(owner);
                result.complete(action.call());
            } catch (Throwable failure) { result.completeExceptionally(failure); }
        };
        try {
            checkActive();
            dispatches.incrementAndGet();
            if (gate == null) owner.executor().execute(execute);
            else gate.executor().execute(() -> {
                // Claim only when the actual bounded target action starts. close() may
                // still cancel a server task queued after the client gate has run.
                if (task.claimed().get()) return;
                try {
                    validateOwner(gate);
                    dispatches.incrementAndGet();
                    owner.executor().execute(execute);
                } catch (Throwable failure) {
                    if (task.claimed().compareAndSet(false,true)) result.completeExceptionally(failure);
                }
            });
            return result.get();
        } catch (InterruptedException interrupted) {
            close();
            // Queued work is cancelled. Started bounded actions must publish their
            // outcome before worker journal cleanup; never discard that readback.
            try { return result.join(); }
            catch (java.util.concurrent.CompletionException failed) { throw propagate(failed.getCause()); }
            finally { Thread.currentThread().interrupt(); }
        } catch (ExecutionException failed) { throw propagate(failed.getCause()); }
        catch (RuntimeException failed) {
            if (task.claimed().compareAndSet(false, true)) result.completeExceptionally(failed);
            throw propagate(failed);
        }
        finally { pending.remove(task); }
    }

    private void validateOwner(Owner owner) {
        checkActive();
        if (!owner.isOwnerThread().getAsBoolean()) throw new ExtensionException("wrong_owner", "Native action ran on the wrong thread");
        owner.validate().run();
        checkActive();
    }
    static RuntimeException propagate(Throwable cause) {
        if (cause instanceof ExtensionException extension) return extension;
        if (cause instanceof Error error) throw error;
        if (cause instanceof IllegalArgumentException)
            return new ExtensionException("invalid_native_input", "The native world operation has invalid input", cause);
        return new ExtensionException("native_failure", "The native world operation failed", cause);
    }

    long dispatches() { return dispatches.get(); }
    boolean isClosed() { return closed.get(); }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            // onCancel has no unregister handle. Do not keep native executor references idle.
            anyOwnerThread = () -> false;
            ExtensionException failure = new ExtensionException("session_closed", "Native world session is closed or cancelled");
            pending.forEach(task -> {
                if (task.claimed().compareAndSet(false, true)) task.future().completeExceptionally(failure);
            });
        }
    }
}
