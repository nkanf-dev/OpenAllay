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
    @dev.openallay.value.ValueType(Lease.ValueSchemaProvider.class)
public static final class Lease {
    private final AgentSessionKey key;
    private final UUID requestId;
    private final CancellationSignal cancellation;
    private final List<ModelMessage> history;
    private final List<ContextCheckpoint> checkpoints;
    private final Progress progress;
    private final SteerInbox steers;
    private final dev.openallay.skill.RetainedSkillContext retainedSkills;
    public Lease(AgentSessionKey key, UUID requestId, CancellationSignal cancellation, List<ModelMessage> history, List<ContextCheckpoint> checkpoints, Progress progress, SteerInbox steers, dev.openallay.skill.RetainedSkillContext retainedSkills) {

            history = dev.openallay.util.Java8Collections.listCopyOf(history);
            checkpoints = dev.openallay.util.Java8Collections.listCopyOf(checkpoints);

        this.key = key;
        this.requestId = requestId;
        this.cancellation = cancellation;
        this.history = history;
        this.checkpoints = checkpoints;
        this.progress = progress;
        this.steers = steers;
        this.retainedSkills = retainedSkills;
    }
    public AgentSessionKey key() { return key; }
    public UUID requestId() { return requestId; }
    public CancellationSignal cancellation() { return cancellation; }
    public List<ModelMessage> history() { return history; }
    public List<ContextCheckpoint> checkpoints() { return checkpoints; }
    public Progress progress() { return progress; }
    public SteerInbox steers() { return steers; }
    public dev.openallay.skill.RetainedSkillContext retainedSkills() { return retainedSkills; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Lease)) return false;
        Lease that = (Lease) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(cancellation, that.cancellation) && java.util.Objects.equals(history, that.history) && java.util.Objects.equals(checkpoints, that.checkpoints) && java.util.Objects.equals(progress, that.progress) && java.util.Objects.equals(steers, that.steers) && java.util.Objects.equals(retainedSkills, that.retainedSkills);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(cancellation);
        hash = 31 * hash + java.util.Objects.hashCode(history);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoints);
        hash = 31 * hash + java.util.Objects.hashCode(progress);
        hash = 31 * hash + java.util.Objects.hashCode(steers);
        hash = 31 * hash + java.util.Objects.hashCode(retainedSkills);
        return hash;
    }
    @Override public String toString() { return "Lease[key=" + key + ", requestId=" + requestId + ", cancellation=" + cancellation + ", history=" + history + ", checkpoints=" + checkpoints + ", progress=" + progress + ", steers=" + steers + ", retainedSkills=" + retainedSkills + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Lease> schema() {
            return new dev.openallay.value.ValueSchema<>(Lease.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Lease>>asList(new dev.openallay.value.ValueSchema.Component<>(Lease.class, "key", Lease::key), new dev.openallay.value.ValueSchema.Component<>(Lease.class, "requestId", Lease::requestId), new dev.openallay.value.ValueSchema.Component<>(Lease.class, "cancellation", Lease::cancellation), new dev.openallay.value.ValueSchema.Component<>(Lease.class, "history", Lease::history), new dev.openallay.value.ValueSchema.Component<>(Lease.class, "checkpoints", Lease::checkpoints), new dev.openallay.value.ValueSchema.Component<>(Lease.class, "progress", Lease::progress), new dev.openallay.value.ValueSchema.Component<>(Lease.class, "steers", Lease::steers), new dev.openallay.value.ValueSchema.Component<>(Lease.class, "retainedSkills", Lease::retainedSkills)), arguments -> new Lease((AgentSessionKey) arguments[0], (UUID) arguments[1], (CancellationSignal) arguments[2], (List) arguments[3], (List) arguments[4], (Progress) arguments[5], (SteerInbox) arguments[6], (dev.openallay.skill.RetainedSkillContext) arguments[7]));
        }
    }
}

    @dev.openallay.value.ValueType(Steer.ValueSchemaProvider.class)
public static final class Steer {
    private final UUID messageId;
    private final ModelMessage message;
    public Steer(UUID messageId, ModelMessage message) {

            java.util.Objects.requireNonNull(messageId, "messageId");
            java.util.Objects.requireNonNull(message, "message");
            if (message.role() != dev.openallay.model.ModelRole.USER) {
                throw new IllegalArgumentException("steer must be a user message");
            }

        this.messageId = messageId;
        this.message = message;
    }
    public UUID messageId() { return messageId; }
    public ModelMessage message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Steer)) return false;
        Steer that = (Steer) other;
        return java.util.Objects.equals(messageId, that.messageId) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Steer[messageId=" + messageId + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Steer> schema() {
            return new dev.openallay.value.ValueSchema<>(Steer.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Steer>>asList(new dev.openallay.value.ValueSchema.Component<>(Steer.class, "messageId", Steer::messageId), new dev.openallay.value.ValueSchema.Component<>(Steer.class, "message", Steer::message)), arguments -> new Steer((UUID) arguments[0], (ModelMessage) arguments[1]));
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
                || lease.steers().sealed) return dev.openallay.util.Java8Collections.listOf();
        List<Steer> instructions = dev.openallay.util.Java8Collections.toList(lease.steers().pending.entrySet().stream()
                .map(entry -> new Steer(entry.getKey(), entry.getValue())));
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
            return dev.openallay.util.Java8Collections.listCopyOf(original.subList(requestFromIndex, original.size()));
        }
    }

    @dev.openallay.value.ValueType(Status.ValueSchemaProvider.class)
public static final class Status {
    private final boolean active;
    private final UUID requestId;
    private final int historyMessages;
    public Status(boolean active, UUID requestId, int historyMessages) {
        this.active = active;
        this.requestId = requestId;
        this.historyMessages = historyMessages;
    }
    public boolean active() { return active; }
    public UUID requestId() { return requestId; }
    public int historyMessages() { return historyMessages; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Status)) return false;
        Status that = (Status) other;
        return active == that.active && java.util.Objects.equals(requestId, that.requestId) && historyMessages == that.historyMessages;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(active);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Integer.hashCode(historyMessages);
        return hash;
    }
    @Override public String toString() { return "Status[active=" + active + ", requestId=" + requestId + ", historyMessages=" + historyMessages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Status> schema() {
            return new dev.openallay.value.ValueSchema<>(Status.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Status>>asList(new dev.openallay.value.ValueSchema.Component<>(Status.class, "active", Status::active), new dev.openallay.value.ValueSchema.Component<>(Status.class, "requestId", Status::requestId), new dev.openallay.value.ValueSchema.Component<>(Status.class, "historyMessages", Status::historyMessages)), arguments -> new Status((Boolean) arguments[0], (UUID) arguments[1], (Integer) arguments[2]));
        }
    }
}

    /** Exclusive idle control reservation. Preparing a summary never changes actual context. */
    @dev.openallay.value.ValueType(ControlLease.ValueSchemaProvider.class)
public static final class ControlLease {
    private final AgentSessionKey key;
    private final UUID controlId;
    private final CancellationSignal cancellation;
    private final List<ModelMessage> history;
    public ControlLease(AgentSessionKey key, UUID controlId, CancellationSignal cancellation, List<ModelMessage> history) {
 history = dev.openallay.util.Java8Collections.listCopyOf(history);
        this.key = key;
        this.controlId = controlId;
        this.cancellation = cancellation;
        this.history = history;
    }
    public AgentSessionKey key() { return key; }
    public UUID controlId() { return controlId; }
    public CancellationSignal cancellation() { return cancellation; }
    public List<ModelMessage> history() { return history; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ControlLease)) return false;
        ControlLease that = (ControlLease) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(controlId, that.controlId) && java.util.Objects.equals(cancellation, that.cancellation) && java.util.Objects.equals(history, that.history);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(controlId);
        hash = 31 * hash + java.util.Objects.hashCode(cancellation);
        hash = 31 * hash + java.util.Objects.hashCode(history);
        return hash;
    }
    @Override public String toString() { return "ControlLease[key=" + key + ", controlId=" + controlId + ", cancellation=" + cancellation + ", history=" + history + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ControlLease> schema() {
            return new dev.openallay.value.ValueSchema<>(ControlLease.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ControlLease>>asList(new dev.openallay.value.ValueSchema.Component<>(ControlLease.class, "key", ControlLease::key), new dev.openallay.value.ValueSchema.Component<>(ControlLease.class, "controlId", ControlLease::controlId), new dev.openallay.value.ValueSchema.Component<>(ControlLease.class, "cancellation", ControlLease::cancellation), new dev.openallay.value.ValueSchema.Component<>(ControlLease.class, "history", ControlLease::history)), arguments -> new ControlLease((AgentSessionKey) arguments[0], (UUID) arguments[1], (CancellationSignal) arguments[2], (List) arguments[3]));
        }
    }
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
        session.checkpoints = dev.openallay.util.Java8Collections.listOf();
        session.retainedSkills = new dev.openallay.skill.RetainedSkillContext();
        session.control = null;
        return true;
    }

    public synchronized void releaseControl(ControlLease lease) {
        Session session = sessions.get(lease.key());
        if (session != null && session.control == lease) session.control = null;
    }

    private static final class Session {
        private List<ModelMessage> history = dev.openallay.util.Java8Collections.listOf();
        private List<ContextCheckpoint> checkpoints = dev.openallay.util.Java8Collections.listOf();
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
        return reserve(key, requestId, history, dev.openallay.util.Java8Collections.listOf());
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
            session.history = dev.openallay.util.Java8Collections.listCopyOf(replacementHistory);
            session.checkpoints = dev.openallay.util.Java8Collections.listCopyOf(replacementCheckpoints);
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
        session.history = dev.openallay.util.Java8Collections.listCopyOf(history);
        session.active = null;
        pruneCheckpointIndex(session);
        return true;
    }

    /** A detached cancelled worker may finalize only while no successor lease was submitted. */
    public synchronized boolean finalizeCancelled(Lease lease, List<ModelMessage> history) {
        Session session = sessions.get(lease.key());
        if (session == null || session.latest != lease || session.active != null
                || session.control != null) return false;
        session.history = dev.openallay.util.Java8Collections.listCopyOf(history);
        pruneCheckpointIndex(session);
        return true;
    }

    /** Runtime reuse index only; durable ContextCompacted events keep every diagnostic record. */
    private static void pruneCheckpointIndex(Session session) {
        com.google.gson.Gson gson = dev.openallay.json.EngineJson.create();
        session.checkpoints = dev.openallay.util.Java8Collections.toList(session.checkpoints.stream().filter(checkpoint ->
                checkpoint.status() == ContextCheckpoint.Status.SUCCEEDED
                        && checkpoint.sourceToIndexExclusive() <= session.history.size()
                        && checkpoint.sourceHash().equals(dev.openallay.agent.context.ContextSourceHash.compute(
                                gson, session.history.subList(checkpoint.sourceFromIndex(),
                                        checkpoint.sourceToIndexExclusive())))));
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
        session.checkpoints = dev.openallay.util.Java8Collections.listCopyOf(updated);
        return true;
    }

    public synchronized List<ContextCheckpoint> checkpoints(AgentSessionKey key) {
        Session session = sessions.get(key);
        return session == null ? dev.openallay.util.Java8Collections.listOf() : session.checkpoints;
    }

    public synchronized void hydrate(AgentSessionKey key, List<ModelMessage> history) {
        hydrate(key, history, dev.openallay.util.Java8Collections.listOf());
    }

    public synchronized void hydrate(
            AgentSessionKey key,
            List<ModelMessage> history,
            List<ContextCheckpoint> checkpoints) {
        Session session = sessions.computeIfAbsent(key, ignored -> new Session());
        if (session.active != null || session.control != null) {
            throw new IllegalStateException("cannot hydrate an active Agent session or context control");
        }
        session.history = dev.openallay.util.Java8Collections.listCopyOf(history);
        session.checkpoints = dev.openallay.util.Java8Collections.listCopyOf(checkpoints);
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
                        dev.openallay.util.Java8Collections.listOf(new dev.openallay.model.ModelContent.Text(
                                "[OpenAllay request ended: agent_cancelled] Agent request was cancelled"))));
                session.history = dev.openallay.util.Java8Collections.listCopyOf(retained);
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
        List<AgentSessionKey> keys = dev.openallay.util.Java8Collections.toList(sessions.keySet().stream()
                .filter(key -> key.actorId().equals(actorId)));
        keys.forEach(this::clear);
    }

    public synchronized List<AgentSessionKey> sessions(UUID actorId) {
        return dev.openallay.util.Java8Collections.toList(sessions.keySet().stream()
                .filter(key -> key.actorId().equals(actorId))
                .sorted(java.util.Comparator.comparing(AgentSessionKey::sessionId)));
    }

    /** Immutable actor/session-scoped snapshot of the actual current context. */
    public synchronized List<ModelMessage> history(AgentSessionKey key) {
        Session session = sessions.get(key);
        if (session == null) return dev.openallay.util.Java8Collections.listOf();
        return dev.openallay.util.Java8Collections.listCopyOf(session.active == null
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
