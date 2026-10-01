package dev.openallay.agent.trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveTraceStore {
    private final Map<UUID, LiveAgentTrace> traces = new ConcurrentHashMap<>();
    private final Path persistenceDirectory;
    private final dev.openallay.agent.KnownSecretRedactor redactor;
    private final java.util.function.BooleanSupplier persistenceEnabled;
    public dev.openallay.agent.KnownSecretRedactor redactor() { return redactor; }

    public LiveTraceStore(Path persistenceDirectory, Set<String> secrets) {
        this(persistenceDirectory, secrets, () -> true);
    }

    /** Persistence can follow the live Debug Mode setting without replacing the store. */
    public LiveTraceStore(
            Path persistenceDirectory,
            Set<String> secrets,
            java.util.function.BooleanSupplier persistenceEnabled) {
        this(persistenceDirectory, new dev.openallay.agent.KnownSecretRedactor(secrets), persistenceEnabled);
    }

    public LiveTraceStore(
            Path persistenceDirectory,
            dev.openallay.agent.KnownSecretRedactor redactor,
            java.util.function.BooleanSupplier persistenceEnabled) {
        this.persistenceDirectory = persistenceDirectory;
        this.redactor = java.util.Objects.requireNonNull(redactor, "redactor");
        this.persistenceEnabled = java.util.Objects.requireNonNull(
                persistenceEnabled, "persistenceEnabled");
    }

    public dev.openallay.agent.AgentEvent safeEvent(dev.openallay.agent.AgentEvent event) {
        return redactor.event(event);
    }

    public void record(LiveAgentTrace trace) {
        traces.put(trace.requestId(), trace);
        if (persistenceDirectory != null && persistenceEnabled.getAsBoolean()) {
            persist(trace);
        }
    }

    public Optional<LiveAgentTrace> find(UUID requestId) {
        return Optional.ofNullable(traces.get(requestId));
    }

    public List<UUID> ids() {
        return traces.values().stream()
                .sorted(java.util.Comparator.comparing(LiveAgentTrace::startedAt).reversed())
                .map(LiveAgentTrace::requestId)
                .toList();
    }

    public String encoded(UUID requestId) {
        LiveAgentTrace trace = find(requestId).orElseThrow(() ->
                new IllegalArgumentException("Unknown live trace " + requestId));
        return redactor.encodeTrace(trace);
    }

    private void persist(LiveAgentTrace trace) {
        try {
            Files.createDirectories(persistenceDirectory);
            Path target = persistenceDirectory.resolve(trace.requestId() + ".json");
            Path temporary = Files.createTempFile(persistenceDirectory, ".openallay-trace-", ".tmp");
            try {
                Files.writeString(temporary, redactor.encodeTrace(trace), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to persist live Agent trace", failure);
        }
    }
}
