package dev.openallay.bridge.protocol;

import com.google.gson.Gson;
import dev.openallay.json.EngineJson;
import dev.openallay.value.ValueSchemas;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Real canonical transport values/reassembler/EngineJson, original-modern vs genuineJava8. */
public final class BridgeTransportJava8Fixture {
    private BridgeTransportJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        boolean java8 = args.length == 1 && args[0].equals("java8");
        List<Class<?>> owners = Arrays.<Class<?>>asList(RemoteToolCallPayload.class, ClientToolCallPayload.class,
                RemoteCancelPayload.class, ClientToolCancelPayload.class, ServerAgentCancelPayload.class,
                RemoteToolRequestClosePayload.class, RemoteToolResultChunkPayload.class, ClientToolResultChunkPayload.class,
                ServerAgentEventChunkPayload.class, ServerAgentRequestChunkPayload.class, ServerAgentSteerChunkPayload.class,
                ServerAgentEventPayload.class);
        if (java8) {
            equal("1.8", System.getProperty("java.specification.version"));
            for (Class<?> owner : owners) classMajor(owner);
            classMajor(ResultChunker.class); classMajor(EngineJson.class);
        }
        UUID request = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID invocation = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000003");
        String content = "first\nUnicode Ω 中\nlast";
        List<RemoteToolResultChunkPayload> chunks = new ResultChunker().split(invocation, content, 7);
        Gson gson = EngineJson.create();
        RemoteToolCallPayload remote = new RemoteToolCallPayload(invocation, "session", "test:tool", "{\"x\":1}");
        ClientToolCallPayload client = new ClientToolCallPayload(request, invocation, "session", "test:tool", "{}");
        RemoteToolResultChunkPayload first = chunks.get(0);
        ClientToolResultChunkPayload reverse = ClientToolResultChunkPayload.from(request, first);
        ServerAgentEventChunkPayload eventChunk = ServerAgentEventChunkPayload.from(request, first);
        ServerAgentRequestChunkPayload requestChunk = new ServerAgentRequestChunkPayload(request, first.index(), first.total(), first.contentHash(), first.base64Data());
        ServerAgentSteerChunkPayload steerChunk = new ServerAgentSteerChunkPayload(request, invocation, first.index(), first.total(), first.contentHash(), first.base64Data());
        equal(first, reverse.asRemoteChunk()); equal(first, eventChunk.asRemoteChunk());
        for (Object value : Arrays.asList(remote, client, new RemoteCancelPayload(invocation), new ClientToolCancelPayload(request, invocation),
                new ServerAgentCancelPayload(request), new RemoteToolRequestClosePayload("request"), first, reverse, eventChunk,
                requestChunk, steerChunk, new ServerAgentEventPayload(request, "done", "{}", true))) {
            equal(value, gson.fromJson(gson.toJson(value), value.getClass()));
            System.out.println(value.getClass().getSimpleName() + "=" + gson.toJson(value));
        }
        int hash = 0;
        for (Object value : new Object[] {client.requestId(), client.invocationId(), client.sessionId(), client.toolId(), client.argumentsJson()}) hash = 31 * hash + java.util.Objects.hashCode(value);
        equal(hash, client.hashCode());
        check(!client.equals(new ClientToolCallPayload(other, invocation, "session", "test:tool", "{}")), "request identity");
        check(!client.equals(new ClientToolCallPayload(request, other, "session", "test:tool", "{}")), "invocation identity");
        ResultChunker.Reassembler assembled = new ResultChunker.Reassembler(Duration.ofSeconds(2));
        equal(java.util.Optional.empty(), assembled.accept(first));
        equal(java.util.Optional.empty(), assembled.accept(first));
        java.util.Optional<String> complete = java.util.Optional.empty();
        for (int index = chunks.size() - 1; index >= 1; index--) complete = assembled.accept(chunks.get(index));
        equal(content, complete.get()); equal(0, assembled.activeAssemblies());
        equal(content, reassemble(new ResultChunker().split(invocation, content, 1)));
        equal("", reassemble(new ResultChunker().split(invocation, "", 7)));
        try { chunks.clear(); throw new AssertionError("mutable split snapshot"); } catch (UnsupportedOperationException expected) {}
        ResultChunker.Reassembler duplicate = new ResultChunker.Reassembler(Duration.ofSeconds(2));
        duplicate.accept(first);
        failure("duplicateChanged", () -> duplicate.accept(new RemoteToolResultChunkPayload(invocation, first.index(), first.total(), first.contentHash(), Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)))));
        equal(0, duplicate.activeAssemblies());
        ResultChunker.Reassembler metadata = new ResultChunker.Reassembler(Duration.ofSeconds(2)); metadata.accept(first);
        failure("metadataChanged", () -> metadata.accept(new RemoteToolResultChunkPayload(invocation, 1, first.total() + 1, first.contentHash(), first.base64Data())));
        equal(0, metadata.activeAssemblies());
        ResultChunker.Reassembler badHash = new ResultChunker.Reassembler(Duration.ofSeconds(2));
        failure("hashMismatch", () -> badHash.accept(new RemoteToolResultChunkPayload(invocation, 0, 1, zeros(), Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)))));
        equal(0, badHash.activeAssemblies());
        ResultChunker.Reassembler limited = new ResultChunker.Reassembler(Duration.ofSeconds(2), 2, 2);
        failure("sizeLimit", () -> limited.accept(first)); equal(0, limited.activeAssemblies());
        ResultChunker.Reassembler cancel = new ResultChunker.Reassembler(Duration.ofSeconds(2)); cancel.accept(first); cancel.cancel(invocation); equal(0, cancel.activeAssemblies());
        cancel.accept(first); cancel.clear(); equal(0, cancel.activeAssemblies());
        ResultChunker.Reassembler timeout = new ResultChunker.Reassembler(Duration.ofMillis(20)); timeout.accept(first);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (timeout.activeAssemblies() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
        equal(0, timeout.activeAssemblies());
        failure("splitCount", () -> new ResultChunker().split(invocation, content, 0));
        failure("callBlank", () -> new RemoteToolCallPayload(invocation, " ", "test:tool", "{}"));
        failure("callBadSession", () -> new ClientToolCallPayload(request, invocation, "has space", "test:tool", "{}"));
        failure("callBadTool", () -> new ClientToolCallPayload(request, invocation, "session", "bad", "{}"));
        failure("cancelNull", () -> new ClientToolCancelPayload(null, invocation));
        failure("closeBlank", () -> new RemoteToolRequestClosePayload(" "));
        failure("chunkHash", () -> new RemoteToolResultChunkPayload(invocation, 0, 1, "bad", "eA=="));
        failure("chunkBase64", () -> new RemoteToolResultChunkPayload(invocation, 0, 1, zeros(), "?"));
        failure("requestCanonicalPadding", () -> new ServerAgentRequestChunkPayload(request, 0, 1, zeros(), "eB=="));
        failure("requestPosition", () -> new ServerAgentRequestChunkPayload(request, 1, 1, zeros(), "eA=="));
        failure("requestCountLimit", () -> new ServerAgentRequestChunkPayload(request, 0, BridgeProtocol.MAX_REQUEST_CHUNKS + 1, zeros(), "eA=="));
        failure("eventBlank", () -> new ServerAgentEventPayload(request, " ", "{}", false));
        if (java8) {
            equal(client, ValueSchemas.of(ClientToolCallPayload.class).construct(new Object[] {request, invocation, "session", "test:tool", "{}"}));
            check(owners.stream().allMatch(ValueSchemas::supports), "explicit schemas");
        }
        System.out.println("sha=" + first.contentHash());
        System.out.println("reassembly=" + content.replace("\n", "\\n"));
        System.out.println("PASS canonical bridge transport identity and reassembly");
    }
    private static String reassemble(List<RemoteToolResultChunkPayload> chunks) {
        ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler(Duration.ofSeconds(2));
        java.util.Optional<String> value = java.util.Optional.empty();
        for (RemoteToolResultChunkPayload chunk : chunks) value = reassembler.accept(chunk);
        return value.get();
    }
    private static String zeros() { return "0000000000000000000000000000000000000000000000000000000000000000"; }
    private static void failure(String name, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException | NullPointerException expected) { System.out.println(name + "=" + expected.getClass().getSimpleName() + ":" + expected.getMessage()); }
    }
    private static void classMajor(Class<?> owner) throws Exception {
        try (InputStream input = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            byte[] header = new byte[8]; int position = 0;
            while (position < 8) { int count = input.read(header, position, 8 - position); check(count > 0, "header"); position += count; }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
