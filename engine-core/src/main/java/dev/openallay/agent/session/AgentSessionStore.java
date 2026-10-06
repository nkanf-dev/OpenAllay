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
            Progress progress,
            SteerInbox steers,
            dev.openallay.skill.RetainedSkillContext retainedSkills) {
        public Lease {
            history = List.copyOf(history);
            checkpoints = List.copyOf(checkpoints);
        }
    }

    public record Steer(UUID messageId, ModelMessage message) {
        public Steer {
            java.util.Objects.requireNonNull(messageId, "messageId");
            java.util.Objects.requireNonNull(message, "message");
            if (message.role() != dev.openallay.model.ModelRole.USER) {
                throw new IllegalArgumentException("steer must be a user message");
            }
        }
    }

    /** Owned by one immutable request lease; all access uses the enclosing store lock. */
    public static final class SteerInbox {
        private final java.util.LinkedHashMap<UUID, ModelMessage> pending = new java.util.LinkedHashMap<>();
        private final java.util.Set<UUID> consumed = new java.util.HashSet<>();
        private boolean sealed;
    }

    public synchronized ToolResult<Boolean> steer(
            AgentSessionKey key, UUID requestId, UUID messageId, ModelMessage message) {
        Steer instruction = new Steer(messageId, message);
        Session session = sessions.get(key);
        if (session == null || session.active == null
                || !session.active.requestId().equals(requestId)
                || session.active.cancellation().isCancelled()) return new ToolResult.Success<>(false);
        SteerInbox inbox = session.active.steers();
        if (inbox.sealed || inbox.consumed.contains(messageId)) return new ToolResult.Success<>(false);
        inbox.pending.put(instruction.messageId(), instruction.message());
        return new ToolResult.Success<>(true);
    }

    public synchronized boolean cancelSteer(AgentSessionKey key, UUID requestId, UUID messageId) {
        Session session = sessions.get(key);
        return session != null && session.active != null
                && session.active.requestId().equals(requestId)
                && session.active.steers().pending.remove(messageId) != null;
    }

    public synchronized List<Steer> drainSteers(Lease lease) {
        Session session = sessions.get(lease.key());
        if (session == null || session.active != lease || lease.cancellation().isCancelled()
                || lease.steers().sealed) return List.of();
        List<Steer> instructions = lease.steers().pending.entrySet().stream()
                .map(entry -> new Steer(entry.getKey(), entry.getValue())).toList();
        instructions.forEach(instruction -> lease.steers().consumed.add(instruction.messageId()));
        lease.steers().pending.clear();
        return instructions;
    }

    /** A final response has no next model boundary; remaining instructions belong to follow-up. */
    public synchronized void sealSteers(Lease lease) {
        lease.steers().sealed = true;
        lease.steers().pending.clear();
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

    /** Exclusive idle control reservation. Preparing a summary never changes actual context. */
    public record ControlLease(AgentSessionKey key, UUID controlId, CancellationSignal cancellation,
            List<ModelMessage> history) {
        public ControlLease { history = List.copyOf(history); }
    }

    public synchronized ToolResult<ControlLease> reserveControl(AgentSessionKey key, UUID controlId,
            List<ModelMessage> durableSeed) {
        Session session = sessions.computeIfAbsent(key, ignored -> new Session());
        if (session.active != null || session.control != null) {
            return new ToolResult.Failure<>("compact_busy",
                    "Wait for the current request to finish before using /compact");
        }
        List<ModelMessage> actual = session.history.isEmpty() && durableSeed != null
                ? durableSeed : session.history;
        ControlLease lease = new ControlLease(key, controlId, new CancellationSignal(),
                dev.openallay.agent.context.ModelContextCodec.safe(actual));
        // An idle control is a successor ownership epoch. A detached cancelled worker must
        // never replace the source while preparing, or overwrite its later published projection.
        session.latest = null;
        session.control = lease;
        return new ToolResult.Success<>(lease);
    }

    public synchronized boolean ownsControl(ControlLease lease) {
        Session session = sessions.get(lease.key());
        return session != null && session.control == lease && !lease.cancellation().isCancelled();
    }

    /** Publish only after the service's prepared durable write and generation fence succeeded. */
    public synchronized boolean publishControl(ControlLease lease, List<ModelMessage> projection) {
        if (!ownsControl(lease)) return false;
        Session session = sessions.get(lease.key());
        session.history = dev.openallay.agent.context.ModelContextCodec.safe(projection);
        session.checkpoints = List.of();
        session.retainedSkills = new dev.openallay.skill.RetainedSkillContext();
        session.control = null;
        return true;
    }

    public synchronized void releaseControl(ControlLease lease) {
        Session session = sessions.get(lease.key());
        if (session != null && session.control == lease) session.control = null;
    }

    private static final class Session {
        private List<ModelMessage> history = List.of();
        private List<ContextCheckpoint> checkpoints = List.of();
        private Lease active;
        private ControlLease control;
        private Lease latest;
        private dev.openallay.skill.RetainedSkillContext retainedSkills =
                new dev.openallay.skill.RetainedSkillContext();
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
        if (session.active != null || session.control != null) {
            return new ToolResult.Failure<>(
                    "agent_busy", "An Agent request or context control is already active in this session");
        }
        if (replacementHistory != null) {
            session.history = List.copyOf(replacementHistory);
            session.checkpoints = List.copyOf(replacementCheckpoints);
            session.retainedSkills = new dev.openallay.skill.RetainedSkillContext();
        }
        Lease lease = new Lease(
                key, requestId, new CancellationSignal(), session.history, session.checkpoints,
                new Progress(session.history), new SteerInbox(), session.retainedSkills);
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
        pruneCheckpointIndex(session);
        return true;
    }

    /** A detached cancelled worker may finalize only while no successor lease was submitted. */
    public synchronized boolean finalizeCancelled(Lease lease, List<ModelMessage> history) {
        Session session = sessions.get(lease.key());
        if (session == null || session.latest != lease || session.active != null
                || session.control != null) return false;
        session.history = List.copyOf(history);
        pruneCheckpointIndex(session);
        return true;
    }

    /** Runtime reuse index only; durable ContextCompacted events keep every diagnostic record. */
    private static void pruneCheckpointIndex(Session session) {
        com.google.gson.Gson gson = dev.openallay.json.EngineJson.create();
        session.checkpoints = session.checkpoints.stream().filter(checkpoint ->
                checkpoint.status() == ContextCheckpoint.Status.SUCCEEDED
                        && checkpoint.sourceToIndexExclusive() <= session.history.size()
                        && checkpoint.sourceHash().equals(dev.openallay.agent.context.ContextSourceHash.compute(
                                gson, session.history.subList(checkpoint.sourceFromIndex(),
                                        checkpoint.sourceToIndexExclusive())))).toList();
    }

    public synchronized boolean recordCheckpoint(Lease lease, ContextCheckpoint checkpoint) {
        Session session = sessions.get(lease.key());
        if (session == null || session.active != lease) {
            return false;
        }
        if (checkpoint.status() != ContextCheckpoint.Status.SUCCEEDED) return true;
        java.util.ArrayList<ContextCheckpoint> updated = new java.util.ArrayList<>(session.checkpoints);
        updated.removeIf(existing -> existing.sourceFromIndex() == checkpoint.sourceFromIndex()
                && existing.sourceToIndexExclusive() == checkpoint.sourceToIndexExclusive()
                && existing.sourceHash().equals(checkpoint.sourceHash()));
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
        if (session.active != null || session.control != null) {
            throw new IllegalStateException("cannot hydrate an active Agent session or context control");
        }
        session.history = List.copyOf(history);
        session.checkpoints = List.copyOf(checkpoints);
        session.retainedSkills = new dev.openallay.skill.RetainedSkillContext();
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
            cancelled.steers().sealed = true;
            cancelled.steers().pending.clear();
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
                // The cancelled worker may still finish a prepare callback after Stop. Its lease
                // keeps the old index; a successor reconciles its own actual projection in a new one.
                session.retainedSkills = new dev.openallay.skill.RetainedSkillContext();
            }
        }
        return accepted;
    }

    public synchronized void clear(AgentSessionKey key) {
        Session session = sessions.remove(key);
        if (session != null && session.active != null) {
            session.active.cancellation().cancel(java.util.concurrent.ForkJoinPool.commonPool());
        }
        if (session != null && session.control != null) {
            session.control.cancellation().cancel(java.util.concurrent.ForkJoinPool.commonPool());
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

    /** Immutable actor/session-scoped snapshot of the actual current context. */
    public synchronized List<ModelMessage> history(AgentSessionKey key) {
        Session session = sessions.get(key);
        if (session == null) return List.of();
        return List.copyOf(session.active == null
                ? session.history : session.active.progress().projected());
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
