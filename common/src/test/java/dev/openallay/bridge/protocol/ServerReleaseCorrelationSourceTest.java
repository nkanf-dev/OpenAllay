package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** One shared callback owner retains exact request identity through the release barrier. */
final class ServerReleaseCorrelationSourceTest {
    @Test void bothLoadersRetireOnlyOnRequestReleased() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        for (String relative : List.of(
                "fabric/src/main/java/dev/openallay/fabric/network/FabricClientBridge.java",
                "neoforge/src/main/java/dev/openallay/neoforge/network/NeoForgeClientBridge.java")) {
            String facade = Files.readString(root.resolve(relative));
            assertTrue(facade.contains("extends ClientBridgeSession"), relative);
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
            assertFalse(facade.contains("serverRequests"), relative);
            assertFalse(facade.contains("receiveAgentEvent("), relative);
        }
        String source = Files.readString(root.resolve(
                "engine-core/src/main/java/dev/openallay/bridge/client/ClientBridgeSession.java"));
        int start = source.indexOf("private void receiveAgentEvent(ServerAgentEventPayload event)");
        int end = source.indexOf("/** One pending cancellation", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);
        assertTrue(method.contains("\"request_released\".equals(event.eventType())"));
        assertTrue(method.contains("if (released && serverRequests.remove(event.requestId(), request))"));
        assertFalse(method.contains("if (terminal && !request.cancelled)"));
        assertFalse(method.contains("request.cancelled && request.contextFinalized && request.terminal"));
    }
}
