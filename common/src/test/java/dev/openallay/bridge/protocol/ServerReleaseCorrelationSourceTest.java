package dev.openallay.bridge.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Both loader adapters must keep exact callback identity through the numeric receipt barrier. */
final class ServerReleaseCorrelationSourceTest {
    @Test void bothLoadersRetireOnlyOnRequestReleased() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("fabric"))) root = root.getParent();
        assertNotNull(root);
        for (String relative : List.of(
                "fabric/src/main/java/dev/openallay/fabric/network/FabricClientBridge.java",
                "neoforge/src/main/java/dev/openallay/neoforge/network/NeoForgeClientBridge.java")) {
            String source = Files.readString(root.resolve(relative));
            int start = source.indexOf("private void receiveAgentEvent(ServerAgentEventPayload event)");
            int end = source.indexOf("/** One pending cancellation", start);
            String method = source.substring(start, end);
            assertTrue(method.contains("\"request_released\".equals(event.eventType())"), relative);
            assertTrue(method.contains("if (released && serverRequests.remove(event.requestId(), request))"), relative);
            assertFalse(method.contains("if (terminal && !request.cancelled)"), relative);
            assertFalse(method.contains("request.cancelled && request.contextFinalized && request.terminal"), relative);
        }
    }
}
