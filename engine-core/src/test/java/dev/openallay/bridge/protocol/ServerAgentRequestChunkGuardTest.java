package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class ServerAgentRequestChunkGuardTest {
    private static final String HASH = "a".repeat(64);

    @Test void payloadRejectsHugeChunkCountsAndDataBeforeReassemblyAllocation() {
        chunkCanonicalBase64AndActorScopedCancellationRejectMalformedBoundaries();
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestChunkPayload(
                UUID.randomUUID(), 0, Integer.MAX_VALUE, HASH, "eA=="));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestChunkPayload(
                UUID.randomUUID(), 0, BridgeProtocol.MAX_REQUEST_CHUNKS + 1, HASH, "eA=="));
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestChunkPayload(
                UUID.randomUUID(), 0, 1, HASH, "A".repeat(BridgeProtocol.MAX_REQUEST_CHUNK_BASE64_CHARS + 1)));
        byte[] tooMany = new byte[BridgeProtocol.TRANSPORT_CHUNK_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestChunkPayload(
                UUID.randomUUID(), 0, 1, HASH, Base64.getEncoder().encodeToString(tooMany)));
        var receiver = new ServerAgentRequestChunker.Reassembler();
        assertEquals(0, receiver.activeAssemblies());
    }

    @Test void typedJsonRejectsFractionOverflowStringsDuplicatesAndOversizedEnvelope() {
        var codec = new BridgeJsonCodec();
        var chunk = new ServerAgentRequestChunker().split(UUID.randomUUID(), "ab", 1).getFirst();
        String json = codec.encode(chunk);
        for (String value : List.of("0.9", "0.0", "1e0", "2147483648", "\"0\"", "true")) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(
                    json.replace("\"index\":0", "\"index\":" + value), ServerAgentRequestChunkPayload.class));
        }
        for (String value : List.of("2.9", "2.0", "2e0", "2147483648", "\"2\"", "true")) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(
                    json.replace("\"total\":2", "\"total\":" + value), ServerAgentRequestChunkPayload.class));
        }
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                json.replace("\"index\":0", "\"index\":0,\"index\":0"), ServerAgentRequestChunkPayload.class));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                json.replace("\"index\":0", "\"index\":0,\"\\u0069ndex\":0"), ServerAgentRequestChunkPayload.class));
        String over = " ".repeat(BridgeProtocol.MAX_REQUEST_CHUNK_JSON_BYTES + 1) + json;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(over, ServerAgentRequestChunkPayload.class));
        var wide = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        wide.addProperty("base64Data", "中".repeat(9000));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wide.toString(),
                ServerAgentRequestChunkPayload.class));
    }

    @Test void duplicateNestedFieldsAreRejectedByGsonTokenScanNotSilentlyOverwritten() {
        var codec = new BridgeJsonCodec();
        var request = new ServerAgentRequestPayload(UUID.randomUUID(), "main", "hello", false);
        String json = codec.encode(request);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(
                json.replace("\"text\":\"hello\"", "\"text\":\"hello\",\"text\":\"bye\""),
                ServerAgentRequestPayload.class));
    }

    @Test void splitUsesEncodedUtf8EnvelopeAndLimitsBothChunkSizeAndChunkCount() {
        var sender = new ServerAgentRequestChunker();
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> sender.split(id, "hello", 0));
        assertThrows(IllegalArgumentException.class, () -> sender.split(id, "hello",
                BridgeProtocol.TRANSPORT_CHUNK_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> sender.split(id, "中中", 6, 5));
        assertThrows(IllegalArgumentException.class, () -> sender.split(id, "ab", 1, 100));
        assertThrows(IllegalArgumentException.class, () -> sender.split(id, "hello", 1,
                BridgeProtocol.MAX_OPENAI_REQUEST_BYTES + 1));
        assertEquals(6, Base64.getDecoder().decode(sender.split(id, "中中", 6, 6)
                .getFirst().base64Data()).length);
        assertEquals("", new ServerAgentRequestChunker.Reassembler(1)
                .accept(UUID.randomUUID(), sender.split(id, "", 1, 1).getFirst()).orElseThrow());
    }

    @Test void protocolConfiguredEnvelopeUsesApplicationBytesNotModelContextOrNativeImageBytes() {
        assertEquals(50_000_000, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        assertEquals(32_000_000, BridgeProtocol.MAX_ANTHROPIC_REQUEST_BYTES);
        assertEquals(16_384, BridgeProtocol.TRANSPORT_CHUNK_BYTES);
        var global = new ServerAgentRequestChunker.Reassembler();
        var anthropic = new ServerAgentRequestChunker.Reassembler(BridgeProtocol.MAX_ANTHROPIC_REQUEST_BYTES);
        int aboveAnthropic = (BridgeProtocol.MAX_ANTHROPIC_REQUEST_BYTES
                + BridgeProtocol.TRANSPORT_CHUNK_BYTES - 1) / BridgeProtocol.TRANSPORT_CHUNK_BYTES + 1;
        var chunk = new ServerAgentRequestChunkPayload(UUID.randomUUID(), 0, aboveAnthropic, HASH, "eA==");
        UUID actor = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> anthropic.accept(actor, chunk));
        assertEquals(0, anthropic.activeAssemblies());
        assertTrue(global.accept(actor, chunk).isEmpty());
        global.clearActor(actor);
        assertEquals(0, global.activeAssemblies());
    }

    @Test void configuredCountAndCumulativeBytesRejectBeforeGrowingAssemblyBeyondBudget() {
        var receiver = new ServerAgentRequestChunker.Reassembler(20_000);
        UUID actor = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(actor,
                new ServerAgentRequestChunkPayload(id, 0, 3, HASH, "eA==")));
        assertEquals(0, receiver.activeAssemblies());
        var first = new ServerAgentRequestChunkPayload(id, 0, 2, HASH, encode(new byte[16_384]));
        var second = new ServerAgentRequestChunkPayload(id, 1, 2, HASH, encode(new byte[5_000]));
        assertTrue(receiver.accept(actor, first).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(actor, second));
        assertEquals(0, receiver.activeAssemblies());
        var tooSmall = new ServerAgentRequestChunker.Reassembler(1);
        assertThrows(IllegalArgumentException.class, () -> tooSmall.accept(actor,
                new ServerAgentRequestChunkPayload(id, 0, 1, HASH, encode(new byte[2]))));
        assertEquals(0, tooSmall.activeAssemblies());
    }

    @Test void oneActorCannotReplaceActiveAssemblyButDifferentActorsRemainIndependent() {
        UUID firstActor = UUID.randomUUID();
        UUID secondActor = UUID.randomUUID();
        var sender = new ServerAgentRequestChunker();
        var first = sender.split(UUID.randomUUID(), "abcd", 2);
        var second = sender.split(UUID.randomUUID(), "efgh", 2);
        var receiver = new ServerAgentRequestChunker.Reassembler();
        assertTrue(receiver.accept(firstActor, first.getFirst()).isEmpty());
        assertThrows(IllegalStateException.class, () -> receiver.accept(firstActor, second.getFirst()));
        assertEquals(1, receiver.activeAssemblies());
        assertTrue(receiver.accept(secondActor, second.getFirst()).isEmpty());
        assertEquals(2, receiver.activeAssemblies());
        assertEquals("abcd", receiver.accept(firstActor, first.getLast()).orElseThrow());
        assertEquals("efgh", receiver.accept(secondActor, second.getLast()).orElseThrow());
        assertEquals(0, receiver.activeAssemblies());
        assertTrue(receiver.accept(firstActor, second.getFirst()).isEmpty());
        receiver.clearActor(firstActor);
        assertEquals(0, receiver.activeAssemblies());
    }

    @Test void duplicateBytesDoNotChargeBudgetTwiceAndConflictingDataClearsOnlyOwningActor() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        byte[] complete = new byte[20_000];
        String hash = ResultChunker.sha256(complete);
        var first = new ServerAgentRequestChunkPayload(id, 0, 2, hash, encode(new byte[10_000]));
        var last = new ServerAgentRequestChunkPayload(id, 1, 2, hash, encode(new byte[10_000]));
        var receiver = new ServerAgentRequestChunker.Reassembler(20_000);
        assertTrue(receiver.accept(actor, first).isEmpty());
        assertTrue(receiver.accept(actor, first).isEmpty());
        assertEquals(20_000, receiver.accept(actor, last).orElseThrow().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(receiver.accept(actor, first).isEmpty());
        assertTrue(receiver.accept(other, first).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(actor,
                new ServerAgentRequestChunkPayload(id, 0, 2, hash, "eA==")));
        assertEquals(1, receiver.activeAssemblies());
        receiver.clearActor(other);
        assertEquals(0, receiver.activeAssemblies());
    }

    @Test void finalHashAndChangedMetadataFailuresReleaseOnlyCurrentAssembly() {
        UUID actor = UUID.randomUUID();
        UUID otherActor = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        var receiver = new ServerAgentRequestChunker.Reassembler();
        var first = new ServerAgentRequestChunkPayload(id, 0, 2, HASH, "eA==");
        assertTrue(receiver.accept(actor, first).isEmpty());
        assertTrue(receiver.accept(otherActor, first).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(actor,
                new ServerAgentRequestChunkPayload(id, 1, 2, "b".repeat(64), "eA==")));
        assertEquals(1, receiver.activeAssemblies());
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(otherActor,
                new ServerAgentRequestChunkPayload(id, 1, 2, HASH, "eA==")));
        assertEquals(0, receiver.activeAssemblies());
    }

    @Test void racingAdmissionsPermitOnlyOneCurrentAssemblyAndCancellationDoesNotMixActors() throws Exception {
        UUID actor = UUID.randomUUID();
        var sender = new ServerAgentRequestChunker();
        var one = sender.split(UUID.randomUUID(), "abcd", 2).getFirst();
        var two = sender.split(UUID.randomUUID(), "efgh", 2).getFirst();
        var receiver = new ServerAgentRequestChunker.Reassembler();
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> admit(receiver, actor, one, ready, go));
            var second = executor.submit(() -> admit(receiver, actor, two, ready, go));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(1, first.get(2, TimeUnit.SECONDS) + second.get(2, TimeUnit.SECONDS));
            assertEquals(1, receiver.activeAssemblies());
            receiver.clearActor(actor);
            assertEquals(0, receiver.activeAssemblies());
        }
    }

    @Test void expiredAssemblyDoesNotRemoveReplacementForSameActorAndRequest() throws Exception {
        var sender = new ServerAgentRequestChunker();
        UUID actor = UUID.randomUUID();
        var chunks = sender.split(UUID.randomUUID(), "abcd", 2);
        var receiver = new ServerAgentRequestChunker.Reassembler(Duration.ofMillis(10));
        assertTrue(receiver.accept(actor, chunks.getFirst()).isEmpty());
        var expired = new CountDownLatch(1);
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            scheduler.schedule(expired::countDown, 50, TimeUnit.MILLISECONDS);
            assertTrue(expired.await(2, TimeUnit.SECONDS));
            assertEquals(0, receiver.activeAssemblies());
            assertTrue(receiver.accept(actor, chunks.getFirst()).isEmpty());
            assertEquals("abcd", receiver.accept(actor, chunks.getLast()).orElseThrow());
            assertEquals(0, receiver.activeAssemblies());
        } finally {
            scheduler.shutdownNow();
        }
    }

    private void chunkCanonicalBase64AndActorScopedCancellationRejectMalformedBoundaries() {
        UUID requestId = UUID.randomUUID();
        for (String data : List.of("A", "AR==", "AQ=A", "-Q==", "AQ==\n")) {
            assertThrows(IllegalArgumentException.class, () -> new ServerAgentRequestChunkPayload(
                    requestId, 0, 1, HASH, data));
        }
        var chunk = new ServerAgentRequestChunker().split(requestId, "abcd", 2).getFirst();
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var receiver = new ServerAgentRequestChunker.Reassembler();
        assertTrue(receiver.accept(actor, chunk).isEmpty());
        assertTrue(receiver.accept(other, chunk).isEmpty());
        assertFalse(receiver.cancel(actor, UUID.randomUUID()));
        assertTrue(receiver.cancel(actor, requestId));
        assertEquals(1, receiver.activeAssemblies());
        assertFalse(receiver.cancel(actor, requestId));
        assertTrue(receiver.cancel(other, requestId));
        assertEquals(0, receiver.activeAssemblies());
    }

    private int admit(ServerAgentRequestChunker.Reassembler receiver, UUID actor,
            ServerAgentRequestChunkPayload chunk, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        assertTrue(go.await(2, TimeUnit.SECONDS));
        try { receiver.accept(actor, chunk); return 1; }
        catch (IllegalStateException busy) { return 0; }
    }

    private static String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
}
