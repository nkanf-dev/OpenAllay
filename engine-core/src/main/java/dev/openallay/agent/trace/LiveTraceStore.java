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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveTraceStore {
    private final Map<UUID, LiveAgentTrace> traces = new ConcurrentHashMap<>();
    private final Path persistenceDirectory;
    private final LiveTraceJson json = new LiveTraceJson();
    private final java.util.function.BooleanSupplier persistenceEnabled;

    public LiveTraceStore(Path persistenceDirectory) {
        this(persistenceDirectory, () -> true);
    }

    /** Persistence can follow the live Debug Mode setting without replacing the store. */
    public LiveTraceStore(
            Path persistenceDirectory,
            java.util.function.BooleanSupplier persistenceEnabled) {
        this.persistenceDirectory = persistenceDirectory;
        this.persistenceEnabled = java.util.Objects.requireNonNull(
                persistenceEnabled, "persistenceEnabled");
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
        return json.encode(trace);
    }

    private void persist(LiveAgentTrace trace) {
        try {
            Files.createDirectories(persistenceDirectory);
            Path target = persistenceDirectory.resolve(trace.requestId() + ".json");
            Path temporary = Files.createTempFile(persistenceDirectory, ".openallay-trace-", ".tmp");
            try {
                Files.writeString(temporary, json.encode(trace), StandardCharsets.UTF_8);
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
