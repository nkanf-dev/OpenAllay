package dev.openallay.server;

import dev.openallay.bridge.server.ServerBridgeSession;
import dev.openallay.context.minecraft.MinecraftServerPlayerLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Small native send/task custody owner shared by loader transports; no request registry. */
public final class NativeServerActorHandoffs {
    // Native task custody budget, not a wire format or player permission gate.
    private static final int MAX_PENDING_HANDOFFS = 1024;
    private final MinecraftServer server;
    private final Object lock = new Object();
    private final Map<UUID, Admission> actors = new HashMap<>();
    private final Set<Handoff> pending = new HashSet<>();
    private boolean stopped;
    public NativeServerActorHandoffs(MinecraftServer server) { this.server = server; }
    private void owner() {
        if (!server.isSameThread()) throw new IllegalStateException("Native handoff requires server owner");
    }
    public void admit(ServerPlayer player) {
        owner();
        UUID actor = player.getUUID();
        Admission value = new Admission(player, NativeServerConnectionGuard.capture(player));
        synchronized (lock) {
            if (stopped || actors.containsKey(actor)) throw new IllegalStateException("Actor must be revoked before admission");
            actors.put(actor, value);
        }
    }
    /** Revoke before callbacks. Returned retirement action is invoked outside all native locks. */
    public Runnable revoke(ServerPlayer player) {
        owner();
        UUID actor = player.getUUID();
        List<Handoff> detached = new ArrayList<>();
        synchronized (lock) {
            Admission old = actors.get(actor);
            if (old == null || old.player != player) return () -> {};
            actors.remove(actor);
            detach(old, detached);
        }
        return () -> retireAll(detached);
    }
    public Runnable stop() {
        owner();
        List<Handoff> detached = new ArrayList<>();
        synchronized (lock) {
            stopped = true;
            actors.clear();
            for (Handoff value : List.copyOf(pending)) {
                if (value.state == State.PENDING) { value.state = State.RETIRED; pending.remove(value); detached.add(value); }
            }
        }
        return () -> retireAll(detached);
    }
    private void detach(Admission token, List<Handoff> detached) {
        for (Handoff value : List.copyOf(pending)) {
            if (value.token == token && value.state == State.PENDING) {
                value.state = State.RETIRED; pending.remove(value); detached.add(value);
            }
        }
    }
    public ServerBridgeSession.Transport bind(UUID actor, ServerBridgeSession.Transport wire) {
        owner();
        Admission token;
        synchronized (lock) { token = actors.get(actor); }
        return new ServerBridgeSession.Transport() {
            @Override public ServerBridgeSession.Transport bind(UUID requested) {
                if (!actor.equals(requested)) throw new IllegalArgumentException("Bound actor differs");
                return this;
            }
            @Override public boolean send(UUID requested, String kind, String json) {
                if (!actor.equals(requested)) return false;
                // Tool sends use no-op retirement; no synchronous callback can enter Agent Owner.
                return dispatch(requested, () -> wire.send(actor, kind, json), () -> {});
            }
            @Override public boolean dispatch(UUID requested, Runnable action, Runnable retired) {
                if (!actor.equals(requested)) { retired.run(); return false; }
                return enqueue(actor, token, action, retired, false);
            }
            @Override public boolean dispatchLater(UUID requested, Runnable action, Runnable retired) {
                if (!actor.equals(requested)) { retired.run(); return false; }
                return enqueue(actor, token, action, retired, true);
            }
        };
    }
    private boolean admitted(UUID actor, Admission token) {
        synchronized (lock) { return token != null && !stopped && actors.get(actor) == token; }
    }
    private boolean nativeCurrent(UUID actor, Admission token) {
        owner();
        return admitted(actor, token) && server.getPlayerList().getPlayer(actor) == token.player
                && MinecraftServerPlayerLevel.get(token.player).getServer() == server && token.connection.getAsBoolean();
    }
    private boolean enqueue(UUID actor, Admission token, Runnable action, Runnable retired, boolean nativeSchedule) {
        Handoff value = new Handoff(actor, token, action, retired);
        boolean rejected;
        boolean atCapacity;
        synchronized (lock) {
            atCapacity = pending.size() >= MAX_PENDING_HANDOFFS;
            rejected = token == null || stopped || actors.get(actor) != token
                    || atCapacity;
            if (!rejected) pending.add(value);
            else value.state = State.RETIRED;
        }
        if (rejected) {
            retired.run();
            if (atCapacity) dev.openallay.OpenAllayConstants.LOGGER.warn("Native server handoff capacity reached; admission rejected");
            return false;
        }
        if (server.isSameThread() && !nativeSchedule) return run(value);
        try {
            if (nativeSchedule && server.isSameThread()) NativeServerDeferredHandoff.enqueue(server, () -> run(value));
            else server.execute(() -> run(value));
            return true;
        }
        catch (RuntimeException failure) {
            Runnable cleanup = retirePending(value);
            Throwable retained = attempt(failure, cleanup);
            if (retained instanceof Error fatal) throw fatal;
            dev.openallay.OpenAllayConstants.LOGGER.error("Native server handoff queue rejected", retained);
            return false;
        } catch (Error failure) {
            rethrow(attempt(failure, retirePending(value))); return false;
        }
    }
    private Runnable retirePending(Handoff value) {
        synchronized (lock) {
            if (value.state != State.PENDING) return () -> {};
            value.state = State.RETIRED; pending.remove(value);
        }
        return value.retired;
    }
    private boolean run(Handoff value) {
        owner();
        boolean claim;
        synchronized (lock) {
            if (value.state != State.PENDING) return false;
            claim = !stopped && actors.get(value.actor) == value.token;
            value.state = claim ? State.CLAIMED : State.RETIRED;
            if (!claim) pending.remove(value);
        }
        if (!claim) { value.retired.run(); return false; }
        Throwable failure = null;
        boolean executed = false;
        try {
            if (nativeCurrent(value.actor, value.token)) { value.action.run(); executed = true; }
        } catch (RuntimeException | Error error) { failure = error; }
        synchronized (lock) { value.state = State.RETIRED; pending.remove(value); }
        rethrow(attempt(failure, value.retired));
        return executed;
    }
    private static void retireAll(List<Handoff> values) {
        Throwable failure = null;
        for (Handoff value : values) failure = attempt(failure, value.retired);
        rethrow(failure);
    }
    /** Also used by native lifecycle owners: all cleanup is attempted, original Errors win. */
    public static void cleanup(Runnable... actions) {
        Throwable failure = null;
        for (Runnable action : actions) failure = attempt(failure, action);
        rethrow(failure);
    }
    private static Throwable attempt(Throwable first, Runnable action) {
        try { action.run(); return first; }
        catch (RuntimeException | Error second) {
            if (first == null) return second;
            if (second instanceof Error && !(first instanceof Error)) { second.addSuppressed(first); return second; }
            if (first != second) first.addSuppressed(second);
            return first;
        }
    }
    private static void rethrow(Throwable failure) {
        if (failure instanceof Error fatal) throw fatal;
        if (failure instanceof RuntimeException runtime) throw runtime;
    }
    private record Admission(ServerPlayer player, BooleanSupplier connection) {}
    private enum State { PENDING, CLAIMED, RETIRED }
    private static final class Handoff {
        final UUID actor; final Admission token; final Runnable action; final Runnable retired;
        State state = State.PENDING;
        Handoff(UUID actor, Admission token, Runnable action, Runnable retired) {
            this.actor = actor; this.token = token; this.action = action; this.retired = retired;
        }
    }
}
