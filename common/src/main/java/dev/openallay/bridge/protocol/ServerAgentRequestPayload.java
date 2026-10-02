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
        dev.openallay.skill.SkillCatalogManifest skillDocuments,
        ServerAgentHistoryMessage userInput,
        List<ServerAgentImageAttachment> imageAttachments) {
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
        java.util.Objects.requireNonNull(userInput, "userInput");
        dev.openallay.agent.AgentRequest.validateUserInput(userInput.toModelMessage());
        if (!question.equals(dev.openallay.agent.AgentRequest.displayText(userInput.toModelMessage()))) {
            throw new IllegalArgumentException("Question display must match the typed user input");
        }
        imageAttachments = List.copyOf(imageAttachments);
        java.util.Map<String, dev.openallay.model.image.ImageReference> required = new java.util.LinkedHashMap<>();
        java.util.List<ServerAgentHistoryMessage> messages = new java.util.ArrayList<>(history);
        messages.add(userInput);
        for (ServerAgentHistoryMessage message : messages) {
            for (ServerAgentHistoryContent block : message.content()) {
                if (block.kind() == ServerAgentHistoryContent.Kind.IMAGE) {
                    var previous = required.putIfAbsent(block.image().sha256(), block.image());
                    if (previous != null && !previous.equals(block.image())) {
                        throw new IllegalArgumentException("Conflicting metadata for the same image hash");
                    }
                }
            }
        }
        java.util.Map<String, dev.openallay.model.image.ImageReference> supplied = new java.util.LinkedHashMap<>();
        for (ServerAgentImageAttachment attachment : imageAttachments) {
            if (supplied.putIfAbsent(attachment.reference().sha256(), attachment.reference()) != null) {
                throw new IllegalArgumentException("Duplicate image attachment");
            }
        }
        if (!supplied.equals(required)) {
            throw new IllegalArgumentException("Image attachments must exactly cover user input and history references");
        }
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
            UUID requestId, String sessionId, String question, boolean stream,
            List<ServerAgentHistoryMessage> history, List<String> clientToolIds,
            dev.openallay.skill.SkillCatalogManifest skillDocuments) {
        this(requestId, sessionId, question, stream, history, clientToolIds, skillDocuments,
                new ServerAgentHistoryMessage(ServerAgentHistoryMessage.Role.USER, question), List.of());
    }

    public ServerAgentRequestPayload(
            UUID requestId, String sessionId, dev.openallay.model.ModelMessage userInput, boolean stream,
            List<ServerAgentHistoryMessage> history, List<ServerAgentImageAttachment> imageAttachments) {
        this(requestId, sessionId, dev.openallay.agent.AgentRequest.displayText(userInput), stream,
                history, List.of(), dev.openallay.skill.SkillCatalogManifest.EMPTY,
                ServerAgentHistoryMessage.from(userInput), imageAttachments);
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
                requestId, sessionId, question, stream, history, replacement, documents,
                userInput, imageAttachments);
    }
}
