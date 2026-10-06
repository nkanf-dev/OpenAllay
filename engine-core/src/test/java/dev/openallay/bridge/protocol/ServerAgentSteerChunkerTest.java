package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ServerAgentSteerChunkerTest {
    @Test
    void exactChunkShapeAndUnicodeReassemblyKeepBothCorrelations() {
        BridgeJsonCodec codec = new BridgeJsonCodec();
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        String text = "别改世界，只比较齿轮 ⚙".repeat(200);
        List<ServerAgentSteerChunkPayload> chunks =
                new ServerAgentSteerChunker().split(request, message, text, 11);
        ServerAgentSteerChunker.Reassembler incoming = new ServerAgentSteerChunker.Reassembler();
        java.util.Optional<String> restored = java.util.Optional.empty();
        for (var chunk : chunks.reversed()) {
            assertEquals(request, chunk.requestId());
            assertEquals(message, chunk.messageId());
            assertEquals(chunk, codec.decode(codec.encode(chunk), ServerAgentSteerChunkPayload.class));
            var accepted = incoming.accept(actor, chunk);
            if (accepted.isPresent()) restored = accepted;
        }
        assertEquals(text, restored.orElseThrow());
        assertEquals(0, incoming.activeAssemblies());
        JsonObject shape = dev.openallay.json.JsonTrees.parse(codec.encode(chunks.getFirst())).getAsJsonObject();
        assertEquals(Set.of("requestId", "messageId", "index", "total", "contentHash", "base64Data"),
                dev.openallay.json.JsonTrees.keys(shape));
        shape.addProperty("version", 1);
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode(shape.toString(), ServerAgentSteerChunkPayload.class));
    }

    @Test
    void assembliesAreIsolatedByActorRequestAndMessageAndClearedAtOwnerBoundaries() {
        UUID firstActor = UUID.randomUUID();
        UUID secondActor = UUID.randomUUID();
        UUID firstRequest = UUID.randomUUID();
        UUID secondRequest = UUID.randomUUID();
        UUID firstMessage = UUID.randomUUID();
        UUID secondMessage = UUID.randomUUID();
        ServerAgentSteerChunker splitter = new ServerAgentSteerChunker();
        ServerAgentSteerChunker.Reassembler incoming = new ServerAgentSteerChunker.Reassembler();
        var first = splitter.split(firstRequest, firstMessage, "pending steer", 2);
        var secondActorChunks = splitter.split(firstRequest, firstMessage, "other actor", 2);
        assertTrue(incoming.accept(firstActor, first.getFirst()).isEmpty());
        assertTrue(incoming.accept(secondActor, secondActorChunks.getFirst()).isEmpty());
        assertThrows(IllegalStateException.class, () -> incoming.accept(firstActor,
                splitter.split(secondRequest, firstMessage, "busy request", 2).getFirst()));
        assertThrows(IllegalStateException.class, () -> incoming.accept(firstActor,
                splitter.split(firstRequest, secondMessage, "busy message", 2).getFirst()));
        assertEquals(2, incoming.activeAssemblies());
        incoming.clearRequest(firstActor, firstRequest);
        assertEquals(1, incoming.activeAssemblies());
        assertFalse(incoming.cancel(firstActor, firstRequest, firstMessage));
        assertTrue(incoming.cancel(secondActor, firstRequest, firstMessage));
        assertEquals(0, incoming.activeAssemblies());
        assertTrue(incoming.accept(firstActor, first.getFirst()).isEmpty());
        incoming.clearActor(firstActor);
        assertEquals(0, incoming.activeAssemblies());
    }

    @Test
    void repeatedFullyAssembledMessageCanCarryAnEditWithoutGrowingAnAssembly() {
        UUID actor = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        ServerAgentSteerChunker splitter = new ServerAgentSteerChunker();
        ServerAgentSteerChunker.Reassembler incoming = new ServerAgentSteerChunker.Reassembler();
        for (String value : List.of("first", "edited", "edited")) {
            java.util.Optional<String> restored = java.util.Optional.empty();
            for (var chunk : splitter.split(request, message, value, 2)) {
                var accepted = incoming.accept(actor, chunk);
                if (accepted.isPresent()) restored = accepted;
            }
            assertEquals(value, restored.orElseThrow());
            assertEquals(0, incoming.activeAssemblies());
        }
        // Reassembly does not invent new inbox IDs. Store's consumed-ID fence rejects replay
        // after application; before application a complete PUT replaces the pending entry.
    }

    @Test
    void productionSteerChunksFitTheExistingMinecraftPacketLimit() {
        BridgeJsonCodec codec = new BridgeJsonCodec();
        for (var chunk : new ServerAgentSteerChunker().split(UUID.randomUUID(), UUID.randomUUID(),
                "⚙".repeat(BridgeProtocol.TRANSPORT_CHUNK_BYTES), BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
            assertTrue(codec.encode(chunk).length() < 32_767);
        }
    }
}
