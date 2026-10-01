package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BridgeJsonCodecTest {
    @Test
    void everyPayloadUsesOnlyItsCurrentFields() {
        BridgeJsonCodec codec = new BridgeJsonCodec();
        UUID requestId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        RemoteToolResultChunkPayload raw = new ResultChunker()
                .split(invocationId, "{\"status\":\"success\"}", 5).getFirst();

        assertExactPayloadShape(codec, new CapabilityPayload(List.of(), false, 0, 0, 0, ""),
                Set.of("remoteTools", "serverModel", "serverContextWindowTokens",
                        "serverMaxOutputTokens", "serverPromptAndToolTokens", "serverCanonicalModelId"));
        assertExactPayloadShape(codec,
                new RemoteToolCallPayload(invocationId, "main", "test:read", "{}"),
                Set.of("correlationId", "sessionId", "toolId", "argumentsJson"));
        assertExactPayloadShape(codec, raw,
                Set.of("correlationId", "index", "total", "contentHash", "base64Data"));
        assertExactPayloadShape(codec, new RemoteCancelPayload(invocationId),
                Set.of("correlationId"));
        assertExactPayloadShape(codec, new RemoteToolRequestClosePayload("main"),
                Set.of("requestId"));
        assertExactPayloadShape(codec,
                new ServerAgentRequestPayload(requestId, "main", "question", true),
                Set.of("requestId", "sessionId", "question", "stream", "history", "clientToolIds"));
        assertExactPayloadShape(codec,
                new ClientToolCallPayload(requestId, invocationId, "main", "test:read", "{}"),
                Set.of("requestId", "invocationId", "sessionId", "toolId", "argumentsJson"));
        assertExactPayloadShape(codec, ClientToolResultChunkPayload.from(requestId, raw),
                Set.of("requestId", "invocationId", "index", "total", "contentHash", "base64Data"));
        assertExactPayloadShape(codec, new ClientToolCancelPayload(requestId, invocationId),
                Set.of("requestId", "invocationId"));
        assertExactPayloadShape(codec,
                new ServerAgentRequestChunker().split(requestId, "question", 5).getFirst(),
                Set.of("requestId", "index", "total", "contentHash", "base64Data"));
        assertExactPayloadShape(codec, new ServerAgentCancelPayload(requestId), Set.of("requestId"));
        assertExactPayloadShape(codec,
                new ServerAgentEventPayload(requestId, "final_text", "{\"text\":\"done\"}", true),
                Set.of("requestId", "eventType", "eventJson", "terminal"));
        assertExactPayloadShape(codec, ServerAgentEventChunkPayload.from(requestId, raw),
                Set.of("requestId", "eventId", "index", "total", "contentHash", "base64Data"));
    }

    @Test
    void roundTripsUnicodeAndRejectsMalformedPayloadShapes() {
        BridgeJsonCodec codec = new BridgeJsonCodec();
        ServerAgentRequestPayload payload = new ServerAgentRequestPayload(
                UUID.randomUUID(),
                "machine",
                "压印机怎么搭？",
                true,
                List.of(
                        new ServerAgentHistoryMessage(
                                ServerAgentHistoryMessage.Role.USER, "先前问题"),
                        new ServerAgentHistoryMessage(
                                ServerAgentHistoryMessage.Role.ASSISTANT, "先前回答")));
        assertEquals(payload, codec.decode(codec.encode(payload), ServerAgentRequestPayload.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                codec.encode(payload).replaceFirst("\\{", "{\"extra\":1,"),
                ServerAgentRequestPayload.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                codec.encode(payload).replace(
                        "\"text\":\"先前问题\"",
                        "\"text\":\"先前问题\",\"extra\":true"),
                ServerAgentRequestPayload.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                codec.encode(payload).replace("\"clientToolIds\":[]", "\"clientToolIds\":null"),
                ServerAgentRequestPayload.class));
    }

    @Test
    void roundTripsStrictRequestScopedClientToolPayloads() {
        BridgeJsonCodec codec = new BridgeJsonCodec();
        UUID requestId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        ServerAgentRequestPayload request = new ServerAgentRequestPayload(
                requestId,
                "main",
                "读取视频设置",
                true,
                List.of(),
                List.of("openallay:inspect_game_state"));
        assertEquals(
                request,
                codec.decode(codec.encode(request), ServerAgentRequestPayload.class));

        ClientToolCallPayload call = new ClientToolCallPayload(
                requestId,
                invocationId,
                "main",
                "openallay:inspect_game_state",
                "{\"section\":\"OPTIONS\"}");
        assertEquals(call, codec.decode(codec.encode(call), ClientToolCallPayload.class));
        ClientToolCancelPayload cancel = new ClientToolCancelPayload(
                requestId, invocationId);
        assertEquals(cancel, codec.decode(codec.encode(cancel), ClientToolCancelPayload.class));

        ClientToolResultChunkPayload result = ClientToolResultChunkPayload.from(
                requestId,
                new ResultChunker().split(invocationId, "{\"status\":\"failure\"}", 5)
                        .getFirst());
        assertEquals(
                result,
                codec.decode(codec.encode(result), ClientToolResultChunkPayload.class));
        ServerAgentEventChunkPayload eventChunk = ServerAgentEventChunkPayload.from(
                requestId,
                new ResultChunker().split(invocationId, codec.encode(request), 5).getFirst());
        assertEquals(
                eventChunk,
                codec.decode(codec.encode(eventChunk), ServerAgentEventChunkPayload.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                codec.encode(call).replaceFirst("\\{", "{\"extra\":1,"),
                ClientToolCallPayload.class));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestPayload(
                requestId,
                "main",
                "question",
                true,
                List.of(),
                List.of("openallay:inspect_game_state", "openallay:inspect_game_state")));
        assertTrue(request.clientToolIds().contains("openallay:inspect_game_state"));
    }

    @Test
    void reassemblesOutOfOrderChunksWithoutLogicalTruncation() {
        UUID id = UUID.randomUUID();
        String content = "第一层：齿轮箱\n第二层：动力输入 ⚙".repeat(20);
        List<RemoteToolResultChunkPayload> chunks = new ResultChunker().split(id, content, 7);
        ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
        java.util.Optional<String> result = java.util.Optional.empty();
        for (RemoteToolResultChunkPayload chunk : chunks.reversed()) {
            java.util.Optional<String> accepted = reassembler.accept(chunk);
            if (accepted.isPresent()) {
                result = accepted;
            }
        }
        assertEquals(content, result.orElseThrow());
    }

    @Test
    void duplicateAndMissingChunksDoNotCompleteEarly() {
        List<RemoteToolResultChunkPayload> chunks =
                new ResultChunker().split(UUID.randomUUID(), "abcdef", 2);
        ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
        assertFalse(reassembler.accept(chunks.getFirst()).isPresent());
        assertFalse(reassembler.accept(chunks.getFirst()).isPresent());
        assertFalse(reassembler.accept(chunks.get(1)).isPresent());
        assertEquals("abcdef", reassembler.accept(chunks.getLast()).orElseThrow());
    }

    @Test
    void untrustedHugeChunkCountDoesNotDriveProportionalAllocation() {
        byte[] data = "x".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        RemoteToolResultChunkPayload chunk = new RemoteToolResultChunkPayload(
                UUID.randomUUID(),
                0,
                Integer.MAX_VALUE,
                ResultChunker.sha256(data),
                java.util.Base64.getEncoder().encodeToString(data));

        ResultChunker.Reassembler reassembler = new ResultChunker.Reassembler();
        assertFalse(reassembler.accept(chunk).isPresent());
        reassembler.cancel(chunk.correlationId());
    }

    @Test
    void incompleteResultAssembliesExpire() throws Exception {
        List<RemoteToolResultChunkPayload> chunks =
                new ResultChunker().split(UUID.randomUUID(), "abcdef", 2);
        ResultChunker.Reassembler reassembler =
                new ResultChunker.Reassembler(Duration.ofMillis(20));

        assertFalse(reassembler.accept(chunks.getFirst()).isPresent());
        assertEquals(1, reassembler.activeAssemblies());
        awaitNoAssemblies(reassembler::activeAssemblies);
    }

    @Test
    void serverAgentRequestsChunkUnicodeAndRemainIsolatedByActor() {
        UUID requestId = UUID.randomUUID();
        UUID firstActor = UUID.randomUUID();
        UUID secondActor = UUID.randomUUID();
        String content = "跨供应商模型上下文 ⚙".repeat(40);
        ServerAgentRequestChunker chunker = new ServerAgentRequestChunker();
        List<ServerAgentRequestChunkPayload> chunks = chunker.split(requestId, content, 11);
        ServerAgentRequestChunker.Reassembler reassembler =
                new ServerAgentRequestChunker.Reassembler();
        BridgeJsonCodec codec = new BridgeJsonCodec();

        assertEquals(
                chunks.getFirst(),
                codec.decode(
                        codec.encode(chunks.getFirst()),
                        ServerAgentRequestChunkPayload.class));
        assertFalse(reassembler.accept(secondActor, chunks.getFirst()).isPresent());
        reassembler.clearActor(secondActor);
        java.util.Optional<String> restored = java.util.Optional.empty();
        for (ServerAgentRequestChunkPayload chunk : chunks.reversed()) {
            java.util.Optional<String> accepted = reassembler.accept(firstActor, chunk);
            if (accepted.isPresent()) {
                restored = accepted;
            }
        }
        assertEquals(content, restored.orElseThrow());
    }

    @Test
    void incompleteServerRequestAssembliesExpire() throws Exception {
        UUID actorId = UUID.randomUUID();
        List<ServerAgentRequestChunkPayload> chunks =
                new ServerAgentRequestChunker().split(UUID.randomUUID(), "abcdef", 2);
        ServerAgentRequestChunker.Reassembler reassembler =
                new ServerAgentRequestChunker.Reassembler(Duration.ofMillis(20));

        assertFalse(reassembler.accept(actorId, chunks.getFirst()).isPresent());
        assertEquals(1, reassembler.activeAssemblies());
        awaitNoAssemblies(reassembler::activeAssemblies);
    }

    @Test
    void productionChunksLeaveRoomBelowMinecraftStringLimitAfterBase64AndJson() {
        BridgeJsonCodec codec = new BridgeJsonCodec();
        UUID requestId = UUID.randomUUID();
        UUID invocationId = UUID.randomUUID();
        String content = "x".repeat(BridgeProtocol.TRANSPORT_CHUNK_BYTES * 3);

        for (RemoteToolResultChunkPayload raw : new ResultChunker().split(
                invocationId, content, BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
            ClientToolResultChunkPayload chunk =
                    ClientToolResultChunkPayload.from(requestId, raw);
            assertTrue(codec.encode(chunk).length() < 32_767);
        }
        for (ServerAgentRequestChunkPayload chunk : new ServerAgentRequestChunker().split(
                requestId, content, BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
            assertTrue(codec.encode(chunk).length() < 32_767);
        }
        for (RemoteToolResultChunkPayload raw : new ResultChunker().split(
                invocationId, content, BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
            ServerAgentEventChunkPayload chunk =
                    ServerAgentEventChunkPayload.from(requestId, raw);
            assertTrue(codec.encode(chunk).length() < 32_767);
        }
    }

    private static void assertExactPayloadShape(
            BridgeJsonCodec codec, Object payload, Set<String> fields) {
        String json = codec.encode(payload);
        JsonObject body = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(fields, body.keySet());
        assertEquals(payload, codec.decode(json, payload.getClass()));

        JsonObject missing = body.deepCopy();
        missing.remove(fields.iterator().next());
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(missing.toString(), payload.getClass()));
        JsonObject extra = body.deepCopy();
        extra.addProperty("unexpected", true);
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(extra.toString(), payload.getClass()));
    }

    private static void awaitNoAssemblies(java.util.function.IntSupplier active) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (active.getAsInt() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(0, active.getAsInt());
    }
}
