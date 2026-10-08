package dev.openallay.bridge.protocol;

import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.json.EngineJson;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImageReference;
import dev.openallay.skill.SkillCatalogManifest;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/** Exact complete AgentRequest/session/serverpayload owners with real context/model/skill support. */
public final class AgentRequestJava8Fixture {
    private AgentRequestJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        boolean java8 = args.length == 1 && args[0].equals("java8");
        if (java8) equal("1.8", System.getProperty("java.specification.version"));
        UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID requestId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ToolInvocationContext context = ToolInvocationContext.developmentConsole("test-request");
        ModelMessage input = ModelMessage.userText("hello");
        AgentRequest request = new AgentRequest(requestId, actor, "session", input, "prompt", context, true, AgentRequest.unavailableImages());
        equal("hello", request.userMessage());
        equal(new AgentSessionKey(actor, "session"), request.sessionKey());
        equal(actor + ":session", request.sessionKey().schedulingKey());
        int hash = 0;
        for (Object value : new Object[] {request.requestId(), request.actorId(), request.sessionId(), request.userInput(),
                request.systemPrompt(), request.context(), request.stream(), request.images()}) hash = 31 * hash + java.util.Objects.hashCode(value);
        equal(hash, request.hashCode());
        AgentRequest copy = new AgentRequest(requestId, actor, "session", input, "prompt", context, true, request.images());
        equal(request, copy);
        check(!request.equals(new AgentRequest(requestId, UUID.fromString("00000000-0000-0000-0000-000000000003"), "session", input, "prompt", context, true, request.images())), "actor identity");
        byte[] bytes = new byte[] {1, 2, 3};
        ImageReference reference = new ImageReference(ResultChunker.sha256(bytes), "image/png", 1, 1, bytes.length);
        ModelMessage imageOnly = new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(new ModelContent.Image(reference)));
        equal("[Image]", AgentRequest.displayText(imageOnly));
        AgentRequest imageRequest = new AgentRequest(requestId, actor, "session", imageOnly, ref -> bytes, "prompt", context, false);
        check(Arrays.equals(bytes, imageRequest.images().read(reference)), "actual resolver retained");
        ServerAgentImageAttachment attachment = ServerAgentImageAttachment.from(reference, bytes);
        ServerAgentRequestPayload payload = new ServerAgentRequestPayload(requestId, "session", imageOnly, true,
                Collections.<ServerAgentHistoryMessage>emptyList(), Arrays.asList(attachment));
        equal("[Image]", payload.question());
        equal(imageOnly, payload.userInput().toModelMessage());
        ServerAgentRequestPayload tools = payload.withClientTools(Arrays.asList("test:tool"), SkillCatalogManifest.EMPTY);
        equal(Arrays.asList("test:tool"), tools.clientToolIds());
        equal(payload.imageAttachments(), tools.imageAttachments());
        com.google.gson.Gson gson = EngineJson.create();
        equal(tools, gson.fromJson(gson.toJson(tools), ServerAgentRequestPayload.class));
        System.out.println("payload=" + gson.toJson(tools));
        System.out.println("session=" + gson.toJson(request.sessionKey()));
        System.out.println("display=" + request.userMessage() + "/" + imageRequest.userMessage());
        failure("requestSession", () -> new AgentRequest(requestId, actor, "has space", input, "prompt", context, true, request.images()));
        failure("requestPrompt", () -> new AgentRequest(requestId, actor, "session", input, " ", context, true, request.images()));
        failure("requestAssistant", () -> new AgentRequest(requestId, actor, "session", new ModelMessage(ModelRole.ASSISTANT, Arrays.<ModelContent>asList(new ModelContent.Text("hello"))), "prompt", context, true, request.images()));
        failure("requestBlank", () -> new AgentRequest(requestId, actor, "session", ModelMessage.userText(" "), "prompt", context, true, request.images()));
        failure("sessionInvalid", () -> new AgentSessionKey(actor, "has space"));
        failure("payloadMissingImage", () -> new ServerAgentRequestPayload(requestId, "session", "[Image]", true,
                Collections.<ServerAgentHistoryMessage>emptyList(), Collections.<String>emptyList(), SkillCatalogManifest.EMPTY,
                ServerAgentHistoryMessage.from(imageOnly), Collections.<ServerAgentImageAttachment>emptyList()));
        failure("payloadDuplicateImage", () -> new ServerAgentRequestPayload(requestId, "session", imageOnly, true,
                Collections.<ServerAgentHistoryMessage>emptyList(), Arrays.asList(attachment, attachment)));
        failure("payloadDisplay", () -> new ServerAgentRequestPayload(requestId, "session", "wrong", true,
                Collections.<ServerAgentHistoryMessage>emptyList(), Collections.<String>emptyList(), SkillCatalogManifest.EMPTY,
                ServerAgentHistoryMessage.from(imageOnly), Arrays.asList(attachment)));
        failure("payloadDuplicateTools", () -> tools.withClientToolIds(Arrays.asList("test:tool", "test:tool")));
        failure("payloadInvalidTool", () -> tools.withClientToolIds(Arrays.asList("bad")));
        failure("payloadWrongHistoryRole", () -> new ServerAgentRequestPayload(requestId, "session", "text", true,
                Collections.<ServerAgentHistoryMessage>emptyList(), Collections.<String>emptyList(), SkillCatalogManifest.EMPTY,
                new ServerAgentHistoryMessage(ServerAgentHistoryMessage.Role.ASSISTANT, "text"), Collections.<ServerAgentImageAttachment>emptyList()));
        try { AgentRequest.unavailableImages().read(reference); throw new AssertionError("unavailable resolver authorized"); }
        catch (java.io.IOException expected) { System.out.println("resolver=" + expected.getMessage()); }
        System.out.println("PASS complete canonical AgentRequest session and server payload");
    }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | NullPointerException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
