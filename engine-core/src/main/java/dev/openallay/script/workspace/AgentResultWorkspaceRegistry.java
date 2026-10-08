package dev.openallay.script.workspace;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentResultWorkspaceRegistry {
    private final ConcurrentHashMap<String, AgentResultWorkspace> workspaces =
            new ConcurrentHashMap<>();

    public AgentResultWorkspace open(String correlationId) {
        return workspaces.computeIfAbsent(requireId(correlationId), ignored -> new AgentResultWorkspace());
    }

    /** Looks up an existing request only; model projection must never reopen a closed request. */
    public java.util.Optional<AgentResultWorkspace> existing(String correlationId) {
        return java.util.Optional.ofNullable(workspaces.get(requireId(correlationId)));
    }

    public void close(String correlationId) {
        AgentResultWorkspace workspace = workspaces.remove(requireId(correlationId));
        if (workspace != null) {
            workspace.close();
        }
    }

    public int activeCount() {
        return workspaces.size();
    }

    public void closeAll() {
        workspaces.values().forEach(AgentResultWorkspace::close);
        workspaces.clear();
    }

    private static String requireId(String value) {
        if (dev.openallay.util.Java8Strings.isBlank(Objects.requireNonNull(value, "correlationId"))) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        return value;
    }
}

