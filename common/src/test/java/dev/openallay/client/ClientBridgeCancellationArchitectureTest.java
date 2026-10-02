package dev.openallay.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Source-level loader parity checks; no loader or networking runtime is required. */
final class ClientBridgeCancellationArchitectureTest {
    @Test
    void bothLoadersRetainCancelledCallbacksAndChunksWhileClosingToolsImmediately()
            throws Exception {
        for (Path bridge : clientBridges()) {
            String source = Files.readString(bridge);
            String cancel = block(source, "public boolean cancelServer(UUID requestId)");
            assertTrue(source.contains("Map<UUID, ServerRequest> serverRequests"), bridge::toString);
            String ask = block(source, "public boolean askServer(");
            assertTrue(ask.contains("new ServerRequest(events)"), bridge::toString);
            assertTrue(ask.contains("serverRequests.put(request.requestId()")
                    || ask.contains("serverRequests.putIfAbsent(request.requestId()"), bridge::toString);
            assertTrue(ask.indexOf("serverRequests.put") < ask.indexOf("requestChunker.split("),
                    bridge::toString);
            assertTrue(cancel.contains("ServerRequest request = serverRequests.get(requestId)"),
                    bridge::toString);
            assertTrue(cancel.contains("cancelled = request != null && !request.cancelled"),
                    bridge::toString);
            assertTrue(cancel.contains("if (cancelled) request.cancelled = true"), bridge::toString);
            assertTrue(cancel.contains("if (!cancelled"), bridge::toString);
            assertFalse(cancel.contains("serverRequests.remove("), bridge::toString);
            assertFalse(cancel.contains("clearAgentEventChunksLocked("), bridge::toString);
            assertTrue(cancel.contains("endpoint.close(requestId)"), bridge::toString);
            assertTrue(cancel.indexOf("endpoint.close(requestId)")
                    < cancel.indexOf("send(\"agent_cancel\""), bridge::toString);
        }
    }

    @Test
    void bothLoadersKeepCancelledCallbacksThroughUsageAndDrainOnlyAfterActualRelease()
            throws Exception {
        List<Path> bridges = clientBridges();
        String fabric = Files.readString(bridges.get(0));
        String neoForge = Files.readString(bridges.get(1));
        List<String> sharedBlocks = List.of(
                "private static final class ServerRequest",
                "private void receiveAgentEvent(ServerAgentEventPayload event)",
                "private void receiveAgentEventChunk(ServerAgentEventChunkPayload chunk)",
                "private void clearAgentEventChunksLocked(UUID requestId)",
                "public void disconnectState()");
        for (String declaration : sharedBlocks) {
            assertEquals(block(fabric, declaration), block(neoForge, declaration), declaration);
        }
        for (Path bridge : bridges) {
            String source = Files.readString(bridge);
            String receive = block(source,
                    "private void receiveAgentEvent(ServerAgentEventPayload event)");
            assertTrue(receive.contains("boolean released = \"request_released\".equals(event.eventType())"),
                    bridge::toString);
            assertTrue(receive.contains("request = serverRequests.get(event.requestId())"), bridge::toString);
            assertTrue(receive.contains("request.events.accept(event)"), bridge::toString);
            assertTrue(receive.contains("if (terminal) request.terminal = true"), bridge::toString);
            // Terminal UI and cancellation do not discard a later actual usage receipt. Removal
            // is guarded by the real release event, never by the terminal/context flags.
            assertTrue(receive.contains("if (released)")
                    || receive.contains("if (released && serverRequests.remove"), bridge::toString);
            assertTrue(receive.contains("serverRequests.remove(event.requestId(), request)"),
                    bridge::toString);
            assertFalse(receive.contains("request.cancelled && request.contextFinalized && request.terminal"),
                    bridge::toString);
            assertFalse(receive.contains("if (terminal && !request.cancelled)"), bridge::toString);
            assertTrue(receive.indexOf("request.events.accept(event)")
                    < receive.indexOf("serverRequests.remove(event.requestId(), request)"), bridge::toString);
            assertTrue(receive.indexOf("serverRequests.remove(event.requestId(), request)")
                    < receive.lastIndexOf("clearAgentEventChunksLocked(event.requestId())"), bridge::toString);
            assertTrue(receive.contains("if (terminal || released)"), bridge::toString);
            // Released can synchronously dispatch the next request. Its old tool scope must
            // already be closed when the callback runs; callback cleanup cannot race it.
            assertTrue(receive.indexOf("endpoint.close(event.requestId())")
                    < receive.indexOf("request.events.accept(event)"), bridge::toString);
            String chunk = block(source,
                    "private void receiveAgentEventChunk(ServerAgentEventChunkPayload chunk)");
            assertTrue(chunk.contains("serverRequests.containsKey(chunk.requestId())"),
                    bridge::toString);
            assertTrue(chunk.contains("agentEventChunks.accept(chunk.asRemoteChunk())"),
                    bridge::toString);
            assertTrue(chunk.contains("receiveAgentEvent(completed)"), bridge::toString);
            String disconnect = block(source, "public void disconnectState()");
            assertTrue(disconnect.contains("serverRequests.clear()"), bridge::toString);
            assertTrue(disconnect.contains("agentEventIds.clear()"), bridge::toString);
            assertTrue(disconnect.contains("agentEventChunks.clear()"), bridge::toString);
        }
    }

    private static List<Path> clientBridges() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path root = current.getFileName() != null
                && current.getFileName().toString().equals("common")
                ? current.getParent() : current;
        return List.of(
                root.resolve("fabric/src/main/java/dev/openallay/fabric/network/"
                        + "FabricClientBridge.java"),
                root.resolve("neoforge/src/main/java/dev/openallay/neoforge/network/"
                        + "NeoForgeClientBridge.java"));
    }

    private static String block(String source, String declaration) {
        int start = source.indexOf(declaration);
        if (start < 0) throw new AssertionError("Missing declaration: " + declaration);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') depth++;
            if (character == '}' && --depth == 0) return source.substring(start, index + 1);
        }
        throw new AssertionError("Unclosed declaration: " + declaration);
    }
}
