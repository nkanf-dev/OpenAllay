package dev.openallay.bridge.protocol;

import java.util.UUID;
import java.util.List;

@dev.openallay.value.ValueType(ServerAgentRequestPayload.ValueSchemaProvider.class)
public final class ServerAgentRequestPayload {
    private final UUID requestId;
    private final String sessionId;
    private final String question;
    private final boolean stream;
    private final List<ServerAgentHistoryMessage> history;
    private final List<String> clientToolIds;
    private final dev.openallay.skill.SkillCatalogManifest skillDocuments;
    private final ServerAgentHistoryMessage userInput;
    private final List<ServerAgentImageAttachment> imageAttachments;
    public ServerAgentRequestPayload(UUID requestId, String sessionId, String question, boolean stream, List<ServerAgentHistoryMessage> history, List<String> clientToolIds, dev.openallay.skill.SkillCatalogManifest skillDocuments, ServerAgentHistoryMessage userInput, List<ServerAgentImageAttachment> imageAttachments) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid Agent session ID");
        }
        if (question == null || dev.openallay.util.Java8Strings.isBlank(question)) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        history = dev.openallay.util.Java8Collections.listCopyOf(history);
        clientToolIds = dev.openallay.util.Java8Collections.listCopyOf(clientToolIds);
        java.util.Objects.requireNonNull(skillDocuments, "skillDocuments");
        java.util.Objects.requireNonNull(userInput, "userInput");
        dev.openallay.agent.AgentRequest.validateUserInput(userInput.toModelMessage());
        if (!question.equals(dev.openallay.agent.AgentRequest.displayText(userInput.toModelMessage()))) {
            throw new IllegalArgumentException("Question display must match the typed user input");
        }
        imageAttachments = dev.openallay.util.Java8Collections.listCopyOf(imageAttachments);
        java.util.Map<String, dev.openallay.model.image.ImageReference> required = new java.util.LinkedHashMap<>();
        java.util.List<ServerAgentHistoryMessage> messages = new java.util.ArrayList<>(history);
        messages.add(userInput);
        dev.openallay.model.image.ModelImages.uniqueReferences(dev.openallay.util.Java8Collections.toList(messages.stream()
                .map(ServerAgentHistoryMessage::toModelMessage)))
                .forEach(image -> required.put(image.sha256(), image));
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

        this.requestId = requestId;
        this.sessionId = sessionId;
        this.question = question;
        this.stream = stream;
        this.history = history;
        this.clientToolIds = clientToolIds;
        this.skillDocuments = skillDocuments;
        this.userInput = userInput;
        this.imageAttachments = imageAttachments;
    }
    public UUID requestId() { return requestId; }
    public String sessionId() { return sessionId; }
    public String question() { return question; }
    public boolean stream() { return stream; }
    public List<ServerAgentHistoryMessage> history() { return history; }
    public List<String> clientToolIds() { return clientToolIds; }
    public dev.openallay.skill.SkillCatalogManifest skillDocuments() { return skillDocuments; }
    public ServerAgentHistoryMessage userInput() { return userInput; }
    public List<ServerAgentImageAttachment> imageAttachments() { return imageAttachments; }
public ServerAgentRequestPayload(
            UUID requestId, String sessionId, String question, boolean stream,
            List<ServerAgentHistoryMessage> history, List<String> clientToolIds,
            dev.openallay.skill.SkillCatalogManifest skillDocuments) {
        this(requestId, sessionId, question, stream, history, clientToolIds, skillDocuments,
                new ServerAgentHistoryMessage(ServerAgentHistoryMessage.Role.USER, question), dev.openallay.util.Java8Collections.listOf());
    }
public ServerAgentRequestPayload(
            UUID requestId, String sessionId, dev.openallay.model.ModelMessage userInput, boolean stream,
            List<ServerAgentHistoryMessage> history, List<ServerAgentImageAttachment> imageAttachments) {
        this(requestId, sessionId, dev.openallay.agent.AgentRequest.displayText(userInput), stream,
                history, dev.openallay.util.Java8Collections.listOf(), dev.openallay.skill.SkillCatalogManifest.EMPTY,
                ServerAgentHistoryMessage.from(userInput), imageAttachments);
    }
public ServerAgentRequestPayload(
            UUID requestId, String sessionId, String question, boolean stream) {
        this(requestId, sessionId, question, stream, dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf(),
                dev.openallay.skill.SkillCatalogManifest.EMPTY);
    }
public ServerAgentRequestPayload(
            UUID requestId,
            String sessionId,
            String question,
            boolean stream,
            List<ServerAgentHistoryMessage> history) {
        this(requestId, sessionId, question, stream, history, dev.openallay.util.Java8Collections.listOf(),
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
public ServerAgentRequestPayload withClientTools(
            List<String> replacement, dev.openallay.skill.SkillCatalogManifest documents) {
        return new ServerAgentRequestPayload(
                requestId, sessionId, question, stream, history, replacement, documents,
                userInput, imageAttachments);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerAgentRequestPayload)) return false;
        ServerAgentRequestPayload that = (ServerAgentRequestPayload) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(question, that.question) && stream == that.stream && java.util.Objects.equals(history, that.history) && java.util.Objects.equals(clientToolIds, that.clientToolIds) && java.util.Objects.equals(skillDocuments, that.skillDocuments) && java.util.Objects.equals(userInput, that.userInput) && java.util.Objects.equals(imageAttachments, that.imageAttachments);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(question);
        hash = 31 * hash + Boolean.hashCode(stream);
        hash = 31 * hash + java.util.Objects.hashCode(history);
        hash = 31 * hash + java.util.Objects.hashCode(clientToolIds);
        hash = 31 * hash + java.util.Objects.hashCode(skillDocuments);
        hash = 31 * hash + java.util.Objects.hashCode(userInput);
        hash = 31 * hash + java.util.Objects.hashCode(imageAttachments);
        return hash;
    }
    @Override public String toString() { return "ServerAgentRequestPayload[requestId=" + requestId + ", sessionId=" + sessionId + ", question=" + question + ", stream=" + stream + ", history=" + history + ", clientToolIds=" + clientToolIds + ", skillDocuments=" + skillDocuments + ", userInput=" + userInput + ", imageAttachments=" + imageAttachments + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerAgentRequestPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerAgentRequestPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerAgentRequestPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "requestId", ServerAgentRequestPayload::requestId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "sessionId", ServerAgentRequestPayload::sessionId), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "question", ServerAgentRequestPayload::question), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "stream", ServerAgentRequestPayload::stream), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "history", ServerAgentRequestPayload::history), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "clientToolIds", ServerAgentRequestPayload::clientToolIds), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "skillDocuments", ServerAgentRequestPayload::skillDocuments), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "userInput", ServerAgentRequestPayload::userInput), new dev.openallay.value.ValueSchema.Component<>(ServerAgentRequestPayload.class, "imageAttachments", ServerAgentRequestPayload::imageAttachments)), arguments -> new ServerAgentRequestPayload((UUID) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (List) arguments[4], (List) arguments[5], (dev.openallay.skill.SkillCatalogManifest) arguments[6], (ServerAgentHistoryMessage) arguments[7], (List) arguments[8]));
        }
    }
}
