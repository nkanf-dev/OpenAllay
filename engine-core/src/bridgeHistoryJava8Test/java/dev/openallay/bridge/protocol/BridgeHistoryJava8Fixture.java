package dev.openallay.bridge.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.json.EngineJson;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImageReference;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Actual canonical image/history envelopes and two actor-bound chunkers. */
public final class BridgeHistoryJava8Fixture {
    private BridgeHistoryJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        boolean java8 = args.length == 1 && args[0].equals("java8");
        if (java8) equal("1.8", System.getProperty("java.specification.version"));
        UUID actor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID message = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000004");
        byte[] bytes = new byte[] {1, 2, 3};
        ImageReference image = new ImageReference(ResultChunker.sha256(bytes), "image/png", 1, 1, bytes.length);
        ServerAgentImageAttachment attachment = ServerAgentImageAttachment.from(image, bytes);
        check(Arrays.equals(bytes, attachment.bytes()), "image bytes/hash");
        Gson gson = EngineJson.create();
        ModelMessage user = new ModelMessage(ModelRole.USER, Arrays.<ModelContent>asList(new ModelContent.Text("look"), new ModelContent.Image(image)));
        ServerAgentHistoryMessage history = ServerAgentHistoryMessage.from(user);
        equal(user, history.toModelMessage());
        ServerAgentHistoryContent imageHistory = ServerAgentHistoryContent.from(new ModelContent.Image(image, "tool-origin"));
        equal("tool-origin", ((ModelContent.Image) imageHistory.toModelContent()).originToolUseId());
        ServerAgentHistoryContent tool = ServerAgentHistoryContent.from(new ModelContent.ToolUse("call", "test:tool", object("x", 1)));
        ServerAgentHistoryContent resultHistory = ServerAgentHistoryContent.from(new ModelContent.ToolResult("call", object("status", "success"), false, Arrays.asList(image)));
        JsonObject result = object("status", "success");
        ToolExecutionMessage execution = new ToolExecutionMessage(result, Arrays.asList(attachment));
        result.addProperty("mutated", true); check(!execution.result().has("mutated"), "tool result constructor copy");
        execution.result().addProperty("later", true); check(!execution.result().has("later"), "tool result accessor copy");
        execution.requireImages(Arrays.asList(image));
        ServerAgentSteerPayload put = new ServerAgentSteerPayload(request, message, ServerAgentSteerPayload.Operation.PUT, history, Arrays.asList(attachment));
        ServerAgentSteerPayload remove = new ServerAgentSteerPayload(request, message, ServerAgentSteerPayload.Operation.REMOVE, null);
        for (Object value : Arrays.asList(attachment, imageHistory, tool, resultHistory, history, execution, put, remove)) {
            equal(value, gson.fromJson(gson.toJson(value), value.getClass()));
            System.out.println(value.getClass().getSimpleName() + "=" + gson.toJson(value));
        }
        failure("reasoning", () -> ServerAgentHistoryContent.from(new ModelContent.Reasoning("secret", null)));
        failure("assistantImage", () -> new ServerAgentHistoryMessage(ServerAgentHistoryMessage.Role.ASSISTANT, Arrays.asList(imageHistory)));
        failure("imageOriginKind", () -> new ServerAgentHistoryContent(ServerAgentHistoryContent.Kind.TEXT, "text", null, null, null, null, null, null, "origin"));
        failure("imagePadding", () -> new ServerAgentImageAttachment(new ImageReference(image.sha256(), "image/png", 1, 1, 1), "AB=="));
        failure("imageHash", () -> new ServerAgentImageAttachment(new ImageReference(zeros(), "image/png", 1, 1, 3), attachment.base64Data()).bytes());
        failure("duplicateImages", () -> new ToolExecutionMessage(object("status", "success"), Arrays.asList(attachment, attachment)));
        failure("failedImages", () -> new ToolExecutionMessage(object("status", "failure"), Arrays.asList(attachment)));
        failure("missingImages", () -> execution.requireImages(Collections.<ImageReference>emptyList()));
        failure("steerMissingImages", () -> new ServerAgentSteerPayload(request, message, ServerAgentSteerPayload.Operation.PUT, history));
        failure("removeMessage", () -> new ServerAgentSteerPayload(request, message, ServerAgentSteerPayload.Operation.REMOVE, history));
        String encoded = "text Ω\n";
        List<ServerAgentRequestChunkPayload> requestChunks = new ServerAgentRequestChunker().split(request, encoded, BridgeProtocol.TRANSPORT_CHUNK_BYTES);
        ServerAgentRequestChunker.Reassembler requests = new ServerAgentRequestChunker.Reassembler(Duration.ofSeconds(2));
        equal(encoded, requests.accept(actor, requestChunks.get(0)).get());
        List<ServerAgentSteerChunkPayload> chunks = new ServerAgentSteerChunker().split(request, message, encoded, BridgeProtocol.TRANSPORT_CHUNK_BYTES);
        ServerAgentSteerChunker.Reassembler steers = new ServerAgentSteerChunker.Reassembler(Duration.ofSeconds(2));
        equal(encoded, steers.accept(actor, chunks.get(0)).get());
        String large = repeat("a", BridgeProtocol.TRANSPORT_CHUNK_BYTES + 1);
        List<ServerAgentSteerChunkPayload> partial = new ServerAgentSteerChunker().split(request, message, large, BridgeProtocol.TRANSPORT_CHUNK_BYTES);
        equal(java.util.Optional.empty(), steers.accept(actor, partial.get(0)));
        equal(java.util.Optional.empty(), steers.accept(actor, partial.get(0)));
        check(!steers.cancel(other, request, message), "other actor cannot cancel");
        check(!steers.cancel(actor, other, message), "other request cannot cancel");
        failure("actorBusy", () -> steers.accept(actor, new ServerAgentSteerChunkPayload(other, other, 0, partial.size(), partial.get(0).contentHash(), partial.get(0).base64Data())));
        check(steers.cancel(actor, request, message), "exact actor/request/message cancel");
        steers.accept(actor, partial.get(0)); steers.clearRequest(other, request); equal(1, steers.activeAssemblies());
        steers.clearRequest(actor, request); equal(0, steers.activeAssemblies());
        steers.accept(actor, partial.get(0)); steers.clearActor(actor); equal(0, steers.activeAssemblies());
        ServerAgentSteerChunker.Reassembler timed = new ServerAgentSteerChunker.Reassembler(Duration.ofMillis(20)); timed.accept(actor, partial.get(0));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (timed.activeAssemblies() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
        equal(0, timed.activeAssemblies());
        failure("requestByteLimit", () -> new ServerAgentRequestChunker().split(request, "Ω", 1, 1));
        failure("steerTransportLimit", () -> new ServerAgentSteerChunker().split(request, message, encoded, BridgeProtocol.TRANSPORT_CHUNK_BYTES + 1));
        System.out.println("imageHash=" + image.sha256());
        System.out.println("steerReassembled=" + encoded.replace("\n", "\\n"));
        System.out.println("PASS canonical bridge history images and actor-bound chunkers");
    }
    private static JsonObject object(String key, String value) { JsonObject result = new JsonObject(); result.addProperty(key, value); return result; }
    private static JsonObject object(String key, int value) { JsonObject result = new JsonObject(); result.addProperty(key, value); return result; }
    private static String repeat(String value, int count) { StringBuilder out = new StringBuilder(); for (int i = 0; i < count; i++) out.append(value); return out.toString(); }
    private static String zeros() { return "0000000000000000000000000000000000000000000000000000000000000000"; }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | IllegalStateException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
