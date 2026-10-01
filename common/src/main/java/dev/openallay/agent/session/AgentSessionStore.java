package dev.openallay.agent.session;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AgentSessionStore {
    public record Lease(
            AgentSessionKey key,
            UUID requestId,
            CancellationSignal cancellation,
            List<ModelMessage> history,
            List<ContextCheckpoint> checkpoints,
            Progress progress) {
        public Lease {
            history = List.copyOf(history);
            checkpoints = List.copyOf(checkpoints);
        }
    }

    /** Updated only at complete structural boundaries. */
    public static final class Progress {
        private List<ModelMessage> projected;
        private List<ModelMessage> original;
        private final int requestFromIndex;

        private Progress(List<ModelMessage> history) {
            projected = dev.openallay.agent.context.ModelContextCodec.safe(history);
            original = projected;
            requestFromIndex = projected.size();
        }

        public synchronized void record(List<ModelMessage> projected, List<ModelMessage> original) {
            this.projected = dev.openallay.agent.context.ModelContextCodec.safe(projected);
            this.original = dev.openallay.agent.context.ModelContextCodec.safe(original);
        }

        public synchronized List<ModelMessage> projected() { return projected; }
        public synchronized List<ModelMessage> original() { return original; }
        public synchronized List<ModelMessage> requestMessages() {
            return List.copyOf(original.subList(requestFromIndex, original.size()));
        }
    }

    public record Status(boolean active, UUID requestId, int historyMessages) {}

    private static final class Session {
        private List<ModelMessage> history = List.of();
        private List<ContextCheckpoint> checkpoints = List.of();
        private Lease active;
        private Lease latest;
    }

    private final Map<AgentSessionKey, Session> sessions = new HashMap<>();

    public synchronized ToolResult<Lease> reserve(AgentSessionKey key, UUID requestId) {
        return reserve(key, requestId, null, null);
    }

    public synchronized ToolResult<Lease> reserveWithHistory(
            AgentSessionKey key, UUID requestId, List<ModelMessage> history) {
        return reserve(key, requestId, history, List.of());
    }

    private ToolResult<Lease> reserve(
            AgentSessionKey key,
            UUID requestId,
            List<ModelMessage> replacementHistory,
            List<ContextCheckpoint> replacementCheckpoints) {
        Session session = sessions.computeIfAbsent(key, ignored -> new Session());
        if (session.active != null) {
            return new ToolResult.Failure<>(
                    "agent_busy", "An Agent request is already active in this session");
        }
        if (replacementHistory != null) {
            session.history = List.copyOf(replacementHistory);
            session.checkpoints = List.copyOf(replacementCheckpoints);
        }
        Lease lease = new Lease(
                key, requestId, new CancellationSignal(), session.history, session.checkpoints,
                new Progress(session.history));
        session.active = lease;
        session.latest = lease;
        return new ToolResult.Success<>(lease);
    }

    public synchronized boolean recordContext(
            Lease lease, List<ModelMessage> projected, List<ModelMessage> original) {
        lease.progress().record(projected, original);
        Session session = sessions.get(lease.key());
        return session != null && session.active == lease;
    }

    public synchronized boolean finish(Lease lease, List<ModelMessage> history) {
        Session session = sessions.get(lease.key());
        if (session == null || session.active != lease) {
            return false;
        }
        session.history = List.copyOf(history);
        session.active = null;
        return true;
    }

    /** A detached cancelled worker may finalize only while no successor lease was submitted. */
    public synchronized boolean finalizeCancelled(Lease lease, List<ModelMessage> history) {
        Session session = sessions.get(lease.key());
        if (session == null || session.latest != lease || session.active != null) return false;
        session.history = List.copyOf(history);
        return true;
    }

    public synchronized boolean recordCheckpoint(Lease lease, ContextCheckpoint checkpoint) {
        Session session = sessions.get(lease.key());
        if (session == null || session.active != lease) {
            return false;
        }
        java.util.ArrayList<ContextCheckpoint> updated = new java.util.ArrayList<>(session.checkpoints);
        updated.add(checkpoint);
        session.checkpoints = List.copyOf(updated);
        return true;
    }

    public synchronized List<ContextCheckpoint> checkpoints(AgentSessionKey key) {
        Session session = sessions.get(key);
        return session == null ? List.of() : session.checkpoints;
    }

    public synchronized void hydrate(AgentSessionKey key, List<ModelMessage> history) {
        hydrate(key, history, List.of());
    }

    public synchronized void hydrate(
            AgentSessionKey key,
            List<ModelMessage> history,
            List<ContextCheckpoint> checkpoints) {
        Session session = sessions.computeIfAbsent(key, ignored -> new Session());
        if (session.active != null) {
            throw new IllegalStateException("cannot hydrate an active Agent session");
        }
        session.history = List.copyOf(history);
        session.checkpoints = List.copyOf(checkpoints);
    }

    public boolean cancel(AgentSessionKey key) {
        return cancel(key, null);
    }

    /** An old server/UI request can never revoke a newer lease in the same session. */
    public boolean cancel(AgentSessionKey key, UUID expectedRequestId) {
        Lease cancelled;
        synchronized (this) {
            Session session = sessions.get(key);
            if (session == null || session.active == null
                    || (expectedRequestId != null && !expectedRequestId.equals(session.active.requestId()))) {
                return false;
            }
            cancelled = session.active;
        }
        boolean accepted = cancelled.cancellation().cancel(
                java.util.concurrent.ForkJoinPool.commonPool());
        synchronized (this) {
            Session session = sessions.get(key);
            if (accepted && session != null && session.active == cancelled) {
                java.util.ArrayList<ModelMessage> retained = new java.util.ArrayList<>(
                        cancelled.progress().projected());
                retained.add(new ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.Text(
                                "[OpenAllay request ended: agent_cancelled] Agent request was cancelled"))));
                session.history = List.copyOf(retained);
                session.active = null;
            }
        }
        return accepted;
    }

    public synchronized void clear(AgentSessionKey key) {
        Session session = sessions.remove(key);
        if (session != null && session.active != null) {
            session.active.cancellation().cancel(java.util.concurrent.ForkJoinPool.commonPool());
        }
    }

    public synchronized void clearActor(UUID actorId) {
        List<AgentSessionKey> keys = sessions.keySet().stream()
                .filter(key -> key.actorId().equals(actorId))
                .toList();
        keys.forEach(this::clear);
    }

    public synchronized List<AgentSessionKey> sessions(UUID actorId) {
        return sessions.keySet().stream()
                .filter(key -> key.actorId().equals(actorId))
                .sorted(java.util.Comparator.comparing(AgentSessionKey::sessionId))
                .toList();
    }

    public synchronized boolean hasContext(AgentSessionKey key) {
        Session session = sessions.get(key);
        return session != null && !session.history.isEmpty();
    }

    public synchronized Status status(AgentSessionKey key) {
        Session session = sessions.get(key);
        if (session == null) {
            return new Status(false, null, 0);
        }
        return new Status(
                session.active != null,
                session.active == null ? null : session.active.requestId(),
                session.history.size());
    }
}
