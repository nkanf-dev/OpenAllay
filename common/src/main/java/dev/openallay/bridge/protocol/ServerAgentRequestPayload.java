package dev.openallay.bridge.protocol;

import java.util.UUID;
import java.util.List;

public record ServerAgentRequestPayload(
        UUID requestId,
        String sessionId,
        String question,
        boolean stream,
        List<ServerAgentHistoryMessage> history,
        List<String> clientToolIds,
        dev.openallay.skill.SkillCatalogManifest skillDocuments) {
    public ServerAgentRequestPayload {
        java.util.Objects.requireNonNull(requestId, "requestId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid Agent session ID");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        history = List.copyOf(history);
        clientToolIds = List.copyOf(clientToolIds);
        java.util.Objects.requireNonNull(skillDocuments, "skillDocuments");
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        for (String toolId : clientToolIds) {
            if (toolId == null
                    || !toolId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || !unique.add(toolId)) {
                throw new IllegalArgumentException("Invalid or duplicate client Tool ID");
            }
        }
    }

    public ServerAgentRequestPayload(
            UUID requestId, String sessionId, String question, boolean stream) {
        this(requestId, sessionId, question, stream, List.of(), List.of(),
                dev.openallay.skill.SkillCatalogManifest.EMPTY);
    }

    public ServerAgentRequestPayload(
            UUID requestId,
            String sessionId,
            String question,
            boolean stream,
            List<ServerAgentHistoryMessage> history) {
        this(requestId, sessionId, question, stream, history, List.of(),
                dev.openallay.skill.SkillCatalogManifest.EMPTY);
    }

    public ServerAgentRequestPayload(
            UUID requestId,
            String sessionId,
            String question,
            boolean stream,
            List<ServerAgentHistoryMessage> history,
            List<String> clientToolIds) {
        this(requestId, sessionId, question, stream, history, clientToolIds,
                dev.openallay.skill.SkillCatalogManifest.EMPTY);
    }

    public ServerAgentRequestPayload withClientToolIds(List<String> replacement) {
        return withClientTools(replacement, skillDocuments);
    }

    /** Binds metadata to the exact frozen capability catalog for this request. */
    public ServerAgentRequestPayload withClientTools(
            List<String> replacement, dev.openallay.skill.SkillCatalogManifest documents) {
        return new ServerAgentRequestPayload(
                requestId, sessionId, question, stream, history, replacement, documents);
    }
}
