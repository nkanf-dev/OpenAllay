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
            String cancel = block(source, "public final boolean cancelServer(UUID requestId)");
            assertTrue(source.contains("Map<UUID, ServerRequest> serverRequests"), bridge::toString);
            String ask = block(source, "public final boolean askServer(");
            assertTrue(ask.contains("new ServerRequest("), bridge::toString);
            assertTrue(ask.contains("captured.actorId(), captured.current(), connectionScope"), bridge::toString);
            assertTrue(ask.contains("host.captureConnection()"), bridge::toString);
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
                "public final void disconnectState()");
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
            String disconnect = block(source, "public final void disconnectState()");
            assertTrue(disconnect.contains("serverRequests.clear()"), bridge::toString);
            assertTrue(disconnect.contains("agentEventIds.clear()"), bridge::toString);
            assertTrue(disconnect.contains("agentEventChunks.clear()"), bridge::toString);
        }
    }

    @Test
    void bothLoadersFenceQueuedImageReadsContextCaptureAndEveryNativeResultSend() throws Exception {
        for (Path bridge : clientBridges()) {
            String source = Files.readString(bridge);
            String prepare = block(source, "public final void configureResultImages(");
            assertTrue(prepare.contains("endpoint.configureResultImages("), bridge::toString);
            assertTrue(prepare.contains("store.read(request.actorId, reference)"), bridge::toString);
            assertTrue(prepare.indexOf("requireCurrent(requestId, request)")
                    < prepare.indexOf("store.read(request.actorId, reference)"), bridge::toString);
            assertTrue(prepare.lastIndexOf("requireCurrent(requestId, request)")
                    > prepare.indexOf("store.read(request.actorId, reference)"), bridge::toString);
            String close = block(prepare, "public void close(UUID requestId)");
            assertTrue(close.contains("contexts.closeRequest(requestId.toString())"), bridge::toString);
            assertFalse(close.contains("releaseObservationImages"), bridge::toString);
            String queued = block(source, "private void queueClientToolResult(");
            assertTrue(queued.contains("dispatcher.execute("), bridge::toString);
            assertTrue(queued.lastIndexOf("current(chunk.requestId(), request)")
                    > queued.indexOf("dispatcher.execute("), bridge::toString);
            assertTrue(queued.lastIndexOf("current(chunk.requestId(), request)")
                    < queued.indexOf("send(\"client_tool_result\""), bridge::toString);
            String identity = block(source, "private boolean current(UUID requestId, ServerRequest request)");
            assertTrue(identity.contains("serverRequests.get(requestId) == request"), bridge::toString);
            assertTrue(identity.contains("request.connectionScope == connectionScope"), bridge::toString);
            assertTrue(identity.contains("request.connectionCurrent.getAsBoolean()"), bridge::toString);
            String admission = block(source, "public final java.util.function.BooleanSupplier clientToolAdmission(");
            assertTrue(admission.contains("current(requestId, request)"), bridge::toString);
        }
    }

    @Test
    void bothNativeFacadesKeepExactConnectionChecksWithoutDuplicatingFeatureState() throws Exception {
        Path root = repositoryRoot();
        for (String relative : List.of(
                "fabric/src/main/java/dev/openallay/fabric/network/FabricClientBridge.java",
                "neoforge/src/main/java/dev/openallay/neoforge/network/NeoForgeClientBridge.java")) {
            String facade = Files.readString(root.resolve(relative));
            assertTrue(facade.contains("extends ClientBridgeSession"), relative);
            assertTrue(facade.contains("client.player.getUUID()"), relative);
            assertTrue(facade.contains("getConnection() == connection"), relative);
            assertTrue(facade.contains("inboundCallback("), relative);
            String nativePath = relative.replace("ClientBridge.java", "NativeClientPayloads.java");
            String nativePort = Files.readString(root.resolve(nativePath));
            if (relative.startsWith("fabric/")) {
                assertTrue(facade.contains("FabricNativeClientPayloads.register("), relative);
                assertTrue(nativePort.contains("ClientPlayNetworking.registerGlobalReceiver"));
                assertTrue(nativePort.contains("context.client().getConnection() == connection"));
                assertTrue(nativePort.indexOf("receiver.apply(packet") < nativePort.indexOf("context.client().execute(callback)"));
            } else {
                assertTrue(facade.contains("NeoForgeNativeClientPayloads.register("), relative);
                assertTrue(nativePort.contains("RegisterClientPayloadHandlersEvent"));
                assertTrue(nativePort.contains("receiver.apply(packet).run()"));
                assertFalse(nativePort.contains("execute(callback)"), "Modern NeoForge owns dispatch; no second queued hop");
            }
            assertFalse(facade.contains("Map<UUID, ServerRequest>"), relative);
            assertFalse(facade.contains("requestChunker.split("), relative);
        }
        String shared = Files.readString(clientBridges().get(0));
        String inbound = block(shared, "protected final Runnable inboundCallback(");
        assertTrue(inbound.contains("scope != connectionScope"));
        assertTrue(inbound.contains("!connectionCurrent.getAsBoolean()"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) current = current.getParent();
        if (current == null) throw new IllegalStateException("Repository root unavailable");
        return current;
    }

    private static List<Path> clientBridges() {
        Path owner = repositoryRoot().resolve(
                "engine-core/src/main/java/dev/openallay/bridge/client/ClientBridgeSession.java");
        // Both loader facades inherit this exact owner; their transport edges are checked separately.
        return List.of(owner, owner);
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
