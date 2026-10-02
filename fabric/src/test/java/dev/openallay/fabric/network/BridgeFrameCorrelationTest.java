package dev.openallay.fabric.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BridgeFrameCorrelationTest {
    @Test void keepsOnlyUnambiguousRootStringUuids() {
        UUID request = UUID.randomUUID();
        String json = "{\"requestId\":\"" + request
                + "\",\"index\":0.9,\"base64Data\":\"invalid\"}";
        assertEquals(request, BridgeFrameCorrelation.read(json).orElseThrow());
    }
    @Test void rejectsDuplicateEscapedOrTypedCorrelationAndNeverUsesNestedIds() {
        UUID id = UUID.randomUUID();
        for (String json : java.util.List.of(
                "{\"requestId\":\""+id+"\",\"requestId\":\""+id+"\"}",
                "{\"requestId\":\""+id+"\",\"request" + "\\" + "u0049d\":\""+id+"\"}",
                "{\"requestId\":42}", "{\"requestId\":null}",
                "{\"value\":{\"requestId\":\""+id+"\"}}", "{\"requestId\":\"1-1-1-1-1\"}",
                "{\"requestId\":\""+id+"\"} trailing")) {
            assertTrue(BridgeFrameCorrelation.read(json).isEmpty(), json);
        }
    }
    @Test void recoversInitiatingRequestFromStrictNumericPayloadRejection() {
        UUID request = UUID.randomUUID();
        String malformed = "{\"requestId\":\"" + request
                + "\",\"index\":0.9,\"total\":1,\"contentHash\":\"" + "a".repeat(64)
                + "\",\"base64Data\":\"eA==\"}";
        var codec = new dev.openallay.bridge.protocol.BridgeJsonCodec();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(
                malformed, dev.openallay.bridge.protocol.ServerAgentRequestChunkPayload.class));
        assertEquals(request, BridgeFrameCorrelation.read(malformed).orElseThrow());
    }
    @Test void actorBusyRejectionDoesNotClearAnotherPlayerOrTheOriginalAssembly() {
        UUID actor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        var chunker = new dev.openallay.bridge.protocol.ServerAgentRequestChunker();
        var firstParts = chunker.split(first, "two transport parts", 10);
        var rejectedParts = chunker.split(rejected, "other two parts", 10);
        var assembly = new dev.openallay.bridge.protocol.ServerAgentRequestChunker.Reassembler();
        assertTrue(assembly.accept(actor, firstParts.getFirst()).isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> assembly.accept(actor, rejectedParts.getFirst()));
        assembly.cancel(actor, rejected);
        assertTrue(assembly.accept(other, rejectedParts.getFirst()).isEmpty());
        assertEquals("two transport parts", assembly.accept(actor, firstParts.getLast()).orElseThrow());
        assertEquals("other two parts", assembly.accept(other, rejectedParts.getLast()).orElseThrow());
    }
    @Test void boundsTransportFramesBeforeReading() {
        assertTrue(BridgeFrameCorrelation.read("x".repeat(32_768)).isEmpty());
        assertTrue(BridgeFrameCorrelation.read(null).isEmpty());
        assertTrue(BridgeFrameCorrelation.read("[]").isEmpty());
        assertTrue(BridgeFrameCorrelation.read("{not-json}").isEmpty());
    }
}
